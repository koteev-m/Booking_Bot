package db // Corrected package

import java.time.Instant

data class Club(
    val id: Int,
    val code: String,
    val title: String,
    val description: String?,
    val address: String?,
    val phone: String?,
    val workingHours: String?,
    val timezone: String, // e.g., "Europe/Moscow"
    val photoUrl: String?,
    val floorPlanImageUrl: String?,
    val isActive: Boolean = true,
    val createdAt: Instant
)

data class TableInfo(
    val id: Int,
    val clubId: Int,
    val number: Int, // User-visible table number
    val seats: Int,
    val posX: Int?, // Relative X for floor plan button positioning (optional)
    val posY: Int?, // Relative Y for floor plan button positioning (optional)
    val photoUrl: String?,
    val isActive: Boolean,
    val description: String?
)

data class User(
    val id: Int, // Internal DB ID
    val telegramId: Long, // Telegram's unique user ID
    var firstName: String?,
    var lastName: String?,
    var username: String?, // Telegram @username
    var phone: String?, // User-provided phone
    var languageCode: String,
    var loyaltyPoints: Int = 0,
    val createdAt: Instant,
    var lastActivityAt: Instant
)

data class Booking(
    val id: Int,
    val clubId: Int,
    val tableId: Int,
    val userId: Int, // Foreign key to User table
    val guestsCount: Int,
    val dateStart: Instant, // Full start date and time in UTC
    val dateEnd: Instant,   // Full end date and time in UTC
    var status: BookingStatus,
    val comment: String?,
    val guestName: String?,
    val guestPhone: String?,
    val loyaltyPointsEarned: Int?,
    var feedbackRating: Int? = null, // 1-5 stars
    var feedbackComment: String? = null,
    val createdAt: Instant,
    var updatedAt: Instant? = null
)

data class BookingWithClubName(
    val id: Int,
    val clubId: Int,
    val clubName: String,
    val tableId: Int,
    val tableNumber: Int,
    val userId: Int,
    val guestsCount: Int,
    val dateStart: Instant,
    val dateEnd: Instant,
    val status: BookingStatus,
    val guestName: String?,
    val guestPhone: String?,
    val createdAt: Instant,
    val feedbackRating: Int?
)

enum class BookingStatus {
    NEW,        // Booking created by user, awaiting confirmation (if any)
    CONFIRMED,  // Booking confirmed by system/admin
    CANCELLED,  // Booking cancelled by user or admin
    COMPLETED,  // User attended (or booking time passed)
    AWAITING_FEEDBACK, // After COMPLETED, if feedback is pending
    ARCHIVED    // Old bookings, not typically shown
}

// Feedback can be part of Booking entity or a separate table if more complex
// data class Feedback(
// val id: Int,
// val bookingId: Int,
// val userId: Int,
// val rating: Int, // 1-5
// val comment: String?,
// val createdAt: Instant
// )