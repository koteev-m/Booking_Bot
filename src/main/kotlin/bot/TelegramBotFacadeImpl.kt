package bot

import db.Club
import db.TableInfo
import db.BookingWithClubName
import db.DraftBooking
import bot.LocalizedStrings
import bot.StringProviderFactory
import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.bot
import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.InlineKeyboardMarkup
import com.github.kotlintelegrambot.entities.KeyboardReplyMarkup
import com.github.kotlintelegrambot.entities.ParseMode
import com.github.kotlintelegrambot.entities.keyboard.KeyboardButton
import com.github.kotlintelegrambot.entities.keyboard.InlineKeyboardButton
import com.github.kotlintelegrambot.network.fold
import fsm.BotFacade
import fsm.BotConstants
import fsm.FsmStates
import org.slf4j.LoggerFactory
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

class TelegramBotFacadeImpl(
    private val bot: Bot,
    private val defaultZoneId: ZoneId = ZoneId.of("Europe/Moscow")
) : BotFacade {

    private val logger = LoggerFactory.getLogger(TelegramBotFacadeImpl::class.java)

    private fun getTimeFormatter(strings: LocalizedStrings): DateTimeFormatter =
        DateTimeFormatter.ofPattern("HH:mm").withZone(defaultZoneId)

    private fun getDateFormatter(strings: LocalizedStrings): DateTimeFormatter =
        DateTimeFormatter.ofPattern("dd MMMM yyyy", Locale(strings.languageCode))
            .withZone(defaultZoneId)

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
                val inlineMarkup = replyMarkup as? InlineKeyboardMarkup
                bot.editMessageText(
                    chatId = chatId,
                    messageId = messageIdToEdit,
                    text = text,
                    replyMarkup = inlineMarkup,
                    parseMode = parseMode
                ).fold(
                    { response ->
                        if (!response.ok) {
                            logger.warn("Failed to edit message $messageIdToEdit for chat $chatId: ${response.description}")
                        }
                    },
                    { error ->
                        logger.error("Exception editing message $messageIdToEdit for chat $chatId: ${error.message}", error.exception)
                    }
                )
            } else {
                when (replyMarkup) {
                    is KeyboardReplyMarkup ->
                        bot.sendMessage(chatId = chatId, text = text, replyMarkup = replyMarkup, parseMode = parseMode)
                    is InlineKeyboardMarkup ->
                        bot.sendMessage(chatId = chatId, text = text, replyMarkup = replyMarkup, parseMode = parseMode)
                    else ->
                        bot.sendMessage(chatId = chatId, text = text, parseMode = parseMode)
                }
            }
        } catch (e: Exception) {
            logger.error("Error in TelegramBotFacade.send for chat $chatId: ${e.message}", e)
        }
    }

    private fun createMainMenuKeyboard(strings: LocalizedStrings, clubs: List<Club>): KeyboardReplyMarkup {
        val clubButtons = clubs.filter { it.isActive }
            .take(2)
            .map { KeyboardButton(strings.menuBookTableInClub(it.title)) }

        val rows = listOfNotNull(
            listOf(KeyboardButton(strings.menuVenueInfo), KeyboardButton(strings.menuMyBookings)),
            clubButtons.ifEmpty { null },
            listOf(KeyboardButton(strings.menuAskQuestion), KeyboardButton(strings.menuHelp)),
            listOf(KeyboardButton(strings.menuChangeLanguage), KeyboardButton(strings.menuOpenApp))
        ).filterNotNull()

        return KeyboardReplyMarkup(
            keyboard = rows,
            resizeKeyboard = true,
            oneTimeKeyboard = false
        )
    }

    private fun createMainMenuInlineKeyboard(strings: LocalizedStrings, clubs: List<Club>): InlineKeyboardMarkup {
        val clubButtons = clubs.filter { it.isActive }
            .take(2)
            .map {
                InlineKeyboardButton.CallbackData(
                    strings.menuBookTableInClub(it.title),
                    "${BotConstants.CB_PREFIX_BOOK_CLUB}${it.id}"
                )
            }

        val rows = listOfNotNull(
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
                InlineKeyboardButton.CallbackData(strings.menuOpenApp, BotConstants.CB_MAIN_MENU_OPEN_APP)
            )
        ).filterNotNull()

        return InlineKeyboardMarkup.create(rows)
    }

    override suspend fun sendWelcomeMessage(
        chatId: ChatId,
        userName: String?,
        strings: LocalizedStrings,
        clubs: List<Club>
    ) {
        val greeting = strings.welcomeMessage(userName)
        send(chatId, greeting, strings, createMainMenuKeyboard(strings, clubs))
    }

    override suspend fun sendLanguageSelection(
        chatId: ChatId,
        currentStrings: LocalizedStrings,
        messageId: Long?
    ) {
        val buttons = StringProviderFactory.allLanguages().map { lang ->
            InlineKeyboardButton.CallbackData(
                lang.languageName,
                "${BotConstants.CB_PREFIX_SELECT_LANG}${lang.languageCode}"
            )
        }
        val backRow = messageId?.let { listOf(backButtonToMainMenu(currentStrings)) }
        val rows = (buttons.chunked(1) + listOfNotNull(backRow)).filterNotNull()
        val keyboard = InlineKeyboardMarkup.create(rows)
        send(chatId, currentStrings.chooseLanguagePrompt, currentStrings, keyboard, messageIdToEdit = messageId)
    }

    override suspend fun sendMainMenu(
        chatId: ChatId,
        strings: LocalizedStrings,
        clubs: List<Club>,
        messageId: Long?,
        text: String?
    ) {
        val messageText = text ?: strings.chooseAction
        if (messageId != null) {
            send(chatId, messageText, strings, createMainMenuInlineKeyboard(strings, clubs), messageIdToEdit = messageId)
        } else {
            send(chatId, messageText, strings, createMainMenuKeyboard(strings, clubs))
        }
    }

    override suspend fun sendStepTrackerMessage(
        chatId: ChatId,
        strings: LocalizedStrings,
        currentStep: Int,
        totalSteps: Int,
        messageText: String,
        keyboard: InlineKeyboardMarkup?,
        messageId: Long?
    ) {
        val fullText = "${strings.stepTracker(currentStep, totalSteps)}\n\n$messageText"
        send(chatId, fullText, strings, keyboard, messageIdToEdit = messageId)
    }

    override suspend fun sendChooseClubKeyboard(
        chatId: ChatId,
        clubs: List<Club>,
        strings: LocalizedStrings,
        messageId: Long?,
        step: Pair<Int, Int>?
    ) {
        val activeClubs = clubs.filter { it.isActive }
        val buttons = activeClubs.map { club ->
            InlineKeyboardButton.CallbackData(club.title, "${BotConstants.CB_PREFIX_BOOK_CLUB}${club.id}")
        }
        val rows = buttons.chunked(2).toMutableList()
        messageId?.let { rows.add(listOf(backButtonToMainMenu(strings))) }
        val keyboard = InlineKeyboardMarkup.create(rows)
        val text = step?.let { "${strings.stepTracker(it.first, it.second)}\n\n${strings.chooseClubPrompt}" }
            ?: strings.chooseClubPrompt
        send(chatId, text, strings, keyboard, messageIdToEdit = messageId)
    }

    override suspend fun sendCalendar(
        chatId: ChatId,
        yearMonth: YearMonth,
        strings: LocalizedStrings,
        messageId: Long?,
        step: Pair<Int, Int>?
    ) {
        val today = LocalDate.now(defaultZoneId)
        val firstOfMonth = yearMonth.atDay(1)
        val firstDayValue = firstOfMonth.dayOfWeek.value
        val leadingEmpty = (firstDayValue - DayOfWeek.MONDAY.value + 7) % 7

        val rows = mutableListOf<List<InlineKeyboardButton>>()
        rows.add(
            listOf(
                InlineKeyboardButton.CallbackData(
                    strings.calendarMonthYear(firstOfMonth.month, yearMonth.year),
                    "cal_ignore_month_year"
                )
            )
        )
        rows.add(
            DayOfWeek.values().map {
                InlineKeyboardButton.CallbackData(it.getDisplayName(TextStyle.SHORT, Locale(strings.languageCode)), "cal_ignore_weekday")
            }
        )

        var week = mutableListOf<InlineKeyboardButton>()
        repeat(leadingEmpty) {
            week.add(InlineKeyboardButton.CallbackData(" ", "cal_ignore_empty"))
        }

        var pointer = firstOfMonth
        while (pointer.month == yearMonth.month) {
            val textDay = strings.calendarDay(pointer.dayOfMonth)
            val data = if (pointer.isBefore(today) || pointer.isAfter(today.plusMonths(3))) {
                "cal_ignore_disabled"
            } else {
                "${BotConstants.CB_PREFIX_CHOOSE_DATE_CAL}$pointer"
            }
            week.add(InlineKeyboardButton.CallbackData(textDay, data))

            if (week.size == 7) {
                rows.add(week.toList())
                week.clear()
            }
            pointer = pointer.plusDays(1)
        }

        if (week.isNotEmpty()) {
            while (week.size < 7) {
                week.add(InlineKeyboardButton.CallbackData(" ", "cal_ignore_empty"))
            }
            rows.add(week.toList())
        }

        val navRow = mutableListOf<InlineKeyboardButton>()
        if (yearMonth.isAfter(YearMonth.from(today))) {
            navRow.add(
                InlineKeyboardButton.CallbackData(
                    strings.calendarPrevMonth,
                    "${BotConstants.CB_PREFIX_CAL_MONTH_CHANGE}prev_${yearMonth.minusMonths(1)}"
                )
            )
        } else {
            navRow.add(InlineKeyboardButton.CallbackData(" ", "cal_ignore_empty"))
        }
        if (yearMonth.isBefore(YearMonth.from(today.plusMonths(2)))) {
            navRow.add(
                InlineKeyboardButton.CallbackData(
                    strings.calendarNextMonth,
                    "${BotConstants.CB_PREFIX_CAL_MONTH_CHANGE}next_${yearMonth.plusMonths(1)}"
                )
            )
        } else {
            navRow.add(InlineKeyboardButton.CallbackData(" ", "cal_ignore_empty"))
        }
        rows.add(navRow)
        rows.add(listOf(backButton(strings, BotConstants.CB_PREFIX_BACK_TO + FsmStates.ChooseClub.name)))

        val text = step?.let { "${strings.stepTracker(it.first, it.second)}\n\n${strings.chooseDatePrompt}" }
            ?: strings.chooseDatePrompt
        val keyboard = InlineKeyboardMarkup.create(rows)
        send(chatId, text, strings, keyboard, messageIdToEdit = messageId)
    }

    override suspend fun sendChooseTableKeyboard(
        chatId: ChatId,
        tables: List<TableInfo>,
        club: Club,
        selectedDate: LocalDate,
        strings: LocalizedStrings,
        messageId: Long?,
        step: Pair<Int, Int>?
    ) {
        val planUrl = club.floorPlanImageUrl ?: BotConstants.TABLE_LAYOUT_PLACEHOLDER_URL
        bot.sendPhoto(chatId, photoUrl = planUrl, caption = strings.tableLayoutInfo).fold(
            { /* sent */ },
            { error -> logger.error("Error sending floor plan for club ${club.id}: ${error.errorBody}") }
        )

        val (text, keyboard) = if (tables.isEmpty()) {
            strings.noAvailableTables to InlineKeyboardMarkup.create(
                listOf(listOf(backButton(strings, BotConstants.CB_PREFIX_BACK_TO + FsmStates.ChooseDate.name)))
            )
        } else {
            val buttons = tables.map { table ->
                InlineKeyboardButton.CallbackData(
                    strings.tableButtonText(table.number, table.seats),
                    "${BotConstants.CB_PREFIX_CHOOSE_TABLE}${table.id}"
                )
            }
            "${strings.chooseTablePrompt} (Клуб: ${club.title}, Дата: ${selectedDate.format(getDateFormatter(strings))})" to
                    InlineKeyboardMarkup.create(
                        buttons.chunked(1) +
                                listOf(listOf(backButton(strings, BotConstants.CB_PREFIX_BACK_TO + FsmStates.ChooseDate.name)))
                    )
        }

        val fullText = step?.let { "${strings.stepTracker(it.first, it.second)}\n\n$text" } ?: text
        send(chatId, fullText, strings, keyboard, messageIdToEdit = messageId)
    }

    override suspend fun askPeopleCount(
        chatId: ChatId,
        strings: LocalizedStrings,
        messageId: Long?,
        step: Pair<Int, Int>?
    ) {
        val text = "${strings.askPeopleCount}"
        val fullText = step?.let { "${strings.stepTracker(it.first, it.second)}\n\n$text" } ?: text
        val keyboard = InlineKeyboardMarkup.create(
            listOf(listOf(backButton(strings, BotConstants.CB_PREFIX_BACK_TO + FsmStates.ChooseTable.name)))
        )
        send(chatId, fullText, strings, keyboard, messageIdToEdit = messageId)
    }

    override suspend fun sendChooseSlotKeyboard(
        chatId: ChatId,
        club: Club,
        table: TableInfo,
        slots: List<Pair<Instant, Instant>>,
        strings: LocalizedStrings,
        messageId: Long?,
        step: Pair<Int, Int>?
    ) {
        val (text, keyboard) = if (slots.isEmpty()) {
            strings.noAvailableSlots to InlineKeyboardMarkup.create(
                listOf(listOf(backButton(strings, BotConstants.CB_PREFIX_BACK_TO + FsmStates.EnterPeople.name)))
            )
        } else {
            val timeFmt = getTimeFormatter(strings)
            val buttons = slots.map { (start, end) ->
                val slotText = "${timeFmt.format(start)} - ${timeFmt.format(end)}"
                InlineKeyboardButton.CallbackData(slotText, "${BotConstants.CB_PREFIX_CHOOSE_SLOT}${start.epochSecond}:${end.epochSecond}")
            }
            strings.chooseSlotForTableInClub(table.number, club.title) to
                    InlineKeyboardMarkup.create(
                        buttons.chunked(2),
                        listOf(listOf(backButton(strings, BotConstants.CB_PREFIX_BACK_TO + FsmStates.EnterPeople.name)))
                    )
        }

        val fullText = step?.let { "${strings.stepTracker(it.first, it.second)}\n\n$text" } ?: text
        send(chatId, fullText, strings, keyboard, messageIdToEdit = messageId)
    }

    override suspend fun askGuestName(
        chatId: ChatId,
        strings: LocalizedStrings,
        messageId: Long?,
        step: Pair<Int, Int>?
    ) {
        val text = "${strings.askGuestName} ${strings.askGuestNameExample}"
        val fullText = step?.let { "${strings.stepTracker(it.first, it.second)}\n\n$text" } ?: text
        val keyboard = InlineKeyboardMarkup.create(
            listOf(listOf(backButton(strings, BotConstants.CB_PREFIX_BACK_TO + FsmStates.ChooseSlot.name)))
        )
        send(chatId, fullText, strings, keyboard, messageIdToEdit = messageId)
    }

    override suspend fun askGuestPhone(
        chatId: ChatId,
        strings: LocalizedStrings,
        messageId: Long?,
        step: Pair<Int, Int>?
    ) {
        val text = "${strings.askGuestPhone} ${strings.askGuestPhoneExample}"
        val fullText = step?.let { "${strings.stepTracker(it.first, it.second)}\n\n$text" } ?: text
        val keyboard = InlineKeyboardMarkup.create(
            listOf(listOf(backButton(strings, BotConstants.CB_PREFIX_BACK_TO + FsmStates.EnterGuestName.name)))
        )
        send(chatId, fullText, strings, keyboard, messageIdToEdit = messageId)
    }

    override suspend fun sendConfirmMessage(
        chatId: ChatId,
        draft: DraftBooking,
        clubName: String,
        tableNumber: Int,
        selectedDate: LocalDate,
        strings: LocalizedStrings,
        messageId: Long?,
        step: Pair<Int, Int>?
    ) {
        val timeFmt = getTimeFormatter(strings)
        val dateFmt = getDateFormatter(strings)
        val slotStart = draft.slotStart?.let { timeFmt.format(it) } ?: "N/A"
        val slotEnd = draft.slotEnd?.let { timeFmt.format(it) } ?: "N/A"
        val dateStr = dateFmt.format(selectedDate)

        val details = strings.bookingDetailsFormat(
            clubName, tableNumber, draft.guests ?: -1,
            dateStr, "$slotStart – $slotEnd",
            draft.guestName ?: "N/A", draft.guestPhone ?: "N/A"
        )
        val text = "${strings.confirmBookingPrompt}\n\n$details"

        val keyboard = InlineKeyboardMarkup.create(
            listOf(
                listOf(
                    InlineKeyboardButton.CallbackData(strings.buttonConfirm, BotConstants.CB_PREFIX_CONFIRM_BOOKING),
                    InlineKeyboardButton.CallbackData(strings.buttonCancel, BotConstants.CB_PREFIX_CANCEL_ACTION)
                )
            ),
            listOf(listOf(backButton(strings, BotConstants.CB_PREFIX_BACK_TO + FsmStates.EnterGuestPhone.name)))
        )
        val fullText = step?.let { "${strings.stepTracker(it.first, it.second)}\n\n$text" } ?: text
        send(chatId, fullText, strings, keyboard, messageIdToEdit = messageId)
    }

    override suspend fun sendBookingSuccessMessage(
        chatId: ChatId,
        bookingId: Int,
        loyaltyPoints: Int,
        strings: LocalizedStrings,
        clubs: List<Club>,
        messageId: Long?
    ) {
        val text = strings.bookingSuccess(bookingId, loyaltyPoints)
        send(chatId, text, strings, createMainMenuInlineKeyboard(strings, clubs), messageIdToEdit = messageId)
    }

    override suspend fun sendBookingCancelledMessage(
        chatId: ChatId,
        strings: LocalizedStrings,
        clubs: List<Club>,
        messageId: Long?
    ) {
        send(chatId, strings.bookingCancelledMessage, strings, createMainMenuInlineKeyboard(strings, clubs), messageIdToEdit = messageId)
    }

    override suspend fun sendActionCancelledMessage(
        chatId: ChatId,
        strings: LocalizedStrings,
        clubs: List<Club>,
        messageId: Long?
    ) {
        send(chatId, strings.actionCancelled, strings, createMainMenuInlineKeyboard(strings, clubs), messageIdToEdit = messageId)
    }

    override suspend fun sendVenueSelection(
        chatId: ChatId,
        venues: List<Club>,
        strings: LocalizedStrings,
        messageId: Long?
    ) {
        val active = venues.filter { it.isActive }
        val buttons = active.map {
            InlineKeyboardButton.CallbackData(it.title, "${BotConstants.CB_PREFIX_VENUE_INFO_SHOW}${it.id}")
        }
        val keyboard = InlineKeyboardMarkup.create(
            buttons.chunked(2),
            listOf(listOf(backButtonToMainMenu(strings)))
        )
        send(chatId, strings.chooseVenueForInfo, strings, keyboard, messageIdToEdit = messageId)
    }

    override suspend fun sendVenueDetails(
        chatId: ChatId,
        venue: Club,
        strings: LocalizedStrings,
        messageId: Long?
    ) {
        val text = strings.venueDetails(
            venue.title, venue.description,
            venue.address, venue.phone, venue.workingHours
        )
        val keyboard = InlineKeyboardMarkup.create(
            listOf(
                listOf(
                    InlineKeyboardButton.CallbackData(strings.venueInfoButtonPosters, "${BotConstants.CB_PREFIX_VENUE_POSTERS}${venue.id}"),
                    InlineKeyboardButton.CallbackData(strings.venueInfoButtonPhotos, "${BotConstants.CB_PREFIX_VENUE_PHOTOS}${venue.id}")
                )
            ),
            listOf(listOf(backButton(strings, BotConstants.CB_PREFIX_BACK_TO + FsmStates.ShowVenueList.name)))
        )
        send(chatId, text, strings, keyboard, messageIdToEdit = messageId)
    }

    override suspend fun sendMyBookingsList(
        chatId: ChatId,
        bookings: List<BookingWithClubName>,
        strings: LocalizedStrings,
        clubs: List<Club>,
        messageId: Long?
    ) {
        val (text, keyboard) = if (bookings.isEmpty()) {
            strings.noActiveBookings to InlineKeyboardMarkup.create(
                listOf(listOf(backButtonToMainMenu(strings)))
            )
        } else {
            val dateFmt = getDateFormatter(strings)
            val timeFmt = getTimeFormatter(strings)
            val buttons = bookings.map { b ->
                InlineKeyboardButton.CallbackData(
                    strings.bookingItemFormat(
                        b.id, b.clubName, b.tableNumber,
                        dateFmt.format(b.dateStart), timeFmt.format(b.dateStart),
                        b.guestsCount, b.status.name
                    ),
                    "${BotConstants.CB_PREFIX_MANAGE_BOOKING}${b.id}"
                )
            }
            strings.myBookingsHeader to InlineKeyboardMarkup.create(
                buttons.chunked(1),
                listOf(listOf(backButtonToMainMenu(strings)))
            )
        }
        send(chatId, text, strings, keyboard, messageIdToEdit = messageId)
    }

    override suspend fun sendManageBookingOptions(
        chatId: ChatId,
        booking: BookingWithClubName,
        strings: LocalizedStrings,
        messageId: Long
    ) {
        val dateFmt = getDateFormatter(strings)
        val timeFmt = getTimeFormatter(strings)
        val details = strings.bookingDetailsFormat(
            booking.clubName, booking.tableNumber, booking.guestsCount,
            dateFmt.format(booking.dateStart),
            "${timeFmt.format(booking.dateStart)} – ${timeFmt.format(booking.dateEnd)}",
            booking.guestName ?: "N/A", booking.guestPhone ?: "N/A"
        )
        val text = strings.manageBookingPrompt(booking.id, details)
        val rows = mutableListOf<List<InlineKeyboardButton>>()
        rows.add(
            listOf(
                InlineKeyboardButton.CallbackData(strings.buttonChangeBooking, "${BotConstants.CB_PREFIX_DO_CHANGE_BOOKING}${booking.id}"),
                InlineKeyboardButton.CallbackData(strings.buttonCancelBooking, "${BotConstants.CB_PREFIX_DO_CANCEL_BOOKING}${booking.id}")
            )
        )
        if (booking.dateEnd.isBefore(Instant.now()) &&
            booking.status == BookingStatus.COMPLETED &&
            booking.feedbackRating == null) {
            rows.add(
                listOf(
                    InlineKeyboardButton.CallbackData(strings.buttonRateBooking, "${BotConstants.CB_PREFIX_RATE_BOOKING}${booking.id}")
                )
            )
        }
        rows.add(listOf(backButton(strings, BotConstants.CB_PREFIX_BACK_TO + FsmStates.ShowMyBookingsList.name)))
        val keyboard = InlineKeyboardMarkup.create(rows)
        send(chatId, text, strings, keyboard, messageIdToEdit = messageId)
    }

    override suspend fun sendBookingCancellationConfirmed(
        chatId: ChatId,
        bookingId: Int,
        clubName: String,
        strings: LocalizedStrings,
        clubs: List<Club>,
        messageId: Long
    ) {
        val text = strings.bookingCancellationConfirmed(bookingId, clubName)
        send(chatId, text, strings, createMainMenuInlineKeyboard(strings, clubs), messageIdToEdit = messageId)
    }

    override suspend fun sendChangeBookingInfo(
        chatId: ChatId,
        strings: LocalizedStrings,
        messageId: Long
    ) {
        val keyboard = InlineKeyboardMarkup.create(
            listOf(listOf(backButton(strings, BotConstants.CB_PREFIX_BACK_TO + FsmStates.ShowMyBookingsList.name)))
        )
        send(chatId, strings.changeBookingInfo, strings, keyboard, messageIdToEdit = messageId)
    }

    override suspend fun sendAskQuestionPrompt(
        chatId: ChatId,
        strings: LocalizedStrings,
        messageId: Long?
    ) {
        val keyboard = InlineKeyboardMarkup.create(
            listOf(listOf(backButtonToMainMenu(strings)))
        )
        send(chatId, strings.askQuestionPrompt, strings, keyboard, messageIdToEdit = messageId)
    }

    override suspend fun sendQuestionReceived(
        chatId: ChatId,
        strings: LocalizedStrings,
        clubs: List<Club>,
        messageId: Long?
    ) {
        send(chatId, strings.questionReceived, strings, createMainMenuInlineKeyboard(strings, clubs), messageIdToEdit = messageId)
    }

    override suspend fun sendHelpMessage(
        chatId: ChatId,
        strings: LocalizedStrings,
        clubs: List<Club>,
        messageId: Long?
    ) {
        send(chatId, strings.helpText, strings, createMainMenuInlineKeyboard(strings, clubs), messageIdToEdit = messageId)
    }

    override suspend fun askForFeedback(
        chatId: ChatId,
        bookingId: Int,
        clubName: String,
        strings: LocalizedStrings,
        messageId: Long
    ) {
        val text = strings.askForFeedbackPrompt(clubName, bookingId)
        val ratingButtons = (1..5).map { rating ->
            InlineKeyboardButton.CallbackData("⭐".repeat(rating), "${BotConstants.CB_PREFIX_RATE_BOOKING}$bookingId:$rating")
        }
        val keyboard = InlineKeyboardMarkup.create(
            listOf(ratingButtons),
            listOf(listOf(backButton(strings, BotConstants.CB_PREFIX_BACK_TO + FsmStates.ManageBookingOptions.name)))
        )
        send(chatId, text, strings, keyboard, messageIdToEdit = messageId)
    }

    override suspend fun sendFeedbackThanks(
        chatId: ChatId,
        rating: Int,
        strings: LocalizedStrings,
        clubs: List<Club>,
        messageId: Long
    ) {
        send(chatId, strings.feedbackThanks(rating), strings, createMainMenuInlineKeyboard(strings, clubs), messageIdToEdit = messageId)
    }

    override suspend fun sendErrorMessage(
        chatId: ChatId,
        strings: LocalizedStrings,
        message: String?,
        clubs: List<Club>,
        messageId: Long?
    ) {
        val text = message ?: strings.errorMessageDefault
        send(chatId, text, strings, createMainMenuInlineKeyboard(strings, clubs), messageIdToEdit = messageId)
    }

    override suspend fun sendInfoMessage(
        chatId: ChatId,
        message: String,
        strings: LocalizedStrings,
        clubs: List<Club>,
        messageId: Long?
    ) {
        send(chatId, message, strings, createMainMenuInlineKeyboard(strings, clubs), messageIdToEdit = messageId)
    }

    override suspend fun sendUnknownCommandMessage(
        chatId: ChatId,
        strings: LocalizedStrings,
        clubs: List<Club>,
        messageId: Long?
    ) {
        send(chatId, strings.unknownCommand, strings, createMainMenuKeyboard(strings, clubs))
    }

    override suspend fun sendFeatureInDevelopmentMessage(
        chatId: ChatId,
        strings: LocalizedStrings,
        messageId: Long?
    ) {
        val keyboard = InlineKeyboardMarkup.create(
            listOf(listOf(backButtonToMainMenu(strings)))
        )
        send(chatId, strings.featureInDevelopment, strings, keyboard, messageIdToEdit = messageId)
    }

    private fun backButton(strings: LocalizedStrings, callbackData: String): InlineKeyboardButton =
        InlineKeyboardButton.CallbackData(strings.buttonBack, callbackData)

    private fun backButtonToMainMenu(strings: LocalizedStrings): InlineKeyboardButton =
        InlineKeyboardButton.CallbackData(strings.buttonBack, BotConstants.CB_PREFIX_BACK_TO + FsmStates.MainMenu.name)
}