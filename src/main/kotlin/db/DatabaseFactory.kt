package db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.jetbrains.exposed.sql.Database
// import org.jetbrains.exposed.sql.SchemaUtils // Removed to avoid conflict with Flyway
// import org.jetbrains.exposed.sql.transactions.transaction // Removed if not used after SchemaUtils removal
import org.slf4j.LoggerFactory

object DatabaseFactory {
    private val logger = LoggerFactory.getLogger(DatabaseFactory::class.java)

    fun init(host: String, port: Int, dbName: String, user: String, password: String) {
        logger.info("Initializing database connection to postgresql://$user@$host:$port/$dbName")
        val jdbcUrl = "jdbc:postgresql://$host:$port/$dbName"
        val config = HikariConfig().apply {
            this.driverClassName = "org.postgresql.Driver"
            this.jdbcUrl = jdbcUrl
            this.username = user
            this.password = password
            this.maximumPoolSize = System.getenv("DB_MAX_POOL_SIZE")?.toIntOrNull() ?: 10
            this.isAutoCommit = false // Exposed manages transactions
            this.transactionIsolation = "TRANSACTION_REPEATABLE_READ" // Recommended for Exposed
            this.connectionTimeout = System.getenv("DB_CONNECTION_TIMEOUT_MS")?.toLongOrNull() ?: 30000
            this.idleTimeout = System.getenv("DB_IDLE_TIMEOUT_MS")?.toLongOrNull() ?: 600000
            this.maxLifetime = System.getenv("DB_MAX_LIFETIME_MS")?.toLongOrNull() ?: 1800000
            this.validationTimeout = System.getenv("DB_VALIDATION_TIMEOUT_MS")?.toLongOrNull() ?: 5000
            validate()
        }
        try {
            val dataSource = HikariDataSource(config)
            Database.connect(dataSource)
            logger.info("Database connection pool established.")

            // Flyway will handle schema creation and migration.
            // SchemaUtils.createMissingTablesAndColumns should not be used with Flyway.
            /*
            transaction {
                // addLogger(StdOutSqlLogger) // Uncomment for SQL query logging during development
                SchemaUtils.createMissingTablesAndColumns(
                    UsersTable, ClubsTable, TablesTable, BookingsTable
                    // Add other tables here if any
                )
                logger.info("Database tables checked/created if missing.")
            }
            */
        } catch (e: Exception) {
            logger.error("FATAL: Failed to initialize database: ${e.message}", e)
            throw e // Re-throw to halt application if DB initialization fails
        }
    }
}