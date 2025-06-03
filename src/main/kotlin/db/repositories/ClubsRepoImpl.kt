package db.repositories

import db.Club // From db.Entities
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.time.LocalDateTime

class ClubsRepoImpl : ClubsRepo {

    private fun ResultRow.toClub(): Club = Club(
        id = this[ClubsTable.id].value,
        code = this[ClubsTable.code],
        title = this[ClubsTable.title],
        description = this[ClubsTable.description],
        address = this[ClubsTable.address],
        phone = this[ClubsTable.phone],
        workingHours = this[ClubsTable.workingHours],
        timezone = this[ClubsTable.timezone],
        photoUrl = this[ClubsTable.photoUrl],
        floorPlanImageUrl = this[ClubsTable.floorPlanImageUrl],
        isActive = this[ClubsTable.isActive],
        createdAt = this[ClubsTable.createdAt]
        // updatedAt = this[ClubsTable.updatedAt] // Add if ClubsTable has updatedAt
    )

    override suspend fun getAllActiveClubs(): List<Club> = newSuspendedTransaction {
        ClubsTable
            .select { ClubsTable.isActive eq true }
            .map { it.toClub() }
    }

    override suspend fun findById(id: Int): Club? = newSuspendedTransaction {
        ClubsTable
            .select { ClubsTable.id eq id }
            .map { it.toClub() }
            .singleOrNull()
    }

    override suspend fun findByCode(code: String): Club? = newSuspendedTransaction {
        ClubsTable
            .select { ClubsTable.code eq code }
            .map { it.toClub() }
            .singleOrNull()
    }

    override suspend fun create(club: Club): Club = newSuspendedTransaction {
        val id = ClubsTable.insertAndGetId {
            it[code] = club.code
            it[title] = club.name // V001 schema uses 'title', test-data.json uses 'name'
            // db.Club entity needs to be consistent; assume club.name maps to title
            it[description] = club.description
            it[address] = club.address
            it[phone] = club.phone
            it[workingHours] = club.workingHours
            it[timezone] = club.timezone
            it[photoUrl] = club.photoUrl
            it[floorPlanImageUrl] = club.floorPlanImageUrl
            it[isActive] = club.isActive
            // createdAt is clientDefault
        }
        club.copy(id = id.value, createdAt = findById(id.value)!!.createdAt) // Re-fetch to get createdAt
    }
}