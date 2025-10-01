package com.ahogek.codetimetracker.topics

import com.intellij.util.messages.Topic

/**
 * Topic for listening to time tracking events.
 *
 * @author AhogeK ahogek@gmail.com
 * @since 2025-10-01 02:25:18
 */
interface TimeTrackerListener {

    /**
     * Called when user activity starts after a period of inactivity
     */
    fun onActivityStarted()

    /**
     * Called when user activity stops (idle timeout)
     * The widget should refresh its data from the database
     */
    fun onActivityStopped()
}

object TimeTrackerTopics {
    val ACTIVITY_TOPIC: Topic<TimeTrackerListener> =
        Topic.create("Code Time Tracker Activity", TimeTrackerListener::class.java)
}