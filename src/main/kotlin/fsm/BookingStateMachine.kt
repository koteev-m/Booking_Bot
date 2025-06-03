package fsm

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

// Import consolidated definitions
import db.Club
import db.TableInfo
import db.User // For fetching user ID
import db.repositories.BookingsRepo
import db.repositories.ClubsRepo
import db.repositories.TablesRepo
import db.repositories.UsersRepo
import com.github.kotlintelegrambot.entities.ChatId
import bot.facade.LocalizedStrings
import bot.facade.StringProviderFactory // To get default strings

// --- ДЕПСЫ (ЗАВИСИМОСТИ), ИНЪЕКЦИЯ В STATE MACHINE ---
data class FsmDeps(
    val clubsRepo: ClubsRepo,
    val tablesRepo: TablesRepo,
    val bookingsRepo: BookingsRepo,
    val usersRepo: UsersRepo,
    val botFacade: BotFacade,
    val scope: CoroutineScope,
    val strings: LocalizedStrings // Default strings, or user-specific if available
)

class BookingStateMachine(
    private val deps: FsmDeps,
    private val chatId: ChatId, // Changed from userId: Long to ChatId
    private val telegramUserId: Long // Keep the numeric Telegram ID for DB user lookups
) {
    private val _state: MutableStateFlow<BookingState> = MutableStateFlow(BookingState.Idle)
    val state: StateFlow<BookingState> = _state.asStateFlow()

    fun onEvent(event: BookingEvent) {
        deps.scope.launch { // Wrap event handling in a coroutine scope for suspend function calls
            try {
                val newState = processEvent(event, _state.value)
                _state.value = newState
            } catch (ex: Throwable) {
                // Log the exception ex
                transitionToError("Произошла внутренняя ошибка: ${ex.message}")
            }
        }
    }

    // Helper to make when exhaustive and handle state transitions
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
            is BookingEvent.Timeout -> handleTimeout(currentState) // Example for exhaustiveness
        }
    }

    private suspend fun handleStart(): BookingState {
        val clubs: List<Club> = deps.clubsRepo.getAllActiveClubs()
        deps.botFacade.sendChooseClubKeyboard(chatId, clubs, deps.strings, null, null)
        return BookingState.ShowingClubs(clubs)
    }

    private suspend fun handleClubSelected(event: BookingEvent.ClubSelected, currentState: BookingState): BookingState {
        val club: Club = deps.clubsRepo.findById(event.clubId)
            ?: return BookingState.Error("Выбранный клуб не найден.") // Or handle error appropriately

        // Example: get available dates for the current month and next 2 months
        val availableDates: List<LocalDate> = deps.tablesRepo.getAvailableDatesForClub(club.id, LocalDate.now(), 3)

        deps.botFacade.sendCalendar(chatId, club.id, club.name, availableDates, deps.strings, null, null) // club.name or club.title
        return BookingState.ShowingDates(
            clubId = club.id,
            clubTitle = club.name, // club.name or club.title
            availableDates = availableDates
        )
    }

    private suspend fun handleDateSelected(event: BookingEvent.DateSelected, currentState: BookingState): BookingState {
        if (currentState is BookingState.ShowingDates) {
            val tables: List<TableInfo> = deps.tablesRepo.getAvailableTables(currentState.clubId, event.date)
            deps.botFacade.sendChooseTableKeyboard(chatId, currentState.clubId, currentState.clubTitle, event.date, tables, deps.strings, null, null)
            return BookingState.ShowingTables(
                clubId = currentState.clubId,
                clubTitle = currentState.clubTitle,
                date = event.date,
                tables = tables
            )
        }
        return currentState // Ignore if not in the correct state
    }

    private suspend fun handleTableSelected(event: BookingEvent.TableSelected, currentState: BookingState): BookingState {
        if (currentState is BookingState.ShowingTables) {
            val club = deps.clubsRepo.findById(currentState.clubId) ?: return BookingState.Error("Клуб не найден")
            val slots: List<String> = deps.tablesRepo.getAvailableSlotsForTable(event.table.id, currentState.date, club)
            deps.botFacade.sendChooseSlotKeyboard(chatId, currentState.clubId, currentState.clubTitle, currentState.date, event.table, slots, deps.strings, null, null)
            return BookingState.ShowingSlots(
                clubId = currentState.clubId,
                clubTitle = currentState.clubTitle,
                date = currentState.date,
                table = event.table,
                availableSlots = slots
            )
        }
        return currentState
    }

    private suspend fun handleSlotSelected(event: BookingEvent.SlotSelected, currentState: BookingState): BookingState {
        if (currentState is BookingState.ShowingSlots) {
            deps.botFacade.askGuestCount(chatId, currentState.clubTitle, currentState.table.label, currentState.date, event.slot, deps.strings, null, null)
            return BookingState.EnteringGuestCount(
                clubId = currentState.clubId,
                clubTitle = currentState.clubTitle,
                date = currentState.date,
                table = currentState.table,
                slot = event.slot
            )
        }
        return currentState
    }

    private suspend fun handleGuestCountEntered(event: BookingEvent.GuestCountEntered, currentState: BookingState): BookingState {
        if (currentState is BookingState.EnteringGuestCount) {
            val count = event.count
            // Basic validation, can be enhanced
            val maxSeats = currentState.table.seats
            if (count < 1 || count > maxSeats) {
                deps.botFacade.askGuestCountInvalid(chatId, 1, maxSeats, deps.strings, null)
                return currentState // Remain in the same state
            }

            val draftTemp = BookingState.DraftBookingTemp(
                clubId = currentState.clubId,
                clubTitle = currentState.clubTitle,
                table = currentState.table,
                date = currentState.date,
                slot = currentState.slot
            ).withCount(count)

            deps.botFacade.askGuestName(chatId, deps.strings, null, null)
            return BookingState.EnteringGuestName(draftTemp)
        }
        return currentState
    }

    private suspend fun handleGuestNameEntered(event: BookingEvent.GuestNameEntered, currentState: BookingState): BookingState {
        if (currentState is BookingState.EnteringGuestName) {
            val name = event.name.trim()
            if (name.length < 2) { // Basic validation
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
            // Basic validation (e.g., using regex from bot.utils.Validation)
            if (!phone.matches(Regex("^\\+?\\d{7,15}$"))) { // Example regex
                deps.botFacade.askGuestPhoneInvalid(chatId, deps.strings, null)
                return currentState
            }
            val draftTemp = currentState.draft.withPhone(phone)
            val finalDraft = draftTemp.toFinal() // Convert to DraftBooking (from fsm package)
            deps.botFacade.showConfirmBooking(chatId, finalDraft, deps.strings, null, null)
            return BookingState.ConfirmingBooking(draftTemp)
        }
        return currentState
    }

    private suspend fun handleConfirmBooking(currentState: BookingState): BookingState {
        if (currentState is BookingState.ConfirmingBooking) {
            val finalDraft: DraftBooking = currentState.draft.toFinal()

            // Fetch internal user ID
            val user: User? = deps.usersRepo.findByTelegramId(telegramUserId)
            if (user == null) {
                // This case should ideally not happen if user is created at /start
                // Or create user here:
                // val tempUser = deps.usersRepo.getOrCreate(telegramUserId, finalDraft.guestName, finalDraft.guestPhone, deps.strings.languageCode)
                // For now, error out if user not found.
                return BookingState.Error("Пользователь не найден. Пожалуйста, начните сначала с /start.")
            }
            val internalUserId = user.id

            // Convert LocalDate and slot string to LocalDateTime for dateStart and dateEnd
            // This requires parsing the slot string like "18:00-20:00"
            val timeParts = finalDraft.slot.split("–")
            if (timeParts.size != 2) return BookingState.Error("Неверный формат времени слота.")

            val startTimeStr = timeParts[0]
            val endTimeStr = timeParts[1]

            val dateStart: LocalDateTime
            val dateEnd: LocalDateTime
            try {
                dateStart = finalDraft.date.atTime(java.time.LocalTime.parse(startTimeStr))
                dateEnd = finalDraft.date.atTime(java.time.LocalTime.parse(endTimeStr))
            } catch (e: java.time.format.DateTimeParseException) {
                return BookingState.Error("Ошибка парсинга времени слота.")
            }


            val (bookingId, earnedPoints) = deps.bookingsRepo.saveBooking(
                userId = internalUserId,
                clubId = finalDraft.clubId,
                tableId = finalDraft.tableId,
                guestsCount = finalDraft.peopleCount,
                dateStart = dateStart,
                dateEnd = dateEnd,
                comment = null, // Add if collected
                guestName = finalDraft.guestName,
                guestPhone = finalDraft.guestPhone
            )

            deps.botFacade.sendBookingSuccessMessage(chatId, bookingId, earnedPoints, deps.strings, emptyList(), null) // emptyList() for clubs for now
            return BookingState.BookingDone(bookingId, earnedPoints)
        }
        return currentState
    }

    private suspend fun handleCancel(): BookingState {
        deps.botFacade.sendActionCancelledMessage(chatId, deps.strings, emptyList(), null) // emptyList() for clubs for now
        return BookingState.Cancelled // Or BookingState.Idle if you want to reset to main menu
    }

    private suspend fun handleTimeout(currentState: BookingState): BookingState {
        // Handle timeout, perhaps by cancelling or sending a message
        if (currentState !is BookingState.Idle && currentState !is BookingState.BookingDone && currentState !is BookingState.Cancelled) {
            deps.botFacade.sendInfoMessage(chatId, "Время сессии истекло. Бронирование отменено.", deps.strings, emptyList(), null)
            return BookingState.Cancelled
        }
        return currentState
    }

    private suspend fun transitionToError(message: String) {
        _state.value = BookingState.Error(message) // Update state directly for simplicity here
        deps.botFacade.sendErrorMessage(chatId, deps.strings, message, emptyList(), null)
    }
}