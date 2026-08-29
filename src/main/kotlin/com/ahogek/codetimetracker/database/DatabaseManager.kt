package com.ahogek.codetimetracker.database

import com.ahogek.codetimetracker.model.*
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

object DatabaseManager {

    private val connectionManager = ConnectionManager()
    private val migrationManager = MigrationManager(connectionManager)
    private val sessionRepository = SessionRepository(connectionManager)
    private val statsRepository = StatsRepository(connectionManager)
    private val syncCursorRepository = SyncCursorRepository(connectionManager)

    init {
        migrationManager.migrate()
    }

    fun initialize() {
        connectionManager.initialize()
    }

    fun saveSessions(sessions: List<CodingSession>, onComplete: () -> Unit) {
        sessionRepository.saveSessions(sessions, onComplete)
    }

    fun shutdown() {
        connectionManager.shutdown()
    }

    fun getUserIdFromDatabase(): String? {
        return sessionRepository.getUserIdFromDatabase()
    }

    /**
     * Returns the stable installation-wide user id, creating and persisting it when
     * missing. The id lives in the shared `app_user` table (independent of coding
     * sessions), so every IDE on this machine resolves the same id from the first run
     * onward; legacy rows in coding_sessions are migrated on first call.
     */
    fun getOrCreateUserId(): String {
        return connectionManager.withConnection { conn ->
            conn.createStatement().executeQuery("SELECT user_id FROM app_user LIMIT 1").use { rs ->
                if (rs.next()) {
                    return@withConnection rs.getString("user_id")
                }
            }
            conn.createStatement().executeQuery(
                "SELECT user_id FROM coding_sessions WHERE user_id IS NOT NULL LIMIT 1",
            ).use { rs ->
                if (rs.next()) {
                    val legacy = rs.getString("user_id")
                    persistUserId(conn, legacy)
                    return@withConnection legacy
                }
            }
            val fresh = java.util.UUID.randomUUID().toString()
            persistUserId(conn, fresh)
            fresh
        }
    }

    private fun persistUserId(conn: java.sql.Connection, userId: String) {
        conn.prepareStatement(
            "INSERT OR IGNORE INTO app_user(user_id, created_at) VALUES (?, ?)",
        ).use { pstmt ->
            pstmt.setString(1, userId)
            pstmt.setString(2, LocalDateTime.now().toString())
            pstmt.executeUpdate()
        }
    }

    /**
     * Scopes statistics to [userId] (null restores the full local view). Called when an
     * API key is bound or switched so stats only cover the currently bound account.
     */
    fun setStatsOwner(userId: String?) {
        statsRepository.ownerUserId = userId
        sessionRepository.ownerUserId = userId
    }

    /** Exposes the session repository to sync services that need repository-level operations. */
    fun getSessionRepository(): SessionRepository = sessionRepository

    /** Exposes the sync watermark repository to sync services. */
    fun getSyncCursorRepository(): SyncCursorRepository = syncCursorRepository

    fun getTotalCodingTime(projectName: String? = null): Duration {
        return statsRepository.getTotalCodingTime(projectName)
    }

    fun getCodingTimeForPeriod(
        startTime: LocalDateTime, endTime: LocalDateTime, projectName: String? = null
    ): Duration {
        return statsRepository.getCodingTimeForPeriod(startTime, endTime, projectName)
    }

    fun getDailyCodingTimeForHeatmap(startTime: LocalDateTime, endTime: LocalDateTime): List<DailySummary> {
        return statsRepository.getDailyCodingTimeForHeatmap(startTime, endTime)
    }

    fun getCodingStreaks(startTime: LocalDateTime, endTime: LocalDateTime): CodingStreaks {
        return statsRepository.getCodingStreaks(startTime, endTime)
    }

    fun getDailyHourDistribution(
        startTime: LocalDateTime? = null, endTime: LocalDateTime? = null
    ): List<HourlyDistribution> {
        return statsRepository.getDailyHourDistribution(startTime, endTime)
    }

    fun getOverallHourlyDistributionWithTotalDays(
        startTime: LocalDateTime? = null, endTime: LocalDateTime? = null
    ): HourlyDistributionResult {
        return statsRepository.getOverallHourlyDistributionWithTotalDays(startTime, endTime)
    }

    fun getLanguageDistribution(
        startTime: LocalDateTime? = null, endTime: LocalDateTime? = null
    ): List<LanguageUsage> {
        return statsRepository.getLanguageDistribution(startTime, endTime)
    }

    fun getProjectDistribution(
        startTime: LocalDateTime? = null, endTime: LocalDateTime? = null
    ): List<ProjectUsage> {
        return statsRepository.getProjectDistribution(startTime, endTime)
    }

    fun getTimeOfDayDistribution(
        startTime: LocalDateTime? = null, endTime: LocalDateTime? = null
    ): List<TimeOfDayUsage> {
        return statsRepository.getTimeOfDayDistribution(startTime, endTime)
    }

    fun getSessions(startTime: LocalDateTime? = null, endTime: LocalDateTime? = null): List<CodingSession> {
        return sessionRepository.getSessions(startTime, endTime)
    }

    fun getAllSessionUuids(): Set<String> {
        return sessionRepository.getAllSessionUuids()
    }

    fun importSessions(sessions: List<CodingSession>): Int {
        return sessionRepository.importSessions(sessions)
    }

    fun getRecordCount(): Long {
        return sessionRepository.getRecordCount()
    }

    fun getAllActiveSessionTimes(): List<SessionSummaryDTO> {
        return sessionRepository.getAllActiveSessionTimes()
    }

    fun getFirstRecordDate(): LocalDate? {
        return sessionRepository.getFirstRecordDate()
    }
}
