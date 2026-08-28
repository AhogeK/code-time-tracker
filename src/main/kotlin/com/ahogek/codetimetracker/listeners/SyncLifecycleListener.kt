package com.ahogek.codetimetracker.listeners

import com.ahogek.codetimetracker.service.sync.SyncScheduler
import com.intellij.ide.AppLifecycleListener
import com.intellij.openapi.application.ApplicationManager

/**
 * Wires the sync scheduler into the IDE lifecycle: starts the periodic task once the
 * application frame is created, and fires a best-effort sync flush before shutdown.
 * Pending dirty sessions are already persisted locally by the tracker, so an interrupted
 * flush only defers their upload to the next launch (the pull cursor and dirty markers
 * survive). Uses appFrameCreated (not appStarted, which is marked @ApiStatus.Internal).
 *
 * @author AhogeK ahogek@gmail.com
 * @since 2026-08-29
 */
class SyncLifecycleListener : AppLifecycleListener {

    override fun appFrameCreated(commandLineArgs: List<String>) {
        ApplicationManager.getApplication().getService(SyncScheduler::class.java).start()
    }

    override fun appWillBeClosed(isRestart: Boolean) {
        ApplicationManager.getApplication().getService(SyncScheduler::class.java).syncNow()
    }
}
