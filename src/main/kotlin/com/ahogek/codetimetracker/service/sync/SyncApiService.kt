package com.ahogek.codetimetracker.service.sync

/**
 * High-level ctt-server endpoints used by the plugin's sync feature.
 *
 * @author AhogeK ahogek@gmail.com
 * @since 2026-08-26
 */
interface SyncApiService {

    /**
     * Signs in with email/password. The [deviceId] is bound to the account on the
     * server, so the plugin passes its installation identifier ([com.ahogek.codetimetracker.user.UserManager.getUserId]).
     */
    fun login(email: String, password: String, deviceId: String): SyncResult<LoginResponse>

    /**
     * Creates an API key with the given [scopes] (e.g. ["SYNC"]) using a JWT
     * [accessToken] obtained from [login].
     */
    fun createApiKey(accessToken: String, name: String, scopes: List<String>): SyncResult<CreateApiKeyResponse>

    /**
     * Checks server reachability without credentials. Any HTTP response (including
     * 401/403) counts as reachable; only transport failures are reported.
     */
    fun pingServer(): SyncResult<Unit>
}
