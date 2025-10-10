package com.ahogek.codetimetracker.statistics

import com.ahogek.codetimetracker.database.DatabaseManager
import java.time.LocalDateTime

/**
 * Provides data for the yearly activity heatmap chart.
 *
 * @author AhogeK ahogek@gmail.com
 * @since 2025-10-10 09:15:11
 */
class YearlyActivityDataProvider : ChartDataProvider {

    override fun prepareData(
        startTime: LocalDateTime?,
        endTime: LocalDateTime?
    ): Map<String, Any> {
        require(startTime != null && endTime != null) {
            "YearlyActivityDataProvider requires both startTime and endTime"
        }

        val dailySummary = DatabaseManager.getDailyCodingTimeForHeatmap(startTime, endTime)
        val codingStreaks = DatabaseManager.getCodingStreaks(startTime, endTime)

        val chartData = dailySummary.map {
            mapOf(
                "date" to it.date.toString(),
                "seconds" to it.totalDuration.toSeconds()
            )
        }

        return mapOf(
            "data" to chartData,
            "streaks" to mapOf(
                "current" to codingStreaks.currentStreak,
                "max" to codingStreaks.maxStreak,
                "totalDays" to dailySummary.size
            )
        )
    }

    override fun getChartKey(): String = "yearlyActivity"

    override fun requiresTimeRange(): Boolean = true
}