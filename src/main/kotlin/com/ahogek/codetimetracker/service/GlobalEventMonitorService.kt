package com.ahogek.codetimetracker.service

import com.ahogek.codetimetracker.handler.MyTypedActionHandler
import com.intellij.ide.IdeEventQueue
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.actionSystem.TypedAction
import com.intellij.openapi.editor.actionSystem.TypedActionHandler
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
    private val timeTrackerService = ApplicationManager.getApplication().getService(TimeTrackerService::class.java)
    private var originalTypedActionHandler: TypedActionHandler? = null

    /**
     * Initializes all listeners that need to be registered programmatically.
     * This method is called via a stable trigger, and the built-in logic gate ensures it only executes once.
     */
    fun initializeListeners() {
        if (isListenerInitialized.compareAndSet(false, true)) {
            log.info("Initializing global listeners for the first time.")

            // Register low-level AWT event listeners (mouse clicks, scrolling)
            IdeEventQueue.getInstance().addPostprocessor({ awtEvent: AWTEvent ->
                if (awtEvent.id == MouseEvent.MOUSE_PRESSED || awtEvent.id == MouseEvent.MOUSE_WHEEL) {
                    findEditorForEvent(awtEvent)?.let { editor ->
                        val vFile = FileDocumentManager.getInstance().getFile(editor.document)
                        if (vFile != null && vFile.isInLocalFileSystem && vFile.isWritable) {
                            // 所有检查通过后才认为是一次有效的编码活动
                            timeTrackerService.onActivity(editor)
                        }
                    }
                }
                false
            }, this)
            log.info("Low-level AWT listener registered.")

            // Register character input listener
            val typedAction = TypedAction.getInstance()
            originalTypedActionHandler = typedAction.rawHandler
            typedAction.setupRawHandler(MyTypedActionHandler(originalTypedActionHandler!!, timeTrackerService))
            log.info("Custom TypedActionHandler registered.")
        }
    }

    override fun dispose() {
        // Restore the original handler
        originalTypedActionHandler?.let {
            TypedAction.getInstance().setupRawHandler(it)
            log.info("Restored original TypedActionHandler.")
        }
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