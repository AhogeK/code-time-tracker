package com.ahogek.codetimetracker.widget

import com.ahogek.codetimetracker.database.DatabaseManager
import com.ahogek.codetimetracker.service.TimeTrackerService
import com.ahogek.codetimetracker.topics.TimeTrackerListener
import com.ahogek.codetimetracker.topics.TimeTrackerTopics
import com.intellij.icons.AllIcons
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.util.Consumer
import com.intellij.util.ui.EmptyIcon
import org.jetbrains.annotations.NonNls
import java.awt.Component
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
import javax.swing.*

private const val CACHED_TOTAL_SECONDS_KEY = "com.ahogek.codetimetracker.cachedTotalSeconds"
private const val SELECTED_PERIOD_KEY = "com.ahogek.codetimetracker.selectedPeriod"

private fun readCachedDuration(): Duration {
    val secondsStr = PropertiesComponent.getInstance().getValue(CACHED_TOTAL_SECONDS_KEY, "0")
    val seconds = secondsStr.toLongOrNull() ?: 0L
    return Duration.ofSeconds(seconds)
}

/**
 * @author AhogeK ahogek@gmail.com
 * @since 2025-10-01 03:46:29
 */
class CodeTimeTrackerWidget : StatusBarWidget, StatusBarWidget.MultipleTextValuesPresentation, Consumer<MouseEvent> {

    private enum class TimePeriod(val displayName: String) {
        TOTAL("Total"),
        TODAY("Today"),
        THIS_WEEK("This Week"),
        THIS_MONTH("This Month"),
        THIS_YEAR("This Year");

        companion object {
            fun fromString(name: String?): TimePeriod = entries.find { it.name == name } ?: TOTAL
        }
    }

    private val scheduler: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
    private val timeTrackerService = ApplicationManager.getApplication().getService(TimeTrackerService::class.java)

    // A null value in this AtomicReference indicates that the data is currently being fetched.
    private val displayDuration = AtomicReference<Duration?>(null)
    private var tickerTask: ScheduledFuture<*>? = null
    private var statusBar: StatusBar? = null

    private var selectedPeriod: TimePeriod =
        TimePeriod.fromString(PropertiesComponent.getInstance().getValue(SELECTED_PERIOD_KEY))

    override fun ID(): @NonNls String = "CodeTimeTrackerWidget"

    override fun getPresentation(): StatusBarWidget.WidgetPresentation = this

    override fun install(statusBar: StatusBar) {
        this.statusBar = statusBar

        // Smart Initialization:
        // Instantly show the cached value for "Total" to avoid any perceived loading.
        // For other periods, start with a default of ZERO.
        displayDuration.set(if (selectedPeriod == TimePeriod.TOTAL) readCachedDuration() else Duration.ZERO)

        // Always trigger a fresh database fetch on installation to ensure data is up-to-date.
        updateTimeFromDatabase()

        // Subscribe to activity events to start/stop the live ticker.
        val connection = ApplicationManager.getApplication().messageBus.connect(this)
        connection.subscribe(TimeTrackerTopics.ACTIVITY_TOPIC, object : TimeTrackerListener {
            override fun onActivityStarted() = startTicker()
            override fun onActivityStopped() {
                stopTicker()
                updateTimeFromDatabase()
            }
        })

        // Check if user was already active on startup.
        val lastActivity = timeTrackerService.getLastActivityTime()
        if (ChronoUnit.SECONDS.between(lastActivity, LocalDateTime.now()) < 5L) {
            startTicker()
        }
    }

    override fun getPopup() = JBPopupFactory.getInstance()
        .createPopupChooserBuilder(TimePeriod.entries.map { it.displayName })
        .setTitle("Select Time Period")
        .setRenderer(object : DefaultListCellRenderer() {
            private val checkIcon: Icon = AllIcons.Actions.Checked
            private val spacerIcon: Icon = EmptyIcon.create(checkIcon)

            override fun getListCellRendererComponent(
                list: JList<*>?,
                value: Any?,
                index: Int,
                isSelected: Boolean,
                cellHasFocus: Boolean
            ): Component {
                val component =
                    super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus) as JLabel
                component.border = BorderFactory.createEmptyBorder(5, 12, 5, 12)

                if (value == selectedPeriod.displayName) {
                    component.icon = checkIcon
                } else {
                    component.icon = spacerIcon
                }

                component.iconTextGap = 10
                return component
            }
        })
        .setItemChosenCallback { selectedValue ->
            val newPeriod = TimePeriod.entries.find { it.displayName == selectedValue } ?: return@setItemChosenCallback
            if (selectedPeriod != newPeriod) {
                selectedPeriod = newPeriod
                PropertiesComponent.getInstance().setValue(SELECTED_PERIOD_KEY, newPeriod.name)
                // Set to a default value and immediately request new data from DB.
                displayDuration.set(Duration.ZERO)
                updateTimeFromDatabase()
            }
        }
        .createPopup()

    override fun getSelectedValue(): @NlsContexts.StatusBarText String {
        // If the duration is null (which happens briefly during state transitions) or
        // if the database returns no data, we default to Duration.ZERO.
        // This ensures the UI always shows "00:00:00" instead of an error or loading text.
        val currentDuration = displayDuration.get() ?: Duration.ZERO
        val prefix = selectedPeriod.displayName

        val hours = currentDuration.toHours()
        val minutes = currentDuration.toMinutesPart()
        val seconds = currentDuration.toSecondsPart()
        return String.format("%s: %02d:%02d:%02d", prefix, hours, minutes, seconds)
    }

    override fun getTooltipText(): @NlsContexts.Tooltip String = "Click to change tracked time period"

    override fun getClickConsumer(): Consumer<MouseEvent> = this

    private fun startTicker() {
        if (tickerTask == null || tickerTask!!.isDone) {
            tickerTask = scheduler.scheduleAtFixedRate({
                // The ticker only increments the duration if it's in a valid (non-loading) state.
                displayDuration.getAndUpdate { it?.plusSeconds(1) }
                statusBar?.updateWidget(ID())
            }, 1, 1, TimeUnit.SECONDS)
        }
    }

    private fun stopTicker() {
        tickerTask?.cancel(false)
        tickerTask = null
    }

    private fun updateTimeFromDatabase() {
        // Immediately update UI with the current state (which might be 00:00:00).
        statusBar?.updateWidget(ID())

        ApplicationManager.getApplication().executeOnPooledThread {
            val now = LocalDateTime.now()
            val startOfToday = now.with(LocalTime.MIN)

            val dbValue = when (selectedPeriod) {
                TimePeriod.TOTAL -> DatabaseManager.getTotalCodingTime()
                TimePeriod.TODAY -> DatabaseManager.getCodingTimeForPeriod(startOfToday, startOfToday.plusDays(1))
                TimePeriod.THIS_WEEK -> {
                    val weekFields = WeekFields.of(Locale.getDefault())
                    val startOfWeek = startOfToday.with(weekFields.dayOfWeek(), 1L)
                    DatabaseManager.getCodingTimeForPeriod(startOfWeek, startOfWeek.plusWeeks(1))
                }

                TimePeriod.THIS_MONTH -> {
                    val startOfMonth = startOfToday.withDayOfMonth(1)
                    DatabaseManager.getCodingTimeForPeriod(startOfMonth, startOfMonth.plusMonths(1))
                }

                TimePeriod.THIS_YEAR -> {
                    val startOfYear = startOfToday.withDayOfYear(1)
                    DatabaseManager.getCodingTimeForPeriod(startOfYear, startOfYear.plusYears(1))
                }
            }

            ApplicationManager.getApplication().invokeLater {
                displayDuration.set(dbValue)
                // If we've just fetched the total, update the cache.
                if (selectedPeriod == TimePeriod.TOTAL) {
                    PropertiesComponent.getInstance().setValue(CACHED_TOTAL_SECONDS_KEY, dbValue.toSeconds().toString())
                }
                statusBar?.updateWidget(ID())
            }
        }
    }

    override fun dispose() {
        stopTicker()
        // Persist the current "Total" time to cache when closing.
        if (selectedPeriod == TimePeriod.TOTAL) {
            displayDuration.get()?.let {
                PropertiesComponent.getInstance().setValue(CACHED_TOTAL_SECONDS_KEY, it.toSeconds().toString())
            }
        }
        if (!scheduler.isShutdown) {
            scheduler.shutdown()
        }
    }

    // This method is required by the interface, even if empty.
    override fun consume(t: MouseEvent?) {}
}

