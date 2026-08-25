package com.ahogek.codetimetracker.service.sync

import com.ahogek.codetimetracker.user.UserManager
import com.intellij.openapi.components.Service
import java.net.InetAddress

/**
 * Owns the sync API key lifecycle: sign-in binding (login -> create SYNC-scoped key ->
 * store in the credential vault), manual paste binding, unbinding and status queries.
 *
 * @author AhogeK ahogek@gmail.com
 * @since 2026-08-26
 */
@Service(Service.Level.APP)
class SyncApiKeyManager(
    private val apiService: SyncApiService,
    private val settings: SyncSettingsState,
) {

    companion object {
        private const val SYNC_SCOPE = "SYNC"
        private const val API_KEY_PREFIX = "cttak_"
        private const val API_KEY_PREFIX_LENGTH = 12
    }

    /** Test seam; production uses the credential-store backed [PasswordSyncKeyVault]. */
    internal var vault: SyncKeyVault = PasswordSyncKeyVault

    /** Test seam; production resolves the IDE installation identifier. */
    internal var deviceIdProvider: () -> String = { UserManager.getUserId() }

    /**
     * Signs in with email/password and binds a freshly created SYNC-scoped API key.
     */
    fun bindWithCredentials(email: String, password: String): SyncResult<Unit> {
        val login = apiService.login(email, password, deviceIdProvider())
        if (login is SyncResult.Failure) return login
        val accessToken = (login as SyncResult.Success).data.accessToken
            ?: return SyncResult.Failure(
                SyncError(SyncErrorKind.UNKNOWN, message = "Login response did not contain an access token"),
            )

        val create = apiService.createApiKey(accessToken, keyName(), listOf(SYNC_SCOPE))
        if (create is SyncResult.Failure) return create
        val rawKey = (create as SyncResult.Success).data.rawKey
            ?: return SyncResult.Failure(
                SyncError(SyncErrorKind.UNKNOWN, message = "API key creation response did not contain a raw key"),
            )

        vault.save(rawKey)
        settings.apiKeyPrefix = ((create as SyncResult.Success).data.apiKey?.keyPrefix ?: rawKey.take(API_KEY_PREFIX_LENGTH))
        settings.syncEnabled = true
        return SyncResult.Success(Unit)
    }

    /**
     * Binds an API key created in the web console (manual paste fallback).
     */
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

    /**
     * Removes the stored key and resets the binding state.
     */
    fun unbind() {
        vault.clear()
        settings.apiKeyPrefix = null
        settings.syncEnabled = false
    }

    fun getApiKey(): String? = vault.load()

    fun isBound(): Boolean = vault.load() != null

    private fun keyName(): String {
        val host = runCatching { InetAddress.getLocalHost().hostName }.getOrNull() ?: "this device"
        return "IntelliJ IDEA - $host"
    }
}
