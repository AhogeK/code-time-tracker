package com.ahogek.codetimetracker.database

import com.ahogek.codetimetracker.model.*
import com.ahogek.codetimetracker.user.UserManager
import com.ahogek.codetimetracker.util.PathUtils
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
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
        val url: String, val factory: ConnectionFactory
    )

    @Volatile
    private var config: DbConfig

    private val log = Logger.getInstance(DatabaseManager::class.java)
    private val dateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME

    // This executor will handle all database write operations sequentially on a background thread
    private val databaseExecutor = Executors.newSingleThreadExecutor()

    // SQL query constants
    private const val SQL_SELECT_SESSIONS_IN_RANGE = """
        SELECT start_time, end_time
        FROM coding_sessions
        WHERE is_deleted = 0 AND end_time > ? AND start_time < ?
    """
    private const val SQL_IS_NOT_DELETED = "is_deleted = 0"

    private const val SQL_SELECT_MIN_MAX_TIME =
        "SELECT MIN(start_time), MAX(end_time) FROM coding_sessions WHERE is_deleted=0"

    init {
        try {
            Class.forName("org.sqlite.JDBC")
        } catch (e: ClassNotFoundException) {
            log.error("Failed to load SQLite JDBC driver.", e)
        }

        val dbPath = PathUtils.getDatabasePath()
        val dbFile = dbPath.toFile()
        dbFile.parentFile?.mkdirs()
        val dbUrl = "jdbc:sqlite:${dbFile.absolutePath}"
        config = DbConfig(
            url = dbUrl, factory = DriverManagerConnectionFactory(dbUrl)
        )
        log.info("Database initialized at official shared location: ${config.url}")

        createTableIfNotExists()
    }

    fun setConnectionFactory(factory: ConnectionFactory, urlHint: String? = null) {
        val newUrl = urlHint ?: config.url
        val normalizedUrl = normalizeJdbcUrl(newUrl)

        config = DbConfig(
            url = normalizedUrl, factory = factory
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

            -- The JetBrains IDE product being used (e.g., "IntelliJ IDEA", "PyCharm").
            ide_name TEXT NOT NULL,
            
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
                session_uuid, user_id, project_name, language, platform, ide_name,
                start_time, end_time, last_modified
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?);
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
                        pstmt.setString(6, session.ideName)
                        pstmt.setString(7, dateTimeFormatter.format(session.startTime))
                        pstmt.setString(8, dateTimeFormatter.format(session.endTime))
                        pstmt.setString(9, currentTimestamp)
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
     * Calculates the total coding duration that overlaps with the given time period.
     *
     * This method supports sessions that span multiple periods, such as those crossing
     * midnight or month boundaries. Only the overlapping part of each session is counted.
     *
     * Example scenario:
     *  - Session: 2025-10-01 23:30 ~ 2025-10-02 00:30
     *  - Period:  2025-10-02 00:00 ~ 2025-10-03 00:00
     *  - Only the portion from 00:00 to 00:30 on 2025-10-02 will be included.
     *
     * @param startTime    The beginning of the time range (inclusive)
     * @param endTime      The end of the time range (exclusive)
     * @param projectName  Optional project name filter
     * @return Duration    Total overlapping coding time during the period
     */
    fun getCodingTimeForPeriod(
        startTime: LocalDateTime,
        endTime: LocalDateTime,
        projectName: String? = null
    ): Duration {
        // SQL will fetch all coding sessions that overlap with the target period.
        // Any session whose end_time is after the period's start, and whose start_time is before the period's end.
        // Optional project filter
        val sql = if (projectName != null) {
            "$SQL_SELECT_SESSIONS_IN_RANGE AND project_name = ?"
        } else {
            SQL_SELECT_SESSIONS_IN_RANGE
        }
        var totalSeconds = 0L

        try {
            withConnection { conn ->
                conn.prepareStatement(sql).use { pstmt ->
                    pstmt.setString(1, dateTimeFormatter.format(startTime))
                    pstmt.setString(2, dateTimeFormatter.format(endTime))
                    projectName?.let { pstmt.setString(3, it) }

                    pstmt.executeQuery().use { rs ->
                        while (rs.next()) {
                            // Parse session times from database
                            val sessionStart = LocalDateTime.parse(rs.getString("start_time"), dateTimeFormatter)
                            val sessionEnd = LocalDateTime.parse(rs.getString("end_time"), dateTimeFormatter)
                            // The latest possible session start for overlap
                            val effectiveStart = maxOf(sessionStart, startTime)
                            // The earliest possible session end for overlap
                            val effectiveEnd = minOf(sessionEnd, endTime)
                            // If there's a valid overlapping range, add its duration
                            if (effectiveStart.isBefore(effectiveEnd)) {
                                totalSeconds += Duration.between(effectiveStart, effectiveEnd).toSeconds()
                            }
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
     * Calculates daily coding duration for a specific time period, suitable for heatmap display.
     * Sessions overlapping the target range are split per day, with every part counted on the respective day.
     *
     * @param startTime The beginning of the period (inclusive)
     * @param endTime   The end of the period (exclusive)
     * @return List of DailySummary with total coding duration for each day
     */
    fun getDailyCodingTimeForHeatmap(startTime: LocalDateTime, endTime: LocalDateTime): List<DailySummary> {
        val sql = SQL_SELECT_SESSIONS_IN_RANGE.trimIndent()
        val dailyMap = mutableMapOf<LocalDate, Long>()

        try {
            withConnection { conn ->
                conn.prepareStatement(sql).use { pstmt ->
                    pstmt.setString(1, dateTimeFormatter.format(startTime))
                    pstmt.setString(2, dateTimeFormatter.format(endTime))
                    pstmt.executeQuery().use { rs ->
                        while (rs.next()) {
                            val sessionStart = LocalDateTime.parse(rs.getString("start_time"), dateTimeFormatter)
                            val sessionEnd = LocalDateTime.parse(rs.getString("end_time"), dateTimeFormatter)
                            // Compute overlap
                            val effectiveStart = maxOf(sessionStart, startTime)
                            val effectiveEnd = minOf(sessionEnd, endTime)
                            if (effectiveStart.isBefore(effectiveEnd)) {
                                // Split session by day, count only the overlapping part
                                splitSessionByDay(effectiveStart, effectiveEnd).forEach { (date, duration) ->
                                    dailyMap[date] = dailyMap.getOrDefault(date, 0L) + duration.toSeconds()
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            log.error("Failed to compute daily coding time for heatmap.", e)
        }
        return dailyMap.map { (date, totalSeconds) ->
            DailySummary(date, Duration.ofSeconds(totalSeconds))
        }.sortedBy { it.date }
    }

    /**
     * Splits a coding session into segments, one for each day the session spans.
     *
     * This helper method breaks down sessions that cross day boundaries (midnight) into
     * multiple segments, ensuring accurate daily statistics.
     *
     * Example: A session from 23:30 on 2025-01-01 to 01:20 on 2025-01-02:
     * - 2025-01-01: 30 minutes (23:30 to 00:00)
     * - 2025-01-02: 80 minutes (00:00 to 01:20)
     *
     * @param start The start time of the session
     * @param end The end time of the session
     * @return A list of pairs, each containing a date and the duration spent on that date
     */
    private fun splitSessionByDay(
        start: LocalDateTime,
        end: LocalDateTime
    ): List<Pair<LocalDate, Duration>> {
        val result = mutableListOf<Pair<LocalDate, Duration>>()
        var current = start

        while (current.isBefore(end)) {
            val currentDate = current.toLocalDate()

            // Calculate the next day boundary (00:00 of the next day)
            val nextDayStart = current.plusDays(1).withHour(0).withMinute(0).withSecond(0).withNano(0)

            // The segment ends at either the next day boundary or the session end time
            val segmentEnd = if (nextDayStart.isAfter(end)) end else nextDayStart

            val duration = Duration.between(current, segmentEnd)
            if (!duration.isZero) {
                result.add(Pair(currentDate, duration))
            }

            current = segmentEnd
        }

        return result
    }

    /**
     * Calculates coding streaks (current and max consecutive active days) in a time interval.
     * Counts all days with any session overlap in the interval for precise streak detection.
     *
     * @param startTime The beginning of the period (inclusive)
     * @param endTime   The end of the period (exclusive)
     * @return CodingStreaks object showing current and max streaks
     */
    fun getCodingStreaks(startTime: LocalDateTime, endTime: LocalDateTime): CodingStreaks {
        val sql = SQL_SELECT_SESSIONS_IN_RANGE
        val codingDates = mutableSetOf<LocalDate>()
        try {
            withConnection { conn ->
                conn.prepareStatement(sql).use { pstmt ->
                    pstmt.setString(1, dateTimeFormatter.format(startTime))
                    pstmt.setString(2, dateTimeFormatter.format(endTime))
                    pstmt.executeQuery().use { rs ->
                        while (rs.next()) {
                            val sessionStart = LocalDateTime.parse(rs.getString("start_time"), dateTimeFormatter)
                            val sessionEnd = LocalDateTime.parse(rs.getString("end_time"), dateTimeFormatter)
                            val effectiveStart = maxOf(sessionStart, startTime)
                            val effectiveEnd = minOf(sessionEnd, endTime)
                            if (effectiveStart.isBefore(effectiveEnd)) {
                                // Split session by day, collect all days covered by its overlapping fragment
                                splitSessionByDay(effectiveStart, effectiveEnd).forEach { (date, _) ->
                                    codingDates.add(date)
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            log.error("Failed to compute coding streaks.", e)
        }
        return calculateCodingStreaks(codingDates.sortedDescending())
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
     * Computes the average coding duration for each hour of each weekday in the specified interval.
     * Sessions crossing day/hour boundaries are split accurately.
     *
     * @param startTime The start of the time range (inclusive). If null, uses earliest session.
     * @param endTime The end of the time range (exclusive). If null, uses latest session.
     * @return List of HourlyDistribution, one entry for each (weekday, hour).
     */
    fun getDailyHourDistribution(
        startTime: LocalDateTime? = null,
        endTime: LocalDateTime? = null
    ): List<HourlyDistribution> {
        val (actualStart, actualEnd) = determineTimeRange(startTime, endTime)
            ?: return emptyList()

        val distributionMap = fetchDailyHourlyData(actualStart, actualEnd)
        val weekdayCount = calculateWeekdayCount(actualStart, actualEnd)

        return buildDailyHourlyDistribution(distributionMap, weekdayCount)
    }

    /**
     * Determines the actual time range to use for the query.
     * If parameters are null, queries the database for min/max times.
     */
    private fun determineTimeRange(
        startTime: LocalDateTime?,
        endTime: LocalDateTime?
    ): Pair<LocalDateTime, LocalDateTime>? {
        if (startTime != null && endTime != null) {
            return Pair(startTime, endTime)
        }

        return try {
            withConnection { conn ->
                conn.prepareStatement(SQL_SELECT_MIN_MAX_TIME).use { pstmt ->
                    pstmt.executeQuery().use { rs ->
                        if (rs.next()) {
                            val minTime = rs.getString(1)?.let {
                                LocalDateTime.parse(it, dateTimeFormatter)
                            } ?: startTime
                            val maxTime = rs.getString(2)?.let {
                                LocalDateTime.parse(it, dateTimeFormatter)
                            } ?: endTime

                            if (minTime != null && maxTime != null) {
                                Pair(minTime, maxTime)
                            } else null
                        } else null
                    }
                }
            }
        } catch (e: Exception) {
            log.error("Failed to determine time range.", e)
            null
        }
    }

    /**
     * Fetches and processes all sessions in the range, splitting by day and hour.
     */
    private fun fetchDailyHourlyData(
        actualStart: LocalDateTime,
        actualEnd: LocalDateTime
    ): Map<Pair<Int, Int>, Long> {
        val distributionMap = mutableMapOf<Pair<Int, Int>, Long>()

        try {
            withConnection { conn ->
                conn.prepareStatement(SQL_SELECT_SESSIONS_IN_RANGE).use { pstmt ->
                    pstmt.setString(1, dateTimeFormatter.format(actualStart))
                    pstmt.setString(2, dateTimeFormatter.format(actualEnd))
                    pstmt.executeQuery().use { rs ->
                        while (rs.next()) {
                            processDailyHourlySession(
                                rs.getString("start_time"),
                                rs.getString("end_time"),
                                actualStart,
                                actualEnd,
                                distributionMap
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            log.error("Failed to compute daily hour distribution.", e)
        }

        return distributionMap
    }

    /**
     * Refactored processDailyHourlySession using the helper method
     */
    private fun processDailyHourlySession(
        startTimeStr: String,
        endTimeStr: String,
        rangeStart: LocalDateTime,
        rangeEnd: LocalDateTime,
        distributionMap: MutableMap<Pair<Int, Int>, Long>
    ) {
        val sessionStart = LocalDateTime.parse(startTimeStr, dateTimeFormatter)
        val sessionEnd = LocalDateTime.parse(endTimeStr, dateTimeFormatter)

        calculateEffectiveRange(sessionStart, sessionEnd, rangeStart, rangeEnd)?.let { (effectiveStart, effectiveEnd) ->
            splitSessionByDayAndHour(effectiveStart, effectiveEnd).forEach { (weekday, hour, duration) ->
                val key = Pair(weekday, hour)
                distributionMap[key] = distributionMap.getOrDefault(key, 0L) + duration.toSeconds()
            }
        }
    }

    /**
     * Calculates how many times each weekday appears in the given range.
     */
    private fun calculateWeekdayCount(
        startTime: LocalDateTime,
        endTime: LocalDateTime
    ): Map<Int, Int> {
        val weekdayCount = mutableMapOf<Int, Int>()
        var date = startTime.toLocalDate()
        val endDate = endTime.toLocalDate()

        while (date.isBefore(endDate)) {
            val weekday = date.dayOfWeek.value
            weekdayCount[weekday] = weekdayCount.getOrDefault(weekday, 0) + 1
            date = date.plusDays(1)
        }

        return weekdayCount
    }

    /**
     * Builds the final distribution list with averaged values per weekday.
     */
    private fun buildDailyHourlyDistribution(
        distributionMap: Map<Pair<Int, Int>, Long>,
        weekdayCount: Map<Int, Int>
    ): List<HourlyDistribution> {
        return distributionMap.map { (key, totalSeconds) ->
            val weekday = key.first
            val hour = key.second
            val appearances = weekdayCount[weekday]?.coerceAtLeast(1) ?: 1
            val avgSeconds = totalSeconds.toDouble() / appearances
            HourlyDistribution(weekday, hour, Duration.ofSeconds(avgSeconds.toLong()))
        }.sortedWith(
            compareBy<HourlyDistribution> { it.dayOfWeek }
                .thenBy { it.hourOfDay }
        )
    }

    /**
     * Splits a coding session into segments by day of week and hour.
     *
     * @param start Start time of the session.
     * @param end End time of the session.
     * @return List of triples containing weekday, hour, and duration.
     */
    private fun splitSessionByDayAndHour(
        start: LocalDateTime,
        end: LocalDateTime
    ): List<Triple<Int, Int, Duration>> {
        val result = mutableListOf<Triple<Int, Int, Duration>>()
        var current = start
        while (current.isBefore(end)) {
            val hour = current.hour
            val weekday = current.dayOfWeek.value // 1=Monday, ..., 7=Sunday
            val nextHour = current.plusHours(1).withMinute(0).withSecond(0).withNano(0)
            val segmentEnd = if (nextHour.isAfter(end)) end else nextHour
            val duration = Duration.between(current, segmentEnd)
            if (!duration.isZero) {
                result.add(Triple(weekday, hour, duration))
            }
            current = segmentEnd
        }
        return result
    }

    private fun checkTimeParamsInStatement(
        pstmt: PreparedStatement, startTime: LocalDateTime?, endTime: LocalDateTime?
    ) {
        var paramIndex = 1
        if (startTime != null) {
            pstmt.setString(paramIndex++, dateTimeFormatter.format(startTime))
        }
        if (endTime != null) {
            pstmt.setString(paramIndex, dateTimeFormatter.format(endTime))
        }
    }

    private fun checkTimeParams(
        conditions: MutableList<String>, startTime: LocalDateTime?, endTime: LocalDateTime?
    ) {
        if (startTime != null) {
            conditions.add("end_time > ?")
        }
        if (endTime != null) {
            conditions.add("start_time < ?")
        }
    }

    /**
     * Fetches the total coding time distribution by hour, aggregated across all days in a given period.
     * This method calculates the average coding duration for each hour of the day and provides metadata
     * about the dataset for context (total number of active days).
     *
     * Sessions that span multiple hours are split at hour boundaries, with each segment attributed
     * to its corresponding hour. The final values represent averages across all active days in the
     * specified time range.
     *
     * Example: If a session runs from 14:45 to 16:15 across 10 active days:
     * - Hour 14: 15 minutes (14:45 to 15:00)
     * - Hour 15: 60 minutes (15:00 to 16:00)
     * - Hour 16: 15 minutes (16:00 to 16:15)
     * Then the averages would be: 14h -> 1.5m/day, 15h -> 6m/day, 16h -> 1.5m/day
     *
     * @param startTime The start of the time range (inclusive). If null, includes all historical data.
     * @param endTime The end of the time range (exclusive). If null, includes all data to the present.
     * @return A [HourlyDistributionResult] containing a list of [HourlyUsage] objects (one per hour 0-23)
     *         with average coding durations and the total number of active days used for calculation.
     */
    fun getOverallHourlyDistributionWithTotalDays(
        startTime: LocalDateTime? = null,
        endTime: LocalDateTime? = null
    ): HourlyDistributionResult {
        val hourlyMap = mutableMapOf<Int, Long>()
        val activeDays = mutableSetOf<LocalDate>()

        fetchAndProcessHourlySessions(startTime, endTime, hourlyMap, activeDays)

        val totalDays = calculateTotalDays(startTime, endTime, activeDays)
        val distribution = buildHourlyDistribution(hourlyMap, totalDays)

        return HourlyDistributionResult(distribution, totalDays)
    }

    /**
     * Fetches coding sessions and processes them into hourly buckets.
     */
    private fun fetchAndProcessHourlySessions(
        startTime: LocalDateTime?,
        endTime: LocalDateTime?,
        hourlyMap: MutableMap<Int, Long>,
        activeDays: MutableSet<LocalDate>
    ) {
        val conditions = mutableListOf(SQL_IS_NOT_DELETED)
        checkTimeParams(conditions, startTime, endTime)

        val sql = """
            SELECT start_time, end_time
            FROM coding_sessions 
            WHERE ${conditions.joinToString(" AND ")}
        """

        try {
            withConnection { conn ->
                conn.prepareStatement(sql).use { pstmt ->
                    checkTimeParamsInStatement(pstmt, startTime, endTime)
                    pstmt.executeQuery().use { rs ->
                        while (rs.next()) {
                            processSessionForHourly(
                                rs.getString("start_time"),
                                rs.getString("end_time"),
                                startTime,
                                endTime,
                                hourlyMap,
                                activeDays
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            log.error("Failed to get overall hourly distribution.", e)
        }
    }

    /**
     * Processes a single session and adds its hourly segments to the map.
     */
    private fun processSessionForHourly(
        startTimeStr: String,
        endTimeStr: String,
        rangeStart: LocalDateTime?,
        rangeEnd: LocalDateTime?,
        hourlyMap: MutableMap<Int, Long>,
        activeDays: MutableSet<LocalDate>
    ) {
        val start = LocalDateTime.parse(startTimeStr, dateTimeFormatter)
        val end = LocalDateTime.parse(endTimeStr, dateTimeFormatter)

        val effectiveStart = if (rangeStart != null) maxOf(start, rangeStart) else start
        val effectiveEnd = if (rangeEnd != null) minOf(end, rangeEnd) else end

        if (effectiveStart.isBefore(effectiveEnd)) {
            splitSessionByFullHour(effectiveStart, effectiveEnd).forEach { (hour, duration, dates) ->
                hourlyMap[hour] = hourlyMap.getOrDefault(hour, 0L) + duration.toSeconds()
                activeDays.addAll(dates)
            }
        }
    }

    /**
     * Calculates the total number of days to use as denominator.
     */
    private fun calculateTotalDays(
        startTime: LocalDateTime?,
        endTime: LocalDateTime?,
        activeDays: Set<LocalDate>
    ): Int {
        return if (startTime != null && endTime != null) {
            ChronoUnit.DAYS.between(startTime.toLocalDate(), endTime.toLocalDate())
                .toInt()
                .coerceAtLeast(1)
        } else {
            activeDays.size.coerceAtLeast(1)
        }
    }

    /**
     * Builds the final hourly distribution list with averaged durations.
     */
    private fun buildHourlyDistribution(
        hourlyMap: Map<Int, Long>,
        totalDays: Int
    ): List<HourlyUsage> {
        return hourlyMap.map { (hour, totalSeconds) ->
            val avgSeconds = totalSeconds / totalDays
            HourlyUsage(hour, 0, Duration.ofSeconds(avgSeconds))
        }.sortedBy { it.hour }
    }

    /**
     * Splits a coding session into segments, one for each full hour the session spans.
     *
     * This helper method breaks down sessions that cross hour boundaries into multiple segments,
     * ensuring that each segment belongs entirely to a single hour. For example, a session from
     * 14:30 to 16:20 is split into three segments:
     * - 14:30 to 15:00 (in hour 14)
     * - 15:00 to 16:00 (in hour 15)
     * - 16:00 to 16:20 (in hour 16)
     *
     * @param start The start time of the session.
     * @param end The end time of the session.
     * @return A list of triples, each containing:
     *         - hour: The hour of the day (0-23) for this segment
     *         - duration: The time spent during this segment
     *         - dates: A set of dates that this segment spans (usually a single date, but may include
     *                  multiple dates if the segment crosses a day boundary at midnight)
     */
    private fun splitSessionByFullHour(
        start: LocalDateTime, end: LocalDateTime
    ): List<Triple<Int, Duration, Set<LocalDate>>> {
        val result = mutableListOf<Triple<Int, Duration, Set<LocalDate>>>()
        var current = start

        while (current.isBefore(end)) {
            val hour = current.hour

            // Calculate the next hour boundary (00:00 of the next hour)
            val nextHour = current.withMinute(0).withSecond(0).withNano(0).plusHours(1)

            // The segment ends at either the next hour boundary or the session end time, whichever comes first
            val segmentEnd = if (nextHour.isAfter(end)) end else nextHour

            val duration = Duration.between(current, segmentEnd)
            if (!duration.isZero) {
                // Collect all dates this segment spans (relevant for cross-midnight sessions)
                val dates = mutableSetOf<LocalDate>()
                var temp = current
                while (temp.isBefore(segmentEnd)) {
                    dates.add(temp.toLocalDate())
                    // Move to the start of the next day
                    temp = temp.plusDays(1).withHour(0).withMinute(0).withSecond(0).withNano(0)
                    if (temp.isAfter(segmentEnd)) break
                }

                result.add(Triple(hour, duration, dates))
            }

            current = segmentEnd
        }

        return result
    }


    /**
     * Calculates coding time distribution by language during a given period.
     * Correctly accounts for only segments of sessions that overlap the period.
     *
     * @param startTime The beginning of the period (inclusive). If null, includes all data.
     * @param endTime   The end of the period (exclusive). If null, includes all data.
     * @return List of LanguageUsage objects sorted by usage
     */
    fun getLanguageDistribution(
        startTime: LocalDateTime? = null,
        endTime: LocalDateTime? = null
    ): List<LanguageUsage> {
        val conditions = mutableListOf(SQL_IS_NOT_DELETED)
        checkTimeParams(conditions, startTime, endTime)

        val sql = """
            SELECT language, start_time, end_time
            FROM coding_sessions
            WHERE ${conditions.joinToString(" AND ")}
        """
        val map = mutableMapOf<String, Long>()
        try {
            withConnection { conn ->
                conn.prepareStatement(sql).use { pstmt ->
                    checkTimeParamsInStatement(pstmt, startTime, endTime)
                    pstmt.executeQuery().use { rs ->
                        while (rs.next()) {
                            val language = rs.getString("language")
                            sessionOp(rs, startTime, endTime, map, language)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            log.error("Failed to compute language distribution.", e)
        }
        return map.map { (language, totalSeconds) ->
            LanguageUsage(language, Duration.ofSeconds(totalSeconds))
        }.sortedByDescending { it.totalDuration }
    }


    /**
     * Calculates coding time distribution by project during a given period.
     * Accurately splits sessions so that only overlapping parts are counted.
     *
     * @param startTime The beginning of the period (inclusive). If null, includes all data.
     * @param endTime   The end of the period (exclusive). If null, includes all data.
     * @return List of ProjectUsage objects sorted by usage
     */
    fun getProjectDistribution(
        startTime: LocalDateTime? = null,
        endTime: LocalDateTime? = null
    ): List<ProjectUsage> {
        val conditions = mutableListOf(SQL_IS_NOT_DELETED)
        checkTimeParams(conditions, startTime, endTime)

        val sql = """
            SELECT project_name, start_time, end_time
            FROM coding_sessions
            WHERE ${conditions.joinToString(" AND ")}
        """
        val map = mutableMapOf<String, Long>()
        try {
            withConnection { conn ->
                conn.prepareStatement(sql).use { pstmt ->
                    checkTimeParamsInStatement(pstmt, startTime, endTime)
                    pstmt.executeQuery().use { rs ->
                        while (rs.next()) {
                            val projectName = rs.getString("project_name")
                            sessionOp(rs, startTime, endTime, map, projectName)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            log.error("Failed to compute project distribution.", e)
        }
        return map.map { (projectName, totalSeconds) ->
            ProjectUsage(projectName, Duration.ofSeconds(totalSeconds))
        }.sortedByDescending { it.totalDuration }
    }

    /**
     * Refactored sessionOp using the helper method
     */
    private fun sessionOp(
        rs: ResultSet,
        startTime: LocalDateTime?,
        endTime: LocalDateTime?,
        map: MutableMap<String, Long>,
        key: String
    ) {
        val sessionStart = LocalDateTime.parse(rs.getString("start_time"), dateTimeFormatter)
        val sessionEnd = LocalDateTime.parse(rs.getString("end_time"), dateTimeFormatter)

        calculateEffectiveRange(sessionStart, sessionEnd, startTime, endTime)?.let { (effectiveStart, effectiveEnd) ->
            map[key] = map.getOrDefault(key, 0L) + Duration.between(effectiveStart, effectiveEnd).toSeconds()
        }
    }

    /**
     * Calculates coding time distribution across time-of-day periods within a date range.
     * Ensures only overlapping segments are assigned and counted for each period.
     *
     * @param startTime The beginning of the period (inclusive). If null, includes all data.
     * @param endTime   The end of the period (exclusive). If null, includes all data.
     * @return List of TimeOfDayUsage objects for each time slot
     */
    fun getTimeOfDayDistribution(
        startTime: LocalDateTime? = null,
        endTime: LocalDateTime? = null
    ): List<TimeOfDayUsage> {
        val conditions = mutableListOf(SQL_IS_NOT_DELETED)
        checkTimeParams(conditions, startTime, endTime)

        val sql = """
        SELECT start_time, end_time
        FROM coding_sessions
        WHERE ${conditions.joinToString(" AND ")}
    """
        val distributionMap = mutableMapOf<String, Long>()

        try {
            withConnection { conn ->
                conn.prepareStatement(sql).use { pstmt ->
                    checkTimeParamsInStatement(pstmt, startTime, endTime)
                    pstmt.executeQuery().use { rs ->
                        while (rs.next()) {
                            processTimeOfDaySession(rs, startTime, endTime, distributionMap)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            log.error("Failed to compute time of day distribution.", e)
        }

        return distributionMap.map { (timeOfDay, seconds) ->
            TimeOfDayUsage(timeOfDay, Duration.ofSeconds(seconds))
        }
    }

    /**
     * Refactored processTimeOfDaySession using the helper method
     */
    private fun processTimeOfDaySession(
        rs: ResultSet,
        rangeStart: LocalDateTime?,
        rangeEnd: LocalDateTime?,
        distributionMap: MutableMap<String, Long>
    ) {
        val sessionStart = LocalDateTime.parse(rs.getString("start_time"), dateTimeFormatter)
        val sessionEnd = LocalDateTime.parse(rs.getString("end_time"), dateTimeFormatter)

        calculateEffectiveRange(sessionStart, sessionEnd, rangeStart, rangeEnd)?.let { (effectiveStart, effectiveEnd) ->
            splitSessionByTimeOfDay(effectiveStart, effectiveEnd).forEach { (timeOfDay, duration) ->
                distributionMap[timeOfDay] = distributionMap.getOrDefault(timeOfDay, 0L) + duration.toSeconds()
            }
        }
    }

    /**
     * Calculates the effective time range for a session within optional boundaries.
     * Returns null if the session doesn't overlap with the given range.
     *
     * @param sessionStart The start time of the session
     * @param sessionEnd The end time of the session
     * @param rangeStart Optional start boundary (inclusive)
     * @param rangeEnd Optional end boundary (exclusive)
     * @return Pair of effective start and end times, or null if no overlap
     */
    private fun calculateEffectiveRange(
        sessionStart: LocalDateTime,
        sessionEnd: LocalDateTime,
        rangeStart: LocalDateTime?,
        rangeEnd: LocalDateTime?
    ): Pair<LocalDateTime, LocalDateTime>? {
        val effectiveStart = if (rangeStart != null) maxOf(sessionStart, rangeStart) else sessionStart
        val effectiveEnd = if (rangeEnd != null) minOf(sessionEnd, rangeEnd) else sessionEnd

        return if (effectiveStart.isBefore(effectiveEnd)) {
            Pair(effectiveStart, effectiveEnd)
        } else {
            null
        }
    }

    /**
     * Splits a coding session by time-of-day periods.
     * Periods: Night (0-5), Morning (6-11), Daytime (12-17), Evening (18-23)
     *
     * @param start The start time of the session
     * @param end The end time of the session
     * @return A list of pairs containing time period name and duration
     */
    private fun splitSessionByTimeOfDay(
        start: LocalDateTime,
        end: LocalDateTime
    ): List<Pair<String, Duration>> {
        val result = mutableListOf<Pair<String, Duration>>()
        var current = start

        while (current.isBefore(end)) {
            val hour = current.hour
            val timeOfDay = when (hour) {
                in 0..5 -> "Night"
                in 6..11 -> "Morning"
                in 12..17 -> "Daytime"
                else -> "Evening"
            }

            // Calculate the next period boundary
            val nextBoundaryHour = when (hour) {
                in 0..5 -> 6
                in 6..11 -> 12
                in 12..17 -> 18
                else -> 24
            }

            val nextBoundary = if (nextBoundaryHour == 24) {
                current.plusDays(1).withHour(0).withMinute(0).withSecond(0).withNano(0)
            } else {
                current.withHour(nextBoundaryHour).withMinute(0).withSecond(0).withNano(0)
            }

            val segmentEnd = if (nextBoundary.isAfter(end)) end else nextBoundary

            val duration = Duration.between(current, segmentEnd)
            if (!duration.isZero) {
                result.add(Pair(timeOfDay, duration))
            }

            current = segmentEnd
        }

        return result
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
            if (mostRecentCodingDate.isEqual(today) || mostRecentCodingDate.isEqual(today.minusDays(1))) {
                mostRecentStreakLength
            } else {
                0
            }

        return CodingStreaks(currentStreak = finalCurrentStreak, maxStreak = maxStreak)
    }
}