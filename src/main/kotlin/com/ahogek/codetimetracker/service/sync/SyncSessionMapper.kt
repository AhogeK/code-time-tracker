package com.ahogek.codetimetracker.service.sync

import com.ahogek.codetimetracker.model.CodingSession
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Maps the local [CodingSession] model onto the server sync contract
 * ([SyncSessionDto]). Field names follow the ctt-server @Schema exactly; the local
 * display-only fields ([CodingSession.platform]/[CodingSession.ideName]) are not
 * sent (device identity is carried by the registered device). Timestamps are
 * converted from local date-times to ISO-8601 instants in the system zone.
 *
 * [SyncSessionDto.deleted] is always false: the plugin does not soft-delete
 * sessions (the published plugin's database has no soft-delete contract), so
 * deletion is not part of the sync payload.
 *
 * @author AhogeK ahogek@gmail.com
 * @since 2026-08-27
 */
object SyncSessionMapper {

    /**
     * Converts a local [CodingSession] into its server-contract representation.
     * Timestamps become ISO-8601 instants in the system zone; [SyncSessionDto.deleted]
     * is always false (the plugin has no soft-delete contract).
     */
    fun toSyncDto(session: CodingSession): SyncSessionDto = SyncSessionDto(
        sessionUuid = session.sessionUuid,
        projectName = session.projectName,
        language = session.language,
        startTime = session.startTime.toInstantString(),
        endTime = session.endTime.toInstantString(),
        clientModifiedAt = session.lastModified.toInstantString(),
        clientVersion = session.syncVersion,
        deleted = false,
    )

    private fun LocalDateTime.toInstantString(): String =
        atZone(ZoneId.systemDefault()).toInstant().toString()
}
