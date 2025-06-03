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
     * Универсальная функция для безопасной отправки сообщения.
     * Возвращает true, если сообщение ушло успешно, и false — если произошла ошибка.
     *
     * @param bot              экземпляр kotlin-telegram-bot Bot
     * @param chatId           ChatId, в который нужно отправить сообщение
     * @param text             текст сообщения
     * @param replyMarkup      (опционально) разметка кнопок
     * @param parseMode        (опционально) разметка HTML/Markdown
     * @param onErrorUserMsg   (опционально) текст, который можно отправить пользователю при ошибке
     * @param userErrorCallback (опционально) лямбда, вызывающаяся при ошибке (например, логика fallback)
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
            // Просто пробуем отправить сообщение
            bot.sendMessage(
                chatId = chatId,
                text = text,
                replyMarkup = replyMarkup,
                parseMode = parseMode
            )
            logger.info("Message sent to $chatId: ${text.take(80)}")
            true
        } catch (e: Exception) {
            // Если что-то пошло не так, логируем ошибку
            logger.error("Error sending message to $chatId: ${e.message}", e)
            // Если передан callback, вызываем его (например, чтобы уведомить пользователя)
            userErrorCallback?.invoke()
            // При желании, можно отдельно отправить onErrorUserMsg через BotFacade
            // но здесь мы просто возвращаем false
            false
        }
    }
}