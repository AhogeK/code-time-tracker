package com.ahogek.codetimetracker.service.sync

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

/**
 * Non-sensitive sync configuration persisted in the IDE: server address, enable flag and
 * the API key prefix used for display. The raw API key itself lives in the IDE credential
 * store ([SyncKeyVault]) and never reaches disk as plaintext.
 *
 * @author AhogeK ahogek@gmail.com
 * @since 2026-08-26
 */
@State(name = "SyncSettings", storages = [Storage("code-time-tracker-sync.xml")])
@Service(Service.Level.APP)
class SyncSettingsState : PersistentStateComponent<SyncSettingsState.State> {

    data class State(
        var serverUrl: String = DEFAULT_SERVER_URL,
        var syncEnabled: Boolean = false,
        var apiKeyPrefix: String? = null,
        var syncIntervalMinutes: Int = DEFAULT_SYNC_INTERVAL_MINUTES,
    )

    @Volatile
    private var state: State = State()

    override fun getState(): State = state

    override fun loadState(state: State) {
        this.state = state
    }

    var serverUrl: String
        get() = state.serverUrl
        set(value) {
            state = state.copy(serverUrl = value)
        }

    var syncEnabled: Boolean
        get() = state.syncEnabled
        set(value) {
            state = state.copy(syncEnabled = value)
        }

    var apiKeyPrefix: String?
        get() = state.apiKeyPrefix
        set(value) {
            state = state.copy(apiKeyPrefix = value)
        }

    var syncIntervalMinutes: Int
        get() = state.syncIntervalMinutes
        set(value) {
            state = state.copy(syncIntervalMinutes = value.coerceAtLeast(0))
        }

    companion object {
        /** Periodic sync fallback interval in minutes (0 disables the timer). */
        const val DEFAULT_SYNC_INTERVAL_MINUTES = 5
        // Default server URL is injected at build time (see .env.example); the settings
        // page can still override it at runtime per user.
        const val DEFAULT_SERVER_URL: String = SyncWebConfig.DEFAULT_SERVER_URL
    }
}
