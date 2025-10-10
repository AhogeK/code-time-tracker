package com.ahogek.codetimetracker.statistics

import com.ahogek.codetimetracker.database.DatabaseManager
import java.time.LocalDateTime

/**
 * Provides data for the daily hour distribution heatmap chart.
 *
 * @author AhogeK ahogek@gmail.com
 * @since 2025-10-10 09:16:45
 */
class DailyHourDataProvider : ChartDataProvider {
    override fun prepareData(startTime: LocalDateTime?, endTime: LocalDateTime?): Map<String, Any> {
        val distribution = DatabaseManager.getDailyHourDistribution()

        val chartData = distribution.map {
            mapOf(
                "dayOfWeek" to it.dayOfWeek,
                "hour" to it.hourOfDay,
                "seconds" to it.totalDuration.toSeconds()
            )
        }

        return mapOf("data" to chartData)
    }

    override fun getChartKey(): String = "hourlyHeatmap"

    override fun requiresTimeRange(): Boolean = false
}