package com.ahogek.codetimetracker.model

import java.time.LocalDateTime

/**
 * A coding session, representing the coding activities of a specific language over a period of time.
 *
 * @property projectName The display name of the project (e.g., "my-awesome-app").
 * @property projectPath The unique, absolute path of the project, used as a reliable identifier.
 * @property language The name of the programming language (e.g., "Kotlin", "Java").
 * @property platform The operating system where the coding activity took place (e.g., "macOS", "Windows 11").
 * @property startTime The timestamp when the session started.
 * @property endTime The timestamp of the last recorded activity in this session.
 */
data class CodingSession(
    val projectName: String,
    val projectPath: String,
    val language: String,
    val platform: String,
    val startTime: LocalDateTime,
    var endTime: LocalDateTime
)
