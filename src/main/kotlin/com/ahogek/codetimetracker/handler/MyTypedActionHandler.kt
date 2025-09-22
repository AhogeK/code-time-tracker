package com.ahogek.codetimetracker.handler

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actionSystem.TypedActionHandler
import com.intellij.openapi.fileEditor.FileDocumentManager

class MyTypedActionHandler(private val originalHandler: TypedActionHandler) : TypedActionHandler {

    private val log = Logger.getInstance(MyTypedActionHandler::class.java)

    override fun execute(editor: Editor, charTyped: Char, dataContext: DataContext) {
        try {
            val vFile = FileDocumentManager.getInstance().getFile(editor.document)
            val charRepresentation = when (charTyped) {
                '\n' -> "Enter"
                '\t' -> "Tab"
                ' ' -> "Space"
                else -> charTyped.toString()
            }
            log.info("Key Typed: '$charRepresentation' in file: ${vFile?.name}")
        } finally {
            // 必须调用原始handler，确保用户的输入能正常显示在编辑器中
            originalHandler.execute(editor, charTyped, dataContext)
        }
    }
}