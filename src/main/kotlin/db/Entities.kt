package db

import java.time.LocalDateTime

/**
 * Сущность «Клуб».
 */
data class Club(
    val id: Int = 0,
    val name: String,
    val code: String, // Added based on V001__Initial_schema.sql
    val description: String?, // Added based on V001__Initial_schema.sql
    val address: String?, // Changed to nullable to match V001
    val phone: String?, // Added based on V001__Initial_schema.sql
    val workingHours: String?, // Added based on V001__Initial_schema.sql
    val timezone: String,
    val photoUrl: String?, // Added based on V001__Initial_schema.sql
    val floorPlanImageUrl: String?, // Added based on V001__Initial_schema.sql
    val isActive: Boolean = true,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime?
)

/**
 * Сущность «Стол» (TableInfo).
 */
data class TableInfo(
    val id: Int = 0,
    val clubId: Int,
    val number: Int, // Changed from name, to align with V001 schema (tables.number)
    val seats: Int, // Changed from capacity, to align with V001 schema (tables.seats)
    val description: String?, // Added based on V001 schema
    val posX: Int?, // Added based on V001 schema
    val posY: Int?, // Added based on V001 schema
    val photoUrl: String?, // Added based on V001 schema
    val isActive: Boolean = true,
    // createdAt and updatedAt are not in V001 for tables, but good to have.
    // If they are managed by DB default/triggers, they might not be in constructor.
    // For now, let's assume they are part of the entity if read.
    // val createdAt: LocalDateTime, // Let's remove these if not directly in V001 for tables
    // val updatedAt: LocalDateTime?
    val label: String // Convenient field, often table.number or a name
)

/**
 * Сущность «Пользователь» (User).
 */
data class User(
    val id: Int = 0, // Internal DB ID
    val telegramId: Long,
    val firstName: String?, // From V001
    val lastName: String?, // From V001
    val username: String?, // From V001
    val phone: String?,
    val languageCode: String,
    val loyaltyPoints: Int, // From V001
    val createdAt: LocalDateTime, // From V001
    val lastActivityAt: LocalDateTime // From V001
)

/**
 * Статусы бронирования.
 * Aligning with V001 and V002 SQL migrations.
 */
enum class BookingStatus {
    NEW,
    CONFIRMED,
    CANCELLED,
    COMPLETED, // from V002
    AWAITING_FEEDBACK, // from V002
    ARCHIVED // from V002
    // PENDING was in Entities.kt but not in SQL schema, removed for now
}

/**
 * Сущность «Бронирование» (Booking).
 * Aligning with V001__Initial_schema.sql bookings table.
 */
data class Booking(
    val id: Int = 0,
    val clubId: Int, // from V001
    val tableId: Int,
    val userId: Int, // Internal user ID (references users.id)
    val guestsCount: Int,
    val dateStart: LocalDateTime, // V001 uses timestamptz
    val dateEnd: LocalDateTime,   // V001 uses timestamptz
    val status: BookingStatus,
    val comment: String?,
    val guestName: String?,
    val guestPhone: String?,
    val loyaltyPointsEarned: Int?,
    val feedbackRating: Int? = null,
    val feedbackComment: String? = null,
    val createdAt: LocalDateTime, // V001 uses timestamptz
    val updatedAt: LocalDateTime? = null // V001 uses timestamptz
)

/**
 * DTO для вывода бронирования вместе с именем клуба.
 */
data class BookingWithClubName(
    val booking: Booking,
    val clubName: String,
    val tableNumber: Int // Added for convenience in display
)