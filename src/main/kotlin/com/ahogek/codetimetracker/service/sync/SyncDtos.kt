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

/** POST /api/v1/sync/pull request body: resume from [lastPulledChangeId] for [deviceId]. */
data class SyncPullRequest(
    val deviceId: String? = null,
    val lastPulledChangeId: Long = 0L,
)

/** POST /api/v1/sync/pull response body: one page of changes plus the next cursor. */
data class SyncPullResponse(
    val changes: List<SyncChangeDto> = emptyList(),
    val nextCursor: Long = 0L,
    /**
     * True when the server has more changes after this page (ctt-server 0.62.0+).
     * Defaults to false so responses from older servers parse as a single final page.
     */
    val hasMore: Boolean = false,
)

/** POST /api/v1/sync/push request body: device-originated session states, atomic batch. */
data class SyncPushRequest(
    val deviceId: String? = null,
    val sessions: List<SyncSessionDto> = emptyList(),
)

/** POST /api/v1/sync/push response body: highest change id recorded (next pull cursor). */
data class SyncPushResponse(
    val nextCursor: Long = 0L,
)

/**
 * A coding session state submitted by the client, matching the server SyncSessionDto
 * record field-for-field (sessionUuid/projectName/language/startTime/endTime/
 * clientModifiedAt/clientVersion/deleted/ideName/ideVersion). Timestamps are ISO-8601
 * instants. The IDE fields are per-session facts recorded when the session started -
 * they must never be taken from the IDE that happens to be running the sync.
 */
data class SyncSessionDto(
    val sessionUuid: String? = null,
    val projectName: String? = null,
    val language: String? = null,
    val startTime: String? = null,
    val endTime: String? = null,
    val clientModifiedAt: String? = null,
    val clientVersion: Int = 0,
    val deleted: Boolean = false,
    val ideName: String? = null,
    val ideVersion: String? = null,
)

/** Operation applied to a synced session. */
enum class ChangeOp {
    UPSERT,
    DELETE,
}

/**
 * A single change-log entry with the winning session snapshot, matching the server
 * SyncChangeDto record. [sessionUuid] is the client contract session identifier (the
 * server returns it in pull responses so the client can match changes to local rows);
 * [sessionId] is the server-side primary key and is not used for local matching.
 *
 * [ideName]/[ideVersion] are optional per-session IDE facts. The server does not send
 * them yet; when absent the applier falls back to local values, so both directions stay
 * compatible.
 */
data class SyncChangeDto(
    val changeId: Long = 0L,
    val sessionId: String? = null,
    val sessionUuid: String? = null,
    val op: ChangeOp? = null,
    val serverVersion: Long = 0L,
    val happenedAt: String? = null,
    val projectName: String? = null,
    val language: String? = null,
    val startTime: String? = null,
    val endTime: String? = null,
    val clientModifiedAt: String? = null,
    val clientVersion: Int = 0,
    val deleted: Boolean = false,
    val ideName: String? = null,
    val ideVersion: String? = null,
)

/** GET /api/v1/devices response item (a registered user device). */
data class DeviceResponse(
    val id: String? = null,
    val deviceName: String? = null,
    val platform: String? = null,
    val ideName: String? = null,
    val ideVersion: String? = null,
    val appVersion: String? = null,
    val createdAt: String? = null,
    val lastSeenAt: String? = null,
    val revokedAt: String? = null,
)

/**
 * POST /api/v1/devices request body: registers or updates the client device.
 * The server upserts by [deviceId] for the authenticated user and binds the device
 * to the current API key (key <-> device).
 */
data class RegisterDeviceRequest(
    val deviceId: String? = null,
    val deviceName: String? = null,
    val platform: String? = null,
    val ideName: String? = null,
    val ideVersion: String? = null,
    val appVersion: String? = null,
)

/**
 * GET /api/v1/users/me response payload: the current authenticated user. The plugin
 * uses [id] as the account-scope owner for local session isolation (statistics only
 * show the currently bound user's data); the remaining fields are ignored.
 */
data class CurrentUserResponse(
    val id: String? = null,
    val email: String? = null,
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
