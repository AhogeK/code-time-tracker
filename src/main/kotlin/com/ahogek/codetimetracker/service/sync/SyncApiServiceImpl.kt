package com.ahogek.codetimetracker.service.sync

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

    override fun login(email: String, password: String, deviceId: String): SyncResult<LoginResponse> =
        client.execute(
            SyncRequest(
                method = "POST",
                path = "/api/v1/auth/login",
                body = LoginRequest(email = email, password = password, deviceId = deviceId),
            ),
            LoginResponse::class.java,
        )

    override fun createApiKey(
        accessToken: String,
        name: String,
        scopes: List<String>,
    ): SyncResult<CreateApiKeyResponse> =
        client.execute(
            SyncRequest(
                method = "POST",
                path = "/api/v1/auth/api-keys",
                body = CreateApiKeyRequest(name = name, scopes = scopes),
                bearerToken = accessToken,
            ),
            CreateApiKeyResponse::class.java,
        )

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
}
