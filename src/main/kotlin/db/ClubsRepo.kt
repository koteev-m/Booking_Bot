package db

import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll

class ClubsRepoImpl : ClubsRepo {
    override suspend fun getAllClubs(activeOnly: Boolean): List<Club> = dbQuery {
        val query = if (activeOnly) ClubsTable.select { ClubsTable.isActive eq true } else ClubsTable.selectAll()
        query.map { it.toClub() }
    }

    override suspend fun findById(id: Int): Club? = dbQuery {
        ClubsTable.select { ClubsTable.id eq id }
            .singleOrNull()
            ?.toClub()
    }

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
    )
}