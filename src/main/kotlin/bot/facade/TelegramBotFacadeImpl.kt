package bot.facade

import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.InlineKeyboardMarkup
import com.github.kotlintelegrambot.entities.ParseMode
import com.github.kotlintelegrambot.network.Response // For Result.fold
import com.github.kotlintelegrambot.network.fold // For Result.fold
import db.BookingWithClubName
import db.Club
import db.TableInfo
import fsm.DraftBooking
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.time.YearMonth
import com.github.kotlintelegrambot.entities.TelegramError // For Result.fold

class TelegramBotFacadeImpl(
    private val bot: Bot,
    private val defaultStrings: LocalizedStrings
) : fsm.BotFacade {

    private val logger = LoggerFactory.getLogger(javaClass)

    // Corrected signature for using Result.fold
    private suspend fun sendMessage(
        chatId: ChatId,
        text: String,
        replyMarkup: InlineKeyboardMarkup? = null,
        parseMode: ParseMode = ParseMode.HTML
    ) {
        val result: Response<com.github.kotlintelegrambot.entities.Message> = bot.sendMessage( // Specify Result type if needed by fold
            chatId = chatId,
            text = text,
            replyMarkup = replyMarkup,
            parseMode = parseMode
        )
        result.fold(
            { response -> logger.info("Message sent to $chatId, ID: ${response.result?.messageId}") }, // onSuccess
            { error -> logger.error("Error sending message to $chatId: $error") } // onError
        )
    }

    private suspend fun editMessageText(
        chatId: ChatId,
        messageId: Long,
        text: String,
        replyMarkup: InlineKeyboardMarkup? = null,
        parseMode: ParseMode = ParseMode.HTML
    ) {
        val result: Response<com.github.kotlintelegrambot.entities.Message> = bot.editMessageText( // Specify Result type
            chatId = chatId,
            messageId = messageId,
            text = text,
            replyMarkup = replyMarkup,
            parseMode = parseMode
        )
        result.fold(
            { response -> logger.info("Message $messageId edited for $chatId") }, // onSuccess
            { error -> logger.error("Error editing message $messageId for $chatId: $error") } // onError
        )
    }

    // ... (rest of the implementations from the previous response, they use the sendMessage/editMessageText helpers above) ...
    // Ensure all method signatures in this Impl match the fsm.BotFacade interface
    override suspend fun sendLanguageSelection(chatId: ChatId, currentStrings: LocalizedStrings, messageId: Long?) {
        sendMessage(chatId, currentStrings.chooseLanguagePrompt, null)
    }

    override suspend fun sendWelcomeMessage(chatId: ChatId, userName: String?, strings: LocalizedStrings, clubs: List<Club>) {
        sendMessage(chatId, strings.welcomeMessage(userName))
    }

    override suspend fun sendMainMenu(chatId: ChatId, strings: LocalizedStrings, clubs: List<Club>, messageId: Long?, text: String?) {
        val message = text ?: strings.chooseAction
        if (messageId != null) {
            editMessageText(chatId, messageId, message, null)
        } else {
            sendMessage(chatId, message, null)
        }
    }

    override suspend fun sendStepTrackerMessage(chatId: ChatId, strings: LocalizedStrings, currentStep: Int, totalSteps: Int, messageText: String, keyboard: InlineKeyboardMarkup?, messageId: Long?) {
        val fullText = "${strings.stepTracker(currentStep, totalSteps)}\n\n$messageText"
        if (messageId != null) {
            editMessageText(chatId, messageId, fullText, keyboard)
        } else {
            sendMessage(chatId, fullText, keyboard)
        }
    }

    override suspend fun sendChooseClubKeyboard(chatId: ChatId, clubs: List<Club>, strings: LocalizedStrings, messageId: Long?, step: Pair<Int,Int>?) {
        val keyboardButtons = clubs.map { club ->
            com.github.kotlintelegrambot.entities.keyboard.InlineKeyboardButton.CallbackData(
                text = club.name,
                callbackData = "${BotConstants.CB_PREFIX_BOOK_CLUB}${club.id}"
            )
        }
        val keyboard = InlineKeyboardMarkup.create(keyboardButtons.map { listOf(it) })
        val text = strings.chooseClubPrompt

        if (messageId != null) editMessageText(chatId, messageId, text, keyboard)
        else sendMessage(chatId, text, keyboard)
    }

    override suspend fun sendCalendar(chatId: ChatId, clubId: Int, clubTitle: String, availableDates: List<LocalDate>, strings: LocalizedStrings, messageId: Long?, step: Pair<Int,Int>?) {
        val text = strings.chooseDatePrompt + " для клуба $clubTitle (${availableDates.size} дат доступно)"
        // val calendarKeyboard = buildCalendarKeyboard(YearMonth.now(), availableDates, strings) // Placeholder
        if (messageId != null) editMessageText(chatId, messageId, text, null )
        else sendMessage(chatId, text, null )
    }

    override suspend fun sendChooseTableKeyboard(chatId: ChatId, clubId: Int, clubTitle: String, date: LocalDate, tables: List<TableInfo>, strings: LocalizedStrings, messageId: Long?, step: Pair<Int,Int>?) {
        val keyboardButtons = tables.map { table ->
            com.github.kotlintelegrambot.entities.keyboard.InlineKeyboardButton.CallbackData(
                text = strings.tableButtonText(table.number, table.seats),
                callbackData = "${BotConstants.CB_PREFIX_CHOOSE_TABLE}${table.id}"
            )
        }
        val keyboard = if (tables.isNotEmpty()) InlineKeyboardMarkup.create(keyboardButtons.map { listOf(it) }) else null
        val text = if (tables.isNotEmpty()) strings.chooseTablePromptForClubAndDate(clubTitle, date.toString()) else strings.noAvailableTables

        if (messageId != null) editMessageText(chatId, messageId, text, keyboard)
        else sendMessage(chatId, text, keyboard)
    }

    override suspend fun sendChooseSlotKeyboard(chatId: ChatId, clubId: Int, clubTitle: String, date: LocalDate, table: TableInfo, slots: List<String>, strings: LocalizedStrings, messageId: Long?, step: Pair<Int,Int>?) {
        val keyboardButtons = slots.map { slot ->
            com.github.kotlintelegrambot.entities.keyboard.InlineKeyboardButton.CallbackData(
                text = slot,
                callbackData = "${BotConstants.CB_PREFIX_CHOOSE_SLOT}${table.id}:$slot"
            )
        }
        val keyboard = if (slots.isNotEmpty()) InlineKeyboardMarkup.create(keyboardButtons.map { listOf(it) }) else null
        val text = if (slots.isNotEmpty()) strings.chooseSlotForTableInClub(table.number, clubTitle) else strings.noAvailableSlots

        if (messageId != null) editMessageText(chatId, messageId, text, keyboard)
        else sendMessage(chatId, text, keyboard)
    }

    override suspend fun askGuestCount(chatId: ChatId, clubTitle: String, tableLabel: String, date: LocalDate, slot: String, strings: LocalizedStrings, messageId: Long?, step: Pair<Int,Int>?) {
        val text = "Стол: $tableLabel, $date, $slot.\n${strings.askPeopleCount}"
        if (messageId != null) editMessageText(chatId, messageId, text, null)
        else sendMessage(chatId, text, null)
    }

    override suspend fun askGuestCountInvalid(chatId: ChatId, min: Int, max: Int, strings: LocalizedStrings, messageId: Long?) {
        sendMessage(chatId, strings.invalidPeopleCount(min, max))
    }

    override suspend fun askGuestName(chatId: ChatId, strings: LocalizedStrings, messageId: Long?, step: Pair<Int,Int>?) {
        val text = strings.askGuestName
        if (messageId != null) editMessageText(chatId, messageId, text, null)
        else sendMessage(chatId, text, null)
    }

    override suspend fun askGuestNameInvalid(chatId: ChatId, strings: LocalizedStrings, messageId: Long?) {
        sendMessage(chatId, strings.invalidGuestName)
    }

    override suspend fun askGuestPhone(chatId: ChatId, strings: LocalizedStrings, messageId: Long?, step: Pair<Int,Int>?) {
        val text = strings.askGuestPhone
        if (messageId != null) editMessageText(chatId, messageId, text, null)
        else sendMessage(chatId, text, null)
    }

    override suspend fun askGuestPhoneInvalid(chatId: ChatId, strings: LocalizedStrings, messageId: Long?) {
        sendMessage(chatId, strings.invalidPhoneFormat)
    }

    override suspend fun showConfirmBooking(chatId: ChatId, draft: DraftBooking, strings: LocalizedStrings, messageId: Long?, step: Pair<Int,Int>?) {
        val text = strings.bookingDetailsFormat(
            clubName = draft.clubTitle,
            tableNumber = draft.tableLabel.filter { it.isDigit() }.toIntOrNull() ?: 0,
            guestCount = draft.peopleCount,
            date = draft.date.format(DateTimeFormatter.ISO_LOCAL_DATE),
            timeSlot = draft.slot,
            guestName = draft.guestName,
            guestPhone = draft.guestPhone
        ) + "\n\n" + strings.confirmBookingPrompt

        val keyboard = InlineKeyboardMarkup.create(listOf(
            listOf(com.github.kotlintelegrambot.entities.keyboard.InlineKeyboardButton.CallbackData(strings.buttonConfirm, BotConstants.CB_PREFIX_CONFIRM_BOOKING)),
            listOf(com.github.kotlintelegrambot.entities.keyboard.InlineKeyboardButton.CallbackData(strings.buttonCancel, BotConstants.CB_PREFIX_CANCEL_ACTION))
        ))

        if (messageId != null) editMessageText(chatId, messageId, text, keyboard)
        else sendMessage(chatId, text, keyboard)
    }

    override suspend fun sendConfirmMessage(chatId: ChatId, draft: DraftBooking, clubName: String, tableNumber: Int, selectedDate: LocalDate, strings: LocalizedStrings, messageId: Long?, step: Pair<Int,Int>?) {
        showConfirmBooking(chatId, draft, strings, messageId, step)
    }

    override suspend fun sendBookingSuccessMessage(chatId: ChatId, bookingId: Int, loyaltyPoints: Int, strings: LocalizedStrings, clubs: List<Club>, messageId: Long?) {
        val text = strings.bookingSuccess(bookingId, loyaltyPoints)
        if (messageId != null) editMessageText(chatId, messageId, text, null)
        else sendMessage(chatId, text, null)
    }

    override suspend fun sendActionCancelledMessage(chatId: ChatId, strings: LocalizedStrings, clubs: List<Club>, messageId: Long?) {
        val text = strings.actionCancelled
        if (messageId != null) editMessageText(chatId, messageId, text, null)
        else sendMessage(chatId, text, null)
    }

    override suspend fun sendBookingCancelledMessage(chatId: ChatId, strings: LocalizedStrings, clubs: List<Club>, messageId: Long?) {
        sendMessage(chatId, strings.bookingCancelledMessage)
    }

    override suspend fun sendErrorMessage(chatId: ChatId, strings: LocalizedStrings, message: String?, clubs: List<Club>, messageId: Long?) {
        val text = message ?: strings.errorMessageDefault
        if (messageId != null) editMessageText(chatId, messageId, text, null)
        else sendMessage(chatId, text, null)
    }
    override suspend fun sendVenueSelection(chatId: ChatId, venues: List<Club>, strings: LocalizedStrings, messageId: Long?) {
        sendMessage(chatId, strings.chooseVenueForInfo)
    }

    override suspend fun sendVenueDetails(chatId: ChatId, venue: Club, strings: LocalizedStrings, messageId: Long?) {
        sendMessage(chatId, strings.venueDetails(venue.name, venue.description, venue.address, venue.phone, venue.workingHours))
    }

    override suspend fun sendMyBookingsList(chatId: ChatId, bookings: List<BookingWithClubName>, strings: LocalizedStrings, clubs: List<Club>, messageId: Long?) {
        if (bookings.isEmpty()) {
            sendMessage(chatId, strings.noActiveBookings)
        } else {
            val bookingsText = bookings.joinToString("\n") {
                strings.bookingItemFormat(it.booking.id, it.clubName, it.tableNumber, it.booking.dateStart.toLocalDate().toString(), it.booking.dateStart.toLocalTime().toString() + "-" + it.booking.dateEnd.toLocalTime().toString(), it.booking.guestsCount, strings.bookingStatusToText(it.booking.status))
            }
            sendMessage(chatId, "${strings.myBookingsHeader}\n$bookingsText")
        }
    }
    override suspend fun sendManageBookingOptions(chatId: ChatId, booking: BookingWithClubName, strings: LocalizedStrings, messageId: Long) {
        val details = strings.bookingItemFormat(booking.booking.id, booking.clubName, booking.tableNumber, booking.booking.dateStart.toLocalDate().toString(), booking.booking.dateStart.toLocalTime().toString(), booking.booking.guestsCount, strings.bookingStatusToText(booking.booking.status))
        sendMessage(chatId, strings.manageBookingPrompt(booking.booking.id, details))
    }
    override suspend fun sendBookingCancellationConfirmed(chatId: ChatId, bookingId: Int, clubName: String, strings: LocalizedStrings, clubs: List<Club>, messageId: Long) {
        sendMessage(chatId, strings.bookingCancellationConfirmed(bookingId, clubName))
    }
    override suspend fun sendChangeBookingInfo(chatId: ChatId, strings: LocalizedStrings, messageId: Long) {
        sendMessage(chatId, strings.changeBookingInfo)
    }
    override suspend fun sendAskQuestionPrompt(chatId: ChatId, strings: LocalizedStrings, messageId: Long?) {
        sendMessage(chatId, strings.askQuestionPrompt)
    }
    override suspend fun sendQuestionReceived(chatId: ChatId, strings: LocalizedStrings, clubs: List<Club>, messageId: Long?) {
        sendMessage(chatId, strings.questionReceived)
    }
    override suspend fun sendHelpMessage(chatId: ChatId, strings: LocalizedStrings, clubs: List<Club>, messageId: Long?) {
        sendMessage(chatId, strings.helpText)
    }
    override suspend fun askForFeedback(chatId: ChatId, bookingId: Int, clubName: String, strings: LocalizedStrings, messageId: Long) {
        sendMessage(chatId, strings.askForFeedbackPrompt(clubName, bookingId))
    }
    override suspend fun sendFeedbackThanks(chatId: ChatId, rating: Int, strings: LocalizedStrings, clubs: List<Club>, messageId: Long) {
        sendMessage(chatId, strings.feedbackThanks(rating))
    }
    override suspend fun sendInfoMessage(chatId: ChatId, message: String, strings: LocalizedStrings, clubs: List<Club>, messageId: Long?) {
        if (messageId != null) editMessageText(chatId, messageId, message)
        else sendMessage(chatId, message)
    }
    override suspend fun sendUnknownCommandMessage(chatId: ChatId, strings: LocalizedStrings, clubs: List<Club>, messageId: Long?) {
        sendMessage(chatId, strings.unknownCommand)
    }
    override suspend fun sendFeatureInDevelopmentMessage(chatId: ChatId, strings: LocalizedStrings, messageId: Long?) {
        sendMessage(chatId, strings.featureInDevelopment)
    }
}