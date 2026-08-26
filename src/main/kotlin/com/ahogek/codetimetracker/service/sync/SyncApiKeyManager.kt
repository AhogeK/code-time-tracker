package com.ahogek.codetimetracker.service.sync

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service

/**
 * Owns the sync API key lifecycle: manual-paste binding, unbinding and status queries.
 * The raw key lives in the IDE credential store ([SyncKeyVault]); only its prefix is
 * kept in settings for display.
 *
 * @author AhogeK ahogek@gmail.com
 * @since 2026-08-26
 */
@Service(Service.Level.APP)
class SyncApiKeyManager(
    private val settings: SyncSettingsState,
) {
    /**
     * Platform-container entry point: the service container only supports
     * parameterless constructors, so dependencies are resolved via [ApplicationManager].
     */
    constructor() : this(ApplicationManager.getApplication().getService(SyncSettingsState::class.java))

    companion object {
        private const val API_KEY_PREFIX = "cttak_"
        private const val API_KEY_PREFIX_LENGTH = 12
    }

    /** Test seam; production uses the credential-store backed [PasswordSyncKeyVault]. */
    internal var vault: SyncKeyVault = PasswordSyncKeyVault

    /** Binds an API key created in the web console (manual paste). */
    fun bindWithManualKey(rawKey: String): SyncResult<Unit> {
        val trimmed = rawKey.trim()
        if (trimmed.isEmpty()) {
            return SyncResult.Failure(
                SyncError(SyncErrorKind.VALIDATION_ERROR, message = "API key must not be empty"),
            )
        }
        if (!trimmed.startsWith(API_KEY_PREFIX)) {
            return SyncResult.Failure(
                SyncError(SyncErrorKind.VALIDATION_ERROR, message = "API key must start with '$API_KEY_PREFIX'"),
            )
        }
        vault.save(trimmed)
        settings.apiKeyPrefix = trimmed.take(API_KEY_PREFIX_LENGTH)
        settings.syncEnabled = true
        return SyncResult.Success(Unit)
    }

    /** Removes the stored key and resets the binding state. */
    fun unbind() {
        vault.clear()
        settings.apiKeyPrefix = null
        settings.syncEnabled = false
    }

    fun getApiKey(): String? = vault.load()

    fun isBound(): Boolean = vault.load() != null
}
