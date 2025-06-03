package fsm

import kotlinx.coroutines.*
import kotlinx.coroutines.test.runBlockingTest
import org.testng.annotations.Test
import java.time.LocalDate
import kotlin.test.assertEquals

/**
 * Несколько тестов, проверяющих базовые переходы FSM.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BookingStateMachineTest {

    private val testUserId = 12345L

    /**
     * Проверяем простой сценарий: StartBooking → выбираем клуб → выбираем дату → выбираем стол → вводим гостей → вводим имя → вводим телефон → подтверждаем.
     */
    @Test
    fun `full booking flow should end in Finished`() = runBlockingTest {
        val sentMessages = mutableListOf<Pair<Long, String>>()

        // Лямбда-заглушка вместо реальной отправки в Telegram:
        val fakeSend: (Long, String, InlineKeyboard?) -> Unit = { chatId, text, _ ->
            sentMessages += (chatId to text)
        }

        val machine = BookingStateMachine(testUserId, fakeSend)

        // Начальное состояние — Idle
        assertEquals(BookingState.Idle, machine.state.value)

        // Шаг 1. /book
        machine.onEvent(BookingEvent.StartBooking)
        advanceUntilIdle()
        assertEquals(BookingState.ChoosingClub, machine.state.value)
        assert(sentMessages.last().second.contains("Выберите клуб"))

        // Шаг 2. выбираем clubId=7
        machine.onEvent(BookingEvent.ClubSelected(7))
        advanceUntilIdle()
        assertEquals(BookingState.EnteringDate(clubId = 7), machine.state.value)
        assert(sentMessages.last().second.contains("Пожалуйста, выберите дату"))

        // Шаг 3. выбираем дату = 2023-06-20
        val date = LocalDate.of(2023, 6, 20)
        machine.onEvent(BookingEvent.DatePicked(date))
        advanceUntilIdle()
        assertEquals(BookingState.EnteringTable(clubId = 7, date = date), machine.state.value)
        assert(sentMessages.last().second.contains("Выберите стол"))

        // Шаг 4. выбираем стол №3, места=4
        machine.onEvent(BookingEvent.TableSelected(tableNumber = 3, tableSeats = 4))
        advanceUntilIdle()
        assertEquals(
            BookingState.EnteringPeopleCount(
                clubId = 7,
                date = date,
                tableNumber = 3,
                tableSeats = 4
            ),
            machine.state.value
        )
        assert(sentMessages.last().second.contains("Сколько гостей будет"))

        // Шаг 5. вводим людей = 3
        machine.onEvent(BookingEvent.PeopleCountEntered(3))
        advanceUntilIdle()
        assertEquals(
            BookingState.EnteringGuestName(
                clubId = 7,
                date = date,
                tableNumber = 3,
                tableSeats = 4,
                peopleCount = 3
            ), machine.state.value
        )
        assert(sentMessages.last().second.contains("введите имя"))

        // Шаг 6. вводим имя «Иван»
        machine.onEvent(BookingEvent.GuestNameEntered("Иван"))
        advanceUntilIdle()
        assertEquals(
            BookingState.EnteringGuestPhone(
                clubId = 7,
                date = date,
                tableNumber = 3,
                tableSeats = 4,
                peopleCount = 3,
                guestName = "Иван"
            ), machine.state.value
        )
        assert(sentMessages.last().second.contains("введите телефон"))

        // Шаг 7. вводим телефон «+79123456789»
        machine.onEvent(BookingEvent.GuestPhoneEntered("+79123456789"))
        advanceUntilIdle()
        assert(machine.state.value is BookingState.ConfirmBooking)
        assert(sentMessages.last().second.contains("Проверьте детали"))

        // Шаг 8. подтверждаем бронь
        machine.onEvent(BookingEvent.ConfirmBookingNow)
        advanceUntilIdle()
        assertEquals(BookingState.Idle, machine.state.value)
        assert(sentMessages.last().second.contains("Ваша бронь"))
    }

    /**
     * Если на любом этапе прислать CancelBooking, машина перейдет в Cancelled, а потом в Idle.
     */
    @Test
    fun `cancel at any step returns to Idle`() = runBlockingTest {
        val sent = mutableListOf<String>()
        val machine = BookingStateMachine(testUserId) { chatId, text, _ ->
            sent += text
        }

        // Переводим в ChoosingClub
        machine.onEvent(BookingEvent.StartBooking)
        advanceUntilIdle()
        assertEquals(BookingState.ChoosingClub, machine.state.value)

        // Отправляем Cancel
        machine.onEvent(BookingEvent.CancelBooking)
        advanceUntilIdle()
        assertEquals(BookingState.Idle, machine.state.value)
        assert(sent.last().contains("отменено", ignoreCase = true))
    }
}