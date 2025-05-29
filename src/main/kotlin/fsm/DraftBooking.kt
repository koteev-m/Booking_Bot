package fsm

import bot.facade.BotConstants
import db.Booking
import db.BookingStatus
import db.Club
import db.TableInfo
import db.User
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

data class DraftBooking(
    // FSM context
    var currentMessageId: Long? = null,
    var dbUser: User? = null,
    var currentLanguageCode: String = BotConstants.DEFAULT_LANGUAGE_CODE,

    // Booking flow data
    var club: Club? = null,
    var selectedDate: LocalDate? = null,
    var tableInfo: TableInfo? = null,
    var guests: Int? = null,
    var slotStart: Instant? = null, // This will be the full Instant
    var slotEnd: Instant? = null,   // This will be the full Instant
    var guestName: String? = null,
    var guestPhone: String? = null,

    // Other flows data
    var selectedVenueForInfo: Club? = null,
    var selectedBookingToManageId: Int? = null,
    var userQuestion: String? = null,
    var bookingToRateId: Int? = null
) {
    val clubId: Int? get() = club?.id
    val tableId: Int? get() = tableInfo?.id

    fun toBookingEntity(): Booking? {
        val currentClub = club ?: return null
        val currentTable = tableInfo ?: return null
        val currentUser = dbUser ?: return null
        val currentGuests = guests ?: return null
        val currentSlotStart = slotStart ?: return null
        val currentSlotEnd = slotEnd ?: return null
        val currentGuestName = guestName ?: return null
        val currentGuestPhone = guestPhone ?: return null
        // selectedDate is used to construct slotStart/End, so it's implicitly checked

        // Calculate loyalty points (example logic)
        val points = currentGuests * BotConstants.POINTS_PER_GUEST

        return Booking(
            id = 0, // Will be set by DB
            clubId = currentClub.id,
            tableId = currentTable.id,
            userId = currentUser.id,
            guestsCount = currentGuests,
            dateStart = currentSlotStart,
            dateEnd = currentSlotEnd,
            status = BookingStatus.NEW, // Initial status
            comment = null,
            guestName = currentGuestName,
            guestPhone = currentGuestPhone,
            loyaltyPointsEarned = points,
            createdAt = Instant.now()
            // feedbackRating and feedbackComment are null initially
            // updatedAt is null initially
        )
    }

    fun clearBookingData() {
        club = null
        selectedDate = null
        tableInfo = null
        guests = null
        slotStart = null
        slotEnd = null
        guestName = null
        guestPhone = null
    }

    fun clearOtherFlowData() {
        selectedVenueForInfo = null
        selectedBookingToManageId = null
        userQuestion = null
        bookingToRateId = null
    }

    // Helper to combine LocalDate and LocalTime into Instant for slotStart/End
    // This should be done when SlotChosenEvent is created or handled.
    // Kept here for reference if needed elsewhere, but FSM should handle this.
    fun combineDateTime(date: LocalDate?, time: LocalTime?, zoneId: ZoneId): Instant? {
        return if (date != null && time != null) {
            date.atTime(time).atZone(zoneId).toInstant()
        } else {
            null
        }
    }
}