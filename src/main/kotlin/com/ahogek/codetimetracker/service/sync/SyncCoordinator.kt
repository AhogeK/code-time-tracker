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
 * <p>Failure handling (idempotency): the pull cursor is only persisted after a successful
 * pull, and dirty sessions keep their `is_synced = 0` marker until a push succeeds, so a
 * pull or push failure leaves both sides untouched and the next round retries safely.
 * 429 back-off is handled inside [SyncHttpClient]. Once a push succeeds the pushed
 * sessions are marked synced (the server has accepted the batch atomically); if the
 * follow-up reconcile pull then fails, the next round pulls the remaining changes and
 * converges.
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
    }

    /** Guards against concurrent sync rounds from overlapping triggers (timer, events, manual). */
    private val syncInProgress = AtomicBoolean(false)


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

        // The server may have revoked this device since the last round (404 COMMON_002);
        // re-registering the same id clears the revocation and resumes sync. The retry
        // fires at most once per round and only when the error is device-not-found.
        var firstPull = api.pull(SyncPullRequest(deviceId = deviceId, lastPulledChangeId = cursor), apiKey)
        if (isDeviceRevoked(firstPull)) {
            reRegisterDevice(apiKey)
            firstPull = api.pull(SyncPullRequest(deviceId = deviceId, lastPulledChangeId = cursor), apiKey)
        }
        val pull = firstPull
        when (pull) {
            is SyncResult.Failure -> return pull
            is SyncResult.Success -> {
                applier.apply(
                    pull.data.changes,
                    userId,
                    local.platform.orEmpty(),
                    local.ideName.orEmpty(),
                    settings.serverUserId,
                )
                cursorRepository.setPullCursor(userId, deviceId, pull.data.nextCursor)
                cursor = pull.data.nextCursor
            }
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

        when (val pull = api.pull(SyncPullRequest(deviceId = deviceId, lastPulledChangeId = cursor), apiKey)) {
            is SyncResult.Failure -> return pull
            is SyncResult.Success -> {
                applier.apply(
                    pull.data.changes,
                    userId,
                    local.platform.orEmpty(),
                    local.ideName.orEmpty(),
                    settings.serverUserId,
                )
                cursorRepository.setPullCursor(userId, deviceId, pull.data.nextCursor)
            }
        }

        // Every completed round (pull or push) advances the persisted last-sync time.
        cursorRepository.setLastSyncAt(userId, deviceId)
        log.info("Sync round completed for device $deviceId")
        return SyncResult.Success(Unit)
    }
}
