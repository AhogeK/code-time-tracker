package com.ahogek.codetimetracker.activity

import com.ahogek.codetimetracker.database.DatabaseManager
import com.ahogek.codetimetracker.service.GlobalEventMonitorService
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

/**
 * A stable, public API entry point that runs when the project opens.
 * Its sole responsibility is to call the initialization method of our application-level services.
 */
class StartupTriggerActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        // Initialize the database manager to ensure the DB file and tables are ready.
        DatabaseManager.initialize()

        // Initialize the event listeners.
        val service = ApplicationManager.getApplication()
            .getService(GlobalEventMonitorService::class.java)
        service.initializeListeners()
    }
}