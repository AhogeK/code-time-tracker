package com.ahogek.codetimetracker.service

import com.ahogek.codetimetracker.database.DatabaseManager
import com.ahogek.codetimetracker.model.CodingSession
import com.ahogek.codetimetracker.util.DurationAdapter
import com.ahogek.codetimetracker.util.LocalDateTimeAdapter
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import com.intellij.openapi.diagnostic.Logger
import java.io.File
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Service for exporting and importing coding session data.
 * Handles JSON serialization and deduplication during import.
 *
 * @author AhogeK ahogek@gmail.com
 * @since 2025-11-30 13:49:08
 */
object DataExportImportService {
    private val log = Logger.getInstance(DataExportImportService::class.java)
    private val gson: Gson = GsonBuilder()
        .registerTypeAdapter(LocalDateTime::class.java, LocalDateTimeAdapter())
        .registerTypeAdapter(Duration::class.java, DurationAdapter())
        .setPrettyPrinting()
        .create()

    /**
     * Export data model for JSON serialization
     */
    data class ExportData(
        val exportVersion: String = "1.0",
        val exportTime: String,
        val totalSessions: Int,
        val sessions: List<ExportSession>
    )

    /**
     * Session data model for export
     */
    data class ExportSession(
        val sessionUuid: String,
        val userId: String,
        val projectName: String,
        val language: String,
        val platform: String,
        val ideName: String,
        val startTime: String,
        val endTime: String,
        val lastModified: String
    )

    /**
     * Exports all coding sessions to a JSON file
     *
     * @param targetFile The file to export to
     * @return Number of sessions exported, or -1 if failed
     */
    fun exportToFile(
        targetFile: File,
        startTime: LocalDateTime? = null,
        endTime: LocalDateTime? = null
    ): Int {
        return try {
            // Query sessions with optional range
            val sessions = DatabaseManager.getSessions(startTime, endTime)
            val exportSessions = sessions.map { session ->
                ExportSession(
                    sessionUuid = session.sessionUuid,
                    userId = session.userId,
                    projectName = session.projectName,
                    language = session.language,
                    platform = session.platform,
                    ideName = session.ideName,
                    startTime = session.startTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                    endTime = session.endTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                    lastModified = session.lastModified.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                )
            }

            val exportData = ExportData(
                exportTime = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                totalSessions = exportSessions.size,
                sessions = exportSessions
            )

            // Write to file
            targetFile.writeText(gson.toJson(exportData))
            log.info("Successfully exported ${exportSessions.size} sessions to ${targetFile.absolutePath}")

            exportSessions.size
        } catch (e: Exception) {
            log.error("Failed to export data", e)
            -1
        }
    }

    /**
     * Imports coding sessions from a JSON file.
     * Only imports sessions that don't already exist (based on sessionUuid).
     *
     * @param sourceFile The file to import from
     * @return ImportResult containing statistics
     */
    fun importFromFile(sourceFile: File): ImportResult {
        return try {
            // Read and parse JSON
            val jsonContent = sourceFile.readText()
            val type = object : TypeToken<ExportData>() {}.type
            val exportData: ExportData = gson.fromJson(jsonContent, type)

            // Validate export version
            if (exportData.exportVersion != "1.0") {
                log.warn("Unsupported export version: ${exportData.exportVersion}")
                return ImportResult(
                    success = false,
                    totalInFile = exportData.totalSessions,
                    imported = 0,
                    skipped = 0,
                    failed = 0,
                    errorMessage = "Unsupported export version: ${exportData.exportVersion}"
                )
            }

            // Get existing session UUIDs from database
            val existingUuids = DatabaseManager.getAllSessionUuids()

            // Filter out sessions that already exist
            val sessionsToImport = exportData.sessions.filter { session ->
                !existingUuids.contains(session.sessionUuid)
            }

            val skippedCount = exportData.sessions.size - sessionsToImport.size

            // Convert to CodingSession objects
            val codingSessions = sessionsToImport.map { session ->
                CodingSession(
                    sessionUuid = session.sessionUuid,
                    userId = session.userId,
                    projectName = session.projectName,
                    language = session.language,
                    platform = session.platform,
                    ideName = session.ideName,
                    startTime = LocalDateTime.parse(session.startTime, DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                    endTime = LocalDateTime.parse(session.endTime, DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                    lastModified = LocalDateTime.parse(session.lastModified, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                )
            }

            // Import new sessions
            var importedCount = 0
            if (codingSessions.isNotEmpty()) {
                val result = DatabaseManager.importSessions(codingSessions)
                importedCount = result
            }

            log.info("Import completed: $importedCount imported, $skippedCount skipped from ${exportData.totalSessions} total")

            ImportResult(
                success = true,
                totalInFile = exportData.totalSessions,
                imported = importedCount,
                skipped = skippedCount,
                failed = 0
            )
        } catch (e: Exception) {
            log.error("Failed to import data", e)
            ImportResult(
                success = false,
                totalInFile = 0,
                imported = 0,
                skipped = 0,
                failed = 0,
                errorMessage = e.message ?: "Unknown error"
            )
        }
    }

    /**
     * Result of import operation
     */
    data class ImportResult(
        val success: Boolean,
        val totalInFile: Int,
        val imported: Int,
        val skipped: Int,
        val failed: Int,
        val errorMessage: String? = null
    )
}