package db.repositories

import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.javatime.datetime
import java.time.LocalDateTime
import db.BookingStatus

/**
 * Таблица пользователей.
 */
object UsersTable : IntIdTable("users") {
    val telegramId    = long("telegram_id").uniqueIndex()
    val fullName      = varchar("full_name", length = 100)
    val phone         = varchar("phone", length = 20).nullable()
    val languageCode  = varchar("language_code", length = 10).default("ru")
    val lastActivity  = datetime("last_activity").clientDefault { LocalDateTime.now() }
}

/**
 * Таблица клубов.
 */
object ClubsTable : IntIdTable("clubs") {
    val name        = varchar("name", length = 100)
    val address     = varchar("address", length = 255)
    val timezone    = varchar("timezone", length = 50).default("Europe/Moscow")
    val isActive    = bool("is_active").default(true)
    val createdAt   = datetime("created_at").clientDefault { LocalDateTime.now() }
    val updatedAt   = datetime("updated_at").nullable()
}

/**
 * Таблица столов (TableInfo).
 */
object TablesTable : IntIdTable("tables") {
    val clubId      = reference("club_id", ClubsTable)
    val name        = varchar("name", length = 100)
    val capacity    = integer("capacity")
    val isActive    = bool("is_active").default(true)
    val createdAt   = datetime("created_at").clientDefault { LocalDateTime.now() }
    val updatedAt   = datetime("updated_at").nullable()
}

/**
 * Таблица бронирований.
 */
object BookingsTable : IntIdTable("bookings") {
    val userId             = reference("user_id", UsersTable)
    val tableId            = reference("table_id", TablesTable)
    val guestsCount        = integer("guests_count")
    val dateStart          = datetime("date_start")
    val dateEnd            = datetime("date_end")
    val status             = enumerationByName("status", 20, BookingStatus::class).default(BookingStatus.NEW)
    val comment            = text("comment").nullable()
    val guestName          = varchar("guest_name", length = 100).nullable()
    val guestPhone         = varchar("guest_phone", length = 20).nullable()
    val loyaltyPointsEarned = integer("loyalty_points_earned").nullable()
    val feedbackRating     = integer("feedback_rating").nullable()
    val feedbackComment    = text("feedback_comment").nullable()
    val createdAt          = datetime("created_at").clientDefault { LocalDateTime.now() }
    val updatedAt          = datetime("updated_at").nullable()
}