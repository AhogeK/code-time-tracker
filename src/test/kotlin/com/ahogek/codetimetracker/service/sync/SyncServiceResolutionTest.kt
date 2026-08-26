package com.ahogek.codetimetracker.service.sync

import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.LightPlatformTestCase
import org.assertj.core.api.Assertions.assertThat

/**
 * Regression guard for the platform-container constructor contract.
 *
 * Plain unit tests construct these services manually and never touch the IDE
 * service container, which is exactly how the constructor-signature failure
 * (custom parameters on [SyncApiKeyManager] / [SyncHttpClient] /
 * [SyncApiServiceImpl]) slipped through. This test resolves every sync service
 * through the real headless application container, so a constructor that the
 * platform cannot instantiate fails here.
 */
class SyncServiceResolutionTest : LightPlatformTestCase() {

    fun testAllSyncServicesResolveFromThePlatformContainer() {
        val app = ApplicationManager.getApplication()

        assertThat(app).isNotNull()
        assertThat(app.getService(SyncSettingsState::class.java)).isNotNull()
        assertThat(app.getService(SyncHttpClient::class.java)).isNotNull()
        assertThat(app.getService(SyncApiServiceImpl::class.java)).isNotNull()
        assertThat(app.getService(SyncApiKeyManager::class.java)).isNotNull()
    }
}
