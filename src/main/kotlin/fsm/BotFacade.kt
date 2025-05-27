package fsm

import LocalizedStrings
import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.InlineKeyboardMarkup // Added missing import
import db.BookingWithClubName
import db.Club
import db.TableInfo
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth

interface BotFacade {
    // Language
    suspend fun sendLanguageSelection(chatId: ChatId, currentStrings: LocalizedStrings, messageId: Long?)

    // Main Menu
    suspend fun sendWelcomeMessage(chatId: ChatId, userName: String?, strings: LocalizedStrings, clubs: List<Club>)
    suspend fun sendMainMenu(chatId: ChatId, strings: LocalizedStrings, clubs: List<Club>, messageId: Long? = null, text: String? = null)
    suspend fun sendStepTrackerMessage(chatId: ChatId, strings: LocalizedStrings, currentStep: Int, totalSteps: Int, messageText: String, keyboard: InlineKeyboardMarkup?, messageId: Long?)


    // Booking Flow
    suspend fun sendChooseClubKeyboard(chatId: ChatId, clubs: List<Club>, strings: LocalizedStrings, messageId: Long?, step: Pair<Int,Int>?)
    suspend fun sendCalendar(chatId: ChatId, yearMonth: YearMonth, strings: LocalizedStrings, messageId: Long?, step: Pair<Int,Int>?)
    suspend fun sendChooseTableKeyboard(chatId: ChatId, tables: List<TableInfo>, club: Club, selectedDate: LocalDate, strings: LocalizedStrings, messageId: Long?, step: Pair<Int,Int>?)
    suspend fun askPeopleCount(chatId: ChatId, strings: LocalizedStrings, messageId: Long?, step: Pair<Int,Int>?)
    suspend fun sendChooseSlotKeyboard(
        chatId: ChatId, club: Club, table: TableInfo, slots: List<Pair<Instant, Instant>>,
        strings: LocalizedStrings, messageId: Long?, step: Pair<Int,Int>?
    )
    suspend fun askGuestName(chatId: ChatId, strings: LocalizedStrings, messageId: Long?, step: Pair<Int,Int>?)
    suspend fun askGuestPhone(chatId: ChatId, strings: LocalizedStrings, messageId: Long?, step: Pair<Int,Int>?)
    suspend fun sendConfirmMessage(chatId: ChatId, draft: DraftBooking, clubName: String, tableNumber: Int, selectedDate: LocalDate, strings: LocalizedStrings, messageId: Long?, step: Pair<Int,Int>?)
    suspend fun sendBookingSuccessMessage(chatId: ChatId, bookingId: Int, loyaltyPoints: Int, strings: LocalizedStrings, clubs: List<Club>, messageId: Long?)
    suspend fun sendBookingCancelledMessage(chatId: ChatId, strings: LocalizedStrings, clubs: List<Club>, messageId: Long?)
    suspend fun sendActionCancelledMessage(chatId: ChatId, strings: LocalizedStrings, clubs: List<Club>, messageId: Long?)


    // Venue Info
    suspend fun sendVenueSelection(chatId: ChatId, venues: List<Club>, strings: LocalizedStrings, messageId: Long?)
    suspend fun sendVenueDetails(chatId: ChatId, venue: Club, strings: LocalizedStrings, messageId: Long?)

    // My Bookings
    suspend fun sendMyBookingsList(chatId: ChatId, bookings: List<BookingWithClubName>, strings: LocalizedStrings, clubs: List<Club>, messageId: Long?)
    suspend fun sendManageBookingOptions(chatId: ChatId, booking: BookingWithClubName, strings: LocalizedStrings, messageId: Long)
    suspend fun sendBookingCancellationConfirmed(chatId: ChatId, bookingId: Int, clubName: String, strings: LocalizedStrings, clubs: List<Club>, messageId: Long)
    suspend fun sendChangeBookingInfo(chatId: ChatId, strings: LocalizedStrings, messageId: Long)

    // Ask Question
    suspend fun sendAskQuestionPrompt(chatId: ChatId, strings: LocalizedStrings, messageId: Long?)
    suspend fun sendQuestionReceived(chatId: ChatId, strings: LocalizedStrings, clubs: List<Club>, messageId: Long?)

    // Help
    suspend fun sendHelpMessage(chatId: ChatId, strings: LocalizedStrings, clubs: List<Club>, messageId: Long?)

    // Feedback
    suspend fun askForFeedback(chatId: ChatId, bookingId: Int, clubName: String, strings: LocalizedStrings, messageId: Long)
    suspend fun sendFeedbackThanks(chatId: ChatId, rating: Int, strings: LocalizedStrings, clubs: List<Club>, messageId: Long)


    // General
    suspend fun sendErrorMessage(chatId: ChatId, strings: LocalizedStrings, message: String? = null, clubs: List<Club>, messageId: Long? = null)
    suspend fun sendInfoMessage(chatId: ChatId, message: String, strings: LocalizedStrings, clubs: List<Club>, messageId: Long? = null)
    suspend fun sendUnknownCommandMessage(chatId: ChatId, strings: LocalizedStrings, clubs: List<Club>, messageId: Long? = null)
    suspend fun sendFeatureInDevelopmentMessage(chatId: ChatId, strings: LocalizedStrings, messageId: Long?)
}