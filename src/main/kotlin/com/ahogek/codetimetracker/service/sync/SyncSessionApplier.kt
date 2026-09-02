package com.ahogek.codetimetracker.service.sync

import com.ahogek.codetimetracker.database.SessionRepository
import com.ahogek.codetimetracker.model.CodingSession
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Applies pull changes ([SyncChangeDto] entries from `POST /api/v1/sync/pull`) onto the
 * local database.
 *
 * <p>Rules (mirroring the client side of the server's LWW contract):
 *
 * <ul>
 *   <li>[ChangeOp.UPSERT]: a session the local store has never seen is created; a clean
 *       local row (already synced) is overwritten in place; a dirty local row (pending
 *       local changes, `isSynced == false`) is left untouched so the push path submits it
 *       and the server decides.
 *   <li>[ChangeOp.DELETE]: a clean local row is soft-deleted; a dirty row is kept (the
 *       local edit wins until the server rules on the next push); an absent row is a
 *       no-op.
 *   <li>Changes without a [SyncChangeDto.sessionUuid] are skipped (server contract field
 *       not yet populated; the client cannot match them to local rows).
 * </ul>
 *
 * A server upsert lifts a local soft-delete tombstone (`is_deleted = 0` on conflict):
 * the change snapshot is the server-authoritative live state, whether it arrives
 * before a same-page delete+re-upsert sequence or in a later page.
 *
 * @author AhogeK ahogek@gmail.com
 * @since 2026-08-29
 */
class SyncSessionApplier(private val repository: SessionRepository) {

    /**
     * Applies [changes] for [userId]. [localPlatform] and [localIdeName] are used only
     * when a change creates a brand-new local row: the server contract carries no
     * platform/IDE fields (device identity lives on the registered device), so the
     * values seen by this device are recorded instead.
     */
    fun apply(
        changes: List<SyncChangeDto>,
        userId: String,
        localPlatform: String,
        localIdeName: String,
        ownerUserId: String? = null,
    ) {
        val applicable = changes.filter { it.sessionUuid != null }
        if (applicable.isEmpty()) return

        val existing = repository.findBySessionUuids(applicable.map { it.sessionUuid!! })
        val now = LocalDateTime.now()

        // Upserts are buffered into consecutive runs and written with one batched
        // statement; a DELETE flushes the buffer first so operations land in the
        // change-log order (an upsert following a delete of the same session must not
        // be overwritten by the buffered row). Batch failures propagate: the caller
        // persists the pull cursor only after a successful apply.
        val pendingUpserts = mutableListOf<CodingSession>()
        fun flushUpserts() {
            if (pendingUpserts.isNotEmpty()) {
                repository.upsertSyncedSessions(pendingUpserts.toList(), ownerUserId)
                pendingUpserts.clear()
            }
        }

        for (change in applicable) {
            val sessionUuid = change.sessionUuid!!
            val existingRow = existing[sessionUuid]
            when (change.op) {
                ChangeOp.UPSERT -> {
                    if (existingRow == null) {
                        pendingUpserts += toNewSession(change, userId, localPlatform, localIdeName, now)
                    } else if (existingRow.isSynced) {
                        pendingUpserts += toUpdatedSession(change, existingRow, now)
                    }
                }
                ChangeOp.DELETE -> {
                    if (existingRow != null && existingRow.isSynced) {
                        flushUpserts()
                        repository.markDeleted(sessionUuid)
                    }
                }
                null -> Unit
            }
        }
        flushUpserts()
    }

    private fun toNewSession(
        change: SyncChangeDto,
        userId: String,
        localPlatform: String,
        localIdeName: String,
        now: LocalDateTime,
    ): CodingSession = CodingSession(
        sessionUuid = change.sessionUuid!!,
        userId = userId,
        projectName = change.projectName.orEmpty(),
        language = change.language.orEmpty(),
        platform = localPlatform,
        ideName = localIdeName,
        startTime = change.startTime.toLocalDateTimeOr(now),
        endTime = change.endTime.toLocalDateTimeOr(now),
        lastModified = change.clientModifiedAt.toLocalDateTimeOr(now),
        isSynced = true,
        syncedAt = now,
        syncVersion = change.clientVersion,
    )

    private fun toUpdatedSession(
        change: SyncChangeDto,
        existing: CodingSession,
        now: LocalDateTime,
    ): CodingSession = CodingSession(
        sessionUuid = change.sessionUuid!!,
        userId = existing.userId,
        projectName = change.projectName.orEmpty(),
        language = change.language.orEmpty(),
        platform = existing.platform,
        ideName = existing.ideName,
        startTime = change.startTime.toLocalDateTimeOr(existing.startTime),
        endTime = change.endTime.toLocalDateTimeOr(existing.endTime),
        lastModified = change.clientModifiedAt.toLocalDateTimeOr(existing.lastModified),
        isSynced = true,
        syncedAt = now,
        syncVersion = change.clientVersion,
    )

    /**
     * Parses an ISO-8601 instant from the change snapshot; an unparsable or missing
     * timestamp falls back to the local row's current value so a malformed remote
     * change can never corrupt the local last-write-wins ordering.
     */
    private fun String?.toLocalDateTimeOr(fallback: LocalDateTime): LocalDateTime =
        this?.let { runCatching { Instant.parse(it).atZone(ZoneId.systemDefault()).toLocalDateTime() }.getOrNull() }
            ?: fallback
}
