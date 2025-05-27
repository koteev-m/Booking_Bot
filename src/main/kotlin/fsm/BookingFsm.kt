package fsm

import StringProviderFactory
import bot.BotConstants
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import ru.nsk.kstatemachine.*
import ru.nsk.kstatemachine.coroutines.CoroutinesLibCoroutineAbstraction
import java.time.Instant
import java.time.YearMonth
import java.time.temporal.ChronoUnit

// KSM Coroutine setup
private val ksmInternalScope = CoroutineScope(Dispatchers.Default + SupervisorJob() + CoroutineName("KStateMachineInternal"))
private val ksmCoroutineAbstraction = CoroutinesLibCoroutineAbstraction(ksmInternalScope)


fun buildBookingFsm(
    deps: FsmDeps,
    chatIdForLog: com.github.kotlintelegrambot.entities.ChatId, // For logging context
    telegramUserIdForLog: Long, // For logging context
    userNameForLog: String?, // For logging context
    initialStrings: bot.LocalizedStrings
) = createStateMachine( // Using createStateMachine from KSM library directly
    name = "MainFSM-${chatIdForLog.id}-${telegramUserIdForLog}",
    childMode = ChildMode.EXCLUSIVE,
    start = false, // Explicit start managed by FsmHandler
    coroutineAbstraction = ksmCoroutineAbstraction,
    initialData = DraftBooking(currentLanguageCode = initialStrings.languageCode)
) {
    val logger = LoggerFactory.getLogger("FSM.${this.name}") // Logger specific to this FSM instance

    val totalBookingSteps = 8 // ChooseClub, ChooseDate, ChooseTable, EnterPeople, ChooseSlot, EnterGuestName, EnterGuestPhone, ConfirmBooking

    // Global transition for unhandled events
    globalTransition<UnknownInputEvent>(name = "GlobalUnknownInputHandler") {
        action {
            if (this.targetState !is FinalState) {
                logger.info("Unknown input '${event.text}', current state ${this.sourceState.name}")
                scope.launch { deps.bot.sendUnknownCommandMessage(event.chatId, event.strings, deps.clubsRepo.getAllClubs(), data.currentMessageId) }
                targetState = FsmStates.MainMenu
            }
        }
    }
    globalTransition<CancelActionPressedEvent>(name = "GlobalCancelAction") {
        targetState = FsmStates.ActionCancelled
        action {
            logger.info("Action cancelled by user from state ${this.sourceState.name}")
            scope.launch { deps.bot.sendActionCancelledMessage(event.chatId, event.strings, deps.clubsRepo.getAllClubs(), data.currentMessageId) }
        }
    }
    globalTransition<ErrorOccurredEvent>(name = "GlobalErrorOccurredHandler") {
        targetState = FsmStates.ErrorOccurred
        action {
            logger.error("Error occurred: ${event.exception.message}. User message: ${event.userVisibleMessage}", event.exception)
            scope.launch {
                deps.bot.sendErrorMessage(event.chatId, event.strings, event.userVisibleMessage ?: event.strings.errorMessageDefault, deps.clubsRepo.getAllClubs(), data.currentMessageId)
            }
        }
    }

    addState(FsmStates.Initial) {
        onEntry {
            logger.info("Entered InitialLoadingState. Waiting for user/language init.")
        }
        transition<StartCommandEvent>("InitToMainMenu") {
            targetState = FsmStates.MainMenu
            action {
                data.dbUser = deps.usersRepo.getOrCreate(event.telegramUserId, event.userName, null, event.userName, event.userInitialLangCode ?: data.currentLanguageCode)
                data.currentLanguageCode = data.dbUser!!.languageCode
                logger.info("Initialized with lang ${data.currentLanguageCode}. User: ${data.dbUser?.username ?: "Unknown"}")
                // MainMenu onEntry will send welcome
            }
        }
    }

    addState(FsmStates.MainMenu) {
        onEntry {
            logger.info("Entered MainMenuState with lang ${data.currentLanguageCode}")
            data.clearBookingData()
            data.clearOtherFlowData()
            val strings = StringProviderFactory.get(data.currentLanguageCode)
            val clubs = deps.clubsRepo.getAllClubs(activeOnly = true)
            scope.launch { deps.bot.sendWelcomeMessage(event.chatId, data.dbUser?.username ?: userNameForLog, strings, clubs) }
        }

        transition<StartCommandEvent>("MenuToBookingStart") {
            targetState = FsmStates.ChooseClub
            action {
                val strings = StringProviderFactory.get(data.currentLanguageCode)
                val clubs = deps.clubsRepo.getAllClubs(activeOnly = true)
                scope.launch { deps.bot.sendChooseClubKeyboard(event.chatId, clubs, strings, data.currentMessageId, 1 to totalBookingSteps) }
            }
        }

        transition<MainMenuActionChosenEvent>("HandleMenuSelection") {
            action {
                val strings = StringProviderFactory.get(data.currentLanguageCode)
                val clubs = deps.clubsRepo.getAllClubs(activeOnly = true)
                logger.info("MainMenuAction: ${event.actionType}")
                when (event.actionType) {
                    MainMenuActionType.SHOW_VENUE_INFO -> {
                        targetState = FsmStates.ShowVenueList
                        scope.launch { deps.bot.sendVenueSelection(event.chatId, clubs, strings, data.currentMessageId) }
                    }
                    MainMenuActionType.MY_BOOKINGS -> {
                        targetState = FsmStates.ShowMyBookingsList
                        val bookings = deps.bookingsRepo.byUserId(data.dbUser!!.id)
                        scope.launch { deps.bot.sendMyBookingsList(event.chatId, bookings, strings, clubs, data.currentMessageId) }
                    }
                    MainMenuActionType.BOOK_CLUB_BY_ID -> {
                        logger.warn("BOOK_CLUB_BY_ID action received directly in MainMenu, should be handled by ClubChosenForBookingEvent if mapped from ReplyKeyboard by FsmHandler.")
                        targetState = FsmStates.ChooseClub // Fallback
                        scope.launch { deps.bot.sendChooseClubKeyboard(event.chatId, clubs, strings, data.currentMessageId, 1 to totalBookingSteps) }
                    }
                    MainMenuActionType.ASK_QUESTION -> {
                        targetState = FsmStates.AskQuestionInput
                        scope.launch { deps.bot.sendAskQuestionPrompt(event.chatId, strings, data.currentMessageId) }
                    }
                    MainMenuActionType.OPEN_APP -> {
                        scope.launch { deps.bot.sendFeatureInDevelopmentMessage(event.chatId, strings, data.currentMessageId) }
                        // Stays in MainMenu
                    }
                    MainMenuActionType.SHOW_HELP -> targetState = FsmStates.ShowHelp
                    MainMenuActionType.CHANGE_LANGUAGE -> targetState = FsmStates.ChangeLanguage
                }
            }
        }
        transition<HelpCommandEvent>("MenuToHelp") { targetState = FsmStates.ShowHelp }
        transition<ChangeLanguageCommandEvent>("MenuToChangeLang") { targetState = FsmStates.ChangeLanguage }

        transition<ClubChosenForBookingEvent>("MenuQuickBookToDate") {
            targetState = FsmStates.ChooseDate
            action {
                data.club = deps.clubsRepo.findById(event.clubId)
                val strings = StringProviderFactory.get(data.currentLanguageCode)
                if (data.club == null || !data.club!!.isActive) { // Check if club is active
                    targetState = FsmStates.MainMenu
                    scope.launch { deps.bot.sendErrorMessage(event.chatId, strings, strings.venueNotFoundInfo, deps.clubsRepo.getAllClubs(activeOnly = true), data.currentMessageId) }
                } else {
                    logger.info("Quick booking for ${data.club?.title}. Moving to ChooseDate.")
                    scope.launch { deps.bot.sendCalendar(event.chatId, YearMonth.now(deps.defaultZoneId), strings, data.currentMessageId, 2 to totalBookingSteps) }
                }
            }
        }
    }

    addState(FsmStates.ChangeLanguage) {
        onEntry {
            val strings = StringProviderFactory.get(data.currentLanguageCode) // Use current lang to ask for new one
            scope.launch { deps.bot.sendLanguageSelection(event.chatId, strings, data.currentMessageId) }
        }
        transition<LanguageSelectedEvent>("LanguageActuallySelected") {
            targetState = FsmStates.MainMenu
            action {
                data.currentLanguageCode = event.selectedLanguageCode
                deps.usersRepo.updateLanguage(data.dbUser!!.id, event.selectedLanguageCode)
                logger.info("Language changed to ${data.currentLanguageCode}")
                // MainMenu onEntry will show updated welcome in new language
            }
        }
        transition<BackPressedEvent>("BackFromLangToMenu") {
            guard = {event.targetStateName == FsmStates.MainMenu.name}
            targetState = FsmStates.MainMenu
        }
    }

    addState(FsmStates.ShowHelp) {
        onEntry {
            val strings = StringProviderFactory.get(data.currentLanguageCode)
            scope.launch { deps.bot.sendHelpMessage(event.chatId, strings, deps.clubsRepo.getAllClubs(activeOnly = true), data.currentMessageId) }
            switchTo(FsmStates.MainMenu) // Automatically return to main menu
        }
    }

    addState(FsmStates.ChooseClub) {
        onEntry {
            val strings = StringProviderFactory.get(data.currentLanguageCode)
            val clubs = deps.clubsRepo.getAllClubs(activeOnly = true)
            if (event !is StartCommandEvent && event !is MainMenuActionChosenEvent) {
                scope.launch { deps.bot.sendChooseClubKeyboard(event.chatId, clubs, strings, data.currentMessageId, 1 to totalBookingSteps) }
            }
        }
        transition<ClubChosenForBookingEvent>("ClubToDate") {
            targetState = FsmStates.ChooseDate
            action {
                data.club = deps.clubsRepo.findById(event.clubId)
                val strings = StringProviderFactory.get(data.currentLanguageCode)
                if (data.club == null || !data.club!!.isActive) {
                    targetState = FsmStates.ChooseClub
                    scope.launch { deps.bot.sendErrorMessage(event.chatId, strings, strings.venueNotFoundInfo, deps.clubsRepo.getAllClubs(activeOnly = true), data.currentMessageId) }
                } else {
                    logger.info("Club ${data.club?.title} chosen. Moving to ChooseDate.")
                    scope.launch { deps.bot.sendCalendar(event.chatId, YearMonth.now(deps.defaultZoneId), strings, data.currentMessageId, 2 to totalBookingSteps) }
                }
            }
        }
        transition<BackPressedEvent>("BackFromClubToMenu") {
            guard = {event.targetStateName == FsmStates.MainMenu.name}
            targetState = FsmStates.MainMenu
        }
    }

    addState(FsmStates.ChooseDate) {
        onEntry {
            if (data.club == null) { targetState = FsmStates.ChooseClub; return@onEntry }
            if (event !is ClubChosenForBookingEvent && event !is CalendarMonthChangeEvent) {
                val strings = StringProviderFactory.get(data.currentLanguageCode)
                scope.launch { deps.bot.sendCalendar(event.chatId, data.selectedDate?.let { YearMonth.from(it)} ?: YearMonth.now(deps.defaultZoneId), strings, data.currentMessageId, 2 to totalBookingSteps) }
            }
        }
        transition<DateChosenEvent>("DateToTable") {
            targetState = FsmStates.ChooseTable
            action {
                data.selectedDate = event.date
                val strings = StringProviderFactory.get(data.currentLanguageCode)
                val tables = deps.tablesRepo.listAvailableByClubAndDate(data.clubId!!, event.date, activeOnly = true)
                logger.info("Date ${event.date} chosen for club ${data.club?.title}. Found ${tables.size} available tables.")
                scope.launch { deps.bot.sendChooseTableKeyboard(event.chatId, tables, data.club!!, event.date, strings, data.currentMessageId, 3 to totalBookingSteps) }
            }
        }
        transition<CalendarMonthChangeEvent>("ChangeCalendarMonth") {
            targetState = FsmStates.ChooseDate
            action {
                val strings = StringProviderFactory.get(data.currentLanguageCode)
                scope.launch { deps.bot.sendCalendar(event.chatId, event.targetYearMonth, strings, data.currentMessageId, 2 to totalBookingSteps) }
            }
        }
        transition<BackPressedEvent>("BackFromDateToClub") {
            guard = {event.targetStateName == FsmStates.ChooseClub.name}
            targetState = FsmStates.ChooseClub
            action {
                data.selectedDate = null
                val strings = StringProviderFactory.get(data.currentLanguageCode)
                val clubs = deps.clubsRepo.getAllClubs(activeOnly = true)
                scope.launch { deps.bot.sendChooseClubKeyboard(event.chatId, clubs, strings, data.currentMessageId, 1 to totalBookingSteps) }
            }
        }
    }

    addState(FsmStates.ChooseTable) {
        onEntry {
            if (data.club == null || data.selectedDate == null) { targetState = FsmStates.ChooseDate; return@onEntry }
            if (event !is DateChosenEvent) { // If re-entering state not via DateChosenEvent
                val strings = StringProviderFactory.get(data.currentLanguageCode)
                val tables = deps.tablesRepo.listAvailableByClubAndDate(data.clubId!!, data.selectedDate!!, activeOnly = true)
                scope.launch { deps.bot.sendChooseTableKeyboard(event.chatId, tables, data.club!!, data.selectedDate!!, strings, data.currentMessageId, 3 to totalBookingSteps) }
            }
        }
        transition<TableChosenEvent>("TableToPeople") {
            targetState = FsmStates.EnterPeople
            action {
                data.tableInfo = deps.tablesRepo.find(event.tableId)
                val strings = StringProviderFactory.get(data.currentLanguageCode)
                if (data.tableInfo == null || !data.tableInfo!!.isActive) {
                    targetState = FsmStates.ChooseTable
                    scope.launch { deps.bot.sendErrorMessage(event.chatId, strings, strings.noAvailableTables, deps.clubsRepo.getAllClubs(activeOnly = true), data.currentMessageId) }
                } else {
                    logger.info("Table ${data.tableInfo?.number} chosen. Moving to EnterPeople.")
                    scope.launch { deps.bot.askPeopleCount(event.chatId, strings, data.currentMessageId, 4 to totalBookingSteps) }
                }
            }
        }
        transition<BackPressedEvent>("BackFromTableToDate") {
            guard = {event.targetStateName == FsmStates.ChooseDate.name}
            targetState = FsmStates.ChooseDate
            action {
                data.tableInfo = null
                val strings = StringProviderFactory.get(data.currentLanguageCode)
                scope.launch { deps.bot.sendCalendar(event.chatId, data.selectedDate?.let{YearMonth.from(it)} ?: YearMonth.now(deps.defaultZoneId), strings, data.currentMessageId, 2 to totalBookingSteps) }
            }
        }
    }

    addState(FsmStates.EnterPeople) {
        transition<PeopleEnteredEvent>("PeopleToSlot") {
            action {
                val strings = StringProviderFactory.get(data.currentLanguageCode)
                val maxGuests = data.tableInfo?.seats?.plus(2) ?: BotConstants.MAX_GUESTS_DEFAULT
                if (event.count < BotConstants.MIN_GUESTS_DEFAULT || event.count > maxGuests) {
                    targetState = FsmStates.EnterPeople
                    scope.launch { deps.bot.sendInfoMessage(event.chatId, strings.invalidPeopleCount(BotConstants.MIN_GUESTS_DEFAULT, maxGuests), strings, deps.clubsRepo.getAllClubs(activeOnly = true), data.currentMessageId) }
                } else {
                    data.guests = event.count
                    // TODO: Implement actual slot fetching logic in deps.slotsRepo.getAvailableSlots(clubId, tableId, date, guests)
                    // For now, using placeholder slots relative to selectedDate at specific times
                    val selectedDate = data.selectedDate!!
                    val zone = ZoneId.of(data.club!!.timezone)
                    val baseTimeStart = selectedDate.atTime(22,0).atZone(zone).toInstant() // Example: 22:00
                    val baseTimeEnd = selectedDate.atTime(23,59).atZone(zone).toInstant() // Example: 23:59

                    val slots = listOf(
                        baseTimeStart to baseTimeStart.plus(2, ChronoUnit.HOURS),
                        baseTimeStart.plus(3, ChronoUnit.HOURS) to baseTimeStart.plus(5, ChronoUnit.HOURS)
                    ).filter { it.second.isBefore(baseTimeEnd) } // Ensure slots are within the day or defined operational hours

                    logger.info("${event.count} guests. Moving to ChooseSlot.")
                    targetState = FsmStates.ChooseSlot
                    scope.launch { deps.bot.sendChooseSlotKeyboard(event.chatId, data.club!!, data.tableInfo!!, slots, strings, data.currentMessageId, 5 to totalBookingSteps) }
                }
            }
        }
        transition<BackPressedEvent>("BackFromPeopleToTable") {
            guard = {event.targetStateName == FsmStates.ChooseTable.name}
            targetState = FsmStates.ChooseTable
            action {
                data.guests = null
                val strings = StringProviderFactory.get(data.currentLanguageCode)
                val tables = deps.tablesRepo.listAvailableByClubAndDate(data.clubId!!, data.selectedDate!!, activeOnly = true)
                scope.launch { deps.bot.sendChooseTableKeyboard(event.chatId, tables, data.club!!, data.selectedDate!!, strings, data.currentMessageId, 3 to totalBookingSteps) }
            }
        }
    }

    addState(FsmStates.ChooseSlot) {
        transition<SlotChosenEvent>("SlotToGuestName") {
            targetState = FsmStates.EnterGuestName
            action {
                data.slotStart = event.start
                data.slotEnd = event.end
                val strings = StringProviderFactory.get(data.currentLanguageCode)
                logger.info("Slot chosen: ${event.start} - ${event.end}. Moving to EnterGuestName.")
                scope.launch { deps.bot.askGuestName(event.chatId, strings, data.currentMessageId, 6 to totalBookingSteps) }
            }
        }
        transition<BackPressedEvent>("BackFromSlotToPeople") {
            guard = {event.targetStateName == FsmStates.EnterPeople.name}
            targetState = FsmStates.EnterPeople
            action {
                data.slotStart = null; data.slotEnd = null
                val strings = StringProviderFactory.get(data.currentLanguageCode)
                scope.launch { deps.bot.askPeopleCount(event.chatId, strings, data.currentMessageId, 4 to totalBookingSteps) }
            }
        }
    }

    addState(FsmStates.EnterGuestName) {
        transition<GuestNameEnteredEvent>("GuestNameToPhone") {
            action {
                val strings = StringProviderFactory.get(data.currentLanguageCode)
                if (event.name.isBlank() || event.name.length < 2 || event.name.length > 50) {
                    targetState = FsmStates.EnterGuestName
                    scope.launch { deps.bot.sendInfoMessage(event.chatId, strings.invalidGuestName, strings, deps.clubsRepo.getAllClubs(activeOnly = true), data.currentMessageId) }
                } else {
                    data.guestName = event.name
                    targetState = FsmStates.EnterGuestPhone
                    logger.info("Guest name '${event.name}'. Moving to EnterGuestPhone.")
                    scope.launch { deps.bot.askGuestPhone(event.chatId, strings, data.currentMessageId, 7 to totalBookingSteps) }
                }
            }
        }
        transition<BackPressedEvent>("BackFromNameToSlot") {
            guard = {event.targetStateName == FsmStates.ChooseSlot.name}
            targetState = FsmStates.ChooseSlot
            action {
                data.guestName = null
                val strings = StringProviderFactory.get(data.currentLanguageCode)
                val slots = listOf( // Placeholder, regenerate or fetch
                    Instant.now().plus(2, ChronoUnit.HOURS) to Instant.now().plus(4, ChronoUnit.HOURS)
                )
                scope.launch { deps.bot.sendChooseSlotKeyboard(event.chatId, data.club!!, data.tableInfo!!, slots, strings, data.currentMessageId, 5 to totalBookingSteps) }
            }
        }
    }

    addState(FsmStates.EnterGuestPhone) {
        transition<GuestPhoneEnteredEvent>("PhoneToConfirm") {
            action {
                val strings = StringProviderFactory.get(data.currentLanguageCode)
                val phoneRegex = """^(\+?\d{1,4}[\s.-]?)?\(?\d{3}\)?[\s.-]?\d{3}[\s.-]?\d{2}[\s.-]?\d{2}$""".toRegex()
                if (!phoneRegex.matches(event.phone)) {
                    targetState = FsmStates.EnterGuestPhone
                    scope.launch { deps.bot.sendInfoMessage(event.chatId, strings.invalidPhoneFormat, strings, deps.clubsRepo.getAllClubs(activeOnly = true), data.currentMessageId) }
                } else {
                    data.guestPhone = event.phone.replace(Regex("[^0-9+]"), "")
                    targetState = FsmStates.ConfirmBooking
                    logger.info("Guest phone '${data.guestPhone}'. Moving to ConfirmBooking.")
                    scope.launch { deps.bot.sendConfirmMessage(event.chatId, data, data.club!!.title, data.tableInfo!!.number, data.selectedDate!!, strings, data.currentMessageId, 8 to totalBookingSteps) }
                }
            }
        }
        transition<BackPressedEvent>("BackFromPhoneToName") {
            guard = {event.targetStateName == FsmStates.EnterGuestName.name}
            targetState = FsmStates.EnterGuestName
            action {
                data.guestPhone = null
                val strings = StringProviderFactory.get(data.currentLanguageCode)
                scope.launch { deps.bot.askGuestName(event.chatId, strings, data.currentMessageId, 6 to totalBookingSteps) }
            }
        }
    }

    transition<ConfirmBookingPressedEvent>("ConfirmToFinished") {
        targetState = FsmStates.BookingFinished
        action {
            val strings = StringProviderFactory.get(data.currentLanguageCode)
            val bookingEntity = data.toBookingEntity()
            if (bookingEntity == null) {
                targetState = FsmStates.MainMenu
                scope.launch {
                    deps.bot.sendErrorMessage(
                        event.chatId,
                        strings,
                        strings.notAllDataCollectedError,
                        deps.clubsRepo.getAllClubs(activeOnly = true),
                        data.currentMessageId
                    )
                }
            } else {
                val bookingDate = bookingEntity.dateStart.atZone(java.time.ZoneId.of(data.club!!.timezone)).toLocalDate()
                val tableId = bookingEntity.tableId

                val isFree = deps.bookingsRepo.isTableAvailableOnDate(tableId, bookingDate)

                if (!isFree) {
                    targetState = FsmStates.ConfirmBooking
                    scope.launch {
                        deps.bot.sendErrorMessage(
                            event.chatId,
                            strings,
                            "Этот стол уже забронирован на выбранную дату. Пожалуйста, выберите другой стол или дату.",
                            deps.clubsRepo.getAllClubs(activeOnly = true),
                            data.currentMessageId
                        )
                    }
                } else {
                    val bookingId = deps.bookingsRepo.create(bookingEntity)
                    val pointsEarned = bookingEntity.loyaltyPointsEarned ?: 0
                    deps.usersRepo.addLoyaltyPoints(data.dbUser!!.id, pointsEarned)
                    logger.info("Booking #$bookingId confirmed. Points earned: $pointsEarned.")
                    scope.launch {
                        deps.bot.sendBookingSuccessMessage(
                            event.chatId,
                            bookingId,
                            pointsEarned,
                            strings,
                            deps.clubsRepo.getAllClubs(activeOnly = true),
                            data.currentMessageId
                        )
                    }
                }
            }
        }
    }

    addState(FsmStates.ShowVenueList) {
        transition<VenueChosenForInfoEvent>("VenueListToDetails") {
            targetState = FsmStates.ShowVenueDetails
            action {
                data.selectedVenueForInfo = deps.clubsRepo.findById(event.venueId)
                val strings = StringProviderFactory.get(data.currentLanguageCode)
                if (data.selectedVenueForInfo == null || !data.selectedVenueForInfo!!.isActive) {
                    targetState = FsmStates.ShowVenueList
                    scope.launch { deps.bot.sendInfoMessage(event.chatId, strings.venueNotFoundInfo, strings, deps.clubsRepo.getAllClubs(activeOnly = true), data.currentMessageId) }
                } else {
                    scope.launch { deps.bot.sendVenueDetails(event.chatId, data.selectedVenueForInfo!!, strings, data.currentMessageId) }
                }
            }
        }
        transition<BackPressedEvent>("BackFromVenueListToMenu") {
            guard = {event.targetStateName == FsmStates.MainMenu.name}
            targetState = FsmStates.MainMenu
        }
    }
    addState(FsmStates.ShowVenueDetails) {
        // Placeholder for Poster/Photo buttons
        transition<VenuePostersRequest>("DetailsToPostersWIP") {
            targetState = FsmStates.ShowVenueDetails // Stay, just send WIP message
            action {
                val strings = StringProviderFactory.get(data.currentLanguageCode)
                scope.launch { deps.bot.sendFeatureInDevelopmentMessage(event.chatId, strings, data.currentMessageId) }
            }
        }
        transition<VenuePhotosRequest>("DetailsToPhotosWIP") {
            targetState = FsmStates.ShowVenueDetails // Stay
            action {
                val strings = StringProviderFactory.get(data.currentLanguageCode)
                scope.launch { deps.bot.sendFeatureInDevelopmentMessage(event.chatId, strings, data.currentMessageId) }
            }
        }
        transition<BackPressedEvent>("BackFromDetailsToVenueList") {
            guard = {event.targetStateName == FsmStates.ShowVenueList.name}
            targetState = FsmStates.ShowVenueList
            action {
                data.selectedVenueForInfo = null
                val strings = StringProviderFactory.get(data.currentLanguageCode)
                val clubs = deps.clubsRepo.getAllClubs(activeOnly = true)
                scope.launch { deps.bot.sendVenueSelection(event.chatId, clubs, strings, data.currentMessageId) }
            }
        }
    }

    addState(FsmStates.ShowMyBookingsList) {
        transition<ManageBookingPressedEvent>("MyBookingsToManageOptions") {
            targetState = FsmStates.ManageBookingOptions
            action {
                val strings = StringProviderFactory.get(data.currentLanguageCode)
                val booking = deps.bookingsRepo.findByIdWithClubName(event.bookingId)
                if (booking == null || booking.userId != data.dbUser!!.id) {
                    targetState = FsmStates.ShowMyBookingsList
                    scope.launch { deps.bot.sendInfoMessage(event.chatId, strings.bookingNotFound, strings, deps.clubsRepo.getAllClubs(activeOnly = true), data.currentMessageId) }
                } else {
                    data.selectedBookingToManageId = event.bookingId
                    scope.launch { deps.bot.sendManageBookingOptions(event.chatId, booking, strings, data.currentMessageId!!) }
                }
            }
        }
        transition<BackPressedEvent>("BackFromMyBookingsToMenu") {
            guard = {event.targetStateName == FsmStates.MainMenu.name}
            targetState = FsmStates.MainMenu
        }
    }
    addState(FsmStates.ManageBookingOptions) {
        transition<ExecuteCancelBookingEvent>("OptionsDoCancel") {
            targetState = FsmStates.BookingActionFinished
            action {
                val strings = StringProviderFactory.get(data.currentLanguageCode)
                val booking = deps.bookingsRepo.findByIdWithClubName(event.bookingId)
                if (booking != null && booking.userId == data.dbUser!!.id) {
                    if (booking.status != BookingStatus.CANCELLED) {
                        deps.bookingsRepo.cancel(event.bookingId, data.dbUser!!.id)
                        scope.launch { deps.bot.sendBookingCancellationConfirmed(event.chatId, event.bookingId, booking.clubName, strings, deps.clubsRepo.getAllClubs(activeOnly = true), data.currentMessageId!!) }
                    } else {
                        scope.launch { deps.bot.sendInfoMessage(event.chatId, strings.bookingAlreadyCancelled, strings, deps.clubsRepo.getAllClubs(activeOnly = true), data.currentMessageId!!) }
                    }
                } else {
                    scope.launch { deps.bot.sendInfoMessage(event.chatId, strings.bookingNotFound, strings, deps.clubsRepo.getAllClubs(activeOnly = true), data.currentMessageId!!) }
                }
            }
        }
        transition<ExecuteChangeBookingInfoEvent>("OptionsDoChangeInfo") {
            action {
                val strings = StringProviderFactory.get(data.currentLanguageCode)
                scope.launch { deps.bot.sendChangeBookingInfo(event.chatId, strings, data.currentMessageId!!) }
                // Stays in this state or goes to a sub-flow for change, then back to ShowMyBookingsList or MainMenu
                // For now, just shows info and stays, user can press "Back"
            }
        }
        transition<RateBookingEvent>("OptionsToRateBooking") {
            guard = { event.rating == null } // This means "initiate feedback"
            targetState = FsmStates.AskFeedback
            action {
                data.bookingToRateId = event.bookingId
                // onEntry of AskFeedback will send the prompt
            }
        }
        transition<BackPressedEvent>("BackFromManageOptionsToMyBookingsList") {
            guard = {event.targetStateName == FsmStates.ShowMyBookingsList.name}
            targetState = FsmStates.ShowMyBookingsList
            action {
                data.selectedBookingToManageId = null
                val strings = StringProviderFactory.get(data.currentLanguageCode)
                val bookings = deps.bookingsRepo.byUserId(data.dbUser!!.id)
                scope.launch { deps.bot.sendMyBookingsList(event.chatId, bookings, strings, deps.clubsRepo.getAllClubs(activeOnly = true), data.currentMessageId) }
            }
        }
    }
    addState(FsmStates.AskFeedback) {
        onEntry {
            val strings = StringProviderFactory.get(data.currentLanguageCode)
            val bookingId = data.bookingToRateId
            if (bookingId != null) {
                val booking = deps.bookingsRepo.findByIdWithClubName(bookingId)
                if (booking != null && booking.userId == data.dbUser!!.id) {
                    scope.launch { deps.bot.askForFeedback(event.chatId, bookingId, booking.clubName, strings, data.currentMessageId!!) }
                } else {
                    scope.launch { deps.bot.sendInfoMessage(event.chatId, strings.bookingNotFound, strings, deps.clubsRepo.getAllClubs(activeOnly = true), data.currentMessageId!!) }
                    switchTo(FsmStates.ShowMyBookingsList)
                }
            } else {
                switchTo(FsmStates.ShowMyBookingsList)
            }
        }
        transition<RateBookingEvent>("FeedbackRatingSubmitted") {
            guard = {event.rating != null} // Rating is provided
            targetState = FsmStates.BookingActionFinished
            action {
                val strings = StringProviderFactory.get(data.currentLanguageCode)
                deps.bookingsRepo.addFeedback(event.bookingId, data.dbUser!!.id, event.rating!!, null) // TODO: Add comment input later
                deps.usersRepo.addLoyaltyPoints(data.dbUser!!.id, BotConstants.POINTS_FOR_FEEDBACK)
                logger.info("Feedback for booking ${event.bookingId}: ${event.rating} stars. Points for feedback: ${BotConstants.POINTS_FOR_FEEDBACK}")
                scope.launch { deps.bot.sendFeedbackThanks(event.chatId, event.rating!!, strings, deps.clubsRepo.getAllClubs(activeOnly = true), data.currentMessageId!!) }
            }
        }
        transition<BackPressedEvent>("BackFromFeedbackToManageOptions") {
            guard = {event.targetStateName == FsmStates.ManageBookingOptions.name}
            targetState = FsmStates.ManageBookingOptions
            action {
                val strings = StringProviderFactory.get(data.currentLanguageCode)
                val booking = data.selectedBookingToManageId?.let { deps.bookingsRepo.findByIdWithClubName(it) }
                if (booking != null) {
                    scope.launch { deps.bot.sendManageBookingOptions(event.chatId, booking, strings, data.currentMessageId!!) }
                } else { // Fallback
                    targetState = FsmStates.ShowMyBookingsList
                    val bookings = deps.bookingsRepo.byUserId(data.dbUser!!.id)
                    scope.launch { deps.bot.sendMyBookingsList(event.chatId, bookings, strings, deps.clubsRepo.getAllClubs(activeOnly = true), data.currentMessageId) }
                }
                data.bookingToRateId = null
            }
        }
    }

    addState(FsmStates.AskQuestionInput) {
        transition<QuestionTextEnteredEvent>("QuestionToSent") {
            targetState = FsmStates.QuestionSent
            action {
                data.userQuestion = event.question
                logger.info("User question: ${event.question}")
                // TODO: Implement actual question handling (e.g., save to DB, notify admin)
                val strings = StringProviderFactory.get(data.currentLanguageCode)
                scope.launch { deps.bot.sendQuestionReceived(event.chatId, strings, deps.clubsRepo.getAllClubs(activeOnly = true), data.currentMessageId) }
            }
        }
        transition<BackPressedEvent>("BackFromQuestionToMenu") {
            guard = {event.targetStateName == FsmStates.MainMenu.name}
            targetState = FsmStates.MainMenu
        }
    }

    addFinalState(FsmStates.BookingFinished) {
        onEntry {
            logger.info("FINAL_STATE: BookingFinishedState Reached.")
            data.clearBookingData()
            data.clearOtherFlowData()
        }
    }
    addFinalState(FsmStates.ActionCancelled) {
        onEntry {
            logger.info("FINAL_STATE: ActionCancelledState Reached.")
            data.clearBookingData()
            data.clearOtherFlowData()
        }
    }
    addFinalState(FsmStates.BookingActionFinished) {
        onEntry {
            logger.info("FINAL_STATE: BookingActionFinished Reached.")
            data.clearBookingData()
            data.clearOtherFlowData()
        }
    }
    addFinalState(FsmStates.QuestionSent) {
        onEntry {
            logger.info("FINAL_STATE: QuestionSentState Reached.")
            data.clearOtherFlowData()
        }
    }
    addFinalState(FsmStates.ErrorOccurred) {
        onEntry {
            logger.error("FINAL_STATE: ErrorOccurredState Reached.")
            data.clearBookingData()
            data.clearOtherFlowData()
        }
    }

    setInitialState(FsmStates.Initial)
}