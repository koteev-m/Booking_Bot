package db.repositories

import db.User // From db.Entities

interface UsersRepo {
    /**
     * Найти пользователя по telegramId, или создать нового, если такой ещё не заведён.
     * Обновляет last_activity_at при каждом вызове для существующего пользователя.
     */
    suspend fun getOrCreate(
        telegramId: Long,
        firstName: String?,
        lastName: String?,
        username: String?,
        phone: String?,
        languageCode: String
    ): User

    /**
     * Найти пользователя по его внутреннему ID.
     */
    suspend fun findById(id: Int): User?

    /**
     * Найти пользователя по его telegramId.
     */
    suspend fun findByTelegramId(telegramId: Long): User?

    /**
     * Обновить язык пользователя.
     */
    suspend fun updateLanguage(telegramId: Long, languageCode: String): Boolean

    /**
     * Обновить баллы лояльности пользователя.
     */
    suspend fun updateLoyaltyPoints(userId: Int, pointsToAdd: Int): User?

    // Method for RepositoriesTest
    suspend fun create(user: User): User
}