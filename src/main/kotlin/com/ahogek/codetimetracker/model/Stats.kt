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