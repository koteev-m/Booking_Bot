// File: src/main/kotlin/db/repositories/UsersRepoImpl.kt
package db.repositories

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.javatime.datetime
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction

/**
 * Объект-описание таблицы пользователей.
 * Предполагаем, что в Tables.kt или рядом есть:
 *
 * object UsersTable : LongIdTable("users") {
 *     val telegramUserId = long("telegram_user_id").uniqueIndex()
 *     val fullName = varchar("full_name", 255).nullable()
 *     val phone = varchar("phone", 32).nullable()
 *     val languageCode = varchar("language_code", 8).default("en")
 *     val createdAt = datetime("created_at")
 * }
 */
object UsersTable : org.jetbrains.exposed.dao.id.LongIdTable("users") {
    val telegramUserId = long("telegram_user_id").uniqueIndex()
    val fullName = varchar("full_name", 255).nullable()
    val phone = varchar("phone", 32).nullable()
    val languageCode = varchar("language_code", 8).default("en")
    val createdAt = datetime("created_at").clientDefault { org.joda.time.DateTime.now() }
}

/**
 * Предполагаемая сигнатура интерфейса UsersRepo:
 *
 * interface UsersRepo {
 *     suspend fun getOrCreate(
 *         telegramUserId: Long,
 *         fullName: String?,
 *         phone: String?,
 *         languageCode: String
 *     ): User
 *
 *     suspend fun findByTelegramId(telegramUserId: Long): User?
 *     suspend fun updateLanguage(userId: Long, newLanguageCode: String): Boolean
 * }
 *
 * В db.entities есть data class User.
 */
class UsersRepoImpl : UsersRepo {

    override suspend fun getOrCreate(
        telegramUserId: Long,
        fullName: String?,
        phone: String?,
        languageCode: String?
    ): User = newSuspendedTransaction {
        // Ищем существующего по telegramUserId
        val existing = UsersTable.select { UsersTable.telegramUserId eq telegramUserId }
            .limit(1)
            .map { toUser(it) }
            .singleOrNull()

        if (existing != null) {
            // Если найден, обновим имя/телефон/язык (если поменялись)
            UsersTable.update({ UsersTable.telegramUserId eq telegramUserId }) {
                if (fullName != null) it[this.fullName] = fullName
                if (phone != null) it[this.phone] = phone
                it[this.languageCode] = languageCode
            }
            existing.copy(
                fullName = fullName ?: existing.fullName,
                phone = phone ?: existing.phone,
                languageCode = languageCode
            )
        } else {
            // Иначе создаем нового
            val insertedId = UsersTable.insertAndGetId {
                it[this.telegramUserId] = telegramUserId
                it[this.fullName] = fullName
                it[this.phone] = phone
                it[this.languageCode] = languageCode
                // createdAt проставится автоматически
            }.value

            User(
                id = insertedId,
                telegramUserId = telegramUserId,
                fullName = fullName,
                phone = phone,
                languageCode = languageCode,
                createdAt = org.joda.time.DateTime.now()
            )
        }
    }

    override suspend fun findByTelegramId(telegramUserId: Long): User? = newSuspendedTransaction {
        UsersTable.select { UsersTable.telegramUserId eq telegramUserId }
            .limit(1)
            .map { toUser(it) }
            .singleOrNull()
    }

    override suspend fun updateLanguage(userId: Long, newLanguageCode: String): Boolean = newSuspendedTransaction {
        UsersTable.update({ UsersTable.id eq userId }) {
            it[this.languageCode] = newLanguageCode
        } > 0
    }

    private fun toUser(row: ResultRow): User {
        return User(
            id = row[UsersTable.id].value,
            telegramUserId = row[UsersTable.telegramUserId],
            fullName = row[UsersTable.fullName],
            phone = row[UsersTable.phone],
            languageCode = row[UsersTable.languageCode],
            createdAt = row[UsersTable.createdAt]
        )
    }
}