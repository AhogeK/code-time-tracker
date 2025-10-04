package com.ahogek.codetimetracker.database

import com.ahogek.codetimetracker.model.DailySummary
import org.assertj.core.api.Assertions
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
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

    private lateinit var connection: Connection
    private lateinit var originalDbUrl: String

    // A constant for our special test URL
    private val testDbUrl = "jdbc:sqlite:file:testdb?mode=memory&cache=shared"

    @BeforeEach
    fun setup() {
        // 1. Store the original dbUrl from the singleton
        originalDbUrl = DatabaseManager.dbUrl

        // 2. **Crucial Change**: Use the shared-cache in-memory URL.
        // Now, any part of the code that uses this exact URL will connect
        // to the SAME in-memory database instance.
        DatabaseManager.dbUrl = testDbUrl
        connection = DriverManager.getConnection(testDbUrl)

        // This ensures that before each test runs, any existing table from a
        // previous test is wiped clean, guaranteeing a fresh start.
        connection.createStatement().execute("DROP TABLE IF EXISTS coding_sessions")

        val createTableSql = """
            CREATE TABLE coding_sessions (
                id INTEGER PRIMARY KEY AUTOINCREMENT, session_uuid TEXT NOT NULL UNIQUE, user_id TEXT NOT NULL,
                project_name TEXT NOT NULL, language TEXT NOT NULL, platform TEXT NOT NULL,
                start_time TEXT NOT NULL, end_time TEXT NOT NULL, last_modified TEXT NOT NULL,
                is_deleted INTEGER NOT NULL DEFAULT 0
            );
        """
        connection.createStatement().execute(createTableSql)
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
                Assertions.tuple(
                    LocalDate.of(2025, 10, 2),
                    Duration.ofMinutes(20)
                )
            )

        // Verify the summary for October 3rd.
        assertThat(heatmapData)
            .extracting("date", "totalDuration") // Another way to extract properties
            .contains(
                Assertions.tuple(
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

    @AfterEach
    fun tearDown() {
        connection.close()
        // Restore the original dbUrl to avoid side-effects on other tests
        DatabaseManager.dbUrl = originalDbUrl
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