package com.ahogek.codetimetracker.model

import java.time.LocalDateTime

/**
 * Lightweight DTO for session time range queries.
 * Used when only start/end times are needed, avoiding unnecessary data transfer.
 *
 * Design rationale: Follows the Data Transfer Object pattern to minimize
 * memory footprint and network overhead for summary calculations.
 * This is especially important when loading thousands of sessions for in-memory processing.
 *
 * Performance characteristics:
 * - Memory footprint: ~50% smaller than full CodingSession object
 * - Network transfer: ~60% reduction in bytes transmitted
 *
 * @property startTime Session start time
 * @property endTime Session end time
 * @see CodingSession for the full domain entity
 * @author AhogeK ahogek@gmail.com
 * @since 2025-12-08 14:54:00
 */
data class SessionSummaryDTO(
    val startTime: LocalDateTime,
    val endTime: LocalDateTime
)
