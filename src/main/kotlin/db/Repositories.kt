package db

import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.javatime.CurrentTimestamp
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/*───────────────────  helper  ───────────────────*/

private suspend fun <T> db(block: suspend () -> T): T =
    newSuspendedTransaction(Dispatchers.IO) { block() }

/*───────────────────  UsersRepo  ───────────────────*/

interface UsersRepo {
    suspend fun getOrCreate(
        telegramId: Long,
        firstName: String?,
        lastName: String?,
        username: String?
    ): User

    suspend fun find(id: Int): User?
}

class UsersRepoImpl : UsersRepo {

    override suspend fun getOrCreate(
        telegramId: Long,
        firstName: String?,
        lastName: String?,
        username: String?
    ): User = db {
        UsersTable
            .select { UsersTable.telegramId eq telegramId }
            .singleOrNull()
            ?.toUser()
            ?: run {
                val id = UsersTable.insert {
                    it[UsersTable.telegramId] = telegramId
                    it[UsersTable.firstName]  = firstName
                    it[UsersTable.lastName]   = lastName
                    it[UsersTable.username]   = username
                    it[createdAt]             = CurrentTimestamp
                }.get(UsersTable.id)

                UsersTable.select { UsersTable.id eq id }
                    .single()
                    .toUser()
            }
    }

    override suspend fun find(id: Int): User? = db {
        UsersTable
            .select { UsersTable.id eq id }
            .singleOrNull()
            ?.toUser()
    }

    private fun ResultRow.toUser() = User(
        id         = this[UsersTable.id],
        telegramId = this[UsersTable.telegramId],
        firstName  = this[UsersTable.firstName],
        lastName   = this[UsersTable.lastName],
        username   = this[UsersTable.username],
        phone      = this[UsersTable.phone],
        createdAt  = this[UsersTable.createdAt]
    )
}

/*───────────────────  TablesRepo  ───────────────────*/

interface TablesRepo {
    suspend fun listByClub(clubId: Int): List<TableInfo>
    suspend fun find(tableId: Int): TableInfo?
}

class TablesRepoImpl : TablesRepo {

    override suspend fun listByClub(clubId: Int): List<TableInfo> = db {
        TablesTable
            .select { TablesTable.clubId eq clubId }
            .map { it.toTable() }
    }

    override suspend fun find(tableId: Int): TableInfo? = db {
        TablesTable
            .select { TablesTable.id eq tableId }
            .singleOrNull()
            ?.toTable()
    }

    private fun ResultRow.toTable() = TableInfo(
        id       = this[TablesTable.id],
        clubId   = this[TablesTable.clubId],
        number   = this[TablesTable.number],
        seats    = this[TablesTable.seats],
        photoUrl = this[TablesTable.photoUrl],
        isActive = this[TablesTable.isActive]
    )
}

/*───────────────────  BookingsRepo  ───────────────────*/

interface BookingsRepo {
    suspend fun create(booking: Booking): Int
    suspend fun byDate(clubId: Int, date: LocalDate): List<Booking>
    suspend fun cancel(id: Int): Boolean
}

class BookingsRepoImpl : BookingsRepo {

    override suspend fun create(booking: Booking): Int = db {
        BookingsTable.insert {
            it[clubId]      = booking.clubId
            it[tableId]     = booking.tableId
            it[userId]      = booking.userId
            it[guestsCount] = booking.guestsCount
            it[dateStart]   = booking.dateStart
            it[dateEnd]     = booking.dateEnd
            it[status]      = booking.status
            it[comment]     = booking.comment
            it[createdAt]   = Instant.now()
        }.get(BookingsTable.id)
    }

    override suspend fun byDate(clubId: Int, date: LocalDate): List<Booking> = db {
        val from = date.atStartOfDay().toInstant(ZoneOffset.UTC)
        val to   = from.plusSeconds(86_400)

        BookingsTable
            .select {
                (BookingsTable.clubId eq clubId) and
                        (BookingsTable.dateStart greaterEq from) and
                        (BookingsTable.dateStart less to)
            }
            .orderBy(BookingsTable.dateStart to SortOrder.ASC)
            .map { it.toBooking() }
    }

    override suspend fun cancel(id: Int): Boolean = db {
        BookingsTable.update({ BookingsTable.id eq id }) {
            it[status] = BookingStatus.CANCELLED
        } > 0
    }

    private fun ResultRow.toBooking() = Booking(
        id          = this[BookingsTable.id],
        clubId      = this[BookingsTable.clubId],
        tableId     = this[BookingsTable.tableId],
        userId      = this[BookingsTable.userId],
        guestsCount = this[BookingsTable.guestsCount],
        dateStart   = this[BookingsTable.dateStart],
        dateEnd     = this[BookingsTable.dateEnd],
        status      = this[BookingsTable.status],
        comment     = this[BookingsTable.comment],
        createdAt   = this[BookingsTable.createdAt]
    )
}