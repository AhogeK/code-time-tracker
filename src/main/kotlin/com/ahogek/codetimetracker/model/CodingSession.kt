package com.ahogek.codetimetracker.model

import java.time.LocalDateTime

/**
 * Represents a coding session.
 *
 * @property sessionUuid Unique identifier for this session
 * @property userId User identifier
 * @property projectName Name of the project
 * @property language Programming language
 * @property platform Operating system platform
 * @property ideName IDE name
 * @property startTime Session start time
 * @property endTime Session end time
 * @property lastModified Last modification timestamp
 */
data class CodingSession(
    val sessionUuid: String,
    val userId: String,
    val projectName: String,
    val language: String,
    val platform: String,
    val ideName: String,
    val startTime: LocalDateTime,
    val endTime: LocalDateTime,
    val lastModified: LocalDateTime
)
