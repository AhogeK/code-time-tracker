package com.ahogek.codetimetracker.util

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.SystemInfo
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Provides cross-platform paths for code time tracker data storage
 *
 * @author AhogeK ahogek@gmail.com
 * @since 2025-11-27 00:24:54
 */
object PathUtils {

    private val log = Logger.getInstance(PathUtils::class.java)

    /**
     * Get the application data directory based on platform conventions
     *
     * - macOS: ~/.config/code-time-tracker/
     * - Linux: ~/.config/code-time-tracker/
     * - Windows: %APPDATA%\code-time-tracker\
     *
     * @return The base data directory path
     */
    fun getAppDataPath(): Path {
        val userHome = System.getProperty("user.home")

        val basePath = when {
            SystemInfo.isMac || SystemInfo.isLinux -> {
                // Unix-like systems: ~/.config/code-time-tracker
                Paths.get(userHome, ".config", "code-time-tracker")
            }

            SystemInfo.isWindows -> {
                // Windows: %APPDATA%\code-time-tracker
                val appData = System.getenv("APPDATA") ?: Paths.get(userHome, "AppData", "Roaming").toString()
                Paths.get(appData, "code-time-tracker")
            }

            else -> {
                // Fallback for unknown platforms
                log.warn("Unknown platform, using fallback path")
                Paths.get(userHome, ".code-time-tracker")
            }
        }

        log.info("Using application data path: $basePath")
        return basePath
    }

    /**
     * Get the database file path
     *
     * @return Full path to coding_data.db
     */
    fun getDatabasePath(): Path {
        return getAppDataPath().resolve("coding_data.db")
    }
}