package db.repositories

import db.Club
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction

/**
 * Интерфейс для работы с таблицей клубов.
 */
interface ClubsRepo {
    suspend fun getAllClubs(): List<Club>
    suspend fun getById(id: Int): Club?
}

/**
 * Реализация [ClubsRepo] через Exposed и объект [ClubsTable] (см. Tables.kt).
 */
class ClubsRepoImpl : ClubsRepo {

    override suspend fun getAllClubs(): List<Club> = transaction {
        ClubsTable
            .selectAll()
            .map { row ->
                Club(
                    id        = row[ClubsTable.id].value,
                    name      = row[ClubsTable.name],
                    address   = row[ClubsTable.address],
                    timezone  = row[ClubsTable.timezone],
                    isActive  = row[ClubsTable.isActive],
                    createdAt = row[ClubsTable.createdAt].toLocalDateTime(),
                    updatedAt = row[ClubsTable.updatedAt]?.toLocalDateTime()
                )
            }
    }

    override suspend fun getById(id: Int): Club? = transaction {
        ClubsTable
            .select { ClubsTable.id eq id }
            .mapNotNull { row ->
                Club(
                    id        = row[ClubsTable.id].value,
                    name      = row[ClubsTable.name],
                    address   = row[ClubsTable.address],
                    timezone  = row[ClubsTable.timezone],
                    isActive  = row[ClubsTable.isActive],
                    createdAt = row[ClubsTable.createdAt].toLocalDateTime(),
                    updatedAt = row[ClubsTable.updatedAt]?.toLocalDateTime()
                )
            }
            .singleOrNull()
    }
}