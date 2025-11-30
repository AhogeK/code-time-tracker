package com.ahogek.codetimetracker.statistics

import com.ahogek.codetimetracker.database.DatabaseManager
import java.time.LocalDateTime

/**
 * Provides language distribution data for chart rendering.
 * Calculates coding time spent in different programming languages.
 *
 * @author AhogeK ahogek@gmail.com
 * @since 2025-11-30 08:48:07
 */
class LanguageDistributionDataProvider : ChartDataProvider {

    override fun getChartKey(): String = "languageDistribution"

    override fun requiresTimeRange(): Boolean = false

    override fun prepareData(startTime: LocalDateTime?, endTime: LocalDateTime?): Map<String, Any> {
        val languageUsages = DatabaseManager.getLanguageDistribution(startTime, endTime)

        val data = languageUsages.map { usage ->
            mapOf(
                "language" to usage.language,
                "seconds" to usage.totalDuration.toSeconds()
            )
        }

        return mapOf("data" to data)
    }
}