package db.repositories

import db.Club
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.time.LocalDateTime
import org.jetbrains.exposed.dao.id.EntityID // For create

class ClubsRepoImpl : ClubsRepo {

    private fun ResultRow.toClub(): Club = Club(
        id = this[ClubsTable.id].value,
        code = this[ClubsTable.code],
        // Assuming Club data class uses 'name' for what ClubsTable calls 'title'
        name = this[ClubsTable.title],
        description = this[ClubsTable.description],
        address = this[ClubsTable.address],
        phone = this[ClubsTable.phone],
        workingHours = this[ClubsTable.workingHours],
        timezone = this[ClubsTable.timezone],
        photoUrl = this[ClubsTable.photoUrl],
        floorPlanImageUrl = this[ClubsTable.floorPlanImageUrl],
        isActive = this[ClubsTable.isActive],
        createdAt = this[ClubsTable.createdAt],
        updatedAt = null // ClubsTable in Tables.kt doesn't have updatedAt. Add if needed.
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
        val id : EntityID<Int> = ClubsTable.insertAndGetId {
            it[code] = club.code
            it[title] = club.name // Club.name maps to ClubsTable.title
            it[description] = club.description
            it[address] = club.address
            it[phone] = club.phone
            it[workingHours] = club.workingHours
            it[timezone] = club.timezone
            it[photoUrl] = club.photoUrl
            it[floorPlanImageUrl] = club.floorPlanImageUrl
            it[isActive] = club.isActive
            // createdAt has clientDefault
        }
        // Re-fetch to get DB generated values like createdAt
        findById(id.value)!!
    }
}