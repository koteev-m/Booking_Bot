package fsm

import db.Club
import db.TableInfo
import db.User
import db.repositories.*
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate
import com.github.kotlintelegrambot.entities.ChatId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers // Or TestCoroutineScheduler for more control
import bot.facade.LocalizedStrings
import bot.facade.StringProviderFactory

@OptIn(ExperimentalCoroutinesApi::class)
class BookingStateMachineTest {

    private val testTelegramUserId = 12345L
    private val testChatId = ChatId.fromId(testTelegramUserId)
    private lateinit var machine: BookingStateMachine

    // Mocks for dependencies
    private lateinit var clubsRepoMock: ClubsRepo
    private lateinit var tablesRepoMock: TablesRepo
    private lateinit var bookingsRepoMock: BookingsRepo
    private lateinit var usersRepoMock: UsersRepo
    private lateinit var botFacadeMock: BotFacade
    private lateinit var testScope: CoroutineScope
    private lateinit var testStrings: LocalizedStrings

    @BeforeEach
    fun setUp() {
        clubsRepoMock = mockk(relaxed = true)
        tablesRepoMock = mockk(relaxed = true)
        bookingsRepoMock = mockk(relaxed = true)
        usersRepoMock = mockk(relaxed = true)
        botFacadeMock = mockk(relaxed = true)
        testScope = CoroutineScope(Dispatchers.Unconfined) // For immediate execution
        testStrings = StringProviderFactory.get("ru") // Default test strings

        val fsmDeps = FsmDeps(
            clubsRepoMock, tablesRepoMock, bookingsRepoMock, usersRepoMock,
            botFacadeMock, testScope, testStrings
        )
        machine = BookingStateMachine(fsmDeps, testChatId, testTelegramUserId)

        // Mock user lookup for booking confirmation
        val mockUser = User(id=1, telegramId=testTelegramUserId, firstName="Test", lastName="User", username="testuser", phone=null, languageCode="ru", loyaltyPoints=0, createdAt=LocalDate.now().atStartOfDay(), lastActivityAt=LocalDate.now().atStartOfDay())
        coEvery { usersRepoMock.findByTelegramId(testTelegramUserId) } returns mockUser
    }

    @Test
    fun `full booking flow should end in BookingDone`() = runTest {
        // Initial state
        assertEquals(BookingState.Idle, machine.state.value)

        // --- Step 1: Start ---
        val mockClubs = listOf(Club(id = 1, name = "Test Club 1", code="TC1", workingHours="10:00-23:00", timezone="Europe/Moscow", createdAt=LocalDate.now().atStartOfDay(), updatedAt=null, description = null, address = null, phone = null, photoUrl = null, floorPlanImageUrl = null, isActive = true), Club(id = 2, name = "Test Club 2", code="TC2", workingHours="10:00-23:00", timezone="Europe/Moscow", createdAt=LocalDate.now().atStartOfDay(), updatedAt=null, description = null, address = null, phone = null, photoUrl = null, floorPlanImageUrl = null, isActive = true))
        coEvery { clubsRepoMock.getAllActiveClubs() } returns mockClubs
        machine.onEvent(BookingEvent.Start)
        assertTrue(machine.state.value is BookingState.ShowingClubs)
        coVerify { botFacadeMock.sendChooseClubKeyboard(testChatId, mockClubs, testStrings, null, null) }

        // --- Step 2: Club Selected ---
        val selectedClubId = 1
        val selectedClub = mockClubs.first { it.id == selectedClubId }
        val availableDates = listOf(LocalDate.now().plusDays(1), LocalDate.now().plusDays(2))
        coEvery { clubsRepoMock.findById(selectedClubId) } returns selectedClub
        coEvery { tablesRepoMock.getAvailableDatesForClub(selectedClubId, any(), any()) } returns availableDates
        machine.onEvent(BookingEvent.ClubSelected(selectedClubId))
        assertTrue(machine.state.value is BookingState.ShowingDates)
        assertEquals(selectedClubId, (machine.state.value as BookingState.ShowingDates).clubId)
        coVerify { botFacadeMock.sendCalendar(testChatId, selectedClubId, selectedClub.name, availableDates, testStrings, null, null) }

        // --- Step 3: Date Selected ---
        val selectedDate = availableDates.first()
        val mockTables = listOf(TableInfo(id = 10, clubId = selectedClubId, number = 1, seats = 4, label = "Table 1", isActive=true, description=null, posX=null,posY=null,photoUrl=null))
        coEvery { tablesRepoMock.getAvailableTables(selectedClubId, selectedDate) } returns mockTables
        machine.onEvent(BookingEvent.DateSelected(selectedDate))
        assertTrue(machine.state.value is BookingState.ShowingTables)
        assertEquals(selectedDate, (machine.state.value as BookingState.ShowingTables).date)
        coVerify { botFacadeMock.sendChooseTableKeyboard(testChatId, selectedClubId, selectedClub.name, selectedDate, mockTables, testStrings, null, null) }

        // --- Step 4: Table Selected ---
        val selectedTableInfo = mockTables.first()
        val mockSlots = listOf("18:00–20:00", "20:00–22:00")
        coEvery { tablesRepoMock.getAvailableSlotsForTable(selectedTableInfo.id, selectedDate, selectedClub) } returns mockSlots // Pass the club object
        machine.onEvent(BookingEvent.TableSelected(selectedTableInfo))
        assertTrue(machine.state.value is BookingState.ShowingSlots)
        assertEquals(selectedTableInfo, (machine.state.value as BookingState.ShowingSlots).table)
        coVerify { botFacadeMock.sendChooseSlotKeyboard(testChatId, selectedClubId, selectedClub.name, selectedDate, selectedTableInfo, mockSlots, testStrings, null, null) }

        // --- Step 5: Slot Selected ---
        val selectedSlot = mockSlots.first()
        machine.onEvent(BookingEvent.SlotSelected(selectedSlot))
        assertTrue(machine.state.value is BookingState.EnteringGuestCount)
        assertEquals(selectedSlot, (machine.state.value as BookingState.EnteringGuestCount).slot)
        coVerify { botFacadeMock.askGuestCount(testChatId, selectedClub.name, selectedTableInfo.label, selectedDate, selectedSlot, testStrings, null, null) }

        // --- Step 6: Guest Count Entered ---
        val guestCount = 2
        machine.onEvent(BookingEvent.GuestCountEntered(guestCount))
        assertTrue(machine.state.value is BookingState.EnteringGuestName)
        assertEquals(guestCount, (machine.state.value as BookingState.EnteringGuestName).draft.peopleCount)
        coVerify { botFacadeMock.askGuestName(testChatId, testStrings, null, null) }

        // --- Step 7: Guest Name Entered ---
        val guestName = "Test Guest"
        machine.onEvent(BookingEvent.GuestNameEntered(guestName))
        assertTrue(machine.state.value is BookingState.EnteringGuestPhone)
        assertEquals(guestName, (machine.state.value as BookingState.EnteringGuestPhone).draft.guestName)
        coVerify { botFacadeMock.askGuestPhone(testChatId, testStrings, null, null) }

        // --- Step 8: Guest Phone Entered ---
        val guestPhone = "+1234567890"
        machine.onEvent(BookingEvent.GuestPhoneEntered(guestPhone))
        assertTrue(machine.state.value is BookingState.ConfirmingBooking)
        assertEquals(guestPhone, (machine.state.value as BookingState.ConfirmingBooking).draft.guestPhone)
        val expectedDraft = (machine.state.value as BookingState.ConfirmingBooking).draft.toFinal()
        coVerify { botFacadeMock.showConfirmBooking(testChatId, expectedDraft, testStrings, null, null) }


        // --- Step 9: Confirm Booking ---
        val bookingId = 1001
        val loyaltyPoints = 20
        coEvery { bookingsRepoMock.saveBooking(any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns Pair(bookingId, loyaltyPoints)
        machine.onEvent(BookingEvent.ConfirmBooking)
        assertTrue(machine.state.value is BookingState.BookingDone)
        assertEquals(bookingId, (machine.state.value as BookingState.BookingDone).bookingId)
        coVerify { botFacadeMock.sendBookingSuccessMessage(testChatId, bookingId, loyaltyPoints, testStrings, emptyList(), null) }
    }

    @Test
    fun `cancel at any step returns to Cancelled state`() = runTest {
        // Start and go to ChoosingClub
        coEvery { clubsRepoMock.getAllActiveClubs() } returns emptyList() // Mock repo call
        machine.onEvent(BookingEvent.Start)
        assertTrue(machine.state.value is BookingState.ShowingClubs) // Or ChoosingClub if that's distinct

        // Send Cancel event
        machine.onEvent(BookingEvent.Cancel)
        assertEquals(BookingState.Cancelled, machine.state.value)
        coVerify { botFacadeMock.sendActionCancelledMessage(testChatId, testStrings, emptyList(), null) }
    }
}