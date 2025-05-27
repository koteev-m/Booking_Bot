package bot

import db.BookingStatus // Импорт для BookingStatus
import db.Club
import db.TableInfo
import db.BookingWithClubName
import fsm.DraftBooking
import bot.LocalizedStrings
import bot.StringProviderFactory
import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.InlineKeyboardMarkup
import com.github.kotlintelegrambot.entities.KeyboardReplyMarkup
import com.github.kotlintelegrambot.entities.ParseMode
import com.github.kotlintelegrambot.entities.keyboard.KeyboardButton
import com.github.kotlintelegrambot.entities.keyboard.InlineKeyboardButton
import com.github.kotlintelegrambot.network.Response
import com.github.kotlintelegrambot.network.TelegramError
import com.github.kotlintelegrambot.network.fold // Убедитесь, что этот fold используется
import fsm.BotFacade
import fsm.FsmStates // Убедитесь, что это ваш fsm.FsmStates
import org.slf4j.LoggerFactory
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import com.github.kotlintelegrambot.entities.Message
import com.github.kotlintelegrambot.entities.TelegramFile // Для sendPhoto

class TelegramBotFacadeImpl(
    private val bot: Bot,
    private val defaultZoneId: ZoneId = ZoneId.of("Europe/Moscow")
) : BotFacade {

    private val logger = LoggerFactory.getLogger(TelegramBotFacadeImpl::class.java)

    private fun getTimeFormatter(strings: LocalizedStrings): DateTimeFormatter =
        DateTimeFormatter.ofPattern("HH:mm").withZone(defaultZoneId)

    private fun getDateFormatter(strings: LocalizedStrings): DateTimeFormatter =
        DateTimeFormatter.ofPattern("dd MMMM uuuu", Locale(strings.languageCode))
            .withZone(defaultZoneId)

    private suspend fun send(
        chatId: ChatId,
        text: String,
        strings: LocalizedStrings, // strings добавлен для общности, но не всегда используется напрямую здесь
        replyMarkup: Any? = null,
        parseMode: ParseMode = ParseMode.HTML,
        messageIdToEdit: Long? = null
    ) {
        try {
            if (messageIdToEdit != null) {
                val inlineMarkup = replyMarkup as? InlineKeyboardMarkup
                bot.editMessageText( // Возвращает Pair<Response<Message>?, TelegramError?>
                    chatId = chatId,
                    messageId = messageIdToEdit,
                    text = text,
                    replyMarkup = inlineMarkup,
                    parseMode = parseMode
                ).fold(
                    ifSuccess = { responseMessage: Response<Message> -> // Используем ifSuccess и ifError
                        if (!responseMessage.ok) {
                            logger.warn("Failed to edit message $messageIdToEdit for chat $chatId: ${responseMessage.description}")
                        } else {
                            logger.debug("Message $messageIdToEdit edited for chat $chatId")
                        }
                    },
                    ifError = { error: TelegramError ->
                        logger.error(
                            "Exception editing message $messageIdToEdit for chat $chatId: ${error.getErrorMessage()}",
                            error.exception
                        )
                    }
                )
            } else {
                val sendResultPair = when (replyMarkup) { // sendResultPair это Pair<Response<Message>?, TelegramError?>
                    is KeyboardReplyMarkup ->
                        bot.sendMessage(chatId = chatId, text = text, replyMarkup = replyMarkup, parseMode = parseMode)
                    is InlineKeyboardMarkup ->
                        bot.sendMessage(chatId = chatId, text = text, replyMarkup = replyMarkup, parseMode = parseMode)
                    else ->
                        bot.sendMessage(chatId = chatId, text = text, parseMode = parseMode)
                }

                sendResultPair.fold(
                    ifSuccess = { responseMessage: Response<Message> -> // Используем ifSuccess и ifError
                        if (!responseMessage.ok) {
                            logger.warn("Failed to send message to chat $chatId: ${responseMessage.description}")
                        } else {
                            logger.debug("Message sent to chat $chatId: ${text.take(50)}...")
                        }
                    },
                    ifError = { error: TelegramError ->
                        logger.error(
                            "Exception sending message to chat $chatId: ${error.getErrorMessage()}",
                            error.exception
                        )
                    }
                )
            }
        } catch (e: Exception) {
            logger.error("Critical error in TelegramBotFacade.send for chat $chatId: ${e.message}", e)
        }
    }
    private fun createMainMenuKeyboard(strings: LocalizedStrings, clubs: List<Club>): KeyboardReplyMarkup {
        val clubButtons = clubs.filter { it.isActive }
            .take(BotConstants.MAX_CLUBS_ON_MAIN_MENU_KEYBOARD)
            .map { KeyboardButton(strings.menuBookTableInClub(it.title)) }

        val rows = mutableListOf<List<KeyboardButton>>()
        rows.add(listOf(KeyboardButton(strings.menuVenueInfo), KeyboardButton(strings.menuMyBookings)))
        if (clubButtons.isNotEmpty()) {
            rows.add(clubButtons)
        }
        rows.add(listOf(KeyboardButton(strings.menuAskQuestion), KeyboardButton(strings.menuHelp)))
        rows.add(listOf(KeyboardButton(strings.menuChangeLanguage)))

        return KeyboardReplyMarkup(
            keyboard = rows,
            resizeKeyboard = true,
            oneTimeKeyboard = false
        )
    }

    private fun createMainMenuInlineKeyboard(strings: LocalizedStrings, clubs: List<Club>): InlineKeyboardMarkup {
        val clubButtons = clubs.filter { it.isActive }
            .take(BotConstants.MAX_CLUBS_ON_MAIN_MENU_KEYBOARD)
            .map {
                InlineKeyboardButton.CallbackData(
                    text = strings.menuBookTableInClub(it.title), // text =
                    callbackData = "${BotConstants.CB_PREFIX_BOOK_CLUB}${it.id}" // callbackData =
                ) as InlineKeyboardButton // Приведение типа
            }

        val rows = mutableListOf<List<InlineKeyboardButton>>() // Тип List<InlineKeyboardButton>
        rows.add(
            listOf(
                InlineKeyboardButton.CallbackData(strings.menuVenueInfo, BotConstants.CB_MAIN_MENU_VENUE_INFO),
                InlineKeyboardButton.CallbackData(strings.menuMyBookings, BotConstants.CB_MAIN_MENU_MY_BOOKINGS)
            )
        )
        if (clubButtons.isNotEmpty()) { // Проверка, чтобы избежать ошибки приведения типа для пустого списка
            clubButtons.chunked(2).forEach { chunk -> rows.add(chunk) } // chunk будет List<InlineKeyboardButton>
        }
        rows.add(
            listOf(
                InlineKeyboardButton.CallbackData(strings.menuAskQuestion, BotConstants.CB_MAIN_MENU_ASK_QUESTION),
                InlineKeyboardButton.CallbackData(strings.menuHelp, BotConstants.CB_MAIN_MENU_HELP)
            )
        )
        rows.add(
            listOf(
                InlineKeyboardButton.CallbackData(strings.menuChangeLanguage, BotConstants.CB_MAIN_MENU_CHANGE_LANG)
            )
        )
        return InlineKeyboardMarkup.create(rows) // Ожидает List<List<InlineKeyboardButton>>
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
                text = lang.languageName,
                callbackData = "${BotConstants.CB_PREFIX_SELECT_LANG}${lang.languageCode}"
            ) as InlineKeyboardButton
        }
        val backRow: List<InlineKeyboardButton>? = if (messageId != null) listOf(backButtonToMainMenu(currentStrings)) else null

        val rows: List<List<InlineKeyboardButton>> = buttons.chunked(1).let { langRows ->
            if (backRow != null) langRows + listOf(backRow) else langRows
        }
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
        val buttons: List<InlineKeyboardButton> = activeClubs.map { club ->
            InlineKeyboardButton.CallbackData(club.title, "${BotConstants.CB_PREFIX_BOOK_CLUB}${club.id}")
        }
        val rows = buttons.chunked(2).toMutableList()
        if (messageId != null || step == null) {
            rows.add(listOf(backButtonToMainMenu(strings)))
        } else if (step.first > 1) {
            rows.add(listOf(backButtonToMainMenu(strings)))
        }


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
        val locale = Locale(strings.languageCode)

        val firstDayOfWeek = DayOfWeek.MONDAY
        val firstDayValue = firstOfMonth.dayOfWeek.value
        val leadingEmptyCells = (firstDayValue - firstDayOfWeek.value + 7) % 7

        val rows = mutableListOf<List<InlineKeyboardButton>>()

        rows.add(
            listOf(
                InlineKeyboardButton.CallbackData(
                    strings.calendarMonthYear(firstOfMonth.month, yearMonth.year),
                    "cal_ignore_month_year"
                )
            )
        )

        val weekdaysHeader = DayOfWeek.values().map {
            val displayDay = DayOfWeek.of((it.value + firstDayOfWeek.value - 2) % 7 + 1)
            InlineKeyboardButton.CallbackData(
                displayDay.getDisplayName(TextStyle.SHORT, locale),
                "cal_ignore_weekday"
            )
        }
        rows.add(weekdaysHeader)


        var currentDayPointer = firstOfMonth
        val daysInMonth = yearMonth.lengthOfMonth()
        var dayOfMonth = 1
        var cellsInRow = 0

        val calendarRows = mutableListOf<List<InlineKeyboardButton>>()
        var currentWeekRow = mutableListOf<InlineKeyboardButton>()

        repeat(leadingEmptyCells) {
            currentWeekRow.add(InlineKeyboardButton.CallbackData(" ", "cal_ignore_empty"))
            cellsInRow++
        }

        while (dayOfMonth <= daysInMonth) {
            val currentDate = yearMonth.atDay(dayOfMonth)
            val textDay = strings.calendarDay(currentDate.dayOfMonth)

            val isSelectable = !currentDate.isBefore(today) && !currentDate.isAfter(today.plusMonths(BotConstants.CALENDAR_MONTHS_AHEAD_LIMIT.toLong()))

            val callbackData = if (isSelectable) {
                "${BotConstants.CB_PREFIX_CHOOSE_DATE_CAL}$currentDate"
            } else {
                "cal_ignore_disabled"
            }
            currentWeekRow.add(InlineKeyboardButton.CallbackData(textDay, callbackData))
            cellsInRow++

            if (cellsInRow == 7) {
                calendarRows.add(currentWeekRow.toList())
                currentWeekRow = mutableListOf()
                cellsInRow = 0
            }
            dayOfMonth++
        }

        if (currentWeekRow.isNotEmpty()) {
            while (cellsInRow < 7) {
                currentWeekRow.add(InlineKeyboardButton.CallbackData(" ", "cal_ignore_empty"))
                cellsInRow++
            }
            calendarRows.add(currentWeekRow.toList())
        }
        rows.addAll(calendarRows)


        val navRow = mutableListOf<InlineKeyboardButton>()
        if (yearMonth.isAfter(YearMonth.from(today))) {
            navRow.add(
                InlineKeyboardButton.CallbackData(
                    strings.calendarPrevMonth,
                    "${BotConstants.CB_PREFIX_CAL_MONTH_CHANGE}prev_${yearMonth.minusMonths(1)}"
                )
            )
        } else {
            navRow.add(InlineKeyboardButton.CallbackData(" ", "cal_ignore_empty_nav_prev"))
        }

        if (yearMonth.isBefore(YearMonth.from(today.plusMonths(BotConstants.CALENDAR_MONTHS_AHEAD_LIMIT.toLong() -1 )))) {
            navRow.add(
                InlineKeyboardButton.CallbackData(
                    strings.calendarNextMonth,
                    "${BotConstants.CB_PREFIX_CAL_MONTH_CHANGE}next_${yearMonth.plusMonths(1)}"
                )
            )
        } else {
            navRow.add(InlineKeyboardButton.CallbackData(" ", "cal_ignore_empty_nav_next"))
        }
        rows.add(navRow)

        rows.add(listOf(backButton(strings, BotConstants.CB_PREFIX_BACK_TO + FsmStates.ChooseClub.name))) // FsmStates.ChooseClub.name

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
        club.floorPlanImageUrl?.let { planUrl ->
            if (planUrl.isNotBlank() && planUrl != BotConstants.TABLE_LAYOUT_PLACEHOLDER_URL) {
                bot.sendPhoto(
                    chatId = chatId,
                    photo = TelegramFile.ByUrl(planUrl), // Используем TelegramFile.ByUrl
                    caption = strings.tableLayoutInfo
                ).fold(
                    ifSuccess = { resp: Response<Message> -> if (!resp.ok) logger.warn("Failed to send photo for club ${club.id}: ${resp.description}") else logger.debug("Floor plan sent for club ${club.id}") },
                    ifError = { err: TelegramError -> logger.error("Error sending floor plan for club ${club.id}: ${err.getErrorMessage()}", err.exception) }
                )
            } else {
                logger.info("Floor plan URL is empty or placeholder for club ${club.id}, not sending image.")
            }
        } ?: logger.info("No floor plan URL for club ${club.id}, not sending image.")


        val (text, keyboard) = if (tables.isEmpty()) {
            strings.noAvailableTables to InlineKeyboardMarkup.create(
                listOf(listOf(backButton(strings, BotConstants.CB_PREFIX_BACK_TO + FsmStates.ChooseDate.name))) // FsmStates.ChooseDate.name
            )
        } else {
            val buttons: List<InlineKeyboardButton> = tables.map { table ->
                InlineKeyboardButton.CallbackData(
                    strings.tableButtonText(table.number, table.seats),
                    "${BotConstants.CB_PREFIX_CHOOSE_TABLE}${table.id}"
                )
            }
            val messageHeader = strings.chooseTablePromptForClubAndDate(club.title, selectedDate.format(getDateFormatter(strings)))
            messageHeader to InlineKeyboardMarkup.create(
                buttons.chunked(1) +
                        listOf(listOf(backButton(strings, BotConstants.CB_PREFIX_BACK_TO + FsmStates.ChooseDate.name))) // FsmStates.ChooseDate.name
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
        val text = strings.askPeopleCount
        val fullText = step?.let { "${strings.stepTracker(it.first, it.second)}\n\n$text" } ?: text
        val keyboard = InlineKeyboardMarkup.create(
            listOf(listOf(backButton(strings, BotConstants.CB_PREFIX_BACK_TO + FsmStates.ChooseTable.name))) // FsmStates.ChooseTable.name
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
                listOf(listOf(backButton(strings, BotConstants.CB_PREFIX_BACK_TO + FsmStates.EnterPeople.name))) // FsmStates.EnterPeople.name
            )
        } else {
            val timeFmt = getTimeFormatter(strings)
            val buttons: List<InlineKeyboardButton> = slots.map { (start, end) ->
                val slotText = "${timeFmt.format(start)} - ${timeFmt.format(end)}"
                InlineKeyboardButton.CallbackData(slotText, "${BotConstants.CB_PREFIX_CHOOSE_SLOT}${start.epochSecond}:${end.epochSecond}")
            }
            strings.chooseSlotForTableInClub(table.number, club.title) to
                    InlineKeyboardMarkup.create(
                        buttons.chunked(2),
                        listOf(listOf(backButton(strings, BotConstants.CB_PREFIX_BACK_TO + FsmStates.EnterPeople.name))) // FsmStates.EnterPeople.name
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
            listOf(listOf(backButton(strings, BotConstants.CB_PREFIX_BACK_TO + FsmStates.ChooseSlot.name))) // FsmStates.ChooseSlot.name
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
            listOf(listOf(backButton(strings, BotConstants.CB_PREFIX_BACK_TO + FsmStates.EnterGuestName.name))) // FsmStates.EnterGuestName.name
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

        val slotStartStr = draft.slotStart?.let { timeFmt.format(it.atZone(defaultZoneId)) } ?: strings.notApplicable
        val slotEndStr = draft.slotEnd?.let { timeFmt.format(it.atZone(defaultZoneId)) } ?: strings.notApplicable
        val dateStr = selectedDate.format(dateFmt)

        val details = strings.bookingDetailsFormat(
            clubName, tableNumber, draft.guests ?: -1,
            dateStr,
            "$slotStartStr – $slotEndStr",
            draft.guestName ?: strings.notApplicable,
            draft.guestPhone ?: strings.notApplicable
        )
        val text = "${strings.confirmBookingPrompt}\n\n$details"

        val keyboard = InlineKeyboardMarkup.create(
            listOf(
                listOf(
                    InlineKeyboardButton.CallbackData(strings.buttonConfirm, BotConstants.CB_PREFIX_CONFIRM_BOOKING),
                    InlineKeyboardButton.CallbackData(strings.buttonCancel, BotConstants.CB_PREFIX_CANCEL_ACTION)
                )
            ),
            listOf(listOf(backButton(strings, BotConstants.CB_PREFIX_BACK_TO + FsmStates.EnterGuestPhone.name))) // FsmStates.EnterGuestPhone.name
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
        val activeVenues = venues.filter { it.isActive }
        val buttons: List<InlineKeyboardButton> = activeVenues.map { venue ->
            InlineKeyboardButton.CallbackData(venue.title, "${BotConstants.CB_PREFIX_VENUE_INFO_SHOW}${venue.id}")
        }
        val keyboard = InlineKeyboardMarkup.create(
            buttons.chunked(2) +
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
        val rows = mutableListOf<List<InlineKeyboardButton>>()
        rows.add(
            listOf(
                InlineKeyboardButton.CallbackData(strings.venueInfoButtonPosters, "${BotConstants.CB_PREFIX_VENUE_POSTERS}${venue.id}"),
                InlineKeyboardButton.CallbackData(strings.venueInfoButtonPhotos, "${BotConstants.CB_PREFIX_VENUE_PHOTOS}${venue.id}")
            )
        )
        rows.add(listOf(backButton(strings, BotConstants.CB_PREFIX_BACK_TO + FsmStates.ShowVenueList.name))) // FsmStates.ShowVenueList.name

        val keyboard = InlineKeyboardMarkup.create(rows)
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
            val buttons: List<InlineKeyboardButton> = bookings.map { b ->
                InlineKeyboardButton.CallbackData(
                    strings.bookingItemFormat(
                        b.id, b.clubName, b.tableNumber,
                        dateFmt.format(b.dateStart.atZone(defaultZoneId)),
                        timeFmt.format(b.dateStart.atZone(defaultZoneId)),
                        b.guestsCount,
                        strings.bookingStatusToText(b.status)
                    ),
                    "${BotConstants.CB_PREFIX_MANAGE_BOOKING}${b.id}"
                )
            }
            strings.myBookingsHeader to InlineKeyboardMarkup.create(
                buttons.chunked(1) + // Каждая бронь на отдельной кнопке
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
            dateFmt.format(booking.dateStart.atZone(defaultZoneId)),
            "${timeFmt.format(booking.dateStart.atZone(defaultZoneId))} – ${timeFmt.format(booking.dateEnd.atZone(defaultZoneId))}",
            booking.guestName ?: strings.notApplicable,
            booking.guestPhone ?: strings.notApplicable
        )
        val text = strings.manageBookingPrompt(booking.id, details)

        val rows = mutableListOf<List<InlineKeyboardButton>>()
        if (booking.status == BookingStatus.NEW || booking.status == BookingStatus.CONFIRMED) {
            rows.add(
                listOf(
                    InlineKeyboardButton.CallbackData(strings.buttonChangeBooking, "${BotConstants.CB_PREFIX_DO_CHANGE_BOOKING}${booking.id}"),
                    InlineKeyboardButton.CallbackData(strings.buttonCancelBooking, "${BotConstants.CB_PREFIX_DO_CANCEL_BOOKING}${booking.id}")
                )
            )
        }

        if ((booking.status == BookingStatus.COMPLETED || booking.status == BookingStatus.AWAITING_FEEDBACK) && booking.feedbackRating == null) {
            if (booking.dateEnd.isBefore(Instant.now()) || booking.status == BookingStatus.AWAITING_FEEDBACK) {
                rows.add(
                    listOf(
                        InlineKeyboardButton.CallbackData(strings.buttonRateBooking, "${BotConstants.CB_PREFIX_RATE_BOOKING}${booking.id}:_")
                    )
                )
            }
        }

        rows.add(listOf(backButton(strings, BotConstants.CB_PREFIX_BACK_TO + FsmStates.ShowMyBookingsList.name))) // FsmStates.ShowMyBookingsList.name
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
            listOf(listOf(backButton(strings, BotConstants.CB_PREFIX_BACK_TO + FsmStates.ManageBookingOptions.name))) // FsmStates.ManageBookingOptions.name
        )
        send(chatId, strings.changeBookingInfo, strings, keyboard, messageIdToEdit = messageId)
    }

    override suspend fun sendAskQuestionPrompt(
        chatId: ChatId,
        strings: LocalizedStrings,
        messageId: Long? // Сделаем messageId nullable, так как он может быть не всегда при вызове этого метода
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
        messageId: Long? // Сделаем messageId nullable
    ) {
        send(chatId, strings.questionReceived, strings, createMainMenuInlineKeyboard(strings, clubs), messageIdToEdit = messageId)
    }

    override suspend fun sendHelpMessage(
        chatId: ChatId,
        strings: LocalizedStrings,
        clubs: List<Club>,
        messageId: Long? // Сделаем messageId nullable
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
        val ratingButtons: List<InlineKeyboardButton> = (1..5).map { rating ->
            InlineKeyboardButton.CallbackData("⭐".repeat(rating), "${BotConstants.CB_PREFIX_RATE_BOOKING}$bookingId:$rating")
        }
        val keyboard = InlineKeyboardMarkup.create(
            listOf(ratingButtons), // ratingButtons уже List<InlineKeyboardButton>
            listOf(listOf(backButton(strings, BotConstants.CB_PREFIX_BACK_TO + FsmStates.ManageBookingOptions.name))) // FsmStates.ManageBookingOptions.name
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

    // Убираем значения по умолчанию из переопределенных методов
    override suspend fun sendErrorMessage(
        chatId: ChatId,
        strings: LocalizedStrings,
        message: String?, // message может быть null
        clubs: List<Club>,
        messageId: Long?
    ) {
        val textToShow = message ?: strings.errorMessageDefault
        val keyboard = if (messageId != null) createMainMenuInlineKeyboard(strings, clubs) else createMainMenuKeyboard(strings, clubs)
        send(chatId, textToShow, strings, keyboard, messageIdToEdit = messageId)
    }

    override suspend fun sendInfoMessage(
        chatId: ChatId,
        message: String,
        strings: LocalizedStrings,
        clubs: List<Club>,
        messageId: Long?
    ) {
        val keyboard = if (messageId != null) createMainMenuInlineKeyboard(strings, clubs) else createMainMenuKeyboard(strings, clubs)
        send(chatId, message, strings, keyboard, messageIdToEdit = messageId)
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
        InlineKeyboardButton.CallbackData(text = strings.buttonBack, callbackData = callbackData)

    private fun backButtonToMainMenu(strings: LocalizedStrings): InlineKeyboardButton =
        InlineKeyboardButton.CallbackData(text = strings.buttonBack, callbackData = BotConstants.CB_PREFIX_BACK_TO + FsmStates.MainMenu.name) // FsmStates.MainMenu.name
}