package com.ahogek.codetimetracker.toolwindow

import com.ahogek.codetimetracker.statistics.StatisticsView
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import com.intellij.ui.content.ContentFactory

/**
 * Factory class for creating the statistics tool window.
 * This class is registered as an extension point in plugin.xml.
 *
 * @author AhogeK ahogek@gmail.com
 * @since 2025-10-05 20:26:10
 */
class StatisticsToolWindowFactory : ToolWindowFactory, DumbAware {

    companion object {
        const val TOOL_WINDOW_ID = "Code Statistics"
    }

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

        project.messageBus.connect(toolWindow.disposable).subscribe(
            ToolWindowManagerListener.TOPIC,
            object : ToolWindowManagerListener {
                override fun stateChanged(toolWindowManager: com.intellij.openapi.wm.ToolWindowManager) {
                    val currentToolWindow = toolWindowManager.getToolWindow(TOOL_WINDOW_ID)
                    if (currentToolWindow != null && currentToolWindow.isVisible) {
                        val content = currentToolWindow.contentManager.getContent(0)
                        if (content?.component is StatisticsView) {
                            (content.component as StatisticsView).loadAndRenderCharts()
                        }
                    }
                }
            }
        )
    }
}