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
class SummaryDataProvider : ChartDataProvider {
    private val log = Logger.getInstance(SummaryDataProvider::class.java)

    companion object {
        /**
         * Threshold for switching between in-memory and SQL-based aggregation.
         * Based on performance benchmarks with SQLite on typical user machines.
         */
        private const val IN_MEMORY_THRESHOLD = 20_000
    }

    /**
     * Internal data holder for duration statistics.
     */
    private data class SummaryData(
        val today: Duration,
        val dailyAverage: Duration,
        val thisWeek: Duration,
        val thisMonth: Duration,
        val thisYear: Duration,
        val total: Duration
    )

    /**
     * Implementation of ChartDataProvider.
     * Maps the internal Duration objects to seconds (Long) for the frontend.
     */
    override fun prepareData(
        startTime: LocalDateTime?,
        endTime: LocalDateTime?
    ): Map<String, Any> {
        // Compute the summary using the existing adaptive strategy
        val summary = computeSummary()

        // Transform strict types (Duration) to frontend-friendly types (Seconds/Long)
        return mapOf(
            "today" to summary.today.toSeconds(),
            "dailyAverage" to summary.dailyAverage.toSeconds(),
            "thisWeek" to summary.thisWeek.toSeconds(),
            "thisMonth" to summary.thisMonth.toSeconds(),
            "thisYear" to summary.thisYear.toSeconds(),
            "total" to summary.total.toSeconds()
        )
    }

    override fun getChartKey(): String = "summaryData"

    override fun requiresTimeRange(): Boolean = false

    /**
     * Computes all summary statistics using an adaptive strategy.
     * Automatically selects the optimal computation method based on data volume.
     */
    private fun computeSummary(): SummaryData {
        return try {
            val totalRecords = DatabaseManager.getRecordCount()
            if (totalRecords < IN_MEMORY_THRESHOLD) {
                log.debug("Using memory computation strategy (Record count: $totalRecords)")
                computeSummaryInMemory()
            } else {
                log.debug("Using database aggregation strategy (Record count: $totalRecords)")
                computeSummaryWithSQL()
            }
        } catch (e: Exception) {
            log.error("Statistical data calculating failed", e)
            createEmptySummaryData()
        }
    }

    /**
     * In-memory computation strategy using lightweight DTOs.
     * Optimized for memory efficiency by loading only time ranges.
     * Uses TimeRangeUtils for consistent time boundary calculations and overlap merging.
     */
    private fun computeSummaryInMemory(): SummaryData {
        val sessionTimes = DatabaseManager.getAllActiveSessionTimes()

        if (sessionTimes.isEmpty()) {
            return createEmptySummaryData()
        }

        val today = LocalDate.now()
        val boundaries = buildTimeBoundaries(today)

        // Prepare lists for each period to collect intervals
        val todayIntervals = mutableListOf<Pair<LocalDateTime, LocalDateTime>>()
        val weekIntervals = mutableListOf<Pair<LocalDateTime, LocalDateTime>>()
        val monthIntervals = mutableListOf<Pair<LocalDateTime, LocalDateTime>>()
        val yearIntervals = mutableListOf<Pair<LocalDateTime, LocalDateTime>>()
        val totalIntervals = mutableListOf<Pair<LocalDateTime, LocalDateTime>>()

        var firstDate: LocalDate? = null

        for (session in sessionTimes) {
            // Track total intervals (lifetime)
            totalIntervals.add(session.startTime to session.endTime)

            // Track earliest date for daily average calculation
            val sessionDate = session.startTime.toLocalDate()
            if (firstDate == null || sessionDate.isBefore(firstDate)) {
                firstDate = sessionDate
            }

            // Filter and clip sessions for each period using helper
            addIfOverlaps(session, boundaries.todayStart, boundaries.todayEnd, todayIntervals)
            addIfOverlaps(session, boundaries.weekStart, boundaries.weekEnd, weekIntervals)
            addIfOverlaps(session, boundaries.monthStart, boundaries.monthEnd, monthIntervals)
            addIfOverlaps(session, boundaries.yearStart, boundaries.yearEnd, yearIntervals)
        }

        // Calculate durations with overlap merging using TimeRangeUtils (Crucial for multi-project/window accuracy)
        val totalDuration = TimeRangeUtils.calculateMergedDuration(totalIntervals)

        // Calculate daily average
        val dailyAverage = if (firstDate != null) {
            val daysSinceFirst = ChronoUnit.DAYS.between(firstDate, today).coerceAtLeast(1)
            Duration.ofSeconds(totalDuration.toSeconds() / daysSinceFirst)
        } else {
            Duration.ZERO
        }

        return SummaryData(
            today = TimeRangeUtils.calculateMergedDuration(todayIntervals),
            dailyAverage = dailyAverage,
            thisWeek = TimeRangeUtils.calculateMergedDuration(weekIntervals),
            thisMonth = TimeRangeUtils.calculateMergedDuration(monthIntervals),
            thisYear = TimeRangeUtils.calculateMergedDuration(yearIntervals),
            total = totalDuration
        )
    }

    /**
     * Helper to add a session to a list if it overlaps with the period, clipping it to the period boundaries.
     */
    private fun addIfOverlaps(
        session: SessionSummaryDTO,
        periodStart: LocalDateTime,
        periodEnd: LocalDateTime,
        targetList: MutableList<Pair<LocalDateTime, LocalDateTime>>
    ) {
        // Compare comparable directly using standard library functions
        val effectiveStart = if (session.startTime.isAfter(periodStart)) session.startTime else periodStart
        val effectiveEnd = if (session.endTime.isBefore(periodEnd)) session.endTime else periodEnd

        if (effectiveStart.isBefore(effectiveEnd)) {
            targetList.add(effectiveStart to effectiveEnd)
        }
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
}