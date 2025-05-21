package db

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp          // ← java-time


/* ---------- Clubs ---------- */
object ClubsTable : Table("clubs") {
    val id        = integer("id").autoIncrement()
    val code      = text("code")
    val title     = text("title")
    val timezone  = text("timezone")
    val createdAt = timestamp("created_at")
    override val primaryKey = PrimaryKey(id)
}

/* ---------- Tables ---------- */
object TablesTable : Table("tables") {
    val id       = integer("id").autoIncrement()
    val clubId   = integer("club_id").references(ClubsTable.id)
    val number   = integer("number")
    val seats    = integer("seats")
    val photoUrl = text("photo_url").nullable()
    val isActive = bool("is_active")
    override val primaryKey = PrimaryKey(id)
}

/* ---------- Users ---------- */
object UsersTable : Table("users") {
    val id         = integer("id").autoIncrement()
    val telegramId = long("telegram_id")
    val firstName  = text("first_name").nullable()
    val lastName   = text("last_name").nullable()
    val username   = text("username").nullable()
    val phone      = text("phone").nullable()
    val createdAt  = timestamp("created_at")
    override val primaryKey = PrimaryKey(id)
}

/* ---------- Bookings ---------- */
enum class BookingStatus { NEW, CONFIRMED, CANCELLED }

object BookingsTable : Table("bookings") {
    val id          = integer("id").autoIncrement()
    val clubId      = integer("club_id").references(ClubsTable.id)
    val tableId     = integer("table_id").references(TablesTable.id)
    val userId      = integer("user_id").references(UsersTable.id).nullable()
    val guestsCount = integer("guests_count")
    val dateStart   = timestamp("date_start")
    val dateEnd     = timestamp("date_end")
    val status      = enumerationByName("status", 10, BookingStatus::class)
    val comment     = text("comment").nullable()
    val createdAt   = timestamp("created_at")
    override val primaryKey = PrimaryKey(id)
}
