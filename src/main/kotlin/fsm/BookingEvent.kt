// src/main/kotlin/fsm/BookingEvent.kt
package fsm

import java.time.LocalDate

/**
 * Все события, которые могут прийти из Telegram-обработчика
 * и которые запускают переходы у FSM.
 */
sealed class BookingEvent {

    /** Пользователь нажал команду "/book" (начинаем FSM) */
    object StartBooking : BookingEvent()

    /** Пользователь нажал «Отмена» (в любой момент) */
    object CancelBooking : BookingEvent()

    /**
     * Пользователь выбрал клуб из списка.
     * @param clubId — ID клуба
     */
    data class ClubSelected(val clubId: Int) : BookingEvent()

    /**
     * Пользователь выбрал дату (получаем уже LocalDate из вашего календаря).
     * @param date — выбранная дата
     */
    data class DatePicked(val date: LocalDate) : BookingEvent()

    /**
     * Пользователь нажал на кнопку стол №X (получаем номер стола и кол-во мест из вашего массива).
     */
    data class TableSelected(val tableNumber: Int, val tableSeats: Int) : BookingEvent()

    /**
     * Пользователь ввёл текст с количеством гостей (парсится в Int).
     */
    data class PeopleCountEntered(val count: Int) : BookingEvent()

    /**
     * Пользователь ввёл текст с именем гостя.
     */
    data class GuestNameEntered(val guestName: String) : BookingEvent()

    /**
     * Пользователь ввёл текст с телефоном.
     */
    data class GuestPhoneEntered(val guestPhone: String) : BookingEvent()

    /**
     * Пользователь подтвердил бронь на экране ConfirmBooking.
     */
    object ConfirmBookingNow : BookingEvent()

    /**
     * Системное событие «timeout» или «ошибка» — автоматически отменяем.
     * (например, если 15 минут пользователь не действовал)
     */
    object Timeout : BookingEvent()
}