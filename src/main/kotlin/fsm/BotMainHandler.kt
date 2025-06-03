package fsm

// import fsm.* // Already in fsm package
import kotlinx.coroutines.GlobalScope // Consider using a more structured scope
import kotlinx.coroutines.launch
import java.time.LocalDate
import com.github.kotlintelegrambot.entities.ChatId // For TelegramApi stub
import com.github.kotlintelegrambot.entities.InlineKeyboardMarkup // For TelegramApi stub
import java.time.format.DateTimeParseException

// This file seems to be an alternative FSM handling approach.
// The main.kt now contains a more integrated FSM handling.
// If this BotMainHandler is still intended to be used, its dependencies need to be provided.

object BotMainHandler {
    // This map should ideally store BookingStateMachine instances
    private val machines: MutableMap<Long, BookingStateMachine> = mutableMapOf()

    // This object needs FsmDeps to create BookingStateMachine instances.
    // This is a simplified stub. A proper DI mechanism or factory is needed.
    // For this example, let's assume FsmDeps can be magically obtained (which is not realistic).
    // In a real app, main.kt would set up FsmDeps and this handler would use it.
    private lateinit var fsmDepsPlaceholder: FsmDeps

    fun initialize(deps: FsmDeps) { // Method to inject dependencies
        fsmDepsPlaceholder = deps
    }

    fun onUpdate(update: TelegramUpdate) { // TelegramUpdate is a local data class here
        if (!::fsmDepsPlaceholder.isInitialized) {
            println("ERROR: BotMainHandler not initialized with FsmDeps!")
            return
        }

        val userTelegramId = update.chatId // Assuming update.chatId is the telegramUserId (Long)
        val fsmChatId = ChatId.fromId(userTelegramId) // Convert to library's ChatId

        val fsm = machines.getOrPut(userTelegramId) {
            // Create user-specific strings or use default from fsmDepsPlaceholder
            val userSpecificStrings = fsmDepsPlaceholder.strings // Or fetch based on user's language
            BookingStateMachine(
                deps = fsmDepsPlaceholder.copy(strings = userSpecificStrings),
                chatId = fsmChatId,
                telegramUserId = userTelegramId
            )
        }

        if (update.callbackData != null) {
            val data = update.callbackData
            // This mapping needs to be robust based on your callback data structure
            when {
                data.startsWith("/selectClub:") -> {
                    data.substringAfter("/selectClub:").toIntOrNull()?.let {
                        fsm.onEvent(BookingEvent.ClubSelected(it))
                    }
                }
                data.startsWith("/selectDate:") -> {
                    try {
                        LocalDate.parse(data.substringAfter("/selectDate:")).let {
                            fsm.onEvent(BookingEvent.DateSelected(it))
                        }
                    } catch (e: DateTimeParseException) { /* handle error */ }
                }
                data.startsWith("/selectTable:") -> { // "/selectTable:tableId"
                    data.substringAfter("/selectTable:").toIntOrNull()?.let { tableId ->
                        // To send TableSelected, we need the full TableInfo.
                        // This requires fetching it based on tableId.
                        // This shows a disconnect if callback only has ID but event needs object.
                        // For simplicity, this part would need a redesign or a new event type.
                        // Option 1: New Event BookingEvent.TableIdSelected(Int)
                        // Option 2: Fetch TableInfo here (adds repo dependency to this handler)
                        GlobalScope.launch { // Example: Fetching (not ideal in handler directly)
                            fsmDepsPlaceholder.tablesRepo.findById(tableId)?.let { tableInfo ->
                                fsm.onEvent(BookingEvent.TableSelected(tableInfo))
                            }
                        }
                    }
                }
                data == "/confirmBooking" -> fsm.onEvent(BookingEvent.ConfirmBooking) // Was ConfirmBookingNow
                data == "/cancelBooking" -> fsm.onEvent(BookingEvent.Cancel) // Was CancelBooking
                else -> println("Unknown callback data in BotMainHandler: $data")
            }
            return
        }

        if (update.text != null) {
            val text = update.text.trim()
            // State-dependent text processing
            GlobalScope.launch { // Event processing can be suspending
                when (val state = fsm.state.value) {
                    is BookingState.EnteringGuestCount -> {
                        text.toIntOrNull()?.let { fsm.onEvent(BookingEvent.GuestCountEntered(it)) }
                            ?: TelegramApi.sendMessage(userTelegramId, "Пожалуйста, введите число цифрами.", null)
                    }
                    is BookingState.EnteringGuestName -> {
                        if (text.isNotBlank()) fsm.onEvent(BookingEvent.GuestNameEntered(text))
                        else TelegramApi.sendMessage(userTelegramId, "Имя не может быть пустым.", null)
                    }
                    is BookingState.EnteringGuestPhone -> {
                        if (text.matches(Regex("^\\+?\\d{7,15}$"))) fsm.onEvent(BookingEvent.GuestPhoneEntered(text))
                        else TelegramApi.sendMessage(userTelegramId, "Неверный формат телефона.", null)
                    }
                    else -> {
                        when (text.lowercase()) {
                            "/book", "/start" -> fsm.onEvent(BookingEvent.Start) // Was StartBooking
                            "/cancel" -> fsm.onEvent(BookingEvent.Cancel)       // Was CancelBooking
                            else -> TelegramApi.sendMessage(userTelegramId, "Не понял вас.", null)
                        }
                    }
                }
            }
        }
    }
}

// These are local stubs for BotMainHandler.kt
data class TelegramUpdate(
    val chatId: Long, // This is telegramUserId
    val text: String? = null,
    val callbackData: String? = null
)

object TelegramApi { // Stub
    fun sendMessage(chatId: Long, text: String, keyboard: InlineKeyboardMarkup?) {
        println("BotMainHandler.TelegramApi → send to $chatId: $text ${keyboard?.let { "KB: $it" } ?: ""}")
    }
}