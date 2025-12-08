package com.ahogek.codetimetracker.model

import java.time.LocalDateTime

/**
 * Represents a coding session with cloud sync metadata.
 *
 * Sync strategy:
 * - is_synced=false: Local changes not yet synced to cloud
 * - is_synced=true: Successfully synced with cloud server
 * - sync_version: Incremented on each modification for conflict detection
 *
 * @property sessionUuid Globally unique identifier for cross-device sync
 * @property userId User identifier for multi-user cloud deployments
 * @property projectName Project name for session categorization
 * @property language Programming language used
 * @property platform Operating system (macOS, Windows, Linux)
 * @property ideName IDE product name (IntelliJ IDEA, PyCharm, etc.)
 * @property startTime Session start timestamp
 * @property endTime Session end timestamp
 * @property lastModified Last modification timestamp for conflict resolution
 * @property isSynced Sync state flag (false = pending sync, true = synced)
 * @property syncedAt Timestamp of last successful cloud sync (null if never synced)
 * @property syncVersion Optimistic locking version for concurrent sync safety
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
    val lastModified: LocalDateTime,
    val isSynced: Boolean = false,
    val syncedAt: LocalDateTime? = null,
    val syncVersion: Int = 0
)
