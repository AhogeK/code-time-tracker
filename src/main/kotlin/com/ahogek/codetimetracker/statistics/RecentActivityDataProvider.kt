package com.ahogek.codetimetracker.statistics

import com.ahogek.codetimetracker.database.DatabaseManager
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Provides coding activity data for the last 30 days.
 * Displays a trend line of daily coding time.
 *
 * @author AhogeK ahogek@gmail.com
 * @since 2025-12-13 02:08:42
 */
class RecentActivityDataProvider : ChartDataProvider {
    override fun prepareData(
        startTime: LocalDateTime?,
        endTime: LocalDateTime?
    ): Map<String, Any> {
        // Force the range to the last 30 days (inclusive of today)
        val end = LocalDateTime.now()
        val start = end.minusDays(29).withHour(0).withMinute(0).withSecond(0)

        // Reuse the existing daily calculation logic (SQL) from DatabaseManager
        val dailySummaries = DatabaseManager.getDailyCodingTimeForHeatmap(start, end)
        val dataMap = dailySummaries.associate { it.date to it.totalDuration.toSeconds() }

        // Fill in missing days with 0 to ensure a continuous X-axis
        val chartData = mutableListOf<Map<String, Any>>()
        var current = start.toLocalDate()
        val endDate = end.toLocalDate()
        val dateFormatter = DateTimeFormatter.ofPattern("MM-dd")

        while (!current.isAfter(endDate)) {
            val seconds = dataMap[current] ?: 0L
            chartData.add(
                mapOf(
                    "date" to current.format(dateFormatter),
                    "fullDate" to current.toString(),
                    "seconds" to seconds
                )
            )
            current = current.plusDays(1)
        }

        return mapOf(
            "data" to chartData,
            "totalSeconds" to chartData.sumOf { it["seconds"] as Long }
        )
    }

    override fun getChartKey(): String = "recentActivity"

    // This chart ignores the global time range picker and always shows the last 30 days
    override fun requiresTimeRange(): Boolean = false
}