package db.repositories

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.time.*

/**
 * Data-класс, представляющий клуб (venue).
 * Поля здесь должны совпадать с теми, что в вашем Entities.kt или схеме БД.
 */
data class Club(
    val id: Int,
    val name: String,
    val address: String,
    val timezone: String,
    val isActive: Boolean,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime?
)
/**
 * Определение таблицы Exposed. Должно совпадать с вашим Tables.kt.
 */
object ClubsTable : Table("clubs") {
    val id = integer("id").autoIncrement().primaryKey()     // первичный ключ, автоинкремент
    val name = varchar("name", length = 255)                 // название клуба
    val description = text("description").nullable()         // описание, может быть null
    val address = varchar("address", length = 512).nullable()// адрес, может быть null
    val phone = varchar("phone", length = 64).nullable()     // телефон, может быть null
    val workingHours = varchar("working_hours", length = 255).nullable() // часы работы
}

/**
 * Интерфейс репозитория для работы с клубами.
 */
interface ClubsRepo {
    suspend fun getAllClubs(): List<Club>                 // получить все клубы
    suspend fun findById(clubId: Int): Club?             // найти клуб по ID
}

/**
 * Реализация ClubsRepo на базе Exposed + корутин.
 */
class ClubsRepoImpl : ClubsRepo {
    override suspend fun getAllClubs(): List<Club> = newSuspendedTransaction {
        // Выбираем все строки из ClubsTable и мапим в List<Club>
        ClubsTable.selectAll().map { rowToClub(it) }
    }

    override suspend fun findById(clubId: Int): Club? = newSuspendedTransaction {
        // Ищем первую запись, где id = clubId
        ClubsTable
            .select { ClubsTable.id eq clubId }
            .limit(1)
            .firstOrNull()
            ?.let { rowToClub(it) }
    }

    /**
     * Преобразование строки из результата запроса в объект Club.
     */
    private fun rowToClub(row: ResultRow): Club {
        return Club(
            id = row[ClubsTable.id],
            name = row[ClubsTable.name],
            description = row[ClubsTable.description],
            address = row[ClubsTable.address],
            phone = row[ClubsTable.phone],
            workingHours = row[ClubsTable.workingHours]
        )
    }
}