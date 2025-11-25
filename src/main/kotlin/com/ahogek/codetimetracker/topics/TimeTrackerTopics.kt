package com.ahogek.codetimetracker.topics

import com.ahogek.codetimetracker.listeners.PeriodResetListener
import com.ahogek.codetimetracker.listeners.TimeTrackerListener
import com.intellij.util.messages.Topic

/**
 * @author AhogeK ahogek@gmail.com
 * @since 2025-11-26 04:10:59
 */
object TimeTrackerTopics {
    val ACTIVITY_TOPIC: Topic<TimeTrackerListener> =
        Topic.create("Code Time Tracker Activity", TimeTrackerListener::class.java)

    val PERIOD_RESET_TOPIC: Topic<PeriodResetListener> =
        Topic.create("Period Reset Events", PeriodResetListener::class.java)
}