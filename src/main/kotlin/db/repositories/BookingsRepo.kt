package db.repositories

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Перечисление возможных статусов бронирования.
 * Должно совпадать с полем status в таблице BookingsTable.
 */
enum class BookingStatus {
    NEW,
    CONFIRMED,
    CANCELLED,
    COMPLETED,
    AWAITING_FEEDBACK,
    ARCHIVED
}

/**
 * Data-класс, представляющий строку бронирования.
 */
data class Booking(
    val id: Int,
    val userId: Long,
    val tableId: Int,
    val guestsCount: Int,
    val dateStart: LocalDateTime,
    val dateEnd: LocalDateTime,
    val status: BookingStatus,
    val comment: String?,
    val guestName: String?,
    val guestPhone: String?,
    val loyaltyPointsEarned: Int,
    val feedbackRating: Int?,
    val feedbackComment: String?,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime?
)

/**
 * Определение таблицы Exposed для bookings.
 * Должно совпадать с вашим Tables.kt.
 */
object BookingsTable : Table("bookings") {
    val id = integer("id").autoIncrement().primaryKey()         // первичный ключ
    val tableId = integer("table_id").references(TablesTable.id)// внешний ключ на TablesTable
    val clubId = integer("club_id").references(ClubsTable.id)   // внешний ключ на ClubsTable
    val userId = long("user_id").references(UsersTable.telegramId)// внешний ключ на UsersTable
    val date = date("date")                                     // дата брони (LocalDate)
    val slot = varchar("slot", length = 50)                     // временной слот (например, "18:00-20:00")
    val peopleCount = integer("people_count")                    // количество гостей
    val guestName = varchar("guest_name", length = 255)         // имя гостя
    val guestPhone = varchar("guest_phone", length = 64)        // телефон гостя
    val status = varchar("status", length = 32)                 // название статуса (BookingStatus.name)
    val loyaltyPointsEarned = integer("loyalty_points_earned").default(0) // набранные баллы
    val createdAt = datetime("created_at")                      // время создания брони
}

/**
 * Интерфейс репозитория для работы с бронированиями.
 */
interface BookingsRepo {
    /**
     * Сохраняет новое бронирование.
     * Возвращает Pair<идентификатор новой брони, набранные баллы>.
     */
    suspend fun saveBooking(
        tableId: Int,
        clubId: Int,
        userId: Long,
        date: LocalDate,
        slot: String,
        peopleCount: Int,
        guestName: String,
        guestPhone: String
    ): Pair<Int, Int>

    suspend fun findByIdWithClubName(bookingId: Int): Booking?

    suspend fun findAllByUserId(userId: Long): List<Booking>

    suspend fun updateStatus(bookingId: Int, newStatus: BookingStatus): Boolean

    suspend fun delete(bookingId: Int): Boolean

    suspend fun isTableAvailableOnDate(tableId: Int, date: LocalDate): Boolean
}

/**
 * Реализация BookingsRepo на базе Exposed + корутин.
 */
class BookingsRepoImpl(
    private val usersRepo: UsersRepo,   // для проверки/создания пользователя
    private val tablesRepo: TablesRepo  // для проверки существования стола
) : BookingsRepo {

    override suspend fun saveBooking(
        tableId: Int,
        clubId: Int,
        userId: Long,
        date: LocalDate,
        slot: String,
        peopleCount: Int,
        guestName: String,
        guestPhone: String
    ): Pair<Int, Int> = newSuspendedTransaction {
        // 1) Проверяем, что указанный стол существует в данном клубе
        val tableExists = TablesTable.select {
            (TablesTable.id eq tableId) and (TablesTable.clubId eq clubId)
        }.empty().not()
        require(tableExists) { "Стол с id=$tableId не найден в клубе $clubId." }

        // 2) Проверяем доступность стола (нет активных бронирований на ту же дату)
        val alreadyBooked = BookingsTable.select {
            (BookingsTable.tableId eq tableId) and
                    (BookingsTable.date eq date) and
                    (BookingsTable.status neq BookingStatus.CANCELLED.name)
        }.empty().not()
        require(!alreadyBooked) { "Стол с id=$tableId уже забронирован на дату $date." }

        // 3) Получаем или создаём пользователя (если нужно)
        usersRepo.getOrCreate(userId, null, null, null)

        // 4) Вычисляем баллы лояльности (пример: 10 баллов на каждого гостя)
        val points = peopleCount * 10

        // 5) Вставляем запись в таблицу
        val insertedId = BookingsTable.insertAndGetId { row ->
            row[BookingsTable.tableId] = tableId
            row[BookingsTable.clubId] = clubId
            row[BookingsTable.userId] = userId
            row[BookingsTable.date] = date
            row[BookingsTable.slot] = slot
            row[BookingsTable.peopleCount] = peopleCount
            row[BookingsTable.guestName] = guestName
            row[BookingsTable.guestPhone] = guestPhone
            row[BookingsTable.status] = BookingStatus.NEW.name
            row[BookingsTable.loyaltyPointsEarned] = points
            // Текущая дата/время. Если используете java.time, замените на LocalDateTime.now()
            row[BookingsTable.createdAt] = org.jetbrains.exposed.sql.jodatime.CurrentDateTime()
        }.value

        // 6) Возвращаем пару (id брони, набранные баллы)
        insertedId to points
    }

    override suspend fun findByIdWithClubName(bookingId: Int): Booking? = newSuspendedTransaction {
        BookingsTable
            .select { BookingsTable.id eq bookingId }
            .limit(1)
            .firstOrNull()
            ?.let { rowToBooking(it) }
    }

    override suspend fun findAllByUserId(userId: Long): List<Booking> = newSuspendedTransaction {
        BookingsTable
            .select { BookingsTable.userId eq userId }
            .orderBy(BookingsTable.createdAt, SortOrder.DESC)
            .map { rowToBooking(it) }
    }

    override suspend fun updateStatus(bookingId: Int, newStatus: BookingStatus): Boolean = newSuspendedTransaction {
        val updatedRows = BookingsTable.update({ BookingsTable.id eq bookingId }) {
            it[status] = newStatus.name
        }
        updatedRows > 0
    }

    override suspend fun delete(bookingId: Int): Boolean = newSuspendedTransaction {
        val deletedRows = BookingsTable.deleteWhere { BookingsTable.id eq bookingId }
        deletedRows > 0
    }

    override suspend fun isTableAvailableOnDate(tableId: Int, date: LocalDate): Boolean = newSuspendedTransaction {
        BookingsTable
            .select {
                (BookingsTable.tableId eq tableId) and
                        (BookingsTable.date eq date) and
                        (BookingsTable.status neq BookingStatus.CANCELLED.name)
            }
            .empty() // если нет записей, значит доступно
    }

    /**
     * Преобразует строку из BookingsTable в объект Booking.
     */
    private fun rowToBooking(row: ResultRow): Booking {
        return Booking(
            id = row[BookingsTable.id],
            tableId = row[BookingsTable.tableId],
            clubId = row[BookingsTable.clubId],
            userId = row[BookingsTable.userId],
            date = row[BookingsTable.date],
            slot = row[BookingsTable.slot],
            peopleCount = row[BookingsTable.peopleCount],
            guestName = row[BookingsTable.guestName],
            guestPhone = row[BookingsTable.guestPhone],
            status = BookingStatus.valueOf(row[BookingsTable.status]),
            loyaltyPointsEarned = row[BookingsTable.loyaltyPointsEarned],
            createdAt = row[BookingsTable.createdAt].toLocalDateTime()
        )
    }
}