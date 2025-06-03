package fsm

import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.InlineKeyboardMarkup
import db.BookingWithClubName
import db.Club
import db.TableInfo
import java.time.LocalDate
import java.time.YearMonth
import java.time.Instant // Keep if some methods genuinely use Instant, but most should align to LocalDateTime

// Assuming LocalizedStrings is defined as in bot.facade.BotStrings
import bot.facade.LocalizedStrings

// DraftBooking used by sendConfirmMessage
// import fsm.DraftBooking // This was defined in BookingState.kt, ensure it's accessible

interface BotFacade {
    // Language
    suspend fun sendLanguageSelection(chatId: ChatId, currentStrings: LocalizedStrings, messageId: Long?)

    // Main Menu
    suspend fun sendWelcomeMessage(chatId: ChatId, userName: String?, strings: LocalizedStrings, clubs: List<Club>)
    suspend fun sendMainMenu(chatId: ChatId, strings: LocalizedStrings, clubs: List<Club>, messageId: Long? = null, text: String? = null)
    suspend fun sendStepTrackerMessage(chatId: ChatId, strings: LocalizedStrings, currentStep: Int, totalSteps: Int, messageText: String, keyboard: InlineKeyboardMarkup?, messageId: Long?)

    // Booking Flow - methods needed by BookingStateMachine.kt
    // Method names from original fsm.BotFacade.kt and adapted from BookingStateMachine.kt usage
    suspend fun sendChooseClubKeyboard(chatId: ChatId, clubs: List<Club>, strings: LocalizedStrings, messageId: Long?, step: Pair<Int,Int>?) // Matched
    suspend fun sendCalendar(chatId: ChatId, clubId: Int, clubTitle: String, availableDates: List<LocalDate>, strings: LocalizedStrings, messageId: Long?, step: Pair<Int,Int>?) // Adapted from BookingStateMachine
    suspend fun sendChooseTableKeyboard(chatId: ChatId, clubId: Int, clubTitle: String, date: LocalDate, tables: List<TableInfo>, strings: LocalizedStrings, messageId: Long?, step: Pair<Int,Int>?) // Adapted
    suspend fun sendChooseSlotKeyboard(chatId: ChatId, clubId: Int, clubTitle: String, date: LocalDate, table: TableInfo, slots: List<String>, strings: LocalizedStrings, messageId: Long?, step: Pair<Int,Int>?) // Adapted

    suspend fun askGuestCount(chatId: ChatId, clubTitle: String, tableLabel: String, date: LocalDate, slot: String, strings: LocalizedStrings, messageId: Long?, step: Pair<Int,Int>?) // Adapted
    suspend fun askGuestCountInvalid(chatId: ChatId, min: Int, max: Int, strings: LocalizedStrings, messageId: Long?) // Adapted

    suspend fun askGuestName(chatId: ChatId, strings: LocalizedStrings, messageId: Long?, step: Pair<Int,Int>?) // Matched
    suspend fun askGuestNameInvalid(chatId: ChatId, strings: LocalizedStrings, messageId: Long?) // Adapted

    suspend fun askGuestPhone(chatId: ChatId, strings: LocalizedStrings, messageId: Long?, step: Pair<Int,Int>?) // Matched
    suspend fun askGuestPhoneInvalid(chatId: ChatId, strings: LocalizedStrings, messageId: Long?) // Adapted

    suspend fun sendConfirmMessage(chatId: ChatId, draft: DraftBooking, clubName: String, tableNumber: Int, selectedDate: LocalDate, strings: LocalizedStrings, messageId: Long?, step: Pair<Int,Int>?) // From original, but BookingStateMachine uses showConfirmBooking
    suspend fun showConfirmBooking(chatId: ChatId, draft: DraftBooking, strings: LocalizedStrings, messageId: Long?, step: Pair<Int,Int>?) // New name based on BookingStateMachine, uses DraftBooking from fsm

    suspend fun sendBookingSuccessMessage(chatId: ChatId, bookingId: Int, loyaltyPoints: Int, strings: LocalizedStrings, clubs: List<Club>, messageId: Long?) // Matched, used as sendBookingSuccess in StateMachine
    suspend fun sendBookingCancelledMessage(chatId: ChatId, strings: LocalizedStrings, clubs: List<Club>, messageId: Long?) // Matched
    suspend fun sendActionCancelledMessage(chatId: ChatId, strings: LocalizedStrings, clubs: List<Club>, messageId: Long?) // Matched, used as sendActionCancelled in StateMachine


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
    suspend fun sendErrorMessage(chatId: ChatId, strings: LocalizedStrings, message: String? = null, clubs: List<Club>, messageId: Long? = null) // Matched, used in StateMachine
    suspend fun sendInfoMessage(chatId: ChatId, message: String, strings: LocalizedStrings, clubs: List<Club>, messageId: Long? = null)
    suspend fun sendUnknownCommandMessage(chatId: ChatId, strings: LocalizedStrings, clubs: List<Club>, messageId: Long? = null)
    suspend fun sendFeatureInDevelopmentMessage(chatId: ChatId, strings: LocalizedStrings, messageId: Long?)
}