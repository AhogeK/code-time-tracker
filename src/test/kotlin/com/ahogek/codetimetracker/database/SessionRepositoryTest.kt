package com.ahogek.codetimetracker.database

import com.ahogek.codetimetracker.model.CodingSession
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.LocalDateTime
import java.util.*

class SessionRepositoryTest {

    private lateinit var connectionManager: ConnectionManager
    private lateinit var migrationManager: MigrationManager
    private lateinit var sessionRepository: SessionRepository
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
        migrationManager.migrate()
        sessionRepository = SessionRepository(connectionManager)
    }

    @AfterEach
    fun tearDown() {
        connectionManager.shutdown()
    }

    @Test
    fun `importSessions should insert new sessions`() {
        val sessions = listOf(
            CodingSession(
                sessionUuid = UUID.randomUUID().toString(),
                userId = "test-user",
                projectName = "TestProject",
                language = "Kotlin",
                platform = "macOS",
                ideName = "IntelliJ IDEA",
                startTime = LocalDateTime.of(2026, 1, 1, 10, 0),
                endTime = LocalDateTime.of(2026, 1, 1, 12, 0),
                lastModified = LocalDateTime.now()
            )
        )

        val imported = sessionRepository.importSessions(sessions)
        assertThat(imported).isEqualTo(1)
    }

    @Test
    fun `importSessions should skip duplicate UUIDs`() {
        val uuid = UUID.randomUUID().toString()
        val sessions = listOf(
            CodingSession(
                sessionUuid = uuid,
                userId = "test-user",
                projectName = "TestProject",
                language = "Kotlin",
                platform = "macOS",
                ideName = "IntelliJ IDEA",
                startTime = LocalDateTime.of(2026, 1, 1, 10, 0),
                endTime = LocalDateTime.of(2026, 1, 1, 12, 0),
                lastModified = LocalDateTime.now()
            )
        )

        sessionRepository.importSessions(sessions)
        val imported = sessionRepository.importSessions(sessions)

        assertThat(imported).isEqualTo(0)
        assertThat(sessionRepository.getRecordCount()).isEqualTo(1)
    }

    @Test
    fun `getSessions should return all sessions`() {
        val session1 = CodingSession(
            sessionUuid = UUID.randomUUID().toString(),
            userId = "test-user",
            projectName = "Project1",
            language = "Kotlin",
            platform = "macOS",
            ideName = "IntelliJ IDEA",
            startTime = LocalDateTime.of(2026, 1, 1, 10, 0),
            endTime = LocalDateTime.of(2026, 1, 1, 12, 0),
            lastModified = LocalDateTime.now()
        )
        val session2 = CodingSession(
            sessionUuid = UUID.randomUUID().toString(),
            userId = "test-user",
            projectName = "Project2",
            language = "Java",
            platform = "macOS",
            ideName = "IntelliJ IDEA",
            startTime = LocalDateTime.of(2026, 1, 2, 14, 0),
            endTime = LocalDateTime.of(2026, 1, 2, 16, 0),
            lastModified = LocalDateTime.now()
        )

        sessionRepository.importSessions(listOf(session1, session2))

        val sessions = sessionRepository.getSessions()
        assertThat(sessions).hasSize(2)
    }

    @Test
    fun `getSessions should filter by time range`() {
        val session1 = CodingSession(
            sessionUuid = UUID.randomUUID().toString(),
            userId = "test-user",
            projectName = "Project1",
            language = "Kotlin",
            platform = "macOS",
            ideName = "IntelliJ IDEA",
            startTime = LocalDateTime.of(2026, 1, 1, 10, 0),
            endTime = LocalDateTime.of(2026, 1, 1, 12, 0),
            lastModified = LocalDateTime.now()
        )
        val session2 = CodingSession(
            sessionUuid = UUID.randomUUID().toString(),
            userId = "test-user",
            projectName = "Project2",
            language = "Java",
            platform = "macOS",
            ideName = "IntelliJ IDEA",
            startTime = LocalDateTime.of(2026, 2, 1, 14, 0),
            endTime = LocalDateTime.of(2026, 2, 1, 16, 0),
            lastModified = LocalDateTime.now()
        )

        sessionRepository.importSessions(listOf(session1, session2))

        val sessions = sessionRepository.getSessions(
            startTime = LocalDateTime.of(2026, 1, 15, 0, 0),
            endTime = LocalDateTime.of(2026, 2, 15, 0, 0)
        )

        assertThat(sessions).hasSize(1)
        assertThat(sessions.first().projectName).isEqualTo("Project2")
    }

    @Test
    fun `getAllSessionUuids should return all UUIDs`() {
        val uuid1 = UUID.randomUUID().toString()
        val uuid2 = UUID.randomUUID().toString()

        sessionRepository.importSessions(
            listOf(
                CodingSession(
                    sessionUuid = uuid1,
                    userId = "test-user",
                    projectName = "Project1",
                    language = "Kotlin",
                    platform = "macOS",
                    ideName = "IntelliJ IDEA",
                    startTime = LocalDateTime.of(2026, 1, 1, 10, 0),
                    endTime = LocalDateTime.of(2026, 1, 1, 12, 0),
                    lastModified = LocalDateTime.now()
                ),
                CodingSession(
                    sessionUuid = uuid2,
                    userId = "test-user",
                    projectName = "Project2",
                    language = "Java",
                    platform = "macOS",
                    ideName = "IntelliJ IDEA",
                    startTime = LocalDateTime.of(2026, 1, 2, 14, 0),
                    endTime = LocalDateTime.of(2026, 1, 2, 16, 0),
                    lastModified = LocalDateTime.now()
                )
            )
        )

        val uuids = sessionRepository.getAllSessionUuids()
        assertThat(uuids).contains(uuid1, uuid2)
    }

    @Test
    fun `getRecordCount should return correct count`() {
        sessionRepository.importSessions(
            listOf(
                CodingSession(
                    sessionUuid = UUID.randomUUID().toString(),
                    userId = "test-user",
                    projectName = "Project1",
                    language = "Kotlin",
                    platform = "macOS",
                    ideName = "IntelliJ IDEA",
                    startTime = LocalDateTime.of(2026, 1, 1, 10, 0),
                    endTime = LocalDateTime.of(2026, 1, 1, 12, 0),
                    lastModified = LocalDateTime.now()
                ),
                CodingSession(
                    sessionUuid = UUID.randomUUID().toString(),
                    userId = "test-user",
                    projectName = "Project2",
                    language = "Java",
                    platform = "macOS",
                    ideName = "IntelliJ IDEA",
                    startTime = LocalDateTime.of(2026, 1, 2, 14, 0),
                    endTime = LocalDateTime.of(2026, 1, 2, 16, 0),
                    lastModified = LocalDateTime.now()
                )
            )
        )

        assertThat(sessionRepository.getRecordCount()).isEqualTo(2)
    }

    @Test
    fun `getFirstRecordDate should return earliest date`() {
        sessionRepository.importSessions(
            listOf(
                CodingSession(
                    sessionUuid = UUID.randomUUID().toString(),
                    userId = "test-user",
                    projectName = "Project1",
                    language = "Kotlin",
                    platform = "macOS",
                    ideName = "IntelliJ IDEA",
                    startTime = LocalDateTime.of(2026, 2, 1, 10, 0),
                    endTime = LocalDateTime.of(2026, 2, 1, 12, 0),
                    lastModified = LocalDateTime.now()
                ),
                CodingSession(
                    sessionUuid = UUID.randomUUID().toString(),
                    userId = "test-user",
                    projectName = "Project2",
                    language = "Java",
                    platform = "macOS",
                    ideName = "IntelliJ IDEA",
                    startTime = LocalDateTime.of(2026, 1, 1, 14, 0),
                    endTime = LocalDateTime.of(2026, 1, 1, 16, 0),
                    lastModified = LocalDateTime.now()
                )
            )
        )

        val firstDate = sessionRepository.getFirstRecordDate()
        assertThat(firstDate?.year).isEqualTo(2026)
        assertThat(firstDate?.monthValue).isEqualTo(1)
        assertThat(firstDate?.dayOfMonth).isEqualTo(1)
    }

    @Test
    fun `getUserIdFromDatabase should return user id`() {
        sessionRepository.importSessions(
            listOf(
                CodingSession(
                    sessionUuid = UUID.randomUUID().toString(),
                    userId = "test-user-123",
                    projectName = "Project1",
                    language = "Kotlin",
                    platform = "macOS",
                    ideName = "IntelliJ IDEA",
                    startTime = LocalDateTime.of(2026, 1, 1, 10, 0),
                    endTime = LocalDateTime.of(2026, 1, 1, 12, 0),
                    lastModified = LocalDateTime.now()
                )
            )
        )

        val userId = sessionRepository.getUserIdFromDatabase()
        assertThat(userId).isEqualTo("test-user-123")
    }

    @Test
    fun `getAllActiveSessionTimes should return session times`() {
        sessionRepository.importSessions(
            listOf(
                CodingSession(
                    sessionUuid = UUID.randomUUID().toString(),
                    userId = "test-user",
                    projectName = "Project1",
                    language = "Kotlin",
                    platform = "macOS",
                    ideName = "IntelliJ IDEA",
                    startTime = LocalDateTime.of(2026, 1, 1, 10, 0),
                    endTime = LocalDateTime.of(2026, 1, 1, 12, 0),
                    lastModified = LocalDateTime.now()
                )
            )
        )

        val times = sessionRepository.getAllActiveSessionTimes()
        assertThat(times).hasSize(1)
        assertThat(times.first().startTime).isEqualTo(LocalDateTime.of(2026, 1, 1, 10, 0))
        assertThat(times.first().endTime).isEqualTo(LocalDateTime.of(2026, 1, 1, 12, 0))
    }
}
