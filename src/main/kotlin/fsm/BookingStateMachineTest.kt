package fsm

import db.Club
import db.TableInfo
import db.User
import db.repositories.*
import io.mockk.* // Import all of MockK
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals // JUnit 5 specific
import org.junit.jupiter.api.Assertions.assertTrue  // JUnit 5 specific
import org.junit.jupiter.api.BeforeEach // JUnit 5 specific
import org.junit.jupiter.api.Test      // JUnit 5 specific
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import com.github.kotlintelegrambot.entities.ChatId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import bot.facade.LocalizedStrings
import bot.facade.StringProviderFactory
import bot.facade.TelegramBotFacade // Import the specific facade if used

@OptIn(ExperimentalCoroutinesApi::class)
class BookingStateMachineTest {

    private val testTelegramUserId = 12345L
    private val testChatId = ChatId.fromId(testTelegramUserId)
    private lateinit var machine: BookingStateMachine

    private lateinit var clubsRepoMock: ClubsRepo
    private lateinit var tablesRepoMock: TablesRepo
    private lateinit var bookingsRepoMock: BookingsRepo
    private lateinit var usersRepoMock: UsersRepo
    private lateinit var botFacadeMock: fsm.BotFacade // Use the fsm.BotFacade interface
    private lateinit var testScope: CoroutineScope
    private lateinit var testStrings: LocalizedStrings

    private val mockClub1 = Club(id = 1, name = "Test Club 1", code="TC1", workingHours="10:00-23:00", timezone="Europe/Moscow", createdAt=LocalDate.now().atStartOfDay(), updatedAt=null, description = null, address = null, phone = null, photoUrl = null, floorPlanImageUrl = null, isActive = true)
    private val mockUser = User(id=1, telegramId=testTelegramUserId, firstName="Test", lastName="User", username="testuser", phone=null, languageCode="ru", loyaltyPoints=0, createdAt=LocalDate.now().atStartOfDay(), lastActivityAt=LocalDate.now().atStartOfDay())


    @BeforeEach
    fun setUp() {
        clubsRepoMock = mockk(relaxed = true)
        tablesRepoMock = mockk(relaxed = true)
        bookingsRepoMock = mockk(relaxed = true)
        usersRepoMock = mockk(relaxed = true)
        botFacadeMock = mockk<fsm.BotFacade>(relaxed = true) // Mock the interface
        testScope = CoroutineScope(Dispatchers.Unconfined)
        testStrings = StringProviderFactory.get("ru")

        val fsmDeps = FsmDeps(
            clubsRepoMock, tablesRepoMock, bookingsRepoMock, usersRepoMock,
            botFacadeMock, testScope, testStrings
        )
        machine = BookingStateMachine(fsmDeps, testChatId, testTelegramUserId)
        coEvery { usersRepoMock.findByTelegramId(testTelegramUserId) } returns mockUser
    }

    @Test
    fun `full booking flow should end in BookingDone`() = runTest {
        assertEquals(BookingState.Idle, machine.state.value)

        val mockClubs = listOf(mockClub1)
        coEvery { clubsRepoMock.getAllActiveClubs() } returns mockClubs
        machine.onEvent(BookingEvent.Start)
        // runCurrent() // If using TestCoroutineDispatcher to ensure coroutines launched by FSM complete
        assertTrue(machine.state.value is BookingState.ShowingClubs)
        coVerify { botFacadeMock.sendChooseClubKeyboard(testChatId, mockClubs, testStrings, null, null) }

        val selectedClubId = 1
        val selectedClub = mockClubs.first { it.id == selectedClubId }
        val availableDates = listOf(LocalDate.now().plusDays(1))
        coEvery { clubsRepoMock.findById(selectedClubId) } returns selectedClub
        coEvery { tablesRepoMock.getAvailableDatesForClub(selectedClubId, any(), any()) } returns availableDates
        machine.onEvent(BookingEvent.ClubSelected(selectedClubId))
        assertTrue(machine.state.value is BookingState.ShowingDates)
        coVerify { botFacadeMock.sendCalendar(testChatId, selectedClubId, selectedClub.name, availableDates, testStrings, null, null) }

        val selectedDate = availableDates.first()
        val mockTable = TableInfo(id = 10, clubId = selectedClubId, number = 1, seats = 4, label = "Table 1", isActive = true, description=null, posX=null,posY=null,photoUrl=null)
        val mockTables = listOf(mockTable)
        coEvery { tablesRepoMock.getAvailableTables(selectedClubId, selectedDate) } returns mockTables
        machine.onEvent(BookingEvent.DateSelected(selectedDate))
        assertTrue(machine.state.value is BookingState.ShowingTables)
        coVerify { botFacadeMock.sendChooseTableKeyboard(testChatId, selectedClubId, selectedClub.name, selectedDate, mockTables, testStrings, null, null) }

        val selectedTableInfo = mockTables.first()
        val mockSlots = listOf("18:00–20:00")
        coEvery { tablesRepoMock.getAvailableSlotsForTable(selectedTableInfo.id, selectedDate, selectedClub) } returns mockSlots
        machine.onEvent(BookingEvent.TableSelected(selectedTableInfo))
        assertTrue(machine.state.value is BookingState.ShowingSlots)
        coVerify { botFacadeMock.sendChooseSlotKeyboard(testChatId, selectedClubId, selectedClub.name, selectedDate, selectedTableInfo, mockSlots, testStrings, null, null) }

        val selectedSlot = mockSlots.first()
        machine.onEvent(BookingEvent.SlotSelected(selectedSlot))
        assertTrue(machine.state.value is BookingState.EnteringGuestCount)
        coVerify { botFacadeMock.askGuestCount(testChatId, selectedClub.name, selectedTableInfo.label, selectedDate, selectedSlot, testStrings, null, null) }

        val guestCount = 2
        machine.onEvent(BookingEvent.GuestCountEntered(guestCount))
        assertTrue(machine.state.value is BookingState.EnteringGuestName)
        coVerify { botFacadeMock.askGuestName(testChatId, testStrings, null, null) }

        val guestName = "Test Guest"
        machine.onEvent(BookingEvent.GuestNameEntered(guestName))
        assertTrue(machine.state.value is BookingState.EnteringGuestPhone)
        coVerify { botFacadeMock.askGuestPhone(testChatId, testStrings, null, null) }

        val guestPhone = "+1234567890"
        machine.onEvent(BookingEvent.GuestPhoneEntered(guestPhone))
        assertTrue(machine.state.value is BookingState.ConfirmingBooking)
        val expectedDraft = (machine.state.value as BookingState.ConfirmingBooking).draft.toFinal()
        coVerify { botFacadeMock.showConfirmBooking(testChatId, expectedDraft, testStrings, null, null) }

        val bookingId = 1001
        val loyaltyPoints = 20
        val dateStart = LocalDateTime.of(selectedDate, LocalTime.parse(selectedSlot.substringBefore("–")))
        val dateEnd = LocalDateTime.of(selectedDate, LocalTime.parse(selectedSlot.substringAfter("–")))

        coEvery {
            bookingsRepoMock.saveBooking(
                userId = mockUser.id,
                clubId = selectedClubId,
                tableId = selectedTableInfo.id,
                guestsCount = guestCount,
                dateStart = dateStart,
                dateEnd = dateEnd,
                comment = null,
                guestName = guestName,
                guestPhone = guestPhone
            )
        } returns Pair(bookingId, loyaltyPoints)
        machine.onEvent(BookingEvent.ConfirmBooking)
        assertTrue(machine.state.value is BookingState.BookingDone)
        coVerify { botFacadeMock.sendBookingSuccessMessage(testChatId, bookingId, loyaltyPoints, testStrings, emptyList(), null) }
    }

    @Test
    fun `cancel at any step returns to Cancelled state`() = runTest {
        coEvery { clubsRepoMock.getAllActiveClubs() } returns emptyList()
        machine.onEvent(BookingEvent.Start)
        assertTrue(machine.state.value is BookingState.ShowingClubs)

        machine.onEvent(BookingEvent.Cancel)
        assertEquals(BookingState.Cancelled, machine.state.value)
        coVerify { botFacadeMock.sendActionCancelledMessage(testChatId, testStrings, emptyList(), null) }
    }
}