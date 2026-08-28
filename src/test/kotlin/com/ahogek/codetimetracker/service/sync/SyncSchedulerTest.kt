package com.ahogek.codetimetracker.service.sync

import com.ahogek.codetimetracker.database.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class SyncSchedulerTest {

    private lateinit var connectionManager: ConnectionManager
    private lateinit var migrationManager: MigrationManager
    private lateinit var sessionRepository: SessionRepository
    private lateinit var cursorRepository: SyncCursorRepository
    private lateinit var settings: SyncSettingsState
    private lateinit var api: BlockingApi
    private lateinit var coordinator: SyncCoordinator
    private lateinit var scheduler: SyncScheduler
    private lateinit var testDbPath: Path

    @BeforeEach
    fun setUp(@TempDir tempDir: Path) {
        testDbPath = tempDir.resolve("test.db")
        connectionManager = ConnectionManager()
        connectionManager.setConnectionFactory(
            DriverManagerConnectionFactory("jdbc:sqlite:${testDbPath}"),
            "jdbc:sqlite:${testDbPath}",
        )
        migrationManager = MigrationManager(connectionManager)
        migrationManager.migrate()
        sessionRepository = SessionRepository(connectionManager)
        cursorRepository = SyncCursorRepository(connectionManager)
        settings = SyncSettingsState().apply {
            syncEnabled = true
            serverUrl = "http://localhost:8080/ctt-server"
        }
        api = BlockingApi()
        coordinator = SyncCoordinator(
            settings = settings,
            keyManager = SyncApiKeyManager(settings).apply {
                vault = InMemoryVault()
                bindWithManualKey("cttak_test-key")
            },
            api = api,
            cursorRepository = cursorRepository,
            sessionRepository = sessionRepository,
        )
        scheduler = SyncScheduler(settings, coordinator)
    }

    @AfterEach
    fun tearDown() {
        scheduler.dispose()
        connectionManager.shutdown()
    }

    @Test
    fun `syncNow should run a sync round on the background executor`() {
        scheduler.syncNow()

        // A completed round performs two pulls (initial + post-push reconcile).
        await(api.pullCalls, 2)
        assertThat(api.pullCalls.get()).isEqualTo(2)
    }

    @Test
    fun `reschedule should not arm a timer when sync is disabled`() {
        settings.syncEnabled = false
        settings.syncIntervalMinutes = 1

        scheduler.reschedule()

        assertThat(scheduler.isPeriodicActive()).isFalse()
    }

    @Test
    fun `reschedule should not arm a timer when the interval is zero`() {
        settings.syncIntervalMinutes = 0

        scheduler.reschedule()

        assertThat(scheduler.isPeriodicActive()).isFalse()
    }

    @Test
    fun `reschedule should arm a timer when enabled with a positive interval`() {
        settings.syncEnabled = true
        settings.syncIntervalMinutes = 5

        scheduler.reschedule()

        assertThat(scheduler.isPeriodicActive()).isTrue()
    }

    private fun await(counter: AtomicInteger, expected: Int) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (counter.get() < expected && System.nanoTime() < deadline) {
            Thread.sleep(20)
        }
        assertThat(counter.get()).isEqualTo(expected)
    }

    private class BlockingApi : FakeApiBase() {
        val pullCalls = AtomicInteger(0)

        override fun pull(request: SyncPullRequest, apiKey: String): SyncResult<SyncPullResponse> {
            pullCalls.incrementAndGet()
            return SyncResult.Success(SyncPullResponse(changes = emptyList(), nextCursor = 0))
        }
    }

    private open class FakeApiBase : SyncApiService {
        override fun pingServer(): SyncResult<Unit> = SyncResult.Success(Unit)
        override fun listDevices(apiKey: String): SyncResult<List<DeviceResponse>> = SyncResult.Success(emptyList())
        override fun registerDevice(request: RegisterDeviceRequest, apiKey: String): SyncResult<DeviceResponse> =
            SyncResult.Success(DeviceResponse())

        override fun pull(request: SyncPullRequest, apiKey: String): SyncResult<SyncPullResponse> =
            SyncResult.Success(SyncPullResponse())

        override fun push(request: SyncPushRequest, apiKey: String): SyncResult<SyncPushResponse> =
            SyncResult.Success(SyncPushResponse())
    }

    private class InMemoryVault : SyncKeyVault {
        private var key: String? = null
        override fun save(rawKey: String) {
            key = rawKey
        }

        override fun load(): String? = key

        override fun clear() {
            key = null
        }
    }
}
