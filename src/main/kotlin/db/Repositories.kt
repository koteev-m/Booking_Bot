package db

import kotlinx.datetime.toKotlinInstant
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.kotlin.datetime.CurrentTimestamp
import org.jetbrains.exposed.sql.kotlin.datetime.kotlinDatetime
import java.time.Instant

interface UsersRepo {
    suspend fun getOrCreate(telegramId: Long,
                            firstName: String?,
                            lastName: String?,
                            username: String?): User

    suspend fun find(id: Int): User?
}

interface TablesRepo {
    suspend fun listByClub(clubId: Int): List<TableInfo>
    suspend fun find(tableId: Int): TableInfo?
}

interface BookingsRepo {
    suspend fun create(booking: Booking): Int
    suspend fun byDate(clubId: Int, date: java.time.LocalDate): List<Booking>
    suspend fun cancel(id: Int): Boolean
}

/* ---------- implementation ---------- */

class UsersRepoImpl : UsersRepo {
    override suspend fun getOrCreate(
        telegramId: Long,
        firstName: String?,
        lastName: String?,
        username: String?
    ): User = DatabaseFactory.suspendingTx {
        val row = UsersTable.select { UsersTable.telegramId eq telegramId }
            .singleOrNull()

        val id = if (row == null) {
            UsersTable.insertAndGetId {
                it[UsersTable.telegramId] = telegramId
                it[UsersTable.firstName] = firstName
                it[UsersTable.lastName] = lastName
                it[UsersTable.username] = username
                it[createdAt] = CurrentTimestamp()
            }.value
        } else row[UsersTable.id]

        UsersTable.select { UsersTable.id eq id }
            .single()
            .toUser()
    }

    override suspend fun find(id: Int): User? = DatabaseFactory.suspendingTx {
        UsersTable.select { UsersTable.id eq id }
            .singleOrNull()
            ?.toUser()
    }

    private fun ResultRow.toUser() = User(
        id = this[UsersTable.id],
        telegramId = this[UsersTable.telegramId],
        firstName = this[UsersTable.firstName],
        lastName = this[UsersTable.lastName],
        username = this[UsersTable.username],
        phone = this[UsersTable.phone],
        createdAt = this[UsersTable.createdAt].toKotlinInstant()
    )
}

class TablesRepoImpl : TablesRepo {
    override suspend fun listByClub(clubId: Int): List<TableInfo> =
        DatabaseFactory.suspendingTx {
            TablesTable.select { TablesTable.clubId eq clubId }
                .map { it.toTable() }
        }

    override suspend fun find(tableId: Int): TableInfo? =
        DatabaseFactory.suspendingTx {
            TablesTable.select { TablesTable.id eq tableId }
                .singleOrNull()
                ?.toTable()
        }

    private fun ResultRow.toTable() = TableInfo(
        id = this[TablesTable.id],
        clubId = this[TablesTable.clubId],
        number = this[TablesTable.number],
        seats = this[TablesTable.seats],
        photoUrl = this[TablesTable.photoUrl],
        isActive = this[TablesTable.isActive]
    )
}

class BookingsRepoImpl : BookingsRepo {
    override suspend fun create(booking: Booking): Int =
        DatabaseFactory.suspendingTx {
            BookingsTable.insertAndGetId {
                it[clubId]       = booking.clubId
                it[tableId]      = booking.tableId
                it[userId]       = booking.userId
                it[guestsCount]  = booking.guestsCount
                it[dateStart]    = java.time.Instant.from(booking.dateStart)
                it[dateEnd]      = java.time.Instant.from(booking.dateEnd)
                it[status]       = booking.status
                it[comment]      = booking.comment
                it[createdAt]    = java.time.Instant.now()
            }.value
        }

    override suspend fun byDate(clubId: Int, date: java.time.LocalDate): List<Booking> =
        DatabaseFactory.suspendingTx {
            val from = date.atStartOfDay(java.time.ZoneOffset.UTC).toInstant()
            val to   = from.plusSeconds(86_400)
            BookingsTable.select {
                (BookingsTable.clubId eq clubId) and
                        (BookingsTable.dateStart greaterEq from) and
                        (BookingsTable.dateStart less  to)
            }.map { it.toBooking() }
        }

    override suspend fun cancel(id: Int): Boolean =
        DatabaseFactory.suspendingTx {
            BookingsTable.update({ BookingsTable.id eq id }) {
                it[status] = BookingStatus.CANCELLED
            } > 0
        }

    private fun ResultRow.toBooking() = Booking(
        id = this[BookingsTable.id],
        clubId = this[BookingsTable.clubId],
        tableId = this[BookingsTable.tableId],
        userId = this[BookingsTable.userId],
        guestsCount = this[BookingsTable.guestsCount],
        dateStart = this[BookingsTable.dateStart].toKotlinInstant(),
        dateEnd = this[BookingsTable.dateEnd].toKotlinInstant(),
        status = this[BookingsTable.status],
        comment = this[BookingsTable.comment],
        createdAt = this[BookingsTable.createdAt].toKotlinInstant()
    )
}