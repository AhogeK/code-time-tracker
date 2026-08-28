package com.ahogek.codetimetracker.listeners

import com.ahogek.codetimetracker.service.TimeTrackerService
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectCloseListener

/**
 * Stops per-project tracking when a project closes, using the platform's current
 * project-close event API (replaces the deprecated ProjectManagerListener path).
 *
 * @author AhogeK ahogek@gmail.com
 * @since 2025-11-25
 */
class ProjectCloseTrackingListener : ProjectCloseListener {

    override fun projectClosing(project: Project) {
        val projectPath = project.basePath ?: return
        val timeTrackerService = ApplicationManager.getApplication().getService(TimeTrackerService::class.java)
        timeTrackerService.stopProjectTracking(projectPath)
    }
}
