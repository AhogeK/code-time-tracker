package com.ahogek.codetimetracker.service

import com.ahogek.codetimetracker.model.TimePeriod
import com.intellij.openapi.diagnostic.Logger
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages time period boundaries and detects period transitions
 * This class tracks when periods (day, week, month, year) change
 * to trigger UI resets while preserving actual session data
 *
 * @author AhogeK ahogek@gmail.com
 * @since 2025-11-26 02:30:28
 */
class PeriodManager {

    companion object {
        private val log = Logger.getInstance(PeriodManager::class.java)
    }

    /**
     * Stores the start time of each period type
     * Used to detect when a period boundary has been crossed
     */
    private val periodStartTimes = ConcurrentHashMap<TimePeriod, LocalDateTime>()

    init {
        initializePeriods()
    }

    /**
     * Initialize all period start times based on current date/time
     */
    private fun initializePeriods() {
        val now = LocalDateTime.now()
        periodStartTimes[TimePeriod.TODAY] = now.truncatedTo(ChronoUnit.DAYS)
        periodStartTimes[TimePeriod.THIS_WEEK] = now.with(DayOfWeek.MONDAY).truncatedTo(ChronoUnit.DAYS)
        periodStartTimes[TimePeriod.THIS_MONTH] = now.withDayOfMonth(1).truncatedTo(ChronoUnit.DAYS)
        periodStartTimes[TimePeriod.THIS_YEAR] = now.withDayOfYear(1).truncatedTo(ChronoUnit.DAYS)

        log.info("Period boundaries initialized: $periodStartTimes")
    }

    /**
     * Check if a specific period has changed (crossed its boundary)
     *
     * @param period The time period to check
     * @return true if the period has changed, false otherwise
     */
    fun isPeriodChanged(period: TimePeriod): Boolean {
        val currentPeriodStart = calculatePeriodStart(period)
        val savedPeriodStart = periodStartTimes[period]

        // Handle null case (first initialization) and use isEqual() for LocalDateTime comparison
        val changed = savedPeriodStart?.let { !currentPeriodStart.isEqual(it) } ?: true

        if (changed) {
            log.info("Period $period changed: $savedPeriodStart -> $currentPeriodStart")
        }

        return changed
    }

    /**
     * Update the stored start time for a period after a reset
     *
     * @param period The period to reset
     */
    fun resetPeriod(period: TimePeriod) {
        val newStart = calculatePeriodStart(period)
        periodStartTimes[period] = newStart
        log.info("Period $period reset to: $newStart")
    }

    /**
     * Calculate the start time of a period based on current date/time
     *
     * @param period The period type
     * @return The LocalDateTime representing the start of that period
     */
    private fun calculatePeriodStart(period: TimePeriod): LocalDateTime {
        val now = LocalDateTime.now()
        return when (period) {
            TimePeriod.TODAY -> now.truncatedTo(ChronoUnit.DAYS)
            TimePeriod.THIS_WEEK -> now.with(DayOfWeek.MONDAY).truncatedTo(ChronoUnit.DAYS)
            TimePeriod.THIS_MONTH -> now.withDayOfMonth(1).truncatedTo(ChronoUnit.DAYS)
            TimePeriod.THIS_YEAR -> now.withDayOfYear(1).truncatedTo(ChronoUnit.DAYS)
        }
    }
}