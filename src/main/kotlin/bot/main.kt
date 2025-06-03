package bot

import bot.config.EnvConfig
import bot.facade.TelegramBotFacadeImpl
import bot.facade.BotConstants
import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.bot
import com.github.kotlintelegrambot.dispatch
import com.github.kotlintelegrambot.dispatcher.callbackQuery
import com.github.kotlintelegrambot.dispatcher.command
import com.github.kotlintelegrambot.dispatcher.text
import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.Update
// import com.github.kotlintelegrambot.extensions.filters.Filter // Not used yet
import com.github.kotlintelegrambot.logging.LogLevel as TelegramLogLevelKt
import db.DatabaseFactory // Assuming DatabaseFactory is in db package
// Assuming migrateDatabase is in db package and handles Flyway
import db.flyway.migrateDatabase // Corrected import path based on common practice
import db.repositories.*
import fsm.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import bot.facade.StringProviderFactory
import java.time.LocalDate
import java.time.format.DateTimeParseException

object GlobalBotContext {
    val userMachines = mutableMapOf<Long, BookingStateMachine>()
    lateinit var fsmDepsTemplate: FsmDeps
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    lateinit var botInstance: Bot
}

fun main() {
    val config = EnvConfig.load()

    DatabaseFactory.init(config.db.host, config.db.port, config.db.name, config.db.user, config.db.password)
    migrateDatabase( // This function needs to be defined, e.g., in a db.flyway package
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

        GlobalBotContext.botInstance = this.build()

        val botFacade = TelegramBotFacadeImpl(GlobalBotContext.botInstance, StringProviderFactory.get(config.defaultTimeZone))

        GlobalBotContext.fsmDepsTemplate = FsmDeps(
            clubsRepo, tablesRepo, bookingsRepo, usersRepo, botFacade,
            GlobalBotContext.applicationScope,
            StringProviderFactory.get(config.defaultTimeZone)
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
                        chatId = chatIdForUser, // This is correct for BookingStateMachine
                        telegramUserId = userTelegramId // Pass the Long telegramId as well
                    )
                }
            }

            command("start") { getOrCreateFsm(update)?.onEvent(BookingEvent.Start) }
            command("book") { getOrCreateFsm(update)?.onEvent(BookingEvent.Start) }
            command("cancel") { getOrCreateFsm(update)?.onEvent(BookingEvent.Cancel) }

            text {
                val fsm = getOrCreateFsm(update) ?: return@text
                val fsmChatId = fsm.chatId // Access public val chatId from BookingStateMachine
                val fsmDeps = fsm.deps     // Access public val deps from BookingStateMachine

                GlobalBotContext.applicationScope.launch {
                    val currentState = fsm.state.value
                    val userText = update.message?.text ?: ""
                    when (currentState) {
                        is BookingState.EnteringGuestCount -> {
                            userText.toIntOrNull()?.let { count ->
                                fsm.onEvent(BookingEvent.GuestCountEntered(count))
                            } ?: fsmDeps.botFacade.askGuestCountInvalid(
                                fsmChatId, 1, currentState.table.seats,
                                fsmDeps.strings, null
                            )
                        }
                        is BookingState.EnteringGuestName -> fsm.onEvent(BookingEvent.GuestNameEntered(userText))
                        is BookingState.EnteringGuestPhone -> fsm.onEvent(BookingEvent.GuestPhoneEntered(userText))
                        else -> {
                            // fsmDeps.botFacade.sendUnknownCommandMessage(fsmChatId, fsmDeps.strings, emptyList(), null)
                        }
                    }
                }
            }

            callbackQuery {
                val fsm = getOrCreateFsm(update) ?: return@callbackQuery
                val data = update.callbackQuery?.data ?: ""
                val fsmChatId = fsm.chatId // Access public val
                val fsmDeps = fsm.deps     // Access public val

                GlobalBotContext.applicationScope.launch {
                    when {
                        data.startsWith(BotConstants.CB_PREFIX_BOOK_CLUB) -> {
                            data.removePrefix(BotConstants.CB_PREFIX_BOOK_CLUB).toIntOrNull()
                                ?.let { fsm.onEvent(BookingEvent.ClubSelected(it)) }
                        }
                        data.startsWith(BotConstants.CB_PREFIX_CHOOSE_DATE_CAL) -> {
                            val dateStr = data.removePrefix(BotConstants.CB_PREFIX_CHOOSE_DATE_CAL)
                            try {
                                LocalDate.parse(dateStr).let { fsm.onEvent(BookingEvent.DateSelected(it)) }
                            } catch (e: DateTimeParseException) {
                                fsmDeps.botFacade.sendErrorMessage(fsmChatId, fsmDeps.strings, "Неверный формат даты.", emptyList(), update.callbackQuery?.message?.messageId)
                            }
                        }
                        data.startsWith(BotConstants.CB_PREFIX_CHOOSE_TABLE) -> {
                            data.removePrefix(BotConstants.CB_PREFIX_CHOOSE_TABLE).toIntOrNull()?.let { tableId ->
                                tablesRepo.findById(tableId)?.let { fsm.onEvent(BookingEvent.TableSelected(it)) }
                                    ?: fsmDeps.botFacade.sendErrorMessage(fsmChatId, fsmDeps.strings, "Стол не найден.", emptyList(), update.callbackQuery?.message?.messageId)
                            }
                        }
                        data.startsWith(BotConstants.CB_PREFIX_CHOOSE_SLOT) -> {
                            val slotTime = data.substringAfterLast(":") // Assuming format "prefix:tableId:HH:MM-HH:MM" or "prefix:slotTime"
                            if (slotTime.contains("–")) { // Basic check for slot format
                                fsm.onEvent(BookingEvent.SlotSelected(slotTime))
                            }
                        }
                        data == BotConstants.CB_PREFIX_CONFIRM_BOOKING -> fsm.onEvent(BookingEvent.ConfirmBooking)
                        data == BotConstants.CB_PREFIX_CANCEL_ACTION -> fsm.onEvent(BookingEvent.Cancel)
                        else -> {
                            // fsmDeps.botFacade.sendUnknownCommandMessage(fsmChatId, fsmDeps.strings, emptyList(), update.callbackQuery?.message?.messageId)
                        }
                    }
                }
                bot.answerCallbackQuery(callbackQueryId = update.callbackQuery!!.id)
            }
        }
    }
    println("Бот запускается...")
    telegramBot.startPolling() // Start polling after the bot is fully built.
    println("Бот запущен.")
}

// Definition for migrateDatabase needs to be available, e.g. in db.flyway package
// package db.flyway
// import org.flywaydb.core.Flyway
// fun migrateDatabase(jdbcUrl: String, user: String, pass: String) {
//     Flyway.configure().dataSource(jdbcUrl, user, pass).load().migrate()
// }