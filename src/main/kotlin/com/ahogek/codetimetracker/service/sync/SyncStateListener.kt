package com.ahogek.codetimetracker.service.sync

import com.intellij.util.messages.Topic

/**
 * Notifies listeners when a sync round finishes, so open UIs (settings page) can refresh
 * their state immediately instead of polling the database. Fired for both successful and
 * failed rounds; the listener re-reads the persisted last-sync time and error.
 *
 * @author AhogeK ahogek@gmail.com
 * @since 2026-08-29
 */
fun interface SyncStateListener {

    /** Invoked after a sync round completes (success or failure). */
    fun syncCompleted()

    companion object {
        val TOPIC = Topic.create("code-time-tracker.syncState", SyncStateListener::class.java)
    }
}
