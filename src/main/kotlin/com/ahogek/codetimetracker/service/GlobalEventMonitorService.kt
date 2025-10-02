package com.ahogek.codetimetracker.service

import com.ahogek.codetimetracker.handler.MyTypedActionHandler
import com.intellij.ide.IdeEventQueue
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.actionSystem.TypedAction
import com.intellij.openapi.editor.actionSystem.TypedActionHandler
import com.intellij.openapi.fileEditor.FileDocumentManager
import java.awt.AWTEvent
import java.awt.Component
import java.awt.event.MouseEvent
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.SwingUtilities

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
                if (awtEvent is MouseEvent && (awtEvent.id == MouseEvent.MOUSE_PRESSED || awtEvent.id == MouseEvent.MOUSE_WHEEL)) {
                    val component = awtEvent.component ?: return@addPostprocessor false

                    checkEditor(component)
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

    private fun checkEditor(component: Component) {
        val editor = EditorFactory.getInstance().allEditors.find { editor ->
            SwingUtilities.isDescendingFrom(component, editor.component)
        }

        if (editor != null) {
            val vFile = FileDocumentManager.getInstance().getFile(editor.document)
            if (vFile != null && vFile.isInLocalFileSystem && vFile.isWritable) {
                timeTrackerService.onActivity(editor)
            }
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
}