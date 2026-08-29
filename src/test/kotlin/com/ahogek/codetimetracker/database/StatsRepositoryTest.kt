package com.ahogek.codetimetracker.database

import com.ahogek.codetimetracker.model.CodingSession
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.Duration
import java.time.LocalDateTime
import java.util.*

class StatsRepositoryTest {

    private lateinit var connectionManager: ConnectionManager
    private lateinit var migrationManager: MigrationManager
    private lateinit var sessionRepository: SessionRepository
    private lateinit var statsRepository: StatsRepository
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
        statsRepository = StatsRepository(connectionManager)
    }

    @AfterEach
    fun tearDown() {
        connectionManager.shutdown()
    }

    @Test
    fun `getTotalCodingTime should return zero for empty database`() {
        val totalTime = statsRepository.getTotalCodingTime()
        assertThat(totalTime).isEqualTo(Duration.ZERO)
    }

    private fun syncedSession(
        sessionUuid: String,
        start: LocalDateTime,
        end: LocalDateTime,
    ) = CodingSession(
        sessionUuid = sessionUuid,
        userId = "local-user",
        projectName = "TestProject",
        language = "Kotlin",
        platform = "macOS",
        ideName = "IntelliJ IDEA",
        startTime = start,
        endTime = end,
        lastModified = end,
    )

    @Test
    fun `getTotalCodingTime should scope to the owner when set`() {
        sessionRepository.upsertSyncedSession(
            syncedSession("a-1", LocalDateTime.of(2026, 1, 1, 9, 0), LocalDateTime.of(2026, 1, 1, 11, 0)),
            ownerUserId = "user-a",
        )
        sessionRepository.upsertSyncedSession(
            syncedSession("b-1", LocalDateTime.of(2026, 1, 2, 9, 0), LocalDateTime.of(2026, 1, 2, 10, 0)),
            ownerUserId = "user-b",
        )

        statsRepository.ownerUserId = "user-a"
        assertThat(statsRepository.getTotalCodingTime()).isEqualTo(Duration.ofHours(2))

        statsRepository.ownerUserId = null
        assertThat(statsRepository.getTotalCodingTime()).isEqualTo(Duration.ofHours(3))
    }

    @Test
    fun `getTotalCodingTime should include unowned local sessions for the bound user`() {
        sessionRepository.upsertSyncedSession(
            syncedSession("a-1", LocalDateTime.of(2026, 1, 1, 9, 0), LocalDateTime.of(2026, 1, 1, 11, 0)),
            ownerUserId = "user-a",
        )
        // A locally created, not-yet-synced session carries no owner (is_synced = 0,
        // owner_user_id = NULL); it is pushed to the bound account on the next sync,
        // so it must count in that account's statistics immediately.
        sessionRepository.importSessions(
            listOf(syncedSession("local-1", LocalDateTime.of(2026, 1, 3, 9, 0), LocalDateTime.of(2026, 1, 3, 10, 0))),
        )
        sessionRepository.upsertSyncedSession(
            syncedSession("b-1", LocalDateTime.of(2026, 1, 2, 9, 0), LocalDateTime.of(2026, 1, 2, 10, 0)),
            ownerUserId = "user-b",
        )

        // Bound to user-a: their synced sessions plus unowned local sessions count;
        // user-b's sessions are excluded.
        statsRepository.ownerUserId = "user-a"
        assertThat(statsRepository.getTotalCodingTime()).isEqualTo(Duration.ofHours(3))

        // Unbound: everything counts.
        statsRepository.ownerUserId = null
        assertThat(statsRepository.getTotalCodingTime()).isEqualTo(Duration.ofHours(4))
    }

    @Test
    fun `getTotalCodingTime should calculate total time correctly`() {
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

        val totalTime = statsRepository.getTotalCodingTime()
        assertThat(totalTime.toHours()).isEqualTo(4)
    }

    @Test
    fun `getCodingTimeForPeriod should filter by time range`() {
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
                    startTime = LocalDateTime.of(2026, 2, 1, 14, 0),
                    endTime = LocalDateTime.of(2026, 2, 1, 16, 0),
                    lastModified = LocalDateTime.now()
                )
            )
        )

        val periodTime = statsRepository.getCodingTimeForPeriod(
            LocalDateTime.of(2026, 1, 15, 0, 0),
            LocalDateTime.of(2026, 2, 15, 0, 0)
        )

        assertThat(periodTime.toHours()).isEqualTo(2)
    }

    @Test
    fun `getCodingStreaks should return zero for empty database`() {
        val streaks = statsRepository.getCodingStreaks()
        assertThat(streaks.currentStreak).isEqualTo(0)
        assertThat(streaks.maxStreak).isEqualTo(0)
    }

    @Test
    fun `getLanguageDistribution should return empty for no data`() {
        val distribution = statsRepository.getLanguageDistribution()
        assertThat(distribution).isEmpty()
    }

    @Test
    fun `getLanguageDistribution should calculate distribution correctly`() {
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
                    language = "Kotlin",
                    platform = "macOS",
                    ideName = "IntelliJ IDEA",
                    startTime = LocalDateTime.of(2026, 1, 2, 14, 0),
                    endTime = LocalDateTime.of(2026, 1, 2, 16, 0),
                    lastModified = LocalDateTime.now()
                ),
                CodingSession(
                    sessionUuid = UUID.randomUUID().toString(),
                    userId = "test-user",
                    projectName = "Project3",
                    language = "Java",
                    platform = "macOS",
                    ideName = "IntelliJ IDEA",
                    startTime = LocalDateTime.of(2026, 1, 3, 9, 0),
                    endTime = LocalDateTime.of(2026, 1, 3, 10, 0),
                    lastModified = LocalDateTime.now()
                )
            )
        )

        val distribution = statsRepository.getLanguageDistribution()
        assertThat(distribution).hasSize(2)

        val kotlinUsage = distribution.first { it.language == "Kotlin" }
        assertThat(kotlinUsage.totalDuration.toHours()).isEqualTo(4)

        val javaUsage = distribution.first { it.language == "Java" }
        assertThat(javaUsage.totalDuration.toHours()).isEqualTo(1)
    }

    @Test
    fun `getProjectDistribution should calculate distribution correctly`() {
        sessionRepository.importSessions(
            listOf(
                CodingSession(
                    sessionUuid = UUID.randomUUID().toString(),
                    userId = "test-user",
                    projectName = "ProjectA",
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
                    projectName = "ProjectB",
                    language = "Kotlin",
                    platform = "macOS",
                    ideName = "IntelliJ IDEA",
                    startTime = LocalDateTime.of(2026, 1, 2, 14, 0),
                    endTime = LocalDateTime.of(2026, 1, 2, 16, 0),
                    lastModified = LocalDateTime.now()
                )
            )
        )

        val distribution = statsRepository.getProjectDistribution()
        assertThat(distribution).hasSize(2)

        val projectA = distribution.first { it.projectName == "ProjectA" }
        assertThat(projectA.totalDuration.toHours()).isEqualTo(2)

        val projectB = distribution.first { it.projectName == "ProjectB" }
        assertThat(projectB.totalDuration.toHours()).isEqualTo(2)
    }

    @Test
    fun `getTimeOfDayDistribution should categorize correctly`() {
        sessionRepository.importSessions(
            listOf(
                CodingSession(
                    sessionUuid = UUID.randomUUID().toString(),
                    userId = "test-user",
                    projectName = "Project1",
                    language = "Kotlin",
                    platform = "macOS",
                    ideName = "IntelliJ IDEA",
                    startTime = LocalDateTime.of(2026, 1, 1, 3, 0),
                    endTime = LocalDateTime.of(2026, 1, 1, 4, 0),
                    lastModified = LocalDateTime.now()
                ),
                CodingSession(
                    sessionUuid = UUID.randomUUID().toString(),
                    userId = "test-user",
                    projectName = "Project2",
                    language = "Kotlin",
                    platform = "macOS",
                    ideName = "IntelliJ IDEA",
                    startTime = LocalDateTime.of(2026, 1, 1, 9, 0),
                    endTime = LocalDateTime.of(2026, 1, 1, 11, 0),
                    lastModified = LocalDateTime.now()
                ),
                CodingSession(
                    sessionUuid = UUID.randomUUID().toString(),
                    userId = "test-user",
                    projectName = "Project3",
                    language = "Kotlin",
                    platform = "macOS",
                    ideName = "IntelliJ IDEA",
                    startTime = LocalDateTime.of(2026, 1, 1, 14, 0),
                    endTime = LocalDateTime.of(2026, 1, 1, 16, 0),
                    lastModified = LocalDateTime.now()
                ),
                CodingSession(
                    sessionUuid = UUID.randomUUID().toString(),
                    userId = "test-user",
                    projectName = "Project4",
                    language = "Kotlin",
                    platform = "macOS",
                    ideName = "IntelliJ IDEA",
                    startTime = LocalDateTime.of(2026, 1, 1, 20, 0),
                    endTime = LocalDateTime.of(2026, 1, 1, 22, 0),
                    lastModified = LocalDateTime.now()
                )
            )
        )

        val distribution = statsRepository.getTimeOfDayDistribution()
        assertThat(distribution).hasSize(4)

        val nightUsage = distribution.first { it.timeOfDay == "Night" }
        assertThat(nightUsage.totalDuration.toHours()).isEqualTo(1)

        val morningUsage = distribution.first { it.timeOfDay == "Morning" }
        assertThat(morningUsage.totalDuration.toHours()).isEqualTo(2)

        val daytimeUsage = distribution.first { it.timeOfDay == "Daytime" }
        assertThat(daytimeUsage.totalDuration.toHours()).isEqualTo(2)

        val eveningUsage = distribution.first { it.timeOfDay == "Evening" }
        assertThat(eveningUsage.totalDuration.toHours()).isEqualTo(2)
    }

    @Test
    fun `getDailyCodingTimeForHeatmap should return daily summaries`() {
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
                    language = "Kotlin",
                    platform = "macOS",
                    ideName = "IntelliJ IDEA",
                    startTime = LocalDateTime.of(2026, 1, 2, 14, 0),
                    endTime = LocalDateTime.of(2026, 1, 2, 16, 0),
                    lastModified = LocalDateTime.now()
                )
            )
        )

        val heatmapData = statsRepository.getDailyCodingTimeForHeatmap(
            LocalDateTime.of(2026, 1, 1, 0, 0),
            LocalDateTime.of(2026, 1, 3, 0, 0)
        )

        assertThat(heatmapData).hasSize(2)
    }

    @Test
    fun `getOverallHourlyDistributionWithTotalDays should return hourly data`() {
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

        val result = statsRepository.getOverallHourlyDistributionWithTotalDays()
        assertThat(result.distribution).isNotEmpty
        assertThat(result.totalDays).isGreaterThan(0)
    }
}
