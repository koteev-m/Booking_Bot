package db.repositories

import db.User // From db.Entities
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.time.LocalDateTime

class UsersRepoImpl : UsersRepo {

    private fun ResultRow.toUser(): User = User(
        id = this[UsersTable.id].value,
        telegramId = this[UsersTable.telegramId],
        firstName = this[UsersTable.firstName],
        lastName = this[UsersTable.lastName],
        username = this[UsersTable.username],
        phone = this[UsersTable.phone],
        languageCode = this[UsersTable.languageCode],
        loyaltyPoints = this[UsersTable.loyaltyPoints],
        createdAt = this[UsersTable.createdAt],
        lastActivityAt = this[UsersTable.lastActivityAt]
    )

    override suspend fun getOrCreate(
        telegramId: Long,
        firstName: String?,
        lastName: String?,
        username: String?,
        phone: String?,
        languageCode: String
    ): User = newSuspendedTransaction {
        val existingUser = UsersTable
            .select { UsersTable.telegramId eq telegramId }
            .map { it.toUser() }
            .singleOrNull()

        if (existingUser != null) {
            UsersTable.update({ UsersTable.id eq existingUser.id }) {
                it[UsersTable.lastActivityAt] = LocalDateTime.now()
                // Optionally update other fields if they've changed
                if (firstName != null) it[UsersTable.firstName] = firstName
                if (lastName != null) it[UsersTable.lastName] = lastName
                if (username != null) it[UsersTable.username] = username
                if (phone != null) it[UsersTable.phone] = phone
                if (existingUser.languageCode != languageCode) it[UsersTable.languageCode] = languageCode
            }
            // Re-fetch to get updated data, especially lastActivityAt
            UsersTable.select { UsersTable.id eq existingUser.id }.map { it.toUser() }.single()
        } else {
            val newId = UsersTable.insertAndGetId {
                it[UsersTable.telegramId] = telegramId
                it[UsersTable.firstName] = firstName
                it[UsersTable.lastName] = lastName
                it[UsersTable.username] = username
                it[UsersTable.phone] = phone
                it[UsersTable.languageCode] = languageCode
                // loyaltyPoints defaults to 0 in DB
                // createdAt and lastActivityAt use clientDefault
            }
            UsersTable.select { UsersTable.id eq newId }.map { it.toUser() }.single()
        }
    }

    override suspend fun findById(id: Int): User? = newSuspendedTransaction {
        UsersTable
            .select { UsersTable.id eq id }
            .map { it.toUser() }
            .singleOrNull()
    }

    override suspend fun findByTelegramId(telegramId: Long): User? = newSuspendedTransaction {
        UsersTable
            .select { UsersTable.telegramId eq telegramId }
            .map { it.toUser() }
            .singleOrNull()
    }

    override suspend fun updateLanguage(telegramId: Long, languageCode: String): Boolean = newSuspendedTransaction {
        UsersTable.update({ UsersTable.telegramId eq telegramId }) {
            it[UsersTable.languageCode] = languageCode
            it[UsersTable.lastActivityAt] = LocalDateTime.now()
        } > 0
    }

    override suspend fun updateLoyaltyPoints(userId: Int, pointsToAdd: Int): User? = newSuspendedTransaction {
        val user = findById(userId) ?: return@newSuspendedTransaction null
        val newLoyaltyPoints = user.loyaltyPoints + pointsToAdd
        UsersTable.update({ UsersTable.id eq userId }) {
            it[loyaltyPoints] = newLoyaltyPoints
            it[lastActivityAt] = LocalDateTime.now()
        }
        findById(userId) // Return updated user
    }

    override suspend fun create(user: User): User = newSuspendedTransaction {
        // This method is for tests, assumes user.id might be 0 or ignored
        val existing = findByTelegramId(user.telegramId)
        if (existing != null) return@newSuspendedTransaction existing // Or throw error/update

        val id = UsersTable.insertAndGetId {
            it[telegramId] = user.telegramId
            it[firstName] = user.firstName
            it[lastName] = user.lastName
            it[username] = user.username
            it[phone] = user.phone
            it[languageCode] = user.languageCode
            it[loyaltyPoints] = user.loyaltyPoints
            // createdAt and lastActivityAt use clientDefault
        }
        // Re-fetch to get DB-generated values
        UsersTable.select { UsersTable.id eq id }.map { it.toUser() }.single()
    }
}