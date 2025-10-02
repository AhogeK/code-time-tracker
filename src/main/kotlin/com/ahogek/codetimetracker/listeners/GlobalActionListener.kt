package com.ahogek.codetimetracker.listeners

import com.ahogek.codetimetracker.service.TimeTrackerService
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.ex.AnActionListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager

/**
 * Global Action Listener.
 *
 * This listener, by implementing the [AnActionListener] interface, can intercept any "Action"
 * before or after its execution within the IntelliJ IDE.
 * Its core responsibility is to monitor a user's action intent (e.g., "Copy", "Save",
 * "Move Caret Up"), and to record the original input method that triggered the action
 * (e.g., a keyboard key press) as well as the context in which the action occurred
 * (e.g., the specific file).
 *
 * To enable it, you must register it in `plugin.xml`.
 *
 * @see AnActionListener
 */
class GlobalActionListener : AnActionListener {
    private val timeTrackerService = ApplicationManager.getApplication().getService(TimeTrackerService::class.java)

    override fun beforeActionPerformed(action: AnAction, event: AnActionEvent) {
        if (event.place == ActionPlaces.POPUP) {
            return
        }

        // Try to get the editor from the event context first.
        var editor = event.dataContext.getData(CommonDataKeys.EDITOR)
        val project = event.project

        // Fallback: If the direct context has no editor, get the selected editor from the project.
        // This is the crucial change that covers more click scenarios.
        if (editor == null && project != null && !project.isDisposed) {
            editor = FileEditorManager.getInstance(project).selectedTextEditor
        }

        if (editor != null) {
            val vFile = FileDocumentManager.getInstance().getFile(editor.document)
            if (vFile != null && vFile.isInLocalFileSystem && vFile.isWritable)
                timeTrackerService.onActivity(editor)
        }
    }
}