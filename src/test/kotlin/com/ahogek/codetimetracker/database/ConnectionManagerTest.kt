package com.ahogek.codetimetracker.database

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class ConnectionManagerTest {

    private lateinit var connectionManager: ConnectionManager
    private lateinit var testDbPath: Path

    @BeforeEach
    fun setUp(@TempDir tempDir: Path) {
        testDbPath = tempDir.resolve("test.db")
        connectionManager = ConnectionManager()
        connectionManager.setConnectionFactory(
            DriverManagerConnectionFactory("jdbc:sqlite:${testDbPath}"),
            "jdbc:sqlite:${testDbPath}"
        )
    }

    @AfterEach
    fun tearDown() {
        connectionManager.shutdown()
    }

    @Test
    fun `withConnection should return connection and execute block`() {
        var connectionAcquired = false
        val result = connectionManager.withConnection { conn ->
            connectionAcquired = true
            conn.isValid(1)
        }

        assertThat(connectionAcquired).isTrue()
        assertThat(result).isTrue()
    }

    @Test
    fun `withConnection should provide valid connection`() {
        connectionManager.withConnection { conn ->
            assertThat(conn).isNotNull()
            assertThat(conn.isClosed).isFalse()
        }
    }

    @Test
    fun `setConnectionFactory should update factory`() {
        val newDbPath = testDbPath.resolveSibling("new.db")
        connectionManager.setConnectionFactory(
            DriverManagerConnectionFactory("jdbc:sqlite:${newDbPath}"),
            "jdbc:sqlite:${newDbPath}"
        )

        connectionManager.withConnection { conn ->
            assertThat(conn).isNotNull()
        }
    }

    @Test
    fun `initialize should not throw`() {
        connectionManager.initialize()
    }

    @Test
    fun `shutdown should terminate executor`() {
        connectionManager.shutdown()
        assertThat(connectionManager.databaseExecutor.isShutdown).isTrue()
    }

    @Test
    fun `withConnection should handle multiple calls`() {
        repeat(3) {
            connectionManager.withConnection { conn ->
                assertThat(conn.isClosed).isFalse()
            }
        }
    }
}
