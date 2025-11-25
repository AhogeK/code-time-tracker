package com.ahogek.codetimetracker.topics

import com.ahogek.codetimetracker.model.TimePeriod

/**
 * Listener interface for period reset events
 * Implement this interface to receive notifications when a time period resets
 *
 * @author AhogeK ahogek@gmail.com
 * @since 2025-11-26 02:36:10
 */
fun interface PeriodResetListener {

    /**
     * Called when a time period has been reset (e.g., day changed, week changed)
     *
     * @param period The period that was reset
     */
    fun onPeriodReset(period: TimePeriod)
}