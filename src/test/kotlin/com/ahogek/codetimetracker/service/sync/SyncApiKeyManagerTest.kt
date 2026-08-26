package com.ahogek.codetimetracker.service.sync

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SyncApiKeyManagerTest {

    private lateinit var settings: SyncSettingsState
    private lateinit var vault: InMemorySyncKeyVault
    private lateinit var manager: SyncApiKeyManager

    @BeforeEach
    fun setUp() {
        settings = SyncSettingsState()
        vault = InMemorySyncKeyVault()
        manager = SyncApiKeyManager(settings)
        manager.vault = vault
    }

    @Test
    fun `should bind a manually pasted key and trim whitespace`() {
        val result = manager.bindWithManualKey("  cttak_manual_key  ")

        assertThat(result).isInstanceOf(SyncResult.Success::class.java)
        assertThat(vault.stored).isEqualTo("cttak_manual_key")
        assertThat(settings.apiKeyPrefix).isEqualTo("cttak_manual")
        assertThat(settings.syncEnabled).isTrue()
        assertThat(manager.isBound()).isTrue()
        assertThat(manager.getApiKey()).isEqualTo("cttak_manual_key")
    }

    @Test
    fun `should reject a manually pasted key without the cttak prefix`() {
        val result = manager.bindWithManualKey("not-an-api-key")

        assertThat(result).isInstanceOf(SyncResult.Failure::class.java)
        assertThat((result as SyncResult.Failure).error.kind).isEqualTo(SyncErrorKind.VALIDATION_ERROR)
        assertThat((result as SyncResult.Failure).error.toUserMessage()).contains("cttak_")
        assertThat(vault.stored).isNull()
        assertThat(manager.isBound()).isFalse()
    }

    @Test
    fun `should unbind and reset settings`() {
        manager.bindWithManualKey("cttak_key")
        assertThat(manager.isBound()).isTrue()

        manager.unbind()

        assertThat(vault.stored).isNull()
        assertThat(settings.apiKeyPrefix).isNull()
        assertThat(settings.syncEnabled).isFalse()
        assertThat(manager.isBound()).isFalse()
    }

    private class InMemorySyncKeyVault : SyncKeyVault {
        var stored: String? = null
        override fun save(rawKey: String) {
            stored = rawKey
        }

        override fun load(): String? = stored

        override fun clear() {
            stored = null
        }
    }
}
