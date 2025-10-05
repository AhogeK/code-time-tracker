package com.ahogek.codetimetracker.database

import com.ahogek.codetimetracker.model.DailySummary
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.tuple
import org.junit.jupiter.api.*
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * @author AhogeK ahogek@gmail.com
 * @since 2025-10-04 01:34:23
 */
class DatabaseManagerTest {

    companion object {
        private const val TEST_DB_FILE = "build/test-dbs/coding_data_test.db"
        private const val TEST_DB_URL = "jdbc:sqlite:$TEST_DB_FILE"

        @BeforeAll
        @JvmStatic
        fun beforeAll() {
            Class.forName("org.sqlite.JDBC")
            File(TEST_DB_FILE).parentFile.mkdirs()
            val supplier: ConnectionSupplier = { DriverManager.getConnection(TEST_DB_URL) }
            DatabaseManager.setConnectionFactory(supplier.asFactory(), TEST_DB_URL)
        }
    }

    private lateinit var connection: Connection

    @BeforeEach
    fun setup() {
        connection = DriverManager.getConnection(TEST_DB_URL)
        connection.createStatement().use { st ->
            st.execute(
                """
                CREATE TABLE IF NOT EXISTS coding_sessions (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    session_uuid TEXT NOT NULL UNIQUE,
                    user_id TEXT NOT NULL,
                    project_name TEXT NOT NULL,
                    language TEXT NOT NULL,
                    platform TEXT NOT NULL,
                    start_time TEXT NOT NULL,
                    end_time TEXT NOT NULL,
                    last_modified TEXT NOT NULL,
                    is_deleted INTEGER NOT NULL DEFAULT 0
                );
                """.trimIndent()
            )
            st.execute("DELETE FROM coding_sessions WHERE is_deleted = 0;")
        }
    }

    @Test
    @DisplayName("getDailyCodingTimeForHeatmap: Should correctly aggregate durations by day and filter out-of-range data")
    fun testGetDailyCodingTimeForHeatmap() {
        // Data before the query range (should be excluded)
        insertSession("ProjectA", "Kotlin", "2025-10-01T09:00:00", "2025-10-01T09:30:00") // 30 mins

        // Data for Oct 2nd (within range)
        insertSession("ProjectB", "Java", "2025-10-02T10:00:00", "2025-10-02T10:15:00")   // 15 mins
        insertSession("ProjectA", "Kotlin", "2025-10-02T14:00:00", "2025-10-02T14:05:00") // 5 mins
        // Expected total for Oct 2nd = 20 mins

        // Data for Oct 3rd (within range)
        insertSession("ProjectC", "Python", "2025-10-03T23:50:00", "2025-10-03T23:59:00") // 9 mins

        // Data after the query range (should be excluded)
        insertSession("ProjectD", "Go", "2025-10-04T08:00:00", "2025-10-04T09:00:00")     // 60 mins

        // Define the query time range: from Oct 2, 00:00 (inclusive) to Oct 4, 00:00 (exclusive)
        val startTime = LocalDateTime.of(2025, 10, 2, 0, 0)
        val endTime = LocalDateTime.of(2025, 10, 4, 0, 0)

        val heatmapData: List<DailySummary> = DatabaseManager.getDailyCodingTimeForHeatmap(startTime, endTime)

        // The result list should contain exactly two entries (for Oct 2nd and Oct 3rd).
        assertThat(heatmapData).hasSize(2)

        // Verify the summary for October 2nd.
        // Using AssertJ's powerful filtering and extracting features for cleaner assertions.
        assertThat(heatmapData)
            .extracting(DailySummary::date, DailySummary::totalDuration)
            .contains(
                tuple(
                    LocalDate.of(2025, 10, 2),
                    Duration.ofMinutes(20)
                )
            )

        // Verify the summary for October 3rd.
        assertThat(heatmapData)
            .extracting("date", "totalDuration") // Another way to extract properties
            .contains(
                tuple(
                    LocalDate.of(2025, 10, 3),
                    Duration.ofMinutes(9)
                )
            )

        // Ensure that excluded dates are not present.
        val resultDates = heatmapData.map { it.date }
        assertThat(resultDates).doesNotContain(
            LocalDate.of(2025, 10, 1),
            LocalDate.of(2025, 10, 4)
        )
    }

    @Test
    @DisplayName("getDailyCodingTimeForHeatmap: Should return an empty list when no data is available")
    fun testGetDailyCodingTimeForHeatmap_whenNoData() {
        // Arrange: No data is inserted.
        val startTime = LocalDateTime.of(2025, 10, 1, 0, 0)
        val endTime = LocalDateTime.of(2025, 10, 2, 0, 0)

        // Act
        val heatmapData = DatabaseManager.getDailyCodingTimeForHeatmap(startTime, endTime)

        // Assert
        assertThat(heatmapData).isNotNull
        assertThat(heatmapData).isEmpty()
    }

    @Test
    @DisplayName("getCodingStreaks: Should return (0, 0) when database is empty")
    fun testGetCodingStreaks_noData() {
        // Act: Call the method on an empty database
        val streaks = DatabaseManager.getCodingStreaks()

        // Assert: Both current and max streaks should be zero
        assertThat(streaks.currentStreak).isZero()
        assertThat(streaks.maxStreak).isZero()
    }

    @Test
    @DisplayName("getCodingStreaks: Should correctly handle a single day of activity")
    fun testGetCodingStreaks_singleDayOfActivity() {
        // Arrange: Insert a session for yesterday
        val yesterday = LocalDate.now().minusDays(1).atTime(10, 0).toString()
        insertSession("ProjectA", "Kotlin", yesterday, yesterday)

        // Act
        val streaks = DatabaseManager.getCodingStreaks()

        // Assert: Since activity was yesterday, current streak is 1, and max is 1
        assertThat(streaks.currentStreak).isEqualTo(1)
        assertThat(streaks.maxStreak).isEqualTo(1)
    }

    @Test
    @DisplayName("getCodingStreaks: Should reset current streak if activity was two days ago")
    fun testGetCodingStreaks_brokenCurrentStreak() {
        // Arrange: Insert a session for two days ago
        val twoDaysAgo = LocalDate.now().minusDays(2).atTime(10, 0).toString()
        insertSession("ProjectA", "Kotlin", twoDaysAgo, twoDaysAgo)

        // Act
        val streaks = DatabaseManager.getCodingStreaks()

        // Assert: The streak is broken, so current is 0, but the max streak was 1
        assertThat(streaks.currentStreak).isZero()
        assertThat(streaks.maxStreak).isEqualTo(1)
    }

    @Test
    @DisplayName("getCodingStreaks: Should correctly calculate a perfect continuous streak ending today")
    fun testGetCodingStreaks_perfectContinuousStreak() {
        // Arrange: 4 continuous days of activity, ending today.
        // Also add multiple sessions on the same day to ensure it doesn't double-count days.
        val today = LocalDate.now()
        insertSession("P1", "Go", today.minusDays(3).atStartOfDay().toString(), "...")
        insertSession("P2", "Go", today.minusDays(2).atStartOfDay().toString(), "...")
        insertSession("P3", "Go", today.minusDays(1).atStartOfDay().toString(), "...")
        insertSession("P4", "Go", today.atStartOfDay().toString(), "...")
        insertSession("P5", "Rust", today.atTime(5, 0).toString(), "...") // Same day, different time

        // Act
        val streaks = DatabaseManager.getCodingStreaks()

        // Assert: Current and max streaks should both be 4
        assertThat(streaks.currentStreak).isEqualTo(4)
        assertThat(streaks.maxStreak).isEqualTo(4)
    }

    @Test
    @DisplayName("getCodingStreaks: Should correctly identify max streak when it is not the current one")
    fun testGetCodingStreaks_brokenLongestStreak() {
        // Arrange: A 5-day streak in the past, a gap, then a current 3-day streak.
        val today = LocalDate.now()

        // Current 3-day streak
        insertSession("Current", "Kotlin", today.minusDays(2).atStartOfDay().toString(), "...")
        insertSession("Current", "Kotlin", today.minusDays(1).atStartOfDay().toString(), "...")
        insertSession("Current", "Kotlin", today.atStartOfDay().toString(), "...")

        // A longer, 5-day streak in the past
        insertSession("Past", "Java", today.minusDays(10).atStartOfDay().toString(), "...")
        insertSession("Past", "Java", today.minusDays(9).atStartOfDay().toString(), "...")
        insertSession("Past", "Java", today.minusDays(8).atStartOfDay().toString(), "...")
        insertSession("Past", "Java", today.minusDays(7).atStartOfDay().toString(), "...")
        insertSession("Past", "Java", today.minusDays(6).atStartOfDay().toString(), "...")

        // Act
        val streaks = DatabaseManager.getCodingStreaks()

        // Assert: Current streak is 3, but the max streak should be 5
        assertThat(streaks.currentStreak).isEqualTo(3)
        assertThat(streaks.maxStreak).isEqualTo(5)
    }

    @Test
    @DisplayName("getDailyHourDistribution: Should correctly aggregate time by day of week and hour")
    fun testGetDailyHourDistribution() {
        // Arrange: Insert data across different days and hours.
        // Note: 2025-10-06 is a Monday (day 1). 2025-10-12 is a Sunday (day 7).

        // === Monday at 10:xx ===
        // Two sessions in the same hour block to test aggregation.
        insertSession("P1", "Kotlin", "2025-10-06T10:00:00", "2025-10-06T10:10:00") // 10 mins
        insertSession("P1", "Kotlin", "2025-10-06T10:30:00", "2025-10-06T10:35:00") // 5 mins
        // Expected total for Monday (1), 10:00 hour = 15 mins

        // === Monday at 14:xx ===
        insertSession("P2", "Java", "2025-10-06T14:00:00", "2025-10-06T14:20:00") // 20 mins

        // === Sunday at 23:xx ===
        // Test the day-of-week conversion (SQLite's 0 -> 7)
        insertSession("P3", "Python", "2025-10-12T23:55:00", "2025-10-12T23:59:00") // 4 mins

        // === Data outside the query range (should be ignored) ===
        insertSession("P4", "Go", "2025-10-05T10:00:00", "2025-10-05T10:10:00")

        val startTime = LocalDateTime.of(2025, 10, 6, 0, 0) // Monday
        val endTime = LocalDateTime.of(2025, 10, 13, 0, 0)  // Next Monday

        // Act
        val distribution = DatabaseManager.getDailyHourDistribution(startTime, endTime)

        // Assert
        // We expect 3 distinct hour blocks in the result.
        assertThat(distribution).hasSize(3)

        // Use AssertJ's `containsExactlyInAnyOrder` for robust, order-independent checking.
        // This checks that the list contains exactly these elements, regardless of their order.
        assertThat(distribution)
            .extracting("dayOfWeek", "hourOfDay", "totalDuration")
            .containsExactlyInAnyOrder(
                tuple(1, 10, Duration.ofMinutes(15)), // Monday, 10:00-10:59, Total 15 mins
                tuple(1, 14, Duration.ofMinutes(20)), // Monday, 14:00-14:59, Total 20 mins
                tuple(7, 23, Duration.ofMinutes(4))   // Sunday, 23:00-23:59, Total 4 mins
            )
    }

    @Test
    @DisplayName("getDailyHourDistribution: Should return an empty list when no data is in range")
    fun testGetDailyHourDistribution_empty() {
        // Arrange: Data is present, but outside the query range.
        insertSession("P1", "Kotlin", "2025-10-06T10:00:00", "2025-10-06T10:10:00")

        val startTime = LocalDateTime.of(2025, 11, 1, 0, 0)
        val endTime = LocalDateTime.of(2025, 11, 2, 0, 0)

        // Act
        val distribution = DatabaseManager.getDailyHourDistribution(startTime, endTime)

        // Assert
        assertThat(distribution).isNotNull
        assertThat(distribution).isEmpty()
    }

    @Test
    @DisplayName("getOverallHourlyDistribution: Should correctly aggregate time by hour across all days")
    fun testGetOverallHourlyDistribution() {
        // Arrange: Insert data for the same hour but on different days.

        // Day 1, 10:xx -> 10 mins
        insertSession("P1", "Kotlin", "2025-10-06T10:00:00", "2025-10-06T10:10:00")
        // Day 2, 10:xx -> 5 mins
        insertSession("P1", "Kotlin", "2025-10-07T10:30:00", "2025-10-07T10:35:00")
        // Expected total for 10:xx hour = 15 mins

        // Day 1, 14:xx -> 20 mins
        insertSession("P2", "Java", "2025-10-06T14:00:00", "2025-10-06T14:20:00")

        // Data outside the query range (should be ignored)
        insertSession("P4", "Go", "2025-10-05T10:00:00", "2025-10-05T10:10:00")

        val startTime = LocalDateTime.of(2025, 10, 6, 0, 0)
        val endTime = LocalDateTime.of(2025, 10, 8, 0, 0)

        // Act
        val distribution = DatabaseManager.getOverallHourlyDistribution(startTime, endTime)

        // Assert
        assertThat(distribution).hasSize(2) // Expect 2 distinct hour blocks (10:xx and 14:xx)

        assertThat(distribution)
            .extracting("hourOfDay", "totalDuration")
            .containsExactlyInAnyOrder(
                tuple(10, Duration.ofMinutes(15)), // Total for 10:xx hour is 10 + 5 = 15 mins
                tuple(14, Duration.ofMinutes(20))  // Total for 14:xx hour is 20 mins
            )
    }

    @Test
    @DisplayName("getLanguageDistribution: Should correctly aggregate time by language")
    fun testGetLanguageDistribution() {
        // Arrange: Insert data for multiple languages.

        // === Kotlin: 10 mins + 5 mins = 15 mins total ===
        insertSession("ProjectA", "Kotlin", "2025-10-06T10:00:00", "2025-10-06T10:10:00")
        insertSession("ProjectB", "Kotlin", "2025-10-07T11:00:00", "2025-10-07T11:05:00")

        // === Java: 30 mins total ===
        insertSession("ProjectC", "Java", "2025-10-08T14:00:00", "2025-10-08T14:30:00")

        // === Data outside the query range (Python) ===
        insertSession("ProjectD", "Python", "2025-10-05T10:00:00", "2025-10-05T10:10:00")

        val startTime = LocalDateTime.of(2025, 10, 6, 0, 0)
        val endTime = LocalDateTime.of(2025, 10, 9, 0, 0)

        // Act
        val distribution = DatabaseManager.getLanguageDistribution(startTime, endTime)

        // Assert
        // We expect 2 languages (Kotlin and Java) in the result. Python should be excluded.
        assertThat(distribution).hasSize(2)

        // Check the contents regardless of order.
        assertThat(distribution)
            .extracting("language", "totalDuration")
            .containsExactlyInAnyOrder(
                tuple("Kotlin", Duration.ofMinutes(15)),
                tuple("Java", Duration.ofMinutes(30))
            )
    }

    @Test
    @DisplayName("getLanguageDistribution: Should return a sorted list by duration descending")
    fun testGetLanguageDistribution_isSorted() {
        // Arrange: Insert data with clear duration differences.
        insertSession("ProjectA", "Java", "2025-10-06T10:00:00", "2025-10-06T10:30:00") // 30 mins
        insertSession("ProjectB", "Kotlin", "2025-10-06T11:00:00", "2025-10-06T11:10:00") // 10 mins
        insertSession("ProjectC", "Go", "2025-10-06T12:00:00", "2025-10-06T12:50:00") // 50 mins

        val startTime = LocalDateTime.of(2025, 10, 6, 0, 0)
        val endTime = LocalDateTime.of(2025, 10, 7, 0, 0)

        // Act
        val distribution = DatabaseManager.getLanguageDistribution(startTime, endTime)

        // Assert
        // Verify that the list is sorted correctly by duration in descending order.
        val languages = distribution.map { it.language }
        assertThat(languages).containsExactly("Go", "Java", "Kotlin")
    }

    @Test
    @DisplayName("getProjectDistribution: Should correctly aggregate time by project name")
    fun testGetProjectDistribution() {
        // Arrange: Insert data for multiple projects.

        // === ProjectA: 10 mins (Kotlin) + 20 mins (Java) = 30 mins total ===
        insertSession("ProjectA", "Kotlin", "2025-10-06T10:00:00", "2025-10-06T10:10:00")
        insertSession("ProjectA", "Java", "2025-10-07T11:00:00", "2025-10-07T11:20:00")

        // === ProjectB: 5 mins total ===
        insertSession("ProjectB", "Kotlin", "2025-10-08T14:00:00", "2025-10-08T14:05:00")

        // === Data outside the query range (ProjectC) ===
        insertSession("ProjectC", "Python", "2025-10-05T10:00:00", "2025-10-05T10:10:00")

        val startTime = LocalDateTime.of(2025, 10, 6, 0, 0)
        val endTime = LocalDateTime.of(2025, 10, 9, 0, 0)

        // Act
        val distribution = DatabaseManager.getProjectDistribution(startTime, endTime)

        // Assert
        // We expect 2 projects (ProjectA and ProjectB) in the result. ProjectC should be excluded.
        assertThat(distribution).hasSize(2)

        // Check that the list is sorted by duration descending and contains the correct totals.
        assertThat(distribution)
            .extracting("projectName", "totalDuration")
            .containsExactly(
                tuple("ProjectA", Duration.ofMinutes(30)),
                tuple("ProjectB", Duration.ofMinutes(5))
            )
    }

    @Test
    @DisplayName("getProjectDistribution: Should return an empty list when no data is in range")
    fun testGetProjectDistribution_empty() {
        // Arrange
        insertSession("ProjectA", "Kotlin", "2025-10-06T10:00:00", "2025-10-06T10:10:00")

        val startTime = LocalDateTime.of(2025, 11, 1, 0, 0)
        val endTime = LocalDateTime.of(2025, 11, 2, 0, 0)

        // Act
        val distribution = DatabaseManager.getProjectDistribution(startTime, endTime)

        // Assert
        assertThat(distribution).isNotNull
        assertThat(distribution).isEmpty()
    }

    @Test
    @DisplayName("getTimeOfDayDistribution: Should correctly categorize and aggregate time into four periods")
    fun testGetTimeOfDayDistribution() {
        // Arrange: Insert data covering all four time periods.

        // === Night (00:00 - 05:59) ===
        insertSession("ProjectA", "Rust", "2025-10-06T04:00:00", "2025-10-06T04:30:00") // 30 mins

        // === Morning (06:00 - 11:59) ===
        insertSession("ProjectB", "Kotlin", "2025-10-06T09:10:00", "2025-10-06T09:20:00") // 10 mins
        insertSession("ProjectA", "Kotlin", "2025-10-07T11:50:00", "2025-10-07T11:55:00") // 5 mins
        // Expected Morning total = 15 mins

        // === Daytime (12:00 - 17:59) ===
        insertSession("ProjectC", "Java", "2025-10-08T15:00:00", "2025-10-08T16:00:00") // 60 mins

        // === Evening (18:00 - 23:59) ===
        insertSession("ProjectD", "Go", "2025-10-08T22:00:00", "2025-10-08T22:40:00") // 40 mins

        // === Data outside the range (should be ignored) ===
        insertSession("ProjectE", "C++", "2025-10-05T12:00:00", "2025-10-05T13:00:00")

        val startTime = LocalDateTime.of(2025, 10, 6, 0, 0)
        val endTime = LocalDateTime.of(2025, 10, 9, 0, 0)

        // Act
        val distribution = DatabaseManager.getTimeOfDayDistribution(startTime, endTime)

        // Assert
        // We expect all four periods to be present.
        assertThat(distribution).hasSize(4)

        // Check the contents regardless of their order.
        assertThat(distribution)
            .extracting("timeOfDay", "totalDuration")
            .containsExactlyInAnyOrder(
                tuple("Night", Duration.ofMinutes(30)),
                tuple("Morning", Duration.ofMinutes(15)),
                tuple("Daytime", Duration.ofMinutes(60)),
                tuple("Evening", Duration.ofMinutes(40))
            )
    }

    @Test
    @DisplayName("getTimeOfDayDistribution: Should only return periods with actual data")
    fun testGetTimeOfDayDistribution_missingPeriods() {
        // Arrange: Insert data for only two periods
        insertSession("ProjectA", "Rust", "2025-10-06T04:00:00", "2025-10-06T04:30:00") // Night
        insertSession("ProjectB", "Kotlin", "2025-10-06T09:10:00", "2025-10-06T09:20:00") // Morning

        val startTime = LocalDateTime.of(2025, 10, 6, 0, 0)
        val endTime = LocalDateTime.of(2025, 10, 7, 0, 0)

        // Act
        val distribution = DatabaseManager.getTimeOfDayDistribution(startTime, endTime)

        // Assert
        // We expect only two periods in the result.
        assertThat(distribution).hasSize(2)
        assertThat(distribution)
            .extracting("timeOfDay")
            .containsExactlyInAnyOrder("Night", "Morning")
    }

    @AfterEach
    fun tearDown() {
        connection.close()
    }

    private fun insertSession(project: String, lang: String, start: String, end: String) {
        val sql =
            "INSERT INTO coding_sessions (session_uuid, user_id, project_name, language, platform, start_time, end_time, last_modified) VALUES (?, ?, ?, ?, ?, ?, ?, ?)"
        connection.prepareStatement(sql).use {
            it.setString(1, java.util.UUID.randomUUID().toString())
            it.setString(2, "test-user")
            it.setString(3, project)
            it.setString(4, lang)
            it.setString(5, "test-platform")
            it.setString(6, start)
            it.setString(7, end)
            it.setString(8, LocalDateTime.now().toString())
            it.executeUpdate()
        }
    }
}