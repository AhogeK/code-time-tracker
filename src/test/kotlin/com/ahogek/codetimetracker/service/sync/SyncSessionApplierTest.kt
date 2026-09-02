package com.ahogek.codetimetracker.service.sync

import com.ahogek.codetimetracker.database.ConnectionManager
import com.ahogek.codetimetracker.database.DriverManagerConnectionFactory
import com.ahogek.codetimetracker.database.MigrationManager
import com.ahogek.codetimetracker.database.SessionRepository
import com.ahogek.codetimetracker.model.CodingSession
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.LocalDateTime
import java.util.UUID

class SyncSessionApplierTest {

    private lateinit var connectionManager: ConnectionManager
    private lateinit var migrationManager: MigrationManager
    private lateinit var sessionRepository: SessionRepository
    private lateinit var applier: SyncSessionApplier
    private lateinit var testDbPath: Path

    private val userId = "test-user"

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
        applier = SyncSessionApplier(sessionRepository)
    }

    @AfterEach
    fun tearDown() {
        connectionManager.shutdown()
    }

    private fun change(
        sessionUuid: String?,
        op: ChangeOp,
        clientVersion: Int = 1,
        clientModifiedAt: String = "2026-08-25T10:00:00Z",
        deleted: Boolean = false,
        projectName: String = "ctt-server",
    ) = SyncChangeDto(
        changeId = 1,
        sessionId = UUID.randomUUID().toString(),
        sessionUuid = sessionUuid,
        op = op,
        serverVersion = 1,
        happenedAt = "2026-08-25T10:00:00Z",
        projectName = projectName,
        language = "Java",
        startTime = "2026-08-25T09:00:00Z",
        endTime = "2026-08-25T10:00:00Z",
        clientModifiedAt = clientModifiedAt,
        clientVersion = clientVersion,
        deleted = deleted,
    )

    private fun localSession(
        sessionUuid: String,
        isSynced: Boolean = true,
        projectName: String = "local-project",
    ): CodingSession = CodingSession(
        sessionUuid = sessionUuid,
        userId = userId,
        projectName = projectName,
        language = "Kotlin",
        platform = "macOS",
        ideName = "IntelliJ IDEA",
        startTime = LocalDateTime.of(2026, 8, 25, 9, 0),
        endTime = LocalDateTime.of(2026, 8, 25, 10, 0),
        lastModified = LocalDateTime.of(2026, 8, 25, 10, 0),
        isSynced = isSynced,
        syncVersion = 1,
    )

    @Test
    fun `should create a local session when an upsert change has no local row`() {
        applier.apply(listOf(change("uuid-new", ChangeOp.UPSERT)), userId, "macOS", "IntelliJ IDEA")

        val sessions = sessionRepository.getSessions()
        assertThat(sessions).hasSize(1)
        assertThat(sessions[0].sessionUuid).isEqualTo("uuid-new")
        assertThat(sessions[0].projectName).isEqualTo("ctt-server")
        assertThat(sessions[0].language).isEqualTo("Java")
        assertThat(sessions[0].isSynced).isTrue()
        assertThat(sessions[0].syncVersion).isEqualTo(1)
    }

    @Test
    fun `should record local platform and ide name when creating a session`() {
        applier.apply(listOf(change("uuid-new", ChangeOp.UPSERT)), userId, "Linux", "PyCharm")

        val sessions = sessionRepository.getSessions()
        assertThat(sessions[0].platform).isEqualTo("Linux")
        assertThat(sessions[0].ideName).isEqualTo("PyCharm")
    }

    @Test
    fun `should overwrite a clean local row with the change snapshot`() {
        val uuid = "uuid-clean"
        sessionRepository.upsertSyncedSessions(listOf(localSession(uuid)))

        applier.apply(listOf(change(uuid, ChangeOp.UPSERT, clientVersion = 3)), userId, "macOS", "IntelliJ IDEA")

        val sessions = sessionRepository.getSessions()
        assertThat(sessions).hasSize(1)
        assertThat(sessions[0].projectName).isEqualTo("ctt-server")
        assertThat(sessions[0].syncVersion).isEqualTo(3)
        assertThat(sessions[0].isSynced).isTrue()
    }

    @Test
    fun `should not overwrite a dirty local row`() {
        val uuid = "uuid-dirty"
        sessionRepository.importSessions(listOf(localSession(uuid, isSynced = false)))

        applier.apply(listOf(change(uuid, ChangeOp.UPSERT, projectName = "remote")), userId, "macOS", "IntelliJ IDEA")

        val sessions = sessionRepository.getSessions()
        assertThat(sessions).hasSize(1)
        assertThat(sessions[0].projectName).isEqualTo("local-project")
        assertThat(sessions[0].isSynced).isFalse()
    }

    @Test
    fun `should soft-delete a clean local row on a delete change`() {
        val uuid = "uuid-del"
        sessionRepository.upsertSyncedSessions(listOf(localSession(uuid)))

        applier.apply(listOf(change(uuid, ChangeOp.DELETE)), userId, "macOS", "IntelliJ IDEA")

        assertThat(sessionRepository.getSessions()).isEmpty()
        assertThat(sessionRepository.findBySessionUuids(listOf(uuid))).isEmpty()
    }

    @Test
    fun `should keep a dirty local row on a delete change`() {
        val uuid = "uuid-del-dirty"
        sessionRepository.importSessions(listOf(localSession(uuid, isSynced = false)))

        applier.apply(listOf(change(uuid, ChangeOp.DELETE)), userId, "macOS", "IntelliJ IDEA")

        val sessions = sessionRepository.getSessions()
        assertThat(sessions).hasSize(1)
        assertThat(sessions[0].isSynced).isFalse()
    }

    @Test
    fun `should keep existing timestamps when a change carries unparsable times`() {
        val uuid = "uuid-bad-time"
        val original = localSession(uuid)
        sessionRepository.upsertSyncedSessions(listOf(original))
        val badChange = SyncChangeDto(
            changeId = 1,
            sessionId = UUID.randomUUID().toString(),
            sessionUuid = uuid,
            op = ChangeOp.UPSERT,
            serverVersion = 1,
            happenedAt = "not-an-instant",
            projectName = "ctt-server",
            language = "Java",
            startTime = "not-an-instant",
            endTime = "not-an-instant",
            clientModifiedAt = "not-an-instant",
            clientVersion = 2,
        )

        applier.apply(listOf(badChange), userId, "macOS", "IntelliJ IDEA")

        val sessions = sessionRepository.getSessions()
        assertThat(sessions).hasSize(1)
        assertThat(sessions[0].lastModified).isEqualTo(original.lastModified)
        assertThat(sessions[0].startTime).isEqualTo(original.startTime)
        assertThat(sessions[0].endTime).isEqualTo(original.endTime)
    }

    @Test
    fun `should ignore changes without a session uuid`() {
        applier.apply(listOf(change(null, ChangeOp.UPSERT)), userId, "macOS", "IntelliJ IDEA")

        assertThat(sessionRepository.getSessions()).isEmpty()
    }

    @Test
    fun `should apply a page of upserts in one batch preserving change-log order`() {
        val upsertA = change("uuid-batch-a", ChangeOp.UPSERT, clientVersion = 2)
        val upsertB = change("uuid-batch-b", ChangeOp.UPSERT, clientVersion = 2)
        sessionRepository.upsertSyncedSessions(
            listOf(localSession("uuid-batch-a"), localSession("uuid-batch-b")),
        )

        applier.apply(listOf(upsertA, upsertB), userId, "macOS", "IntelliJ IDEA", ownerUserId = "owner-1")

        val rows = sessionRepository.findBySessionUuids(listOf("uuid-batch-a", "uuid-batch-b"))
        assertThat(rows).hasSize(2)
        assertThat(rows.values.map { it.syncVersion }).containsExactly(2, 2)
        // Both rows stay active (batch upsert must not soft-delete or drop rows).
        assertThat(sessionRepository.getSessions().map { it.sessionUuid })
            .containsExactlyInAnyOrder("uuid-batch-a", "uuid-batch-b")
    }

    @Test
    fun `should flush buffered upserts before a delete of the same session`() {
        // Change-log order: upsert A, delete A, upsert A again. The final state must
        // be the re-upserted row, not a resurrected pre-delete snapshot.
        val first = change("uuid-seq", ChangeOp.UPSERT, clientVersion = 2)
        val delete = change("uuid-seq", ChangeOp.DELETE)
        val reUpsert = change("uuid-seq", ChangeOp.UPSERT, clientVersion = 3)
        sessionRepository.upsertSyncedSessions(listOf(localSession("uuid-seq")))

        applier.apply(listOf(first, delete, reUpsert), userId, "macOS", "IntelliJ IDEA")

        // The re-upsert landed after the soft delete: the row is active again.
        val active = sessionRepository.getSessions().map { it.sessionUuid }
        assertThat(active).containsExactly("uuid-seq")
        val row = sessionRepository.findBySessionUuids(listOf("uuid-seq"))["uuid-seq"]
        assertThat(row).isNotNull()
        assertThat(row!!.syncVersion).isEqualTo(3)
    }

    @Test
    fun `should ignore delete changes for absent sessions`() {
        applier.apply(listOf(change("absent", ChangeOp.DELETE)), userId, "macOS", "IntelliJ IDEA")

        assertThat(sessionRepository.getSessions()).isEmpty()
    }
}
