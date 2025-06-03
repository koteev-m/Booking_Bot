package bot.config

import io.github.cdimascio.dotenv.Dotenv
import com.github.kotlintelegrambot.logging.LogLevel as TelegramLogLevel

data class DbConfig(
    val host: String,
    val port: Int,
    val name: String,
    val user: String,
    val password: String
)

data class AppConfig(
    val botToken: String,
    val db: DbConfig,
    val defaultTimeZone: String,
    val telegramLogLevel: TelegramLogLevel
)

object EnvConfig {
    // Загружаем .env (если он есть). Если .env отсутствует, просто переходим к System.getenv()
    private val dotenv: Dotenv = Dotenv.configure()
        .ignoreIfMissing()
        .load()

    /**
     * Читает конфигурацию:
     * 1) Сначала из файла .env
     * 2) Если там нет – ищем в System.getenv()
     * 3) Если и там не найдено – используем значение по умолчанию
     */
    fun load(): AppConfig {
        val botToken = dotenv["TELEGRAM_BOT_TOKEN"]
            ?: System.getenv("TELEGRAM_BOT_TOKEN")
            ?: ""

        val dbHost = dotenv["POSTGRES_HOST"]
            ?: System.getenv("POSTGRES_HOST")
            ?: "localhost"

        val dbPort: Int = runCatching {
            (dotenv["POSTGRES_PORT"] ?: System.getenv("POSTGRES_PORT") ?: "5432").toInt()
        }.getOrDefault(5432)

        val dbName = dotenv["POSTGRES_DB"]
            ?: System.getenv("POSTGRES_DB")
            ?: "club_booking_bot_db_v2"

        val dbUser = dotenv["POSTGRES_USER"]
            ?: System.getenv("POSTGRES_USER")
            ?: "clubadmin"

        val dbPass = dotenv["POSTGRES_PASSWORD"]
            ?: System.getenv("POSTGRES_PASSWORD")
            ?: "supersecret"

        val defaultTz = dotenv["DEFAULT_TIMEZONE"]
            ?: System.getenv("DEFAULT_TIMEZONE")
            ?: "Europe/Moscow"

        // Читаем уровень логирования из .env или System.getenv; по умолчанию "Error"
        val lvlStr = (dotenv["TELEGRAM_LOG_LEVEL"] ?: System.getenv("TELEGRAM_LOG_LEVEL") ?: "Error")
            .trim()
            .uppercase()

        /**
         * В версии kotlin-telegram-bot, в пакете com.github.kotlintelegrambot.logging,
         * доступны два простых уровня:
         *   • LogLevel.None   (никаких логов)
         *   • LogLevel.Error  (только ошибки)
         *
         * Если вам нужен более детальный уровень, проверьте свою версию библиотеки
         * и добавьте соответствующие объекты (например, Warning или Info),
         * но в данном коде мы ограничиваемся только этими двумя.
         */
        val tglLevel: TelegramLogLevel = when (lvlStr) {
            "NONE"  -> TelegramLogLevel.None
            else    -> TelegramLogLevel.Error
        }

        return AppConfig(
            botToken = botToken,
            db = DbConfig(
                host     = dbHost,
                port     = dbPort,
                name     = dbName,
                user     = dbUser,
                password = dbPass
            ),
            defaultTimeZone   = defaultTz,
            telegramLogLevel  = tglLevel
        )
    }
}