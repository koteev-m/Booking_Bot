package db.repositories

import db.Booking
import db.BookingStatus
import java.time.LocalDateTime

interface BookingsRepo {
    /**
     * Сохранить новую бронь.
     * @param userId ID пользователя (UsersTable.id)
     * @param clubId ID клуба (ClubsTable.id)
     * @param tableId ID стола (TablesTable.id)
     * @return Pair(id созданного бронирования, начисленные loyalty points)
     */
    suspend fun saveBooking(
        userId: Int,
        clubId: Int,
        tableId: Int,
        guestsCount: Int,
        dateStart: LocalDateTime,
        dateEnd: LocalDateTime,
        comment: String?,
        guestName: String?,
        guestPhone: String?
    ): Pair<Int, Int> // Returns Booking ID and loyalty points earned

    suspend fun findById(id: Int): Booking?

    /**
     * Найти все бронирования для пользователя (по его внутреннему ID).
     * Optionally, could also take telegramId and resolve internal ID.
     */
    suspend fun findAllByUserId(userId: Int): List<Booking>

    /**
     * Обновить статус брони.
     */
    suspend fun updateStatus(bookingId: Int, newStatus: BookingStatus): Boolean

    /**
     * Удалить бронь по ID.
     */
    suspend fun delete(id: Int): Boolean

    /**
     * Проверить, свободен ли стол в указанный период времени.
     * @return true, если стол свободен, false — если уже есть пересекающиеся брони.
     */
    suspend fun isTableAvailableOnPeriod(
        tableId: Int,
        periodStart: LocalDateTime,
        periodEnd: LocalDateTime
    ): Boolean

    // Method for RepositoriesTest
    suspend fun create(booking: Booking): Booking
    suspend fun findAll(): List<Booking> // For RepositoriesTest
}