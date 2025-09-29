package com.ahogek.codetimetracker.model

import java.time.LocalDateTime

/**
 * A coding session, representing the coding activities of a specific language over a period of time.
 *
 * @property language Language name (e.g., "Kotlin", "Java", "Python").
 * @property startTime Session start time.
 * @property endTime Session end time.
 */
data class CodingSession(
    val language: String,
    val startTime: LocalDateTime,
    var endTime: LocalDateTime
)
