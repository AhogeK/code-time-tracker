package com.ahogek.codetimetracker.service.sync

import com.ahogek.codetimetracker.model.CodingSession
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Maps the local [CodingSession] model onto the server sync contract
 * ([SyncSessionDto]). Field names follow the ctt-server @Schema exactly. The IDE
 * fields ([CodingSession.ideName]/[CodingSession.ideVersion]) travel with each
 * session, so statistics attribute a session to the IDE that produced it rather
 * than to whichever IDE last registered the device; [CodingSession.platform]
 * remains local-only. Timestamps are converted from local date-times to ISO-8601
 * instants in the system zone.
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
     * is always false (the plugin has no soft-delete contract). The IDE fields come
     * from the session itself, never from the device running this sync.
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
        ideName = session.ideName,
        ideVersion = session.ideVersion,
    )

    private fun LocalDateTime.toInstantString(): String =
        atZone(ZoneId.systemDefault()).toInstant().toString()
}
