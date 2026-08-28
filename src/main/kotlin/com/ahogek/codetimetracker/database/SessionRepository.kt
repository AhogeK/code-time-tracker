package com.ahogek.codetimetracker.database

import com.ahogek.codetimetracker.model.CodingSession
import com.ahogek.codetimetracker.model.SessionSummaryDTO
import com.ahogek.codetimetracker.user.UserManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*

class SessionRepository(private val connectionManager: ConnectionManager) {

    private val log = Logger.getInstance(SessionRepository::class.java)
    private val dateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME

    /**
     * 设置 PreparedStatement 中 session 的核心字段（第 3-8 位参数）
     */
    private fun java.sql.PreparedStatement.setSessionCoreParams(startIndex: Int, session: CodingSession) {
        setString(startIndex, session.projectName)
        setString(startIndex + 1, session.language)
        setString(startIndex + 2, session.platform)
        setString(startIndex + 3, session.ideName)
        setString(startIndex + 4, dateTimeFormatter.format(session.startTime))
        setString(startIndex + 5, dateTimeFormatter.format(session.endTime))
    }

    fun saveSessions(sessions: List<CodingSession>, onComplete: () -> Unit) {
        if (sessions.isEmpty()) {
            ApplicationManager.getApplication().invokeLater {
                onComplete()
            }
            return
        }

        connectionManager.databaseExecutor.execute {
            val sql = """
                INSERT INTO coding_sessions(
                    session_uuid, user_id, project_name, language, platform, ide_name,
                    start_time, end_time, last_modified, is_synced, synced_at, sync_version
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);
            """
            try {
                connectionManager.withConnection { conn ->
                    conn.autoCommit = false
                    val pstmt = conn.prepareStatement(sql)
                    val currentTimestamp = dateTimeFormatter.format(LocalDateTime.now())
                    val userId = UserManager.getUserId()

                    for (session in sessions) {
                        pstmt.setString(1, UUID.randomUUID().toString())
                        pstmt.setString(2, userId)
                        pstmt.setSessionCoreParams(3, session)
                        pstmt.setString(9, currentTimestamp)
                        pstmt.setInt(10, if (session.isSynced) 1 else 0)
                        pstmt.setString(11, session.syncedAt?.let { dateTimeFormatter.format(it) })
                        pstmt.setInt(12, session.syncVersion)
                        pstmt.addBatch()
                    }

                    pstmt.executeBatch()
                    conn.commit()
                    log.info("Successfully saved ${sessions.size} sessions to the database.")
                }
            } catch (e: Exception) {
                log.error("Failed to save sessions to the database.", e)
            } finally {
                ApplicationManager.getApplication().invokeLater {
                    onComplete()
                }
            }
        }
    }

    fun importSessions(sessions: List<CodingSession>): Int {
        if (sessions.isEmpty()) return 0

        val sql = """
        INSERT OR IGNORE INTO coding_sessions(
            session_uuid, user_id, project_name, language, platform, ide_name,
            start_time, end_time, last_modified, is_deleted
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
    """

        var importedCount = 0

        try {
            connectionManager.withConnection { conn ->
                conn.autoCommit = false
                conn.prepareStatement(sql).use { pstmt ->
                    for (session in sessions) {
                        pstmt.setString(1, session.sessionUuid)
                        pstmt.setString(2, session.userId)
                        pstmt.setSessionCoreParams(3, session)
                        pstmt.setString(9, dateTimeFormatter.format(session.lastModified))

                        val result = pstmt.executeUpdate()
                        if (result > 0) {
                            importedCount++
                        }
                    }
                    conn.commit()
                }
                log.info("Successfully imported $importedCount sessions")
            }
        } catch (e: Exception) {
            log.error("Failed to import sessions", e)
        }

        return importedCount
    }

    fun getSessions(startTime: LocalDateTime? = null, endTime: LocalDateTime? = null): List<CodingSession> {
        val conditions = mutableListOf("is_deleted = 0")
        if (startTime != null) {
            conditions.add("end_time > ?")
        }
        if (endTime != null) {
            conditions.add("start_time < ?")
        }

        val sql = """
            SELECT
                session_uuid, user_id, project_name, language, platform, ide_name, 
                start_time, end_time, last_modified, is_synced, synced_at, sync_version
            FROM coding_sessions
            WHERE ${conditions.joinToString(" AND ")}
            ORDER BY start_time DESC
        """.trimIndent()

        val sessions = mutableListOf<CodingSession>()
        try {
            connectionManager.withConnection { conn ->
                conn.prepareStatement(sql).use { pstmt ->
                    var paramIndex = 1
                    if (startTime != null) {
                        pstmt.setString(paramIndex++, dateTimeFormatter.format(startTime))
                    }
                    if (endTime != null) {
                        pstmt.setString(paramIndex, dateTimeFormatter.format(endTime))
                    }
                    pstmt.executeQuery().use { rs ->
                        while (rs.next()) {
                            sessions.add(rs.toCodingSession())
                        }
                    }
                }
            }
        } catch (e: Exception) {
            log.error("Failed to retrieve sessions", e)
        }

        return sessions
    }

    /**
     * Returns sessions with local changes not yet synced (is_synced = 0); this is the
     * change-tracking view used by the push path to decide what to send to the server.
     */
    fun getDirtySessions(): List<CodingSession> {
        val sql = """
            SELECT
                session_uuid, user_id, project_name, language, platform, ide_name,
                start_time, end_time, last_modified, is_synced, synced_at, sync_version
            FROM coding_sessions
            WHERE is_deleted = 0 AND is_synced = 0
            ORDER BY last_modified
        """.trimIndent()

        val sessions = mutableListOf<CodingSession>()
        try {
            connectionManager.withConnection { conn ->
                conn.prepareStatement(sql).use { pstmt ->
                    pstmt.executeQuery().use { rs ->
                        while (rs.next()) {
                            sessions.add(rs.toCodingSession())
                        }
                    }
                }
            }
        } catch (e: Exception) {
            log.error("Failed to retrieve dirty sessions", e)
        }
        return sessions
    }

    /**
     * Returns the active local rows for the given session uuids as a map keyed by session
     * uuid. Soft-deleted rows are excluded so a pull change can never revive them. Used by
     * the sync pull path to decide how to apply a remote change: rows already present and
     * clean are overwritten, dirty rows are left untouched.
     */
    fun findBySessionUuids(sessionUuids: Collection<String>): Map<String, CodingSession> {
        if (sessionUuids.isEmpty()) return emptyMap()
        val placeholders = sessionUuids.joinToString(",") { "?" }
        val sql = """
            SELECT
                session_uuid, user_id, project_name, language, platform, ide_name,
                start_time, end_time, last_modified, is_synced, synced_at, sync_version
            FROM coding_sessions
            WHERE session_uuid IN ($placeholders) AND is_deleted = 0
        """.trimIndent()
        val result = mutableMapOf<String, CodingSession>()
        try {
            connectionManager.withConnection { conn ->
                conn.prepareStatement(sql).use { pstmt ->
                    sessionUuids.forEachIndexed { index, uuid -> pstmt.setString(index + 1, uuid) }
                    pstmt.executeQuery().use { rs ->
                        while (rs.next()) {
                            rs.toCodingSession().let { result[it.sessionUuid] = it }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            log.error("Failed to retrieve sessions by uuids", e)
        }
        return result
    }

    /**
     * Inserts a session state received from the server (pull), or updates the existing
     * row in place. The row is marked synced. `ON CONFLICT` guards against a concurrent
     * local write between the lookup and this insert.
     */
    fun upsertSyncedSession(session: CodingSession) {
        val sql = """
            INSERT INTO coding_sessions(
                session_uuid, user_id, project_name, language, platform, ide_name,
                start_time, end_time, last_modified, is_synced, synced_at, sync_version
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 1, ?, ?)
            ON CONFLICT(session_uuid) DO UPDATE SET
                project_name = excluded.project_name,
                language = excluded.language,
                start_time = excluded.start_time,
                end_time = excluded.end_time,
                last_modified = excluded.last_modified,
                is_synced = 1,
                synced_at = excluded.synced_at,
                sync_version = excluded.sync_version
        """.trimIndent()
        try {
            connectionManager.withConnection { conn ->
                conn.prepareStatement(sql).use { pstmt ->
                    pstmt.setString(1, session.sessionUuid)
                    pstmt.setString(2, session.userId)
                    pstmt.setSessionCoreParams(3, session)
                    pstmt.setString(9, dateTimeFormatter.format(session.lastModified))
                    pstmt.setString(10, dateTimeFormatter.format(session.syncedAt ?: LocalDateTime.now()))
                    pstmt.setInt(11, session.syncVersion)
                    pstmt.executeUpdate()
                }
            }
        } catch (e: Exception) {
            log.error("Failed to upsert synced session ${session.sessionUuid}", e)
        }
    }

    /** Marks the given session uuids as synced (used after a successful push). */
    fun markSynced(sessionUuids: Collection<String>) {
        if (sessionUuids.isEmpty()) return
        val placeholders = sessionUuids.joinToString(",") { "?" }
        val sql = """
            UPDATE coding_sessions
            SET is_synced = 1, synced_at = ?
            WHERE session_uuid IN ($placeholders) AND is_deleted = 0
        """.trimIndent()
        try {
            connectionManager.withConnection { conn ->
                conn.prepareStatement(sql).use { pstmt ->
                    pstmt.setString(1, dateTimeFormatter.format(LocalDateTime.now()))
                    sessionUuids.forEachIndexed { index, uuid -> pstmt.setString(index + 2, uuid) }
                    pstmt.executeUpdate()
                }
            }
        } catch (e: Exception) {
            log.error("Failed to mark sessions as synced", e)
        }
    }

    /** Soft-deletes a local session after the server confirmed a DELETE change (pull). */
    fun markDeleted(sessionUuid: String) {
        val sql = "UPDATE coding_sessions SET is_deleted = 1 WHERE session_uuid = ? AND is_deleted = 0"
        try {
            connectionManager.withConnection { conn ->
                conn.prepareStatement(sql).use { pstmt ->
                    pstmt.setString(1, sessionUuid)
                    pstmt.executeUpdate()
                }
            }
        } catch (e: Exception) {
            log.error("Failed to mark session $sessionUuid as deleted", e)
        }
    }

    private fun java.sql.ResultSet.toCodingSession(): CodingSession = CodingSession(
        sessionUuid = getString("session_uuid"),
        userId = getString("user_id"),
        projectName = getString("project_name"),
        language = getString("language"),
        platform = getString("platform"),
        ideName = getString("ide_name"),
        startTime = LocalDateTime.parse(getString("start_time"), dateTimeFormatter),
        endTime = LocalDateTime.parse(getString("end_time"), dateTimeFormatter),
        lastModified = LocalDateTime.parse(getString("last_modified"), dateTimeFormatter),
        isSynced = getInt("is_synced") == 1,
        syncedAt = getString("synced_at")?.let { LocalDateTime.parse(it, dateTimeFormatter) },
        syncVersion = getInt("sync_version"),
    )

    fun getAllSessionUuids(): Set<String> {
        val sql = "SELECT session_uuid FROM coding_sessions WHERE is_deleted = 0"
        val uuids = mutableSetOf<String>()

        try {
            connectionManager.withConnection { conn ->
                conn.createStatement().executeQuery(sql).use { rs ->
                    while (rs.next()) {
                        uuids.add(rs.getString("session_uuid"))
                    }
                }
            }
        } catch (e: Exception) {
            log.error("Failed to retrieve session UUIDs", e)
        }

        return uuids
    }

    fun getAllActiveSessionTimes(): List<SessionSummaryDTO> {
        val sql = """
        SELECT start_time, end_time
        FROM coding_sessions
        WHERE is_deleted = 0
        ORDER BY start_time
    """.trimIndent()

        val sessions = mutableListOf<SessionSummaryDTO>()

        try {
            connectionManager.withConnection { conn ->
                conn.prepareStatement(sql).use { pstmt ->
                    pstmt.executeQuery().use { rs ->
                        while (rs.next()) {
                            sessions.add(
                                SessionSummaryDTO(
                                    startTime = LocalDateTime.parse(
                                        rs.getString("start_time"),
                                        dateTimeFormatter
                                    ),
                                    endTime = LocalDateTime.parse(
                                        rs.getString("end_time"),
                                        dateTimeFormatter
                                    )
                                )
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            log.error("Failed to load session times", e)
        }

        return sessions
    }

    fun getRecordCount(): Long {
        val sql = "SELECT COUNT(*) as total FROM coding_sessions WHERE is_deleted = 0"

        return try {
            connectionManager.withConnection { conn ->
                conn.prepareStatement(sql).use { pstmt ->
                    pstmt.executeQuery().use { rs ->
                        if (rs.next()) rs.getLong("total") else 0L
                    }
                }
            }
        } catch (e: Exception) {
            log.error("Failed to get record count", e)
            0L
        }
    }

    fun getFirstRecordDate(): LocalDate? {
        val sql = "SELECT MIN(start_time) as first_date FROM coding_sessions WHERE is_deleted = 0"

        return try {
            connectionManager.withConnection { conn ->
                conn.prepareStatement(sql).use { pstmt ->
                    pstmt.executeQuery().use { rs ->
                        if (rs.next()) {
                            val timestamp = rs.getString("first_date")
                            timestamp?.let {
                                LocalDateTime.parse(it, dateTimeFormatter).toLocalDate()
                            }
                        } else null
                    }
                }
            }
        } catch (e: Exception) {
            log.error("Failed to retrieve first record date", e)
            null
        }
    }

    fun getUserIdFromDatabase(): String? {
        val sql = "SELECT user_id FROM coding_sessions LIMIT 1"
        return try {
            connectionManager.withConnection { conn ->
                conn.createStatement().executeQuery(sql).use { rs ->
                    if (rs.next()) rs.getString("user_id")
                    else null
                }
            }
        } catch (e: Exception) {
            log.error("Failed to get user ID from database.", e)
            null
        }
    }
}
