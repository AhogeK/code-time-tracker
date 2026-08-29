package com.ahogek.codetimetracker.user

import com.ahogek.codetimetracker.database.DatabaseManager
import com.intellij.ide.util.PropertiesComponent
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

    /**
     * Pins the id for tests so unit tests neither touch the shared database nor the
     * IDE properties container. Production callers use [getUserId], which lazily
     * resolves and caches the id; this hook only seeds the same cache.
     */
    internal fun setUserIdForTest(id: String) {
        currentUserId = id
    }

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
                if (currentUserId == null) {
            lock.lock()
            try {
                                if (currentUserId == null) currentUserId = determineUserId()
            } finally {
                lock.unlock()
                        }
        }
        return currentUserId!!
    }

    /**
     * This is the core logic that runs ONLY ONCE per IDE session.
     * It determines the user ID based on the "first-write-wins" principle.
     */
    private fun UserManager.determineUserId(): String {
        // The shared database owns the installation-wide id (independent of coding
        // sessions), so every IDE on this machine resolves the same value from the
        // first run onward. Also cached in the IDE properties as a secondary record.
        val sharedUserId = DatabaseManager.getOrCreateUserId()
        PropertiesComponent.getInstance().setValue(USER_ID_KEY, sharedUserId)
        return sharedUserId
    }
}

