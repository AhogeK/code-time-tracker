package com.ahogek.codetimetracker.ui

import com.ahogek.codetimetracker.database.DatabaseManager
import com.ahogek.codetimetracker.service.sync.*
import com.ahogek.codetimetracker.user.UserManager
import com.intellij.ide.BrowserUtil
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.ui.MessageType
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.time.format.DateTimeFormatter
import javax.swing.*

private val SERVER_URL_PATTERN = Regex("^https?://.+")
private val STATUS_SUCCESS_COLOR = JBColor(0x1B7F3B, 0x4E9A51)

/**
 * Settings page for the sync feature: server address, web console URL, sync toggle,
 * API key binding (manual paste), unbinding, server connectivity check, a manual
 * "Sync now" action, a periodic sync interval (minutes) and a sync status line
 * (last sync time, pending session count and last error).
 *
 * Network and database operations run on a pooled thread and never block the EDT;
 * results are surfaced through balloon notifications and the status lines.
 *
 * @author AhogeK ahogek@gmail.com
 * @since 2026-08-26
 */
class SyncSettingsConfigurable : SearchableConfigurable {

    private val settings = ApplicationManager.getApplication().getService(SyncSettingsState::class.java)
    private val keyManager = ApplicationManager.getApplication().getService(SyncApiKeyManager::class.java)
    private val apiService = ApplicationManager.getApplication().getService(SyncApiServiceImpl::class.java)
    private val coordinator = ApplicationManager.getApplication().getService(SyncCoordinator::class.java)
    private val scheduler = ApplicationManager.getApplication().getService(SyncScheduler::class.java)

    private val serverUrlField = JBTextField(settings.serverUrl, 40)
    private val syncEnabledCheckBox = JBCheckBox("Enable synchronization", settings.syncEnabled)

    private val bindingStatusLabel = JBLabel()
    private val getKeyButton = JButton("Get an API key")
    private val manualKeyField = JBTextField(40)
    private val pasteButton = JButton("Bind pasted API key")
    private val unbindButton = JButton("Unbind API key")
    private val testButton = JButton("Test connection")
    private val syncNowButton = JButton("Sync now")
    private val syncIntervalField = JBTextField(settings.syncIntervalMinutes.toString(), 5)
    private val statusLabel = JBLabel(" ").apply { foreground = JBColor.GRAY }
    private val deviceStatusLabel = JBLabel(" ")
    private val syncStatusLabel = JBLabel(" ").apply { foreground = JBColor.GRAY }
    private val sessionRepository = DatabaseManager.getSessionRepository()
    private val statusTimer = Timer(STATUS_MESSAGE_MS) {
        statusLabel.text = " "
        statusLabel.foreground = JBColor.GRAY
    }.apply { isRepeats = false }
    private var panel: JComponent? = null

    override fun getDisplayName(): String = DISPLAY_NAME

    override fun getId(): String = "com.ahogek.codetimetracker.syncSettings"

    override fun createComponent(): JComponent {
        serverUrlField.toolTipText = "ctt-server base URL, e.g. http://localhost:8080/ctt-server"
        syncEnabledCheckBox.toolTipText = "Enables uploading and downloading coding sessions"

        getKeyButton.addActionListener { openWebConsole() }
        pasteButton.addActionListener { bindWithManualKey() }
        unbindButton.addActionListener { unbind() }
        testButton.addActionListener { testConnection() }
        syncNowButton.addActionListener { syncNow() }

        val actionPanel = JPanel().apply {
            // BoxLayout avoids FlowLayout's default 5px insets, keeping the buttons
            // left-aligned with the "Bind pasted API key" button above.
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            add(unbindButton)
            add(Box.createHorizontalStrut(4))
            add(testButton)
            add(Box.createHorizontalStrut(4))
            add(syncNowButton)
        }

        val statusRow = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            add(bindingStatusLabel)
            add(Box.createHorizontalStrut(8))
            add(deviceStatusLabel)
        }

        val formPanel = FormBuilder.createFormBuilder()
            .addLabeledComponent(JBLabel("Server address:"), serverUrlField)
            .addComponent(syncEnabledCheckBox)
            .addSeparator()
            .addLabeledComponent(JBLabel("API key:"), statusRow)
            .addComponent(getKeyButton)
            .addLabeledComponent(JBLabel("Or paste an API key:"), manualKeyField)
            .addComponent(pasteButton)
            .addComponent(
                JBLabel(
                    "<html>Keys are stored in the IDE credential store and used with the SYNC scope.<br>" +
                        "Create one manually at ctt-server \u2192 Profile \u2192 API keys.</html>",
                ).apply {
                    foreground = JBColor.GRAY
                    border = JBUI.Borders.emptyLeft(JBUI.scale(24))
                    UIUtil.applyStyle(UIUtil.ComponentStyle.SMALL, this)
                },
            )
            .addComponent(actionPanel)
            .addComponent(statusLabel)
            .addSeparator()
            .addLabeledComponent(JBLabel("Sync interval (minutes, 0 = off):"), syncIntervalField)
            .addComponent(syncStatusLabel)
            .panel

        val root = JPanel(BorderLayout()).apply {
            add(formPanel, BorderLayout.NORTH)
        }

        panel = root
        refreshBindingState()
        refreshSyncStatus()
        return root
    }

    override fun isModified(): Boolean =
        serverUrlField.text.trim() != settings.serverUrl ||
            syncEnabledCheckBox.isSelected != settings.syncEnabled ||
            syncIntervalField.text.trim() != settings.syncIntervalMinutes.toString()

    override fun apply() {
        var url = serverUrlField.text.trim()
        if (!isValidServerUrl(url)) {
            url = SyncSettingsState.DEFAULT_SERVER_URL
            serverUrlField.text = url
            notify("Invalid server address. Reverted to the default.", MessageType.WARNING)
        }
        settings.serverUrl = url
        settings.syncEnabled = syncEnabledCheckBox.isSelected
        settings.syncIntervalMinutes = readInterval() ?: SyncSettingsState.DEFAULT_SYNC_INTERVAL_MINUTES
        syncIntervalField.text = settings.syncIntervalMinutes.toString()
        // Re-arm the periodic task for the new interval / enable flag.
        scheduler.reschedule()
    }

    override fun reset() {
        serverUrlField.text = settings.serverUrl
        syncEnabledCheckBox.isSelected = settings.syncEnabled
        syncIntervalField.text = settings.syncIntervalMinutes.toString()
        refreshBindingState()
    }

    private fun openWebConsole() {
        BrowserUtil.browse(SyncWebConfig.WEB_URL)
    }

    private fun bindWithManualKey() {
        val rawKey = manualKeyField.text
        if (rawKey.isBlank()) {
            Messages.showWarningDialog("Paste the API key first.", "Sync Setup")
            return
        }
        setBusy(true)
        statusLabel.text = "Connecting to ctt-server..."
        ApplicationManager.getApplication().executeOnPooledThread {
            val result = try {
                keyManager.bindWithManualKey(rawKey)
            } catch (t: Throwable) {
                SyncResult.Failure(SyncError(SyncErrorKind.UNKNOWN, message = "Unexpected error: ${t.message}"))
            }
            ApplicationManager.getApplication().invokeLater({
                setBusy(false)
                handleBindingResult(result)
            }, ModalityState.stateForComponent(panel ?: return@executeOnPooledThread))
        }
    }

    private fun registerDeviceOnBind() {
        val apiKey = keyManager.getApiKey() ?: return
        ApplicationManager.getApplication().executeOnPooledThread {
            val result = apiService.registerDevice(SyncDeviceMetadata.registrationRequest(), apiKey)
            // A freshly bound device runs an initial sync round right after registration so
            // the local store converges with the server without further user action.
            val syncResult = if (result is SyncResult.Success) {
                // Binding enables sync; re-arm the periodic fallback so it applies
                // without waiting for an IDE restart.
                scheduler.reschedule()
                coordinator.syncOnce()
            } else {
                null
            }
            ApplicationManager.getApplication().invokeLater({
                when (result) {
                    is SyncResult.Success -> {
                        showStatus("Device registered.")
                        checkDeviceRegistration()
                        if (syncResult is SyncResult.Failure) {
                            showStatus("Initial sync failed: ${syncResult.error.toUserMessage()}", error = true)
                        }
                    }
                    is SyncResult.Failure -> showStatus(result.error.toUserMessage(), error = true)
                }
            }, ModalityState.stateForComponent(panel ?: return@executeOnPooledThread))
        }
    }

    private fun handleBindingResult(result: SyncResult<Unit>) {
        when (result) {
            is SyncResult.Success -> {
                // Credential hygiene: the secret must not linger in the component.
                manualKeyField.text = ""
                showStatus(BIND_SUCCESS_MESSAGE)
                notify(BIND_SUCCESS_MESSAGE, MessageType.INFO)
                registerDeviceOnBind()
            }
            is SyncResult.Failure -> {
                showStatus(result.error.toUserMessage(), error = true)
                notify(result.error.toUserMessage(), MessageType.ERROR)
            }
        }
        refreshBindingState()
        refreshSyncStatus()
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
            refreshSyncStatus()
            showStatus("API key removed.")
            notify("API key removed.", MessageType.INFO)
        }
    }

    private fun testConnection() {
        val url = serverUrlField.text.trim()
        if (!isValidServerUrl(url)) {
            showStatus("Enter a valid server address (http:// or https://) first.", error = true)
            return
        }
        setBusy(true)
        statusLabel.text = "Testing connection to $url..."
        ApplicationManager.getApplication().executeOnPooledThread {
            // Ping the URL the user is editing, not the last applied one; the settings
            // value is restored afterwards so an un-applied edit is not persisted.
            val savedUrl = settings.serverUrl
            settings.serverUrl = url
            try {
                val result = apiService.pingServer()
                ApplicationManager.getApplication().invokeLater({
                    setBusy(false)
                    when (result) {
                        is SyncResult.Success -> showStatus("Server is reachable.")
                        is SyncResult.Failure -> showStatus(result.error.toUserMessage(), error = true)
                    }
                }, ModalityState.stateForComponent(panel ?: return@executeOnPooledThread))
            } finally {
                settings.serverUrl = savedUrl
            }
        }
    }

    private fun syncNow() {
        if (!settings.syncEnabled || keyManager.getApiKey() == null) {
            showStatus("Bind an API key and enable synchronization first.", error = true)
            return
        }
        setBusy(true)
        syncStatusLabel.text = "Syncing..."
        ApplicationManager.getApplication().executeOnPooledThread {
            val result = coordinator.syncOnce()
            ApplicationManager.getApplication().invokeLater({
                setBusy(false)
                refreshSyncStatus()
                when (result) {
                    is SyncResult.Success -> showStatus("Sync completed.")
                    is SyncResult.Failure -> showStatus("Sync failed: ${result.error.toUserMessage()}", error = true)
                }
            }, ModalityState.stateForComponent(panel ?: return@executeOnPooledThread))
        }
    }

    private fun readInterval(): Int? = syncIntervalField.text.trim().toIntOrNull()

    private fun refreshSyncStatus() {
        val app = ApplicationManager.getApplication()
        app.executeOnPooledThread {
            val pending = runCatching { sessionRepository.getDirtySessions().size }.getOrDefault(0)
            app.invokeLater({
                val lastSync = coordinator.lastSyncAt
                val error = coordinator.lastSyncError
                val parts = mutableListOf<String>()
                parts.add(if (lastSync != null) "Last sync: ${lastSync.format(SYNC_TIME_FORMATTER)}" else "Never synced")
                parts.add("Pending: $pending")
                if (error != null) {
                    parts.add("Last error: $error")
                }
                syncStatusLabel.foreground = if (error != null) UIUtil.getErrorForeground() else JBColor.GRAY
                syncStatusLabel.text = parts.joinToString("  \u2022  ")
            }, ModalityState.stateForComponent(panel ?: return@executeOnPooledThread))
        }
    }

    private fun refreshBindingState() {
        val app = ApplicationManager.getApplication()
        app.executeOnPooledThread {
            // PasswordSafe access is a slow operation and is forbidden on the EDT
            // (SlowOperations), so the credential-store read runs off the EDT.
            val bound = keyManager.isBound()
            app.invokeLater({
                // Keep the toggle consistent with the persisted state: bind/unbind change
                // settings.syncEnabled, otherwise OK/Apply would silently revert the change.
                syncEnabledCheckBox.isSelected = settings.syncEnabled
                val prefix = settings.apiKeyPrefix
                bindingStatusLabel.text = if (bound) {
                    "<html>Bound (<b>${StringUtil.escapeXmlEntities(prefix ?: "")}</b>\u2026)</html>"
                } else {
                    "Not bound"
                }
                if (bound) {
                    checkDeviceRegistration()
                } else {
                    deviceStatusLabel.text = ""
                }
            }, ModalityState.stateForComponent(panel ?: return@executeOnPooledThread))
        }
    }

    private fun checkDeviceRegistration() {
        val apiKey = keyManager.getApiKey() ?: return
        val deviceId = UserManager.getUserId()
        ApplicationManager.getApplication().executeOnPooledThread {
            val result = apiService.listDevices(apiKey)
            ApplicationManager.getApplication().invokeLater({
                when (result) {
                    is SyncResult.Success -> {
                        val registered = result.data.any { it.id == deviceId }
                        deviceStatusLabel.foreground =
                            if (registered) STATUS_SUCCESS_COLOR else UIUtil.getErrorForeground()
                        deviceStatusLabel.text = if (registered) {
                            "Device registered"
                        } else {
                            "Device not registered - it will be registered on first sync with the server"
                        }
                    }
                    is SyncResult.Failure -> deviceStatusLabel.text = ""
                }
            }, ModalityState.stateForComponent(panel ?: return@executeOnPooledThread))
        }
    }

    private fun setBusy(busy: Boolean) {
        listOf(pasteButton, unbindButton, testButton, syncNowButton).forEach { it.isEnabled = !busy }
        if (!busy) {
            // Unbind and Sync now are binding-aware; Test connection stays available
            // regardless of the binding state (it only checks server reachability).
            unbindButton.isEnabled = settings.apiKeyPrefix != null
            syncNowButton.isEnabled = settings.apiKeyPrefix != null
        }
    }

    private fun isValidServerUrl(url: String): Boolean =
        url.matches(SERVER_URL_PATTERN)

    private fun showStatus(message: String, error: Boolean = false) {
        statusLabel.foreground = if (error) UIUtil.getErrorForeground() else STATUS_SUCCESS_COLOR
        statusLabel.text = message
        statusTimer.restart()
    }

    private fun notify(message: String, type: MessageType) {
        val notificationType = when (type) {
            MessageType.ERROR -> NotificationType.ERROR
            MessageType.WARNING -> NotificationType.WARNING
            else -> NotificationType.INFORMATION
        }
        NotificationGroupManager.getInstance()
            .getNotificationGroup(NOTIFICATION_GROUP_ID)
            .createNotification(DISPLAY_NAME, message, notificationType)
            .notify(null)
    }

    companion object {
        const val DISPLAY_NAME = "Code Time Tracker Sync"
        const val NOTIFICATION_GROUP_ID = DISPLAY_NAME
        private const val BIND_SUCCESS_MESSAGE = "API key bound successfully."
        private const val STATUS_MESSAGE_MS = 5_000
        private val SYNC_TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss")
    }
}
