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
private val SYNC_DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

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

    private val serverUrlField = JBTextField(settings.serverUrl, 24)
    private val syncEnabledCheckBox = JBCheckBox("Enable synchronization", settings.syncEnabled)

    private val bindingStatusLabel = JBLabel()
    private val getKeyButton = JButton("Get an API key")
    private val manualKeyField = JBTextField(40)
    private val pasteButton = JButton("Bind pasted API key")
    private val unbindButton = JButton("Unbind API key")
    private val testButton = JButton("Test connection")
    private val syncNowButton = JButton("Sync now")
    // Bound to its preferred size: FormBuilder stretches the row and BoxLayout would
    // otherwise expand the field (unbounded maximum size) to fill the whole width.
    private val syncIntervalField = JBTextField(settings.syncIntervalMinutes.toString(), 4).apply {
        maximumSize = preferredSize
    }
    private val statusLabel = JBLabel(" ").apply { foreground = JBColor.GRAY }
    private val deviceStatusLabel = JBLabel(" ")
    private val syncStatusLabel = JBLabel(" ").apply { foreground = JBColor.GRAY }
    private val testStatusLabel = JBLabel(" ").apply { foreground = JBColor.GRAY }
    private val sessionRepository = DatabaseManager.getSessionRepository()
    private val statusTimer = Timer(STATUS_MESSAGE_MS) {
        statusLabel.text = " "
        statusLabel.foreground = JBColor.GRAY
        testStatusLabel.text = " "
        testStatusLabel.foreground = JBColor.GRAY
        // Restore the sync status row after a transient message (e.g. the Sync-now gate).
        refreshSyncStatus()
    }.apply { isRepeats = false }

    // The message bus notifies this page right after every sync round, so the status
    // row refreshes immediately (no polling) while the page is open.
    private val syncStateConnection = ApplicationManager.getApplication().messageBus.connect().apply {
        subscribe(
            SyncStateListener.TOPIC,
            SyncStateListener {
                val target = panel ?: return@SyncStateListener
                ApplicationManager.getApplication().invokeLater({
                    refreshSyncStatus()
                }, ModalityState.stateForComponent(target))
            },
        )
    }
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

        val serverRow = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            add(serverUrlField)
            add(Box.createHorizontalStrut(4))
            add(testButton)
        }

        // Test feedback lives on its own row (left), sharing it with the sync toggle
        // (right), so message length changes never reflow the controls above.
        val statusAndToggleRow = JPanel(BorderLayout()).apply {
            add(syncEnabledCheckBox, BorderLayout.WEST)
            add(testStatusLabel, BorderLayout.EAST)
        }

        val bindRow = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            add(pasteButton)
            add(Box.createHorizontalStrut(4))
            add(unbindButton)
        }

        val intervalRow = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            add(syncIntervalField)
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
            .addLabeledComponent(JBLabel("Server address:"), serverRow)
            .addComponent(statusAndToggleRow)
            .addSeparator()
            .addLabeledComponent(JBLabel("API key:"), statusRow)
            .addComponent(getKeyButton)
            .addLabeledComponent(JBLabel("Or paste an API key:"), manualKeyField)
            .addComponent(bindRow)
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
            .addComponent(statusLabel)
            .addSeparator()
            .addLabeledComponent(JBLabel("Sync interval (min):"), intervalRow)
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

    override fun disposeUIResources() {
        syncStateConnection.dispose()
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
        // A switch of the bound key resets the sync context so the previous user's
        // sessions are never pushed to the newly bound account.
        val wasBound = keyManager.isBound()
        ApplicationManager.getApplication().executeOnPooledThread {
            val result = try {
                keyManager.bindWithManualKey(rawKey)
            } catch (t: Throwable) {
                SyncResult.Failure(SyncError(SyncErrorKind.UNKNOWN, message = "Unexpected error: ${t.message}"))
            }
            ApplicationManager.getApplication().invokeLater({
                setBusy(false)
                handleBindingResult(result, wasBound)
            }, ModalityState.stateForComponent(panel ?: return@executeOnPooledThread))
        }
    }

    private fun registerDeviceOnBind(wasBound: Boolean = false) {
        val apiKey = keyManager.getApiKey() ?: return
        ApplicationManager.getApplication().executeOnPooledThread {
            val result = apiService.registerDevice(SyncDeviceMetadata.registrationRequest(), apiKey)
            // Resolve the account scope first so the initial sync marks sessions with the
            // bound user's id and statistics only cover that account.
            val userResult = apiService.currentUser(apiKey)
            val newUserId = (userResult as? SyncResult.Success)?.data?.id
            // A re-bind only resets the sync context when the account actually changed:
            // re-binding the same user's key must keep the cursor and the owner scope,
            // otherwise every bind would re-pull everything and briefly drop the stats
            // owner (the transient wrong-statistics window).
            if (wasBound && newUserId != null && newUserId != settings.serverUserId) {
                coordinator.resetForUserSwitch()
            }
            if (newUserId != null) {
                settings.serverUserId = newUserId
                DatabaseManager.setStatsOwner(newUserId)
            }
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

    private fun handleBindingResult(result: SyncResult<Unit>, wasBound: Boolean = false) {
        when (result) {
            is SyncResult.Success -> {
                // Credential hygiene: the secret must not linger in the component.
                manualKeyField.text = ""
                showStatus(BIND_SUCCESS_MESSAGE)
                notify(BIND_SUCCESS_MESSAGE, MessageType.INFO)
                registerDeviceOnBind(wasBound)
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
            settings.serverUserId = null
            DatabaseManager.setStatsOwner(null)
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
        testStatusLabel.text = "Testing connection..."
        testStatusLabel.foreground = JBColor.GRAY
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
                        is SyncResult.Success -> showTestStatus("Server is reachable.")
                        is SyncResult.Failure -> showTestStatus(result.error.toUserMessage(), error = true)
                    }
                }, ModalityState.stateForComponent(panel ?: return@executeOnPooledThread))
            } finally {
                settings.serverUrl = savedUrl
            }
        }
    }

    private fun syncNow() {
        if (!settings.syncEnabled || keyManager.getApiKey() == null) {
            // This action lives in the sync section, so the gate message belongs on the
            // sync status row (next to the button), not on the binding operation row.
            // It auto-clears like the other transient messages (statusTimer restores the row).
            syncStatusLabel.foreground = UIUtil.getErrorForeground()
            syncStatusLabel.text = "Bind an API key and enable synchronization first."
            statusTimer.restart()
            return
        }
        setBusy(true)
        syncStatusLabel.text = "Syncing..."
        ApplicationManager.getApplication().executeOnPooledThread {
            val result = coordinator.syncOnce()
            ApplicationManager.getApplication().invokeLater({
                setBusy(false)
                // Success is reflected by the refreshed status row (Last sync advances);
                // a failure surfaces in the status row and as a notification, without
                // pushing an operation message up to the binding section.
                refreshSyncStatus()
                if (result is SyncResult.Failure) {
                    val message = "Sync failed: ${result.error.toUserMessage()}"
                    syncStatusLabel.text = message
                    syncStatusLabel.foreground = UIUtil.getErrorForeground()
                    notify(message, MessageType.ERROR)
                }
            }, ModalityState.stateForComponent(panel ?: return@executeOnPooledThread))
        }
    }

    private fun readInterval(): Int? = syncIntervalField.text.trim().toIntOrNull()

    private fun refreshSyncStatus() {
        val app = ApplicationManager.getApplication()
        app.executeOnPooledThread {
            // Both reads hit the database (dirty count, persisted push time) and must
            // run off the EDT; only the label update happens on it.
            val pending = runCatching { sessionRepository.getDirtySessions().size }.getOrDefault(0)
            val lastSync = runCatching { coordinator.lastSyncAt() }.getOrNull()
            val error = coordinator.lastSyncError
            app.invokeLater({
                val parts = mutableListOf<String>()
                parts.add(if (lastSync != null) "Last sync: ${lastSync.format(SYNC_DATETIME_FORMATTER)}" else "Never synced")
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

    private fun showTestStatus(message: String, error: Boolean = false) {
        testStatusLabel.foreground = if (error) UIUtil.getErrorForeground() else STATUS_SUCCESS_COLOR
        testStatusLabel.text = message
        statusTimer.restart()
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
    }
}
