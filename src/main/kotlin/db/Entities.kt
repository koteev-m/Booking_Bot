package db

import java.time.Instant

/**
 * Сущность «Клуб».
 */
data class Club(
    val id: Int = 0,
    val name: String,
    val address: String,
    val timezone: String,
    val isActive: Boolean = true,
    val createdAt: Any,
    val updatedAt: Any
)

/**
 * Сущность «Стол» (TableInfo).
 */
data class TableInfo(
    val id: Int = 0,
    val clubId: Int,
    val name: String,
    val capacity: Int,
    val isActive: Boolean = true,
    val createdAt: Any,
    val updatedAt: Any
)

/**
 * Сущность «Пользователь» (User).
 */
data class User(
    val id: Int = 0,
    val telegramId: Long,
    val fullName: String,
    val phone: String?,
    val languageCode: String,
    val lastActivity: Instant
)

/**
 * Статусы бронирования.
 */
enum class BookingStatus {
    NEW,
    PENDING,
    CONFIRMED,
    CANCELLED
}

/**
 * Сущность «Бронирование» (Booking).
 */
data class Booking(
    val id: Int = 0,
    val userId: Int,
    val tableId: Int,
    val guestsCount: Int,
    val dateStart: Instant,
    val dateEnd: Instant,
    val status: BookingStatus,
    val comment: String?,
    val guestName: String?,
    val guestPhone: String?,
    val loyaltyPointsEarned: Int?,
    val feedbackRating: Int? = null,
    val feedbackComment: String? = null,
    val createdAt: Instant,
    val updatedAt: Instant? = null
)

/**
 * DTO для вывода бронирования вместе с именем клуба.
 */
data class BookingWithClubName(
    val booking: Booking,
    val clubName: String
)