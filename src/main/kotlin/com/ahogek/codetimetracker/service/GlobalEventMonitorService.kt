package com.ahogek.codetimetracker.service

import com.ahogek.codetimetracker.handler.MyTypedActionHandler
import com.ahogek.codetimetracker.widget.CodeTimeTrackerWidget
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

            IdeEventQueue.getInstance().addPostprocessor({ awtEvent: AWTEvent ->
                // The definitive check: If our widget's popup is active, ignore the event.
                if (CodeTimeTrackerWidget.isPopupActive) {
                    return@addPostprocessor false
                }
                if (awtEvent is MouseEvent && (awtEvent.id == MouseEvent.MOUSE_PRESSED || awtEvent.id == MouseEvent.MOUSE_WHEEL)) {
                    findEditorByCoordinates(awtEvent)?.let { editor ->
                        processActivity(editor)
                    }
                }
                false
            }, this)

            val typedAction = TypedAction.getInstance()
            originalTypedActionHandler = typedAction.rawHandler
            typedAction.setupRawHandler(MyTypedActionHandler(originalTypedActionHandler!!, timeTrackerService))
        }
    }

    private fun findEditorByCoordinates(event: MouseEvent): Editor? {
        return EditorFactory.getInstance().allEditors.find { editor ->
            val component = editor.component
            if (component.isShowing) {
                val boundsOnScreen = Rectangle(component.locationOnScreen, component.size)
                boundsOnScreen.contains(event.locationOnScreen)
            } else {
                false
            }
        }
    }

    private fun processActivity(editor: Editor) {
        val vFile = FileDocumentManager.getInstance().getFile(editor.document)
        if (vFile != null && vFile.isInLocalFileSystem && vFile.isWritable) {
            timeTrackerService.onActivity(editor)
        }
    }

    override fun dispose() {
        originalTypedActionHandler?.let {
            TypedAction.getInstance().setupRawHandler(it)
            log.info("Restored original TypedActionHandler.")
        }
        log.info("GlobalEventMonitorService disposed.")
    }
}