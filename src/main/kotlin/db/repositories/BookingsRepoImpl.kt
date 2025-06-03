package db.repositories

import db.Booking
import db.BookingStatus
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.time.LocalDateTime

class BookingsRepoImpl(
    private val usersRepo: UsersRepo // Dependency to update loyalty points
) : BookingsRepo {

    private fun ResultRow.toBooking(): Booking = Booking(
        id = this[BookingsTable.id].value,
        clubId = this[BookingsTable.clubId].value,
        tableId = this[BookingsTable.tableId].value,
        userId = this[BookingsTable.userId].value,
        guestsCount = this[BookingsTable.guestsCount],
        dateStart = this[BookingsTable.dateStart],
        dateEnd = this[BookingsTable.dateEnd],
        status = this[BookingsTable.status],
        comment = this[BookingsTable.comment],
        guestName = this[BookingsTable.guestName],
        guestPhone = this[BookingsTable.guestPhone],
        loyaltyPointsEarned = this[BookingsTable.loyaltyPointsEarned],
        feedbackRating = this[BookingsTable.feedbackRating],
        feedbackComment = this[BookingsTable.feedbackComment],
        createdAt = this[BookingsTable.createdAt],
        updatedAt = this[BookingsTable.updatedAt]
    )

    override suspend fun saveBooking(
        userId: Int,
        clubId: Int,
        tableId: Int,
        guestsCount: Int,
        dateStart: LocalDateTime,
        dateEnd: LocalDateTime,
        comment: String?,
        guestName: String?,
        guestPhone: String?
    ): Pair<Int, Int> = newSuspendedTransaction {
        // Check if user, club, table exist
        val userExists = UsersTable.select { UsersTable.id eq userId }.count() > 0
        if (!userExists) throw IllegalArgumentException("User with id $userId does not exist.")

        val clubExists = ClubsTable.select { ClubsTable.id eq clubId }.count() > 0
        if (!clubExists) throw IllegalArgumentException("Club with id $clubId does not exist.")

        val tableExists = TablesTable.select { TablesTable.id eq tableId }.count() > 0
        if (!tableExists) throw IllegalArgumentException("Table with id $tableId does not exist.")

        // Basic validation for guests count (DB has a CHECK constraint too)
        if (guestsCount <= 0) throw IllegalArgumentException("Guests count must be positive.")

        val loyaltyPoints = guestsCount * 10 // Example calculation

        val insertedId = BookingsTable.insertAndGetId {
            it[BookingsTable.clubId] = ClubsTable.id.entityId(clubId)
            it[BookingsTable.tableId] = TablesTable.id.entityId(tableId)
            it[BookingsTable.userId] = UsersTable.id.entityId(userId)
            it[BookingsTable.guestsCount] = guestsCount
            it[BookingsTable.dateStart] = dateStart
            it[BookingsTable.dateEnd] = dateEnd
            it[BookingsTable.status] = BookingStatus.NEW // Default status
            it[BookingsTable.comment] = comment
            it[BookingsTable.guestName] = guestName
            it[BookingsTable.guestPhone] = guestPhone
            it[BookingsTable.loyaltyPointsEarned] = loyaltyPoints
            // createdAt is clientDefault
            it[BookingsTable.updatedAt] = LocalDateTime.now() // Set initial updated_at
        }.value

        // Update user's total loyalty points
        usersRepo.updateLoyaltyPoints(userId, loyaltyPoints)

        Pair(insertedId, loyaltyPoints)
    }

    override suspend fun findById(id: Int): Booking? = newSuspendedTransaction {
        BookingsTable
            .select { BookingsTable.id eq id }
            .map { it.toBooking() }
            .singleOrNull()
    }

    override suspend fun findAllByUserId(userId: Int): List<Booking> = newSuspendedTransaction {
        BookingsTable
            .select { BookingsTable.userId eq UsersTable.id.entityId(userId) } // Compare with EntityID<Int>
            .orderBy(BookingsTable.createdAt, SortOrder.DESC)
            .map { it.toBooking() }
    }

    override suspend fun updateStatus(bookingId: Int, newStatus: BookingStatus): Boolean = newSuspendedTransaction {
        BookingsTable.update({ BookingsTable.id eq bookingId }) {
            it[status] = newStatus
            it[updatedAt] = LocalDateTime.now()
        } > 0
    }

    override suspend fun delete(id: Int): Boolean = newSuspendedTransaction {
        BookingsTable.deleteWhere { BookingsTable.id eq id } > 0
    }

    override suspend fun isTableAvailableOnPeriod(
        tableId: Int,
        periodStart: LocalDateTime,
        periodEnd: LocalDateTime
    ): Boolean = newSuspendedTransaction {
        BookingsTable.select {
            (BookingsTable.tableId eq TablesTable.id.entityId(tableId)) and
                    (BookingsTable.status neq BookingStatus.CANCELLED) and
                    (BookingsTable.status neq BookingStatus.ARCHIVED) and // Also exclude archived
                    // Interval overlap condition: (StartA < EndB) and (EndA > StartB)
                    (BookingsTable.dateStart less periodEnd) and // Booking starts before requested period ends
                    (BookingsTable.dateEnd greater periodStart)    // Booking ends after requested period starts
        }.empty() // True if no such bookings exist (i.e., table is available)
    }

    override suspend fun create(booking: Booking): Booking = newSuspendedTransaction {
        val id = BookingsTable.insertAndGetId {
            it[clubId] = ClubsTable.id.entityId(booking.clubId)
            it[tableId] = TablesTable.id.entityId(booking.tableId)
            it[userId] = UsersTable.id.entityId(booking.userId)
            it[guestsCount] = booking.guestsCount
            it[dateStart] = booking.dateStart
            it[dateEnd] = booking.dateEnd
            it[status] = booking.status
            it[comment] = booking.comment
            it[guestName] = booking.guestName
            it[guestPhone] = booking.guestPhone
            it[loyaltyPointsEarned] = booking.loyaltyPointsEarned
            // createdAt uses clientDefault if not provided by booking object
            if (booking.createdAt != null) it[createdAt] = booking.createdAt // Or rely on clientDefault
            it[updatedAt] = booking.updatedAt ?: LocalDateTime.now()
        }
        findById(id.value)!!
    }

    override suspend fun findAll(): List<Booking> = newSuspendedTransaction { // For RepositoriesTest
        BookingsTable.selectAll().map { it.toBooking() }
    }
}