package bot

import bot.config.EnvConfig
import bot.facade.TelegramBotFacadeImpl
import bot.facade.BotConstants // For callback prefixes
import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.bot
import com.github.kotlintelegrambot.dispatch
import com.github.kotlintelegrambot.dispatcher.callbackQuery
import com.github.kotlintelegrambot.dispatcher.command
import com.github.kotlintelegrambot.dispatcher.text
import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.Update
import com.github.kotlintelegrambot.extensions.filters.Filter
import com.github.kotlintelegrambot.logging.LogLevel as TelegramLogLevelKt // Alias to avoid conflict
import db.DatabaseFactory
import db.migrateDatabase
import db.repositories.*
import fsm.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import bot.facade.StringProviderFactory
import bot.facade.LocalizedStrings
import java.time.LocalDate
import java.time.format.DateTimeParseException

object GlobalBotContext {
    val userMachines = mutableMapOf<Long, BookingStateMachine>()
    lateinit var fsmDepsTemplate: FsmDeps // Template for FSM dependencies
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    lateinit var botInstance: Bot // To hold the bot instance for the facade
}

fun main() {
    val config = EnvConfig.load()

    DatabaseFactory.init(config.db.host, config.db.port, config.db.name, config.db.user, config.db.password)
    migrateDatabase(
        "jdbc:postgresql://${config.db.host}:${config.db.port}/${config.db.name}",
        config.db.user,
        config.db.password
    )

    val usersRepo = UsersRepoImpl()
    val clubsRepo = ClubsRepoImpl()
    val bookingsRepo = BookingsRepoImpl(usersRepo)
    val tablesRepo = TablesRepoImpl(bookingsRepo)

    val telegramBot = bot {
        token = config.botToken
        logLevel = if (config.telegramLogLevel.name.equals("ERROR", true)) TelegramLogLevelKt.Error else TelegramLogLevelKt.None

        GlobalBotContext.botInstance = this.build() // Store the bot instance

        val botFacade = TelegramBotFacadeImpl(GlobalBotContext.botInstance, StringProviderFactory.get(config.defaultTimeZone))

        GlobalBotContext.fsmDepsTemplate = FsmDeps(
            clubsRepo, tablesRepo, bookingsRepo, usersRepo, botFacade,
            GlobalBotContext.applicationScope,
            StringProviderFactory.get(config.defaultTimeZone) // Default strings
        )

        dispatch {
            fun getOrCreateFsm(update: Update): BookingStateMachine? {
                val user = update.message?.from ?: update.callbackQuery?.from
                val chatIdSource = update.message?.chat ?: update.callbackQuery?.message?.chat

                if (user == null || chatIdSource == null) return null
                val userTelegramId = user.id
                val chatIdForUser = ChatId.fromId(chatIdSource.id)

                return GlobalBotContext.userMachines.getOrPut(userTelegramId) {
                    val userSpecificStrings = StringProviderFactory.get(user.languageCode ?: config.defaultTimeZone)
                    BookingStateMachine(
                        deps = GlobalBotContext.fsmDepsTemplate.copy(strings = userSpecificStrings),
                        chatId = chatIdForUser,
                        telegramUserId = userTelegramId
                    )
                }
            }

            command("start") {
                getOrCreateFsm(update)?.onEvent(BookingEvent.Start)
            }
            command("book") {
                getOrCreateFsm(update)?.onEvent(BookingEvent.Start)
            }
            command("cancel") {
                getOrCreateFsm(update)?.onEvent(BookingEvent.Cancel)
            }

            text {
                val fsm = getOrCreateFsm(update) ?: return@text
                GlobalBotContext.applicationScope.launch {
                    val currentState = fsm.state.value
                    val userText = update.message?.text ?: ""
                    when (currentState) {
                        is BookingState.EnteringGuestCount -> {
                            userText.toIntOrNull()?.let { count ->
                                fsm.onEvent(BookingEvent.GuestCountEntered(count))
                            } ?: GlobalBotContext.fsmDepsTemplate.botFacade.askGuestCountInvalid(
                                ChatId.fromId(update.message!!.chat.id), 1, currentState.table.seats, // Max seats from table
                                GlobalBotContext.fsmDepsTemplate.strings, null
                            )
                        }
                        is BookingState.EnteringGuestName -> fsm.onEvent(BookingEvent.GuestNameEntered(userText))
                        is BookingState.EnteringGuestPhone -> fsm.onEvent(BookingEvent.GuestPhoneEntered(userText))
                        else -> { // Not expecting text input or an unhandled command
                            // GlobalBotContext.fsmDepsTemplate.botFacade.sendUnknownCommandMessage(ChatId.fromId(update.message!!.chat.id), GlobalBotContext.fsmDepsTemplate.strings, emptyList(),null)
                            // Or, could trigger a generic help / main menu display
                        }
                    }
                }
            }

            callbackQuery {
                val fsm = getOrCreateFsm(update) ?: return@callbackQuery
                val data = update.callbackQuery?.data ?: ""

                GlobalBotContext.applicationScope.launch {
                    when {
                        data.startsWith(BotConstants.CB_PREFIX_BOOK_CLUB) -> {
                            val clubId = data.removePrefix(BotConstants.CB_PREFIX_BOOK_CLUB).toIntOrNull()
                            clubId?.let { fsm.onEvent(BookingEvent.ClubSelected(it)) }
                        }
                        data.startsWith(BotConstants.CB_PREFIX_CHOOSE_DATE_CAL) -> {
                            // Format: CB_PREFIX_CHOOSE_DATE_CAL:YYYY-MM-DD
                            val dateStr = data.removePrefix(BotConstants.CB_PREFIX_CHOOSE_DATE_CAL)
                            try {
                                val date = LocalDate.parse(dateStr)
                                fsm.onEvent(BookingEvent.DateSelected(date))
                            } catch (e: DateTimeParseException) {
                                GlobalBotContext.fsmDepsTemplate.botFacade.sendErrorMessage(fsm.chatId, fsm.deps.strings, "Неверный формат даты.", emptyList(), update.callbackQuery?.message?.messageId)
                            }
                        }
                        data.startsWith(BotConstants.CB_PREFIX_CHOOSE_TABLE) -> {
                            // Format: CB_PREFIX_CHOOSE_TABLE:tableId
                            val tableId = data.removePrefix(BotConstants.CB_PREFIX_CHOOSE_TABLE).toIntOrNull()
                            if (tableId != null) {
                                val tableInfo = tablesRepo.findById(tableId) // Fetch TableInfo
                                tableInfo?.let { fsm.onEvent(BookingEvent.TableSelected(it)) }
                                    ?: GlobalBotContext.fsmDepsTemplate.botFacade.sendErrorMessage(fsm.chatId, fsm.deps.strings, "Стол не найден.", emptyList(), update.callbackQuery?.message?.messageId)
                            }
                        }
                        data.startsWith(BotConstants.CB_PREFIX_CHOOSE_SLOT) -> {
                            // Format: CB_PREFIX_CHOOSE_SLOT:tableId:HH:MM–HH:MM
                            val parts = data.removePrefix(BotConstants.CB_PREFIX_CHOOSE_SLOT).split(":")
                            if (parts.size >= 2) { // Expect at least tableId and one slot part
                                // val tableIdFromCb = parts[0].toIntOrNull() // Not directly used by SlotSelected event if FSM has table context
                                val slotTime = parts.drop(1).joinToString(":") // Reconstruct HH:MM-HH:MM if it was split by mistake
                                fsm.onEvent(BookingEvent.SlotSelected(slotTime))
                            }
                        }
                        data == BotConstants.CB_PREFIX_CONFIRM_BOOKING -> fsm.onEvent(BookingEvent.ConfirmBooking)
                        data == BotConstants.CB_PREFIX_CANCEL_ACTION -> fsm.onEvent(BookingEvent.Cancel)
                        // Add other callback handlers (main menu items, etc.)
                        else -> {
                            GlobalBotContext.fsmDepsTemplate.botFacade.sendUnknownCommandMessage(fsm.chatId, fsm.deps.strings, emptyList(), update.callbackQuery?.message?.messageId)
                        }
                    }
                }
                // Answer callback query to remove "loading" state on button
                bot.answerCallbackQuery(callbackQueryId = update.callbackQuery!!.id)
            }
        }
    }
    println("Бот запускается...")
    telegramBot.startPolling()
    println("Бот запущен.")
}