package com.ahogek.codetimetracker.service.sync

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service

/**
 * Concrete [SyncApiService] mapping endpoint semantics onto the ctt-server REST
 * contract verified against the backend source.
 *
 * @author AhogeK ahogek@gmail.com
 * @since 2026-08-26
 */
@Service(Service.Level.APP)
class SyncApiServiceImpl(private val client: SyncHttpClient) : SyncApiService {
    /**
     * Platform-container entry point: the service container only supports
     * parameterless constructors, so dependencies are resolved via [ApplicationManager].
     */
    constructor() : this(ApplicationManager.getApplication().getService(SyncHttpClient::class.java))

    override fun pingServer(): SyncResult<Unit> {
        val result = client.execute(
            SyncRequest(method = "GET", path = "/api/v1/auth/api-keys"),
            com.google.gson.JsonElement::class.java,
        )
        return when (result) {
            is SyncResult.Success -> SyncResult.Success(Unit)
            is SyncResult.Failure ->
                if (result.error.kind == SyncErrorKind.NETWORK_ERROR || result.error.kind == SyncErrorKind.TIMEOUT) {
                    result
                } else {
                    SyncResult.Success(Unit)
                }
        }
    }

    override fun listDevices(apiKey: String): SyncResult<List<DeviceResponse>> =
        client.execute(
            SyncRequest(method = "GET", path = "/api/v1/devices", bearerToken = apiKey),
            object : com.google.gson.reflect.TypeToken<List<DeviceResponse>>() {}.type,
        )

    override fun registerDevice(request: RegisterDeviceRequest, apiKey: String): SyncResult<DeviceResponse> =
        client.execute(
            SyncRequest(method = "POST", path = "/api/v1/devices", body = request, bearerToken = apiKey),
            DeviceResponse::class.java,
        )

    override fun pull(request: SyncPullRequest, apiKey: String): SyncResult<SyncPullResponse> =
        client.execute(
            SyncRequest(method = "POST", path = "/api/v1/sync/pull", body = request, bearerToken = apiKey),
            SyncPullResponse::class.java,
        )

    override fun push(request: SyncPushRequest, apiKey: String): SyncResult<SyncPushResponse> =
        client.execute(
            SyncRequest(method = "POST", path = "/api/v1/sync/push", body = request, bearerToken = apiKey),
            SyncPushResponse::class.java,
        )

    override fun currentUser(apiKey: String): SyncResult<CurrentUserResponse> =
        client.execute(
            SyncRequest(method = "GET", path = "/api/v1/users/me", bearerToken = apiKey),
            CurrentUserResponse::class.java,
        )
}
