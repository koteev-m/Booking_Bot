package db.repositaries

import db.Booking
import db.BookingStatus
import db.BookingWithClubName
import db.repositaries.BookingsTable
import db.Club
import db.repositaries.ClubsTable
import db.TableInfo
import db.repositaries.TablesTable
import db.User
import db.repositaries.UsersTable
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.javatime.date
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.collections.plus

suspend fun <T> dbQuery(block: suspend Transaction.() -> T): T = //
    newSuspendedTransaction(Dispatchers.IO, statement = block) //

interface UsersRepo {
    suspend fun getOrCreate(telegramId: Long, firstName: String?, lastName: String?, username: String?, initialLangCode: String): User //
    suspend fun findById(id: Int): User? //
    suspend fun updateLanguage(userId: Int, languageCode: String): Boolean //
    suspend fun addLoyaltyPoints(userId: Int, pointsToAdd: Int): Boolean //
    suspend fun updateLastActivity(userId: Int): Boolean //
}

class UsersRepoImpl : UsersRepo { //
    private val logger = LoggerFactory.getLogger(UsersRepoImpl::class.java) //

    override suspend fun getOrCreate(telegramId: Long, firstName: String?, lastName: String?, username: String?, initialLangCode: String): User = dbQuery { //
        val existingUser = db.UsersTable //
            .select { db.UsersTable.telegramId eq telegramId } //
            .singleOrNull() //
            ?.toUser() //

        if (existingUser != null) { //
            var changed = false //
            db.UsersTable.update({ db.UsersTable.id eq existingUser.id }) { stmt -> //
                if (firstName != null && existingUser.firstName != firstName) { stmt[db.UsersTable.firstName] = firstName; changed = true } //
                if (lastName != null && existingUser.lastName != lastName) { stmt[db.UsersTable.lastName] = lastName; changed = true } //
                if (username != null && existingUser.username != username) { stmt[db.UsersTable.username] = username; changed = true } //
                stmt[db.UsersTable.lastActivityAt] = Instant.now() //
            }
            logger.debug("User found/updated: telegramId=$telegramId, dbId=${existingUser.id}, changed=$changed") //
            existingUser.apply { //
                if (changed) { //
                    this.firstName = firstName ?: this.firstName //
                    this.lastName = lastName ?: this.lastName //
                    this.username = username ?: this.username //
                }
                this.lastActivityAt = Instant.now() //
            }
        } else { //
            val id = db.UsersTable.insertAndGetId { //
                it[db.UsersTable.telegramId] = telegramId //
                it[db.UsersTable.firstName] = firstName //
                it[db.UsersTable.lastName] = lastName //
                it[db.UsersTable.username] = username //
                it[db.UsersTable.languageCode] = initialLangCode //
                it[db.UsersTable.loyaltyPoints] = 0 //
                it[db.UsersTable.createdAt] = Instant.now() //
                it[db.UsersTable.lastActivityAt] = Instant.now() //
            }.value //
            logger.info("New user created: telegramId=$telegramId, dbId=$id, lang=$initialLangCode") //
            User(
                id,
                telegramId,
                firstName,
                lastName,
                username,
                null,
                initialLangCode,
                0,
                Instant.now(),
                Instant.now()
            ) //
        }
    }
    override suspend fun findById(id: Int): User? = dbQuery { db.UsersTable.selectAll().where { db.UsersTable.id eq id }.singleOrNull()?.toUser() } //

    override suspend fun updateLanguage(userId: Int, languageCode: String): Boolean = dbQuery { //
        db.UsersTable.update({ db.UsersTable.id eq userId }) { //
            it[db.UsersTable.languageCode] = languageCode //
            it[lastActivityAt] = Instant.now() //
        } > 0 //
    }
    override suspend fun addLoyaltyPoints(userId: Int, pointsToAdd: Int): Boolean = dbQuery { //
        val currentPoints = db.UsersTable.select { db.UsersTable.id eq userId }.singleOrNull()?.get(db.UsersTable.loyaltyPoints) ?: 0 //
        db.UsersTable.update({ db.UsersTable.id eq userId }) { //
            it[loyaltyPoints] = currentPoints + pointsToAdd //
            it[lastActivityAt] = Instant.now() //
        } > 0 //
    }
    override suspend fun updateLastActivity(userId: Int): Boolean = dbQuery { //
        db.UsersTable.update({ db.UsersTable.id eq userId }) { it[lastActivityAt] = Instant.now() } > 0 //
    }
    private fun ResultRow.toUser() = User( //
        id = this[UsersTable.id].value,
        telegramId = this[UsersTable.telegramId], //
        firstName = this[UsersTable.firstName],
        lastName = this[UsersTable.lastName],
        username = this[UsersTable.username], //
        phone = this[UsersTable.phone],
        languageCode = this[UsersTable.languageCode],
        loyaltyPoints = this[UsersTable.loyaltyPoints], //
        createdAt = this[UsersTable.createdAt],
        lastActivityAt = this[UsersTable.lastActivityAt] //
    )
}

interface ClubsRepo {
    suspend fun getAllClubs(activeOnly: Boolean = true): List<Club> //
    suspend fun findById(id: Int): Club? //
}


interface TablesRepo {
    suspend fun listByClub(clubId: Int, activeOnly: Boolean = true): List<TableInfo> //
    suspend fun listAvailableByClubAndDate(clubId: Int, date: LocalDate, activeOnly: Boolean = true): List<TableInfo> //
    suspend fun find(tableId: Int): TableInfo? //
}


interface BookingsRepo {
    suspend fun create(booking: Booking): Int //
    suspend fun findById(id: Int): Booking? //
    suspend fun findByIdWithClubName(id: Int): BookingWithClubName? //
    suspend fun byUserId(userId: Int, activeOnly: Boolean = true): List<BookingWithClubName> //
    suspend fun byClubAndDate(clubId: Int, date: LocalDate): List<Booking> //
    suspend fun cancel(bookingId: Int, userIdToCheck: Int): Boolean //
    suspend fun updateStatus(bookingId: Int, newStatus: BookingStatus): Boolean //
    suspend fun addFeedback(bookingId: Int, userId: Int, rating: Int, comment: String?): Boolean //
    suspend fun getBookingsRequiringFeedback(userId: Int, olderThan: Instant): List<BookingWithClubName> //
    suspend fun getUpcomingBookingsForReminder(reminderWindowStart: Instant, reminderWindowEnd: Instant): List<BookingWithClubName> //
    // Added this missing method from BookingFsm
    suspend fun isTableAvailableOnDate(tableId: Int, tableDate: LocalDate): Boolean
}
class BookingsRepoImpl : BookingsRepo { //
    private val logger = LoggerFactory.getLogger(BookingsRepoImpl::class.java) //

    override suspend fun create(booking: Booking): Int = dbQuery { //
        db.BookingsTable.insertAndGetId { //
            it[clubId] = booking.clubId; it[tableId] = booking.tableId; it[userId] = booking.userId //
            it[guestsCount] = booking.guestsCount; it[dateStart] = booking.dateStart; it[dateEnd] = booking.dateEnd //
            it[status] = booking.status; it[comment] = booking.comment; it[guestName] = booking.guestName //
            it[guestPhone] = booking.guestPhone; it[loyaltyPointsEarned] = booking.loyaltyPointsEarned //
            it[createdAt] = Instant.now(); it[updatedAt] = Instant.now() //
        }.value.also { logger.info("Booking created with ID: $it for user ${booking.userId}") } //
    }
    override suspend fun findById(id: Int): Booking? = dbQuery { db.BookingsTable.select { db.BookingsTable.id eq id }.singleOrNull()?.toBooking() } //

    override suspend fun findByIdWithClubName(id: Int): BookingWithClubName? = dbQuery { //
        (db.BookingsTable innerJoin db.ClubsTable innerJoin db.TablesTable on db.BookingsTable.tableId eq db.TablesTable.id) //
            .slice(db.BookingsTable.columns + db.ClubsTable.title + db.TablesTable.number) //
            .select { db.BookingsTable.id eq id } //
            .singleOrNull()?.toBookingWithClubName() //
    }
    override suspend fun byUserId(userIdToFind: Int, activeOnly: Boolean): List<BookingWithClubName> = dbQuery { //
        val query = (db.BookingsTable innerJoin db.ClubsTable on db.BookingsTable.clubId eq db.ClubsTable.id //
                innerJoin db.TablesTable on db.BookingsTable.tableId eq db.TablesTable.id) //
            .slice(db.BookingsTable.columns + db.ClubsTable.title + db.TablesTable.number) //
            .select { db.BookingsTable.userId eq userIdToFind } //
        if (activeOnly) { //
            query.andWhere { (db.BookingsTable.status eq db.BookingStatus.NEW) or (db.BookingsTable.status eq db.BookingStatus.CONFIRMED) or (db.BookingsTable.status eq db.BookingStatus.AWAITING_FEEDBACK) } //
        }
        query.orderBy(db.BookingsTable.dateStart to SortOrder.DESC).map { it.toBookingWithClubName() } //
    }
    override suspend fun byClubAndDate(targetClubId: Int, targetDate: LocalDate): List<Booking> = dbQuery { //
        val clubRow = db.ClubsTable.select{ db.ClubsTable.id eq targetClubId}.singleOrNull() //
            ?: run { logger.warn("Club not found for ID: $targetClubId in byClubAndDate"); return@dbQuery emptyList() } //
        val club = clubRow.toClub() // Use the simplified toClub()
        val zoneId = ZoneId.of(club.timezone) //
        val startOfDay = targetDate.atStartOfDay(zoneId).toInstant() //
        val endOfDay = targetDate.plusDays(1).atStartOfDay(zoneId).toInstant() //

        db.BookingsTable.select { //
            (db.BookingsTable.clubId eq targetClubId) and //
                    (db.BookingsTable.dateStart greaterEq startOfDay) and //
                    (db.BookingsTable.dateStart less endOfDay) and //
                    ((db.BookingsTable.status eq db.BookingStatus.NEW) or (db.BookingsTable.status eq db.BookingStatus.CONFIRMED)) //
        }.map { it.toBooking() } //
    }

    override suspend fun cancel(bookingId: Int, userIdToCheck: Int): Boolean = dbQuery { //
        val booking = db.BookingsTable.select { (db.BookingsTable.id eq bookingId) and (db.BookingsTable.userId eq userIdToCheck) }.singleOrNull() //
        if (booking != null && booking[db.BookingsTable.status] != db.BookingStatus.CANCELLED) { //
            db.BookingsTable.update({ (db.BookingsTable.id eq bookingId) and (db.BookingsTable.userId eq userIdToCheck) }) { //
                it[status] = db.BookingStatus.CANCELLED //
                it[updatedAt] = Instant.now() //
            } > 0 //
        } else false //
    }
    override suspend fun updateStatus(bookingId: Int, newStatus: BookingStatus): Boolean = dbQuery { //
        db.BookingsTable.update({ db.BookingsTable.id eq bookingId }) { it[status] = newStatus; it[updatedAt] = Instant.now() } > 0 //
    }
    override suspend fun addFeedback(bookingId: Int, feedbackUserId: Int, rating: Int, comment: String?): Boolean = dbQuery { //
        db.BookingsTable.update({ (db.BookingsTable.id eq bookingId) and (db.BookingsTable.userId eq feedbackUserId) }) { //
            it[feedbackRating] = rating //
            it[feedbackComment] = comment //
            it[status] = db.BookingStatus.COMPLETED // Set status to COMPLETED after feedback
            it[updatedAt] = Instant.now() //
        } > 0 //
    }
    override suspend fun getBookingsRequiringFeedback(targetUserId: Int, olderThan: Instant): List<BookingWithClubName> = dbQuery { //
        (db.BookingsTable innerJoin db.ClubsTable on db.BookingsTable.clubId eq db.ClubsTable.id //
                innerJoin db.TablesTable on db.BookingsTable.tableId eq db.TablesTable.id) //
            .slice(db.BookingsTable.columns + db.ClubsTable.title + db.TablesTable.number) //
            .select { //
                (db.BookingsTable.userId eq targetUserId) and //
                        (db.BookingsTable.status eq db.BookingStatus.AWAITING_FEEDBACK) and //
                        (db.BookingsTable.dateEnd less olderThan) and //
                        (db.BookingsTable.feedbackRating.isNull()) //
            }
            .orderBy(db.BookingsTable.dateEnd to SortOrder.ASC) //
            .limit(5) //
            .map { it.toBookingWithClubName() } //
    }
    override suspend fun getUpcomingBookingsForReminder(reminderWindowStart: Instant, reminderWindowEnd: Instant): List<BookingWithClubName> = dbQuery { //
        (db.BookingsTable innerJoin db.ClubsTable on db.BookingsTable.clubId eq db.ClubsTable.id //
                innerJoin db.TablesTable on db.BookingsTable.tableId eq db.TablesTable.id) //
            .slice(db.BookingsTable.columns + db.ClubsTable.title + db.TablesTable.number) //
            .select { //
                ((db.BookingsTable.status eq db.BookingStatus.NEW) or (db.BookingsTable.status eq db.BookingStatus.CONFIRMED)) and //
                        (db.BookingsTable.dateStart greaterEq reminderWindowStart) and //
                        (db.BookingsTable.dateStart lessEq reminderWindowEnd) //
            }
            .map { it.toBookingWithClubName() } //
    }

    private fun ResultRow.toBooking() = Booking( //
        id = this[BookingsTable.id].value,
        clubId = this[BookingsTable.clubId],
        tableId = this[BookingsTable.tableId],
        userId = this[BookingsTable.userId], //
        guestsCount = this[BookingsTable.guestsCount],
        dateStart = this[BookingsTable.dateStart],
        dateEnd = this[BookingsTable.dateEnd], //
        status = this[BookingsTable.status],
        comment = this[BookingsTable.comment],
        guestName = this[BookingsTable.guestName], //
        guestPhone = this[BookingsTable.guestPhone],
        loyaltyPointsEarned = this[BookingsTable.loyaltyPointsEarned], //
        feedbackRating = this[BookingsTable.feedbackRating],
        feedbackComment = this[BookingsTable.feedbackComment], //
        createdAt = this[BookingsTable.createdAt],
        updatedAt = this[BookingsTable.updatedAt] //
    )
    private fun ResultRow.toBookingWithClubName() = BookingWithClubName( //
        id = this[BookingsTable.id].value,
        clubId = this[BookingsTable.clubId],
        clubName = this[ClubsTable.title], //
        tableId = this[BookingsTable.tableId],
        tableNumber = this[TablesTable.number],
        userId = this[BookingsTable.userId], //
        guestsCount = this[BookingsTable.guestsCount],
        dateStart = this[BookingsTable.dateStart],
        dateEnd = this[BookingsTable.dateEnd], //
        status = this[BookingsTable.status],
        guestName = this[BookingsTable.guestName],
        guestPhone = this[BookingsTable.guestPhone], //
        createdAt = this[BookingsTable.createdAt],
        feedbackRating = this[BookingsTable.feedbackRating] //
    )
    // Helper to map ResultRow to Club, simplified as defaultZoneId was not used.
    private fun ResultRow.toClub(): Club = Club( //
        id = this[ClubsTable.id].value, //
        code = this[ClubsTable.code], //
        title = this[ClubsTable.title], //
        description = this[ClubsTable.description], //
        address = this[ClubsTable.address], //
        phone = this[ClubsTable.phone], //
        workingHours = this[ClubsTable.workingHours], //
        timezone = this[ClubsTable.timezone], //
        photoUrl = this[ClubsTable.photoUrl], //
        floorPlanImageUrl = this[ClubsTable.floorPlanImageUrl], //
        isActive = this[ClubsTable.isActive], //
        createdAt = this[ClubsTable.createdAt] //
    )

    // Implementation for the method declared in the interface
    override suspend fun isTableAvailableOnDate(tableId: Int, tableDate: LocalDate): Boolean = dbQuery { //
        db.BookingsTable //
            .select { //
                (db.BookingsTable.tableId eq tableId) and //
                        ((db.BookingsTable.status eq db.BookingStatus.NEW) or (db.BookingsTable.status eq db.BookingStatus.CONFIRMED)) and //
                        (db.BookingsTable.dateStart.date() eq tableDate) //
            }
            .empty() //
    }
}