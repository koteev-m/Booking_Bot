package bot

import db.Club
import bot.LocalizedStrings
import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.InlineKeyboardMarkup
import com.github.kotlintelegrambot.entities.ParseMode
import com.github.kotlintelegrambot.entities.ReplyKeyboardMarkup
import com.github.kotlintelegrambot.entities.keyboard.InlineKeyboardButton
import com.github.kotlintelegrambot.entities.keyboard.KeyboardButton
import com.github.kotlintelegrambot.network.fold // For handling API call results
import fsm.BotFacade
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import org.slf4j.LoggerFactory

class TelegramBotFacadeImpl(
    private val bot: Bot,
    private val defaultZoneId: ZoneId = ZoneId.of("Europe/Moscow")
) : BotFacade {

    private val logger = LoggerFactory.getLogger(TelegramBotFacadeImpl::class.java)

    private fun getTimeFormatter(strings: LocalizedStrings): DateTimeFormatter {
        return DateTimeFormatter.ofPattern("HH:mm").withZone(defaultZoneId)
    }
    private fun getDateFormatter(strings: LocalizedStrings): DateTimeFormatter {
        // Using a more universal date format that also implies year for clarity
        return DateTimeFormatter.ofPattern("dd MMMM yyyy", Locale(strings.languageCode)).withZone(defaultZoneId)
    }


    private suspend fun send(
        chatId: ChatId,
        text: String,
        strings: LocalizedStrings,
        replyMarkup: Any? = null,
        parseMode: ParseMode = ParseMode.HTML,
        messageIdToEdit: Long? = null
    ) {
        try {
            if (messageIdToEdit != null) {
                val inlineMarkup = if (replyMarkup is InlineKeyboardMarkup) replyMarkup else null
                bot.editMessageText(
                    chatId = chatId,
                    messageId = messageIdToEdit,
                    text = text,
                    replyMarkup = inlineMarkup,
                    parseMode = parseMode
                ).fold(
                    { response -> if (!response.ok) logger.warn("Failed to edit message $messageIdToEdit for chat $chatId: ${response.description}") },
                    { error -> logger.error("Exception editing message $messageIdToEdit for chat $chatId: ${error.message}", error.exception) }
                )
            } else {
                when (replyMarkup) {
                    is ReplyKeyboardMarkup -> bot.sendMessage(chatId, text, replyMarkup = replyMarkup, parseMode = parseMode)
                    is InlineKeyboardMarkup -> bot.sendMessage(chatId, text, replyMarkup = replyMarkup, parseMode = parseMode)
                    null -> bot.sendMessage(chatId, text, parseMode = parseMode)
                    else -> {
                        logger.error("Unknown replyMarkup type: ${replyMarkup::class.simpleName} for chat $chatId")
                        bot.sendMessage(chatId, text, parseMode = parseMode)
                    }
                }
            }
        } catch (e: Exception) {
            logger.error("Error in TelegramBotFacade.send for chat $chatId: ${e.message}", e)
        }
    }

    private fun createMainMenuKeyboard(strings: LocalizedStrings, clubs: List<Club>): ReplyKeyboardMarkup {
        val clubButtons = clubs.filter { it.isActive }.take(2).map { KeyboardButton(strings.menuBookTableInClub(it.title)) }

        return ReplyKeyboardMarkup(
            keyboard = listOfNotNull(
                listOf(KeyboardButton(strings.menuVenueInfo), KeyboardButton(strings.menuMyBookings)),
                clubButtons.ifEmpty { null },
                listOf(KeyboardButton(strings.menuAskQuestion), KeyboardButton(strings.menuHelp)),
                listOf(KeyboardButton(strings.menuChangeLanguage), KeyboardButton(strings.menuOpenApp))
            ).filterNotNull(),
            resizeKeyboard = true,
            oneTimeKeyboard = false // Keep menu visible
        )
    }
    private fun createMainMenuInlineKeyboard(strings: LocalizedStrings, clubs: List<Club>): InlineKeyboardMarkup {
        val clubButtons = clubs.filter { it.isActive }.take(2).map {
            InlineKeyboardButton.CallbackData(strings.menuBookTableInClub(it.title), "${BotConstants.CB_PREFIX_BOOK_CLUB}${it.id}")
        }
        return InlineKeyboardMarkup.create(
            listOfNotNull(
                listOf(
                    InlineKeyboardButton.CallbackData(strings.menuVenueInfo, BotConstants.CB_MAIN_MENU_VENUE_INFO),
                    InlineKeyboardButton.CallbackData(strings.menuMyBookings, BotConstants.CB_MAIN_MENU_MY_BOOKINGS)
                ),
                clubButtons.chunked(2).firstOrNull()?.ifEmpty { null },
                listOf(
                    InlineKeyboardButton.CallbackData(strings.menuAskQuestion, BotConstants.CB_MAIN_MENU_ASK_QUESTION),
                    InlineKeyboardButton.CallbackData(strings.menuHelp, BotConstants.CB_MAIN_MENU_HELP)
                ),
                listOf(
                    InlineKeyboardButton.CallbackData(strings.menuChangeLanguage, BotConstants.CB_MAIN_MENU_CHANGE_LANG),
                    InlineKeyboardButton.CallbackData(strings.menuOpenApp, BotConstants.CB_MAIN_MENU_OPEN_APP) // Placeholder
                )
            ).filterNotNull()
        )
    }

    override suspend fun sendWelcomeMessage(chatId: ChatId, userName: String?, strings: LocalizedStrings, clubs: List<Club>) {
        val greeting = strings.welcomeMessage(userName)
        send(chatId, greeting, strings, createMainMenuKeyboard(strings, clubs))
    }

    override suspend fun sendLanguageSelection(chatId: ChatId, currentStrings: LocalizedStrings, messageId: Long?) {
        val text = currentStrings.chooseLanguagePrompt
        val buttons = StringProviderFactory.allLanguages().map { langStrings ->
            InlineKeyboardButton.CallbackData(langStrings.languageName, "${BotConstants.CB_PREFIX_SELECT_LANG}${langStrings.languageCode}")
        }
        // Add a back button if messageId is present (meaning we are in a sub-flow)
        val backRow = if (messageId != null) listOf(backButtonToMainMenu(currentStrings)) else null
        val keyboardRows = buttons.chunked(1).toMutableList()
        backRow?.let { keyboardRows.add(it as List<InlineKeyboardButton.CallbackData>) }

        val keyboard = InlineKeyboardMarkup.create(keyboardRows)
        send(chatId, text, currentStrings, keyboard, messageIdToEdit = messageId)
    }

    override suspend fun sendMainMenu(chatId: ChatId, strings: LocalizedStrings, clubs: List<Club>, messageId: Long?, text: String?) {
        val messageText = text ?: strings.chooseAction
        if (messageId != null) {
            send(chatId, messageText, strings, createMainMenuInlineKeyboard(strings, clubs), messageIdToEdit = messageId)
        } else {
            send(chatId, messageText, strings, createMainMenuKeyboard(strings, clubs))
        }
    }

    override suspend fun sendStepTrackerMessage(chatId: ChatId, strings: LocalizedStrings, currentStep: Int, totalSteps: Int, messageText: String, keyboard: InlineKeyboardMarkup?, messageId: Long?) {
        val fullText = "${strings.stepTracker(currentStep, totalSteps)}\n\n$messageText"
        send(chatId, fullText, strings, keyboard, messageIdToEdit = messageId)
    }

    override suspend fun sendChooseClubKeyboard(chatId: ChatId, clubs: List<Club>, strings: LocalizedStrings, messageId: Long?, step: Pair<Int, Int>?) {
        val activeClubs = clubs.filter { it.isActive }
        val buttons = activeClubs.map { club ->
            InlineKeyboardButton.CallbackData(club.title, "${BotConstants.CB_PREFIX_BOOK_CLUB}${club.id}")
        }
        val keyboard = InlineKeyboardMarkup.create(
            buttons.chunked(2), // Max 2 club buttons per row
            listOf(backButtonToMainMenu(strings))
        )
        val text = strings.chooseClubPrompt
        val fullText = step?.let{"${strings.stepTracker(it.first, it.second)}\n\n$text"} ?: text
        send(chatId, fullText, strings, keyboard, messageIdToEdit = messageId)
    }

    override suspend fun sendCalendar(chatId: ChatId, yearMonth: YearMonth, strings: LocalizedStrings, messageId: Long?, step: Pair<Int, Int>?) {
        val today = LocalDate.now(defaultZoneId)
        val firstDayOfMonth = yearMonth.atDay(1)
        // val lastDayOfMonth = yearMonth.atEndOfMonth() // Not used directly

        val keyboardRows = mutableListOf<List<InlineKeyboardButton>>()
        keyboardRows.add(listOf(InlineKeyboardButton.CallbackData(strings.calendarMonthYear(firstDayOfMonth.month, yearMonth.year), "cal_ignore_month_year")))
        keyboardRows.add(DayOfWeek.entries.map { InlineKeyboardButton.CallbackData(it.getDisplayName(TextStyle.SHORT, Locale(strings.languageCode)), "cal_ignore_weekday") })

        var currentDayPointer = firstDayOfMonth
        val firstDayOfWeekValue = firstDayOfMonth.dayOfWeek.value // Monday=1, Sunday=7 (ISO)
        val leadingEmptyCells = (firstDayOfWeekValue - DayOfWeek.MONDAY.value + 7) % 7 // Ensure positive result

        var weekRow = mutableListOf<InlineKeyboardButton>()
        repeat(leadingEmptyCells) { weekRow.add(InlineKeyboardButton.CallbackData(" ", "cal_ignore_empty")) }

        while (currentDayPointer.year == yearMonth.year && currentDayPointer.month == yearMonth.month) {
            val buttonText = strings.calendarDay(currentDayPointer.dayOfMonth)
            val callbackData = if (currentDayPointer.isBefore(today) || currentDayPointer.isAfter(today.plusMonths(3))) {
                "cal_ignore_disabled"
            } else {
                "${BotConstants.CB_PREFIX_CHOOSE_DATE_CAL}${currentDayPointer}"
            }
            weekRow.add(InlineKeyboardButton.CallbackData(buttonText, callbackData))

            if (weekRow.size == 7) {
                keyboardRows.add(weekRow.toList())
                weekRow.clear()
            }
            currentDayPointer = currentDayPointer.plusDays(1)
        }
        if (weekRow.isNotEmpty()) { // Fill remaining cells of the last week
            while (weekRow.size < 7) weekRow.add(InlineKeyboardButton.CallbackData(" ", "cal_ignore_empty"))
            keyboardRows.add(weekRow.toList())
        }

        val navRow = mutableListOf<InlineKeyboardButton>()
        if (yearMonth.isAfter(YearMonth.from(today))) {
            navRow.add(InlineKeyboardButton.CallbackData(strings.calendarPrevMonth, "${BotConstants.CB_PREFIX_CAL_MONTH_CHANGE}prev_${yearMonth.minusMonths(1)}"))
        } else {
            navRow.add(InlineKeyboardButton.CallbackData(" ", "cal_ignore_empty")) // Placeholder for alignment
        }
        if (yearMonth.isBefore(YearMonth.from(today.plusMonths(2)))) {
            navRow.add(InlineKeyboardButton.CallbackData(strings.calendarNextMonth, "${BotConstants.CB_PREFIX_CAL_MONTH_CHANGE}next_${yearMonth.plusMonths(1)}"))
        } else {
            navRow.add(InlineKeyboardButton.CallbackData(" ", "cal_ignore_empty")) // Placeholder
        }
        keyboardRows.add(navRow)
        keyboardRows.add(listOf(backButton(strings, BotConstants.CB_PREFIX_BACK_TO + FsmStates.ChooseClub.name)))

        val keyboard = InlineKeyboardMarkup.create(keyboardRows)
        val text = strings.chooseDatePrompt
        val fullText = step?.let{"${strings.stepTracker(it.first, it.second)}\n\n$text"} ?: text
        send(chatId, fullText, strings, keyboard, messageIdToEdit = messageId)
    }

    override suspend fun sendChooseTableKeyboard(chatId: ChatId, tables: List<TableInfo>, club: Club, selectedDate: LocalDate, strings: LocalizedStrings, messageId: Long?, step: Pair<Int, Int>?) {
        val floorPlanUrl = club.floorPlanImageUrl ?: BotConstants.TABLE_LAYOUT_PLACEHOLDER_URL
        // Sending photo first, then a new message with table buttons
        bot.sendPhoto(chatId, photoUrl = floorPlanUrl, caption = strings.tableLayoutInfo).fold(
            { /* Photo sent successfully */ },
            { error -> logger.error("Failed to send floor plan photo for club ${club.id}: ${error.exception?.message ?: error.errorBody}") }
        )

        val text: String
        val keyboard: InlineKeyboardMarkup
        if (tables.isEmpty()) {
            text = strings.noAvailableTables
            keyboard = InlineKeyboardMarkup.create(listOf(backButton(strings, BotConstants.CB_PREFIX_BACK_TO + FsmStates.ChooseDate.name)))
        } else {
            val buttons = tables.map { table ->
                InlineKeyboardButton.CallbackData(
                    strings.tableButtonText(table.number, table.seats),
                    "${BotConstants.CB_PREFIX_CHOOSE_TABLE}${table.id}"
                )
            }
            keyboard = InlineKeyboardMarkup.create(
                buttons.chunked(1), // Each table on a new row for clarity
                listOf(backButton(strings, BotConstants.CB_PREFIX_BACK_TO + FsmStates.ChooseDate.name))
            )
            text = "${strings.chooseTablePrompt} (Клуб: ${club.title}, Дата: ${selectedDate.format(getDateFormatter(strings))})"
        }
        val fullText = step?.let{"${strings.stepTracker(it.first, it.second)}\n\n$text"} ?: text
        // Send as a new message because we can't edit after sending a photo in the same interaction easily
        send(chatId, fullText, strings, keyboard, messageIdToEdit = null)
    }

    override suspend fun askPeopleCount(chatId: ChatId, strings: LocalizedStrings, messageId: Long?, step: Pair<Int, Int>?) {
        val text = strings.askPeopleCount
        val fullText = step?.let{"${strings.stepTracker(it.first, it.second)}\n\n$text"} ?: text
        val keyboard = InlineKeyboardMarkup.create(listOf(backButton(strings, BotConstants.CB_PREFIX_BACK_TO + FsmStates.ChooseTable.name)))
        send(chatId, fullText, strings, keyboard, messageIdToEdit = messageId)
    }

    override suspend fun sendChooseSlotKeyboard(
        chatId: ChatId, club: Club, table: TableInfo, slots: List<Pair<Instant, Instant>>,
        strings: LocalizedStrings, messageId: Long?, step: Pair<Int, Int>?
    ) {
        val text: String
        val keyboard: InlineKeyboardMarkup?
        if (slots.isEmpty()) {
            text = strings.noAvailableSlots
            keyboard = InlineKeyboardMarkup.create(listOf(backButton(strings, BotConstants.CB_PREFIX_BACK_TO + FsmStates.EnterPeople.name)))
        } else {
            val timeFmt = getTimeFormatter(strings)
            val buttons = slots.map { (start, end) ->
                val slotText = "${timeFmt.format(start)} - ${timeFmt.format(end)}"
                InlineKeyboardButton.CallbackData(slotText, "${BotConstants.CB_PREFIX_CHOOSE_SLOT}${start.epochSecond}:${end.epochSecond}")
            }
            keyboard = InlineKeyboardMarkup.create(
                buttons.chunked(2),
                listOf(backButton(strings, BotConstants.CB_PREFIX_BACK_TO + FsmStates.EnterPeople.name))
            )
            text = strings.chooseSlotForTableInClub(table.number, club.title)
        }
        val fullText = step?.let{"${strings.stepTracker(it.first, it.second)}\n\n$text"} ?: text
        send(chatId, fullText, strings, keyboard, messageIdToEdit = messageId)
    }

    override suspend fun askGuestName(chatId: ChatId, strings: LocalizedStrings, messageId: Long?, step: Pair<Int, Int>?) {
        val text = "${strings.askGuestName} ${strings.askGuestNameExample}"
        val fullText = step?.let{"${strings.stepTracker(it.first, it.second)}\n\n$text"} ?: text
        val keyboard = InlineKeyboardMarkup.create(listOf(backButton(strings, BotConstants.CB_PREFIX_BACK_TO + FsmStates.ChooseSlot.name)))
        send(chatId, fullText, strings, keyboard, messageIdToEdit = messageId)
    }

    override suspend fun askGuestPhone(chatId: ChatId, strings: LocalizedStrings, messageId: Long?, step: Pair<Int, Int>?) {
        val text = "${strings.askGuestPhone} ${strings.askGuestPhoneExample}"
        val fullText = step?.let{"${strings.stepTracker(it.first, it.second)}\n\n$text"} ?: text
        val keyboard = InlineKeyboardMarkup.create(listOf(backButton(strings, BotConstants.CB_PREFIX_BACK_TO + FsmStates.EnterGuestName.name)))
        send(chatId, fullText, strings, keyboard, messageIdToEdit = messageId)
    }

    override suspend fun sendConfirmMessage(chatId: ChatId, draft: DraftBooking, clubName: String, tableNumber: Int, selectedDate: LocalDate, strings: LocalizedStrings, messageId: Long?, step: Pair<Int, Int>?) {
        val timeFmt = getTimeFormatter(strings)
        val dateFmt = getDateFormatter(strings)
        val slotStartStr = draft.slotStart?.let { timeFmt.format(it) } ?: "N/A"
        val slotEndStr = draft.slotEnd?.let { timeFmt.format(it) } ?: "N/A"
        val dateStr = dateFmt.format(selectedDate)

        val bookingInfo = strings.bookingDetailsFormat(
            clubName, tableNumber, draft.guests ?: -1, dateStr,
            "$slotStartStr - $slotEndStr", draft.guestName ?: "N/A", draft.guestPhone ?: "N/A"
        )
        val text = "${strings.confirmBookingPrompt}\n\n$bookingInfo"
        val fullText = step?.let{"${strings.stepTracker(it.first, it.second)}\n\n$text"} ?: text
        val keyboard = InlineKeyboardMarkup.create(
            listOf(
                InlineKeyboardButton.CallbackData(strings.buttonConfirm, BotConstants.CB_PREFIX_CONFIRM_BOOKING),
                InlineKeyboardButton.CallbackData(strings.buttonCancel, BotConstants.CB_PREFIX_CANCEL_ACTION)
            ),
            listOf(backButton(strings, BotConstants.CB_PREFIX_BACK_TO + FsmStates.EnterGuestPhone.name))
        )
        send(chatId, fullText, strings, keyboard, messageIdToEdit = messageId)
    }

    override suspend fun sendBookingSuccessMessage(chatId: ChatId, bookingId: Int, loyaltyPoints: Int, strings: LocalizedStrings, clubs: List<Club>, messageId: Long?) {
        val text = strings.bookingSuccess(bookingId, loyaltyPoints)
        send(chatId, text, strings, createMainMenuInlineKeyboard(strings, clubs), messageIdToEdit = messageId)
    }

    override suspend fun sendBookingCancelledMessage(chatId: ChatId, strings: LocalizedStrings, clubs: List<Club>, messageId: Long?) {
        send(chatId, strings.bookingCancelledMessage, strings, createMainMenuInlineKeyboard(strings, clubs), messageIdToEdit = messageId)
    }

    override suspend fun sendActionCancelledMessage(chatId: ChatId, strings: LocalizedStrings, clubs: List<Club>, messageId: Long?) {
        send(chatId, strings.actionCancelled, strings, createMainMenuInlineKeyboard(strings, clubs), messageIdToEdit = messageId)
    }

    override suspend fun sendVenueSelection(chatId: ChatId, venues: List<Club>, strings: LocalizedStrings, messageId: Long?) {
        val activeVenues = venues.filter { it.isActive }
        val buttons = activeVenues.map { venue ->
            InlineKeyboardButton.CallbackData(venue.title, "${BotConstants.CB_PREFIX_VENUE_INFO_SHOW}${venue.id}")
        }
        val keyboard = InlineKeyboardMarkup.create(
            buttons.chunked(2),
            listOf(backButtonToMainMenu(strings))
        )
        send(chatId, strings.chooseVenueForInfo, strings, keyboard, messageIdToEdit = messageId)
    }

    override suspend fun sendVenueDetails(chatId: ChatId, venue: Club, strings: LocalizedStrings, messageId: Long?) {
        val text = strings.venueDetails(venue.title, venue.description, venue.address, venue.phone, venue.workingHours)
        val keyboard = InlineKeyboardMarkup.create(
            listOf( // Placeholder buttons for posters/photos
                InlineKeyboardButton.CallbackData(strings.venueInfoButtonPosters, "${BotConstants.CB_PREFIX_VENUE_POSTERS}${venue.id}"),
                InlineKeyboardButton.CallbackData(strings.venueInfoButtonPhotos, "${BotConstants.CB_PREFIX_VENUE_PHOTOS}${venue.id}")
            ),
            listOf(backButton(strings, BotConstants.CB_PREFIX_BACK_TO + FsmStates.ShowVenueList.name))
        )
        send(chatId, text, strings, keyboard, messageIdToEdit = messageId)
    }

    override suspend fun sendMyBookingsList(chatId: ChatId, bookings: List<BookingWithClubName>, strings: LocalizedStrings, clubs: List<Club>, messageId: Long?) {
        val text: String
        val keyboard: InlineKeyboardMarkup
        if (bookings.isEmpty()) {
            text = strings.noActiveBookings
            keyboard = InlineKeyboardMarkup.create(listOf(backButtonToMainMenu(strings)))
        } else {
            text = strings.myBookingsHeader
            val dateFmt = getDateFormatter(strings)
            val timeFmt = getTimeFormatter(strings)
            val buttons = bookings.map { booking ->
                val itemText = strings.bookingItemFormat(
                    booking.id, booking.clubName, booking.tableNumber,
                    dateFmt.format(booking.dateStart), timeFmt.format(booking.dateStart),
                    booking.guestsCount, booking.status.name // Consider localizing status
                )
                InlineKeyboardButton.CallbackData(itemText, "${BotConstants.CB_PREFIX_MANAGE_BOOKING}${booking.id}")
            }
            keyboard = InlineKeyboardMarkup.create(
                buttons.chunked(1),
                listOf(backButtonToMainMenu(strings))
            )
        }
        send(chatId, text, strings, keyboard, messageIdToEdit = messageId)
    }

    override suspend fun sendManageBookingOptions(chatId: ChatId, booking: BookingWithClubName, strings: LocalizedStrings, messageId: Long) {
        val dateFmt = getDateFormatter(strings)
        val timeFmt = getTimeFormatter(strings)
        val bookingDetails = strings.bookingDetailsFormat(
            booking.clubName, booking.tableNumber, booking.guestsCount,
            dateFmt.format(booking.dateStart),
            "${timeFmt.format(booking.dateStart)} - ${timeFmt.format(booking.dateEnd)}",
            booking.guestName ?: "N/A", booking.guestPhone ?: "N/A"
        )
        val text = strings.manageBookingPrompt(booking.id, bookingDetails)
        val buttons = mutableListOf<List<InlineKeyboardButton>>()
        buttons.add(listOf(
            InlineKeyboardButton.CallbackData(strings.buttonChangeBooking, "${BotConstants.CB_PREFIX_DO_CHANGE_BOOKING}${booking.id}"),
            InlineKeyboardButton.CallbackData(strings.buttonCancelBooking, "${BotConstants.CB_PREFIX_DO_CANCEL_BOOKING}${booking.id}")
        ))
        if (booking.dateEnd.isBefore(Instant.now()) && booking.status == db.BookingStatus.COMPLETED && booking.feedbackRating == null) {
            buttons.add(listOf(InlineKeyboardButton.CallbackData(strings.buttonRateBooking, "${BotConstants.CB_PREFIX_RATE_BOOKING}${booking.id}")))
        }
        buttons.add(listOf(backButton(strings, BotConstants.CB_PREFIX_BACK_TO + FsmStates.ShowMyBookingsList.name)))
        val keyboard = InlineKeyboardMarkup.create(buttons)
        send(chatId, text, strings, keyboard, messageIdToEdit = messageId)
    }

    override suspend fun sendBookingCancellationConfirmed(chatId: ChatId, bookingId: Int, clubName: String, strings: LocalizedStrings, clubs: List<Club>, messageId: Long) {
        val text = strings.bookingCancellationConfirmed(bookingId, clubName)
        send(chatId, text, strings, createMainMenuInlineKeyboard(strings, clubs), messageIdToEdit = messageId)
    }

    override suspend fun sendChangeBookingInfo(chatId: ChatId, strings: LocalizedStrings, messageId: Long) {
        send(chatId, strings.changeBookingInfo, strings, InlineKeyboardMarkup.create(listOf(backButton(strings, BotConstants.CB_PREFIX_BACK_TO + FsmStates.ShowMyBookingsList.name))), messageIdToEdit = messageId)
    }

    override suspend fun sendAskQuestionPrompt(chatId: ChatId, strings: LocalizedStrings, messageId: Long?) {
        send(chatId, strings.askQuestionPrompt, strings, InlineKeyboardMarkup.create(listOf(backButtonToMainMenu(strings))), messageIdToEdit = messageId)
    }

    override suspend fun sendQuestionReceived(chatId: ChatId, strings: LocalizedStrings, clubs: List<Club>, messageId: Long?) {
        send(chatId, strings.questionReceived, strings, createMainMenuInlineKeyboard(strings, clubs), messageIdToEdit = messageId)
    }

    override suspend fun sendHelpMessage(chatId: ChatId, strings: LocalizedStrings, clubs: List<Club>, messageId: Long?) {
        send(chatId, strings.helpText, strings, createMainMenuInlineKeyboard(strings, clubs), messageIdToEdit = messageId)
    }

    override suspend fun askForFeedback(chatId: ChatId, bookingId: Int, clubName: String, strings: LocalizedStrings, messageId: Long) {
        val text = strings.askForFeedbackPrompt(clubName, bookingId)
        val ratingButtons = (1..5).map { rating ->
            InlineKeyboardButton.CallbackData("⭐".repeat(rating), "${BotConstants.CB_PREFIX_RATE_BOOKING}${bookingId}:$rating")
        }
        val keyboard = InlineKeyboardMarkup.create(
            listOf(ratingButtons) as List<InlineKeyboardButton>, // Ratings on one row
            listOf(backButton(strings, BotConstants.CB_PREFIX_BACK_TO + FsmStates.ManageBookingOptions.name)) // Back to manage options
        )
        send(chatId, text, strings, keyboard, messageIdToEdit = messageId)
    }
    override suspend fun sendFeedbackThanks(chatId: ChatId, rating: Int, strings: LocalizedStrings, clubs: List<Club>, messageId: Long) {
        send(chatId, strings.feedbackThanks(rating), strings, createMainMenuInlineKeyboard(strings, clubs), messageIdToEdit = messageId)
    }

    override suspend fun sendErrorMessage(chatId: ChatId, strings: LocalizedStrings, message: String?, clubs: List<Club>, messageId: Long?) {
        val textToSend = message ?: strings.errorMessageDefault
        send(chatId, textToSend, strings, createMainMenuInlineKeyboard(strings, clubs), messageIdToEdit = messageId)
    }

    override suspend fun sendInfoMessage(chatId: ChatId, message: String, strings: LocalizedStrings, clubs: List<Club>, messageId: Long?) {
        send(chatId, message, strings, createMainMenuInlineKeyboard(strings, clubs), messageIdToEdit = messageId)
    }

    override suspend fun sendUnknownCommandMessage(chatId: ChatId, strings: LocalizedStrings, clubs: List<Club>, messageId: Long?) {
        send(chatId, strings.unknownCommand, strings, createMainMenuKeyboard(strings, clubs))
    }

    override suspend fun sendFeatureInDevelopmentMessage(chatId: ChatId, strings: LocalizedStrings, messageId: Long?) {
        send(chatId, strings.featureInDevelopment, strings, InlineKeyboardMarkup.create(listOf(backButtonToMainMenu(strings))), messageIdToEdit = messageId)
    }

    private fun backButton(strings: LocalizedStrings, callbackData: String): InlineKeyboardButton {
        return InlineKeyboardButton.CallbackData(strings.buttonBack, callbackData)
    }
    private fun backButtonToMainMenu(strings: LocalizedStrings): InlineKeyboardButton {
        return InlineKeyboardButton.CallbackData(strings.buttonBack, BotConstants.CB_PREFIX_BACK_TO + FsmStates.MainMenu.name)
    }
}