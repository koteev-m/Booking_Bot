package fsm

import com.github.kotlintelegrambot.entities.ChatId
import bot.LocalizedStrings
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth

sealed interface FsmEvent {
    val chatId: ChatId
    val telegramUserId: Long
    val messageId: Long? // ID of the message being replied to or edited
    val strings: LocalizedStrings // Current language for processing this event
}

// --- User Initiated Events (Commands, ReplyKeyboard, direct text for FSM) ---
data class StartCommandEvent(
    override val chatId: ChatId, override val telegramUserId: Long, override val messageId: Long?,
    override val strings: LocalizedStrings, val userName: String?, val userInitialLangCode: String?
) : FsmEvent

data class HelpCommandEvent(
    override val chatId: ChatId, override val telegramUserId: Long, override val messageId: Long?,
    override val strings: LocalizedStrings
) : FsmEvent

data class ChangeLanguageCommandEvent(
    override val chatId: ChatId, override val telegramUserId: Long, override val messageId: Long?,
    override val strings: LocalizedStrings
) : FsmEvent

data class MainMenuActionChosenEvent(
    override val chatId: ChatId, override val telegramUserId: Long, override val messageId: Long?,
    override val strings: LocalizedStrings, val actionType: MainMenuActionType
) : FsmEvent

// --- CallbackQuery Events (InlineKeyboards) ---
data class LanguageSelectedEvent(
    override val chatId: ChatId, override val telegramUserId: Long, override val messageId: Long?,
    override val strings: LocalizedStrings, val selectedLanguageCode: String
) : FsmEvent

data class ClubChosenForBookingEvent(
    override val chatId: ChatId, override val telegramUserId: Long, override val messageId: Long?,
    override val strings: LocalizedStrings, val clubId: Int
) : FsmEvent

data class DateChosenEvent(
    override val chatId: ChatId, override val telegramUserId: Long, override val messageId: Long?,
    override val strings: LocalizedStrings, val date: LocalDate
) : FsmEvent

data class CalendarMonthChangeEvent(
    override val chatId: ChatId, override val telegramUserId: Long, override val messageId: Long?,
    override val strings: LocalizedStrings, val targetYearMonth: YearMonth
) : FsmEvent

data class TableChosenEvent(
    override val chatId: ChatId, override val telegramUserId: Long, override val messageId: Long?,
    override val strings: LocalizedStrings, val tableId: Int
) : FsmEvent

data class SlotChosenEvent(
    override val chatId: ChatId, override val telegramUserId: Long, override val messageId: Long?,
    override val strings: LocalizedStrings, val start: Instant, val end: Instant
) : FsmEvent

data class ConfirmBookingPressedEvent(
    override val chatId: ChatId, override val telegramUserId: Long, override val messageId: Long?,
    override val strings: LocalizedStrings
) : FsmEvent

data class VenueChosenForInfoEvent(
    override val chatId: ChatId, override val telegramUserId: Long, override val messageId: Long?,
    override val strings: LocalizedStrings, val venueId: Int
) : FsmEvent

data class VenuePostersRequest( // Placeholder for WIP feature
    override val chatId: ChatId, override val telegramUserId: Long, override val messageId: Long?,
    override val strings: LocalizedStrings, val venueId: Int
) : FsmEvent

data class VenuePhotosRequest( // Placeholder for WIP feature
    override val chatId: ChatId, override val telegramUserId: Long, override val messageId: Long?,
    override val strings: LocalizedStrings, val venueId: Int
) : FsmEvent


data class ManageBookingPressedEvent(
    override val chatId: ChatId, override val telegramUserId: Long, override val messageId: Long?,
    override val strings: LocalizedStrings, val bookingId: Int
) : FsmEvent

data class ExecuteCancelBookingEvent(
    override val chatId: ChatId, override val telegramUserId: Long, override val messageId: Long?,
    override val strings: LocalizedStrings, val bookingId: Int
) : FsmEvent

data class ExecuteChangeBookingInfoEvent(
    override val chatId: ChatId, override val telegramUserId: Long, override val messageId: Long?,
    override val strings: LocalizedStrings, val bookingId: Int
) : FsmEvent

data class RateBookingEvent(
    override val chatId: ChatId, override val telegramUserId: Long, override val messageId: Long?,
    override val strings: LocalizedStrings, val bookingId: Int, val rating: Int? // rating is null if just opening feedback prompt
) : FsmEvent

data class BackPressedEvent(
    override val chatId: ChatId, override val telegramUserId: Long, override val messageId: Long?,
    override val strings: LocalizedStrings, val targetStateName: String
) : FsmEvent

data class CancelActionPressedEvent(
    override val chatId: ChatId, override val telegramUserId: Long, override val messageId: Long?,
    override val strings: LocalizedStrings
) : FsmEvent

// --- Text Input Events (when FSM is expecting specific text) ---
data class PeopleEnteredEvent(
    override val chatId: ChatId, override val telegramUserId: Long, override val messageId: Long?,
    override val strings: LocalizedStrings, val count: Int
) : FsmEvent

data class GuestNameEnteredEvent(
    override val chatId: ChatId, override val telegramUserId: Long, override val messageId: Long?,
    override val strings: LocalizedStrings, val name: String
) : FsmEvent

data class GuestPhoneEnteredEvent(
    override val chatId: ChatId, override val telegramUserId: Long, override val messageId: Long?,
    override val strings: LocalizedStrings, val phone: String
) : FsmEvent

data class QuestionTextEnteredEvent(
    override val chatId: ChatId, override val telegramUserId: Long, override val messageId: Long?,
    override val strings: LocalizedStrings, val question: String
) : FsmEvent

// --- System & Other Events ---
data class UnknownInputEvent(
    override val chatId: ChatId, override val telegramUserId: Long, override val messageId: Long?,
    override val strings: LocalizedStrings, val text: String?
) : FsmEvent

data class ErrorOccurredEvent(
    override val chatId: ChatId, override val telegramUserId: Long, override val messageId: Long?,
    override val strings: LocalizedStrings, val exception: Exception, val userVisibleMessage: String? = null
) : FsmEvent

enum class MainMenuActionType {
    SHOW_VENUE_INFO, MY_BOOKINGS, BOOK_CLUB_BY_ID, // BOOK_CLUB_BY_ID is for ReplyKeyboard quick book buttons
    ASK_QUESTION, OPEN_APP, SHOW_HELP, CHANGE_LANGUAGE
}