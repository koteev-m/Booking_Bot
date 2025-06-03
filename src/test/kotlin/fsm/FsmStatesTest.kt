package fsm

import bot.facade.TelegramBotFacade
import bot.model.ChatEvent
import bot.model.Command
import fsm.state.BookingState
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Юнит-тест FSM: проверяем последовательность «/start → chooseTable →
 * guestCount → confirm».
 *
 * Сеть Telegram не трогаем — мокируем BotFacade.
 */
class FsmStatesTest {

    @Test
    fun `booking flow goes through happy path`() = runTest {
        /* 1. Мокаем фасад Telegram-бота */
        val botFacade = mockk<TelegramBotFacade>(relaxed = true)

        /* 2. Депсы и FSM */
        val fsmDeps = mockk<FsmDeps>() {
            every { this@mockk.bot } returns botFacade
            // дополнительные репо можно мокировать/стаббить аналогично
        }
        val handler = FsmHandler(fsmDeps)

        /* 3. Стартуем сценарий */
        val chatId = ChatId(123_456_789L)
        handler.handleEvent(chatId, ChatEvent.Command(Command.START))

        /* 4. Выбираем стол (id = 1) */
        handler.handleEvent(chatId, ChatEvent.TableChosen(tableId = 1))

        /* 5. Указываем кол-во гостей = 4 */
        handler.handleEvent(chatId, ChatEvent.GuestCountProvided(count = 4))

        /* 6. Подтверждаем */
        handler.handleEvent(chatId, ChatEvent.Confirmed)

        /* 7. Проверяем, что FSM упала в финальное состояние */
        val session = handler.sessionStore[chatId]     // pseudo-код: где вы храните сессии
        assertEquals(BookingState.FINISHED, session?.currentState)

        /* 8. Проверяем, что бот попытался отправить финальное сообщение */
        verify {
            botFacade.sendMessage(
                chatId = chatId,
                text = match { it.contains("Бронирование подтверждено") }
            )
        }
    }
}