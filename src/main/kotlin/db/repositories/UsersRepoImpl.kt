package db.repositories

import db.User
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.javatime.datetime
import org.joda.time.DateTime
import java.time.LocalDateTime

/**
 * Интерфейс для работы с таблицей пользователей.
 */
interface UsersRepo {
    /**
     * Найти пользователя по telegramId, или создать нового,
     * если такой ещё не заведён.
     */
    suspend fun getOrCreate(
        telegramUserId: Long,
        fullName: String,
        phone: String?,
        languageCode: String
    ): User

    suspend fun findByTelegramId(telegramUserId: Long): User?

    /**
     * Обновить languageCode для конкретного telegramUserId.
     * Вернёт true, если строка реально изменилась.
     */
    suspend fun updateLanguage(telegramUserId: Long, languageCode: String): Boolean
}

/**
 * Реализация [UsersRepo], использует [UsersTable] из Tables.kt.
 */
class UsersRepoImpl : UsersRepo {

    override suspend fun getOrCreate(
        telegramUserId: Long,
        fullName: String,
        phone: String?,
        languageCode: String
    ): User = transaction {
        // Пытаемся найти существующего
        var user = UsersTable
            .select { UsersTable.telegramId eq telegramUserId }
            .mapNotNull { row ->
                User(
                    id             = row[UsersTable.id].value,
                    telegramUserId = row[UsersTable.telegramId],
                    fullName       = row[UsersTable.fullName],
                    phone          = row[UsersTable.phone],
                    languageCode   = row[UsersTable.languageCode],
                    lastActivity   = row[UsersTable.lastActivity].toLocalDateTime()
                )
            }
            .singleOrNull()

        if (user == null) {
            // Создаём нового
            val newId = UsersTable.insertAndGetId { stmt ->
                stmt[UsersTable.telegramId]   = telegramUserId
                stmt[UsersTable.fullName]     = fullName
                stmt[UsersTable.phone]        = phone
                stmt[UsersTable.languageCode] = languageCode
                // lastActivity заполняется clientDefault
            }.value

            user = User(
                id             = newId,
                telegramUserId = telegramUserId,
                fullName       = fullName,
                phone          = phone,
                languageCode   = languageCode,
                lastActivity   = LocalDateTime.now()
            )
        } else {
            // Обновим lastActivity на текущее время (при любом обращении)
            UsersTable.update({ UsersTable.telegramId eq telegramUserId }) {
                it[UsersTable.lastActivity] = LocalDateTime.now()
            }
            user = user.copy(lastActivity = LocalDateTime.now())
        }

        user
    }

    override suspend fun findByTelegramId(telegramUserId: Long): User? = transaction {
        UsersTable
            .select { UsersTable.telegramId eq telegramUserId }
            .mapNotNull { row ->
                User(
                    id             = row[UsersTable.id].value,
                    telegramUserId = row[UsersTable.telegramId],
                    fullName       = row[UsersTable.fullName],
                    phone          = row[UsersTable.phone],
                    languageCode   = row[UsersTable.languageCode],
                    lastActivity   = row[UsersTable.lastActivity].toLocalDateTime()
                )
            }
            .singleOrNull()
    }

    override suspend fun updateLanguage(telegramUserId: Long, languageCode: String): Boolean = transaction {
        UsersTable.update({ UsersTable.telegramId eq telegramUserId }) {
            it[UsersTable.languageCode] = languageCode
        } > 0
    }
}