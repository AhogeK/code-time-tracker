package com.ahogek.codetimetracker.service.sync

/**
 * Request/response DTOs mirroring the ctt-server REST contract
 * (https://github.com/AhogeK/ctt-server). All fields carry defaults so Gson can
 * instantiate them via the no-arg constructor; unknown JSON fields are ignored.
 *
 * @author AhogeK ahogek@gmail.com
 * @since 2026-08-26
 */

/** POST /api/v1/auth/login response body (also used as a generic parsed-response DTO in tests). */
data class LoginResponse(
    val userId: String? = null,
    val accessToken: String? = null,
    val refreshToken: String? = null,
    val expiresIn: Long = 0L,
    val tokenType: String? = null,
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
