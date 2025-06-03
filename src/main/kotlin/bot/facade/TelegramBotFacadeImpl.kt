package bot.facade

import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.entities.ChatId
import org.slf4j.LoggerFactory

/**
 * Упрощённый фасад для kotlin-telegram-bot (v6.3.0+).
 *
 * Вместо сложных fold-операций мы просто оборачиваем вызовы
 * sendMessage и editMessageText в try/catch, чтобы поймать ошибки.
 */
class TelegramBotFacadeImpl(
    private val bot: Bot
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * Отправляет сообщение в чат. Если Telegram API бросает исключение,
     * оно логируется, но не поднимается дальше.
     */
    fun sendMessage(
        chatId: ChatId,
        text: String
    ) {
        try {
            bot.sendMessage(
                chatId = chatId,
                text   = text
            )
        } catch (e: Exception) {
            logger.error("Telegram API error on sendMessage: ${e.message}", e)
        }
    }

    /**
     * Редактирует текст уже отправленного сообщения. Если Telegram API бросает исключение,
     * оно логируется, но не поднимается дальше.
     */
    fun editMessage(
        chatId: ChatId,
        messageId: Long,
        newText: String
    ) {
        try {
            bot.editMessageText(
                chatId    = chatId,
                messageId = messageId,
                text      = newText
            )
        } catch (e: Exception) {
            logger.error("Telegram API error on editMessageText: ${e.message}", e)
        }
    }
}