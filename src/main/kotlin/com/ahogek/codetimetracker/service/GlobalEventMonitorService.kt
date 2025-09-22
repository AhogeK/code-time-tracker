package com.ahogek.codetimetracker.service

import com.ahogek.codetimetracker.handler.MyTypedActionHandler
import com.intellij.ide.IdeEventQueue
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.actionSystem.TypedAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import java.awt.AWTEvent
import java.awt.MouseInfo
import java.awt.Point
import java.awt.Rectangle
import java.awt.event.MouseEvent
import java.util.concurrent.atomic.AtomicBoolean

@Service(Service.Level.APP)
class GlobalEventMonitorService : Disposable {

    private val log = Logger.getInstance(GlobalEventMonitorService::class.java)
    private val isListenerInitialized = AtomicBoolean(false)

    /**
     * 初始化所有需要通过编程方式注册的监听器。
     * 这个方法通过一个稳定的触发器来调用，且内置的逻辑门确保只执行一次。
     */
    fun initializeListeners() {
        if (isListenerInitialized.compareAndSet(false, true)) {
            log.info("Initializing global listeners for the first time.")

            // 1. 注册底层AWT事件监听器 (鼠标点击、滚动)
            IdeEventQueue.getInstance().addPostprocessor({ awtEvent: AWTEvent ->
                findEditorForEvent(awtEvent)?.let { editor ->
                    val vFile = FileDocumentManager.getInstance().getFile(editor.document)
                    if (awtEvent.id == MouseEvent.MOUSE_WHEEL) {
                        val wheelEvent = awtEvent as java.awt.event.MouseWheelEvent
                        log.info("Mouse wheel scrolled: ${wheelEvent.wheelRotation} in file: ${vFile?.name}")
                    } else if (awtEvent.id == MouseEvent.MOUSE_PRESSED) {
                        val mouseEvent = awtEvent as MouseEvent
                        val buttonType = when (mouseEvent.button) {
                            MouseEvent.BUTTON1 -> "Left Click"
                            MouseEvent.BUTTON2 -> "Middle Click"
                            MouseEvent.BUTTON3 -> "Right Click"
                            else -> "Unknown Click"
                        }
                        log.info("$buttonType in file: ${vFile?.name}")
                    }
                }
                false
            }, this)
            log.info("Low-level AWT listener registered.")

            // 2. 注册字符输入监听器
            val typedAction = TypedAction.getInstance()
            val originalHandler = typedAction.rawHandler
            typedAction.setupRawHandler(MyTypedActionHandler(originalHandler))
            log.info("Custom TypedActionHandler registered.")
        }
    }

    override fun dispose() {
        log.info("GlobalEventMonitorService disposed.")
    }

    private fun findEditorForEvent(awtEvent: AWTEvent): Editor? {
        val mousePointOnScreen: Point = when (awtEvent) {
            is MouseEvent -> awtEvent.locationOnScreen
            else -> MouseInfo.getPointerInfo()?.location ?: return null
        }

        return EditorFactory.getInstance().allEditors.find { editor ->
            val editorComponent = editor.component
            if (!editorComponent.isShowing) return@find false
            val editorBounds = Rectangle(editorComponent.locationOnScreen, editorComponent.size)
            editorBounds.contains(mousePointOnScreen)
        }
    }
}