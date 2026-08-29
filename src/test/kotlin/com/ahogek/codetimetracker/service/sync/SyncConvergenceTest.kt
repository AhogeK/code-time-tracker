package com.ahogek.codetimetracker.service.sync

import com.ahogek.codetimetracker.database.ConnectionManager
import com.ahogek.codetimetracker.database.DriverManagerConnectionFactory
import com.ahogek.codetimetracker.database.MigrationManager
import com.ahogek.codetimetracker.database.SessionRepository
import com.ahogek.codetimetracker.model.CodingSession
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.Instant
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * End-to-end convergence test across two independent devices sharing one server.
 *
 * <p>Each device owns a separate SQLite database, its own pull cursor and a full
 * [syncOnce] round that mirrors [SyncCoordinator.doSyncOnce]: pull and apply, push the
 * dirty sessions and mark them synced, then pull again so the store converges on the
 * server-authoritative state. Both devices talk to a single [InMemorySyncServer] that
 * reproduces the ctt-server change log and last-write-wins conflict resolution (delete
 * wins first, then server version, then client version, then modified-at).
 *
 * <p>Scenarios cover the E1 acceptance criteria: device A creates a session, device B
 * pulls it back; device B edits the same session and device A converges on the edit; a
 * server-side delete converges on both devices; and a true LWW conflict (both devices
 * edit the same session concurrently) resolves to a single row with a consistent
 * version on both sides.
 */
class SyncConvergenceTest {

    private val connectionManagers = mutableListOf<ConnectionManager>()

    @AfterEach
    fun tearDown() {
        connectionManagers.forEach { it.shutdown() }
        connectionManagers.clear()
    }

    /**
     * A device = its own SQLite store, repository and pull cursor. Mirrors one IDE
     * installation, including the full pull-push-pull round.
     */
    private inner class Device(
        private val connectionManager: ConnectionManager,
        val name: String,
    ) {
        val repository = SessionRepository(connectionManager)
        private var cursor: Long = 0L

        /** Inserts a locally created, not-yet-synced session (dirty). */
        fun recordLocalSession(session: CodingSession) {
            repository.importSessions(listOf(session))
        }

        /** Simulates the user editing an existing session: rewrites the row as dirty. */
        fun editSession(
            uuid: String,
            projectName: String,
            syncVersion: Int,
            lastModified: LocalDateTime,
        ) {
            val formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME
            connectionManager.withConnection { conn ->
                conn.prepareStatement(
                    """
                    UPDATE coding_sessions
                    SET project_name = ?, is_synced = 0, sync_version = ?, last_modified = ?
                    WHERE session_uuid = ?
                    """.trimIndent(),
                ).use { pstmt ->
                    pstmt.setString(1, projectName)
                    pstmt.setInt(2, syncVersion)
                    pstmt.setString(3, formatter.format(lastModified))
                    pstmt.setString(4, uuid)
                    pstmt.executeUpdate()
                }
            }
        }

        /**
         * One full sync round, mirroring [SyncCoordinator.doSyncOnce]: pull and apply,
         * push dirty sessions and mark them synced, then pull again for convergence.
         */
        fun syncOnce(server: SyncApiService) {
            // Pull and apply.
            val pull1 = server.pull(SyncPullRequest(deviceId = name, lastPulledChangeId = cursor), apiKey = "key")
            val changes1 = (pull1 as SyncResult.Success).data.changes
            SyncSessionApplier(repository).apply(
                changes1,
                userId = "shared-user",
                localPlatform = "macOS",
                localIdeName = "IntelliJ IDEA",
                ownerUserId = "shared-user",
            )
            cursor = (pull1 as SyncResult.Success).data.nextCursor

            // Push dirty sessions and mark them synced.
            val dirty = repository.getDirtySessions()
            if (dirty.isNotEmpty()) {
                val push = server.push(
                    SyncPushRequest(deviceId = name, sessions = dirty.map { SyncSessionMapper.toSyncDto(it) }),
                    apiKey = "key",
                )
                assertThat(push).isInstanceOf(SyncResult.Success::class.java)
                repository.markSynced(dirty.map { it.sessionUuid }, "shared-user")
            }

            // Pull again so this device converges on its own and concurrent writes.
            val pull2 = server.pull(SyncPullRequest(deviceId = name, lastPulledChangeId = cursor), apiKey = "key")
            SyncSessionApplier(repository).apply(
                (pull2 as SyncResult.Success).data.changes,
                userId = "shared-user",
                localPlatform = "macOS",
                localIdeName = "IntelliJ IDEA",
                ownerUserId = "shared-user",
            )
            cursor = (pull2 as SyncResult.Success).data.nextCursor
        }

        fun hasSession(uuid: String): Boolean =
            repository.findBySessionUuids(listOf(uuid)).containsKey(uuid)

        fun session(uuid: String): CodingSession? =
            repository.findBySessionUuids(listOf(uuid))[uuid]
    }

    private fun openDevice(tempDir: Path, name: String): Device {
        val dbPath = tempDir.resolve("$name.db")
        val connectionManager = ConnectionManager()
        connectionManager.setConnectionFactory(
            DriverManagerConnectionFactory("jdbc:sqlite:${dbPath}"),
            "jdbc:sqlite:${dbPath}",
        )
        MigrationManager(connectionManager).migrate()
        connectionManagers.add(connectionManager)
        return Device(connectionManager, name)
    }

    private fun session(
        uuid: String,
        project: String = "ctt-server",
        syncVersion: Int = 1,
        lastModified: LocalDateTime = LocalDateTime.of(2026, 8, 25, 10, 0),
    ) = CodingSession(
        sessionUuid = uuid,
        userId = "shared-user",
        projectName = project,
        language = "Kotlin",
        platform = "macOS",
        ideName = "IntelliJ IDEA",
        startTime = LocalDateTime.of(2026, 8, 25, 9, 0),
        endTime = LocalDateTime.of(2026, 8, 25, 10, 0),
        lastModified = lastModified,
        isSynced = false,
        syncVersion = syncVersion,
    )

    /**
     * In-memory replica of the ctt-server sync backend: a monotonic change log plus
     * LWW conflict resolution. Push applies the winning state (delete first, then
     * server version, then client version, then modified-at) and appends a change
     * entry; pull returns the changes after the caller's cursor.
     */
    private class InMemorySyncServer : SyncApiService {

        data class ServerState(
            var serverVersion: Long,
            var clientVersion: Int,
            var clientModifiedAt: LocalDateTime,
            var deleted: Boolean,
            var projectName: String,
            var language: String,
            var startTime: String,
            var endTime: String,
        )

        private val sessions = mutableMapOf<String, ServerState>()
        private val changes = mutableListOf<SyncChangeDto>()
        private var nextChangeId = 1L

        override fun pingServer(): SyncResult<Unit> = SyncResult.Success(Unit)

        override fun listDevices(apiKey: String): SyncResult<List<DeviceResponse>> =
            SyncResult.Success(emptyList())

        override fun registerDevice(request: RegisterDeviceRequest, apiKey: String): SyncResult<DeviceResponse> =
            SyncResult.Success(DeviceResponse())

        override fun currentUser(apiKey: String): SyncResult<CurrentUserResponse> =
            SyncResult.Success(CurrentUserResponse(id = "shared-user"))

        override fun pull(request: SyncPullRequest, apiKey: String): SyncResult<SyncPullResponse> {
            val applicable = changes.filter { it.changeId > request.lastPulledChangeId }
            return SyncResult.Success(
                SyncPullResponse(
                    changes = applicable,
                    nextCursor = changes.lastOrNull()?.changeId ?: 0L,
                ),
            )
        }

        override fun push(request: SyncPushRequest, apiKey: String): SyncResult<SyncPushResponse> {
            for (dto in request.sessions) {
                val uuid = dto.sessionUuid ?: continue
                val existing = sessions[uuid]
                val incoming = ServerState(
                    serverVersion = existing?.serverVersion ?: 0L,
                    clientVersion = dto.clientVersion,
                    clientModifiedAt = parseInstant(dto.clientModifiedAt),
                    deleted = dto.deleted,
                    projectName = dto.projectName.orEmpty(),
                    language = dto.language.orEmpty(),
                    startTime = dto.startTime.orEmpty(),
                    endTime = dto.endTime.orEmpty(),
                )

                val incomingWins = when {
                    existing == null -> true
                    // Delete wins over a live row; a live row wins over a deleted one.
                    existing.deleted != incoming.deleted -> incoming.deleted
                    // Higher server version wins (both were persisted before).
                    existing.serverVersion > 0 && incoming.serverVersion > 0 &&
                        existing.serverVersion != incoming.serverVersion ->
                        incoming.serverVersion > existing.serverVersion
                    // Higher client version wins.
                    existing.clientVersion != incoming.clientVersion ->
                        incoming.clientVersion > existing.clientVersion
                    // Later modified-at wins.
                    existing.clientModifiedAt != incoming.clientModifiedAt ->
                        incoming.clientModifiedAt.isAfter(existing.clientModifiedAt)
                    // Identical state: keep the server row (idempotent re-submission).
                    else -> false
                }

                if (!incomingWins) continue

                val nextServerVersion = (existing?.serverVersion ?: 0L) + 1
                sessions[uuid] = incoming.copy(serverVersion = nextServerVersion)
                changes.add(
                    SyncChangeDto(
                        changeId = nextChangeId++,
                        sessionUuid = uuid,
                        op = if (incoming.deleted) ChangeOp.DELETE else ChangeOp.UPSERT,
                        serverVersion = nextServerVersion,
                        happenedAt = Instant.now().toString(),
                        projectName = incoming.projectName,
                        language = incoming.language,
                        startTime = incoming.startTime,
                        endTime = incoming.endTime,
                        clientModifiedAt = incoming.clientModifiedAt.toString(),
                        clientVersion = incoming.clientVersion,
                        deleted = incoming.deleted,
                    ),
                )
            }
            return SyncResult.Success(SyncPushResponse(nextCursor = changes.lastOrNull()?.changeId ?: 0L))
        }

        private fun parseInstant(value: String?): LocalDateTime =
            value?.let {
                runCatching { Instant.parse(it).atZone(java.time.ZoneId.systemDefault()).toLocalDateTime() }.getOrNull()
            } ?: LocalDateTime.MIN

        /** Server-side soft delete: simulates a delete performed outside the plugin. */
        fun deleteRemote(uuid: String) {
            val existing = sessions[uuid] ?: return
            val nextServerVersion = existing.serverVersion + 1
            sessions[uuid] = existing.copy(serverVersion = nextServerVersion, deleted = true)
            changes.add(
                SyncChangeDto(
                    changeId = nextChangeId++,
                    sessionUuid = uuid,
                    op = ChangeOp.DELETE,
                    serverVersion = nextServerVersion,
                    happenedAt = Instant.now().toString(),
                    deleted = true,
                ),
            )
        }
    }

    // ---------------------------------------------------------------------------
    // E1: device A creates a session, device B pulls it back
    // ---------------------------------------------------------------------------

    @Test
    fun `should converge a session created on device A onto device B`(@TempDir tempDir: Path) {
        val server = InMemorySyncServer()
        val deviceA = openDevice(tempDir, "deviceA")
        val deviceB = openDevice(tempDir, "deviceB")

        // Device A records a local session and syncs it up.
        deviceA.recordLocalSession(session(uuid = "s-1"))
        deviceA.syncOnce(server)

        // Device B syncs and pulls the session back.
        deviceB.syncOnce(server)

        val onDeviceB = deviceB.session("s-1")
        assertThat(onDeviceB).isNotNull
        assertThat(onDeviceB!!.projectName).isEqualTo("ctt-server")
        assertThat(deviceA.hasSession("s-1")).isTrue()
    }

    // ---------------------------------------------------------------------------
    // E1: device B edits the same session, device A converges on the edit
    // ---------------------------------------------------------------------------

    @Test
    fun `should converge a session edited on device B back onto device A`(@TempDir tempDir: Path) {
        val server = InMemorySyncServer()
        val deviceA = openDevice(tempDir, "deviceA")
        val deviceB = openDevice(tempDir, "deviceB")

        // Seed the session on both devices via a full sync round.
        deviceA.recordLocalSession(session(uuid = "s-2"))
        deviceA.syncOnce(server)
        deviceB.syncOnce(server)
        assertThat(deviceB.hasSession("s-2")).isTrue()

        // Device B edits the session locally and syncs the edit (version 2, later time).
        deviceB.editSession("s-2", projectName = "renamed-project", syncVersion = 2, lastModified = LocalDateTime.of(2026, 8, 25, 11, 0))
        deviceB.syncOnce(server)

        // Device A syncs and converges on the edit.
        deviceA.syncOnce(server)

        val onDeviceA = deviceA.session("s-2")
        assertThat(onDeviceA).isNotNull
        assertThat(onDeviceA!!.projectName).isEqualTo("renamed-project")
        assertThat(onDeviceA.syncVersion).isEqualTo(2)
    }

    // ---------------------------------------------------------------------------
    // E1: server-side delete converges on both devices
    // ---------------------------------------------------------------------------

    @Test
    fun `should converge a server-side delete on both devices`(@TempDir tempDir: Path) {
        val server = InMemorySyncServer()
        val deviceA = openDevice(tempDir, "deviceA")
        val deviceB = openDevice(tempDir, "deviceB")

        // Seed: A pushes, both devices hold a clean row.
        deviceA.recordLocalSession(session(uuid = "s-3"))
        deviceA.syncOnce(server)
        deviceB.syncOnce(server)
        assertThat(deviceB.hasSession("s-3")).isTrue()

        // Server-side delete (e.g. removed in the web console).
        server.deleteRemote("s-3")

        // Device A syncs and soft-deletes; device B syncs and soft-deletes too.
        deviceA.syncOnce(server)
        deviceB.syncOnce(server)

        assertThat(deviceA.hasSession("s-3")).isFalse()
        assertThat(deviceB.hasSession("s-3")).isFalse()
    }

    // ---------------------------------------------------------------------------
    // E1: true LWW conflict — both devices edit the same session concurrently
    // ---------------------------------------------------------------------------

    @Test
    fun `should resolve a concurrent edit conflict to a single consistent row`(@TempDir tempDir: Path) {
        val server = InMemorySyncServer()
        val deviceA = openDevice(tempDir, "deviceA")
        val deviceB = openDevice(tempDir, "deviceB")

        // Seed: both devices hold the same clean session.
        deviceA.recordLocalSession(session(uuid = "s-4"))
        deviceA.syncOnce(server)
        deviceB.syncOnce(server)

        // Both devices edit the same session concurrently; B's edit is modified later,
        // so the server must resolve to B.
        deviceA.editSession("s-4", projectName = "edit-from-A", syncVersion = 1, lastModified = LocalDateTime.of(2026, 8, 25, 12, 0))
        deviceB.editSession("s-4", projectName = "edit-from-B", syncVersion = 1, lastModified = LocalDateTime.of(2026, 8, 25, 13, 0))

        // Both push concurrently; the later modified-at (B, 13:00) must win.
        deviceA.syncOnce(server)
        deviceB.syncOnce(server)

        // Each device runs one more round and pulls the server-authoritative winner
        // back, exactly as periodic sync would do after a conflict.
        deviceA.syncOnce(server)
        deviceB.syncOnce(server)

        // Both converge on the single winning row.
        val onA = deviceA.session("s-4")
        val onB = deviceB.session("s-4")
        assertThat(onA).isNotNull
        assertThat(onB).isNotNull
        assertThat(onA!!.projectName).isEqualTo("edit-from-B")
        assertThat(onB!!.projectName).isEqualTo("edit-from-B")
    }
}
