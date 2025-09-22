package com.ahogek.codetimetracker.service

import com.intellij.ide.IdeEventQueue
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
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

    // 使用线程安全的 AtomicBoolean 作为“只执行一次”的标志位
    private val isListenerInitialized = AtomicBoolean(false)

    /**
     * 这是新的初始化方法，它将由一个稳定的触发器调用。
     */
    fun initializeListener() {
        // compareAndSet 是一个原子操作，它能确保在多线程环境下，
        // if 块内的代码在整个 application 生命周期中只会被执行一次。
        if (isListenerInitialized.compareAndSet(false, true)) {
            log.info("Initializing Global AWT listener for the first time.")

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
        }
    }

    override fun dispose() {
        log.info("GlobalEventMonitorService disposed, AWT listener removed automatically.")
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