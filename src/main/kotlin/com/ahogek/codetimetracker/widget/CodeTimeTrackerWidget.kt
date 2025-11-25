package com.ahogek.codetimetracker.widget

import com.ahogek.codetimetracker.database.DatabaseManager
import com.ahogek.codetimetracker.listeners.PeriodResetListener
import com.ahogek.codetimetracker.listeners.TimeTrackerListener
import com.ahogek.codetimetracker.model.TimePeriod
import com.ahogek.codetimetracker.service.TimeTrackerService
import com.ahogek.codetimetracker.topics.TimeTrackerTopics
import com.intellij.icons.AllIcons
import com.intellij.ide.DataManager
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.JBPopupListener
import com.intellij.openapi.ui.popup.LightweightWindowEvent
import com.intellij.openapi.wm.CustomStatusBarWidget
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.impl.status.TextPanel
import com.intellij.ui.awt.RelativePoint
import com.intellij.util.ui.EmptyIcon
import org.jetbrains.annotations.NonNls
import java.awt.Point
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import java.time.temporal.WeekFields
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import javax.swing.JComponent
import javax.swing.SwingUtilities

private const val CACHED_TOTAL_SECONDS_KEY = "com.ahogek.codetimetracker.cachedTotalSeconds"
private const val SELECTED_PERIOD_KEY = "com.ahogek.codetimetracker.selectedPeriod"
private const val TRACK_CURRENT_PROJECT_ONLY_KEY = "com.ahogek.codetimetracker.trackCurrentProjectOnly"

private fun readCachedDuration(): Duration {
    val secondsStr = PropertiesComponent.getInstance().getValue(CACHED_TOTAL_SECONDS_KEY, "0")
    val seconds = secondsStr.toLongOrNull() ?: 0L
    return Duration.ofSeconds(seconds)
}

/**
 * @author AhogeK ahogek@gmail.com
 * @since 2025-10-01 03:46:29
 */
class CodeTimeTrackerWidget(private val project: Project) : StatusBarWidget, CustomStatusBarWidget {

    /**
     * Internal enum for widget display, includes TOTAL which is not a time period
     */
    private enum class DisplayPeriod(val displayName: String) {
        TOTAL("Total"),
        TODAY("Today"),
        THIS_WEEK("This Week"),
        THIS_MONTH("This Month"),
        THIS_YEAR("This Year");

        companion object {
            fun fromString(name: String?): DisplayPeriod = entries.find { it.name == name } ?: TOTAL
        }

        /**
         * Convert DisplayPeriod to model TimePeriod (excludes TOTAL)
         */
        fun toModelPeriod(): TimePeriod? = when (this) {
            TODAY -> TimePeriod.TODAY
            THIS_WEEK -> TimePeriod.THIS_WEEK
            THIS_MONTH -> TimePeriod.THIS_MONTH
            THIS_YEAR -> TimePeriod.THIS_YEAR
            TOTAL -> null  // TOTAL is not a time period with reset boundary
        }
    }

    private val scheduler: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
    private val timeTrackerService = ApplicationManager.getApplication().getService(TimeTrackerService::class.java)
    private val displayDuration = AtomicReference<Duration?>(null)

    private var tickerTask: ScheduledFuture<*>? = null
    private var statusBar: StatusBar? = null
    private var selectedPeriod: DisplayPeriod =
        DisplayPeriod.fromString(PropertiesComponent.getInstance().getValue(SELECTED_PERIOD_KEY))
    private var trackCurrentProjectOnly: Boolean =
        PropertiesComponent.getInstance().getBoolean(TRACK_CURRENT_PROJECT_ONLY_KEY, false)

    private val label = TextPanel()

    override fun ID(): @NonNls String = "CodeTimeTrackerWidget"

    override fun getComponent(): JComponent = label

    companion object {
        // A global "switch" to indicate if our widget's popup is currently active.
        // @Volatile ensures that changes are immediately visible to all threads.
        @Volatile
        var isPopupActive = false
    }

    override fun install(statusBar: StatusBar) {
        this.statusBar = statusBar
        label.isFocusable = false
        label.putClientProperty("code.time.tracker.widget.invoker", true)
        label.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (SwingUtilities.isLeftMouseButton(e)) {
                    // Flip the switch ON at the earliest possible moment.
                    isPopupActive = true
                    val popup = JBPopupFactory.getInstance().createActionGroupPopup(
                        "Code Time Tracker",
                        createActionGroup(),
                        DataManager.getInstance().getDataContext(label),
                        JBPopupFactory.ActionSelectionAid.MNEMONICS,
                        false
                    )
                    // Add a listener to flip the switch OFF when the popup closes.
                    popup.addListener(object : JBPopupListener {
                        override fun onClosed(event: LightweightWindowEvent) {
                            isPopupActive = false
                        }
                    })
                    val dimension = popup.content.preferredSize
                    val at = Point(
                        label.width - dimension.width, -dimension.height
                    )
                    popup.show(RelativePoint(label, at))
                }
            }
        })
        label.toolTipText = "Click to change tracked time period"

        displayDuration.set(if (selectedPeriod == DisplayPeriod.TOTAL) readCachedDuration() else Duration.ZERO)
        updateTimeFromDatabase()

        val connection = ApplicationManager.getApplication().messageBus.connect(this)
        connection.subscribe(TimeTrackerTopics.ACTIVITY_TOPIC, object : TimeTrackerListener {
            override fun onActivityStarted() = startTicker()
            override fun onActivityStopped() {
                stopTicker()
                updateTimeFromDatabase()
            }
        })

        // Subscribe to period reset events for automatic UI refresh
        connection.subscribe(TimeTrackerTopics.PERIOD_RESET_TOPIC, PeriodResetListener { period ->
            // Check if the reset period matches current selected period
            val currentModelPeriod = selectedPeriod.toModelPeriod()
            if (currentModelPeriod == period) {
                // Period boundary crossed, refresh from database
                updateTimeFromDatabase()
            }
        })
    }

    private fun createActionGroup(): ActionGroup {
        val group = DefaultActionGroup()

        group.add(object : AnAction("Current Project Only"), DumbAware {
            override fun actionPerformed(e: AnActionEvent) {
                trackCurrentProjectOnly = !trackCurrentProjectOnly
                PropertiesComponent.getInstance().setValue(TRACK_CURRENT_PROJECT_ONLY_KEY, trackCurrentProjectOnly)
                stopTicker()
                timeTrackerService.forcePersistSessions()
                updateTimeFromDatabase()
            }

            override fun update(e: AnActionEvent) {
                val presentation = e.presentation
                presentation.icon =
                    if (trackCurrentProjectOnly) AllIcons.Actions.Checked else EmptyIcon.create(AllIcons.Actions.Checked)
            }

            override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
        })

        group.add(Separator.getInstance())

        DisplayPeriod.entries.forEach { period ->
            group.add(object : AnAction(period.displayName), DumbAware {
                override fun actionPerformed(e: AnActionEvent) {
                    if (selectedPeriod != period) {
                        selectedPeriod = period
                        PropertiesComponent.getInstance().setValue(SELECTED_PERIOD_KEY, period.name)
                        stopTicker()
                        timeTrackerService.forcePersistSessions()
                        updateTimeFromDatabase()
                    }
                }

                override fun update(e: AnActionEvent) {
                    val presentation = e.presentation
                    presentation.icon =
                        if (selectedPeriod == period) AllIcons.Actions.Checked else EmptyIcon.create(AllIcons.Actions.Checked)
                }

                override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
            })
        }

        return group
    }


    private fun updateLabelText() {
        val currentDuration = displayDuration.get() ?: Duration.ZERO
        val prefix = selectedPeriod.displayName

        val hours = currentDuration.toHours()
        val minutes = currentDuration.toMinutesPart()
        val seconds = currentDuration.toSecondsPart()
        label.text = String.format("%s: %02d:%02d:%02d", prefix, hours, minutes, seconds)
    }

    private fun startTicker() {
        if (tickerTask == null || tickerTask!!.isDone) {
            tickerTask = scheduler.scheduleAtFixedRate({
                val lastActivity = timeTrackerService.getLastActivityTime()
                val idleSeconds = ChronoUnit.SECONDS.between(lastActivity, LocalDateTime.now())
                if (idleSeconds <= TimeTrackerService.IDLE_THRESHOLD_SECONDS) {
                    displayDuration.getAndUpdate { it?.plusSeconds(1) }
                    ApplicationManager.getApplication().invokeLater { updateLabelText() }
                }
            }, 1, 1, TimeUnit.SECONDS)
        }
    }

    private fun stopTicker() {
        tickerTask?.cancel(false)
        tickerTask = null
    }

    /**
     * Update the displayed time from database or service
     * Queries appropriate data source based on selected period
     */
    private fun updateTimeFromDatabase() {
        // Immediately update UI with the current state (which might be 00:00:00).
        updateLabelText()

        ApplicationManager.getApplication().executeOnPooledThread {
            val projectName = if (trackCurrentProjectOnly) project.name else null
            val dbValue = calculateDurationForPeriod(projectName)

            updateDisplayDuration(dbValue)
        }
    }

    /**
     * Calculate duration for the currently selected period
     *
     * @param projectName Optional project name filter (null for all projects)
     * @return Duration representing the accumulated time
     */
    private fun calculateDurationForPeriod(projectName: String?): Duration {
        return when (selectedPeriod) {
            DisplayPeriod.TOTAL -> DatabaseManager.getTotalCodingTime(projectName)
            DisplayPeriod.TODAY -> getTimeForPeriod(TimePeriod.TODAY) { startOfToday ->
                DatabaseManager.getCodingTimeForPeriod(startOfToday, startOfToday.plusDays(1), projectName)
            }

            DisplayPeriod.THIS_WEEK -> getTimeForPeriod(TimePeriod.THIS_WEEK) { startOfToday ->
                val weekFields = WeekFields.of(Locale.getDefault())
                val startOfWeek = startOfToday.with(weekFields.dayOfWeek(), 1L)
                DatabaseManager.getCodingTimeForPeriod(startOfWeek, startOfWeek.plusWeeks(1), projectName)
            }

            DisplayPeriod.THIS_MONTH -> getTimeForPeriod(TimePeriod.THIS_MONTH) { startOfToday ->
                val startOfMonth = startOfToday.withDayOfMonth(1)
                DatabaseManager.getCodingTimeForPeriod(startOfMonth, startOfMonth.plusMonths(1), projectName)
            }

            DisplayPeriod.THIS_YEAR -> getTimeForPeriod(TimePeriod.THIS_YEAR) { startOfToday ->
                val startOfYear = startOfToday.withDayOfYear(1)
                DatabaseManager.getCodingTimeForPeriod(startOfYear, startOfYear.plusYears(1), projectName)
            }
        }
    }

    /**
     * Get time for a specific period, preferring service UI time over database query
     *
     * @param modelPeriod The time period from the model
     * @param dbQueryFallback Lambda to query database if service time is not available
     * @return Duration representing the accumulated time
     */
    private fun getTimeForPeriod(
        modelPeriod: TimePeriod,
        dbQueryFallback: (LocalDateTime) -> Duration
    ): Duration {
        // First try to get time from service (real-time accumulation)
        val serviceTime = timeTrackerService.getUIDisplayTime(modelPeriod)
        if (serviceTime > 0) {
            return Duration.ofMillis(serviceTime)
        }

        // Fallback to database query
        val now = LocalDateTime.now()
        val startOfToday = now.with(LocalTime.MIN)
        return dbQueryFallback(startOfToday)
    }

    /**
     * Update the display duration and refresh UI on EDT
     *
     * @param duration The new duration to display
     */
    private fun updateDisplayDuration(duration: Duration) {
        ApplicationManager.getApplication().invokeLater {
            displayDuration.set(duration)

            // Cache total time if displaying total for all projects
            if (selectedPeriod == DisplayPeriod.TOTAL && !trackCurrentProjectOnly) {
                PropertiesComponent.getInstance()
                    .setValue(CACHED_TOTAL_SECONDS_KEY, duration.toSeconds().toString())
            }

            updateLabelText()
        }
    }

    override fun dispose() {
        stopTicker()
        // Persist the current "Total" time to cache when closing.
        if (selectedPeriod == DisplayPeriod.TOTAL && !trackCurrentProjectOnly) {
            displayDuration.get()?.let {
                PropertiesComponent.getInstance().setValue(CACHED_TOTAL_SECONDS_KEY, it.toSeconds().toString())
            }
        }
        if (!scheduler.isShutdown) {
            scheduler.shutdown()
        }
    }
}

