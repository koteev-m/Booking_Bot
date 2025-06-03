package db

import db.repositories.ClubsRepoImpl
import db.repositories.TablesRepoImpl
import db.repositories.UsersRepoImpl
import db.repositories.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import net.bytebuddy.utility.dispatcher.JavaDispatcher
import org.jetbrains.exposed.sql.Database
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Testcontainers
import java.nio.file.Files
import java.nio.file.Paths

/**
 * Интеграционный тест репозиториев на real PostgreSQL (Testcontainers).
 */
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RepositoriesTest {

    companion object {
        @JavaDispatcher.Container
        private val pg = PostgreSQLContainer("postgres:16-alpine").apply {
            withDatabaseName("test_db")
            withUsername("test")
            withPassword("test")
            start()
        }
    }

    private lateinit var clubsRepo: ClubsRepoImpl
    private lateinit var tablesRepo: TablesRepoImpl
    private lateinit var usersRepo: UsersRepoImpl
    private lateinit var bookingsRepo: BookingsRepoImpl

    @BeforeAll
    fun setUp() {
        // Инициализируем Exposed
        Database.connect(
            url = pg.jdbcUrl,
            driver = "org.postgresql.Driver",
            user = pg.username,
            password = pg.password,
        )

        // Создаём схему через миграции Flyway (тот же метод, что в main.kt)
        migrateDatabase(pg.jdbcUrl, pg.username, pg.password)

        clubsRepo    = ClubsRepoImpl()
        tablesRepo   = TablesRepoImpl(bookingsRepo = BookingsRepoImpl()) // временный
        usersRepo    = UsersRepoImpl()
        bookingsRepo = BookingsRepoImpl()

        // Читаем JSON-фикстуры
        val jsonPath = Paths.get("src/test/resources/test-data.json")
        val jsonData = Files.readString(jsonPath)
        val fixture: Fixture = Json.decodeFromString(jsonData)

        // Наполняем БД
        clubsRepo.create(fixture.clubs.first())
        tablesRepo.create(fixture.tables.first())
        usersRepo.create(fixture.users.first())
        bookingsRepo.create(fixture.bookings.first())
    }

    @AfterAll
    fun tearDown() {
        pg.stop()
    }

    @Test
    fun `booking repository CRUD works as expected`() {
        val allBookings = bookingsRepo.findAll()
        assertEquals(1, allBookings.size)

        val b = allBookings.first()
        assertEquals(BookingStatus.CONFIRMED, b.status)

        // Изменяем статус
        bookingsRepo.updateStatus(b.id, BookingStatus.CANCELLED)
        val updated = bookingsRepo.findById(b.id)!!
        assertEquals(BookingStatus.CANCELLED, updated.status)
    }

    // DTO для JSON
    @Serializable
    data class Fixture(
        val clubs: List<Club>,
        val tables: List<TableInfo>,
        val users: List<User>,
        val bookings: List<Booking>,
    )
}