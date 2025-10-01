package com.ahogek.codetimetracker.widget

import com.ahogek.codetimetracker.database.DatabaseManager
import com.ahogek.codetimetracker.service.TimeTrackerService
import com.ahogek.codetimetracker.topics.TimeTrackerListener
import com.ahogek.codetimetracker.topics.TimeTrackerTopics
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import org.jetbrains.annotations.NonNls
import java.awt.Component
import java.time.Duration
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

private const val CACHED_TOTAL_SECONDS_KEY = "com.ahogek.codetimetracker.cachedTotalSeconds"

private fun readCachedDuration(): Duration {
    val secondsStr = PropertiesComponent.getInstance().getValue(CACHED_TOTAL_SECONDS_KEY, "0")
    val seconds = secondsStr.toLongOrNull() ?: 0L
    return Duration.ofSeconds(seconds)
}

/**
 * @author AhogeK ahogek@gmail.com
 * @since 2025-10-01 03:46:29
 */
class CodeTimeTrackerWidget : StatusBarWidget, StatusBarWidget.TextPresentation {

    private val scheduler: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()

    // Use AtomicReference to ensure thread-safe updates to the duration
    private val totalDuration = AtomicReference(readCachedDuration())
    private val timeTrackerService = ApplicationManager.getApplication().getService(TimeTrackerService::class.java)
    private val isInitialized = AtomicBoolean(false)

    private var statusBar: StatusBar? = null
    private var tickerTask: ScheduledFuture<*>? = null

    override fun ID(): @NonNls String = "CodeTimeTrackerWidget"
    override fun getPresentation(): StatusBarWidget.WidgetPresentation = this

    override fun install(statusBar: StatusBar) {
        this.statusBar = statusBar
        isInitialized.set(false)
        val connection = ApplicationManager.getApplication().messageBus.connect(this)

        updateTotalTimeFromDatabase {
            isInitialized.set(true)

            val lastActivity = timeTrackerService.getLastActivityTime()
            val secondsSinceLastActivity = ChronoUnit.SECONDS.between(lastActivity, LocalDateTime.now())
            if (secondsSinceLastActivity < 5L) {
                startTicker()
            }
        }

        connection.subscribe(TimeTrackerTopics.ACTIVITY_TOPIC, object : TimeTrackerListener {
            override fun onActivityStarted() {
                if (isInitialized.get()) {
                    startTicker()
                }
            }

            override fun onActivityStopped() {
                stopTicker()
                updateTotalTimeFromDatabase()
            }
        })
    }

    override fun getText(): @NlsContexts.Label String {
        val duration = totalDuration.get()
        val hours = duration.toHours()
        val minutes = duration.toMinutesPart()
        val seconds = duration.toSecondsPart()
        return String.format("Code Time: %d:%02d:%02d", hours, minutes, seconds)
    }

    override fun getAlignment(): Float {
        return Component.CENTER_ALIGNMENT
    }

    override fun getTooltipText(): @NlsContexts.Tooltip String = "Total code time tracked"

    private fun startTicker() {
        // Ensure only one ticker runs at a time
        if (tickerTask == null || tickerTask!!.isDone) {
            tickerTask = scheduler.scheduleAtFixedRate({
                // Atomically add one second to the current duration
                totalDuration.updateAndGet { it.plusSeconds(1) }
                statusBar?.updateWidget(ID())
            }, 1, 1, TimeUnit.SECONDS)
        }
    }

    private fun stopTicker() {
        tickerTask?.cancel(false)
        tickerTask = null
    }

    private fun updateTotalTimeFromDatabase(onComplete: (() -> Unit)? = null) {
        ApplicationManager.getApplication().executeOnPooledThread {
            val dbValue = DatabaseManager.getTotalCodingTime()
            val currentWidgetValue = totalDuration.get()

            val finalDuration = if (dbValue > currentWidgetValue) dbValue else currentWidgetValue

            ApplicationManager.getApplication().invokeLater {
                totalDuration.set(finalDuration)
                PropertiesComponent.getInstance()
                    .setValue(CACHED_TOTAL_SECONDS_KEY, finalDuration.toSeconds().toString())
                statusBar?.updateWidget(ID())
                onComplete?.invoke()
            }
        }
    }

    override fun dispose() {
        stopTicker()
        val currentSeconds = totalDuration.get().toSeconds()
        PropertiesComponent.getInstance().setValue(CACHED_TOTAL_SECONDS_KEY, currentSeconds.toString())

        if (!scheduler.isShutdown) {
            scheduler.shutdown()
        }
    }
}

