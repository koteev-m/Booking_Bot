package fsm

import db.TableInfo // Assuming TableInfo is in db package (db.Entities)
import java.time.LocalDate

/**
 * Все события, которые могут прийти из Telegram-обработчика
 * и которые запускают переходы у FSM.
 * This version is based on the structure from the original BookingStateMachine.kt provided.
 */
sealed class BookingEvent {
    /** Пользователь начал новый процесс бронирования */
    object Start : BookingEvent() // Renamed from StartBooking for consistency with BookingStateMachine

    /** Пользователь выбрал клуб (по его ID) */
    data class ClubSelected(val clubId: Int) : BookingEvent()

    /** Пользователь выбрал дату (LocalDate) */
    data class DateSelected(val date: LocalDate) : BookingEvent() // Renamed from DatePicked

    /** Пользователь выбрал стол (объект TableInfo) */
    data class TableSelected(val table: TableInfo) : BookingEvent() // Using TableInfo entity

    /** Пользователь выбрал временной слот (строка, например "19:00–20:00") */
    data class SlotSelected(val slot: String) : BookingEvent()

    /** Пользователь ввел количество гостей */
    data class GuestCountEntered(val count: Int) : BookingEvent() // Renamed from PeopleCountEntered

    /** Пользователь ввел имя гостя */
    data class GuestNameEntered(val name: String) : BookingEvent()

    /** Пользователь ввел телефон гостя */
    data class GuestPhoneEntered(val phone: String) : BookingEvent()

    /** Пользователь подтвердил создание брони */
    object ConfirmBooking : BookingEvent() // Renamed from ConfirmBookingNow

    /** Пользователь отменил операцию в любой момент */
    object Cancel : BookingEvent() // Renamed from CancelBooking

    // Events from the original simpler BookingEvent.kt, if still needed by tests like FsmStatesTest
    // These might be duplicates or can be mapped from the more detailed events above.
    // For FsmStatesTest.kt, it uses ChatEvent which is different.
    // Let's assume the events above are canonical for BookingStateMachine.kt.
    // Timeout event was in the simpler version, might be useful.
    /**
     * Системное событие «timeout» или «ошибка» — автоматически отменяем.
     * (например, если 15 минут пользователь не действовал)
     */
    object Timeout : BookingEvent()
}