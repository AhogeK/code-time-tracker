package com.ahogek.codetimetracker.widget

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory
import org.jetbrains.annotations.NonNls

/**
 * @author AhogeK ahogek@gmail.com
 * @since 2025-10-01 21:47:53
 */
class CodeTimeTrackerWidgetFactory : StatusBarWidgetFactory {
    override fun getId(): @NonNls String = "CodeTimeTrackerWidget"

    override fun getDisplayName(): @NlsContexts.ConfigurableName String = "Code Time Tracker"

    override fun isAvailable(project: Project): Boolean = true

    override fun createWidget(project: Project): StatusBarWidget = CodeTimeTrackerWidget()

    override fun disposeWidget(widget: StatusBarWidget) {
        Disposer.dispose(widget)
    }

    override fun canBeEnabledOn(statusBar: com.intellij.openapi.wm.StatusBar): Boolean = true
}