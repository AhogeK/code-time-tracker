package com.ahogek.codetimetracker.model

/**
 * Represents different time periods for tracking statistics
 *
 * @author AhogeK ahogek@gmail.com
 * @since 2025-11-26 02:29:14
 */
enum class TimePeriod {
    /**
     * Represents the current day (midnight to midnight)
     */
    TODAY,

    /**
     * Represents the current week (Monday to Sunday)
     */
    THIS_WEEK,

    /**
     * Represents the current month (1st to last day of month)
     */
    THIS_MONTH,

    /**
     * Represents the current year (January 1st to December 31st)
     */
    THIS_YEAR
}