package db.repositories

import db.Booking
import db.BookingStatus
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDateTime

/**
 * Интерфейс для работы с таблицей бронирований.
 */
interface BookingsRepo {
    /**
     * Сохранить новую бронь. Возвращает Pair(bookingId, loyaltyPointsEarned).
     *
     * @param userId            ID пользователя (foreign key в UsersTable)
     * @param tableId           ID стола (foreign key в TablesTable)
     * @param guestsCount       количество гостей
     * @param dateStart         когда начинается бронь (LocalDateTime)
     * @param dateEnd           когда заканчивается бронь (LocalDateTime)
     * @param comment           комментарий клиента (nullable)
     * @param guestName         имя гостя (nullable)
     * @param guestPhone        телефон гостя (nullable)
     * @return Pair(id созданного бронирования, начисленные loyalty points)
     */
    suspend fun saveBooking(
        userId: Long,
        tableId: Int,
        guestsCount: Int,
        dateStart: LocalDateTime,
        dateEnd: LocalDateTime,
        comment: String?,
        guestName: String?,
        guestPhone: String?
    ): Pair<Int, Int>

    suspend fun findById(id: Int): Booking?
    suspend fun findAll(): List<Booking>

    /**
     * Обновить статус брони (например, CONFIRMED, CANCELLED).
     * Вернёт true, если обновление действительно затронуло хотя бы одну строку.
     */
    suspend fun updateStatus(bookingId: Int, status: BookingStatus): Boolean

    /**
     * Удалить бронь по ID. Вернёт true, если запись была удалена.
     */
    suspend fun delete(id: Int): Boolean


    suspend fun isTableAvailableOnPeriod(
        tableId: Int,
        periodStart: LocalDateTime,
        periodEnd: LocalDateTime
    ): Boolean
}

/**
 * Реализация [BookingsRepo], использует объекты [BookingsTable], [TablesTable] (при подсчёте loyalty).
 */
class BookingsRepoImpl(
    private val tablesRepo: TablesRepo
) : BookingsRepo {

    override suspend fun saveBooking(
        userId: Long,
        tableId: Int,
        guestsCount: Int,
        dateStart: LocalDateTime,
        dateEnd: LocalDateTime,
        comment: String?,
        guestName: String?,
        guestPhone: String?
    ): Pair<Int, Int> = transaction {
        // Вставляем новую запись в BookingsTable
        val insertedId = BookingsTable.insertAndGetId { stmt ->
            stmt[BookingsTable.userId]             = userId
            stmt[BookingsTable.tableId]            = tableId
            stmt[BookingsTable.guestsCount]        = guestsCount
            stmt[BookingsTable.dateStart]          = dateStart
            stmt[BookingsTable.dateEnd]            = dateEnd
            stmt[BookingsTable.status]             = BookingStatus.NEW
            stmt[BookingsTable.comment]            = comment
            stmt[BookingsTable.guestName]          = guestName
            stmt[BookingsTable.guestPhone]         = guestPhone
            // loyaltyPointsEarned и feedbackRating/feedbackComment пока оставляем null
            // createdAt заполняется автоматически через clientDefault
        }.value

        // Допустим, лоялти → guestsCount * 10
        val loyaltyPoints = guestsCount * 10
        // Обновим колонку loyaltyPointsEarned в только что вставленной записи
        BookingsTable.update({ BookingsTable.id eq insertedId }) {
            it[BookingsTable.loyaltyPointsEarned] = loyaltyPoints
        }

        Pair(insertedId, loyaltyPoints)
    }

    override suspend fun findById(id: Int): Booking? = transaction {
        BookingsTable
            .select { BookingsTable.id eq id }
            .mapNotNull { row ->
                Booking(
                    id                  = row[BookingsTable.id].value,
                    userId              = row[BookingsTable.userId].value,
                    tableId             = row[BookingsTable.tableId].value,
                    guestsCount         = row[BookingsTable.guestsCount],
                    dateStart           = row[BookingsTable.dateStart].toLocalDateTime(),
                    dateEnd             = row[BookingsTable.dateEnd].toLocalDateTime(),
                    status              = row[BookingsTable.status],
                    comment             = row[BookingsTable.comment],
                    guestName           = row[BookingsTable.guestName],
                    guestPhone          = row[BookingsTable.guestPhone],
                    loyaltyPointsEarned = row[BookingsTable.loyaltyPointsEarned] ?: 0,
                    feedbackRating      = row[BookingsTable.feedbackRating],
                    feedbackComment     = row[BookingsTable.feedbackComment],
                    createdAt           = row[BookingsTable.createdAt].toLocalDateTime(),
                    updatedAt           = row[BookingsTable.updatedAt]?.toLocalDateTime()
                )
            }
            .singleOrNull()
    }

    override suspend fun findAll(): List<Booking> = transaction {
        BookingsTable.selectAll().map { row ->
            Booking(
                id                  = row[BookingsTable.id].value,
                userId              = row[BookingsTable.userId].value,
                tableId             = row[BookingsTable.tableId].value,
                guestsCount         = row[BookingsTable.guestsCount],
                dateStart           = row[BookingsTable.dateStart].toLocalDateTime(),
                dateEnd             = row[BookingsTable.dateEnd].toLocalDateTime(),
                status              = row[BookingsTable.status],
                comment             = row[BookingsTable.comment],
                guestName           = row[BookingsTable.guestName],
                guestPhone          = row[BookingsTable.guestPhone],
                loyaltyPointsEarned = row[BookingsTable.loyaltyPointsEarned] ?: 0,
                feedbackRating      = row[BookingsTable.feedbackRating],
                feedbackComment     = row[BookingsTable.feedbackComment],
                createdAt           = row[BookingsTable.createdAt].toLocalDateTime(),
                updatedAt           = row[BookingsTable.updatedAt]?.toLocalDateTime()
            )
        }
    }

    override suspend fun updateStatus(bookingId: Int, status: BookingStatus): Boolean = transaction {
        BookingsTable.update({ BookingsTable.id eq bookingId }) {
            it[BookingsTable.status] = status
        } > 0
    }

    override suspend fun delete(id: Int): Boolean = transaction {
        BookingsTable.deleteWhere { BookingsTable.id eq id } > 0
    }

    override suspend fun isTableAvailableOnPeriod(
        tableId: Int,
        periodStart: LocalDateTime,
        periodEnd: LocalDateTime
    ): Boolean = transaction {
        // Ищём брони этого стола, у которых статус != CANCELLED, и периоды пересекаются
        val overlapExists = BookingsTable.select {
            (BookingsTable.tableId eq tableId) and
                    (BookingsTable.status neq BookingStatus.CANCELLED) and
                    // условие пересечения интервалов:
                    (BookingsTable.dateStart lessEq periodEnd) and
                    (BookingsTable.dateEnd   greaterEq periodStart)
        }.any()
        !overlapExists
    }
}