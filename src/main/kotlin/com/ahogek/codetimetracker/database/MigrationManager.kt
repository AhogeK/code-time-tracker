package com.ahogek.codetimetracker.database

import com.intellij.openapi.diagnostic.Logger
import java.sql.Connection
import java.sql.SQLException

class MigrationManager(private val connectionManager: ConnectionManager) {

    private val log = Logger.getInstance(MigrationManager::class.java)

    fun migrate() {
        createTableIfNotExists()
    }

    private fun createTableIfNotExists() {
        val tableCreationSql = """
            CREATE TABLE IF NOT EXISTS coding_sessions (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                
                session_uuid TEXT NOT NULL UNIQUE,
                
                user_id TEXT NOT NULL,
                
                project_name TEXT NOT NULL,
                
                language TEXT NOT NULL,
                
                platform TEXT NOT NULL,
                
                ide_name TEXT NOT NULL,
                
                ide_version TEXT,
                
                start_time TEXT NOT NULL,
                
                end_time TEXT NOT NULL,
                
                last_modified TEXT NOT NULL,
                
                is_deleted INTEGER NOT NULL DEFAULT 0,
                
                is_synced INTEGER NOT NULL DEFAULT 0,
                
                synced_at TEXT,
                
                sync_version INTEGER NOT NULL DEFAULT 0,
                
                owner_user_id TEXT
            );
        """.trimIndent()

        val indexes = mapOf(
            "idx_session_uuid" to """
                CREATE UNIQUE INDEX IF NOT EXISTS idx_session_uuid
                ON coding_sessions(session_uuid)
            """.trimIndent(),

            "idx_sessions_time_range" to """
                CREATE INDEX IF NOT EXISTS idx_sessions_time_range
                ON coding_sessions(is_deleted, start_time, end_time)
            """.trimIndent(),

            "idx_sessions_min_time" to """
                CREATE INDEX IF NOT EXISTS idx_sessions_min_time
                ON coding_sessions(is_deleted, start_time)
            """.trimIndent(),

            "idx_sessions_sync_state" to """
                CREATE INDEX IF NOT EXISTS idx_sessions_sync_state
                ON coding_sessions(is_synced, last_modified)
            """.trimIndent()
        )

        val userTableSql = """
            CREATE TABLE IF NOT EXISTS app_user (
                user_id TEXT NOT NULL PRIMARY KEY,
                created_at TEXT NOT NULL
            );
        """.trimIndent()

        val cursorTableSql = """
            CREATE TABLE IF NOT EXISTS sync_cursor (
                user_id TEXT NOT NULL,
                device_id TEXT NOT NULL,
                last_pulled_change_id INTEGER NOT NULL DEFAULT 0,
                last_push_at TEXT,
                last_sync_at TEXT,
                updated_at TEXT NOT NULL,
                PRIMARY KEY (user_id, device_id)
            );
        """.trimIndent()

        try {
            connectionManager.withConnection { conn ->
                conn.createStatement().use { stmt ->
                    stmt.execute(tableCreationSql)
                    log.info("Database table 'coding_sessions' is ready.")
                    stmt.execute(userTableSql)
                    log.info("Database table 'app_user' is ready.")
                    stmt.execute(cursorTableSql)
                    log.info("Database table 'sync_cursor' is ready.")
                }
                // Existing databases predate these columns. Each addition is checked instead
                // of attempted-and-ignored: a failed ALTER must not leave the schema without
                // a column the repositories select. A concurrent IDE instance adding the same
                // column is tolerated by re-checking after a rejected ALTER.
                ensureColumn(
                    conn, "coding_sessions", "owner_user_id",
                    "ALTER TABLE coding_sessions ADD COLUMN owner_user_id TEXT",
                )
                ensureColumn(
                    conn, "coding_sessions", "ide_version",
                    "ALTER TABLE coding_sessions ADD COLUMN ide_version TEXT",
                )
                ensureColumn(
                    conn, "sync_cursor", "last_sync_at",
                    "ALTER TABLE sync_cursor ADD COLUMN last_sync_at TEXT",
                )

                indexes.forEach { (indexName, indexSql) ->
                    try {
                        conn.createStatement().use { stmt ->
                            stmt.execute(indexSql)
                            log.debug("Index '$indexName' created successfully.")
                        }
                    } catch (e: Exception) {
                        log.warn("Failed to create index '$indexName'. Query performance may be degraded.", e)
                    }
                }
                log.info("Database schema initialization completed with ${indexes.size} indexes.")
            }
        } catch (e: Exception) {
            log.error("Failed to create database table.", e)
            throw e
        }
    }

    /**
     * Adds [column] to [table] when it is missing.
     *
     * <p>The presence check is explicit because "the ALTER failed" and "the column was
     * already there" are indistinguishable by exception type alone. Treating both as
     * success would silently leave the schema without a column the repositories select,
     * which surfaces much later as empty statistics instead of an actionable error.
     *
     * <p>Two IDE instances can open the same database at once, so a rejected ALTER is
     * re-checked: when the other process added the column in between, this is not a
     * failure.
     *
     * @throws SQLException when the column is still missing after the attempt
     */
    private fun ensureColumn(conn: Connection, table: String, column: String, ddl: String) {
        if (columnExists(conn, table, column)) return
        try {
            conn.createStatement().use { it.execute(ddl) }
        } catch (e: SQLException) {
            if (columnExists(conn, table, column)) {
                log.info("Column $table.$column was added by another connection; continuing.")
                return
            }
            throw e
        }
        if (!columnExists(conn, table, column)) {
            throw SQLException(
                "Migration failed to add column $table.$column; the database cannot be used safely.",
            )
        }
    }

    /** Returns true when [table] already has [column] (case-insensitive, SQLite semantics). */
    private fun columnExists(conn: Connection, table: String, column: String): Boolean =
        conn.prepareStatement("PRAGMA table_info($table)").use { pstmt ->
            pstmt.executeQuery().use { rs ->
                var found = false
                while (rs.next()) {
                    if (rs.getString("name").equals(column, ignoreCase = true)) {
                        found = true
                    }
                }
                found
            }
        }
}
