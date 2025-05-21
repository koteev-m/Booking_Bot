package bot

import com.github.kotlintelegrambot.entities.CallbackQuery
import com.github.kotlintelegrambot.entities.Message
import com.github.kotlintelegrambot.entities.Update
import java.time.*
import java.time.format.DateTimeFormatter

/**
 * Промежуточное «сырья» из Telegram до событий FSM.
 * Здесь парсим текст/кнопки и превращаем их в BotEvent.
 */
sealed interface BotEvent {
    object StartCmd          : BotEvent
    data class ClubChosen(val id: Int)     : BotEvent
    data class TableChosen(val id: Int)    : BotEvent
    data class PeopleEntered(val count: Int) : BotEvent
    data class SlotChosen(val start: Instant, val end: Instant) : BotEvent
    object BackPressed       : BotEvent
    object CancelPressed     : BotEvent
    object ConfirmPressed    : BotEvent
}

/* --- утилити для Update --- */

val Update.chatId: Long?
    get() = message?.chat?.id ?: callbackQuery?.message?.chat?.id

fun Update.toBotEvent(): BotEvent? {
    // 1) /book
    message?.text?.let { txt ->
        return when {
            txt.equals("/book", ignoreCase = true)     -> BotEvent.StartCmd
            txt.matches(Regex("\\d+"))                  -> BotEvent.PeopleEntered(txt.toInt())
            txt.equals("back",   true)                  -> BotEvent.BackPressed
            txt.equals("cancel", true)                  -> BotEvent.CancelPressed
            txt.equals("ok",     true)                  -> BotEvent.ConfirmPressed
            txt.matches(Regex("\\d{2}:\\d{2}-\\d{2}:\\d{2}")) -> {
                val (st, en) = txt.split('-')
                val today = LocalDate.now()
                val start = today.atTime(LocalTime.parse(st)).toInstant(ZoneOffset.UTC)
                val end   = if (en < st) today.plusDays(1).atTime(LocalTime.parse(en))
                else today.atTime(LocalTime.parse(en))
                BotEvent.SlotChosen(start, end.toInstant(ZoneOffset.UTC))
            }
            txt.uppercase() in listOf("MIX","OSOBNYAK","CLUB3","CLUB4") ->
                BotEvent.ClubChosen(clubNameToId(txt.uppercase()))
            txt.matches(Regex("\\d+ стол")) -> {        // e.g. «3 стол»
                val id = txt.split(' ')[0].toInt()
                BotEvent.TableChosen(id)
            }
            else -> null
        }
    }

    // 2) CallbackQuery (если будете использовать inline-кнопки)
    callbackQuery?.let(::handleCallback)

    return null
}

private fun handleCallback(cb: CallbackQuery): BotEvent? =
    when (val data = cb.data) {
        "back"   -> BotEvent.BackPressed
        "cancel" -> BotEvent.CancelPressed
        "ok"     -> BotEvent.ConfirmPressed
        else     -> null           // или декодируйте data самостоятельно
    }

private fun clubNameToId(code: String): Int = when (code) {
    "MIX"       -> 1
    "OSOBNYAK"  -> 2
    "CLUB3"     -> 3
    "CLUB4"     -> 4
    else        -> error("unknown club")
}