package db.repositories

import db.Booking
import db.BookingStatus
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.time.LocalDateTime
import org.jetbrains.exposed.dao.id.EntityID // Required for setting reference columns
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq

class BookingsRepoImpl(
    private val usersRepo: UsersRepo
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
        userId: Int, // This is the internal UsersTable.id (Int)
        clubId: Int,
        tableId: Int,
        guestsCount: Int,
        dateStart: LocalDateTime,
        dateEnd: LocalDateTime,
        comment: String?,
        guestName: String?,
        guestPhone: String?
    ): Pair<Int, Int> = newSuspendedTransaction {
        val userExists = UsersTable.select { UsersTable.id eq userId }.count() > 0
        if (!userExists) throw IllegalArgumentException("User with id $userId does not exist.")
        val clubExists = ClubsTable.select { ClubsTable.id eq clubId }.count() > 0
        if (!clubExists) throw IllegalArgumentException("Club with id $clubId does not exist.")
        val tableExists = TablesTable.select { TablesTable.id eq tableId }.count() > 0
        if (!tableExists) throw IllegalArgumentException("Table with id $tableId does not exist.")
        if (guestsCount <= 0) throw IllegalArgumentException("Guests count must be positive.")

        val loyaltyPoints = guestsCount * 10

        val insertedId = BookingsTable.insertAndGetId {
            it[BookingsTable.clubId] = EntityID(clubId, ClubsTable)
            it[BookingsTable.tableId] = EntityID(tableId, TablesTable)
            it[BookingsTable.userId] = EntityID(userId, UsersTable)
            it[BookingsTable.guestsCount] = guestsCount
            it[BookingsTable.dateStart] = dateStart
            it[BookingsTable.dateEnd] = dateEnd
            it[BookingsTable.status] = BookingStatus.NEW
            it[BookingsTable.comment] = comment
            it[BookingsTable.guestName] = guestName
            it[BookingsTable.guestPhone] = guestPhone
            it[BookingsTable.loyaltyPointsEarned] = loyaltyPoints
            it[BookingsTable.updatedAt] = LocalDateTime.now()
        }.value

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
            .select { BookingsTable.userId eq EntityID(userId, UsersTable) }
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
            (BookingsTable.tableId eq EntityID(tableId, TablesTable)) and
                    (BookingsTable.status neq BookingStatus.CANCELLED) and
                    (BookingsTable.status neq BookingStatus.ARCHIVED) and
                    (BookingsTable.dateStart less periodEnd) and
                    (BookingsTable.dateEnd greater periodStart)
        }.empty()
    }

    override suspend fun create(booking: Booking): Booking = newSuspendedTransaction {
        val id = BookingsTable.insertAndGetId {
            it[clubId] = EntityID(booking.clubId, ClubsTable)
            it[tableId] = EntityID(booking.tableId, TablesTable)
            it[userId] = EntityID(booking.userId, UsersTable)
            it[guestsCount] = booking.guestsCount
            it[dateStart] = booking.dateStart
            it[dateEnd] = booking.dateEnd
            it[status] = booking.status
            it[comment] = booking.comment
            it[guestName] = booking.guestName
            it[guestPhone] = booking.guestPhone
            it[loyaltyPointsEarned] = booking.loyaltyPointsEarned
            it[createdAt] = booking.createdAt
            it[updatedAt] = booking.updatedAt ?: LocalDateTime.now()
        }
        findById(id.value)!!
    }

    override suspend fun findAll(): List<Booking> = newSuspendedTransaction {
        BookingsTable.selectAll().map { it.toBooking() }
    }
}