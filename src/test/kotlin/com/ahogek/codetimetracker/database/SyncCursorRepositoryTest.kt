package com.ahogek.codetimetracker.database

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class SyncCursorRepositoryTest {

    private lateinit var connectionManager: ConnectionManager
    private lateinit var migrationManager: MigrationManager
    private lateinit var cursorRepository: SyncCursorRepository
    private lateinit var testDbPath: Path

    @BeforeEach
    fun setUp(@TempDir tempDir: Path) {
        testDbPath = tempDir.resolve("test.db")
        connectionManager = ConnectionManager()
        connectionManager.setConnectionFactory(
            DriverManagerConnectionFactory("jdbc:sqlite:${testDbPath}"),
            "jdbc:sqlite:${testDbPath}",
        )
        migrationManager = MigrationManager(connectionManager)
        migrationManager.migrate()
        cursorRepository = SyncCursorRepository(connectionManager)
    }

    @AfterEach
    fun tearDown() {
        connectionManager.shutdown()
    }

    @Test
    fun `should return zero cursor for a device that never synced`() {
        val cursor = cursorRepository.getPullCursor("device-1")

        assertThat(cursor).isEqualTo(0L)
    }

    @Test
    fun `should persist and read back the pull cursor per device`() {
        cursorRepository.setPullCursor("user-1", "device-1", 42L)

        assertThat(cursorRepository.getPullCursor("device-1")).isEqualTo(42L)
        assertThat(cursorRepository.getPullCursor("device-2")).isEqualTo(0L)
    }

    @Test
    fun `should never rewind the pull cursor`() {
        cursorRepository.setPullCursor("user-1", "device-1", 42L)
        cursorRepository.setPullCursor("user-1", "device-1", 10L)

        assertThat(cursorRepository.getPullCursor("device-1")).isEqualTo(42L)
    }

    @Test
    fun `should record push timestamp without clobbering the pull cursor`() {
        cursorRepository.setPullCursor("user-1", "device-1", 42L)
        cursorRepository.setPushAt("user-1", "device-1")

        assertThat(cursorRepository.getPullCursor("device-1")).isEqualTo(42L)
    }

    @Test
    fun `should return null push time for a device that never pushed`() {
        assertThat(cursorRepository.getLastPushAt("device-never-pushed")).isNull()
    }

    @Test
    fun `should return the persisted push time`() {
        cursorRepository.setPushAt("user-1", "device-1")

        assertThat(cursorRepository.getLastPushAt("device-1")).isNotNull()
    }

    @Test
    fun `should record and read the last sync time`() {
        cursorRepository.setLastSyncAt("user-1", "device-1")

        assertThat(cursorRepository.getLastSyncAt("device-1")).isNotNull()
    }

    @Test
    fun `should fall back to the push time when last sync time is absent`() {
        cursorRepository.setPushAt("user-1", "device-1")

        assertThat(cursorRepository.getLastSyncAt("device-1")).isNotNull()
    }

    @Test
    fun `should clear the cursor and push time`() {
        cursorRepository.setPullCursor("user-1", "device-1", 42L)
        cursorRepository.setPushAt("user-1", "device-1")

        cursorRepository.clear("device-1")

        assertThat(cursorRepository.getPullCursor("device-1")).isEqualTo(0L)
        assertThat(cursorRepository.getLastPushAt("device-1")).isNull()
    }
}
