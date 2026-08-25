package com.ahogek.codetimetracker.service.sync

import java.time.Instant

/**
 * Categorised failure kinds derived from HTTP status codes and ctt-server error codes.
 *
 * @author AhogeK ahogek@gmail.com
 * @since 2026-08-26
 */
enum class SyncErrorKind {
    /** 401 AUTH_001 - invalid login credentials */
    INVALID_CREDENTIALS,

    /** 401 AUTH_002 - JWT access token expired */
    TOKEN_EXPIRED,

    /** 401 AUTH_003 - JWT access token invalid */
    TOKEN_INVALID,

    /** 401 AUTH_010 - API key does not exist or does not belong to the caller */
    API_KEY_INVALID,

    /** 401 AUTH_011 - API key expired */
    API_KEY_EXPIRED,

    /** 401 AUTH_021 - Authorization header malformed */
    API_KEY_MALFORMED,

    /** 403 AUTH_004/005/006/022 - account locked, suspended, unverified or deactivated */
    ACCOUNT_RESTRICTED,

    /** 403 AUTH_019 - terms of service must be re-accepted */
    TERMS_REQUIRED,

    /** 403 AUTH_012 - API key revoked */
    API_KEY_REVOKED,

    /** 403 AUTH_020 - API key missing required scope */
    SCOPE_DENIED,

    /** 404 COMMON_002 - device not found or access denied */
    DEVICE_NOT_FOUND,

    /** 404 - any other missing resource */
    NOT_FOUND,

    /** 4xx - request rejected by the server (validation, security or unknown code) */
    VALIDATION_ERROR,

    /** 429 - rate limited (RATE_LIMIT_001, or COMMON_002 emitted by auth endpoints) */
    RATE_LIMITED,

    /** 5xx - server-side failure */
    SERVER_ERROR,

    /** Transport failure - server unreachable */
    NETWORK_ERROR,

    /** Transport failure - request timed out */
    TIMEOUT,

    /** Anything not covered above */
    UNKNOWN,
}

/**
 * Normalised sync failure carrying both the categorised [kind] and the raw server facts
 * (HTTP status, ctt-server error code, message) for diagnostics and UI messages.
 *
 * @author AhogeK ahogek@gmail.com
 * @since 2026-08-26
 */
data class SyncError(
    val kind: SyncErrorKind,
    val httpStatus: Int? = null,
    val code: String? = null,
    val message: String? = null,
    val retryAfterSeconds: Long? = null,
) {
    /**
     * Human-readable, actionable message shown in the settings UI. A concrete
     * [message] (server-provided or client-side) takes precedence over the generic
     * kind-based default.
     */
    fun toUserMessage(): String = message ?: when (kind) {
        SyncErrorKind.INVALID_CREDENTIALS ->
            "Invalid email or password. Please check your credentials and try again."
        SyncErrorKind.TOKEN_EXPIRED, SyncErrorKind.TOKEN_INVALID ->
            "Session expired. Please sign in again."
        SyncErrorKind.API_KEY_INVALID ->
            "The API key is invalid. Please re-bind your API key."
        SyncErrorKind.API_KEY_EXPIRED ->
            "The API key has expired. Please create a new API key."
        SyncErrorKind.API_KEY_MALFORMED ->
            "The API key format is invalid. Please re-enter the key."
        SyncErrorKind.API_KEY_REVOKED ->
            "The API key has been revoked. Please create a new API key."
        SyncErrorKind.SCOPE_DENIED ->
            "The API key lacks the required scope (SYNC). Please create a key with the SYNC scope."
        SyncErrorKind.DEVICE_NOT_FOUND ->
            "No device binding was found for this account. Please sign in again to register this device."
        SyncErrorKind.ACCOUNT_RESTRICTED ->
            "The account is restricted (locked, suspended, unverified or deactivated). Please resolve it in the web console."
        SyncErrorKind.TERMS_REQUIRED ->
            "The terms of service must be re-accepted. Please sign in via the web console first."
        SyncErrorKind.VALIDATION_ERROR ->
            "The server rejected the request. Please check the entered values."
        SyncErrorKind.NOT_FOUND ->
            "The requested resource was not found. Please check the server address."
        SyncErrorKind.RATE_LIMITED ->
            "Rate limit exceeded. Please wait and try again later."
        SyncErrorKind.SERVER_ERROR ->
            "The server returned an error. Please try again later."
        SyncErrorKind.NETWORK_ERROR ->
            "Cannot reach the server. Please check the server address and your network connection."
        SyncErrorKind.TIMEOUT ->
            "The request timed out. Please try again."
        SyncErrorKind.UNKNOWN ->
            "An unexpected error occurred. Please try again."
    }
}

/**
 * Maps HTTP status codes and ctt-server error codes to [SyncErrorKind].
 *
 * The backend currently emits rate-limit failures both as RATE_LIMIT_001 and as
 * COMMON_002 on some auth endpoints, so 429 handling keys on the HTTP status code first
 * and only then consults the error code.
 *
 * @author AhogeK ahogek@gmail.com
 * @since 2026-08-26
 */
object SyncErrorMapper {

    private val errorCodeToKind: Map<String, SyncErrorKind> = mapOf(
        "AUTH_001" to SyncErrorKind.INVALID_CREDENTIALS,
        "AUTH_002" to SyncErrorKind.TOKEN_EXPIRED,
        "AUTH_003" to SyncErrorKind.TOKEN_INVALID,
        "AUTH_004" to SyncErrorKind.ACCOUNT_RESTRICTED,
        "AUTH_005" to SyncErrorKind.ACCOUNT_RESTRICTED,
        "AUTH_006" to SyncErrorKind.ACCOUNT_RESTRICTED,
        "AUTH_010" to SyncErrorKind.API_KEY_INVALID,
        "AUTH_011" to SyncErrorKind.API_KEY_EXPIRED,
        "AUTH_012" to SyncErrorKind.API_KEY_REVOKED,
        "AUTH_019" to SyncErrorKind.TERMS_REQUIRED,
        "AUTH_020" to SyncErrorKind.SCOPE_DENIED,
        "AUTH_021" to SyncErrorKind.API_KEY_MALFORMED,
        "AUTH_022" to SyncErrorKind.ACCOUNT_RESTRICTED,
        "COMMON_002" to SyncErrorKind.DEVICE_NOT_FOUND,
        "RATE_LIMIT_001" to SyncErrorKind.RATE_LIMITED,
    )

    /**
     * Maps an HTTP status and optional ctt-server error code to a [SyncErrorKind].
     * 429 and 5xx are decided by status alone; 4xx falls back to the error code table
     * and finally to [SyncErrorKind.VALIDATION_ERROR].
     */
    fun map(httpStatus: Int, code: String?): SyncErrorKind {
        if (httpStatus == 429) return SyncErrorKind.RATE_LIMITED
        if (httpStatus >= 500) return SyncErrorKind.SERVER_ERROR
        code?.let { errorCodeToKind[it]?.let { kind -> return kind } }
        return if (httpStatus in 400..499) SyncErrorKind.VALIDATION_ERROR else SyncErrorKind.UNKNOWN
    }

    /**
     * Resolves the retry delay for a rate-limited response. The Retry-After header
     * (delta-seconds) takes precedence; the response-body retryAfter (ISO-8601 instant)
     * is the fallback. Returns null when neither is present or parseable, and never
     * returns a negative delay.
     */
    fun parseRetryAfter(headerSeconds: String?, bodyRetryAfter: String?): Long? {
        headerSeconds?.trim()?.takeIf { it.isNotEmpty() }?.let {
            val seconds = it.toLongOrNull()
            if (seconds != null && seconds >= 0) return seconds
        }
        bodyRetryAfter?.trim()?.takeIf { it.isNotEmpty() }?.let {
            return runCatching { Instant.parse(it) }.getOrNull()
                ?.let { instant -> (instant.toEpochMilli() - System.currentTimeMillis()) / 1000 }
                ?.coerceAtLeast(0)
        }
        return null
    }
}
