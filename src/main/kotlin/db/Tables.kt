package db

import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.javatime.timestamp
import java.time.Instant
import bot.BotConstants //

object UsersTable : IntIdTable("users") {
    val telegramId = long("telegram_id").uniqueIndex() //
    val firstName = text("first_name").nullable() //
    val lastName = text("last_name").nullable() //
    val username = text("username").nullable() //
    val phone = text("phone").nullable() //
    val languageCode = varchar("language_code", 5).default(BotConstants.DEFAULT_LANGUAGE_CODE) //
    val loyaltyPoints = integer("loyalty_points").default(0) //
    val createdAt = timestamp("created_at").clientDefault { Instant.now() } //
    val lastActivityAt = timestamp("last_activity_at").clientDefault { Instant.now() } //
}

object ClubsTable : IntIdTable("clubs") {
    val code = text("code").uniqueIndex() //
    val title = text("title") //
    val description = text("description").nullable() //
    val address = text("address").nullable() //
    // Corrected column name from "club_phone" to "phone" to match V001__Initial_schema.sql
    val phone = text("phone").nullable() //
    val workingHours = text("working_hours").nullable() //
    val timezone = text("timezone").default("Europe/Moscow") //
    val photoUrl = text("photo_url").nullable() //
    val floorPlanImageUrl = text("floor_plan_image_url").nullable() //
    val isActive = bool("is_active").default(true) //
    val createdAt = timestamp("created_at").clientDefault { Instant.now() } //
}

object TablesTable : IntIdTable("tables") {
    val clubId = reference("club_id", ClubsTable) //
    val number = integer("number") //
    val seats = integer("seats") //
    val description = text("description").nullable() //
    val posX = integer("pos_x").nullable() //
    val posY = integer("pos_y").nullable() //
    val photoUrl = text("photo_url").nullable() //
    val isActive = bool("is_active").default(true) //
    init { uniqueIndex(clubId, number) } //
}

object BookingsTable : IntIdTable("bookings") {
    val clubId = reference("club_id", ClubsTable) //
    val tableId = reference("table_id", TablesTable) //
    val userId = reference("user_id", UsersTable) //
    val guestsCount = integer("guests_count") //
    val dateStart = timestamp("date_start") //
    val dateEnd = timestamp("date_end") //
    // Ensure BookingStatus ENUM is correctly mapped
    val status = enumerationByName("status", 20, BookingStatus::class).default(BookingStatus.NEW) //
    val comment = text("comment").nullable() //
    val guestName = text("guest_name").nullable() //
    val guestPhone = text("guest_phone").nullable() //
    val loyaltyPointsEarned = integer("loyalty_points_earned").nullable() //
    val feedbackRating = integer("feedback_rating").nullable() //
    val feedbackComment = text("feedback_comment").nullable() //
    val createdAt = timestamp("created_at").clientDefault { Instant.now() } //
    val updatedAt = timestamp("updated_at").nullable() //
}