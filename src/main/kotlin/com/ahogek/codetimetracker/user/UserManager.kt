package com.ahogek.codetimetracker.user

import com.ahogek.codetimetracker.database.DatabaseManager
import com.intellij.ide.util.PropertiesComponent
import okio.withLock
import java.util.*
import java.util.concurrent.locks.ReentrantLock

/**
 * Manages the unique identifier for the current user/installation.
 * The ID is generated once and then stored persistently in the IDE's properties.
 *
 * @author AhogeK ahogek@gmail.com
 * @since 2025-09-29 19:53:53
 */
object UserManager {

    // A unique key to store our user ID in the IDE's properties
    private const val USER_ID_KEY = "com.ahogek.codetimetracker.userId"

    // A cache to store the user ID in memory after the first lookup
    @Volatile
    private var currentUserId: String? = null

    // A lock to prevent race conditions during the initial lazy-loading
    private val lock = ReentrantLock()

    /**
     * Gets the unique user ID for this plugin instance.
     * If it doesn't exist, it generates a new one and saves it.
     *
     * @return A unique UUID string for the user.
     */
    fun getUserId(): String {
        // Double-checked locking for thread-safe, lazy initialization
        if (currentUserId == null)
            lock.withLock {
                if (currentUserId == null) currentUserId = determineUserId()
            }
        return currentUserId!!
    }

    /**
     * This is the core logic that runs ONLY ONCE per IDE session.
     * It determines the user ID based on the "first-write-wins" principle.
     */
    private fun UserManager.determineUserId(): String {
        val properties = PropertiesComponent.getInstance()

        // If no local ID, check the shared database to see if another IDE has established one.
        // This query runs on the database executor thread to not block.
        val dbUserId = DatabaseManager.getUserIdFromDatabase()
        if (dbUserId != null) {
            properties.setValue(USER_ID_KEY, dbUserId)
            return dbUserId
        }

        // Check for a locally stored ID first (the most common case)
        val localUserId = properties.getValue(USER_ID_KEY)
        if (!localUserId.isNullOrBlank()) return localUserId

        // If we are truly the first, generate a new ID and save it locally
        val newUserId = UUID.randomUUID().toString()
        properties.setValue(USER_ID_KEY, newUserId)
        return newUserId
    }
}

