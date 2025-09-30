package com.ahogek.codetimetracker.user

import com.intellij.ide.util.PropertiesComponent
import java.util.*

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

    /**
     * Gets the unique user ID for this plugin instance.
     * If it doesn't exist, it generates a new one and saves it.
     *
     * @return A unique UUID string for the user.
     */
    fun getUserId(): String {
        val properties = PropertiesComponent.getInstance()
        var userId = properties.getValue(USER_ID_KEY)
        if (userId.isNullOrBlank()) {
            userId = UUID.randomUUID().toString()
            properties.setValue(USER_ID_KEY, userId)
        }
        return userId
    }
}