package com.ahogek.codetimetracker.statistics

import com.ahogek.codetimetracker.database.DatabaseManager
import java.time.LocalDateTime

/**
 * Provides time-of-day distribution data for chart rendering.
 * Calculates coding time spent in different time periods (Night, Morning, Daytime, Evening).
 *
 * @author AhogeK ahogek@gmail.com
 * @since 2025-11-30 09:38:18
 */
class TimeOfDayDistributionDataProvider : ChartDataProvider {

    override fun getChartKey(): String = "timeOfDayDistribution"

    override fun requiresTimeRange(): Boolean = false

    override fun prepareData(startTime: LocalDateTime?, endTime: LocalDateTime?): Map<String, Any> {
        val timeOfDayUsages = DatabaseManager.getTimeOfDayDistribution(startTime, endTime)

        val data = timeOfDayUsages.map { usage ->
            mapOf(
                "timeOfDay" to usage.timeOfDay,
                "seconds" to usage.totalDuration.toSeconds()
            )
        }

        return mapOf("data" to data)
    }
}