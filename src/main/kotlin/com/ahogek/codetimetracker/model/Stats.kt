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
 * Represents the total coding duration for a specific hour of the day.
 *
 * This data class is used to store aggregated coding statistics for hourly analysis.
 * The average is calculated by dividing the total duration by the number of active days.
 *
 * @property hour The hour of the day (0-23, where 0 represents midnight and 23 represents 11 PM).
 * @property minute The starting minute of the interval (0 or 30). Defaults to 0 for full hour.
 *                  In full-hour mode, this is always 0. In half-hour mode, this can be 30.
 * @property totalDuration The average time spent coding during this hour across all active days.
 *                         This is calculated as: total_seconds_for_this_hour / total_active_days
 */
data class HourlyUsage(
    val hour: Int,
    val minute: Int = 0,
    val totalDuration: Duration
)

/**
 * Represents the complete result of hourly distribution analysis including metadata.
 *
 * This class combines the hourly coding statistics with information about the dataset,
 * allowing consumers to understand the context of the distribution data (e.g., how many
 * days of data the average is based on).
 *
 * @property distribution A list of [HourlyUsage] objects, each containing coding duration
 *                        for a specific hour of the day. The list should contain 24 entries
 *                        (one for each hour) when using full-hour granularity.
 * @property totalDays The total number of active days (days with at least one coding session)
 *                     used to calculate the averages in the distribution. This value is at
 *                     least 1 to prevent division by zero errors.
 */
data class HourlyDistributionResult(
    val distribution: List<HourlyUsage>,
    val totalDays: Int
)

/**
 * Represents the total coding time for a specific language.
 *
 * @property language The name of the programming language.
 * @property totalDuration The total time spent coding in this language.
 */
data class LanguageUsage(
    val language: String,
    val totalDuration: Duration
)

/**
 * Represents the total coding time for a specific project.
 *
 * @property projectName The name of the project.
 * @property totalDuration The total time spent on this project.
 */
data class ProjectUsage(
    val projectName: String,
    val totalDuration: Duration
)

/**
 * Represents coding time distribution across different parts of the day.
 *
 * @property timeOfDay The name of the period (e.g., "Morning", "Daytime", "Evening", "Night").
 * @property totalDuration The total time spent during this period.
 */
data class TimeOfDayUsage(
    val timeOfDay: String,
    val totalDuration: Duration
)