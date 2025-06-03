package db.repositories

import db.BookingStatus // Import from db.Entities
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.javatime.datetime
import java.time.LocalDateTime

/**
 * Таблица клубов.
 * Based on V001__Initial_schema.sql
 */
object ClubsTable : IntIdTable("clubs") {
    val code = text("code").uniqueIndex() // MACHINE name
    val title = text("title")             // Читаемое имя
    val description = text("description").nullable()
    val address = text("address").nullable()
    val phone = text("phone").nullable()
    val workingHours = text("working_hours").nullable()
    val timezone = text("timezone").default("Europe/Moscow")
    val photoUrl = text("photo_url").nullable()
    val floorPlanImageUrl = text("floor_plan_image_url").nullable()
    val isActive = bool("is_active").default(true)
    val createdAt = datetime("created_at").clientDefault { LocalDateTime.now() }
    // V001 schema doesn't explicitly list an 'updated_at' for clubs,
    // but it's good practice if updates are possible.
    // val updatedAt = datetime("updated_at").nullable()
}

/**
 * Таблица столов.
 * Based on V001__Initial_schema.sql
 */
object TablesTable : IntIdTable("tables") {
    val clubId = reference("club_id", ClubsTable)
    val number = integer("number") // Номер стола
    val seats = integer("seats")   // Кол-во мест
    val description = text("description").nullable()
    val posX = integer("pos_x").nullable()
    val posY = integer("pos_y").nullable()
    val photoUrl = text("photo_url").nullable()
    val isActive = bool("is_active").default(true)
    // V001 doesn't have created_at/updated_at for tables, but often useful.
    // Adding them as per your original Tables.kt definition:
    val createdAt = datetime("created_at").clientDefault { LocalDateTime.now() }
    val updatedAt = datetime("updated_at").nullable()

    init {
        uniqueIndex(clubId, number) // номер уникален внутри клуба
    }
}

/**
 * Таблица пользователей Telegram.
 * Based on V001__Initial_schema.sql
 */
object UsersTable : IntIdTable("users") { // Using IntIdTable for internal PK `id`
    val telegramId = long("telegram_id").uniqueIndex()
    val firstName = text("first_name").nullable()
    val lastName = text("last_name").nullable()
    val username = text("username").nullable()
    val phone = text("phone").nullable()
    val languageCode = varchar("language_code", 5).default("ru")
    val loyaltyPoints = integer("loyalty_points").default(0)
    val createdAt = datetime("created_at").clientDefault { LocalDateTime.now() }
    val lastActivityAt = datetime("last_activity_at").clientDefault { LocalDateTime.now() }
}

/**
 * Таблица бронирований.
 * Based on V001__Initial_schema.sql
 */
object BookingsTable : IntIdTable("bookings") {
    val clubId = reference("club_id", ClubsTable)
    val tableId = reference("table_id", TablesTable)
    val userId = reference("user_id", UsersTable) // Refers to UsersTable.id (Int)
    val guestsCount = integer("guests_count") // CHECK (guests_count > 0) is handled by DB
    val dateStart = datetime("date_start") // начало интервала (timestamptz in SQL -> LocalDateTime in Kotlin)
    val dateEnd = datetime("date_end")     // конец интервала (timestamptz in SQL -> LocalDateTime in Kotlin)
    val status = enumerationByName("status", 20, BookingStatus::class)
        .default(BookingStatus.NEW)
    val comment = text("comment").nullable()
    val guestName = text("guest_name").nullable() // V001 uses text
    val guestPhone = text("guest_phone").nullable() // V001 uses text
    val loyaltyPointsEarned = integer("loyalty_points_earned").nullable()
    val feedbackRating = integer("feedback_rating").nullable()
    val feedbackComment = text("feedback_comment").nullable()
    val createdAt = datetime("created_at").clientDefault { LocalDateTime.now() }
    val updatedAt = datetime("updated_at").nullable()

    // GiST index for overlaps is handled by V001__Initial_schema.sql, not directly in Exposed table definition
}