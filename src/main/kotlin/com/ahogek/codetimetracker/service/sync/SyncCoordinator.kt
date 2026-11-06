package com.ahogek.codetimetracker.service.sync

import com.ahogek.codetimetracker.database.DatabaseManager
import com.ahogek.codetimetracker.database.SessionRepository
import com.ahogek.codetimetracker.database.SyncCursorRepository
import com.ahogek.codetimetracker.user.UserManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import java.time.LocalDateTime
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Orchestrates one sync round: pull remote changes and apply them, push the local dirty
 * sessions, then pull again so the local store converges on the server-authoritative
 * state (including this device's own changes and any concurrent writes).
 *
 * <p>Failure handling (idempotency): the pull cursor is persisted after each applied
 * page (a pull sequence drains server-reported pages until `hasMore` is false), and
 * dirty sessions keep their `is_synced = 0` marker until a push succeeds, so a pull
 * or push failure leaves the cursor at the last successful page. Every pull also
 * replays from that cursor (`replayFromChangeId`): the server advances its own
 * watermark before it hands a page over, so without the replay a page that failed to
 * apply - or arrived while the process was killed - would be skipped forever. A server
 * that predates the field answers 400 to the unknown property, and [pullPage] falls
 * back to a plain request once per server URL. 429 back-off is handled inside
 * [SyncHttpClient]. Once a push succeeds the pushed sessions are marked synced (the
 * server has accepted the batch atomically); if the follow-up reconcile pull then
 * fails, the next round pulls the remaining changes and converges.
 *
 * @author AhogeK ahogek@gmail.com
 * @since 2026-08-29
 */
@Service(Service.Level.APP)
class SyncCoordinator(
    private val settings: SyncSettingsState,
    private val keyManager: SyncApiKeyManager,
    private val api: SyncApiService,
    private val cursorRepository: SyncCursorRepository,
    private val sessionRepository: SessionRepository,
    private val applier: SyncSessionApplier = SyncSessionApplier(sessionRepository),
    private val deviceMetadataProvider: () -> RegisterDeviceRequest = SyncDeviceMetadata::registrationRequest,
    private val notifySyncCompleted: () -> Unit = {
        ApplicationManager.getApplication().messageBus
            .syncPublisher(SyncStateListener.TOPIC)
            .syncCompleted()
    },
    private val pushBatchSize: Int = 500,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    /**
     * Platform-container entry point: the service container only supports parameterless
     * constructors, so dependencies are resolved via [ApplicationManager] and [DatabaseManager].
     */
    constructor() : this(
        ApplicationManager.getApplication().getService(SyncSettingsState::class.java),
        ApplicationManager.getApplication().getService(SyncApiKeyManager::class.java),
        ApplicationManager.getApplication().getService(SyncApiServiceImpl::class.java),
        DatabaseManager.getSyncCursorRepository(),
        DatabaseManager.getSessionRepository(),
    )

    companion object {
        private val log = Logger.getInstance(SyncCoordinator::class.java)

        /** Property name the server echoes when it rejects an unknown pull field. */
        private const val REPLAY_FIELD = "replayFromChangeId"

        /** How long a server that rejected [REPLAY_FIELD] is left alone before probing again. */
        private const val REPLAY_PROBE_INTERVAL_MS = 60 * 60 * 1000L
    }

    /** Guards against concurrent sync rounds from overlapping triggers (timer, events, manual). */
    private val syncInProgress = AtomicBoolean(false)

    /**
     * Server URL known to reject [SyncPullRequest.replayFromChangeId] as an unknown field,
     * and the moment that verdict expires. Replay is suspended for that URL only: pointing
     * the plugin at another server re-enables it immediately, and a mixed-version deployment
     * (one old instance behind a proxy) costs at most one probe per hour instead of a silent
     * permanent loss of replay.
     */
    @Volatile
    private var replayUnsupportedServerUrl: String? = null

    @Volatile
    private var replayUnsupportedUntilMillis: Long = 0L

    /**
     * Last successful sync completion time, read from the persisted push timestamp
     * (survives IDE restarts); exposed to the settings UI.
     */
    fun lastSyncAt(): LocalDateTime? = cursorRepository.getLastSyncAt(SyncDeviceMetadata.deviceId())

    /** Last sync failure message, exposed to the settings UI (cleared on success). */
    @Volatile
    var lastSyncError: String? = null
        private set

    /**
     * Resets the sync context when the bound API key switches to a different user: the
     * local pull cursor is cleared so the new user's changes are pulled from 0, and
     * existing dirty sessions are marked synced so the previous user's data is kept
     * locally but never pushed to the newly bound account. New sessions created after
     * the switch sync normally to the new user.
     */
    fun resetForUserSwitch() {
        val deviceId = SyncDeviceMetadata.deviceId()
        cursorRepository.clear(deviceId)
        sessionRepository.markAllSynced()
        lastSyncError = null
        // Drop the previous account scope; the newly bound key resolves its own id next.
        settings.serverUserId = null
        DatabaseManager.setStatsOwner(null)
    }

    /**
     * Runs a single sync round: pull and apply remote changes, push local dirty sessions,
     * then pull again so the local store converges on the server-authoritative state.
     * Returns [SyncResult.Success] when sync is disabled, already running or nothing needed
     * to sync; otherwise returns the first failure.
     */
    fun syncOnce(): SyncResult<Unit> {
        // A skipped round (disabled or unbound) is not a sync and must not touch the
        // status fields; an overlapping round is a no-op for the same reason.
        if (!settings.syncEnabled || !keyManager.isBound()) {
            return SyncResult.Success(Unit)
        }
        if (!syncInProgress.compareAndSet(false, true)) {
            return SyncResult.Success(Unit)
        }
        return try {
            doSyncOnce().also { result ->
                lastSyncError = when (result) {
                    is SyncResult.Success -> null
                    is SyncResult.Failure -> result.error.toUserMessage()
                }
                // Any completed round refreshes open UIs (settings page) immediately.
                notifySyncCompleted()
            }
        } finally {
            syncInProgress.set(false)
        }
    }

    /**
     * Re-registers this device when the server reports it revoked (404 COMMON_002):
     * the server keeps the device row for audit but stops sync; posting the same
     * registration clears revoked_at and resumes sync without re-binding the key.
     */
    private fun reRegisterDevice(apiKey: String) {
        api.registerDevice(deviceMetadataProvider(), apiKey)
    }

    /**
     * Returns true when a sync failure is the server reporting this device as
     * revoked/unknown (404 COMMON_002), which the device can self-heal by
     * re-registering.
     */
    private fun isDeviceRevoked(result: SyncResult<*>): Boolean =
        result is SyncResult.Failure && result.error.kind == SyncErrorKind.DEVICE_NOT_FOUND

    private fun doSyncOnce(): SyncResult<Unit> {
        val apiKey = keyManager.getApiKey() ?: return SyncResult.Failure(
            SyncError(SyncErrorKind.API_KEY_INVALID, message = "No API key stored for sync"),
        )
        val deviceId = SyncDeviceMetadata.deviceId()
        val userId = UserManager.getUserId()
        val local = deviceMetadataProvider()

        var cursor = cursorRepository.getPullCursor(deviceId)

        when (val pull = pullAndApplyPages(userId, deviceId, apiKey, local, cursor)) {
            is SyncResult.Failure -> return pull
            is SyncResult.Success -> cursor = pull.data
        }

        val dirty = sessionRepository.getDirtySessions()
        if (dirty.isNotEmpty()) {
            // Push in bounded batches: the server applies each request in one
            // transaction with per-session inserts, and the request timeout is 30s.
            // A large local history must not be sent as a single oversized request.
            var failedPushError: SyncError? = null
            dirty.chunked(pushBatchSize).forEach { batch ->
                val request = SyncPushRequest(
                    deviceId = deviceId,
                    sessions = batch.map { SyncSessionMapper.toSyncDto(it) },
                )
                var push = api.push(request, apiKey)
                if (isDeviceRevoked(push)) {
                    reRegisterDevice(apiKey)
                    push = api.push(request, apiKey)
                }
                when (push) {
                    is SyncResult.Failure -> {
                        if (failedPushError == null) failedPushError = push.error
                    }
                    is SyncResult.Success -> {
                        sessionRepository.markSynced(batch.map { it.sessionUuid }, settings.serverUserId)
                    }
                }
            }
            failedPushError?.let { return SyncResult.Failure(it) }
        }

        // Reconcile pull: converge on the server-authoritative state after pushing.
        when (val pulled = pullAndApplyPages(userId, deviceId, apiKey, local, cursor)) {
            is SyncResult.Failure -> return pulled
            is SyncResult.Success -> Unit
        }

        // Every completed round (pull or push) advances the persisted last-sync time.
        cursorRepository.setLastSyncAt(userId, deviceId)
        log.info("Sync round completed for device $deviceId")
        return SyncResult.Success(Unit)
    }

    /**
     * Drains one pull sequence: applies each page locally, persists the cursor after
     * every applied page, and continues while the server reports more pages
     * (ctt-server 0.62.0+ paged the change log; older servers answer a single
     * `hasMore = false` page). Returns the final cursor on success.
     *
     * Every request replays from the persisted cursor (see [SyncPullRequest.replayFromChangeId]):
     * a page that fails to apply or persist aborts the sequence and leaves the cursor at the
     * last successful page, which is exactly the point the next round asks the server to
     * replay. A guard against a non-advancing cursor prevents a misbehaving server from
     * pinning the sync loop forever.
     */
    private fun pullAndApplyPages(
        userId: String,
        deviceId: String,
        apiKey: String,
        local: RegisterDeviceRequest,
        initialCursor: Long,
    ): SyncResult<Long> {
        var cursor = initialCursor
        while (true) {
            when (val page = pullPage(deviceId, cursor, apiKey)) {
                is SyncResult.Failure -> return page
                is SyncResult.Success -> {
                    val data = page.data
                    if (data.hasMore && data.nextCursor <= cursor) {
                        return SyncResult.Failure(
                            SyncError(
                                SyncErrorKind.SERVER_ERROR,
                                message = "Server reported more changes without advancing the cursor " +
                                    "(cursor=$cursor, next=${data.nextCursor}); skipping this round.",
                            ),
                        )
                    }
                    try {
                        applier.apply(
                            data.changes,
                            userId,
                            local.platform.orEmpty(),
                            local.ideName.orEmpty(),
                            settings.serverUserId,
                        )
                        cursorRepository.setPullCursor(userId, deviceId, data.nextCursor)
                    } catch (e: Exception) {
                        log.warn("Failed to apply the pull page starting at cursor $cursor for device $deviceId", e)
                        return SyncResult.Failure(
                            SyncError(
                                SyncErrorKind.SERVER_ERROR,
                                message = "Failed to apply the pull page starting at cursor $cursor " +
                                    "(${data.changes.size} changes): ${e.message}",
                            ),
                        )
                    }
                    cursor = data.nextCursor
                    if (!data.hasMore) return SyncResult.Success(cursor)
                }
            }
        }
    }

    /**
     * Sends one pull page for [cursor], replaying from that same id so a page the client
     * never applied is delivered again even though the server watermark has moved past it.
     *
     * A server older than the replay contract answers 400 naming the unknown property. That
     * answer suspends replay for its URL for [REPLAY_PROBE_INTERVAL_MS] and the request is
     * retried without the field, so the extra round-trip is paid once per probe rather than
     * on every page. A 400 about anything else is retried the same way but does not suspend
     * replay, because it says nothing about the field.
     */
    private fun pullPage(deviceId: String, cursor: Long, apiKey: String): SyncResult<SyncPullResponse> {
        val plainRequest = SyncPullRequest(deviceId = deviceId, lastPulledChangeId = cursor)
        if (!replaySupported()) return sendPull(plainRequest, apiKey)

        val replay = sendPull(plainRequest.copy(replayFromChangeId = cursor), apiKey)
        when (replay) {
            is SyncResult.Success -> return replay
            is SyncResult.Failure -> if (replay.error.httpStatus != 400) return replay
        }
        if (replay.error.message?.contains(REPLAY_FIELD) == true) {
            replayUnsupportedServerUrl = settings.serverUrl
            replayUnsupportedUntilMillis = clock() + REPLAY_PROBE_INTERVAL_MS
            log.info("Server rejected $REPLAY_FIELD; retrying without it and probing again in an hour for ${settings.serverUrl}")
        }
        return sendPull(plainRequest, apiKey)
    }

    private fun replaySupported(): Boolean =
        replayUnsupportedServerUrl != settings.serverUrl || clock() >= replayUnsupportedUntilMillis

    /**
     * Sends one pull, re-registering this device once when the server reports it revoked
     * (404 COMMON_002). Registration clears the revocation server-side, so retrying the
     * same request resumes sync without re-binding the API key.
     */
    private fun sendPull(request: SyncPullRequest, apiKey: String): SyncResult<SyncPullResponse> {
        val first = api.pull(request, apiKey)
        return if (isDeviceRevoked(first)) {
            reRegisterDevice(apiKey)
            api.pull(request, apiKey)
        } else {
            first
        }
    }
}
