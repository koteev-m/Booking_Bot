package bot

import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.entities.ParseMode
import db.TableInfo
import fsm.BotFacade
import fsm.DraftBooking

/**
 * Реализация интерфейса BotFacade для kotlin-telegram-bot.
 * Здесь пока «черновые» sendMessage, чтобы видеть поток.
 * Позже можно заменить на красивые inline-клавиатуры.
 */
class TelegramBotFacade(private val bot: Bot) : BotFacade {

    private fun send(chatId: Long, text: String) =
        bot.sendMessage(chatId, text, ParseMode.HTML)

    override suspend fun sendChooseClubKeyboard(chatId: Long) =
        send(chatId, "Выберите клуб (пока просто введите MIX / OSOBNYAK / CLUB3 / CLUB4):")

    override suspend fun sendChooseTableKeyboard(chatId: Long, tables: List<TableInfo>) =
        send(chatId, "Доступные столы: ${tables.joinToString { it.number.toString() }}")

    override suspend fun askPeopleCount(chatId: Long) =
        send(chatId, "Сколько гостей придёт? (числом)")

    override suspend fun sendChooseSlotKeyboard(chatId: Long, clubId: Int, tableId: Int) =
        send(chatId, "Введите время слота в формате 23:00-01:00 (упрощённо)")

    override suspend fun sendConfirmMessage(chatId: Long, draft: DraftBooking) =
        send(chatId, "Подтвердите бронь: $draft\nНапишите OK / back / cancel")

    override suspend fun sendSuccessMessage(chatId: Long, bookingId: Int) =
        send(chatId, "✅ Бронь #$bookingId создана. Спасибо!")

    override suspend fun sendCancelledMessage(chatId: Long) =
        send(chatId, "Бронирование отменено.")
}