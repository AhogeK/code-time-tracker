package com.ahogek.codetimetracker.ui

import com.ahogek.codetimetracker.service.sync.SyncApiKeyManager
import com.ahogek.codetimetracker.service.sync.SyncApiService
import com.ahogek.codetimetracker.service.sync.SyncResult
import com.ahogek.codetimetracker.service.sync.SyncSettingsState
import com.intellij.notification.NotificationType
import com.intellij.notification.NotificationGroupManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.ui.MessageType
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import java.awt.FlowLayout
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Settings page for the sync feature: server address, sync toggle, API key binding
 * (sign-in flow or manual paste), unbinding and server connectivity check.
 *
 * Network operations run on a pooled thread and never block the EDT; results are
 * surfaced through balloon notifications.
 *
 * @author AhogeK ahogek@gmail.com
 * @since 2026-08-26
 */
class SyncSettingsConfigurable : SearchableConfigurable {

    private val settings = ApplicationManager.getApplication().getService(SyncSettingsState::class.java)
    private val keyManager = ApplicationManager.getApplication().getService(SyncApiKeyManager::class.java)
    private val apiService = ApplicationManager.getApplication().getService(SyncApiService::class.java)

    private val serverUrlField = JBTextField(settings.serverUrl, 40)
    private val syncEnabledCheckBox = JBCheckBox("Enable synchronization", settings.syncEnabled)

    private val bindingStatusLabel = JBLabel()
    private val emailField = JBTextField(30)
    private val passwordField = JBPasswordField().apply { columns = 30 }
    private val bindButton = JButton("Sign in and bind API key")
    private val manualKeyField = JBTextField(40)
    private val pasteButton = JButton("Bind pasted API key")
    private val unbindButton = JButton("Unbind API key")
    private val testButton = JButton("Test connection")

    override fun getDisplayName(): String = "Code Time Tracker Sync"

    override fun getId(): String = "com.ahogek.codetimetracker.syncSettings"

    override fun createComponent(): JComponent {
        serverUrlField.toolTipText = "ctt-server base URL, e.g. http://localhost:8080/ctt-server"
        syncEnabledCheckBox.toolTipText = "Enables uploading and downloading coding sessions"

        bindButton.addActionListener { bindWithCredentials() }
        pasteButton.addActionListener { bindWithManualKey() }
        unbindButton.addActionListener { unbind() }
        testButton.addActionListener { testConnection() }

        refreshBindingState()

        val actionPanel = JPanel(FlowLayout(FlowLayout.LEFT, 4, 0)).apply {
            add(unbindButton)
            add(testButton)
        }

        return FormBuilder.createFormBuilder()
            .addLabeledComponent(JBLabel("Server address:"), serverUrlField)
            .addComponent(syncEnabledCheckBox)
            .addSeparator()
            .addLabeledComponent(JBLabel("API key:"), bindingStatusLabel)
            .addLabeledComponent(JBLabel("Email:"), emailField)
            .addLabeledComponent(JBLabel("Password:"), passwordField)
            .addComponent(bindButton)
            .addLabeledComponent(JBLabel("Or paste an API key:"), manualKeyField)
            .addComponent(pasteButton)
            .addComponent(
                JBLabel(
                    "Keys are stored in the IDE credential store and used with the SYNC scope. " +
                        "Create one manually at ctt-server \u2192 Profile \u2192 API keys.",
                ).apply {
                    foreground = JBColor.GRAY
                },
            )
            .addComponent(actionPanel)
            .panel
    }

    override fun isModified(): Boolean =
        serverUrlField.text.trim() != settings.serverUrl ||
            syncEnabledCheckBox.isSelected != settings.syncEnabled

    override fun apply() {
        var url = serverUrlField.text.trim()
        if (!isValidServerUrl(url)) {
            url = SyncSettingsState.DEFAULT_SERVER_URL
            serverUrlField.text = url
            notify("Invalid server address. Reverted to the default.", MessageType.WARNING)
        }
        settings.serverUrl = url
        settings.syncEnabled = syncEnabledCheckBox.isSelected
    }

    override fun reset() {
        serverUrlField.text = settings.serverUrl
        syncEnabledCheckBox.isSelected = settings.syncEnabled
        refreshBindingState()
    }

    private fun bindWithCredentials() {
        val email = emailField.text.trim()
        val password = String(passwordField.password)
        if (email.isEmpty() || password.isEmpty()) {
            Messages.showWarningDialog("Enter both email and password first.", "Sync Setup")
            return
        }
        setBusy(true)
        ApplicationManager.getApplication().executeOnPooledThread {
            val result = keyManager.bindWithCredentials(email, password)
            ApplicationManager.getApplication().invokeLater {
                setBusy(false)
                handleBindingResult(result, "API key bound successfully.")
            }
        }
    }

    private fun bindWithManualKey() {
        val rawKey = manualKeyField.text
        if (rawKey.isBlank()) {
            Messages.showWarningDialog("Paste the API key first.", "Sync Setup")
            return
        }
        setBusy(true)
        ApplicationManager.getApplication().executeOnPooledThread {
            val result = keyManager.bindWithManualKey(rawKey)
            ApplicationManager.getApplication().invokeLater {
                setBusy(false)
                handleBindingResult(result, "API key bound successfully.")
            }
        }
    }

    private fun handleBindingResult(result: SyncResult<Unit>, successMessage: String) {
        when (result) {
            is SyncResult.Success -> {
                // Credential hygiene: the secret must not linger in the component.
                passwordField.text = ""
                manualKeyField.text = ""
                notify(successMessage, MessageType.INFO)
            }
            is SyncResult.Failure -> notify(result.error.toUserMessage(), MessageType.ERROR)
        }
        refreshBindingState()
    }

    private fun unbind() {
        val choice = Messages.showYesNoDialog(
            "Remove the bound API key from this IDE?",
            "Unbind API Key",
            Messages.getYesButton(),
            Messages.getNoButton(),
            Messages.getQuestionIcon(),
        )
        if (choice == Messages.YES) {
            keyManager.unbind()
            refreshBindingState()
            notify("API key removed.", MessageType.INFO)
        }
    }

    private fun testConnection() {
        val url = serverUrlField.text.trim()
        if (!isValidServerUrl(url)) {
            notify("Enter a valid server address (http:// or https://) first.", MessageType.WARNING)
            return
        }
        setBusy(true)
        ApplicationManager.getApplication().executeOnPooledThread {
            // Ping the URL the user is editing, not the last applied one; the settings
            // value is restored afterwards so an un-applied edit is not persisted.
            val savedUrl = settings.serverUrl
            settings.serverUrl = url
            try {
                val result = apiService.pingServer()
                ApplicationManager.getApplication().invokeLater {
                    setBusy(false)
                    when (result) {
                        is SyncResult.Success -> notify("Server is reachable.", MessageType.INFO)
                        is SyncResult.Failure -> notify(result.error.toUserMessage(), MessageType.ERROR)
                    }
                }
            } finally {
                settings.serverUrl = savedUrl
            }
        }
    }

    private fun refreshBindingState() {
        val bound = keyManager.isBound()
        // Keep the toggle consistent with the persisted state: bind/unbind change
        // settings.syncEnabled, otherwise OK/Apply would silently revert the change.
        syncEnabledCheckBox.isSelected = settings.syncEnabled
        val prefix = settings.apiKeyPrefix
        bindingStatusLabel.text = if (bound) {
            "<html>Bound (<b>${StringUtil.escapeXmlEntities(prefix ?: "")}</b>\u2026)</html>"
        } else {
            "Not bound"
        }
        unbindButton.isEnabled = bound
        testButton.isEnabled = !bound
    }

    private fun setBusy(busy: Boolean) {
        listOf(bindButton, pasteButton, unbindButton, testButton).forEach { it.isEnabled = !busy }
    }

    private fun isValidServerUrl(url: String): Boolean =
        url.matches(SERVER_URL_PATTERN)

    private fun notify(message: String, type: MessageType) {
        val notificationType = when (type) {
            MessageType.ERROR -> NotificationType.ERROR
            MessageType.WARNING -> NotificationType.WARNING
            else -> NotificationType.INFORMATION
        }
        NotificationGroupManager.getInstance()
            .getNotificationGroup(NOTIFICATION_GROUP_ID)
            .createNotification("Code Time Tracker Sync", message, notificationType)
            .notify(null)
    }

    companion object {
        const val NOTIFICATION_GROUP_ID = "Code Time Tracker Sync"
        private val SERVER_URL_PATTERN = Regex("^https?://.+")
    }
}
