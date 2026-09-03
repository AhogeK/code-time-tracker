package com.ahogek.codetimetracker.database

import com.ahogek.codetimetracker.model.CodingSession
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.Duration
import java.time.LocalDate
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
        sessionRepository.upsertSyncedSessions(
            listOf(syncedSession("a-1", LocalDateTime.of(2026, 1, 1, 9, 0), LocalDateTime.of(2026, 1, 1, 11, 0))),
            ownerUserId = "user-a",
        )
        sessionRepository.upsertSyncedSessions(
            listOf(syncedSession("b-1", LocalDateTime.of(2026, 1, 2, 9, 0), LocalDateTime.of(2026, 1, 2, 10, 0))),
            ownerUserId = "user-b",
        )

        statsRepository.ownerUserId = "user-a"
        assertThat(statsRepository.getTotalCodingTime()).isEqualTo(Duration.ofHours(2))

        statsRepository.ownerUserId = null
        assertThat(statsRepository.getTotalCodingTime()).isEqualTo(Duration.ofHours(3))
    }

    @Test
    fun `getTotalCodingTime should include unowned local sessions for the bound user`() {
        sessionRepository.upsertSyncedSessions(
            listOf(syncedSession("a-1", LocalDateTime.of(2026, 1, 1, 9, 0), LocalDateTime.of(2026, 1, 1, 11, 0))),
            ownerUserId = "user-a",
        )
        // A locally created, not-yet-synced session carries no owner (is_synced = 0,
        // owner_user_id = NULL); it is pushed to the bound account on the next sync,
        // so it must count in that account's statistics immediately.
        sessionRepository.importSessions(
            listOf(syncedSession("local-1", LocalDateTime.of(2026, 1, 3, 9, 0), LocalDateTime.of(2026, 1, 3, 10, 0))),
        )
        sessionRepository.upsertSyncedSessions(
            listOf(syncedSession("b-1", LocalDateTime.of(2026, 1, 2, 9, 0), LocalDateTime.of(2026, 1, 2, 10, 0))),
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
    fun `getDailyCodingTimeForHeatmap should merge overlapping sessions within a day`() {
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
                    startTime = LocalDateTime.of(2026, 1, 1, 11, 0),
                    endTime = LocalDateTime.of(2026, 1, 1, 13, 0),
                    lastModified = LocalDateTime.now()
                )
            )
        )

        val heatmapData = statsRepository.getDailyCodingTimeForHeatmap(
            LocalDateTime.of(2026, 1, 1, 0, 0),
            LocalDateTime.of(2026, 1, 2, 0, 0)
        )

        assertThat(heatmapData).hasSize(1)
        // Raw sum would be 4h; merged union of 10:00-13:00 is 3h.
        assertThat(heatmapData[0].totalDuration).isEqualTo(Duration.ofHours(3))
    }

    @Test
    fun `getDailyCodingTimeForHeatmap should merge a session spanning midnight per day`() {
        sessionRepository.importSessions(
            listOf(
                CodingSession(
                    sessionUuid = UUID.randomUUID().toString(),
                    userId = "test-user",
                    projectName = "Project1",
                    language = "Kotlin",
                    platform = "macOS",
                    ideName = "IntelliJ IDEA",
                    startTime = LocalDateTime.of(2026, 1, 1, 23, 0),
                    endTime = LocalDateTime.of(2026, 1, 2, 1, 0),
                    lastModified = LocalDateTime.now()
                )
            )
        )

        val heatmapData = statsRepository.getDailyCodingTimeForHeatmap(
            LocalDateTime.of(2026, 1, 1, 0, 0),
            LocalDateTime.of(2026, 1, 3, 0, 0)
        )

        assertThat(heatmapData).hasSize(2)
        assertThat(heatmapData[0].date).isEqualTo(LocalDate.of(2026, 1, 1))
        assertThat(heatmapData[0].totalDuration).isEqualTo(Duration.ofHours(1))
        assertThat(heatmapData[1].date).isEqualTo(LocalDate.of(2026, 1, 2))
        assertThat(heatmapData[1].totalDuration).isEqualTo(Duration.ofHours(1))
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
    fun `getDailyHourDistribution should not double count overlapping sessions in the same hour`() {
        // Two parallel IDE windows: 10:00-11:00 and 10:30-10:45. The union of the
        // 10:00-11:00 hour is exactly one hour, not 1h15m (sum of both windows).
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
                    endTime = LocalDateTime.of(2026, 1, 1, 11, 0),
                    lastModified = LocalDateTime.now()
                ),
                CodingSession(
                    sessionUuid = UUID.randomUUID().toString(),
                    userId = "test-user",
                    projectName = "Project2",
                    language = "Kotlin",
                    platform = "macOS",
                    ideName = "IntelliJ IDEA",
                    startTime = LocalDateTime.of(2026, 1, 1, 10, 30),
                    endTime = LocalDateTime.of(2026, 1, 1, 10, 45),
                    lastModified = LocalDateTime.now()
                )
            )
        )

        val distribution = statsRepository.getDailyHourDistribution(
            LocalDateTime.of(2026, 1, 1, 0, 0),
            LocalDateTime.of(2026, 1, 2, 0, 0)
        )

        // Thursday (2026-01-01) hour 10 = 3600s (merged union), not 3900s.
        val thursdayHour10 = distribution.first { it.dayOfWeek == 4 && it.hourOfDay == 10 }
        assertThat(thursdayHour10.totalDuration.toSeconds()).isEqualTo(3600)
    }

    @Test
    fun `getDailyHourDistribution should merge overlapping sessions across adjacent hours`() {
        // Session A 10:00-11:30 and session B 11:00-12:00 overlap at 11:00-11:30.
        // Union: 10:00-12:00 => hour 10 = 3600s, hour 11 = 3600s (not 3600+1800).
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
                    endTime = LocalDateTime.of(2026, 1, 1, 11, 30),
                    lastModified = LocalDateTime.now()
                ),
                CodingSession(
                    sessionUuid = UUID.randomUUID().toString(),
                    userId = "test-user",
                    projectName = "Project2",
                    language = "Kotlin",
                    platform = "macOS",
                    ideName = "IntelliJ IDEA",
                    startTime = LocalDateTime.of(2026, 1, 1, 11, 0),
                    endTime = LocalDateTime.of(2026, 1, 1, 12, 0),
                    lastModified = LocalDateTime.now()
                )
            )
        )

        val distribution = statsRepository.getDailyHourDistribution(
            LocalDateTime.of(2026, 1, 1, 0, 0),
            LocalDateTime.of(2026, 1, 2, 0, 0)
        )

        val hour10 = distribution.first { it.dayOfWeek == 4 && it.hourOfDay == 10 }
        val hour11 = distribution.first { it.dayOfWeek == 4 && it.hourOfDay == 11 }
        assertThat(hour10.totalDuration.toSeconds()).isEqualTo(3600)
        assertThat(hour11.totalDuration.toSeconds()).isEqualTo(3600)
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
