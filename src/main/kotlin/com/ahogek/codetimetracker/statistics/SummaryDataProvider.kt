package com.ahogek.codetimetracker.statistics

import com.ahogek.codetimetracker.database.DatabaseManager
import com.ahogek.codetimetracker.model.SessionSummaryDTO
import com.ahogek.codetimetracker.util.TimeRangeUtils
import com.intellij.openapi.diagnostic.Logger
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/**
 * Adaptive summary statistics provider with intelligent query strategy selection.
 *
 * Strategy selection logic:
 * - For small datasets (< 20,000 records): Load all data once and compute in-memory
 * - For large datasets (>= 20,000 records): Use multiple targeted SQL queries with indexes
 *
 * This hybrid approach balances performance and memory efficiency based on actual data size,
 * following patterns used in Apache Spark's adaptive query execution.
 *
 * @author AhogeK ahogek@gmail.com
 * @since 2025-12-05 16:46:13
 */
class SummaryDataProvider {
    private val log = Logger.getInstance(SummaryDataProvider::class.java)

    companion object {
        /**
         * Threshold for switching between in-memory and SQL-based aggregation.
         * Based on performance benchmarks with SQLite on typical user machines.
         */
        private const val IN_MEMORY_THRESHOLD = 20_000
    }

    data class SummaryData(
        val today: Duration,
        val dailyAverage: Duration,
        val thisWeek: Duration,
        val thisMonth: Duration,
        val thisYear: Duration,
        val total: Duration
    )

    /**
     * Computes all summary statistics using an adaptive strategy.
     * Automatically selects the optimal computation method based on data volume.
     */
    fun computeSummary(): SummaryData {
        return try {
            val totalRecords = DatabaseManager.getRecordCount()
            if (totalRecords < IN_MEMORY_THRESHOLD) {
                log.info("Using memory computation strategy (Record count: $totalRecords)")
                computeSummaryInMemory()
            } else {
                log.info("Using database aggregation strategy (Record count: $totalRecords)")
                computeSummaryWithSQL()
            }
        } catch (e: Exception) {
            log.error("Statistical data calculating failed", e)
            SummaryData(
                Duration.ZERO, Duration.ZERO, Duration.ZERO,
                Duration.ZERO, Duration.ZERO, Duration.ZERO
            )
        }
    }

    /**
     * In-memory computation strategy using lightweight DTOs.
     * Optimized for memory efficiency by loading only time ranges.
     * Uses TimeRangeUtils for consistent time boundary calculations.
     */
    private fun computeSummaryInMemory(): SummaryData {
        val sessionTimes = DatabaseManager.getAllActiveSessionTimes()

        if (sessionTimes.isEmpty()) {
            return createEmptySummaryData()
        }

        val today = LocalDate.now()
        val timeBoundaries = buildTimeBoundaries(today)
        val aggregates = aggregateSessionData(sessionTimes, timeBoundaries)
        val dailyAverage = calculateDailyAverage(aggregates.totalSeconds, aggregates.firstDate, today)

        return SummaryData(
            today = Duration.ofSeconds(aggregates.todaySeconds),
            dailyAverage = dailyAverage,
            thisWeek = Duration.ofSeconds(aggregates.thisWeekSeconds),
            thisMonth = Duration.ofSeconds(aggregates.thisMonthSeconds),
            thisYear = Duration.ofSeconds(aggregates.thisYearSeconds),
            total = Duration.ofSeconds(aggregates.totalSeconds)
        )
    }

    /**
     * Holds time boundaries for all tracked periods.
     * Immutable data structure for thread-safe boundary calculations.
     */
    private data class TimeBoundaries(
        val todayStart: LocalDateTime,
        val todayEnd: LocalDateTime,
        val weekStart: LocalDateTime,
        val weekEnd: LocalDateTime,
        val monthStart: LocalDateTime,
        val monthEnd: LocalDateTime,
        val yearStart: LocalDateTime,
        val yearEnd: LocalDateTime
    )

    /**
     * Aggregated statistics from session data processing.
     * Separates data accumulation from business logic.
     */
    private data class SessionAggregates(
        val todaySeconds: Long,
        val thisWeekSeconds: Long,
        val thisMonthSeconds: Long,
        val thisYearSeconds: Long,
        val totalSeconds: Long,
        val firstDate: LocalDate?
    )

    /**
     * Creates a zero-initialized SummaryData instance.
     * Used as a safe default when no session data exists.
     */
    private fun createEmptySummaryData(): SummaryData {
        return SummaryData(
            Duration.ZERO, Duration.ZERO, Duration.ZERO,
            Duration.ZERO, Duration.ZERO, Duration.ZERO
        )
    }

    /**
     * Constructs time boundaries for all tracking periods based on reference date.
     * Centralizes boundary calculation logic for consistency.
     *
     * @param referenceDate The date to calculate boundaries from (typically today)
     * @return TimeBoundaries containing all period start/end times
     */
    private fun buildTimeBoundaries(referenceDate: LocalDate): TimeBoundaries {
        return TimeBoundaries(
            todayStart = TimeRangeUtils.getDayStart(referenceDate),
            todayEnd = TimeRangeUtils.getDayEnd(referenceDate),
            weekStart = TimeRangeUtils.getWeekStart(referenceDate),
            weekEnd = TimeRangeUtils.getWeekEnd(referenceDate),
            monthStart = TimeRangeUtils.getMonthStart(referenceDate),
            monthEnd = TimeRangeUtils.getMonthEnd(referenceDate),
            yearStart = TimeRangeUtils.getYearStart(referenceDate),
            yearEnd = TimeRangeUtils.getYearEnd(referenceDate)
        )
    }

    /**
     * Aggregates session data into period-specific totals using single-pass calculation.
     * Efficiently processes all sessions once and accumulates time for each period.
     *
     * Performance: O(n) where n is the number of sessions
     *
     * @param sessions List of session time ranges to process
     * @param boundaries Time boundaries for period calculations
     * @return SessionAggregates containing accumulated seconds per period
     */
    private fun aggregateSessionData(
        sessions: List<SessionSummaryDTO>,
        boundaries: TimeBoundaries
    ): SessionAggregates {
        var todaySeconds = 0L
        var thisWeekSeconds = 0L
        var thisMonthSeconds = 0L
        var thisYearSeconds = 0L
        var totalSeconds = 0L
        var firstDate: LocalDate? = null

        for (session in sessions) {
            val sessionDuration = ChronoUnit.SECONDS.between(session.startTime, session.endTime)
            totalSeconds += sessionDuration

            // Track the earliest session date for daily average calculation
            val sessionDate = session.startTime.toLocalDate()
            if (firstDate == null || sessionDate.isBefore(firstDate)) {
                firstDate = sessionDate
            }

            // Accumulate overlapping time for each period
            todaySeconds += calculatePeriodOverlap(session, boundaries.todayStart, boundaries.todayEnd)
            thisWeekSeconds += calculatePeriodOverlap(session, boundaries.weekStart, boundaries.weekEnd)
            thisMonthSeconds += calculatePeriodOverlap(session, boundaries.monthStart, boundaries.monthEnd)
            thisYearSeconds += calculatePeriodOverlap(session, boundaries.yearStart, boundaries.yearEnd)
        }

        return SessionAggregates(
            todaySeconds = todaySeconds,
            thisWeekSeconds = thisWeekSeconds,
            thisMonthSeconds = thisMonthSeconds,
            thisYearSeconds = thisYearSeconds,
            totalSeconds = totalSeconds,
            firstDate = firstDate
        )
    }

    /**
     * Calculates overlap between a session and a time period.
     * Returns 0 if there's no overlap, otherwise returns overlapping duration in seconds.
     *
     * @param session The coding session to check
     * @param periodStart Start of the time period (inclusive)
     * @param periodEnd End of the time period (exclusive)
     * @return Overlapping duration in seconds (0 if no overlap)
     */
    private fun calculatePeriodOverlap(
        session: SessionSummaryDTO,
        periodStart: LocalDateTime,
        periodEnd: LocalDateTime
    ): Long {
        return if (session.endTime.isAfter(periodStart) && session.startTime.isBefore(periodEnd)) {
            calculateOverlap(session.startTime, session.endTime, periodStart, periodEnd)
        } else {
            0L
        }
    }

    /**
     * Calculates daily average coding time based on total accumulation and date range.
     * Uses the earliest session date to determine the calculation window.
     *
     * @param totalSeconds Total accumulated coding time in seconds
     * @param firstDate Earliest session date (null if no sessions)
     * @param today Current date for calculation
     * @return Duration representing average daily coding time
     */
    private fun calculateDailyAverage(
        totalSeconds: Long,
        firstDate: LocalDate?,
        today: LocalDate
    ): Duration {
        return if (firstDate != null) {
            val daysSinceFirst = ChronoUnit.DAYS.between(firstDate, today).coerceAtLeast(1)
            Duration.ofSeconds(totalSeconds / daysSinceFirst)
        } else {
            Duration.ZERO
        }
    }


    /**
     * SQL-based computation strategy for large datasets.
     * Uses multiple indexed queries to minimize data transfer and memory usage.
     *
     * Performance characteristics:
     * - Leverages database indexes for O(log n) lookups per query
     * - Minimal memory footprint
     * - Best for: >= 20,000 records
     */
    private fun computeSummaryWithSQL(): SummaryData {
        val now = LocalDateTime.now()
        val today = now.toLocalDate()

        // Use centralized time range utility for consistent calculations
        val todayStart = TimeRangeUtils.getDayStart(today)
        val todayEnd = TimeRangeUtils.getDayEnd(today)
        val todayDuration = DatabaseManager.getCodingTimeForPeriod(todayStart, todayEnd)

        val weekStart = TimeRangeUtils.getWeekStart(today)
        val weekEnd = TimeRangeUtils.getWeekEnd(today)
        val thisWeekDuration = DatabaseManager.getCodingTimeForPeriod(weekStart, weekEnd)

        val monthStart = TimeRangeUtils.getMonthStart(today)
        val monthEnd = TimeRangeUtils.getMonthEnd(today)
        val thisMonthDuration = DatabaseManager.getCodingTimeForPeriod(monthStart, monthEnd)

        val yearStart = TimeRangeUtils.getYearStart(today)
        val yearEnd = TimeRangeUtils.getYearEnd(today)
        val thisYearDuration = DatabaseManager.getCodingTimeForPeriod(yearStart, yearEnd)

        val totalDuration = DatabaseManager.getTotalCodingTime()
        val firstRecordDate = DatabaseManager.getFirstRecordDate()

        val dailyAverage = if (firstRecordDate != null) {
            val daysSinceFirst = ChronoUnit.DAYS.between(firstRecordDate, today).coerceAtLeast(1)
            Duration.ofSeconds(totalDuration.toSeconds() / daysSinceFirst)
        } else {
            Duration.ZERO
        }

        return SummaryData(
            today = todayDuration,
            dailyAverage = dailyAverage,
            thisWeek = thisWeekDuration,
            thisMonth = thisMonthDuration,
            thisYear = thisYearDuration,
            total = totalDuration
        )
    }


    /**
     * Calculates the overlapping duration between a session and a time range.
     * Handles sessions that partially overlap with the target range.
     *
     * @return Overlapping duration in seconds
     */
    private fun calculateOverlap(
        sessionStart: LocalDateTime,
        sessionEnd: LocalDateTime,
        rangeStart: LocalDateTime,
        rangeEnd: LocalDateTime
    ): Long {
        val effectiveStart = maxOf(sessionStart, rangeStart)
        val effectiveEnd = minOf(sessionEnd, rangeEnd)
        return if (effectiveStart.isBefore(effectiveEnd)) {
            ChronoUnit.SECONDS.between(effectiveStart, effectiveEnd)
        } else {
            0L
        }
    }
}