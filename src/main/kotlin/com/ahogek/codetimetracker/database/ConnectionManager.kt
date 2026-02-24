package com.ahogek.codetimetracker.database

import com.intellij.openapi.diagnostic.Logger
import java.sql.Connection
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class ConnectionManager {

    internal data class DbConfig(
        val url: String, val factory: ConnectionFactory
    )

    @Volatile
    internal var config: DbConfig

    private val log = Logger.getInstance(ConnectionManager::class.java)

    internal val databaseExecutor = Executors.newSingleThreadExecutor()

    init {
        try {
            Class.forName("org.sqlite.JDBC")
        } catch (e: ClassNotFoundException) {
            log.error("Failed to load SQLite JDBC driver.", e)
        }

        val dbPath = com.ahogek.codetimetracker.util.PathUtils.getDatabasePath()
        val dbFile = dbPath.toFile()
        dbFile.parentFile?.mkdirs()
        val dbUrl = "jdbc:sqlite:${dbFile.absolutePath}"
        config = DbConfig(
            url = dbUrl, factory = DriverManagerConnectionFactory(dbUrl)
        )
        log.info("Database initialized at official shared location: ${config.url}")
    }

    fun setConnectionFactory(factory: ConnectionFactory, urlHint: String? = null) {
        val newUrl = urlHint ?: config.url
        val normalizedUrl = normalizeJdbcUrl(newUrl)

        config = DbConfig(
            url = normalizedUrl, factory = factory
        )
    }

    private fun normalizeJdbcUrl(url: String): String {
        val trimmed = url.trim()
        return if (trimmed.startsWith("jdbc:sqlite:")) trimmed else "jdbc:sqlite:$trimmed"
    }

    fun <T> withConnection(block: (Connection) -> T): T {
        val local = config
        return local.factory.getConnection().use { conn -> block(conn) }
    }

    /**
     * Placeholder for explicit initialization. Actual initialization is performed in the `init` block
     * (loading JDBC driver, setting up database path), so this method is intentionally empty.
     * Kept as a public API for consistency with [shutdown] and future extensibility.
     */
    fun initialize() {
        // Initialization completed in init block
    }

    fun shutdown() {
        log.info("Shutting down ConnectionManager...")
        databaseExecutor.shutdown()
        try {
            if (!databaseExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                log.warn("Database executor did not terminate in the specified time.")
                databaseExecutor.shutdownNow()
            }
        } catch (e: InterruptedException) {
            log.warn("ConnectionManager shutdown was interrupted.", e)
            databaseExecutor.shutdownNow()
        }
        log.info("ConnectionManager has been shut down.")
    }
}
