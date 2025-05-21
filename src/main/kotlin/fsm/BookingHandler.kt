package fsm

import bot.BotEvent          // из UpdateMapper
import bot.toBotEvent
import com.github.kotlintelegrambot.entities.Update
import bot.chatId

/**
 * «Клей» между Telegram-Update и FSM.
 * Держит session (state) на каждого chatId.
 */
class BookingHandler(private val deps: BookingDeps) {

    private val machine = buildBookingMachine(deps)
    private val sessions = mutableMapOf<Long, BookingState>()

    /** Вызывается из главного update-цикла бота */
    suspend fun handle(update: Update) {
        val chatId = update.chatId ?: return                // игнорируем сервисные апдейты
        val event: BookingEvent  = update.toBotEvent()?.toBookingEvent() ?: return

        val current = sessions.getOrDefault(chatId, BookingState.Start)
        val next    = machine.transition(current, event)    // pure-логика

        sessions[chatId] = when (next) {                    // post-processing
            BookingState.Finished,
            BookingState.Cancelled -> BookingState.Start
            else -> next
        }
    }

    /* -------- маппер BotEvent → BookingEvent -------- */
    private fun BotEvent.toBookingEvent(): BookingEvent? = when (this) {
        BotEvent.StartCmd          -> BookingEvent.StartCmd
        is BotEvent.ClubChosen     -> BookingEvent.ClubChosen(id)
        is BotEvent.TableChosen    -> BookingEvent.TableChosen(id)
        is BotEvent.PeopleEntered  -> BookingEvent.PeopleEntered(count)
        is BotEvent.SlotChosen     -> BookingEvent.SlotChosen(start, end)
        BotEvent.BackPressed       -> BookingEvent.BackPressed
        BotEvent.CancelPressed     -> BookingEvent.CancelPressed
        BotEvent.ConfirmPressed    -> BookingEvent.ConfirmPressed
    }
}