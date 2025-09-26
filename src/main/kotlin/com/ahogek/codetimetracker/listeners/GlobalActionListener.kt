package com.ahogek.codetimetracker.listeners

import com.ahogek.codetimetracker.service.TimeTrackerService
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.ex.AnActionListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileDocumentManager
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent

/**
 * 全局动作监听器 (Global Action Listener)。
 *
 * 这个监听器通过实现 [AnActionListener] 接口，可以在 IntelliJ IDE 中任何一个 "Action" 执行前后进行拦截。
 * 它的核心职责是监控用户的操作意图（例如“复制”、“保存”、“向上移动光标”等），
 * 并记录触发该操作的原始输入方式（如键盘按键）以及操作发生的上下文（如具体在哪个文件中）。
 *
 * 要使其生效，必须在 `plugin.xml` 中进行注册。
 *
 * @see AnActionListener
 */
class GlobalActionListener : AnActionListener {
    private val log = Logger.getInstance(GlobalActionListener::class.java)
    private val timeTrackerService = ApplicationManager.getApplication().getService(TimeTrackerService::class.java)

    override fun beforeActionPerformed(action: AnAction, event: AnActionEvent) {
        // 尝试从事件上下文中获取编辑器
        val editor = event.dataContext.getData(CommonDataKeys.EDITOR)
        if (editor != null) {
            // 检查文件有效性
            val vFile = FileDocumentManager.getInstance().getFile(editor.document)
            if (vFile != null && vFile.isInLocalFileSystem && vFile.isWritable)
            // 如果是在一个有效的编辑器中执行动作，则认为是一次活动
                timeTrackerService.onActivity(editor)
        }

        // 日志监控（用于调试可后续移除）
        logAction(action, event)
    }

    private fun logAction(
        action: AnAction,
        event: AnActionEvent
    ) {
        val actionId = event.actionManager.getId(action) ?: action.javaClass.name
        val triggerSource = when (val inputEvent = event.inputEvent) {
            is KeyEvent -> "Key Press: ${KeyEvent.getKeyText(inputEvent.keyCode)}"
            is MouseEvent -> "Mouse Click (count: ${inputEvent.clickCount})"
            else -> "Programmatic/Unknown"
        }
        val editor = event.dataContext.getData(CommonDataKeys.EDITOR)
        if (editor != null) {
            val vFile = FileDocumentManager.getInstance().getFile(editor.document)
            log.info("Action '$actionId' triggered by [$triggerSource] in Editor -> File: ${vFile?.path}")
            return
        }
        val vFile = event.dataContext.getData(CommonDataKeys.VIRTUAL_FILE)
        if (vFile != null) {
            log.info("Action '$actionId' triggered by [$triggerSource] on File -> File: ${vFile.path}")
            return
        }
        log.info("Action '$actionId' triggered by [$triggerSource] (Global, no file context)")
    }
}