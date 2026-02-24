package com.ahogek.codetimetracker.database

import com.ahogek.codetimetracker.model.*
import com.ahogek.codetimetracker.user.UserManager
import com.ahogek.codetimetracker.util.TimeRangeUtils
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.*

object DatabaseManager {

    private val log = Logger.getInstance(DatabaseManager::class.java)
    private val dateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME

    private val connectionManager = ConnectionManager()
    private val migrationManager = MigrationManager(connectionManager)

    private const val SQL_SELECT_SESSIONS_IN_RANGE = """
        SELECT start_time, end_time
        FROM coding_sessions
        WHERE is_deleted = 0 AND end_time > ? AND start_time < ?
    """
    private const val SQL_IS_NOT_DELETED = "is_deleted = 0"

    private const val SQL_SELECT_MIN_MAX_TIME =
        "SELECT MIN(start_time), MAX(end_time) FROM coding_sessions WHERE is_deleted=0"

    init {
        migrationManager.migrate()
    }

    fun initialize() {
        connectionManager.initialize()
    }

    fun saveSessions(sessions: List<CodingSession>, onComplete: () -> Unit) {
        if (sessions.isEmpty()) {
            ApplicationManager.getApplication().invokeLater {
                onComplete()
            }
            return
        }

        connectionManager.databaseExecutor.execute {
            val sql = """
                INSERT INTO coding_sessions(
                    session_uuid, user_id, project_name, language, platform, ide_name,
                    start_time, end_time, last_modified, is_synced, synced_at, sync_version
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);
            """
            try {
                connectionManager.withConnection { conn ->
                    conn.autoCommit = false
                    val pstmt = conn.prepareStatement(sql)
                    val currentTimestamp = dateTimeFormatter.format(LocalDateTime.now())
                    val userId = UserManager.getUserId()

                    for (session in sessions) {
                        pstmt.setString(1, UUID.randomUUID().toString())
                        pstmt.setString(2, userId)
                        pstmt.setSessionCoreParams(3, session)
                        pstmt.setString(9, currentTimestamp)
                        pstmt.setInt(10, if (session.isSynced) 1 else 0)
                        pstmt.setString(11, session.syncedAt?.let { dateTimeFormatter.format(it) })
                        pstmt.setInt(12, session.syncVersion)
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

    fun shutdown() {
        connectionManager.shutdown()
    }

    fun getUserIdFromDatabase(): String? {
        val sql = "SELECT user_id FROM coding_sessions LIMIT 1"
        return try {
            connectionManager.withConnection { conn ->
                conn.createStatement().executeQuery(sql).use { rs ->
                    if (rs.next()) rs.getString("user_id")
                    else null
                }
            }
        } catch (e: Exception) {
            log.error("Failed to get user ID from database.", e)
            null
        }
    }

    fun getTotalCodingTime(projectName: String? = null): Duration {
        val conditions = mutableListOf("is_deleted = 0")
        if (projectName != null) {
            conditions.add("project_name = ?")
        }

        val sql = "SELECT start_time, end_time FROM coding_sessions WHERE ${conditions.joinToString(" AND ")}"
        val intervals = mutableListOf<Pair<LocalDateTime, LocalDateTime>>()

        try {
            connectionManager.withConnection { conn ->
                conn.prepareStatement(sql).use { pstmt ->
                    if (projectName != null) {
                        pstmt.setString(1, projectName)
                    }
                    pstmt.executeQuery().use { rs ->
                        while (rs.next()) {
                            val start = LocalDateTime.parse(rs.getString("start_time"), dateTimeFormatter)
                            val end = LocalDateTime.parse(rs.getString("end_time"), dateTimeFormatter)
                            intervals.add(start to end)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            log.error("Failed to get total coding time from database.", e)
            return Duration.ZERO
        }

        return TimeRangeUtils.calculateMergedDuration(intervals)
    }

    fun getCodingTimeForPeriod(
        startTime: LocalDateTime,
        endTime: LocalDateTime,
        projectName: String? = null
    ): Duration {
        val sql = if (projectName != null) {
            "$SQL_SELECT_SESSIONS_IN_RANGE AND project_name = ?"
        } else {
            SQL_SELECT_SESSIONS_IN_RANGE
        }

        val intervals = mutableListOf<Pair<LocalDateTime, LocalDateTime>>()

        try {
            connectionManager.withConnection { conn ->
                conn.prepareStatement(sql).use { pstmt ->
                    pstmt.setString(1, dateTimeFormatter.format(startTime))
                    pstmt.setString(2, dateTimeFormatter.format(endTime))
                    projectName?.let { pstmt.setString(3, it) }

                    pstmt.executeQuery().use { rs ->
                        while (rs.next()) {
                            val sessionStart = LocalDateTime.parse(rs.getString("start_time"), dateTimeFormatter)
                            val sessionEnd = LocalDateTime.parse(rs.getString("end_time"), dateTimeFormatter)

                            val effectiveStart = maxOf(sessionStart, startTime)
                            val effectiveEnd = minOf(sessionEnd, endTime)

                            if (effectiveStart.isBefore(effectiveEnd)) {
                                intervals.add(effectiveStart to effectiveEnd)
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            log.error("Failed to get coding time for period from database.", e)
            return Duration.ZERO
        }

        return TimeRangeUtils.calculateMergedDuration(intervals)
    }

    fun getDailyCodingTimeForHeatmap(startTime: LocalDateTime, endTime: LocalDateTime): List<DailySummary> {
        val sql = SQL_SELECT_SESSIONS_IN_RANGE.trimIndent()
        val dailyMap = mutableMapOf<LocalDate, Long>()

        try {
            connectionManager.withConnection { conn ->
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

    private fun splitSessionByDay(
        start: LocalDateTime,
        end: LocalDateTime
    ): List<Pair<LocalDate, Duration>> {
        val result = mutableListOf<Pair<LocalDate, Duration>>()
        var current = start

        while (current.isBefore(end)) {
            val currentDate = current.toLocalDate()
            val nextDayStart = current.plusDays(1).withHour(0).withMinute(0).withSecond(0).withNano(0)
            val segmentEnd = if (nextDayStart.isAfter(end)) end else nextDayStart
            val duration = Duration.between(current, segmentEnd)
            if (!duration.isZero) {
                result.add(Pair(currentDate, duration))
            }
            current = segmentEnd
        }

        return result
    }

    fun getCodingStreaks(startTime: LocalDateTime, endTime: LocalDateTime): CodingStreaks {
        val sql = SQL_SELECT_SESSIONS_IN_RANGE
        val codingDates = mutableSetOf<LocalDate>()
        try {
            connectionManager.withConnection { conn ->
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

    private fun determineTimeRange(
        startTime: LocalDateTime?,
        endTime: LocalDateTime?
    ): Pair<LocalDateTime, LocalDateTime>? {
        if (startTime != null && endTime != null) {
            return Pair(startTime, endTime)
        }

        return try {
            connectionManager.withConnection { conn ->
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

    private fun fetchDailyHourlyData(
        actualStart: LocalDateTime,
        actualEnd: LocalDateTime
    ): Map<Pair<Int, Int>, Long> {
        val distributionMap = mutableMapOf<Pair<Int, Int>, Long>()

        try {
            connectionManager.withConnection { conn ->
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

    private fun splitSessionByDayAndHour(
        start: LocalDateTime,
        end: LocalDateTime
    ): List<Triple<Int, Int, Duration>> {
        val result = mutableListOf<Triple<Int, Int, Duration>>()
        var current = start
        while (current.isBefore(end)) {
            val hour = current.hour
            val weekday = current.dayOfWeek.value
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

    /**
     * 设置 PreparedStatement 中 session 的基本字段（第 3-8 位参数）
     */
    private fun PreparedStatement.setSessionCoreParams(startIndex: Int, session: CodingSession) {
        setString(startIndex, session.projectName)
        setString(startIndex + 1, session.language)
        setString(startIndex + 2, session.platform)
        setString(startIndex + 3, session.ideName)
        setString(startIndex + 4, dateTimeFormatter.format(session.startTime))
        setString(startIndex + 5, dateTimeFormatter.format(session.endTime))
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
            connectionManager.withConnection { conn ->
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

    private fun buildHourlyDistribution(
        hourlyMap: Map<Int, Long>,
        totalDays: Int
    ): List<HourlyUsage> {
        return hourlyMap.map { (hour, totalSeconds) ->
            val avgSeconds = totalSeconds / totalDays
            HourlyUsage(hour, 0, Duration.ofSeconds(avgSeconds))
        }.sortedBy { it.hour }
    }

    private fun splitSessionByFullHour(
        start: LocalDateTime, end: LocalDateTime
    ): List<Triple<Int, Duration, Set<LocalDate>>> {
        val result = mutableListOf<Triple<Int, Duration, Set<LocalDate>>>()
        var current = start

        while (current.isBefore(end)) {
            val hour = current.hour
            val nextHour = current.withMinute(0).withSecond(0).withNano(0).plusHours(1)
            val segmentEnd = if (nextHour.isAfter(end)) end else nextHour
            val duration = Duration.between(current, segmentEnd)
            if (!duration.isZero) {
                val dates = mutableSetOf<LocalDate>()
                var temp = current
                while (temp.isBefore(segmentEnd)) {
                    dates.add(temp.toLocalDate())
                    temp = temp.plusDays(1).withHour(0).withMinute(0).withSecond(0).withNano(0)
                    if (temp.isAfter(segmentEnd)) break
                }
                result.add(Triple(hour, duration, dates))
            }
            current = segmentEnd
        }

        return result
    }

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
            connectionManager.withConnection { conn ->
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
            connectionManager.withConnection { conn ->
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
            connectionManager.withConnection { conn ->
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
        if (codingDates.isEmpty()) return CodingStreaks(0, 0)

        var maxStreak = 0
        var loopStreakCounter = 0
        var lastDate = codingDates.first().plusDays(1)
        var mostRecentStreakLength = -1

        for (date in codingDates) {
            if (lastDate.minusDays(1).isEqual(date)) {
                loopStreakCounter++
            } else {
                if (mostRecentStreakLength == -1) mostRecentStreakLength = loopStreakCounter
                maxStreak = maxOf(maxStreak, loopStreakCounter)
                loopStreakCounter = 1
            }
            lastDate = date
        }
        maxStreak = maxOf(maxStreak, loopStreakCounter)
        if (mostRecentStreakLength == -1) mostRecentStreakLength = loopStreakCounter

        val today = LocalDate.now()
        val mostRecentCodingDate = codingDates.first()

        val finalCurrentStreak =
            if (mostRecentCodingDate.isEqual(today) || mostRecentCodingDate.isEqual(today.minusDays(1))) {
                mostRecentStreakLength
            } else {
                0
            }

        return CodingStreaks(currentStreak = finalCurrentStreak, maxStreak = maxStreak)
    }

    fun getSessions(startTime: LocalDateTime? = null, endTime: LocalDateTime? = null): List<CodingSession> {
        val conditions = mutableListOf(SQL_IS_NOT_DELETED)
        checkTimeParams(conditions, startTime, endTime)

        val sql = """
            SELECT
                session_uuid, user_id, project_name, language, platform, ide_name, 
                start_time, end_time, last_modified, is_synced, synced_at, sync_version
            FROM coding_sessions
            WHERE ${conditions.joinToString(" AND ")}
            ORDER BY start_time DESC
        """.trimIndent()

        val sessions = mutableListOf<CodingSession>()
        try {
            connectionManager.withConnection { conn ->
                conn.prepareStatement(sql).use { pstmt ->
                    checkTimeParamsInStatement(pstmt, startTime, endTime)
                    pstmt.executeQuery().use { rs ->
                        while (rs.next()) {
                            sessions.add(
                                CodingSession(
                                    sessionUuid = rs.getString("session_uuid"),
                                    userId = rs.getString("user_id"),
                                    projectName = rs.getString("project_name"),
                                    language = rs.getString("language"),
                                    platform = rs.getString("platform"),
                                    ideName = rs.getString("ide_name"),
                                    startTime = LocalDateTime.parse(rs.getString("start_time"), dateTimeFormatter),
                                    endTime = LocalDateTime.parse(rs.getString("end_time"), dateTimeFormatter),
                                    lastModified = LocalDateTime.parse(
                                        rs.getString("last_modified"),
                                        dateTimeFormatter
                                    ),
                                    isSynced = rs.getInt("is_synced") == 1,
                                    syncedAt = rs.getString("synced_at")?.let {
                                        LocalDateTime.parse(it, dateTimeFormatter)
                                    },
                                    syncVersion = rs.getInt("sync_version")
                                )
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            log.error("Failed to retrieve sessions", e)
        }

        return sessions
    }

    fun getAllSessionUuids(): Set<String> {
        val sql = "SELECT session_uuid FROM coding_sessions WHERE is_deleted = 0"
        val uuids = mutableSetOf<String>()

        try {
            connectionManager.withConnection { conn ->
                conn.createStatement().executeQuery(sql).use { rs ->
                    while (rs.next()) {
                        uuids.add(rs.getString("session_uuid"))
                    }
                }
            }
        } catch (e: Exception) {
            log.error("Failed to retrieve session UUIDs", e)
        }

        return uuids
    }

    fun importSessions(sessions: List<CodingSession>): Int {
        if (sessions.isEmpty()) return 0

        val sql = """
        INSERT OR IGNORE INTO coding_sessions(
            session_uuid, user_id, project_name, language, platform, ide_name,
            start_time, end_time, last_modified, is_deleted
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
    """

        var importedCount = 0

        try {
            connectionManager.withConnection { conn ->
                conn.autoCommit = false
                conn.prepareStatement(sql).use { pstmt ->
                    for (session in sessions) {
                        pstmt.setString(1, session.sessionUuid)
                        pstmt.setString(2, session.userId)
                        pstmt.setSessionCoreParams(3, session)
                        pstmt.setString(9, dateTimeFormatter.format(session.lastModified))

                        val result = pstmt.executeUpdate()
                        if (result > 0) {
                            importedCount++
                        }
                    }
                    conn.commit()
                }
                log.info("Successfully imported $importedCount sessions")
            }
        } catch (e: Exception) {
            log.error("Failed to import sessions", e)
        }

        return importedCount
    }

    fun getRecordCount(): Long {
        val sql = "SELECT COUNT(*) as total FROM coding_sessions WHERE $SQL_IS_NOT_DELETED"

        return try {
            connectionManager.withConnection { conn ->
                conn.prepareStatement(sql).use { pstmt ->
                    pstmt.executeQuery().use { rs ->
                        if (rs.next()) rs.getLong("total") else 0L
                    }
                }
            }
        } catch (e: Exception) {
            log.error("Failed to get record count", e)
            0L
        }
    }

    fun getAllActiveSessionTimes(): List<SessionSummaryDTO> {
        val sql = """
        SELECT start_time, end_time
        FROM coding_sessions
        WHERE $SQL_IS_NOT_DELETED
        ORDER BY start_time
    """.trimIndent()

        val sessions = mutableListOf<SessionSummaryDTO>()

        try {
            connectionManager.withConnection { conn ->
                conn.prepareStatement(sql).use { pstmt ->
                    pstmt.executeQuery().use { rs ->
                        while (rs.next()) {
                            sessions.add(
                                SessionSummaryDTO(
                                    startTime = LocalDateTime.parse(
                                        rs.getString("start_time"),
                                        dateTimeFormatter
                                    ),
                                    endTime = LocalDateTime.parse(
                                        rs.getString("end_time"),
                                        dateTimeFormatter
                                    )
                                )
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            log.error("Failed to load session times", e)
        }

        return sessions
    }

    fun getFirstRecordDate(): LocalDate? {
        val sql = "SELECT MIN(start_time) as first_date FROM coding_sessions WHERE $SQL_IS_NOT_DELETED"

        return try {
            connectionManager.withConnection { conn ->
                conn.prepareStatement(sql).use { pstmt ->
                    pstmt.executeQuery().use { rs ->
                        if (rs.next()) {
                            val timestamp = rs.getString("first_date")
                            timestamp?.let {
                                LocalDateTime.parse(it, dateTimeFormatter).toLocalDate()
                            }
                        } else null
                    }
                }
            }
        } catch (e: Exception) {
            log.error("Failed to retrieve first record date", e)
            null
        }
    }
}
