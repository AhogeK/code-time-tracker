package com.ahogek.codetimetracker.toolwindow

import com.ahogek.codetimetracker.statistics.StatisticsView
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory

/**
 * Factory class for creating the statistics tool window.
 * This class is registered as an extension point in plugin.xml.
 *
 * @author AhogeK ahogek@gmail.com
 * @since 2025-10-05 20:26:10
 */
class StatisticsToolWindowFactory : ToolWindowFactory, DumbAware {

    /**
     * This method is called by the IDE to create the content of the tool window.
     *
     * @param project The current project.
     * @param toolWindow The tool window instance.
     */
    override fun createToolWindowContent(
        project: Project,
        toolWindow: ToolWindow
    ) {
        val statisticsView = StatisticsView()
        val contentFactory = ContentFactory.getInstance()
        val content = contentFactory.createContent(statisticsView, "", false)
        toolWindow.contentManager.addContent(content)

        // This is a modern callback for when the tool window is displayed.
        // Calling loadAndRenderCharts() here ensures the data is refreshed
        // every time the user opens or focuses the tool window.
        toolWindow.show {
            statisticsView.loadAndRenderCharts()
        }
    }
}