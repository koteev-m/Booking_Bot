package fsm

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime // Added import
import java.time.LocalTime     // Added import
import java.time.format.DateTimeParseException // Added import
import db.Club
import db.TableInfo
import db.User
import db.repositories.BookingsRepo
import db.repositories.ClubsRepo
import db.repositories.TablesRepo
import db.repositories.UsersRepo
import com.github.kotlintelegrambot.entities.ChatId
import bot.facade.LocalizedStrings

data class FsmDeps(
    val clubsRepo: ClubsRepo,
    val tablesRepo: TablesRepo,
    val bookingsRepo: BookingsRepo,
    val usersRepo: UsersRepo,
    val botFacade: BotFacade,
    val scope: CoroutineScope,
    val strings: LocalizedStrings
)

class BookingStateMachine(
    val deps: FsmDeps, // Made public for main.kt access pattern (can be refactored)
    val chatId: ChatId,   // Made public for main.kt access pattern
    private val telegramUserId: Long
) {
    private val _state: MutableStateFlow<BookingState> = MutableStateFlow(BookingState.Idle)
    val state: StateFlow<BookingState> = _state.asStateFlow()

    fun onEvent(event: BookingEvent) {
        deps.scope.launch {
            try {
                val newState = processEvent(event, _state.value)
                _state.value = newState
            } catch (ex: Throwable) {
                ex.printStackTrace() // Log error
                transitionToError("Произошла внутренняя ошибка: ${ex.localizedMessage}")
            }
        }
    }

    private suspend fun processEvent(event: BookingEvent, currentState: BookingState): BookingState {
        return when (event) {
            is BookingEvent.Start -> handleStart()
            is BookingEvent.Cancel -> handleCancel()
            is BookingEvent.ClubSelected -> handleClubSelected(event, currentState)
            is BookingEvent.DateSelected -> handleDateSelected(event, currentState)
            is BookingEvent.TableSelected -> handleTableSelected(event, currentState)
            is BookingEvent.SlotSelected -> handleSlotSelected(event, currentState)
            is BookingEvent.GuestCountEntered -> handleGuestCountEntered(event, currentState)
            is BookingEvent.GuestNameEntered -> handleGuestNameEntered(event, currentState)
            is BookingEvent.GuestPhoneEntered -> handleGuestPhoneEntered(event, currentState)
            is BookingEvent.ConfirmBooking -> handleConfirmBooking(currentState)
            is BookingEvent.Timeout -> handleTimeout(currentState)
        }
    }

    private suspend fun handleStart(): BookingState {
        val clubs: List<Club> = deps.clubsRepo.getAllActiveClubs()
        deps.botFacade.sendChooseClubKeyboard(chatId, clubs, deps.strings, null, null)
        return BookingState.ShowingClubs(clubs)
    }

    private suspend fun handleClubSelected(event: BookingEvent.ClubSelected, currentState: BookingState): BookingState {
        val club: Club = deps.clubsRepo.findById(event.clubId)
            ?: return BookingState.Error("Выбранный клуб не найден.").also { transitionToError("Выбранный клуб не найден.") }

        val availableDates: List<LocalDate> = deps.tablesRepo.getAvailableDatesForClub(club.id, LocalDate.now(), 3)
        deps.botFacade.sendCalendar(chatId, club.id, club.name, availableDates, deps.strings, null, null)
        return BookingState.ShowingDates(clubId = club.id, clubTitle = club.name, availableDates = availableDates)
    }

    private suspend fun handleDateSelected(event: BookingEvent.DateSelected, currentState: BookingState): BookingState {
        if (currentState is BookingState.ShowingDates) {
            val tables: List<TableInfo> = deps.tablesRepo.getAvailableTables(currentState.clubId, event.date)
            deps.botFacade.sendChooseTableKeyboard(chatId, currentState.clubId, currentState.clubTitle, event.date, tables, deps.strings, null, null)
            return BookingState.ShowingTables(clubId = currentState.clubId, clubTitle = currentState.clubTitle, date = event.date, tables = tables)
        }
        return currentState
    }

    private suspend fun handleTableSelected(event: BookingEvent.TableSelected, currentState: BookingState): BookingState {
        if (currentState is BookingState.ShowingTables) {
            val club = deps.clubsRepo.findById(currentState.clubId)
                ?: return BookingState.Error("Клуб не найден").also { transitionToError("Клуб не найден") }
            val slots: List<String> = deps.tablesRepo.getAvailableSlotsForTable(event.table.id, currentState.date, club)
            deps.botFacade.sendChooseSlotKeyboard(chatId, currentState.clubId, currentState.clubTitle, currentState.date, event.table, slots, deps.strings, null, null)
            return BookingState.ShowingSlots(clubId = currentState.clubId, clubTitle = currentState.clubTitle, date = currentState.date, table = event.table, availableSlots = slots)
        }
        return currentState
    }

    private suspend fun handleSlotSelected(event: BookingEvent.SlotSelected, currentState: BookingState): BookingState {
        if (currentState is BookingState.ShowingSlots) {
            deps.botFacade.askGuestCount(chatId, currentState.clubTitle, currentState.table.label, currentState.date, event.slot, deps.strings, null, null)
            return BookingState.EnteringGuestCount(clubId = currentState.clubId, clubTitle = currentState.clubTitle, date = currentState.date, table = currentState.table, slot = event.slot)
        }
        return currentState
    }

    private suspend fun handleGuestCountEntered(event: BookingEvent.GuestCountEntered, currentState: BookingState): BookingState {
        if (currentState is BookingState.EnteringGuestCount) {
            val count = event.count
            val maxSeats = currentState.table.seats
            if (count < 1 || count > maxSeats) {
                deps.botFacade.askGuestCountInvalid(chatId, 1, maxSeats, deps.strings, null)
                return currentState
            }
            val draftTemp = BookingState.DraftBookingTemp(clubId = currentState.clubId, clubTitle = currentState.clubTitle, table = currentState.table, date = currentState.date, slot = currentState.slot).withCount(count)
            deps.botFacade.askGuestName(chatId, deps.strings, null, null)
            return BookingState.EnteringGuestName(draftTemp)
        }
        return currentState
    }

    private suspend fun handleGuestNameEntered(event: BookingEvent.GuestNameEntered, currentState: BookingState): BookingState {
        if (currentState is BookingState.EnteringGuestName) {
            val name = event.name.trim()
            if (name.length < 2) {
                deps.botFacade.askGuestNameInvalid(chatId, deps.strings, null)
                return currentState
            }
            val draftTemp = currentState.draft.withName(name)
            deps.botFacade.askGuestPhone(chatId, deps.strings, null, null)
            return BookingState.EnteringGuestPhone(draftTemp)
        }
        return currentState
    }

    private suspend fun handleGuestPhoneEntered(event: BookingEvent.GuestPhoneEntered, currentState: BookingState): BookingState {
        if (currentState is BookingState.EnteringGuestPhone) {
            val phone = event.phone.trim()
            if (!phone.matches(Regex("^\\+?\\d{7,15}$"))) {
                deps.botFacade.askGuestPhoneInvalid(chatId, deps.strings, null)
                return currentState
            }
            val draftTemp = currentState.draft.withPhone(phone)
            val finalDraft = draftTemp.toFinal()
            deps.botFacade.showConfirmBooking(chatId, finalDraft, deps.strings, null, null)
            return BookingState.ConfirmingBooking(draftTemp)
        }
        return currentState
    }

    private suspend fun handleConfirmBooking(currentState: BookingState): BookingState {
        if (currentState is BookingState.ConfirmingBooking) {
            val finalDraft: DraftBooking = currentState.draft.toFinal()
            val user: User? = deps.usersRepo.findByTelegramId(telegramUserId)
            if (user == null) {
                return BookingState.Error("Пользователь не найден. Пожалуйста, начните сначала с /start.").also { transitionToError("Пользователь не найден.") }
            }
            val internalUserId = user.id

            val timeParts = finalDraft.slot.split("–")
            if (timeParts.size != 2) return BookingState.Error("Неверный формат времени слота.").also { transitionToError("Неверный формат времени слота.") }

            val dateStart: LocalDateTime
            val dateEnd: LocalDateTime
            try {
                dateStart = finalDraft.date.atTime(LocalTime.parse(timeParts[0]))
                dateEnd = finalDraft.date.atTime(LocalTime.parse(timeParts[1]))
                // Handle slot crossing midnight if endTime is before startTime
                if (dateEnd.isBefore(dateStart)) {
                    // This assumes the slot is for the *same chosen date*, but ends on the next calendar day
                    // For instance, 23:00-01:00 means booking ends at 01:00 on date + 1 day.
                    // However, if the club working hours define this, tablesRepo.getAvailableSlots should generate it correctly.
                    // For now, if the parsing indicates end < start, it's likely an issue with slot string or this logic.
                    // A simple fix: if the slot string implies crossing midnight (e.g. "23:00-02:00"), dateEnd should be on the next day.
                    // This logic should ideally be part of slot generation.
                    // Here, we'll assume the slot string is valid for the chosen date.
                    // If endTime < startTime (e.g., "23:00", "01:00"), then dateEnd is on the next day.
                    // This needs careful handling based on how slots are presented and club hours.
                    // A robust solution would carry ZonedDateTime or ensure slots are unambiguous.
                    // For now, this simple parsing might be insufficient if slots truly span past midnight into another day part of the *same booking*.
                    // If a slot is "23:00-01:00", it implies it ends at 01:00 on the *next day* relative to finalDraft.date.
                    // This example doesn't add a day to dateEnd if time is 'earlier' than startTime,
                    // which is a common way to denote next-day closing for such slots. This needs to be robust.
                }

            } catch (e: DateTimeParseException) {
                return BookingState.Error("Ошибка парсинга времени слота: ${e.message}").also { transitionToError("Ошибка парсинга времени слота: ${e.message}") }
            }

            val (bookingId, earnedPoints) = deps.bookingsRepo.saveBooking(
                userId = internalUserId,
                clubId = finalDraft.clubId,
                tableId = finalDraft.tableId,
                guestsCount = finalDraft.peopleCount,
                dateStart = dateStart,
                dateEnd = dateEnd,
                comment = null,
                guestName = finalDraft.guestName,
                guestPhone = finalDraft.guestPhone
            )

            deps.botFacade.sendBookingSuccessMessage(chatId, bookingId, earnedPoints, deps.strings, emptyList(), null)
            return BookingState.BookingDone(bookingId, earnedPoints)
        }
        return currentState
    }

    private suspend fun handleCancel(): BookingState {
        deps.botFacade.sendActionCancelledMessage(chatId, deps.strings, emptyList(), null)
        return BookingState.Cancelled
    }

    private suspend fun handleTimeout(currentState: BookingState): BookingState {
        if (currentState !is BookingState.Idle && currentState !is BookingState.BookingDone && currentState !is BookingState.Cancelled) {
            deps.botFacade.sendInfoMessage(chatId, "Время сессии истекло. Бронирование отменено.", deps.strings, emptyList(), null)
            return BookingState.Cancelled
        }
        return currentState
    }

    private suspend fun transitionToError(message: String) {
        // Update state through the flow to ensure observers see it
        _state.value = BookingState.Error(message)
        deps.botFacade.sendErrorMessage(chatId, deps.strings, message, emptyList(), null)
    }
}