package com.ahogek.codetimetracker.listeners

import com.ahogek.codetimetracker.service.TimeTrackerService
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManagerListener

/**
 * @author AhogeK ahogek@gmail.com
 * @since 2025-11-25 05:09:04
 */
class ProjectCloseListener : ProjectManagerListener {
    override fun projectClosing(project: Project) {
        val projectPath = project.basePath ?: return
        val timeTrackerService = ApplicationManager.getApplication().getService(TimeTrackerService::class.java)

        timeTrackerService.stopProjectTracking(projectPath)
    }
}