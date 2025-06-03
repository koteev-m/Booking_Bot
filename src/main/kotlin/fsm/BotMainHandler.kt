// src/main/kotlin/bot/BotMainHandler.kt
package bot

import fsm.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Это примерный код, который «подключается» к TelegramApi (Ktor, поллинг или webhook).
 * Давайте предположим, что у нас есть некая функция onUpdate(update: Update),
 * которая вызывается на каждое новое сообщение или callback_query.
 */
object BotMainHandler {

    /** Здесь храним для каждого userId свою машину состояний. */
    private val machines: MutableMap<Long, BookingStateMachine> = mutableMapOf()

    /**
     * Имитируем Telegram Update:
     * - если это callback_query → мы парсим callbackData ("/selectClub:3" и т.д.)
     * - если это обычное text-сообщение → обрабатываем как ввод текста.
     */
    fun onUpdate(update: TelegramUpdate) {
        val userId = update.chatId
        val fsm = machines.getOrPut(userId) {
            BookingStateMachine(userId) { chatId, text, keyboard ->
                // Ваша функция отправки в Telegram
                TelegramApi.sendMessage(chatId, text, keyboard)
            }
        }

        // Если это callback_query
        if (update.callbackData != null) {
            val data = update.callbackData
            when {
                data.startsWith("/selectClub:") -> {
                    val clubId = data.substringAfter("/selectClub:").toInt()
                    fsm.onEvent(BookingEvent.ClubSelected(clubId))
                }
                data.startsWith("/selectDate:") -> {
                    // Допустим, календарь отсылает callback "/selectDate:2023-06-15"
                    val dateStr = data.substringAfter("/selectDate:")
                    val date = LocalDate.parse(dateStr)
                    fsm.onEvent(BookingEvent.DatePicked(date))
                }
                data.startsWith("/selectTable:") -> {
                    // "/selectTable:5:4" → стол №5, 4 места
                    val parts = data.substringAfter("/selectTable:").split(":")
                    val tableNumber = parts[0].toInt()
                    val seats = parts[1].toInt()
                    fsm.onEvent(BookingEvent.TableSelected(tableNumber, seats))
                }
                data == "/confirmBooking" -> {
                    fsm.onEvent(BookingEvent.ConfirmBookingNow)
                }
                data == "/cancelBooking" -> {
                    fsm.onEvent(BookingEvent.CancelBooking)
                }
                // … любые другие callback’и
            }
            return
        }

        // Если это текстовое сообщение (юзер вводит число гостей, имя или телефон)
        if (update.text != null) {
            val text = update.text.trim()
            when (val state = fsm.state.value) {
                is BookingState.EnteringPeopleCount -> {
                    val cnt = text.toIntOrNull()
                    if (cnt != null) {
                        fsm.onEvent(BookingEvent.PeopleCountEntered(cnt))
                    } else {
                        // отправить пользователю «Неверный формат, введите цифрами»
                        TelegramApi.sendMessage(userId, "Пожалуйста, введите число цифрами.", null)
                    }
                }
                is BookingState.EnteringGuestName -> {
                    if (text.isNotBlank()) {
                        fsm.onEvent(BookingEvent.GuestNameEntered(text))
                    } else {
                        TelegramApi.sendMessage(userId, "Имя не может быть пустым.", null)
                    }
                }
                is BookingState.EnteringGuestPhone -> {
                    // Простейшая валидация номера
                    if (text.matches(Regex("^\\+?\\d{7,15}\$"))) {
                        fsm.onEvent(BookingEvent.GuestPhoneEntered(text))
                    } else {
                        TelegramApi.sendMessage(userId, "Неверный формат телефона.", null)
                    }
                }
                else -> {
                    // Если нет специальных состояний, проверяем команды:
                    when (text.lowercase()) {
                        "/book", "/start" -> fsm.onEvent(BookingEvent.StartBooking)
                        "/cancel" -> fsm.onEvent(BookingEvent.CancelBooking)
                        // … любые другие текстовые команды
                        else -> {
                            TelegramApi.sendMessage(userId, "Не понял вас. Воспользуйтесь кнопками или /book.", null)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Простейшие модели для TelegramUpdate и sendMessage.
 * Замените на ваши реальные из KtorBot или прочего.
 */
data class TelegramUpdate(
    val chatId: Long,
    val text: String? = null,
    val callbackData: String? = null
)

object TelegramApi {
    fun sendMessage(chatId: Long, text: String, keyboard: InlineKeyboard?) {
        // Ваша реальная отправка через Ktor → Telegram Bot API
        // Например: client.post("https://api.telegram.org/bot$token/sendMessage") { … }
        println("→ send to $chatId: $text")
        if (keyboard != null) {
            println("   keyboard: $keyboard")
        }
    }
}