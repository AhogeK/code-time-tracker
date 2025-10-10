package com.ahogek.codetimetracker.service

import com.ahogek.codetimetracker.database.DatabaseManager
import com.ahogek.codetimetracker.model.CodingSession
import com.ahogek.codetimetracker.topics.TimeTrackerTopics
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.util.SystemInfo
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

private const val IDLE_THRESHOLD_SECONDS = 60L
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
    private val activeSessions: MutableMap<String, MutableMap<String, CodingSession>> = ConcurrentHashMap()
    private val lastActivityTime: AtomicReference<LocalDateTime> = AtomicReference(LocalDateTime.now().minusHours(1))
    private val platform: String = "${SystemInfo.OS_NAME} | ${SystemInfo.OS_VERSION} | ${SystemInfo.OS_ARCH}"
    private val isUserActive = AtomicBoolean(false)

    fun getLastActivityTime(): LocalDateTime {
        return lastActivityTime.get()
    }

    init {
        log.info("TimeTrackerService initialized on platform: $platform")
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
        val project = editor.project ?: return
        // Use the project's unique base path as the key.
        val projectPath = project.basePath ?: return
        val projectName = project.name
        val file = FileDocumentManager.getInstance().getFile(editor.document) ?: return
        val currentLanguage = file.fileType.name
        val now = LocalDateTime.now()

        // Update the last activity timestamp.
        lastActivityTime.set(now)

        // If the user was previously inactive, fire the "started" event
        if (isUserActive.compareAndSet(false, true)) {
            log.info("User activity started.")
            ApplicationManager.getApplication().messageBus
                .syncPublisher(TimeTrackerTopics.ACTIVITY_TOPIC)
                .onActivityStarted()
        }

        // Get or create the session map for the current project.
        val projectSessions = activeSessions.computeIfAbsent(projectPath) {
            ConcurrentHashMap()
        }

        // Check for language switch within the same project.
        val activeLanguage = projectSessions.keys.firstOrNull()
        if (activeLanguage != null && activeLanguage != currentLanguage) {
            log.info("[$projectName] Language switch detected: $activeLanguage -> $currentLanguage.")
            pauseAndPersistSessions(now) // This will clear all sessions, which is correct for a switch.
        }

        // Start a new session or update the end time of the existing one.
        val session = projectSessions.computeIfAbsent(currentLanguage) {
            log.info("[$projectName] Starting new coding session for $currentLanguage.")
            CodingSession(projectName, currentLanguage, platform, now, now)
        }
        session.endTime = now
    }

    /**
     * Periodically called by the scheduler to check if the user is idle.
     */
    private fun checkIdleStatus() {
        val now = LocalDateTime.now()
        val lastActivity = lastActivityTime.get()
        val idleSeconds = ChronoUnit.SECONDS.between(lastActivity, now)

        // If idle threshold is met and there are any active sessions in any project.
        if (idleSeconds >= IDLE_THRESHOLD_SECONDS && activeSessions.any { it.value.isNotEmpty() }) {
            log.info("User has been idle for over $IDLE_THRESHOLD_SECONDS seconds. Pausing all tracking sessions.")
            val sessionEndTime = lastActivity.plusSeconds(IDLE_THRESHOLD_SECONDS)
            ApplicationManager.getApplication().invokeLater {
                val onSaveCompleteCallback = {
                    if (isUserActive.compareAndSet(true, false)) {
                        ApplicationManager.getApplication().messageBus
                            .syncPublisher(TimeTrackerTopics.ACTIVITY_TOPIC)
                            .onActivityStopped()
                    }
                }
                pauseAndPersistSessions(sessionEndTime, onSaveCompleteCallback)
            }
        }
    }

    /**
     * Public method to force the persistence of all currently active sessions.
     * This can be called when the user manually triggers an action that should
     * result in a save, such as changing a tracking setting.
     */
    fun forcePersistSessions() {
        log.info("Forcing persistence of all active sessions.")
        val now = LocalDateTime.now()

        ApplicationManager.getApplication().invokeLater {
            // Define the callback that will run after sessions are saved.
            // This callback is crucial for resetting the user's active state.
            val onSaveCompleteCallback = {
                // If the user was considered active, set them to inactive
                // and notify listeners that activity has stopped.
                if (isUserActive.compareAndSet(true, false)) {
                    ApplicationManager.getApplication().messageBus
                        .syncPublisher(TimeTrackerTopics.ACTIVITY_TOPIC)
                        .onActivityStopped()
                }
                log.info("Forced persistence completed.")
            }

            // Call pauseAndPersistSessions with the state-resetting callback.
            pauseAndPersistSessions(now, onSaveCompleteCallback)
        }
    }

    /**
     * Pause and persist all currently active sessions
     */
    private fun pauseAndPersistSessions(finalEndTime: LocalDateTime, onSaveComplete: () -> Unit = {}) {
        if (activeSessions.isNotEmpty()) {
            val sessionsToStore = mutableListOf<CodingSession>()

            activeSessions.values.forEach { projectSessions ->
                projectSessions.values.forEach { session ->
                    session.endTime = finalEndTime
                    sessionsToStore.add(session)
                }
            }

            if (sessionsToStore.isEmpty()) {
                onSaveComplete()
                return
            }

            log.info("Pausing tracking for: ${sessionsToStore.joinToString { "[${it.projectName}] ${it.language}" }}")
            // Clear all active sessions.
            activeSessions.clear()

            DatabaseManager.saveSessions(sessionsToStore, onSaveComplete)
            log.info("Persisted sessions task submitted for: $sessionsToStore")
        } else {
            onSaveComplete()
        }
    }

    /**
     * Immediately stops all tracking activities and shuts down the scheduler.
     * Usually called when the application is closing.
     */
    fun stopTracking() {
        log.info("Stopping all tracking sessions immediately.")
        // Prevent any further scheduled tasks from running.
        if (!scheduler.isShutdown) {
            scheduler.shutdown()
        }
        pauseAndPersistSessions(LocalDateTime.now())
    }

    override fun dispose() {
        // First, stop the tracker, which submits the final data to be saved.
        stopTracking()

        // Then, explicitly tell the DatabaseManager to shut down and wait for
        //      all pending tasks (including the final one) to complete.
        DatabaseManager.shutdown()
        log.info("TimeTrackerService disposed.")
    }
}