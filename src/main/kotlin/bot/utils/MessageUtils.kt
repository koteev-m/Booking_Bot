package bot.utils

import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.ParseMode
import com.github.kotlintelegrambot.entities.ReplyMarkup
import org.slf4j.Logger
import org.slf4j.LoggerFactory

object MessageUtils {
    private val logger: Logger = LoggerFactory.getLogger("MessageUtils")

    /**
     * Универсальная функция отправки сообщения с логированием и обработкой ошибок.
     * Возвращает true если сообщение ушло, false — если произошла ошибка.
     */
    suspend fun sendSafe(
        bot: Bot,
        chatId: ChatId,
        text: String,
        replyMarkup: ReplyMarkup? = null,
        parseMode: ParseMode = ParseMode.HTML,
        onErrorUserMsg: String? = null,
        userErrorCallback: (suspend () -> Unit)? = null
    ): Boolean {
        return try {
            bot.sendMessage(
                chatId = chatId,
                text = text,
                replyMarkup = replyMarkup,
                parseMode = parseMode
            ).fold(
                { response ->
                    if (!response.ok) {
                        logger.error("Failed to send message to $chatId: ${response.description}")
                        userErrorCallback?.invoke()
                        false
                    } else {
                        logger.info("Message sent to $chatId: ${text.take(80)}")
                        true
                    }
                },
                { error ->
                    logger.error("Exception while sending message to $chatId: ${error.message}", error.exception)
                    userErrorCallback?.invoke()
                    false
                }
            )
        } catch (e: Exception) {
            logger.error("Critical error sending message to $chatId: ${e.message}", e)
            userErrorCallback?.invoke()
            false
        }
    }
}