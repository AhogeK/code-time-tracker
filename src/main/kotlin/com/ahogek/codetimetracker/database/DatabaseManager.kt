package com.ahogek.codetimetracker.database

import com.ahogek.codetimetracker.model.*
import com.ahogek.codetimetracker.user.UserManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.Logger
import java.sql.Connection
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Manages all database operations, including connection, table creation,
 * and data persistence for coding sessions.
 *
 * @author AhogeK ahogek@gmail.com
 * @since 2025-09-29 20:49:29
 */
object DatabaseManager {

    private data class DbConfig(
        val url: String,
        val factory: ConnectionFactory
    )

    @Volatile
    private var config: DbConfig

    private val log = Logger.getInstance(DatabaseManager::class.java)
    private val dateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME

    // This executor will handle all database write operations sequentially on a background thread
    private val databaseExecutor = Executors.newSingleThreadExecutor()

    init {
        try {
            Class.forName("org.sqlite.JDBC")
        } catch (e: ClassNotFoundException) {
            log.error("Failed to load SQLite JDBC driver.", e)
        }

        val commonDataPath = PathManager.getCommonDataPath()
        val dbPath = commonDataPath.resolve("code-time-tracker-data")
        val dbFile = dbPath.resolve("coding_data.db").toFile()
        dbFile.parentFile.mkdirs()
        val dbUrl = "jdbc:sqlite:${dbFile.absolutePath}"
        config = DbConfig(
            url = dbUrl,
            factory = DriverManagerConnectionFactory(dbUrl)
        )
        log.info("Database initialized at official shared location: ${config.url}")

        createTableIfNotExists()
    }

    fun setConnectionFactory(factory: ConnectionFactory, urlHint: String? = null) {
        val newUrl = urlHint ?: config.url
        val normalizedUrl = normalizeJdbcUrl(newUrl)

        config = DbConfig(
            url = normalizedUrl,
            factory = factory
        )

        createTableIfNotExists()
    }

    private fun normalizeJdbcUrl(url: String): String {
        val trimmed = url.trim()
        return if (trimmed.startsWith("jdbc:sqlite:")) trimmed else "jdbc:sqlite:$trimmed"
    }

    private inline fun <T> withConnection(block: (Connection) -> T): T {
        val local = config
        return local.factory.getConnection().use { conn -> block(conn) }
    }

    /**
     * An explicit initialization method. Calling this ensures that the singleton's
     * init block has been executed and the database is ready.
     */
    fun initialize() {
        // This method can be empty. Its sole purpose is to provide a clear
        // entry point to trigger the object's initialization.
    }

    private fun createTableIfNotExists() {
        val sql = """
            CREATE TABLE IF NOT EXISTS coding_sessions (
            -- Primary Key. A simple auto-incrementing integer for local use.
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            
            -- Globally Unique ID. A UUID for the session record itself. Critical for merging and syncing.
            session_uuid TEXT NOT NULL UNIQUE,
            
            -- The unique ID of the user/installation. Prevents data contamination between different users.
            user_id TEXT NOT NULL,
            
            -- The display name of the project.
            project_name TEXT NOT NULL,
            
            -- The programming language used.
            language TEXT NOT NULL,
            
            -- The user's operating system (e.g., "macOS Sonoma").
            platform TEXT NOT NULL,
            
            -- The session's start time, in ISO-8601 format.
            start_time TEXT NOT NULL,
            
            -- The session's end time, in ISO-8601 format.
            end_time TEXT NOT NULL,
            
            -- Timestamp for syncing. Used for conflict resolution during merges.
            last_modified TEXT NOT NULL,
            
            -- Soft Delete Flag. A boolean (0 or 1) for marking records as deleted.
            is_deleted INTEGER NOT NULL DEFAULT 0
        );
        """

        val indexSql = "CREATE UNIQUE INDEX IF NOT EXISTS idx_session_uuid ON coding_sessions(session_uuid);"

        try {
            withConnection { conn ->
                conn.createStatement().execute(sql)
                conn.createStatement().execute(indexSql)
                log.info("Database table 'coding_sessions' is ready.")
            }
        } catch (e: Exception) {
            log.error("Failed to create database table.", e)
        }
    }

    fun saveSessions(sessions: List<CodingSession>, onComplete: () -> Unit) {
        if (sessions.isEmpty()) {
            onComplete()
            return
        }

        databaseExecutor.execute {
            val sql = """
            INSERT INTO coding_sessions(
                session_uuid, user_id, project_name, language, platform,
                start_time, end_time, last_modified
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?);
            """

            try {
                withConnection { conn ->
                    conn.autoCommit = false // Use a transaction for batch inserts
                    val pstmt = conn.prepareStatement(sql)
                    val currentTimestamp = dateTimeFormatter.format(LocalDateTime.now())
                    val userId = UserManager.getUserId()

                    for (session in sessions) {
                        pstmt.setString(1, UUID.randomUUID().toString())
                        pstmt.setString(2, userId)
                        pstmt.setString(3, session.projectName)
                        pstmt.setString(4, session.language)
                        pstmt.setString(5, session.platform)
                        pstmt.setString(6, dateTimeFormatter.format(session.startTime))
                        pstmt.setString(7, dateTimeFormatter.format(session.endTime))
                        pstmt.setString(8, currentTimestamp)
                        pstmt.addBatch()
                    }
                    pstmt.executeBatch()
                    conn.commit()
                    log.info("Successfully saved ${sessions.size} sessions to the database.")
                }
            } catch (e: Exception) {
                log.error("Failed to save sessions to the database.", e)
            } finally {
                ApplicationManager.getApplication().invokeLater {
                    onComplete()
                }
            }
        }
    }

    /**
     * Shuts down the database executor gracefully
     * It stops accepting new tasks and waits for pending tasks to complete
     */
    fun shutdown() {
        log.info("Shutting down DatabaseManager...")
        // Prevent new tasks from being submitted
        databaseExecutor.shutdown()
        try {
            // Wait a maximum of 5 seconds for existing tasks to terminate
            if (!databaseExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                log.warn("Database executor did not terminate in the specified time.")
                // Forcibly shut down if tasks are still running
                databaseExecutor.shutdownNow()
            }
        } catch (e: InterruptedException) {
            log.warn("Database shutdown was interrupted.", e)
            databaseExecutor.shutdownNow()
        }
        log.info("DatabaseManager has been shut down.")
    }

    /**
     * Fetched the user ID from the database, if not exists.
     * It assumes all records in the database belong to the same user.
     *
     * @return The user ID string found in the database, or null if the database is empty.
     */
    fun getUserIdFromDatabase(): String? {
        val sql = "SELECT user_id FROM coding_sessions LIMIT 1"
        return try {
            withConnection { conn ->
                conn.createStatement().executeQuery(sql).use { rs ->
                    if (rs.next()) rs.getString("user_id")
                    else null // Database is empty
                }
            }
        } catch (e: Exception) {
            log.error("Failed to get user ID from database.", e)
            null
        }
    }

    /**
     * Efficiently retrieves and calculates the total encoding duration from the database.
     * This method leverages SQL's SUM and strftime functions, offloading the calculation
     * entirely to the database and avoiding extensive data processing on the client side.
     *
     * @param projectName Optional project name to filter by
     * @return The total duration of all encoding activities (Duration).
     */
    fun getTotalCodingTime(projectName: String? = null): Duration {
        // Uses strftime('%s', column) to convert the time string into a Unix timestamp (seconds).
        // Then, it subtracts to get the number of seconds for each session, and finally uses the SUM() function to get the total.
        val baseSql =
            "SELECT SUM(strftime('%s', end_time) - strftime('%s', start_time)) FROM coding_sessions WHERE is_deleted = 0"
        val sql = if (projectName != null) "$baseSql AND project_name = ?" else baseSql
        var totalSeconds = 0L
        try {
            withConnection { conn ->
                conn.prepareStatement(sql).use { pstmt ->
                    projectName?.let { pstmt.setString(1, it) }
                    pstmt.executeQuery().use { rs ->
                        if (rs.next()) {
                            totalSeconds = rs.getLong(1)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            log.error("Failed to get total coding time from database.", e)
        }
        return Duration.ofSeconds(totalSeconds)
    }

    /**
     * Calculates the total coding duration within a  specific time range.
     * This method is versatile and can be used for daily, weekly, monthly, or any custom period reports.
     *
     * @param startTime The beginning of the time period (inclusive)
     * @param endTime The end of the time period (exclusive)
     * @param projectName Optional project name to filter by.
     * @return The total duration of coding activities within the specified range
     */
    fun getCodingTimeForPeriod(
        startTime: LocalDateTime,
        endTime: LocalDateTime,
        projectName: String? = null
    ): Duration {
        // The SQL query now includes a WHERE clause to filter sessions based on their start time.
        val baseSql = """
        SELECT SUM(strftime('%s', end_time) - strftime('%s', start_time))
        FROM coding_sessions
        WHERE is_deleted = 0 AND start_time >= ? AND start_time < ?
        """
        val sql = if (projectName != null) "$baseSql AND project_name = ?" else baseSql
        var totalSeconds = 0L
        try {
            withConnection { conn ->
                conn.prepareStatement(sql).use { pstmt ->
                    // Set the start and end time parameters in the query.
                    pstmt.setString(1, dateTimeFormatter.format(startTime))
                    pstmt.setString(2, dateTimeFormatter.format(endTime))
                    projectName?.let { pstmt.setString(3, it) }
                    pstmt.executeQuery().use { rs ->
                        if (rs.next()) {
                            totalSeconds = rs.getLong(1)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            log.error("Failed to get coding time for period from database.", e)
        }
        return Duration.ofSeconds(totalSeconds)
    }

    /**
     * Fetches daily coding time for a specific time range, suitable for a heatmap
     *
     * @param startTime The start of the time range
     * @param endTime The end of the time range
     * @return A list of DailySummary objects, each representing a day with coding activity.
     */
    fun getDailyCodingTimeForHeatmap(startTime: LocalDateTime, endTime: LocalDateTime): List<DailySummary> {
        val sql = """
            SELECT
                DATE(start_time) as coding_date,
                SUM(strftime('%s', end_time) - strftime('%s', start_time)) as total_seconds
            FROM coding_sessions
            WHERE is_deleted = 0 AND start_time >= ? AND start_time < ?
            GROUP BY coding_date
            ORDER BY coding_date;
        """
        val dailySummaries = mutableListOf<DailySummary>()
        try {
            withConnection { conn ->
                conn.prepareStatement(sql).use { pstmt ->
                    pstmt.setString(1, dateTimeFormatter.format(startTime))
                    pstmt.setString(2, dateTimeFormatter.format(endTime))
                    pstmt.executeQuery().use { rs ->
                        while (rs.next()) {
                            val date = LocalDate.parse(rs.getString("coding_date"))
                            val totalSeconds = rs.getLong("total_seconds")
                            dailySummaries.add(DailySummary(date, Duration.ofSeconds(totalSeconds)))
                        }
                    }
                }
            }
        } catch (e: Exception) {
            log.error("Failed to get daily coding time for heatmap.", e)
        }
        return dailySummaries
    }

    /**
     * Calculates the current and maximum consecutive coding streaks within a given time range.
     *
     * @param startTime The start of the time range.
     * @param endTime The end of the time range.
     * @return A CodingStreaks object containing the current and maximum streaks.
     */
    fun getCodingStreaks(startTime: LocalDateTime, endTime: LocalDateTime): CodingStreaks {
        val sql = """
            SELECT DISTINCT DATE(start_time) as coding_date
            FROM coding_sessions
            WHERE is_deleted = 0 AND start_time >= ? AND start_time < ?
            ORDER BY coding_date DESC;
        """
        val codingDates = mutableListOf<LocalDate>()
        try {
            withConnection { conn ->
                conn.prepareStatement(sql).use { pstmt ->
                    pstmt.setString(1, dateTimeFormatter.format(startTime))
                    pstmt.setString(2, dateTimeFormatter.format(endTime))
                    pstmt.executeQuery().use { rs ->
                        while (rs.next()) {
                            codingDates.add(LocalDate.parse(rs.getString("coding_date")))
                        }
                    }
                }
            }
        } catch (e: Exception) {
            log.error("Failed to get coding dates for streaks.", e)
            return CodingStreaks(0, 0)
        }

        return calculateCodingStreaks(codingDates)
    }

    /**
     * Calculates the current and maximum consecutive coding streaks
     *
     * @return A CodingStreaks object containing the current and maximum streaks
     */
    fun getCodingStreaks(): CodingStreaks {
        val endTime = LocalDateTime.now()
        val startTime = endTime.minusYears(1)
        return getCodingStreaks(startTime, endTime)
    }

    /**
     * Fetches the coding time distribution by hour for each day of the week, for a given period.
     *
     * @param startTime The start of the time range.
     * @param endTime The end of the time range.
     * @return A list of HourlyDistribution objects.
     */
    fun getDailyHourDistribution(startTime: LocalDateTime, endTime: LocalDateTime): List<HourlyDistribution> {
        val sql = """
            SELECT
                CAST(strftime('%w', start_time) AS INTEGER) as day_of_week, -- SQLite: 0=Sunday, 1=Monday,...
                CAST(strftime('%H', start_time) AS INTEGER) as hour_of_day,
                SUM(strftime('%s', end_time) - strftime('%s', start_time)) as total_seconds
            FROM coding_sessions
            WHERE is_deleted = 0 AND start_time >= ? AND start_time < ?
            GROUP BY day_of_week, hour_of_day;
        """
        val distribution = mutableListOf<HourlyDistribution>()
        try {
            withConnection { conn ->
                conn.prepareStatement(sql).use { pstmt ->
                    pstmt.setString(1, dateTimeFormatter.format(startTime))
                    pstmt.setString(2, dateTimeFormatter.format(endTime))
                    pstmt.executeQuery().use { rs ->
                        while (rs.next()) {
                            var dayOfWeek = rs.getInt("day_of_week")
                            // Standardize to ISO 8601 week date system: 1=Monday, ..., 7=Sunday
                            if (dayOfWeek == 0) {
                                dayOfWeek = 7
                            }
                            val hour = rs.getInt("hour_of_day")
                            val seconds = rs.getLong("total_seconds")
                            distribution.add(
                                HourlyDistribution(dayOfWeek, hour, Duration.ofSeconds(seconds))
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            log.error("Failed to get daily hour distribution.", e)
        }
        return distribution
    }

    /**
     * Fetches the total coding time distribution by hour, aggregated across all days in a given period.
     * This is ideal for generating a "typical day" activity chart.
     *
     * @param startTime The start of the time range.
     * @param endTime The end of the time range.
     * @return A list of HourlyUsage objects.
     */
    fun getOverallHourlyDistribution(startTime: LocalDateTime, endTime: LocalDateTime): List<HourlyUsage> {
        val sql = """
            SELECT
                CAST(strftime('%H', start_time) AS INTEGER) as hour_of_day,
                SUM(strftime('%s', end_time) - strftime('%s', start_time)) as total_seconds
            FROM coding_sessions
            WHERE is_deleted = 0 AND start_time >= ? AND start_time < ?
            GROUP BY hour_of_day
            ORDER BY hour_of_day;
        """
        val distribution = mutableListOf<HourlyUsage>()
        try {
            withConnection { conn ->
                conn.prepareStatement(sql).use { pstmt ->
                    pstmt.setString(1, dateTimeFormatter.format(startTime))
                    pstmt.setString(2, dateTimeFormatter.format(endTime))
                    pstmt.executeQuery().use { rs ->
                        while (rs.next()) {
                            val hour = rs.getInt("hour_of_day")
                            val seconds = rs.getLong("total_seconds")
                            distribution.add(
                                HourlyUsage(hour, Duration.ofSeconds(seconds))
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            log.error("Failed to get overall hourly distribution.", e)
        }
        return distribution
    }

    /**
     * Fetches the total coding time for each programming language within a given time range.
     *
     * @param startTime The start of the time range.
     * @param endTime The end of the time range.
     * @return A list of LanguageUsage objects, typically ordered from the most used to the least.
     */
    fun getLanguageDistribution(startTime: LocalDateTime, endTime: LocalDateTime): List<LanguageUsage> {
        val sql = """
            SELECT
                language,
                SUM(strftime('%s', end_time) - strftime('%s', start_time)) as total_seconds
            FROM coding_sessions
            WHERE is_deleted = 0 AND start_time >= ? AND start_time < ?
            GROUP BY language
            ORDER BY total_seconds DESC;
        """
        val distribution = mutableListOf<LanguageUsage>()
        try {
            withConnection { conn ->
                conn.prepareStatement(sql).use { pstmt ->
                    pstmt.setString(1, dateTimeFormatter.format(startTime))
                    pstmt.setString(2, dateTimeFormatter.format(endTime))
                    pstmt.executeQuery().use { rs ->
                        while (rs.next()) {
                            val language = rs.getString("language")
                            val seconds = rs.getLong("total_seconds")
                            distribution.add(
                                LanguageUsage(language, Duration.ofSeconds(seconds))
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            log.error("Failed to get language distribution.", e)
        }
        return distribution
    }

    /**
     * Fetches the total coding time for each project within a given time range.
     *
     * @param startTime The start of the time range.
     * @param endTime The end of the time range.
     * @return A list of ProjectUsage objects, ordered from the most worked-on to the least.
     */
    fun getProjectDistribution(startTime: LocalDateTime, endTime: LocalDateTime): List<ProjectUsage> {
        val sql = """
            SELECT
                project_name,
                SUM(strftime('%s', end_time) - strftime('%s', start_time)) as total_seconds
            FROM coding_sessions
            WHERE is_deleted = 0 AND start_time >= ? AND start_time < ?
            GROUP BY project_name
            ORDER BY total_seconds DESC;
        """
        val distribution = mutableListOf<ProjectUsage>()
        try {
            withConnection { conn ->
                conn.prepareStatement(sql).use { pstmt ->
                    pstmt.setString(1, dateTimeFormatter.format(startTime))
                    pstmt.setString(2, dateTimeFormatter.format(endTime))
                    pstmt.executeQuery().use { rs ->
                        while (rs.next()) {
                            val projectName = rs.getString("project_name")
                            val seconds = rs.getLong("total_seconds")
                            distribution.add(
                                ProjectUsage(projectName, Duration.ofSeconds(seconds))
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            log.error("Failed to get project distribution.", e)
        }
        return distribution
    }

    /**
     * Categorizes coding time into four periods of the day and calculates the total duration for each.
     * Periods are: Night (0-5), Morning (6-11), Daytime (12-17), Evening (18-23).
     *
     * @param startTime The start of the time range for the query.
     * @param endTime The end of the time range for the query.
     * @return A list of TimeOfDayUsage objects.
     */
    fun getTimeOfDayDistribution(startTime: LocalDateTime, endTime: LocalDateTime): List<TimeOfDayUsage> {
        val sql = """
            SELECT
                CASE
                    WHEN CAST(strftime('%H', start_time) AS INTEGER) BETWEEN 0 AND 5 THEN 'Night'
                    WHEN CAST(strftime('%H', start_time) AS INTEGER) BETWEEN 6 AND 11 THEN 'Morning'
                    WHEN CAST(strftime('%H', start_time) AS INTEGER) BETWEEN 12 AND 17 THEN 'Daytime'
                    ELSE 'Evening'
                END as time_of_day,
                SUM(strftime('%s', end_time) - strftime('%s', start_time)) as total_seconds
            FROM coding_sessions
            WHERE is_deleted = 0 AND start_time >= ? AND start_time < ?
            GROUP BY time_of_day;
        """
        val distribution = mutableListOf<TimeOfDayUsage>()
        try {
            withConnection { conn ->
                conn.prepareStatement(sql).use { pstmt ->
                    pstmt.setString(1, dateTimeFormatter.format(startTime))
                    pstmt.setString(2, dateTimeFormatter.format(endTime))
                    pstmt.executeQuery().use { rs ->
                        while (rs.next()) {
                            val timeOfDay = rs.getString("time_of_day")
                            val seconds = rs.getLong("total_seconds")
                            distribution.add(
                                TimeOfDayUsage(timeOfDay, Duration.ofSeconds(seconds))
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            log.error("Failed to get time of day distribution.", e)
        }
        return distribution
    }

    private fun calculateCodingStreaks(codingDates: List<LocalDate>): CodingStreaks {
        // If there's no data, return zero for both streaks
        if (codingDates.isEmpty()) return CodingStreaks(0, 0)

        var maxStreak = 0
        var loopStreakCounter = 0
        // Start iterating from the most recent data
        // Initialize `lastDate` to one day after the most recent coding day to correctly start the first streak count
        var lastDate = codingDates.first().plusDays(1)
        var mostRecentStreakLength = -1

        for (date in codingDates) {
            if (lastDate.minusDays(1).isEqual(date)) {
                loopStreakCounter++
            } else {
                if (mostRecentStreakLength == -1) mostRecentStreakLength = loopStreakCounter
                // The streak is broken
                // First, check if the streak that just ended is the new maximum
                maxStreak = maxOf(maxStreak, loopStreakCounter)
                // Then, start a new streak
                loopStreakCounter = 1
            }
            lastDate = date
        }
        // The loop finishes, so the very last calculated streak (which could be the longest) needs to be checked
        maxStreak = maxOf(maxStreak, loopStreakCounter)
        if (mostRecentStreakLength == -1) mostRecentStreakLength = loopStreakCounter

        val today = LocalDate.now()
        val mostRecentCodingDate = codingDates.first()

        // The "current" streak is only valid if the last coding session was today or yesterday.
        // Otherwise, the streak is considered broken.
        val finalCurrentStreak =
            if (mostRecentCodingDate.isEqual(today) ||
                mostRecentCodingDate.isEqual(today.minusDays(1))
            ) {
                mostRecentStreakLength
            } else {
                0
            }

        return CodingStreaks(currentStreak = finalCurrentStreak, maxStreak = maxStreak)
    }
}