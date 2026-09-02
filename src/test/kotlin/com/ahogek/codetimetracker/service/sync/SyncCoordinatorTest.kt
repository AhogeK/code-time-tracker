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
        coordinator = buildCoordinator()
    }

    private fun buildCoordinator(pushBatchSize: Int = 500): SyncCoordinator =
        SyncCoordinator(
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
            pushBatchSize = pushBatchSize,
        )

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
    fun `should re-register the device and retry when the server reports it revoked`() {
        val dirtyUuid = "dirty-1"
        sessionRepository.importSessions(listOf(localSession(dirtyUuid)))
        // First round: the initial pull reports the device revoked (404 COMMON_002).
        api.pullResponses.add(
            SyncResult.Failure(SyncError(SyncErrorKind.DEVICE_NOT_FOUND)),
        )
        // After re-registration the retried pull and the reconcile pull succeed.
        api.pullResponses.add(SyncResult.Success(SyncPullResponse(changes = emptyList(), nextCursor = 1)))
        api.pullResponses.add(SyncResult.Success(SyncPullResponse(changes = emptyList(), nextCursor = 1)))

        val result = coordinator.syncOnce()

        assertThat(result).isInstanceOf(SyncResult.Success::class.java)
        // The device was re-registered exactly once, then sync completed normally.
        assertThat(api.registerCalls).isEqualTo(1)
        assertThat(api.pushCalls).hasSize(1)
        assertThat(sessionRepository.getDirtySessions()).isEmpty()
    }

    @Test
    fun `should split a large dirty set into bounded push batches`() {
        val coordinator = buildCoordinator(pushBatchSize = 3)
        (1..7).forEach { sessionRepository.importSessions(listOf(localSession("b-$it"))) }
        api.pullResponses.add(SyncResult.Success(SyncPullResponse(changes = emptyList(), nextCursor = 1)))
        api.pullResponses.add(SyncResult.Success(SyncPullResponse(changes = emptyList(), nextCursor = 1)))

        val result = coordinator.syncOnce()

        assertThat(result).isInstanceOf(SyncResult.Success::class.java)
        // 7 sessions with batch size 3 => 3 pushes (3 + 3 + 1).
        assertThat(api.pushCalls).hasSize(3)
        assertThat(api.pushCalls[0].sessions).hasSize(3)
        assertThat(api.pushCalls[1].sessions).hasSize(3)
        assertThat(api.pushCalls[2].sessions).hasSize(1)
        // Every session was marked synced after its batch succeeded.
        assertThat(sessionRepository.getDirtySessions()).isEmpty()
    }

    @Test
    fun `should keep un-pushed batches dirty when a later batch fails`() {
        val coordinator = buildCoordinator(pushBatchSize = 3)
        (1..6).forEach { sessionRepository.importSessions(listOf(localSession("f-$it"))) }
        api.pullResponses.add(SyncResult.Success(SyncPullResponse(changes = emptyList(), nextCursor = 1)))
        // First batch succeeds, second batch fails.
        api.pushResponses.add(SyncResult.Success(SyncPushResponse(nextCursor = 3)))
        api.pushResponses.add(SyncResult.Failure(SyncError(SyncErrorKind.SERVER_ERROR)))

        val result = coordinator.syncOnce()

        assertThat(result).isInstanceOf(SyncResult.Failure::class.java)
        assertThat(api.pushCalls).hasSize(2)
        // First batch (f-1..f-3) marked synced; second batch (f-4..f-6) stays dirty.
        assertThat(sessionRepository.getDirtySessions().map { it.sessionUuid }).containsExactly("f-4", "f-5", "f-6")
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

    @Test
    fun `should drain multiple pull pages while the server reports hasMore`() {
        val firstUuid = "page-1-uuid"
        sessionRepository.importSessions(listOf(localSession(firstUuid)))
        // Initial pull: two pages. The reconcile pull: one empty page.
        api.pullResponses.add(
            SyncResult.Success(
                SyncPullResponse(
                    changes = listOf(pageChange(1, firstUuid, clientVersion = 2)),
                    nextCursor = 1,
                    hasMore = true,
                ),
            ),
        )
        api.pullResponses.add(
            SyncResult.Success(SyncPullResponse(changes = emptyList(), nextCursor = 4, hasMore = false)),
        )
        api.pullResponses.add(SyncResult.Success(SyncPullResponse(changes = emptyList(), nextCursor = 4)))
        api.pushResult = SyncResult.Success(SyncPushResponse(nextCursor = 4))

        val result = coordinator.syncOnce()

        assertThat(result).isInstanceOf(SyncResult.Success::class.java)
        // Three pulls drained the sequence; each request resumed from the previous
        // page's cursor.
        assertThat(api.pullCalls).hasSize(3)
        assertThat(api.pullCalls[1].lastPulledChangeId).isEqualTo(1L)
        assertThat(api.pullCalls[2].lastPulledChangeId).isEqualTo(4L)
        assertThat(cursorRepository.getPullCursor(UserManager.getUserId())).isEqualTo(4L)
    }

    @Test
    fun `should keep the cursor at the last applied page when a later page fails`() {
        val firstUuid = "page-1-uuid"
        sessionRepository.upsertSyncedSessions(listOf(localSession(firstUuid)))
        api.pullResponses.add(
            SyncResult.Success(
                SyncPullResponse(
                    changes = listOf(pageChange(1, firstUuid, clientVersion = 2)),
                    nextCursor = 1,
                    hasMore = true,
                ),
            ),
        )
        api.pullResponses.add(SyncResult.Failure(SyncError(SyncErrorKind.NETWORK_ERROR)))

        val result = coordinator.syncOnce()

        assertThat(result).isInstanceOf(SyncResult.Failure::class.java)
        // The first page was applied and its cursor persisted; the failed page did
        // not rewind or advance it.
        assertThat(cursorRepository.getPullCursor(UserManager.getUserId())).isEqualTo(1L)
        // The first page's upsert landed (the row was overwritten by the change).
        val applied = sessionRepository.findBySessionUuids(listOf(firstUuid))[firstUuid]
        assertThat(applied).isNotNull()
        assertThat(applied!!.syncVersion).isEqualTo(2)
        assertThat(api.pullCalls).hasSize(2)
    }

    @Test
    fun `should treat a missing hasMore field as a final page`() {
        val dirtyUuid = "dirty-1"
        sessionRepository.importSessions(listOf(localSession(dirtyUuid)))
        // Gson maps an absent hasMore (older server) to false: single final page.
        api.pullResponses.add(SyncResult.Success(SyncPullResponse(changes = emptyList(), nextCursor = 2)))
        api.pullResponses.add(SyncResult.Success(SyncPullResponse(changes = emptyList(), nextCursor = 2)))
        api.pushResult = SyncResult.Success(SyncPushResponse(nextCursor = 2))

        val result = coordinator.syncOnce()

        assertThat(result).isInstanceOf(SyncResult.Success::class.java)
        assertThat(api.pullCalls).hasSize(2)
        assertThat(cursorRepository.getPullCursor(UserManager.getUserId())).isEqualTo(2L)
    }

    @Test
    fun `should fail the round when the server reports more pages without advancing the cursor`() {
        val dirtyUuid = "dirty-1"
        sessionRepository.importSessions(listOf(localSession(dirtyUuid)))
        // A misbehaving server: hasMore = true with a non-advancing cursor.
        api.pullResponses.add(
            SyncResult.Success(SyncPullResponse(changes = emptyList(), nextCursor = 0, hasMore = true)),
        )

        val result = coordinator.syncOnce()

        assertThat(result).isInstanceOf(SyncResult.Failure::class.java)
        assertThat((result as SyncResult.Failure).error.message).contains("without advancing the cursor")
        // One pull, no pages applied, no push attempted.
        assertThat(api.pullCalls).hasSize(1)
        assertThat(api.pushCalls).isEmpty()
    }

    private fun pageChange(changeId: Long, sessionUuid: String, clientVersion: Int) = SyncChangeDto(
        changeId = changeId,
        sessionId = UUID.randomUUID().toString(),
        sessionUuid = sessionUuid,
        op = ChangeOp.UPSERT,
        serverVersion = 1,
        happenedAt = "2026-08-25T10:00:00Z",
        projectName = "ctt-server",
        language = "Java",
        startTime = "2026-08-25T09:00:00Z",
        endTime = "2026-08-25T10:00:00Z",
        clientModifiedAt = "2026-08-25T10:00:00Z",
        clientVersion = clientVersion,
    )

    private open class FakeApi : SyncApiService {
        val pullCalls = mutableListOf<SyncPullRequest>()
        val pushCalls = mutableListOf<SyncPushRequest>()
        var registerCalls = 0
        val pullResponses = ArrayDeque<SyncResult<SyncPullResponse>>()
        val pushResponses = ArrayDeque<SyncResult<SyncPushResponse>>()
        var pushResult: SyncResult<SyncPushResponse> = SyncResult.Success(SyncPushResponse())

        override fun pingServer(): SyncResult<Unit> = SyncResult.Success(Unit)

        override fun listDevices(apiKey: String): SyncResult<List<DeviceResponse>> =
            SyncResult.Success(emptyList())

        override fun registerDevice(request: RegisterDeviceRequest, apiKey: String): SyncResult<DeviceResponse> {
            registerCalls++
            return SyncResult.Success(DeviceResponse())
        }

        override fun pull(request: SyncPullRequest, apiKey: String): SyncResult<SyncPullResponse> {
            pullCalls.add(request)
            return pullResponses.removeFirstOrNull() ?: SyncResult.Success(SyncPullResponse())
        }

        override fun push(request: SyncPushRequest, apiKey: String): SyncResult<SyncPushResponse> {
            pushCalls.add(request)
            return pushResponses.removeFirstOrNull() ?: pushResult
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
