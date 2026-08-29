package com.ahogek.codetimetracker.service.sync

import com.ahogek.codetimetracker.database.ConnectionManager
import com.ahogek.codetimetracker.database.DriverManagerConnectionFactory
import com.ahogek.codetimetracker.database.MigrationManager
import com.ahogek.codetimetracker.database.SessionRepository
import com.ahogek.codetimetracker.database.SyncCursorRepository
import com.ahogek.codetimetracker.model.CodingSession
import com.ahogek.codetimetracker.user.UserManager
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.LocalDateTime
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class SyncCoordinatorTest {

    private lateinit var connectionManager: ConnectionManager
    private lateinit var migrationManager: MigrationManager
    private lateinit var sessionRepository: SessionRepository
    private lateinit var cursorRepository: SyncCursorRepository
    private lateinit var settings: SyncSettingsState
    private lateinit var keyManager: SyncApiKeyManager
    private lateinit var api: FakeApi
    private lateinit var coordinator: SyncCoordinator
    private lateinit var testDbPath: Path

    @BeforeEach
    fun setUp(@TempDir tempDir: Path) {
        // Pin the installation id so sync tests neither read the real shared database
        // nor require the IDE application container (see UserManager.setUserIdForTest).
        UserManager.setUserIdForTest("test-device")
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
        keyManager = SyncApiKeyManager(settings).apply {
            vault = InMemoryVault()
            bindWithManualKey("cttak_test-key")
        }
        api = FakeApi()
        coordinator = SyncCoordinator(
            settings = settings,
            keyManager = keyManager,
            api = api,
            cursorRepository = cursorRepository,
            sessionRepository = sessionRepository,
            applier = SyncSessionApplier(sessionRepository),
            deviceMetadataProvider = {
                RegisterDeviceRequest(deviceName = "test", platform = "macOS", ideName = "IntelliJ IDEA")
            },
            notifySyncCompleted = {},
        )
    }

    @AfterEach
    fun tearDown() {
        connectionManager.shutdown()
    }

    private fun localSession(sessionUuid: String, isSynced: Boolean = false): CodingSession = CodingSession(
        sessionUuid = sessionUuid,
        userId = "test-user",
        projectName = "ctt-server",
        language = "Java",
        platform = "macOS",
        ideName = "IntelliJ IDEA",
        startTime = LocalDateTime.of(2026, 8, 25, 9, 0),
        endTime = LocalDateTime.of(2026, 8, 25, 10, 0),
        lastModified = LocalDateTime.of(2026, 8, 25, 10, 0),
        isSynced = isSynced,
        syncVersion = 1,
    )

    @Test
    fun `should skip sync when synchronization is disabled`() {
        settings.syncEnabled = false

        val result = coordinator.syncOnce()

        assertThat(result).isInstanceOf(SyncResult.Success::class.java)
        assertThat(api.pullCalls).isEmpty()
        assertThat(api.pushCalls).isEmpty()
    }

    @Test
    fun `should skip sync when no api key is bound`() {
        keyManager.unbind()

        val result = coordinator.syncOnce()

        assertThat(result).isInstanceOf(SyncResult.Success::class.java)
        assertThat(api.pullCalls).isEmpty()
    }

    @Test
    fun `should pull apply push then pull again on a happy path`() {
        val dirtyUuid = "dirty-1"
        sessionRepository.importSessions(listOf(localSession(dirtyUuid)))
        // First pull: nothing remote. Second pull: returns this device's own change.
        api.pullResponses.add(
            SyncResult.Success(SyncPullResponse(changes = emptyList(), nextCursor = 0)),
        )
        api.pullResponses.add(
            SyncResult.Success(
                SyncPullResponse(
                    changes = listOf(
                        SyncChangeDto(
                            changeId = 7,
                            sessionId = UUID.randomUUID().toString(),
                            sessionUuid = dirtyUuid,
                            op = ChangeOp.UPSERT,
                            serverVersion = 1,
                            happenedAt = "2026-08-25T10:00:00Z",
                            projectName = "ctt-server",
                            language = "Java",
                            startTime = "2026-08-25T09:00:00Z",
                            endTime = "2026-08-25T10:00:00Z",
                            clientModifiedAt = "2026-08-25T10:00:00Z",
                            clientVersion = 1,
                        ),
                    ),
                    nextCursor = 7,
                ),
            ),
        )
        api.pushResult = SyncResult.Success(SyncPushResponse(nextCursor = 7))

        val result = coordinator.syncOnce()

        assertThat(result).isInstanceOf(SyncResult.Success::class.java)
        // Push carried exactly the dirty session.
        assertThat(api.pushCalls).hasSize(1)
        assertThat(api.pushCalls[0].sessions.map { it.sessionUuid }).containsExactly(dirtyUuid)
        // Dirty session is now clean.
        assertThat(sessionRepository.getDirtySessions()).isEmpty()
        // Cursor persisted from the second pull (device id is the installation id).
        assertThat(cursorRepository.getPullCursor(UserManager.getUserId())).isEqualTo(7L)
        // Two pulls: initial + post-push convergence.
        assertThat(api.pullCalls).hasSize(2)
        assertThat(api.pullCalls[0].lastPulledChangeId).isEqualTo(0L)
        assertThat(api.pullCalls[1].lastPulledChangeId).isEqualTo(0L)
    }

    @Test
    fun `should keep the cursor and dirty markers when the first pull fails`() {
        val dirtyUuid = "dirty-1"
        val device = UserManager.getUserId()
        sessionRepository.importSessions(listOf(localSession(dirtyUuid)))
        cursorRepository.setPullCursor(device, device, 5L)
        api.pullResponses.add(
            SyncResult.Failure(SyncError(SyncErrorKind.NETWORK_ERROR)),
        )

        val result = coordinator.syncOnce()

        assertThat(result).isInstanceOf(SyncResult.Failure::class.java)
        assertThat(cursorRepository.getPullCursor(device)).isEqualTo(5L)
        assertThat(sessionRepository.getDirtySessions().map { it.sessionUuid }).containsExactly(dirtyUuid)
        assertThat(api.pushCalls).isEmpty()
    }

    @Test
    fun `should retry successfully after a network failure and converge`() {
        val dirtyUuid = "dirty-1"
        sessionRepository.importSessions(listOf(localSession(dirtyUuid)))
        // First round hits a network error during the initial pull.
        api.pullResponses.add(
            SyncResult.Failure(SyncError(SyncErrorKind.NETWORK_ERROR)),
        )

        val failed = coordinator.syncOnce()
        assertThat(failed).isInstanceOf(SyncResult.Failure::class.java)
        assertThat(sessionRepository.getDirtySessions().map { it.sessionUuid }).containsExactly(dirtyUuid)

        // Network recovers: the next round pulls empty changes, pushes the still-dirty
        // session and marks it synced, so no data is lost.
        api.pullResponses.add(SyncResult.Success(SyncPullResponse(changes = emptyList(), nextCursor = 3)))
        api.pullResponses.add(SyncResult.Success(SyncPullResponse(changes = emptyList(), nextCursor = 3)))
        val recovered = coordinator.syncOnce()

        assertThat(recovered).isInstanceOf(SyncResult.Success::class.java)
        assertThat(sessionRepository.getDirtySessions()).isEmpty()
        assertThat(api.pushCalls).hasSize(1)
        assertThat(api.pushCalls[0].sessions.map { it.sessionUuid }).contains(dirtyUuid)
    }

    @Test
    fun `should keep dirty markers when the push fails`() {
        val dirtyUuid = "dirty-1"
        sessionRepository.importSessions(listOf(localSession(dirtyUuid)))
        api.pullResponses.add(SyncResult.Success(SyncPullResponse(changes = emptyList(), nextCursor = 3)))
        api.pushResult = SyncResult.Failure(SyncError(SyncErrorKind.SERVER_ERROR))

        val result = coordinator.syncOnce()

        assertThat(result).isInstanceOf(SyncResult.Failure::class.java)
        assertThat(sessionRepository.getDirtySessions().map { it.sessionUuid }).containsExactly(dirtyUuid)
    }

    @Test
    fun `should skip a second round while one is in progress`() {
        val holdFirst = CountDownLatch(1)
        val releaseFirst = CountDownLatch(1)
        val blockingApi = object : FakeApi() {
            override fun pull(request: SyncPullRequest, apiKey: String): SyncResult<SyncPullResponse> {
                pullCalls.add(request)
                holdFirst.countDown()
                releaseFirst.await(5, TimeUnit.SECONDS)
                return SyncResult.Success(SyncPullResponse(changes = emptyList(), nextCursor = 0))
            }
        }
        val blockingCoordinator = SyncCoordinator(
            settings = settings,
            keyManager = keyManager,
            api = blockingApi,
            cursorRepository = cursorRepository,
            sessionRepository = sessionRepository,
            deviceMetadataProvider = {
                RegisterDeviceRequest(deviceName = "test", platform = "macOS", ideName = "IntelliJ IDEA")
            },
            notifySyncCompleted = {},
        )

        val first = Thread { blockingCoordinator.syncOnce() }.also { it.start() }
        holdFirst.await(5, TimeUnit.SECONDS)
        val second = blockingCoordinator.syncOnce()
        releaseFirst.countDown()
        first.join(5_000)

        // The overlapping trigger is a no-op, not a second round. One round performs two
        // pulls (initial + post-push reconcile), so exactly two pulls means one round ran.
        assertThat(second).isInstanceOf(SyncResult.Success::class.java)
        assertThat(blockingApi.pullCalls).hasSize(2)
    }

    @Test
    fun `resetForUserSwitch should clear the cursor and mark local sessions synced`() {
        val dirtyUuid = "dirty-1"
        val device = UserManager.getUserId()
        sessionRepository.importSessions(listOf(localSession(dirtyUuid)))
        cursorRepository.setPullCursor(device, device, 42L)

        coordinator.resetForUserSwitch()

        assertThat(cursorRepository.getPullCursor(device)).isEqualTo(0L)
        assertThat(sessionRepository.getDirtySessions()).isEmpty()
    }

    private open class FakeApi : SyncApiService {
        val pullCalls = mutableListOf<SyncPullRequest>()
        val pushCalls = mutableListOf<SyncPushRequest>()
        val pullResponses = ArrayDeque<SyncResult<SyncPullResponse>>()
        var pushResult: SyncResult<SyncPushResponse> = SyncResult.Success(SyncPushResponse())

        override fun pingServer(): SyncResult<Unit> = SyncResult.Success(Unit)

        override fun listDevices(apiKey: String): SyncResult<List<DeviceResponse>> =
            SyncResult.Success(emptyList())

        override fun registerDevice(request: RegisterDeviceRequest, apiKey: String): SyncResult<DeviceResponse> =
            SyncResult.Success(DeviceResponse())

        override fun pull(request: SyncPullRequest, apiKey: String): SyncResult<SyncPullResponse> {
            pullCalls.add(request)
            return pullResponses.removeFirstOrNull() ?: SyncResult.Success(SyncPullResponse())
        }

        override fun push(request: SyncPushRequest, apiKey: String): SyncResult<SyncPushResponse> {
            pushCalls.add(request)
            return pushResult
        }

        override fun currentUser(apiKey: String): SyncResult<CurrentUserResponse> =
            SyncResult.Success(CurrentUserResponse(id = "user-1"))
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
