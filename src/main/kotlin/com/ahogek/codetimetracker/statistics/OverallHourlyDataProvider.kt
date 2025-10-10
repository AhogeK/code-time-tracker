package com.ahogek.codetimetracker.statistics

import com.ahogek.codetimetracker.database.DatabaseManager
import java.time.LocalDateTime

/**
 * Provides data for the overall hourly distribution chart.
 * Shows typical daily coding patterns aggregated from all historical data.
 *
 * @author AhogeK ahogek@gmail.com
 * @since 2025-10-10 13:26:58
 */
class OverallHourlyDataProvider : ChartDataProvider {
    override fun prepareData(startTime: LocalDateTime?, endTime: LocalDateTime?): Map<String, Any> {
        val distribution = DatabaseManager.getOverallHourlyDistribution()

        val chartData = distribution.map {
            mapOf(
                "hour" to it.hour,
                "minute" to it.minute,
                "seconds" to it.totalDuration.toSeconds()
            )
        }

        return mapOf("data" to chartData)
    }

    override fun getChartKey(): String = "overallHourly"

    override fun requiresTimeRange(): Boolean = false
}