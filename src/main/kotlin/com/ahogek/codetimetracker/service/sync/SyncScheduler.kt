package com.ahogek.codetimetracker.service.sync

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * Owns the periodic and event-driven sync triggers.
 *
 * <p>A single background executor runs one periodic task with fixed delay between rounds
 * (interval configured via [SyncSettingsState.syncIntervalMinutes]; 0 disables the timer).
 * [syncNow] is the unified entry point for manual and event triggers (project switch,
 * lifecycle flush). [SyncCoordinator] itself guards against overlapping rounds: triggers
 * that fire while a round is running are no-ops, and queued triggers still run a round
 * each (idempotent, so wasted work is only the round itself).
 *
 * @author AhogeK ahogek@gmail.com
 * @since 2026-08-29
 */
@Service(Service.Level.APP)
class SyncScheduler(
    private val settings: SyncSettingsState,
    private val coordinator: SyncCoordinator,
) : Disposable {

    /**
     * Platform-container entry point: the service container only supports parameterless
     * constructors, so dependencies are resolved via [ApplicationManager].
     */
    constructor() : this(
        ApplicationManager.getApplication().getService(SyncSettingsState::class.java),
        ApplicationManager.getApplication().getService(SyncCoordinator::class.java),
    )

    companion object {
        private val log = Logger.getInstance(SyncScheduler::class.java)
    }

    private val executor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "code-time-tracker-sync").apply { isDaemon = true }
    }

    @Volatile
    private var periodicTask: ScheduledFuture<*>? = null

    /** Starts (or restarts) the periodic sync task using the current configured interval. */
    fun start() {
        reschedule()
    }

    /**
     * Re-arms the periodic task from [SyncSettingsState.syncIntervalMinutes]. Safe to call
     * when the interval or the sync enable flag changes; a non-positive interval stops the
     * timer entirely.
     */
    fun reschedule() {
        periodicTask?.cancel(false)
        periodicTask = null
        if (!settings.syncEnabled) {
            return
        }
        val minutes = settings.syncIntervalMinutes
        if (minutes <= 0) {
            log.info("Periodic sync disabled (interval=$minutes)")
            return
        }
        periodicTask = executor.scheduleWithFixedDelay(
            { runSafely("periodic") },
            minutes.toLong(),
            minutes.toLong(),
            TimeUnit.MINUTES,
        )
        log.info("Periodic sync scheduled every $minutes minute(s)")
    }

    /**
     * Triggers an immediate sync round on the background executor. Safe to call from any
     * thread (EDT, project events, lifecycle); overlapping triggers are no-ops because
     * [SyncCoordinator] only allows one round at a time.
     */
    fun syncNow() {
        // Safety net: any trigger (manual, project event) also arms the periodic task if
        // the lifecycle start did not run (e.g. listener registration hiccup) or the
        // enable flag changed after startup. Reschedule is idempotent.
        if (periodicTask == null) {
            reschedule()
        }
        try {
            executor.execute { runSafely("trigger") }
        } catch (e: java.util.concurrent.RejectedExecutionException) {
            // Executor already shut down (plugin being disposed); nothing to schedule.
            log.warn("Sync trigger dropped: executor is shut down", e)
        }
    }

    /** Test seam: whether a periodic task is currently armed. */
    internal fun isPeriodicActive(): Boolean = periodicTask != null

    private fun runSafely(source: String) {
        try {
            val result = coordinator.syncOnce()
            if (result is SyncResult.Failure) {
                log.warn("Sync ($source) failed: ${result.error.toUserMessage()}")
            }
        } catch (t: Throwable) {
            log.error("Sync ($source) crashed", t)
        }
    }

    override fun dispose() {
        periodicTask?.cancel(false)
        periodicTask = null
        executor.shutdown()
    }
}
