package com.ahogek.codetimetracker.service.sync

import com.google.gson.JsonElement
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test

/**
 * Integration tests against a locally running ctt-server (dev profile).
 *
 * These tests verify the real HTTP contract end to end: context path, unified
 * response envelope parsing and error-code mapping. They are skipped automatically
 * when the backend is not reachable. Credential-requiring flows (register, login,
 * key creation) are intentionally excluded: they create throwaway accounts and are
 * covered by the manual smoke script `scripts/verify-sync-flow.sh` instead.
 *
 * @author AhogeK ahogek@gmail.com
 * @since 2026-08-26
 */
class SyncApiIntegrationTest {

    private val baseUrl: String = System.getenv("CTT_API") ?: "http://localhost:8080/ctt-server"

    @Test
    fun `should reach the local ctt-server`() {
        val api = newApi()
        assumeTrue(api.pingServer() is SyncResult.Success, "ctt-server not reachable at $baseUrl")

        assertThat(api.pingServer()).isInstanceOf(SyncResult.Success::class.java)
    }

    @Test
    fun `should parse the real error envelope and map the auth error code`() {
        val client = newClient()
        assumeTrue(newApi().pingServer() is SyncResult.Success, "ctt-server not reachable at $baseUrl")

        val result = client.execute(SyncRequest(method = "GET", path = "/api/v1/auth/api-keys"), JsonElement::class.java)

        assertThat(result).isInstanceOf(SyncResult.Failure::class.java)
        val error = (result as SyncResult.Failure).error
        assertThat(error.kind).isNotEqualTo(SyncErrorKind.NETWORK_ERROR)
        assertThat(error.kind).isNotEqualTo(SyncErrorKind.TIMEOUT)
        assertThat(error.httpStatus).isEqualTo(401)
        assertThat(error.code).isNotNull
    }

    private fun newSettings(): SyncSettingsState = SyncSettingsState().apply {
        serverUrl = baseUrl
    }

    private fun newClient(): SyncHttpClient = SyncHttpClient(newSettings())

    private fun newApi(): SyncApiService = SyncApiServiceImpl(newClient())
}
