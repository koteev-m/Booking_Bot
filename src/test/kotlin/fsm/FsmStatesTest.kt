package fsm

import bot.facade.*
import com.github.kotlintelegrambot.entities.ChatId
import db.Club
import db.TableInfo
import db.User
import db.repositories.*
import io.mockk.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
import bot.facade.LocalizedStrings
import bot.facade.StringProviderFactory

@OptIn(ExperimentalCoroutinesApi::class)
class FsmStatesTest {

    private val testTelegramUserId = 123456789L
    private val testChatId = ChatId.fromId(testTelegramUserId)
    private lateinit var machine: BookingStateMachine

    // Mocks for dependencies
    private lateinit var clubsRepoMock: ClubsRepo
    private lateinit var tablesRepoMock: TablesRepo
    private lateinit var bookingsRepoMock: BookingsRepo
    private lateinit var usersRepoMock: UsersRepo
    private lateinit var botFacadeMock: TelegramBotFacade // Using the specific interface for clarity if needed, or fsm.BotFacade
    private lateinit var testScope: CoroutineScope
    private lateinit var testStrings: LocalizedStrings

    // Test data
    private val mockClub1 = Club(id = 1, name = "Neon Club", code = "NEON", address = "Москва, Тверская 15", timezone = "Europe/Moscow", isActive = true, createdAt = LocalDateTime.now(), updatedAt = null, description = null, phone = null, workingHours = "18:00-03:00", photoUrl = null, floorPlanImageUrl = null)
    private val mockUser = User(id = 1001, telegramId = testTelegramUserId, firstName = "Иван", lastName = "Петров", username = "ivan_petrov", phone = "+79161234567", languageCode = "ru", loyaltyPoints = 0, createdAt = LocalDateTime.now(), lastActivityAt = LocalDateTime.now())
    private val mockTable1 = TableInfo(id = 1, clubId = mockClub1.id, number = 1, seats = 6, label = "VIP 1", isActive = true, description = null, posX = null, posY = null, photoUrl = null)
    private val selectedDate = LocalDate.now().plusDays(5)
    private val selectedSlot = "22:00–00:00" // Assuming 2-hour slots from original test
    private val guestCount = 4

    @BeforeEach
    fun setUp() {
        clubsRepoMock = mockk(relaxed = true)
        tablesRepoMock = mockk(relaxed = true)
        bookingsRepoMock = mockk(relaxed = true)
        usersRepoMock = mockk(relaxed = true)
        botFacadeMock = mockk<TelegramBotFacade>(relaxed = true) // Cast to specific type if BotFacade is generic
        testScope = CoroutineScope(Dispatchers.Unconfined + SupervisorJob())
        testStrings = StringProviderFactory.get("ru")

        val fsmDeps = FsmDeps(
            clubsRepo = clubsRepoMock,
            tablesRepo = tablesRepoMock,
            bookingsRepo = bookingsRepoMock,
            usersRepo = usersRepoMock,
            botFacade = botFacadeMock,
            scope = testScope,
            strings = testStrings
        )
        machine = BookingStateMachine(fsmDeps, testChatId, testTelegramUserId)

        // Common mock setup
        coEvery { usersRepoMock.findByTelegramId(testTelegramUserId) } returns mockUser
    }

    @Test
    fun `booking flow goes through happy path and verifies facade calls`() = runTest {
        // 1. Стартуем сценарий (/start)
        coEvery { clubsRepoMock.getAllActiveClubs() } returns listOf(mockClub1)
        machine.onEvent(BookingEvent.Start)
        assertEquals(BookingState.ShowingClubs(listOf(mockClub1)), machine.state.value)
        coVerify { botFacadeMock.sendChooseClubKeyboard(testChatId, listOf(mockClub1), testStrings, null, null) }

        // 2. Выбираем клуб (id = 1)
        coEvery { clubsRepoMock.findById(mockClub1.id) } returns mockClub1
        val availableDates = listOf(selectedDate)
        coEvery { tablesRepoMock.getAvailableDatesForClub(mockClub1.id, any(), any()) } returns availableDates
        machine.onEvent(BookingEvent.ClubSelected(mockClub1.id))
        assertEquals(BookingState.ShowingDates(mockClub1.id, mockClub1.name, availableDates), machine.state.value)
        coVerify { botFacadeMock.sendCalendar(testChatId, mockClub1.id, mockClub1.name, availableDates, testStrings, null, null) }

        // 3. Выбираем дату
        val availableTables = listOf(mockTable1)
        coEvery { tablesRepoMock.getAvailableTables(mockClub1.id, selectedDate) } returns availableTables
        machine.onEvent(BookingEvent.DateSelected(selectedDate))
        assertEquals(BookingState.ShowingTables(mockClub1.id, mockClub1.name, selectedDate, availableTables), machine.state.value)
        coVerify { botFacadeMock.sendChooseTableKeyboard(testChatId, mockClub1.id, mockClub1.name, selectedDate, availableTables, testStrings, null, null) }

        // 4. Выбираем стол (TableInfo)
        val availableSlots = listOf(selectedSlot, "00:00–02:00")
        coEvery { tablesRepoMock.getAvailableSlotsForTable(mockTable1.id, selectedDate, mockClub1) } returns availableSlots
        machine.onEvent(BookingEvent.TableSelected(mockTable1))
        assertEquals(BookingState.ShowingSlots(mockClub1.id, mockClub1.name, selectedDate, mockTable1, availableSlots), machine.state.value)
        coVerify { botFacadeMock.sendChooseSlotKeyboard(testChatId, mockClub1.id, mockClub1.name, selectedDate, mockTable1, availableSlots, testStrings, null, null) }

        // 5. Выбираем слот
        machine.onEvent(BookingEvent.SlotSelected(selectedSlot))
        val expectedEnteringGuestCountState = BookingState.EnteringGuestCount(mockClub1.id, mockClub1.name, selectedDate, mockTable1, selectedSlot)
        assertEquals(expectedEnteringGuestCountState, machine.state.value)
        coVerify { botFacadeMock.askGuestCount(testChatId, mockClub1.name, mockTable1.label, selectedDate, selectedSlot, testStrings, null, null) }

        // 6. Указываем кол-во гостей = 4
        machine.onEvent(BookingEvent.GuestCountEntered(guestCount))
        val expectedDraftTempAfterCount = BookingState.DraftBookingTemp(mockClub1.id, mockClub1.name, mockTable1, selectedDate, selectedSlot, guestCount)
        assertEquals(BookingState.EnteringGuestName(expectedDraftTempAfterCount), machine.state.value)
        coVerify { botFacadeMock.askGuestName(testChatId, testStrings, null, null) }

        // 7. Указываем имя
        val guestName = mockUser.firstName ?: "Иван"
        machine.onEvent(BookingEvent.GuestNameEntered(guestName))
        val expectedDraftTempAfterName = expectedDraftTempAfterCount.withName(guestName)
        assertEquals(BookingState.EnteringGuestPhone(expectedDraftTempAfterName), machine.state.value)
        coVerify { botFacadeMock.askGuestPhone(testChatId, testStrings, null, null) }

        // 8. Указываем телефон
        val guestPhone = mockUser.phone ?: "+79161234567"
        machine.onEvent(BookingEvent.GuestPhoneEntered(guestPhone))
        val expectedDraftTempAfterPhone = expectedDraftTempAfterName.withPhone(guestPhone)
        assertEquals(BookingState.ConfirmingBooking(expectedDraftTempAfterPhone), machine.state.value)
        coVerify { botFacadeMock.showConfirmBooking(testChatId, expectedDraftTempAfterPhone.toFinal(), testStrings, null, null) }

        // 9. Подтверждаем
        val bookingId = 5001
        val loyaltyPointsEarned = guestCount * 10
        // Mock the saveBooking call
        coEvery {
            bookingsRepoMock.saveBooking(
                userId = mockUser.id,
                clubId = mockClub1.id,
                tableId = mockTable1.id,
                guestsCount = guestCount,
                dateStart = LocalDateTime.of(selectedDate, LocalTime.parse(selectedSlot.substringBefore("–"))),
                dateEnd = LocalDateTime.of(selectedDate, LocalTime.parse(selectedSlot.substringAfter("–"))), // This needs robust parsing if slot crosses midnight
                comment = null,
                guestName = guestName,
                guestPhone = guestPhone
            )
        } returns Pair(bookingId, loyaltyPointsEarned)

        machine.onEvent(BookingEvent.ConfirmBooking)

        // 10. Проверяем, что FSM упала в финальное состояние BookingDone
        val finalState = machine.state.value
        assertTrue(finalState is BookingState.BookingDone, "State should be BookingDone. Actual: $finalState")
        assertEquals(bookingId, (finalState as BookingState.BookingDone).bookingId)
        assertEquals(loyaltyPointsEarned, finalState.loyaltyPoints)

        // 11. Проверяем, что бот попытался отправить финальное сообщение
        coVerify {
            botFacadeMock.sendBookingSuccessMessage(
                chatId = testChatId,
                bookingId = bookingId,
                loyaltyPoints = loyaltyPointsEarned,
                strings = testStrings,
                clubs = emptyList(), // Or provide a list of clubs if your FSM/Facade needs it
                messageId = null
            )
        }
    }
}