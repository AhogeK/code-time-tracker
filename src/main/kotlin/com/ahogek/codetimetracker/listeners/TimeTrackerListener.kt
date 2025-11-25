package com.ahogek.codetimetracker.listeners

/**
 * listening to time tracking events.
 *
 * @author AhogeK ahogek@gmail.com
 * @since 2025-11-26 04:10:20
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