package db.repositories

import db.Club // From db.Entities

interface ClubsRepo {
    /**
     * Получить все активные клубы.
     */
    suspend fun getAllActiveClubs(): List<Club>

    /**
     * Найти клуб по его ID.
     */
    suspend fun findById(id: Int): Club?

    /**
     * Найти клуб по его машинному коду (code).
     */
    suspend fun findByCode(code: String): Club?

    // If you need a method to get all clubs regardless of active status:
    // suspend fun getAllClubsIncludingInactive(): List<Club>

    // Method to create a club, used in RepositoriesTest
    suspend fun create(club: Club): Club
}