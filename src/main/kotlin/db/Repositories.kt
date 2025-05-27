package db // Corrected package

import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq // Keep this specific import
import org.jetbrains.exposed.sql.javatime.date
import org.jetbrains.exposed.sql.javatime.timestamp
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.slf4j.LoggerFactory

suspend fun <T> dbQuery(block: suspend Transaction.() -> T): T =
    newSuspendedTransaction(Dispatchers.IO, statement = block)

interface UsersRepo {
    suspend fun getOrCreate(telegramId: Long, firstName: String?, lastName: String?, username: String?, initialLangCode: String): User
    suspend fun findById(id: Int): User?
    suspend fun updateLanguage(userId: Int, languageCode: String): Boolean
    suspend fun addLoyaltyPoints(userId: Int, pointsToAdd: Int): Boolean
    suspend fun updateLastActivity(userId: Int): Boolean
}

class UsersRepoImpl : UsersRepo {
    private val logger = LoggerFactory.getLogger(UsersRepoImpl::class.java)

    override suspend fun getOrCreate(telegramId: Long, firstName: String?, lastName: String?, username: String?, initialLangCode: String): User = dbQuery {
        val existingUser = UsersTable
            .select { UsersTable.telegramId eq telegramId }
            .singleOrNull()
            ?.toUser()

        if (existingUser != null) {
            var changed = false
            UsersTable.update({ UsersTable.id eq existingUser.id }) { stmt ->
                if (firstName != null && existingUser.firstName != firstName) { stmt[UsersTable.firstName] = firstName; changed = true }
                if (lastName != null && existingUser.lastName != lastName) { stmt[UsersTable.lastName] = lastName; changed = true }
                if (username != null && existingUser.username != username) { stmt[UsersTable.username] = username; changed = true }
                // Only update language if it's different and provided (e.g. from Telegram's settings on first contact)
                // Or if user explicitly changes it via /lang command (handled by updateLanguage)
                // if (initialLangCode != existingUser.languageCode) { stmt[UsersTable.languageCode] = initialLangCode; changed = true}
                stmt[UsersTable.lastActivityAt] = Instant.now()
            }
            logger.debug("User found/updated: telegramId=$telegramId, dbId=${existingUser.id}, changed=$changed")
            existingUser.apply { // Apply changes to the returned object if they occurred
                if (changed) {
                    this.firstName = firstName ?: this.firstName
                    this.lastName = lastName ?: this.lastName
                    this.username = username ?: this.username
                }
                this.lastActivityAt = Instant.now()
            }
        } else {
            val id = UsersTable.insertAndGetId {
                it[UsersTable.telegramId] = telegramId
                it[UsersTable.firstName] = firstName
                it[UsersTable.lastName] = lastName
                it[UsersTable.username] = username
                it[UsersTable.languageCode] = initialLangCode
                it[UsersTable.loyaltyPoints] = 0
                it[UsersTable.createdAt] = Instant.now()
                it[UsersTable.lastActivityAt] = Instant.now()
            }.value
            logger.info("New user created: telegramId=$telegramId, dbId=$id, lang=$initialLangCode")
            User(id, telegramId, firstName, lastName, username, null, initialLangCode, 0, Instant.now(), Instant.now())
        }
    }
    override suspend fun findById(id: Int): User? = dbQuery { UsersTable.select { UsersTable.id eq id }.singleOrNull()?.toUser() }

    override suspend fun updateLanguage(userId: Int, languageCode: String): Boolean = dbQuery {
        UsersTable.update({ UsersTable.id eq userId }) {
            it[UsersTable.languageCode] = languageCode
            it[lastActivityAt] = Instant.now()
        } > 0
    }
    override suspend fun addLoyaltyPoints(userId: Int, pointsToAdd: Int): Boolean = dbQuery {
        val currentPoints = UsersTable.select { UsersTable.id eq userId }.singleOrNull()?.get(UsersTable.loyaltyPoints) ?: 0
        UsersTable.update({ UsersTable.id eq userId }) {
            it[loyaltyPoints] = currentPoints + pointsToAdd
            it[lastActivityAt] = Instant.now()
        } > 0
    }
    override suspend fun updateLastActivity(userId: Int): Boolean = dbQuery {
        UsersTable.update({ UsersTable.id eq userId }) { it[lastActivityAt] = Instant.now() } > 0
    }
    private fun ResultRow.toUser() = User(
        id = this[UsersTable.id].value, telegramId = this[UsersTable.telegramId],
        firstName = this[UsersTable.firstName], lastName = this[UsersTable.lastName], username = this[UsersTable.username],
        phone = this[UsersTable.phone], languageCode = this[UsersTable.languageCode], loyaltyPoints = this[UsersTable.loyaltyPoints],
        createdAt = this[UsersTable.createdAt], lastActivityAt = this[UsersTable.lastActivityAt]
    )
}

interface ClubsRepo {
    suspend fun getAllClubs(activeOnly: Boolean = true): List<Club>
    suspend fun findById(id: Int): Club?
}


interface TablesRepo {
    suspend fun listByClub(clubId: Int, activeOnly: Boolean = true): List<TableInfo>
    suspend fun listAvailableByClubAndDate(clubId: Int, date: LocalDate, activeOnly: Boolean = true): List<TableInfo>
    suspend fun find(tableId: Int): TableInfo?
}


interface BookingsRepo {
    suspend fun create(booking: Booking): Int
    suspend fun findById(id: Int): Booking?
    suspend fun findByIdWithClubName(id: Int): BookingWithClubName?
    suspend fun byUserId(userId: Int, activeOnly: Boolean = true): List<BookingWithClubName>
    suspend fun byClubAndDate(clubId: Int, date: LocalDate): List<Booking>
    suspend fun cancel(bookingId: Int, userIdToCheck: Int): Boolean
    suspend fun updateStatus(bookingId: Int, newStatus: BookingStatus): Boolean
    suspend fun addFeedback(bookingId: Int, userId: Int, rating: Int, comment: String?): Boolean
    suspend fun getBookingsRequiringFeedback(userId: Int, olderThan: Instant): List<BookingWithClubName>
    suspend fun getUpcomingBookingsForReminder(reminderWindowStart: Instant, reminderWindowEnd: Instant): List<BookingWithClubName>
}
class BookingsRepoImpl : BookingsRepo {
    private val logger = LoggerFactory.getLogger(BookingsRepoImpl::class.java)

    override suspend fun create(booking: Booking): Int = dbQuery {
        BookingsTable.insertAndGetId {
            it[clubId] = booking.clubId; it[tableId] = booking.tableId; it[userId] = booking.userId
            it[guestsCount] = booking.guestsCount; it[dateStart] = booking.dateStart; it[dateEnd] = booking.dateEnd
            it[status] = booking.status; it[comment] = booking.comment; it[guestName] = booking.guestName
            it[guestPhone] = booking.guestPhone; it[loyaltyPointsEarned] = booking.loyaltyPointsEarned
            it[createdAt] = Instant.now(); it[updatedAt] = Instant.now()
        }.value.also { logger.info("Booking created with ID: $it for user ${booking.userId}") }
    }
    override suspend fun findById(id: Int): Booking? = dbQuery { BookingsTable.select { BookingsTable.id eq id }.singleOrNull()?.toBooking() }

    override suspend fun findByIdWithClubName(id: Int): BookingWithClubName? = dbQuery {
        (BookingsTable innerJoin ClubsTable innerJoin TablesTable on BookingsTable.tableId eq TablesTable.id)
            .slice(BookingsTable.columns + ClubsTable.title + TablesTable.number)
            .select { BookingsTable.id eq id }
            .singleOrNull()?.toBookingWithClubName()
    }
    override suspend fun byUserId(userIdToFind: Int, activeOnly: Boolean): List<BookingWithClubName> = dbQuery {
        val query = (BookingsTable innerJoin ClubsTable on BookingsTable.clubId eq ClubsTable.id
                innerJoin TablesTable on BookingsTable.tableId eq TablesTable.id)
            .slice(BookingsTable.columns + ClubsTable.title + TablesTable.number)
            .select { BookingsTable.userId eq userIdToFind }
        if (activeOnly) {
            query.andWhere { (BookingsTable.status eq BookingStatus.NEW) or (BookingsTable.status eq BookingStatus.CONFIRMED) or (BookingsTable.status eq BookingStatus.AWAITING_FEEDBACK) }
        }
        query.orderBy(BookingsTable.dateStart to SortOrder.DESC).map { it.toBookingWithClubName() }
    }
    override suspend fun byClubAndDate(targetClubId: Int, targetDate: LocalDate): List<Booking> = dbQuery {
        val club = ClubsTable.select{ClubsTable.id eq targetClubId}.singleOrNull()?.toClub(ZoneId.systemDefault()) // Assuming toClub needs a ZoneId
            ?: run { logger.warn("Club not found for ID: $targetClubId in byClubAndDate"); return@dbQuery emptyList() }
        val zoneId = ZoneId.of(club.timezone)
        val startOfDay = targetDate.atStartOfDay(zoneId).toInstant()
        val endOfDay = targetDate.plusDays(1).atStartOfDay(zoneId).toInstant()

        BookingsTable.select {
            (BookingsTable.clubId eq targetClubId) and
                    (BookingsTable.dateStart greaterEq startOfDay) and
                    (BookingsTable.dateStart less endOfDay) and
                    ((BookingsTable.status eq BookingStatus.NEW) or (BookingsTable.status eq BookingStatus.CONFIRMED))
        }.map { it.toBooking() }
    }

    override suspend fun cancel(bookingId: Int, userIdToCheck: Int): Boolean = dbQuery {
        val booking = BookingsTable.select { (BookingsTable.id eq bookingId) and (BookingsTable.userId eq userIdToCheck) }.singleOrNull()
        if (booking != null && booking[BookingsTable.status] != BookingStatus.CANCELLED) {
            BookingsTable.update({ (BookingsTable.id eq bookingId) and (BookingsTable.userId eq userIdToCheck) }) {
                it[status] = BookingStatus.CANCELLED
                it[updatedAt] = Instant.now()
            } > 0
        } else false
    }
    override suspend fun updateStatus(bookingId: Int, newStatus: BookingStatus): Boolean = dbQuery {
        BookingsTable.update({ BookingsTable.id eq bookingId }) { it[status] = newStatus; it[updatedAt] = Instant.now() } > 0
    }
    override suspend fun addFeedback(bookingId: Int, feedbackUserId: Int, rating: Int, comment: String?): Boolean = dbQuery {
        BookingsTable.update({ (BookingsTable.id eq bookingId) and (BookingsTable.userId eq feedbackUserId) }) {
            it[feedbackRating] = rating
            it[feedbackComment] = comment
            it[status] = BookingStatus.COMPLETED
            it[updatedAt] = Instant.now()
        } > 0
    }
    override suspend fun getBookingsRequiringFeedback(targetUserId: Int, olderThan: Instant): List<BookingWithClubName> = dbQuery {
        (BookingsTable innerJoin ClubsTable on BookingsTable.clubId eq ClubsTable.id
                innerJoin TablesTable on BookingsTable.tableId eq TablesTable.id)
            .slice(BookingsTable.columns + ClubsTable.title + TablesTable.number)
            .select {
                (BookingsTable.userId eq targetUserId) and
                        (BookingsTable.status eq BookingStatus.AWAITING_FEEDBACK) and
                        (BookingsTable.dateEnd less olderThan) and
                        (BookingsTable.feedbackRating.isNull())
            }
            .orderBy(BookingsTable.dateEnd to SortOrder.ASC)
            .limit(5)
            .map { it.toBookingWithClubName() }
    }
    override suspend fun getUpcomingBookingsForReminder(reminderWindowStart: Instant, reminderWindowEnd: Instant): List<BookingWithClubName> = dbQuery {
        (BookingsTable innerJoin ClubsTable on BookingsTable.clubId eq ClubsTable.id
                innerJoin TablesTable on BookingsTable.tableId eq TablesTable.id)
            .slice(BookingsTable.columns + ClubsTable.title + TablesTable.number)
            .select {
                ((BookingsTable.status eq BookingStatus.NEW) or (BookingsTable.status eq BookingStatus.CONFIRMED)) and
                        (BookingsTable.dateStart greaterEq reminderWindowStart) and
                        (BookingsTable.dateStart lessEq reminderWindowEnd)
                // TODO: Add a flag to Booking entity/table: `isReminderSent: Boolean` and check it here.
            }
            .map { it.toBookingWithClubName() }
    }

    private fun ResultRow.toBooking() = Booking(
        id = this[BookingsTable.id].value, clubId = this[BookingsTable.clubId], tableId = this[BookingsTable.tableId], userId = this[BookingsTable.userId],
        guestsCount = this[BookingsTable.guestsCount], dateStart = this[BookingsTable.dateStart], dateEnd = this[BookingsTable.dateEnd],
        status = this[BookingsTable.status], comment = this[BookingsTable.comment], guestName = this[BookingsTable.guestName],
        guestPhone = this[BookingsTable.guestPhone], loyaltyPointsEarned = this[BookingsTable.loyaltyPointsEarned],
        feedbackRating = this[BookingsTable.feedbackRating], feedbackComment = this[BookingsTable.feedbackComment],
        createdAt = this[BookingsTable.createdAt], updatedAt = this[BookingsTable.updatedAt]
    )
    private fun ResultRow.toBookingWithClubName() = BookingWithClubName(
        id = this[BookingsTable.id].value, clubId = this[BookingsTable.clubId], clubName = this[ClubsTable.title],
        tableId = this[BookingsTable.tableId], tableNumber = this[TablesTable.number], userId = this[BookingsTable.userId],
        guestsCount = this[BookingsTable.guestsCount], dateStart = this[BookingsTable.dateStart], dateEnd = this[BookingsTable.dateEnd],
        status = this[BookingsTable.status], guestName = this[BookingsTable.guestName], guestPhone = this[BookingsTable.guestPhone],
        createdAt = this[BookingsTable.createdAt], feedbackRating = this[BookingsTable.feedbackRating]
    )
    // Helper to map ResultRow to Club, needed for byClubAndDate if ClubsTable is joined or queried separately
    private fun ResultRow.toClub(defaultZoneId: ZoneId): Club = Club(
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

    suspend fun isTableAvailableOnDate(tableId: Int, tableDate: java.time.LocalDate): Boolean = dbQuery {
        BookingsTable
            .select {
                (BookingsTable.tableId eq tableId) and
                        ((BookingsTable.status eq BookingStatus.NEW) or (BookingsTable.status eq BookingStatus.CONFIRMED)) and
                        (BookingsTable.dateStart.date() eq tableDate)
            }
            .empty()
    }
}