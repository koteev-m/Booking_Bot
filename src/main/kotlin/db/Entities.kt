package db

import java.time.Instant

data class Club(
    val id: Int,
    val code: String,
    val title: String,
    val timezone: String,
    val createdAt: Instant
)

data class TableInfo(
    val id: Int,
    val clubId: Int,
    val number: Int,
    val seats: Int,
    val photoUrl: String?,
    val isActive: Boolean
)

data class User(
    val id: Int,
    val telegramId: Long,
    val firstName: String?,
    val lastName: String?,
    val username: String?,
    val phone: String?,
    val createdAt: Instant
)

data class Booking(
    val id: Int,
    val clubId: Int,
    val tableId: Int,
    val userId: Int?,
    val guestsCount: Int,
    val dateStart: Instant,
    val dateEnd: Instant,
    val status: BookingStatus,
    val comment: String?,
    val createdAt: Instant
)