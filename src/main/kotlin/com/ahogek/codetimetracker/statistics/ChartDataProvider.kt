package com.ahogek.codetimetracker.statistics

import java.time.LocalDateTime

/**
 * Interface for providing chart data.
 * Each chart type should implement this interface to prepare its specific data.
 *
 * @author AhogeK ahogek@gmail.com
 * @since 2025-10-10 09:09:41
 */
interface ChartDataProvider {

    /**
     * Prepares the data for a specific chart type.
     *
     * @param startTime The start of the time range.
     * @param endTime The end of the time range.
     * @return A map containing the prepared data for the chart.
     */
    fun prepareData(startTime: LocalDateTime? = null, endTime: LocalDateTime? = null): Map<String, Any>

    /**
     * Returns the identifier for this chart type.
     * This will be used as the key in the payload sent to JavaScript
     */
    fun getChartKey(): String

    /**
     * Indicates whether this chart requires a time range.
     * @return true if the chart needs startTime/endTime, false if it uses all data
     */
    fun requiresTimeRange(): Boolean = true
}