package com.ahogek.codetimetracker.util

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.TemporalAdjusters

/**
 * Utility functions for calculating time ranges in a locale-independent manner.
 * All week calculations follow ISO 8601 standard (Monday as first day of week).
 *
 * Design rationale: Centralized time range logic prevents inconsistencies
 * across different components and ensures predictable behavior regardless
 * of user's system locale settings.
 *
 * @author AhogeK ahogek@gmail.com
 * @since 2025-12-08 16:08:13
 */
object TimeRangeUtils {
    /**
     * Returns the start of the current week (Monday 00:00:00) according to ISO 8601.
     *
     * Examples:
     * - If today is Monday 2025-12-08, returns 2025-12-08 00:00:00
     * - If today is Sunday 2025-12-14, returns 2025-12-08 00:00:00 (previous Monday)
     * - If today is Wednesday 2025-12-10, returns 2025-12-08 00:00:00
     *
     * @param referenceDate The date to calculate week start from (defaults to today)
     * @return LocalDateTime representing the start of the week (inclusive)
     */
    fun getWeekStart(referenceDate: LocalDate = LocalDate.now()): LocalDateTime {
        return referenceDate
            .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            .atStartOfDay()
    }

    /**
     * Returns the end of the current week (next Monday 00:00:00) according to ISO 8601.
     * This is an exclusive boundary suitable for range queries.
     *
     * Examples:
     * - If today is Monday 2025-12-08, returns 2025-12-15 00:00:00
     * - If today is Sunday 2025-12-14, returns 2025-12-15 00:00:00
     *
     * @param referenceDate The date to calculate week end from (defaults to today)
     * @return LocalDateTime representing the end of the week (exclusive)
     */
    fun getWeekEnd(referenceDate: LocalDate = LocalDate.now()): LocalDateTime {
        return getWeekStart(referenceDate).plusWeeks(1)
    }

    /**
     * Returns the start of the current month (1st day at 00:00:00).
     *
     * Examples:
     * - If today is 2025-12-08, returns 2025-12-01 00:00:00
     * - If today is 2025-12-31, returns 2025-12-01 00:00:00
     *
     * @param referenceDate The date to calculate month start from (defaults to today)
     * @return LocalDateTime representing the start of the month (inclusive)
     */
    fun getMonthStart(referenceDate: LocalDate = LocalDate.now()): LocalDateTime {
        return referenceDate.withDayOfMonth(1).atStartOfDay()
    }

    /**
     * Returns the end of the current month (1st day of next month at 00:00:00).
     * This is an exclusive boundary suitable for range queries.
     *
     * Examples:
     * - If today is 2025-12-08, returns 2026-01-01 00:00:00
     * - If today is 2025-12-31, returns 2026-01-01 00:00:00
     *
     * @param referenceDate The date to calculate month end from (defaults to today)
     * @return LocalDateTime representing the end of the month (exclusive)
     */
    fun getMonthEnd(referenceDate: LocalDate = LocalDate.now()): LocalDateTime {
        return getMonthStart(referenceDate).plusMonths(1)
    }

    /**
     * Returns the start of the current year (January 1st at 00:00:00).
     *
     * Examples:
     * - If today is 2025-12-08, returns 2025-01-01 00:00:00
     * - If today is 2025-12-31, returns 2025-01-01 00:00:00
     *
     * @param referenceDate The date to calculate year start from (defaults to today)
     * @return LocalDateTime representing the start of the year (inclusive)
     */
    fun getYearStart(referenceDate: LocalDate = LocalDate.now()): LocalDateTime {
        return referenceDate.withDayOfYear(1).atStartOfDay()
    }

    /**
     * Returns the end of the current year (January 1st of next year at 00:00:00).
     * This is an exclusive boundary suitable for range queries.
     *
     * Examples:
     * - If today is 2025-12-08, returns 2026-01-01 00:00:00
     * - If today is 2025-12-31, returns 2026-01-01 00:00:00
     *
     * @param referenceDate The date to calculate year end from (defaults to today)
     * @return LocalDateTime representing the end of the year (exclusive)
     */
    fun getYearEnd(referenceDate: LocalDate = LocalDate.now()): LocalDateTime {
        return getYearStart(referenceDate).plusYears(1)
    }

    /**
     * Returns the start of today (00:00:00).
     * Convenience method for consistency with other time range functions.
     *
     * @param referenceDate The date to get start of day from (defaults to today)
     * @return LocalDateTime representing the start of the day (inclusive)
     */
    fun getDayStart(referenceDate: LocalDate = LocalDate.now()): LocalDateTime {
        return referenceDate.atStartOfDay()
    }

    /**
     * Returns the end of today (00:00:00 of next day).
     * This is an exclusive boundary suitable for range queries.
     *
     * @param referenceDate The date to get end of day from (defaults to today)
     * @return LocalDateTime representing the end of the day (exclusive)
     */
    fun getDayEnd(referenceDate: LocalDate = LocalDate.now()): LocalDateTime {
        return referenceDate.plusDays(1).atStartOfDay()
    }
}