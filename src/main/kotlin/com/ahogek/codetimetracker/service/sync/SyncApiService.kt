package com.ahogek.codetimetracker.service.sync

/**
 * High-level ctt-server endpoints used by the plugin's sync feature.
 *
 * @author AhogeK ahogek@gmail.com
 * @since 2026-08-26
 */
interface SyncApiService {

    /**
     * Checks server reachability without credentials. Any HTTP response (including
     * 401/403) counts as reachable; only transport failures are reported.
     */
    fun pingServer(): SyncResult<Unit>

    /**
     * Lists the registered devices for the authenticated user, used to verify the
     * current device is registered before syncing. [apiKey] authenticates with the
     * SYNC-scoped API key.
     */
    fun listDevices(apiKey: String): SyncResult<List<DeviceResponse>>

    /**
     * Registers (or updates) the client device so it can sync. The server upserts by
     * [RegisterDeviceRequest.deviceId] for the authenticated user and binds the device
     * to the current API key. Idempotent; safe to call on every bind. [apiKey]
     * authenticates with the SYNC-scoped API key.
     */
    fun registerDevice(request: RegisterDeviceRequest, apiKey: String): SyncResult<DeviceResponse>

    /**
     * Pulls the changes recorded after [request.lastPulledChangeId] for [request.deviceId].
     * The response carries the changes to apply plus the next pull cursor. [apiKey]
     * authenticates with the SYNC-scoped API key.
     */
    fun pull(request: SyncPullRequest, apiKey: String): SyncResult<SyncPullResponse>

    /**
     * Pushes a batch of session states for [request.deviceId] under server-side LWW
     * conflict resolution; the batch is applied atomically by the server. [apiKey]
     * authenticates with the SYNC-scoped API key.
     */
    fun push(request: SyncPushRequest, apiKey: String): SyncResult<SyncPushResponse>

    /**
     * Returns the current authenticated user (GET /api/v1/users/me). The returned
     * [CurrentUserResponse.id] is the account scope used for local data isolation.
     * [apiKey] authenticates with any valid API key (SYNC scope suffices).
     */
    fun currentUser(apiKey: String): SyncResult<CurrentUserResponse>
}
