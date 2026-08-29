package com.ahogek.codetimetracker.service.sync

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class SyncErrorMapperTest {

    @Test
    fun `should map every known error code to its kind`() {
        assertThat(SyncErrorMapper.map(401, "AUTH_001")).isEqualTo(SyncErrorKind.INVALID_CREDENTIALS)
        assertThat(SyncErrorMapper.map(401, "AUTH_002")).isEqualTo(SyncErrorKind.TOKEN_EXPIRED)
        assertThat(SyncErrorMapper.map(401, "AUTH_003")).isEqualTo(SyncErrorKind.TOKEN_INVALID)
        assertThat(SyncErrorMapper.map(401, "AUTH_010")).isEqualTo(SyncErrorKind.API_KEY_INVALID)
        assertThat(SyncErrorMapper.map(401, "AUTH_011")).isEqualTo(SyncErrorKind.API_KEY_EXPIRED)
        assertThat(SyncErrorMapper.map(401, "AUTH_021")).isEqualTo(SyncErrorKind.API_KEY_MALFORMED)
        assertThat(SyncErrorMapper.map(403, "AUTH_004")).isEqualTo(SyncErrorKind.ACCOUNT_RESTRICTED)
        assertThat(SyncErrorMapper.map(403, "AUTH_005")).isEqualTo(SyncErrorKind.ACCOUNT_RESTRICTED)
        assertThat(SyncErrorMapper.map(403, "AUTH_006")).isEqualTo(SyncErrorKind.ACCOUNT_RESTRICTED)
        assertThat(SyncErrorMapper.map(403, "AUTH_022")).isEqualTo(SyncErrorKind.ACCOUNT_RESTRICTED)
        assertThat(SyncErrorMapper.map(403, "AUTH_019")).isEqualTo(SyncErrorKind.TERMS_REQUIRED)
        assertThat(SyncErrorMapper.map(403, "AUTH_012")).isEqualTo(SyncErrorKind.API_KEY_REVOKED)
        assertThat(SyncErrorMapper.map(403, "AUTH_020")).isEqualTo(SyncErrorKind.SCOPE_DENIED)
        assertThat(SyncErrorMapper.map(404, "COMMON_002")).isEqualTo(SyncErrorKind.DEVICE_NOT_FOUND)
        assertThat(SyncErrorMapper.map(429, "RATE_LIMIT_001")).isEqualTo(SyncErrorKind.RATE_LIMITED)
    }

    @Test
    fun `should treat any 429 as rate limited regardless of error code`() {
        assertThat(SyncErrorMapper.map(429, "COMMON_002")).isEqualTo(SyncErrorKind.RATE_LIMITED)
        assertThat(SyncErrorMapper.map(429, null)).isEqualTo(SyncErrorKind.RATE_LIMITED)
    }

    @Test
    fun `should map server errors by status alone`() {
        assertThat(SyncErrorMapper.map(500, "COMMON_002")).isEqualTo(SyncErrorKind.SERVER_ERROR)
        assertThat(SyncErrorMapper.map(503, null)).isEqualTo(SyncErrorKind.SERVER_ERROR)
    }

    @Test
    fun `should map unknown 4xx codes to validation error`() {
        assertThat(SyncErrorMapper.map(400, "COMMON_003")).isEqualTo(SyncErrorKind.VALIDATION_ERROR)
        assertThat(SyncErrorMapper.map(401, null)).isEqualTo(SyncErrorKind.VALIDATION_ERROR)
        assertThat(SyncErrorMapper.map(409, "AUTH_024")).isEqualTo(SyncErrorKind.VALIDATION_ERROR)
    }

    @Test
    fun `should map unexpected statuses to unknown`() {
        assertThat(SyncErrorMapper.map(299, null)).isEqualTo(SyncErrorKind.UNKNOWN)
        assertThat(SyncErrorMapper.map(399, null)).isEqualTo(SyncErrorKind.UNKNOWN)
    }

    @Test
    fun `should prefer header retry-after over body`() {
        val result = SyncErrorMapper.parseRetryAfter("5", "2099-01-01T00:00:00Z")
        assertThat(result).isEqualTo(5L)
    }

    @Test
    fun `should fall back to body retry-after when header is absent or malformed`() {
        val future = java.time.Instant.now().plusSeconds(120).toString()
        assertThat(SyncErrorMapper.parseRetryAfter(null, future)).isNotNull
        assertThat(SyncErrorMapper.parseRetryAfter("abc", future)).isNotNull
        assertThat(SyncErrorMapper.parseRetryAfter("-3", future)).isNotNull
    }

    @Test
    fun `should return null when no retry-after source is parseable`() {
        assertThat(SyncErrorMapper.parseRetryAfter(null, null)).isNull()
        assertThat(SyncErrorMapper.parseRetryAfter("", "not-a-date")).isNull()
        assertThat(SyncErrorMapper.parseRetryAfter("abc", "")).isNull()
    }


    @Test
    fun `should prompt to re-bind when the api key is revoked`() {
        val revoked = SyncError(
            SyncErrorKind.API_KEY_REVOKED,
            httpStatus = 403,
            code = "AUTH_012",
        )
        assertThat(revoked.toUserMessage()).contains("revoked")
        assertThat(revoked.toUserMessage()).contains("new API key")
    }

    @Test
    fun `should prefer the concrete message over the kind-based default`() {
        val withMessage = SyncError(SyncErrorKind.VALIDATION_ERROR, message = "API key must not be empty")
        assertThat(withMessage.toUserMessage()).isEqualTo("API key must not be empty")

        val withoutMessage = SyncError(SyncErrorKind.API_KEY_INVALID)
        assertThat(withoutMessage.toUserMessage()).contains("API key is invalid")
    }
}
