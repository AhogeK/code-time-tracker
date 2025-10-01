package com.ahogek.codetimetracker.widget

import com.ahogek.codetimetracker.database.DatabaseManager
import com.ahogek.codetimetracker.topics.TimeTrackerListener
import com.ahogek.codetimetracker.topics.TimeTrackerTopics
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import org.jetbrains.annotations.NonNls
import java.awt.Component
import java.time.Duration
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * @author AhogeK ahogek@gmail.com
 * @since 2025-10-01 03:46:29
 */
class CodeTimeTrackerWidget : StatusBarWidget, StatusBarWidget.TextPresentation {

    private val scheduler: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()

    // Use AtomicReference to ensure thread-safe updates to the duration
    private val totalDuration = AtomicReference(Duration.ZERO)

    private var statusBar: StatusBar? = null
    private var tickerTask: ScheduledFuture<*>? = null

    override fun ID(): @NonNls String = "CodeTimeTrackerWidget"
    override fun getPresentation(): StatusBarWidget.WidgetPresentation = this

    override fun install(statusBar: StatusBar) {
        this.statusBar = statusBar
        // Initial data fetch from database
        null.updateTotalTimeFromDatabase()

        // Subscribe to activity events
        val connection = ApplicationManager.getApplication().messageBus.connect(this)
        connection.subscribe(TimeTrackerTopics.ACTIVITY_TOPIC, object : TimeTrackerListener {
            override fun onActivityStarted() {
                startTicker()
            }

            override fun onActivityStopped() {
                stopTicker()
                // After stopping, refresh with the precise value from the DB
                val tickerStoppedValue = totalDuration.get()
                tickerStoppedValue.updateTotalTimeFromDatabase()
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
    }

    private fun Duration?.updateTotalTimeFromDatabase() {
        ApplicationManager.getApplication().executeOnPooledThread {
            val dbValue = DatabaseManager.getTotalCodingTime()

            // If a baseline is provided, ensure the new value is not less than it.
            val finalDuration = this?.let {
                if (dbValue > it) dbValue else it
            } ?: dbValue

            // Switch back to the UI thread to update the widget
            ApplicationManager.getApplication().invokeLater {
                totalDuration.set(finalDuration)
                statusBar?.updateWidget(ID())
            }
        }
    }

    override fun dispose() {
        stopTicker()
        scheduler.shutdown()
    }
}

