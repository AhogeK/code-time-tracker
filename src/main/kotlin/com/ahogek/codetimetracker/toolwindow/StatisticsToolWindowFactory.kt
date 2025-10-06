package com.ahogek.codetimetracker.toolwindow

import com.ahogek.codetimetracker.statistics.StatisticsView
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.content.ContentManagerEvent
import com.intellij.ui.content.ContentManagerListener

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

        toolWindow.addContentManagerListener(object : ContentManagerListener {
            private var firstTimeVisible = true

            override fun contentAdded(event: ContentManagerEvent) {
                if (toolWindow.isVisible && firstTimeVisible) {
                    statisticsView.loadAndRenderCharts()
                    firstTimeVisible = false
                }
            }
        })

        // Consideration should also be given to refreshing the data upon tool window activation
        toolWindow.show {
            // This is a more modern callback for display.
            // You can decide whether to refresh the data here
            // statisticsView.loadAndRenderCharts()
        }
    }
}