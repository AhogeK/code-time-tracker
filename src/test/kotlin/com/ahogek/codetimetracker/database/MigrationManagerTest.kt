package com.ahogek.codetimetracker.database

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import java.sql.SQLException

class MigrationManagerTest {

    private lateinit var connectionManager: ConnectionManager
    private lateinit var migrationManager: MigrationManager
    private lateinit var testDbPath: Path

    @BeforeEach
    fun setUp(@TempDir tempDir: Path) {
        testDbPath = tempDir.resolve("test.db")
        connectionManager = ConnectionManager()
        connectionManager.setConnectionFactory(
            DriverManagerConnectionFactory("jdbc:sqlite:${testDbPath}"),
            "jdbc:sqlite:${testDbPath}"
        )
        migrationManager = MigrationManager(connectionManager)
    }

    @AfterEach
    fun tearDown() {
        connectionManager.shutdown()
    }

    @Test
    fun `migrate should create coding_sessions table`() {
        migrationManager.migrate()

        val tableExists = connectionManager.withConnection { conn ->
            conn.prepareStatement(
                "SELECT name FROM sqlite_master WHERE type='table' AND name='coding_sessions'"
            ).use { pstmt ->
                pstmt.executeQuery().use { rs ->
                    rs.next()
                }
            }
        }

        assertThat(tableExists).isTrue()
    }

    @Test
    fun `migrate should create required indexes`() {
        migrationManager.migrate()

        val indexes = connectionManager.withConnection { conn ->
            conn.prepareStatement(
                "SELECT name FROM sqlite_master WHERE type='index' AND tbl_name='coding_sessions'"
            ).use { pstmt ->
                pstmt.executeQuery().use { rs ->
                    val indexNames = mutableListOf<String>()
                    while (rs.next()) {
                        indexNames.add(rs.getString("name"))
                    }
                    indexNames
                }
            }
        }

        assertThat(indexes).isNotEmpty
    }

    @Test
    fun `migrate should create unique index on session_uuid`() {
        migrationManager.migrate()

        val isUnique = connectionManager.withConnection { conn ->
            conn.prepareStatement(
                "SELECT sql FROM sqlite_master WHERE type='index' AND name='idx_session_uuid'"
            ).use { pstmt ->
                pstmt.executeQuery().use { rs ->
                    rs.next() && rs.getString("sql")?.contains("UNIQUE") == true
                }
            }
        }

        assertThat(isUnique).isTrue()
    }

    @Test
    fun `migrate should create all required columns`() {
        migrationManager.migrate()

        val columns = connectionManager.withConnection { conn ->
            conn.prepareStatement("PRAGMA table_info(coding_sessions)").use { pstmt ->
                pstmt.executeQuery().use { rs ->
                    val columnNames = mutableListOf<String>()
                    while (rs.next()) {
                        columnNames.add(rs.getString("name"))
                    }
                    columnNames
                }
            }
        }

        assertThat(columns).contains("id")
        assertThat(columns).contains("session_uuid")
        assertThat(columns).contains("user_id")
        assertThat(columns).contains("project_name")
        assertThat(columns).contains("language")
        assertThat(columns).contains("platform")
        assertThat(columns).contains("ide_name")
        assertThat(columns).contains("start_time")
        assertThat(columns).contains("end_time")
        assertThat(columns).contains("last_modified")
        assertThat(columns).contains("is_deleted")
        assertThat(columns).contains("is_synced")
        assertThat(columns).contains("synced_at")
        assertThat(columns).contains("sync_version")
    }

    @Test
    fun `migrate should be idempotent`() {
        migrationManager.migrate()
        migrationManager.migrate()

        val tableExists = connectionManager.withConnection { conn ->
            conn.prepareStatement(
                "SELECT name FROM sqlite_master WHERE type='table' AND name='coding_sessions'"
            ).use { pstmt ->
                pstmt.executeQuery().use { rs ->
                    rs.next()
                }
            }
        }

        assertThat(tableExists).isTrue()
    }

    @Test
    fun `migrate should add the ide_version column idempotently`() {
        migrationManager.migrate()
        migrationManager.migrate() // second run must not throw

        val columns = columnsOf("coding_sessions")

        assertThat(columns).contains("ide_name", "ide_version")
    }

    @Test
    fun `migrate should upgrade a legacy table and leave old rows without an IDE version`() {
        // Simulate a database created before the ide_version column existed.
        connectionManager.withConnection { conn ->
            conn.createStatement().use { stmt ->
                stmt.execute(
                    """
                    CREATE TABLE coding_sessions (
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
                        sync_version INTEGER NOT NULL DEFAULT 0
                    )
                    """.trimIndent(),
                )
                stmt.execute(
                    """
                    INSERT INTO coding_sessions(
                        session_uuid, user_id, project_name, language, platform, ide_name,
                        start_time, end_time, last_modified
                    ) VALUES (
                        'legacy-1', 'test-user', 'TestProject', 'Kotlin', 'macOS', 'IntelliJ IDEA',
                        '2026-01-01T10:00:00', '2026-01-01T11:00:00', '2026-01-01T11:00:00'
                    )
                    """.trimIndent(),
                )
            }
        }

        migrationManager.migrate()

        val stored = SessionRepository(connectionManager).getSessions().single()
        assertThat(stored.ideName).isEqualTo("IntelliJ IDEA")
        assertThat(stored.ideVersion).isNull()

        assertThat(columnsOf("coding_sessions")).contains("owner_user_id", "ide_version")
        assertThat(columnsOf("sync_cursor")).contains("last_sync_at")
    }

    @Test
    fun `migrate should surface a column it cannot add instead of ignoring it`() {
        // Legacy schema with none of the added columns, plus the auxiliary tables, so the
        // only remaining write is ALTER TABLE. A read-only file makes those ALTERs fail:
        // the failure must escape instead of leaving a silently incomplete schema.
        connectionManager.withConnection { conn ->
            conn.createStatement().use { stmt ->
                stmt.execute(
                    """
                    CREATE TABLE coding_sessions (
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
                        sync_version INTEGER NOT NULL DEFAULT 0
                    )
                    """.trimIndent(),
                )
                stmt.execute(
                    """
                    CREATE TABLE app_user (
                        user_id TEXT NOT NULL PRIMARY KEY,
                        created_at TEXT NOT NULL
                    )
                    """.trimIndent(),
                )
                stmt.execute(
                    """
                    CREATE TABLE sync_cursor (
                        user_id TEXT NOT NULL,
                        device_id TEXT NOT NULL,
                        last_pulled_change_id INTEGER NOT NULL DEFAULT 0,
                        last_push_at TEXT,
                        updated_at TEXT NOT NULL,
                        PRIMARY KEY (user_id, device_id)
                    )
                    """.trimIndent(),
                )
            }
        }

        val originalPermissions = Files.getPosixFilePermissions(testDbPath)
        Files.setPosixFilePermissions(testDbPath, PosixFilePermissions.fromString("r--r--r--"))
        try {
            val thrown: Throwable? = try {
                migrationManager.migrate()
                null
            } catch (e: Throwable) {
                e
            }
            // Production logs and rethrows; the test logger turns that same call into an
            // assertion error. Either way the migration must not return normally, and the
            // original SQL failure must stay visible in the cause chain.
            assertThat(thrown).describedAs("migration must not ignore a failed column addition").isNotNull()
            val sqlFailure = generateSequence(thrown!!) { it.cause }
                .filterIsInstance<SQLException>()
                .firstOrNull()
            assertThat(sqlFailure != null)
                .describedAs("the original SQL failure must stay visible")
                .isTrue()
        } finally {
            Files.setPosixFilePermissions(testDbPath, originalPermissions)
        }

        // With write access restored the same migration completes, proving the failure above
        // came from the environment rather than from the migration being broken.
        migrationManager.migrate()
        assertThat(columnsOf("coding_sessions")).contains("owner_user_id", "ide_version")
        assertThat(columnsOf("sync_cursor")).contains("last_sync_at")
    }

    private fun columnsOf(table: String): List<String> =
        connectionManager.withConnection { conn ->
            conn.prepareStatement("PRAGMA table_info($table)").use { pstmt ->
                pstmt.executeQuery().use { rs ->
                    buildList { while (rs.next()) add(rs.getString("name")) }
                }
            }
        }

}
