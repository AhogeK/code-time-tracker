package com.ahogek.codetimetracker.service.sync

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SyncApiKeyManagerTest {

    private lateinit var apiService: FakeSyncApiService
    private lateinit var settings: SyncSettingsState
    private lateinit var vault: InMemorySyncKeyVault
    private lateinit var manager: SyncApiKeyManager

    @BeforeEach
    fun setUp() {
        apiService = FakeSyncApiService()
        settings = SyncSettingsState()
        vault = InMemorySyncKeyVault()
        manager = SyncApiKeyManager(apiService, settings)
        manager.vault = vault
        manager.deviceIdProvider = { "test-device-id" }
    }

    @Test
    fun `should bind with credentials when login and key creation succeed`() {
        val result = manager.bindWithCredentials("user@example.com", "password")

        assertThat(result).isInstanceOf(SyncResult.Success::class.java)
        assertThat(apiService.loginCalls).isEqualTo(1)
        assertThat(apiService.createCalls).isEqualTo(1)
        assertThat(apiService.lastDeviceId).isNotBlank()
        assertThat(apiService.lastScopes).containsExactly("SYNC")
        assertThat(apiService.lastKeyName).startsWith("IntelliJ IDEA - ")
        assertThat(vault.stored).isEqualTo("cttak_test_raw_key")
        assertThat(settings.apiKeyPrefix).isEqualTo("cttak_test_r")
        assertThat(settings.syncEnabled).isTrue()
    }

    @Test
    fun `should not bind anything when login fails`() {
        apiService.loginResult = SyncResult.Failure(SyncError(SyncErrorKind.INVALID_CREDENTIALS, httpStatus = 401, code = "AUTH_001"))

        val result = manager.bindWithCredentials("user@example.com", "wrong")

        assertThat(result).isInstanceOf(SyncResult.Failure::class.java)
        assertThat((result as SyncResult.Failure).error.kind).isEqualTo(SyncErrorKind.INVALID_CREDENTIALS)
        assertThat(apiService.createCalls).isEqualTo(0)
        assertThat(vault.stored).isNull()
        assertThat(settings.syncEnabled).isFalse()
    }

    @Test
    fun `should not bind anything when key creation fails`() {
        apiService.createResult = SyncResult.Failure(SyncError(SyncErrorKind.SCOPE_DENIED, httpStatus = 403, code = "AUTH_020"))

        val result = manager.bindWithCredentials("user@example.com", "password")

        assertThat(result).isInstanceOf(SyncResult.Failure::class.java)
        assertThat((result as SyncResult.Failure).error.kind).isEqualTo(SyncErrorKind.SCOPE_DENIED)
        assertThat(vault.stored).isNull()
        assertThat(settings.syncEnabled).isFalse()
    }

    @Test
    fun `should fail when login response lacks an access token`() {
        apiService.loginResult = SyncResult.Success(LoginResponse(accessToken = null))

        val result = manager.bindWithCredentials("user@example.com", "password")

        assertThat(result).isInstanceOf(SyncResult.Failure::class.java)
        assertThat((result as SyncResult.Failure).error.kind).isEqualTo(SyncErrorKind.UNKNOWN)
        assertThat(apiService.createCalls).isEqualTo(0)
        assertThat(vault.stored).isNull()
    }

    @Test
    fun `should fail when key creation response lacks the raw key`() {
        apiService.createResult = SyncResult.Success(CreateApiKeyResponse(rawKey = null))

        val result = manager.bindWithCredentials("user@example.com", "password")

        assertThat(result).isInstanceOf(SyncResult.Failure::class.java)
        assertThat((result as SyncResult.Failure).error.kind).isEqualTo(SyncErrorKind.UNKNOWN)
        assertThat(vault.stored).isNull()
    }

    @Test
    fun `should bind a manually pasted key and trim whitespace`() {
        val result = manager.bindWithManualKey("  cttak_manual_key  ")

        assertThat(result).isInstanceOf(SyncResult.Success::class.java)
        assertThat(vault.stored).isEqualTo("cttak_manual_key")
        assertThat(settings.apiKeyPrefix).isEqualTo("cttak_manual")
        assertThat(settings.syncEnabled).isTrue()
        assertThat(manager.isBound()).isTrue()
        assertThat(manager.getApiKey()).isEqualTo("cttak_manual_key")
    }


    @Test
    fun `should reject a manually pasted key without the cttak prefix`() {
        val result = manager.bindWithManualKey("not-an-api-key")

        assertThat(result).isInstanceOf(SyncResult.Failure::class.java)
        assertThat((result as SyncResult.Failure).error.kind).isEqualTo(SyncErrorKind.VALIDATION_ERROR)
        assertThat((result as SyncResult.Failure).error.toUserMessage()).contains("cttak_")
        assertThat(vault.stored).isNull()
        assertThat(manager.isBound()).isFalse()
    }

    @Test
    fun `should unbind and reset settings`() {
        manager.bindWithManualKey("cttak_key")
        assertThat(manager.isBound()).isTrue()

        manager.unbind()

        assertThat(vault.stored).isNull()
        assertThat(settings.apiKeyPrefix).isNull()
        assertThat(settings.syncEnabled).isFalse()
        assertThat(manager.isBound()).isFalse()
    }

    @Test
    fun `should fall back to raw key prefix when api key metadata is missing`() {
        apiService.createResult = SyncResult.Success(
            CreateApiKeyResponse(rawKey = "cttak_without_metadata", apiKey = null),
        )

        val result = manager.bindWithCredentials("user@example.com", "password")

        assertThat(result).isInstanceOf(SyncResult.Success::class.java)
        assertThat(settings.apiKeyPrefix).isEqualTo("cttak_withou")
    }

    private class FakeSyncApiService : SyncApiService {
        var loginResult: SyncResult<LoginResponse> = SyncResult.Success(LoginResponse(accessToken = "jwt-token"))
        var createResult: SyncResult<CreateApiKeyResponse> =
            SyncResult.Success(CreateApiKeyResponse(rawKey = "cttak_test_raw_key", apiKey = ApiKeyResponse(keyPrefix = "cttak_test_r")))
        var loginCalls = 0
        var createCalls = 0
        var lastDeviceId: String? = null
        var lastScopes: List<String>? = null
        var lastKeyName: String? = null

        override fun login(email: String, password: String, deviceId: String): SyncResult<LoginResponse> {
            loginCalls++
            lastDeviceId = deviceId
            return loginResult
        }

        override fun createApiKey(accessToken: String, name: String, scopes: List<String>): SyncResult<CreateApiKeyResponse> {
            createCalls++
            lastKeyName = name
            lastScopes = scopes
            return createResult
        }

        override fun pingServer(): SyncResult<Unit> = SyncResult.Success(Unit)
    }

    private class InMemorySyncKeyVault : SyncKeyVault {
        var stored: String? = null
        override fun save(rawKey: String) {
            stored = rawKey
        }

        override fun load(): String? = stored

        override fun clear() {
            stored = null
        }
    }
}
