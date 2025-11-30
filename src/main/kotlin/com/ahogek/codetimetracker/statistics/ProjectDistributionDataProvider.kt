package com.ahogek.codetimetracker.statistics

import com.ahogek.codetimetracker.database.DatabaseManager
import java.time.LocalDateTime

/**
 * Provides project distribution data for chart rendering.
 * Calculates coding time spent in different projects.
 *
 * @author AhogeK ahogek@gmail.com
 * @since 2025-11-30 09:16:22
 */
class ProjectDistributionDataProvider : ChartDataProvider {

    override fun getChartKey(): String = "projectDistribution"

    override fun requiresTimeRange(): Boolean = false

    override fun prepareData(startTime: LocalDateTime?, endTime: LocalDateTime?): Map<String, Any> {
        val projectUsages = DatabaseManager.getProjectDistribution(startTime, endTime)

        val data = projectUsages.map { usage ->
            mapOf(
                "project" to usage.projectName,
                "seconds" to usage.totalDuration.toSeconds()
            )
        }

        return mapOf("data" to data)
    }
}