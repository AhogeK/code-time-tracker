package com.ahogek.codetimetracker.service.sync

import com.ahogek.codetimetracker.database.DatabaseManager
import com.ahogek.codetimetracker.database.SessionRepository
import com.ahogek.codetimetracker.database.SyncCursorRepository
import com.ahogek.codetimetracker.user.UserManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger

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

    /**
     * Runs a single sync round: pull and apply remote changes, push local dirty sessions,
     * then pull again so the local store converges on the server-authoritative state.
     * Returns [SyncResult.Success] when sync is disabled or nothing needed to sync;
     * otherwise returns the first failure.
     */
    fun syncOnce(): SyncResult<Unit> {
        if (!settings.syncEnabled || !keyManager.isBound()) {
            return SyncResult.Success(Unit)
        }
        val apiKey = keyManager.getApiKey() ?: return SyncResult.Failure(
            SyncError(SyncErrorKind.API_KEY_INVALID, message = "No API key stored for sync"),
        )
        val deviceId = SyncDeviceMetadata.deviceId()
        val userId = UserManager.getUserId()
        val local = SyncDeviceMetadata.registrationRequest()

        var cursor = cursorRepository.getPullCursor(deviceId)

        when (val pull = api.pull(SyncPullRequest(deviceId = deviceId, lastPulledChangeId = cursor), apiKey)) {
            is SyncResult.Failure -> return pull
            is SyncResult.Success -> {
                applier.apply(
                    pull.data.changes,
                    userId,
                    local.platform.orEmpty(),
                    local.ideName.orEmpty(),
                )
                cursorRepository.setPullCursor(userId, deviceId, pull.data.nextCursor)
                cursor = pull.data.nextCursor
            }
        }

        val dirty = sessionRepository.getDirtySessions()
        if (dirty.isNotEmpty()) {
            val request = SyncPushRequest(
                deviceId = deviceId,
                sessions = dirty.map { SyncSessionMapper.toSyncDto(it) },
            )
            when (val push = api.push(request, apiKey)) {
                is SyncResult.Failure -> return push
                is SyncResult.Success -> {
                    sessionRepository.markSynced(dirty.map { it.sessionUuid })
                    cursorRepository.setPushAt(userId, deviceId)
                }
            }
        }

        when (val pull = api.pull(SyncPullRequest(deviceId = deviceId, lastPulledChangeId = cursor), apiKey)) {
            is SyncResult.Failure -> return pull
            is SyncResult.Success -> {
                applier.apply(
                    pull.data.changes,
                    userId,
                    local.platform.orEmpty(),
                    local.ideName.orEmpty(),
                )
                cursorRepository.setPullCursor(userId, deviceId, pull.data.nextCursor)
            }
        }

        log.info("Sync round completed for device $deviceId")
        return SyncResult.Success(Unit)
    }
}
