package fsm

import bot.facade.LocalizedStrings
import bot.facade.BotConstants
import bot.facade.StringProviderFactory
import bot.facade.chatId
import bot.facade.userId
import bot.facade.userLanguageCode
import bot.facade.userName
import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.Update
import db.repositaries.ClubsRepo
import jdk.jfr.internal.consumer.EventLog.start
import ru.nsk.kstatemachine.*
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeParseException
import org.slf4j.LoggerFactory
import ru.nsk.kstatemachine.statemachine.StateMachine

class FsmHandler(
    private val deps: FsmDeps
) {
    private val logger = LoggerFactory.getLogger(FsmHandler::class.java)
    private val userMachines = mutableMapOf<ChatId, StateMachine<DraftBooking>>()

    private suspend fun getOrCreateMachine(chatId: ChatId, telegramUserId: Long, userName: String?, userInitialLangCode: String?): StateMachine<DraftBooking> {
        return userMachines.getOrPut(chatId) {
            logger.info("FSM_HANDLER: Creating new FSM for chat ${chatId.id}, user $telegramUserId (name: $userName)")
            val dbUser = deps.usersRepo.getOrCreate(telegramUserId, userName, null, userName, userInitialLangCode ?: BotConstants.DEFAULT_LANGUAGE_CODE)
            val initialStrings = StringProviderFactory.get(dbUser.languageCode)

            buildBookingFsm(deps, chatId, telegramUserId, userName, initialStrings).apply {
                this.data.dbUser = dbUser
                this.data.currentLanguageCode = dbUser.languageCode

                onTransition { event, arguments ->
                    logger.debug("FSM_TRANSITION (ChatID: ${chatId.id}, UserID: ${data.dbUser?.id}): ${event?.event?.javaClass?.simpleName} -> ${this.activeStates().joinToString { it.name ?: "N/A" }}. Args: $arguments. Data: $data")
                }
                onStateChanged { newState ->
                    logger.debug("FSM_STATE_CHANGED (ChatID: ${chatId.id}, UserID: ${data.dbUser?.id}): -> ${newState?.name}. Data: $data")
                }
                start() // Start the machine immediately after creation and setup
                logger.info("FSM_HANDLER: FSM for chat ${chatId.id} started. Initial state: ${this.activeStates().firstOrNull()?.name}")
            }
        }
    }

    suspend fun handleUpdate(update: Update) {
        val chatId = update.chatId ?: run {
            logger.warn("FSM_HANDLER_ERROR: Could not extract chatId from update: $update")
            return
        }
        val telegramUserId = update.userId ?: run {
            logger.warn("FSM_HANDLER_ERROR: Could not extract userId from update: $update for chatId: ${chatId.id}")
            return
        }
        val currentUserName = update.userName
        val userInitialLangCode = update.userLanguageCode

        val machine = getOrCreateMachine(chatId, telegramUserId, currentUserName, userInitialLangCode)

        update.message?.messageId?.let { machine.data.currentMessageId = it }
        update.callbackQuery?.message?.messageId?.let { machine.data.currentMessageId = it }

        val currentStrings = StringProviderFactory.get(machine.data.currentLanguageCode)

        val fsmEvent = mapUpdateToFsmEvent(update, chatId, telegramUserId, machine.data.currentMessageId, currentStrings, deps.clubsRepo)

        if (fsmEvent == null) {
            if (update.message?.text != null && update.message?.text?.startsWith("/") == false) { // Only treat non-commands as unknown input
                logger.info("FSM_HANDLER (ChatID: ${chatId.id}): Unmapped text input '${update.message?.text}', sending UnknownInputEvent.")
                machine.processEvent(UnknownInputEvent(chatId, telegramUserId, machine.data.currentMessageId, currentStrings, update.message?.text))
            } else if (update.message?.text?.startsWith("/") == true) {
                logger.info("FSM_HANDLER (ChatID: ${chatId.id}): Unhandled command '${update.message?.text}'.")
                // Optionally send a "command not recognized" message if not handled by specific command listeners
            }
            else {
                logger.warn("FSM_HANDLER_WARN (ChatID: ${chatId.id}): Could not map update to FSM event and not a text message. Update: $update")
            }
            return
        }

        try {
            logger.info("FSM_HANDLER (ChatID: ${chatId.id}): Processing event ${fsmEvent::class.simpleName}")
            machine.processEvent(fsmEvent)
        } catch (e: Exception) {
            logger.error("FSM_HANDLER_ERROR (ChatID: ${chatId.id}): Error processing FSM event $fsmEvent: ${e.message}", e)
            val errorEvent = ErrorOccurredEvent(chatId, telegramUserId, machine.data.currentMessageId, currentStrings, e, currentStrings.errorMessageDefault)
            try { machine.processEvent(errorEvent) } catch (e2: Exception) { logger.error("FSM_HANDLER_ERROR (ChatID: ${chatId.id}): Error processing ErrorOccurredEvent: ${e2.message}", e2) }
        }

        if (machine.isFinished) {
            logger.info("FSM_HANDLER (ChatID: ${chatId.id}): FSM finished. Final state: ${machine.activeStates().firstOrNull()?.name}. Removing from active machines.")
            userMachines.remove(chatId)
        }
    }

    private suspend fun mapUpdateToFsmEvent(
        update: Update,
        chatId: ChatId,
        telegramUserId: Long,
        messageId: Long?,
        strings: LocalizedStrings,
        clubsRepo: ClubsRepo
    ): FsmEvent? {
        val text = update.message?.text
        val callbackData = update.callbackQuery?.data
        val userName = update.userName
        val userInitialLangCode = update.userLanguageCode

        if (text != null) {
            when (text.lowercase()) {
                strings.startBookingCommand.lowercase(), "/start" -> return StartCommandEvent(chatId, telegramUserId, messageId, strings, userName, userInitialLangCode)
                strings.menuCommand.lowercase(), "/menu" -> return MainMenuActionChosenEvent(chatId, telegramUserId, messageId, strings, MainMenuActionType.SHOW_VENUE_INFO) // Default to showing venue info or main menu
                strings.helpCommand.lowercase(), "/help" -> return HelpCommandEvent(chatId, telegramUserId, messageId, strings)
                strings.langCommand.lowercase(), "/lang" -> return ChangeLanguageCommandEvent(chatId, telegramUserId, messageId, strings)
            }

            when (text) {
                strings.menuVenueInfo -> return MainMenuActionChosenEvent(chatId, telegramUserId, messageId, strings, MainMenuActionType.SHOW_VENUE_INFO)
                strings.menuMyBookings -> return MainMenuActionChosenEvent(chatId, telegramUserId, messageId, strings, MainMenuActionType.MY_BOOKINGS)
                strings.menuAskQuestion -> return MainMenuActionChosenEvent(chatId, telegramUserId, messageId, strings, MainMenuActionType.ASK_QUESTION)
                strings.menuOpenApp -> return MainMenuActionChosenEvent(chatId, telegramUserId, messageId, strings, MainMenuActionType.OPEN_APP)
                strings.menuHelp -> return MainMenuActionChosenEvent(chatId, telegramUserId, messageId, strings, MainMenuActionType.SHOW_HELP)
                strings.menuChangeLanguage -> return MainMenuActionChosenEvent(chatId, telegramUserId, messageId, strings, MainMenuActionType.CHANGE_LANGUAGE)
                else -> {
                    val allClubs = clubsRepo.getAllClubs(activeOnly = true)
                    allClubs.forEach { club ->
                        if (text == strings.menuBookTableInClub(club.title)) {
                            // This event will trigger transition from MainMenu to ChooseDate, setting the club.
                            return ClubChosenForBookingEvent(chatId, telegramUserId, messageId, strings, club.id)
                        }
                    }
                }
            }

            val currentFsmStateName = userMachines[chatId]?.activeStates()?.firstOrNull()?.name
            return when (currentFsmStateName) {
                FsmStates.EnterPeople.name -> text.toIntOrNull()?.let { PeopleEnteredEvent(chatId, telegramUserId, messageId, strings, it) }
                FsmStates.EnterGuestName.name -> GuestNameEnteredEvent(chatId, telegramUserId, messageId, strings, text)
                FsmStates.EnterGuestPhone.name -> GuestPhoneEnteredEvent(chatId, telegramUserId, messageId, strings, text)
                FsmStates.AskQuestionInput.name -> QuestionTextEnteredEvent(chatId, telegramUserId, messageId, strings, text)
                else -> null
            }
        }

        if (callbackData != null) {
            return when {
                callbackData.startsWith(BotConstants.CB_PREFIX_SELECT_LANG) ->
                    LanguageSelectedEvent(chatId, telegramUserId, messageId, strings, callbackData.removePrefix(BotConstants.CB_PREFIX_SELECT_LANG))
                callbackData.startsWith(BotConstants.CB_PREFIX_BOOK_CLUB) ->
                    callbackData.removePrefix(BotConstants.CB_PREFIX_BOOK_CLUB).toIntOrNull()?.let {
                        ClubChosenForBookingEvent(chatId, telegramUserId, messageId, strings, it)
                    }
                callbackData.startsWith(BotConstants.CB_PREFIX_CHOOSE_DATE_CAL) -> {
                    try { LocalDate.parse(callbackData.removePrefix(BotConstants.CB_PREFIX_CHOOSE_DATE_CAL))?.let { DateChosenEvent(chatId, telegramUserId, messageId, strings, it) } }
                    catch (e: DateTimeParseException) { logger.warn("Invalid date callback: $callbackData"); null }
                }
                callbackData.startsWith(BotConstants.CB_PREFIX_CAL_MONTH_CHANGE) -> {
                    try { YearMonth.parse(callbackData.substringAfterLast("_"))?.let { CalendarMonthChangeEvent(chatId, telegramUserId, messageId, strings, it) } }
                    catch (e: Exception) { logger.warn("Invalid calendar month change callback: $callbackData"); null }
                }
                callbackData.startsWith(BotConstants.CB_PREFIX_CHOOSE_TABLE) ->
                    callbackData.removePrefix(BotConstants.CB_PREFIX_CHOOSE_TABLE).toIntOrNull()?.let {
                        TableChosenEvent(chatId, telegramUserId, messageId, strings, it)
                    }
                callbackData.startsWith(BotConstants.CB_PREFIX_CHOOSE_SLOT) -> {
                    val parts = callbackData.removePrefix(BotConstants.CB_PREFIX_CHOOSE_SLOT).split(':')
                    if (parts.size == 2) {
                        val startEpoch = parts[0].toLongOrNull(); val endEpoch = parts[1].toLongOrNull()
                        if (startEpoch != null && endEpoch != null) {
                            SlotChosenEvent(chatId, telegramUserId, messageId, strings, Instant.ofEpochSecond(startEpoch), Instant.ofEpochSecond(endEpoch))
                        } else { logger.warn("Invalid slot epoch seconds in callback: $callbackData"); null }
                    } else { logger.warn("Invalid slot callback format: $callbackData"); null }
                }
                callbackData == BotConstants.CB_PREFIX_CONFIRM_BOOKING -> ConfirmBookingPressedEvent(chatId, telegramUserId, messageId, strings)
                callbackData == BotConstants.CB_PREFIX_CANCEL_ACTION -> CancelActionPressedEvent(chatId, telegramUserId, messageId, strings)
                callbackData.startsWith(BotConstants.CB_PREFIX_BACK_TO) ->
                    BackPressedEvent(chatId, telegramUserId, messageId, strings, callbackData.removePrefix(BotConstants.CB_PREFIX_BACK_TO))

                callbackData == BotConstants.CB_MAIN_MENU_VENUE_INFO -> MainMenuActionChosenEvent(chatId, telegramUserId, messageId, strings, MainMenuActionType.SHOW_VENUE_INFO)
                callbackData == BotConstants.CB_MAIN_MENU_MY_BOOKINGS -> MainMenuActionChosenEvent(chatId, telegramUserId, messageId, strings, MainMenuActionType.MY_BOOKINGS)
                callbackData == BotConstants.CB_MAIN_MENU_ASK_QUESTION -> MainMenuActionChosenEvent(chatId, telegramUserId, messageId, strings, MainMenuActionType.ASK_QUESTION)
                callbackData == BotConstants.CB_MAIN_MENU_OPEN_APP -> MainMenuActionChosenEvent(chatId, telegramUserId, messageId, strings, MainMenuActionType.OPEN_APP)
                callbackData == BotConstants.CB_MAIN_MENU_HELP -> MainMenuActionChosenEvent(chatId, telegramUserId, messageId, strings, MainMenuActionType.SHOW_HELP)
                callbackData == BotConstants.CB_MAIN_MENU_CHANGE_LANG -> MainMenuActionChosenEvent(chatId, telegramUserId, messageId, strings, MainMenuActionType.CHANGE_LANGUAGE)

                callbackData.startsWith(BotConstants.CB_PREFIX_VENUE_INFO_SHOW) ->
                    callbackData.removePrefix(BotConstants.CB_PREFIX_VENUE_INFO_SHOW).toIntOrNull()?.let {
                        VenueChosenForInfoEvent(chatId, telegramUserId, messageId, strings, it)
                    }
                callbackData.startsWith(BotConstants.CB_PREFIX_VENUE_POSTERS) -> // WIP
                    callbackData.removePrefix(BotConstants.CB_PREFIX_VENUE_POSTERS).toIntOrNull()?.let {
                        VenuePostersRequest(chatId, telegramUserId, messageId, strings, it)
                    }
                callbackData.startsWith(BotConstants.CB_PREFIX_VENUE_PHOTOS) -> // WIP
                    callbackData.removePrefix(BotConstants.CB_PREFIX_VENUE_PHOTOS).toIntOrNull()?.let {
                        VenuePhotosRequest(chatId, telegramUserId, messageId, strings, it)
                    }
                callbackData.startsWith(BotConstants.CB_PREFIX_MANAGE_BOOKING) ->
                    callbackData.removePrefix(BotConstants.CB_PREFIX_MANAGE_BOOKING).toIntOrNull()?.let {
                        ManageBookingPressedEvent(chatId, telegramUserId, messageId, strings, it)
                    }
                callbackData.startsWith(BotConstants.CB_PREFIX_DO_CANCEL_BOOKING) ->
                    callbackData.removePrefix(BotConstants.CB_PREFIX_DO_CANCEL_BOOKING).toIntOrNull()?.let {
                        ExecuteCancelBookingEvent(chatId, telegramUserId, messageId, strings, it)
                    }
                callbackData.startsWith(BotConstants.CB_PREFIX_DO_CHANGE_BOOKING) ->
                    callbackData.removePrefix(BotConstants.CB_PREFIX_DO_CHANGE_BOOKING).toIntOrNull()?.let {
                        ExecuteChangeBookingInfoEvent(chatId, telegramUserId, messageId, strings, it)
                    }
                callbackData.startsWith(BotConstants.CB_PREFIX_RATE_BOOKING) -> {
                    val parts = callbackData.removePrefix(BotConstants.CB_PREFIX_RATE_BOOKING).split(":")
                    val bookingId = parts.getOrNull(0)?.toIntOrNull()
                    val rating = parts.getOrNull(1)?.toIntOrNull()
                    bookingId?.let { RateBookingEvent(chatId, telegramUserId, messageId, strings, it, rating) }
                }
                else -> { logger.warn("Unknown callback data: $callbackData"); null }
            }
        }
        return null
    }
}