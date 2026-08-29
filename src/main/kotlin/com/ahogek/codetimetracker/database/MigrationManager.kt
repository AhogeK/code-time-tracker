package com.ahogek.codetimetracker.database

import com.intellij.openapi.diagnostic.Logger
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
                    // Existing databases predate the owner column; add it idempotently.
                    try {
                        stmt.execute("ALTER TABLE coding_sessions ADD COLUMN owner_user_id TEXT")
                    } catch (_: SQLException) {
                        // Column already present.
                    }
                    stmt.execute(userTableSql)
                    log.info("Database table 'app_user' is ready.")
                    stmt.execute(cursorTableSql)
                    log.info("Database table 'sync_cursor' is ready.")
                    // Existing databases predate the last_sync_at column; add it idempotently.
                    try {
                        stmt.execute("ALTER TABLE sync_cursor ADD COLUMN last_sync_at TEXT")
                    } catch (_: SQLException) {
                        // Column already present.
                    }
                }

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
}
