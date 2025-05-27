package bot

import com.github.kotlintelegrambot.entities.Update
import com.github.kotlintelegrambot.entities.Message
import com.github.kotlintelegrambot.entities.CallbackQuery
import com.github.kotlintelegrambot.entities.ChatId

sealed interface BotEvent {
    data class CommandEvent(val chatId: ChatId, val command: String) : BotEvent
    data class CallbackEvent(val chatId: ChatId, val data: String) : BotEvent
    data class TextMessageEvent(val chatId: ChatId, val text: String) : BotEvent
}

fun Update.toBotEvent(): BotEvent? {
    val chatId = this.chatId ?: return null

    return when {
        message?.text?.startsWith("/") == true -> {
            BotEvent.CommandEvent(chatId, message!!.text!!)
        }
        callbackQuery?.data != null -> {
            BotEvent.CallbackEvent(chatId, callbackQuery!!.data)
        }
        message?.text != null -> {
            BotEvent.TextMessageEvent(chatId, message!!.text!!)
        }
        else -> null
    }
}