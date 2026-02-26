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
