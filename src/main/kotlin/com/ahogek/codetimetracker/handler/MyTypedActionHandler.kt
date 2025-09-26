package com.ahogek.codetimetracker.handler

import com.ahogek.codetimetracker.service.TimeTrackerService
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actionSystem.TypedActionHandler
import com.intellij.openapi.fileEditor.FileDocumentManager

class MyTypedActionHandler(
    private val originalHandler: TypedActionHandler,
    private val timeTrackerService: TimeTrackerService
) : TypedActionHandler {
    override fun execute(editor: Editor, charTyped: Char, dataContext: DataContext) {
        try {
            val vFile = FileDocumentManager.getInstance().getFile(editor.document)
            if (vFile != null && vFile.isInLocalFileSystem && vFile.isWritable) {
                timeTrackerService.onActivity(editor)
            }
        } finally {
            // 必须调用原始handler，确保用户的输入能正常显示在编辑器中
            originalHandler.execute(editor, charTyped, dataContext)
        }
    }
}