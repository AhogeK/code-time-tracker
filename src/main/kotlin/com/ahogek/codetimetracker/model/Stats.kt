package com.ahogek.codetimetracker.model

import java.time.Duration
import java.time.LocalDate

/**
 * Represents the total coding duration for a single day.
 * Used for heatmap visualizations.
 *
 * @property date The specific date of the summary
 * @property totalDuration The total time spent coding on that date
 */
data class DailySummary(
    val date: LocalDate,
    val totalDuration: Duration
)

/**
 * Holds statistics about the user's consecutive coding days
 *
 * @property currentStreak The number of consecutive days the user has coded up to today (or yesterday)
 * @property maxStreak The longest consecutive coding streak ever recorded for the user
 */
data class CodingStreaks(
    val currentStreak: Int,
    val maxStreak: Int
)

/**
 * Represents coding activity within a specific hour of a specific day of the week.
 *
 * @property dayOfWeek The day of the week (1 for Monday, 7 for Sunday).
 * @property hourOfDay The hour of the day (0-23).
 * @property totalDuration The total time spent during this specific hour block.
 */

data class HourlyDistribution(
    val dayOfWeek: Int,
    val hourOfDay: Int,
    val totalDuration: Duration
)

/**
 * Represents total coding activity within a specific hour of the day, aggregated across all days.
 *
 * @property hourOfDay The hour of the day (0-23).
 * @property totalDuration The total time spent during this hour across the entire queried period.
 */
data class HourlyUsage(
    val hourOfDay: Int,
    val totalDuration: Duration
)