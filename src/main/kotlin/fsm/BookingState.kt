package fsm

import db.Club
import db.TableInfo // Assuming TableInfo is in db package (db.Entities)
import java.time.LocalDate

/**
 * Все возможные состояния нашего FSM для одного пользователя.
 * This version is based on the structure from the original BookingStateMachine.kt provided,
 * which is more detailed than the simpler BookingState.kt.
 * We will consolidate to this more detailed version.
 */
sealed class BookingState {
    /** Начальное состояние: ничего не делаем, ждем команды Start */
    object Idle : BookingState()

    /** Показ списка клубов */
    data class ShowingClubs(val clubs: List<Club>) : BookingState() // Club from db.Entities

    /** Показ доступных дат для выбранного клуба */
    data class ShowingDates(
        val clubId: Int,
        val clubTitle: String,
        val availableDates: List<LocalDate> // Assuming LocalDate is suitable here
    ) : BookingState()

    /** Показ доступных столов для выбранного клуба и даты */
    data class ShowingTables(
        val clubId: Int,
        val clubTitle: String,
        val date: LocalDate,
        val tables: List<TableInfo> // TableInfo from db.Entities
    ) : BookingState()

    /** Показ доступных слотов (временных интервалов) для выбранного стола */
    data class ShowingSlots(
        val clubId: Int,
        val clubTitle: String,
        val date: LocalDate,
        val table: TableInfo, // Use the TableInfo entity
        val availableSlots: List<String> // Example: "19:00-20:00"
    ) : BookingState()

    /** Ввод количества гостей */
    data class EnteringGuestCount(
        val clubId: Int,
        val clubTitle: String,
        val date: LocalDate,
        val table: TableInfo,
        val slot: String
    ) : BookingState()

    /**
     * Промежуточный класс для последовательного сбора данных перед созданием окончательного DraftBooking.
     * This DraftBookingTemp is used internally by states.
     */
    data class DraftBookingTemp(
        val clubId: Int,
        val clubTitle: String,
        val table: TableInfo,
        val date: LocalDate,
        val slot: String,
        val peopleCount: Int? = null,
        val guestName: String? = null,
        val guestPhone: String? = null
    ) {
        fun withCount(count: Int): DraftBookingTemp = copy(peopleCount = count)
        fun withName(name: String): DraftBookingTemp = copy(guestName = name)
        fun withPhone(phone: String): DraftBookingTemp = copy(guestPhone = phone)

        fun toFinal(): DraftBooking { // DraftBooking would be another data class, potentially similar to this
            require(peopleCount != null) { "peopleCount not set" }
            require(guestName != null) { "guestName not set" }
            require(guestPhone != null) { "guestPhone not set" }
            return DraftBooking(
                clubId = clubId,
                clubTitle = clubTitle,
                tableId = table.id, // Assuming TableInfo has id
                tableLabel = table.label, // Assuming TableInfo has a label or similar
                date = date,
                slot = slot,
                peopleCount = peopleCount,
                guestName = guestName,
                guestPhone = guestPhone
            )
        }
    }

    /** Ввод имени гостя */
    data class EnteringGuestName(val draft: DraftBookingTemp) : BookingState()

    /** Ввод телефона гостя */
    data class EnteringGuestPhone(val draft: DraftBookingTemp) : BookingState()

    /** Подтверждение брони (показ деталей) */
    data class ConfirmingBooking(val draft: DraftBookingTemp) : BookingState()

    /** Завершено успешно: бронь сохранена */
    data class BookingDone(
        val bookingId: Int, // This should match the type of Booking.id from db.Entities
        val loyaltyPoints: Int
    ) : BookingState()

    /** Состояние отмены */
    object Cancelled : BookingState()

    /** Ошибка — показываем сообщение пользователю */
    data class Error(val message: String) : BookingState()

    // Added from the simpler BookingState.kt for completeness, if still used by FsmStatesTest
    /** Выбор клуба (пользователь увидел клавиатуру со списком клубов) */
    object ChoosingClub : BookingState() // This might be redundant if ShowingClubs is always used

    /** Бронь подтверждена (финальное состояние for FsmStatesTest) */
    object FINISHED : BookingState() // Renamed to avoid conflict with BookingDone, used by FsmStatesTest
}

// This is the DraftBooking that DraftBookingTemp converts to.
// It should align with the data needed for booking confirmation messages and saving.
data class DraftBooking(
    val clubId: Int,
    val clubTitle: String,
    val tableId: Int,
    val tableLabel: String, // e.g. "Table 5" or "VIP 1"
    val date: LocalDate,
    val slot: String, // e.g., "18:00-20:00"
    val peopleCount: Int,
    val guestName: String,
    val guestPhone: String
)