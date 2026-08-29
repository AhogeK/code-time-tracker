package com.ahogek.codetimetracker.database

import com.intellij.openapi.diagnostic.Logger
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Persists the per-device sync watermark ([lastPulledChangeId]) and the last push
 * timestamp in the `sync_cursor` table. The pull cursor is advanced monotonically:
 * a stale pull response can never rewind it, so a crashed or restarted client resumes
 * from its last-known position without re-applying already-seen changes.
 *
 * @author AhogeK ahogek@gmail.com
 * @since 2026-08-29
 */
class SyncCursorRepository(private val connectionManager: ConnectionManager) {

    private val log = Logger.getInstance(SyncCursorRepository::class.java)
    private val dateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME

    /**
     * Binds the (user, device, now, now) parameters shared by the timestamp-inserting
     * cursor writes.
     */
    private fun java.sql.PreparedStatement.bindCursorTimestamps(userId: String, deviceId: String, now: String) {
        setString(1, userId)
        setString(2, deviceId)
        setString(3, now)
        setString(4, now)
    }

    /**
     * Returns the last pulled change id for [deviceId], or 0 when the device has never
     * synced (first sync starts from the beginning of the change log).
     */
    fun getPullCursor(deviceId: String): Long {
        val sql = """
            SELECT last_pulled_change_id
            FROM sync_cursor
            WHERE device_id = ?
        """.trimIndent()
        return try {
            connectionManager.withConnection { conn ->
                conn.prepareStatement(sql).use { pstmt ->
                    pstmt.setString(1, deviceId)
                    pstmt.executeQuery().use { rs ->
                        if (rs.next()) rs.getLong("last_pulled_change_id") else 0L
                    }
                }
            }
        } catch (e: Exception) {
            log.error("Failed to read sync cursor for device $deviceId", e)
            0L
        }
    }

    /**
     * Advances the pull cursor for [deviceId] to [changeId]. Uses a monotonic guard so
     * the stored cursor never moves backwards.
     */
    fun setPullCursor(userId: String, deviceId: String, changeId: Long) {
        val sql = """
            INSERT INTO sync_cursor(user_id, device_id, last_pulled_change_id, updated_at)
            VALUES (?, ?, ?, ?)
            ON CONFLICT(user_id, device_id) DO UPDATE SET
                last_pulled_change_id = MAX(last_pulled_change_id, excluded.last_pulled_change_id),
                updated_at = excluded.updated_at
        """.trimIndent()
        try {
            connectionManager.withConnection { conn ->
                conn.prepareStatement(sql).use { pstmt ->
                    pstmt.setString(1, userId)
                    pstmt.setString(2, deviceId)
                    pstmt.setLong(3, changeId)
                    pstmt.setString(4, dateTimeFormatter.format(LocalDateTime.now()))
                    pstmt.executeUpdate()
                }
            }
        } catch (e: Exception) {
            log.error("Failed to persist sync cursor for device $deviceId", e)
        }
    }

    /**
     * Returns the last successful push timestamp for [deviceId], or null when the device
     * has never pushed. Persisted across IDE restarts, unlike in-memory status.
     */
    fun getLastPushAt(deviceId: String): LocalDateTime? {
        val sql = "SELECT last_push_at FROM sync_cursor WHERE device_id = ?"
        return try {
            connectionManager.withConnection { conn ->
                conn.prepareStatement(sql).use { pstmt ->
                    pstmt.setString(1, deviceId)
                    pstmt.executeQuery().use { rs ->
                        if (rs.next()) {
                            rs.getString("last_push_at")?.let { LocalDateTime.parse(it, dateTimeFormatter) }
                        } else null
                    }
                }
            }
        } catch (e: Exception) {
            log.error("Failed to read last push time for device $deviceId", e)
            null
        }
    }

    /**
     * Removes the local sync watermark for [deviceId]. Used when the bound API key
     * switches to a different user, so the next pull starts from 0 for that user
     * instead of reusing the previous user's cursor.
     */
    fun clear(deviceId: String) {
        val sql = "DELETE FROM sync_cursor WHERE device_id = ?"
        try {
            connectionManager.withConnection { conn ->
                conn.prepareStatement(sql).use { pstmt ->
                    pstmt.setString(1, deviceId)
                    pstmt.executeUpdate()
                }
            }
        } catch (e: Exception) {
            log.error("Failed to clear sync cursor for device $deviceId", e)
        }
    }

    /**
     * Records the last successful sync round timestamp (pull or push) for [deviceId].
     * This is the "last sync" shown in the settings UI, distinct from the push-only
     * timestamp.
     */
    fun setLastSyncAt(userId: String, deviceId: String) {
        val sql = """
            INSERT INTO sync_cursor(user_id, device_id, last_sync_at, updated_at)
            VALUES (?, ?, ?, ?)
            ON CONFLICT(user_id, device_id) DO UPDATE SET
                last_sync_at = excluded.last_sync_at,
                updated_at = excluded.updated_at
        """.trimIndent()
        try {
            connectionManager.withConnection { conn ->
                conn.prepareStatement(sql).use { pstmt ->
                    pstmt.bindCursorTimestamps(userId, deviceId, dateTimeFormatter.format(LocalDateTime.now()))
                    pstmt.executeUpdate()
                }
            }
        } catch (e: Exception) {
            log.error("Failed to record last sync time for device $deviceId", e)
        }
    }

    /**
     * Returns the last successful sync round timestamp, falling back to the last push
     * timestamp for rows created before the column existed.
     */
    fun getLastSyncAt(deviceId: String): LocalDateTime? {
        val sql = "SELECT last_sync_at, last_push_at FROM sync_cursor WHERE device_id = ?"
        return try {
            connectionManager.withConnection { conn ->
                conn.prepareStatement(sql).use { pstmt ->
                    pstmt.setString(1, deviceId)
                    pstmt.executeQuery().use { rs ->
                        if (rs.next()) {
                            rs.getString("last_sync_at")
                                ?.let { runCatching { LocalDateTime.parse(it, dateTimeFormatter) }.getOrNull() }
                                ?: rs.getString("last_push_at")?.let {
                                    runCatching { LocalDateTime.parse(it, dateTimeFormatter) }.getOrNull()
                                }
                        } else null
                    }
                }
            }
        } catch (e: Exception) {
            log.error("Failed to read last sync time for device $deviceId", e)
            null
        }
    }

    /** Records the last successful push timestamp for [deviceId]. */
    fun setPushAt(userId: String, deviceId: String) {
        val sql = """
            INSERT INTO sync_cursor(user_id, device_id, last_pulled_change_id, last_push_at, updated_at)
            VALUES (?, ?, 0, ?, ?)
            ON CONFLICT(user_id, device_id) DO UPDATE SET
                last_push_at = excluded.last_push_at,
                updated_at = excluded.updated_at
        """.trimIndent()
        try {
            connectionManager.withConnection { conn ->
                conn.prepareStatement(sql).use { pstmt ->
                    pstmt.bindCursorTimestamps(userId, deviceId, dateTimeFormatter.format(LocalDateTime.now()))
                    pstmt.executeUpdate()
                }
            }
        } catch (e: Exception) {
            log.error("Failed to persist push timestamp for device $deviceId", e)
        }
    }
}
