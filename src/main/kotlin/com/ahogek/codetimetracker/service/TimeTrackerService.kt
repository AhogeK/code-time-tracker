package com.ahogek.codetimetracker.service

import com.ahogek.codetimetracker.model.CodingSession
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

private const val IDLE_THRESHOLD_SECONDS = 120L
private const val IDLE_CHECK_INTERVAL_SECONDS = 5L

/**
 * Manage all timing logic
 *
 * @author AhogeK ahogek@gmail.com
 * @date 9/23/25
 */
@Service(Service.Level.APP)
class TimeTrackerService : Disposable {
    private val log = Logger.getInstance(TimeTrackerService::class.java)
    private val scheduler: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
    private val activeSessions: MutableMap<String, CodingSession> = ConcurrentHashMap()
    private val lastActivityTime: AtomicReference<LocalDateTime> = AtomicReference(LocalDateTime.now())

    init {
        // Start a unique, periodic task to check for idle state
        scheduler.scheduleAtFixedRate(
            ::checkIdleStatus,
            IDLE_CHECK_INTERVAL_SECONDS,
            IDLE_CHECK_INTERVAL_SECONDS,
            TimeUnit.SECONDS
        )
    }

    /**
     * Call this method when user activity is detected
     *
     * @param editor The currently active editor instance
     */
    fun onActivity(editor: Editor) {
        val file = FileDocumentManager.getInstance().getFile(editor.document) ?: return
        val currentLanguage = file.fileType.name
        val now = LocalDateTime.now()

        // Update last active time
        lastActivityTime.set(now)

        // Check if language is switched
        val activeLanguage = activeSessions.keys.firstOrNull()
        if (activeLanguage != null && activeLanguage != currentLanguage) {
            log.info("Language switch detected from $activeLanguage to $currentLanguage. Pausing previous session.")
            // Immediately pause and save the session of the previous language
            pauseAndPersistSessions()
        }

        // Start or update the current language session
        val session = activeSessions.computeIfAbsent(currentLanguage) {
            log.info("Starting new coding session for $currentLanguage")
            CodingSession(currentLanguage, now, now)
        }
        session.endTime = now
    }

    /**
     * Periodically called by the scheduler to check if the user is idle
     */
    private fun checkIdleStatus() {
        val now = LocalDateTime.now()
        val lastActivity = lastActivityTime.get()
        val idleSeconds = ChronoUnit.SECONDS.between(lastActivity, now)

        if (idleSeconds >= IDLE_THRESHOLD_SECONDS && activeSessions.isNotEmpty()) {
            log.info("User has been idle for over $IDLE_THRESHOLD_SECONDS seconds. Pausing tracking.")
            ApplicationManager.getApplication().invokeLater {
                pauseAndPersistSessions()
            }
        }
    }

    /**
     * Pause and persist all currently active sessions
     */
    private fun pauseAndPersistSessions() {
        if (activeSessions.isNotEmpty()) {
            val sessionsToStore = activeSessions.values.toList()
            log.info("Pausing tracking for: ${sessionsToStore.joinToString { it.language }}")
            activeSessions.clear()

            // TODO: Add data persistence logic here
            log.info("Persisted sessions: $sessionsToStore")
        }
    }

    fun stopTracking() {
        log.info("Stopping all tracking sessions immediately.")
        scheduler.shutdown()
        pauseAndPersistSessions()
    }

    override fun dispose() {
        stopTracking()
        log.info("TimeTrackerService disposed.")
    }
}