package db.repositories

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.time.LocalDateTime

/**
 * Data-класс, представляющий пользователя.
 * Поля должны соответствовать вашему Entities.kt.
 */
data class User(
    val id: Int,
    val telegramUserId: Long,
    val fullName: String,
    val phone: String?,
    val languageCode: String,
    val lastActivity: LocalDateTime
)

/**
 * Определение таблицы Exposed для пользователей.
 * Должно совпадать с вашим Tables.kt.
 */
object UsersTable : Table("users") {
    val telegramId = long("telegram_id").primaryKey()        // Telegram ID в качестве PK
    val fullName = varchar("full_name", length = 255).nullable()  // ФИО, может быть null
    val phone = varchar("phone", length = 64).nullable()      // Телефон, может быть null
    val languageCode = varchar("language_code", length = 10) // Код языка ("en", "ru" и т.д.)
    val createdAt = datetime("created_at")                   // Время создания записи
}

/**
 * Интерфейс репозитория для работы с пользователями.
 */
interface UsersRepo {
    /**
     * Ищет пользователя по telegramId или создаёт нового, если не найден.
     * Возвращает объект User.
     */
    suspend fun getOrCreate(
        telegramId: Long,
        fullName: String?,
        phone: String?,
        languageCode: String?
    ): User
}

/**
 * Реализация UsersRepo на базе Exposed + корутин.
 */
class UsersRepoImpl : UsersRepo {
    override suspend fun getOrCreate(
        telegramId: Long,
        fullName: String?,
        phone: String?,
        languageCode: String?
    ): User = newSuspendedTransaction {
        // 1) Пытаемся найти существующего пользователя
        val existing = UsersTable.select { UsersTable.telegramId eq telegramId }
            .limit(1)
            .firstOrNull()

        if (existing != null) {
            // 2) Если найден, проверяем, нужно ли обновить поля (например, язык или имя)
            val currentLang = existing[UsersTable.languageCode]
            val newLang = languageCode ?: currentLang
            if (newLang != currentLang || (fullName != null && fullName != existing[UsersTable.fullName])) {
                UsersTable.update({ UsersTable.telegramId eq telegramId }) {
                    if (fullName != null) it[UsersTable.fullName] = fullName
                    it[UsersTable.languageCode] = newLang
                    phone?.let { ph -> it[UsersTable.phone] = ph }
                }
            }
            return@newSuspendedTransaction rowToUser(existing)
        }

        // 3) Если не найден, создаём нового пользователя
        val now = org.jetbrains.exposed.sql.jodatime.CurrentDateTime() // или LocalDateTime.now()
        UsersTable.insert { row ->
            row[UsersTable.telegramId] = telegramId
            row[UsersTable.fullName] = fullName
            row[UsersTable.phone] = phone
            row[UsersTable.languageCode] = languageCode ?: "en" // по умолчанию "en", если не указан
            row[UsersTable.createdAt] = now
        }

        // 4) Возвращаем только что созданную запись
        UsersTable.select { UsersTable.telegramId eq telegramId }
            .limit(1)
            .first()
            .let { rowToUser(it) }
    }

    /**
     * Преобразует ResultRow в объект User.
     */
    private fun rowToUser(row: ResultRow): User {
        return User(
            telegramId = row[UsersTable.telegramId],
            fullName = row[UsersTable.fullName],
            phone = row[UsersTable.phone],
            languageCode = row[UsersTable.languageCode],
            createdAt = row[UsersTable.createdAt].toLocalDateTime(),
        )
    }
}