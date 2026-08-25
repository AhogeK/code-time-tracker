package com.ahogek.codetimetracker.service.sync

/**
 * Request/response DTOs mirroring the ctt-server REST contract
 * (https://github.com/AhogeK/ctt-server). All fields carry defaults so Gson can
 * instantiate them via the no-arg constructor; unknown JSON fields are ignored.
 *
 * @author AhogeK ahogek@gmail.com
 * @since 2026-08-26
 */

/** POST /api/v1/auth/login request body. */
data class LoginRequest(
    val email: String? = null,
    val password: String? = null,
    val deviceId: String? = null,
    val captchaToken: String? = null,
)

/** POST /api/v1/auth/login response body. */
data class LoginResponse(
    val userId: String? = null,
    val accessToken: String? = null,
    val refreshToken: String? = null,
    val expiresIn: Long = 0L,
    val tokenType: String? = null,
)

/** POST /api/v1/auth/api-keys request body. */
data class CreateApiKeyRequest(
    val name: String? = null,
    val scopes: List<String> = emptyList(),
    val expiresAt: String? = null,
)

/** POST /api/v1/auth/api-keys response body; rawKey is exposed exactly once. */
data class CreateApiKeyResponse(
    val rawKey: String? = null,
    val apiKey: ApiKeyResponse? = null,
)

/** API key metadata snapshot (no secret material). */
data class ApiKeyResponse(
    val id: String? = null,
    val name: String? = null,
    val keyPrefix: String? = null,
    val scopes: List<String> = emptyList(),
    val lastUsedAt: String? = null,
    val expiresAt: String? = null,
    val revokedAt: String? = null,
    val createdAt: String? = null,
    val status: String? = null,
)

/** ctt-server unified response envelope: {success, message, data, timestamp}. */
data class RestApiEnvelope(
    val success: Boolean = false,
    val message: String? = null,
    val data: com.google.gson.JsonElement? = null,
)

/** ctt-server error payload carried inside the envelope `data` field. */
data class ErrorData(
    val code: String? = null,
    val message: String? = null,
    val details: List<ErrorDetail>? = null,
    val traceId: String? = null,
    val httpStatus: Int? = null,
    val retryAfter: String? = null,
)

/** Field-level validation detail inside [ErrorData]. */
data class ErrorDetail(
    val field: String? = null,
    val message: String? = null,
)
