package bot

import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.Update

val Update.chatId: ChatId?
    get() = message?.chat?.id?.let { ChatId.fromId(it) }
        ?: callbackQuery?.message?.chat?.id?.let { ChatId.fromId(it) }

val Update.userId: Long? // Telegram User ID
    get() = message?.from?.id ?: callbackQuery?.from?.id

val Update.userName: String? // Telegram @username
    get() = message?.from?.username ?: callbackQuery?.from?.username

// Unused in current version, but kept for potential future use
// val Update.userFirstName: String?
// get() = message?.from?.firstName ?: callbackQuery?.from?.firstName

val Update.userLanguageCode: String? // Language code from Telegram user settings
    get() = message?.from?.languageCode ?: callbackQuery?.from?.languageCode