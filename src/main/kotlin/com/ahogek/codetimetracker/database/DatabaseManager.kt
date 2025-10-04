package com.ahogek.codetimetracker.database

import com.ahogek.codetimetracker.model.CodingSession
import com.ahogek.codetimetracker.model.DailySummary
import com.ahogek.codetimetracker.user.UserManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.Logger
import java.sql.DriverManager
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Manages all database operations, including connection, table creation,
 * and data persistence for coding sessions.
 *
 * @author AhogeK ahogek@gmail.com
 * @since 2025-09-29 20:49:29
 */
object DatabaseManager {

    internal var dbUrl: String

    private val log = Logger.getInstance(DatabaseManager::class.java)
    private val dateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME

    // This executor will handle all database write operations sequentially on a background thread
    private val databaseExecutor = Executors.newSingleThreadExecutor()

    init {
        try {
            // Manually load the SQLite JDBC driver class.
            // This ensures it's registered with the DriverManager, preventing the "No suitable driver found" error.
            Class.forName("org.sqlite.JDBC")
        } catch (e: ClassNotFoundException) {
            log.error("Failed to load SQLite JDBC driver.", e)
        }

        // PathManager.getCommonDataPath() returns a path that is shared all JetBrains products
        // This is the ideal location for a shared database
        val commonDataPath = PathManager.getCommonDataPath()
        val dbPath = commonDataPath.resolve("code-time-tracker-data")
        val dbFile = dbPath.resolve("coding_data.db").toFile()

        // Ensure the directory exists
        dbFile.parentFile.mkdirs()
        dbUrl = "jdbc:sqlite:${dbFile.absolutePath}"
        log.info("Database initialized at official shared location: $dbUrl")
        databaseExecutor.execute {
            createTableIfNotExists()
        }
    }

    /**
     * An explicit initialization method. Calling this ensures that the singleton's
     * init block has been executed and the database is ready.
     */
    fun initialize() {
        // This method can be empty. Its sole purpose is to provide a clear
        // entry point to trigger the object's initialization.
    }

    private fun createTableIfNotExists() {
        val sql = """
            CREATE TABLE IF NOT EXISTS coding_sessions (
            -- Primary Key. A simple auto-incrementing integer for local use.
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            
            -- Globally Unique ID. A UUID for the session record itself. Critical for merging and syncing.
            session_uuid TEXT NOT NULL UNIQUE,
            
            -- The unique ID of the user/installation. Prevents data contamination between different users.
            user_id TEXT NOT NULL,
            
            -- The display name of the project.
            project_name TEXT NOT NULL,
            
            -- The programming language used.
            language TEXT NOT NULL,
            
            -- The user's operating system (e.g., "macOS Sonoma").
            platform TEXT NOT NULL,
            
            -- The session's start time, in ISO-8601 format.
            start_time TEXT NOT NULL,
            
            -- The session's end time, in ISO-8601 format.
            end_time TEXT NOT NULL,
            
            -- Timestamp for syncing. Used for conflict resolution during merges.
            last_modified TEXT NOT NULL,
            
            -- Soft Delete Flag. A boolean (0 or 1) for marking records as deleted.
            is_deleted INTEGER NOT NULL DEFAULT 0
        );
        """

        val indexSql = "CREATE UNIQUE INDEX IF NOT EXISTS idx_session_uuid ON coding_sessions(session_uuid);"

        try {
            DriverManager.getConnection(dbUrl).use { conn ->
                conn.createStatement().execute(sql)
                conn.createStatement().execute(indexSql)
                log.info("Database table 'coding_sessions' is ready.")
            }
        } catch (e: Exception) {
            log.error("Failed to create database table.", e)
        }
    }

    fun saveSessions(sessions: List<CodingSession>, onComplete: () -> Unit) {
        if (sessions.isEmpty()) {
            onComplete()
            return
        }

        databaseExecutor.execute {
            val sql = """
            INSERT INTO coding_sessions(
                session_uuid, user_id, project_name, language, platform,
                start_time, end_time, last_modified
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?);
            """

            try {
                DriverManager.getConnection(dbUrl).use { conn ->
                    conn.autoCommit = false // Use a transaction for batch inserts
                    val pstmt = conn.prepareStatement(sql)
                    val currentTimestamp = dateTimeFormatter.format(LocalDateTime.now())
                    val userId = UserManager.getUserId()

                    for (session in sessions) {
                        pstmt.setString(1, UUID.randomUUID().toString())
                        pstmt.setString(2, userId)
                        pstmt.setString(3, session.projectName)
                        pstmt.setString(4, session.language)
                        pstmt.setString(5, session.platform)
                        pstmt.setString(6, dateTimeFormatter.format(session.startTime))
                        pstmt.setString(7, dateTimeFormatter.format(session.endTime))
                        pstmt.setString(8, currentTimestamp)
                        pstmt.addBatch()
                    }
                    pstmt.executeBatch()
                    conn.commit()
                    log.info("Successfully saved ${sessions.size} sessions to the database.")
                }
            } catch (e: Exception) {
                log.error("Failed to save sessions to the database.", e)
            } finally {
                ApplicationManager.getApplication().invokeLater {
                    onComplete()
                }
            }
        }
    }

    /**
     * Shuts down the database executor gracefully
     * It stops accepting new tasks and waits for pending tasks to complete
     */
    fun shutdown() {
        log.info("Shutting down DatabaseManager...")
        // Prevent new tasks from being submitted
        databaseExecutor.shutdown()
        try {
            // Wait a maximum of 5 seconds for existing tasks to terminate
            if (!databaseExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                log.warn("Database executor did not terminate in the specified time.")
                // Forcibly shut down if tasks are still running
                databaseExecutor.shutdownNow()
            }
        } catch (e: InterruptedException) {
            log.warn("Database shutdown was interrupted.", e)
            databaseExecutor.shutdownNow()
        }
        log.info("DatabaseManager has been shut down.")
    }

    /**
     * Fetched the user ID from the database, if not exists.
     * It assumes all records in the database belong to the same user.
     *
     * @return The user ID string found in the database, or null if the database is empty.
     */
    fun getUserIdFromDatabase(): String? {
        val sql = "SELECT user_id FROM coding_sessions LIMIT 1"
        return try {
            DriverManager.getConnection(dbUrl).use { conn ->
                conn.createStatement().executeQuery(sql).use { rs ->
                    if (rs.next()) rs.getString("user_id")
                    else null // Database is empty
                }
            }
        } catch (e: Exception) {
            log.error("Failed to get user ID from database.", e)
            null
        }
    }

    /**
     * Efficiently retrieves and calculates the total encoding duration from the database.
     * This method leverages SQL's SUM and strftime functions, offloading the calculation
     * entirely to the database and avoiding extensive data processing on the client side.
     *
     * @param projectName Optional project name to filter by
     * @return The total duration of all encoding activities (Duration).
     */
    fun getTotalCodingTime(projectName: String? = null): Duration {
        // Uses strftime('%s', column) to convert the time string into a Unix timestamp (seconds).
        // Then, it subtracts to get the number of seconds for each session, and finally uses the SUM() function to get the total.
        val baseSql =
            "SELECT SUM(strftime('%s', end_time) - strftime('%s', start_time)) FROM coding_sessions WHERE is_deleted = 0"
        val sql = if (projectName != null) "$baseSql AND project_name = ?" else baseSql
        var totalSeconds = 0L
        try {
            DriverManager.getConnection(dbUrl).use { conn ->
                conn.prepareStatement(sql).use { pstmt ->
                    projectName?.let { pstmt.setString(1, it) }
                    pstmt.executeQuery().use { rs ->
                        if (rs.next()) {
                            totalSeconds = rs.getLong(1)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            log.error("Failed to get total coding time from database.", e)
        }
        return Duration.ofSeconds(totalSeconds)
    }

    /**
     * Calculates the total coding duration within a  specific time range.
     * This method is versatile and can be used for daily, weekly, monthly, or any custom period reports.
     *
     * @param startTime The beginning of the time period (inclusive)
     * @param endTime The end of the time period (exclusive)
     * @param projectName Optional project name to filter by.
     * @return The total duration of coding activities within the specified range
     */
    fun getCodingTimeForPeriod(
        startTime: LocalDateTime,
        endTime: LocalDateTime,
        projectName: String? = null
    ): Duration {
        // The SQL query now includes a WHERE clause to filter sessions based on their start time.
        val baseSql = """
        SELECT SUM(strftime('%s', end_time) - strftime('%s', start_time))
        FROM coding_sessions
        WHERE is_deleted = 0 AND start_time >= ? AND start_time < ?
        """
        val sql = if (projectName != null) "$baseSql AND project_name = ?" else baseSql
        var totalSeconds = 0L
        try {
            DriverManager.getConnection(dbUrl).use { conn ->
                conn.prepareStatement(sql).use { pstmt ->
                    // Set the start and end time parameters in the query.
                    pstmt.setString(1, dateTimeFormatter.format(startTime))
                    pstmt.setString(2, dateTimeFormatter.format(endTime))
                    projectName?.let { pstmt.setString(3, it) }
                    pstmt.executeQuery().use { rs ->
                        if (rs.next()) {
                            totalSeconds = rs.getLong(1)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            log.error("Failed to get coding time for period from database.", e)
        }
        return Duration.ofSeconds(totalSeconds)
    }

    /**
     * Fetches daily coding time for a specific time range, suitable for a heatmap
     *
     * @param startTime The start of the time range
     * @param endTime The end of the time range
     * @return A list of DailySummary objects, each representing a day with coding activity.
     */
    fun getDailyCodingTimeForHeatmap(startTime: LocalDateTime, endTime: LocalDateTime): List<DailySummary> {
        val sql = """
            SELECT
                DATE(start_time) as coding_date,
                SUM(strftime('%s', end_time) - strftime('%s', start_time)) as total_seconds
            FROM coding_sessions
            WHERE is_deleted = 0 AND start_time >= ? AND start_time < ?
            GROUP BY coding_date
            ORDER BY coding_date;
        """
        val dailySummaries = mutableListOf<DailySummary>()
        try {
            DriverManager.getConnection(dbUrl).use { conn ->
                conn.prepareStatement(sql).use { pstmt ->
                    pstmt.setString(1, dateTimeFormatter.format(startTime))
                    pstmt.setString(2, dateTimeFormatter.format(endTime))
                    pstmt.executeQuery().use { rs ->
                        while (rs.next()) {
                            val date = LocalDate.parse(rs.getString("coding_date"))
                            val totalSeconds = rs.getLong("total_seconds")
                            dailySummaries.add(DailySummary(date, Duration.ofSeconds(totalSeconds)))
                        }
                    }
                }
            }
        } catch (e: Exception) {
            log.error("Failed to get daily coding time for heatmap.", e)
        }
        return dailySummaries
    }
}