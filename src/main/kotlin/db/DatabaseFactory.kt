package db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.Properties

object DatabaseFactory {

    fun init(
        host: String,
        port: Int,
        dbName: String,
        user: String,
        password: String,
    ) {
        val jdbcUrl = "jdbc:postgresql://$host:$port/$dbName"
        val config = HikariConfig().apply {
            driverClassName = "org.postgresql.Driver"
            username = user
            this.password = password
            maximumPoolSize = 10
            isAutoCommit = false
            validate()
        }
        Database.connect(HikariDataSource(config))
    }

    /** Синхронная транзакция (только для миграций/скриптов) */
    fun <T> tx(block: () -> T): T = transaction { block() }

    /** Suspend-транзакция для корутин */
    suspend fun <T> suspendingTx(block: suspend () -> T): T =
        newSuspendedTransaction(Dispatchers.IO) { block() }
}