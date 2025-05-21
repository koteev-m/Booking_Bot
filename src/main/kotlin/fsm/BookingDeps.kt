package fsm                         // тот же пакет, что и FSM

import db.BookingsRepo
import db.TablesRepo

/** Обёртка над Telegram-API, которую вы реализуете сами */
interface BotFacade {
    suspend fun sendChooseClubKeyboard(chatId: Long)
    suspend fun sendChooseTableKeyboard(chatId: Long, tables: List<db.TableInfo>)
    suspend fun askPeopleCount(chatId: Long)
    suspend fun sendChooseSlotKeyboard(chatId: Long, clubId: Int, tableId: Int)
    suspend fun sendConfirmMessage(chatId: Long, draft: DraftBooking)
    suspend fun sendSuccessMessage(chatId: Long, bookingId: Int)
    suspend fun sendCancelledMessage(chatId: Long)
}

data class BookingDeps(
    val bot         : BotFacade,
    val tablesRepo  : TablesRepo,
    val bookingsRepo: BookingsRepo,
)