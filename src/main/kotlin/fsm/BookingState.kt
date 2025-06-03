package fsm

import java.time.LocalDate

/**
 * Все возможные состояния нашего FSM для одного пользователя.
 */
sealed class BookingState {

    /** Ничего не делаем, ждём команды /book или /menu */
    object Idle : BookingState()

    /** Выбор клуба (пользователь увидел клавиатуру со списком клубов) */
    object ChoosingClub : BookingState()

    /**
     * Пользователь выбрал клуб, надо выбрать дату.
     * @param clubId — идентификатор выбранного клуба
     */
    data class EnteringDate(val clubId: Int) : BookingState()

    /**
     * Пользователь выбрал дату, надо выбрать стол.
     * @param clubId — из какого клуба
     * @param date — выбранная дату
     */
    data class EnteringTable(
        val clubId: Int,
        val date: LocalDate
    ) : BookingState()

    /**
     * Пользователь выбрал стол, надо ввести количество гостей.
     * @param clubId — из какого клуба
     * @param date — выбранная дата
     * @param tableNumber — номер выбранного стола
     * @param tableSeats — сколько мест у стола (из таблицы)
     */
    data class EnteringPeopleCount(
        val clubId: Int,
        val date: LocalDate,
        val tableNumber: Int,
        val tableSeats: Int
    ) : BookingState()

    /**
     * Пользователь ввёл количество гостей, нужно ввести имя гостя.
     */
    data class EnteringGuestName(
        val clubId: Int,
        val date: LocalDate,
        val tableNumber: Int,
        val tableSeats: Int,
        val peopleCount: Int
    ) : BookingState()

    /**
     * Пользователь ввёл имя, нужно ввести телефон.
     */
    data class EnteringGuestPhone(
        val clubId: Int,
        val date: LocalDate,
        val tableNumber: Int,
        val tableSeats: Int,
        val peopleCount: Int,
        val guestName: String
    ) : BookingState()

    /**
     * Пользователь ввёл телефон, нужно подтвердить бронь.
     */
    data class ConfirmBooking(
        val clubId: Int,
        val date: LocalDate,
        val tableNumber: Int,
        val tableSeats: Int,
        val peopleCount: Int,
        val guestName: String,
        val guestPhone: String
    ) : BookingState()

    /** Бронь подтверждена (финальное состояние) */
    object Finished : BookingState()

    /** Бронь отменена (финальное состояние) */
    object Cancelled : BookingState()
}