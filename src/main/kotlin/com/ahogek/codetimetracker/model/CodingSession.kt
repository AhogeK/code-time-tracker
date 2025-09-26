package com.ahogek.codetimetracker.model

import java.time.LocalDateTime

/**
 * 表示一个编码会话，记录了特定语言在一段时间内的编码活动。
 *
 * @property language 语言名称 (例如 "Kotlin", "Java", "Python").
 * @property startTime 会话开始时间.
 * @property endTime 会话结束时间.
 */
data class CodingSession(
    val language: String,
    val startTime: LocalDateTime,
    var endTime: LocalDateTime
)
