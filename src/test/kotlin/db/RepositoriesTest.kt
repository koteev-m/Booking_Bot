package db

import db.repositories.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
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
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@Serializable
data class JsonClub(val id: Int? = null, val name: String, val address: String, val timezone: String)
@Serializable
data class JsonTable(val id: Int? = null, val clubId: Int, val name: String, val capacity: Int) // name is label, capacity is seats
@Serializable
data class JsonUser(val id: Int? = null, val telegramId: Long, val fullName: String, val phone: String?)
@Serializable
data class JsonBooking(
    val id: Int? = null,
    val userId: Long, // This is telegramId from users in test-data
    val tableId: Int,
    val date: String, // "YYYY-MM-DD"
    val time: String, // "HH:MM"
    val guestCount: Int,
    val status: String // "CONFIRMED"
)

@Serializable
data class TestFixture(
    val clubs: List<JsonClub>,
    val tables: List<JsonTable>,
    val users: List<JsonUser>,
    val bookings: List<JsonBooking>,
)

@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RepositoriesTest {

    companion object {
        private val pg: PostgreSQLContainer<*> by lazy {
            PostgreSQLContainer("postgres:16-alpine").apply {
                withDatabaseName("test_db")
                withUsername("test")
                withPassword("test")
                start()
            }
        }
    }

    private lateinit var clubsRepo: ClubsRepo
    private lateinit var tablesRepo: TablesRepo
    private lateinit var usersRepo: UsersRepo
    private lateinit var bookingsRepo: BookingsRepo

    @BeforeAll
    fun setUp() {
        Database.connect(pg.jdbcUrl, driver = "org.postgresql.Driver", user = pg.username, password = pg.password)
        migrateDatabase(pg.jdbcUrl, pg.username, pg.password)

        usersRepo = UsersRepoImpl()
        clubsRepo = ClubsRepoImpl()
        bookingsRepo = BookingsRepoImpl(usersRepo)
        tablesRepo = TablesRepoImpl(bookingsRepo)

        val jsonPath = Paths.get("src/test/resources/test-data.json")
        val jsonData = Files.readString(jsonPath)
        val fixture: TestFixture = Json { ignoreUnknownKeys = true }.decodeFromString(jsonData)

        val createdClubsMap = mutableMapOf<Int, Club>() // Maps original JSON ID to created Club
        fixture.clubs.forEach { jsonClub ->
            val created = clubsRepo.create(
                Club(
                    name = jsonClub.name, // In DB this is 'title'
                    code = jsonClub.name.replace(" ", "_").take(10).uppercase(), // Ensure code is sensible
                    description = "Описание для ${jsonClub.name}",
                    address = jsonClub.address,
                    phone = "N/A", // Add if in JSON
                    workingHours = "10:00-02:00", // Add if in JSON
                    timezone = jsonClub.timezone,
                    photoUrl = null,
                    floorPlanImageUrl = null,
                    isActive = true,
                    createdAt = LocalDateTime.now(), // DB will set this via clientDefault
                    updatedAt = null
                )
            )
            jsonClub.id?.let { createdClubsMap[it] = created }
        }

        val createdUsersMap = mutableMapOf<Long, User>() // Maps telegramId to created User
        fixture.users.forEach { jsonUser ->
            val nameParts = jsonUser.fullName.split(" ", limit = 2)
            val created = usersRepo.create( // create should handle getOrCreate logic for tests
                User(
                    telegramId = jsonUser.telegramId,
                    firstName = nameParts.getOrNull(0),
                    lastName = nameParts.getOrNull(1),
                    username = jsonUser.fullName.filterNot { it.isWhitespace() }.toLowerCase(),
                    phone = jsonUser.phone,
                    languageCode = "ru", // Default for test
                    loyaltyPoints = 0,
                    createdAt = LocalDateTime.now(),
                    lastActivityAt = LocalDateTime.now()
                )
            )
            createdUsersMap[jsonUser.telegramId] = created
        }

        val createdTablesMap = mutableMapOf<Int, TableInfo>() // Maps original JSON ID to created TableInfo
        fixture.tables.forEach { jsonTable ->
            // Use the ID from createdClubsMap if the original jsonClub.id was used as key
            // Or, if jsonTable.clubId directly matches the id in test-data.json for clubs
            val dbClub = createdClubsMap[jsonTable.clubId]
                ?: throw IllegalStateException("Club with original ID ${jsonTable.clubId} not found in createdClubsMap")

            val created = tablesRepo.create(
                TableInfo(
                    clubId = dbClub.id,
                    number = jsonTable.id ?: 0, // Using jsonTable.id as number, ensure uniqueness if needed
                    seats = jsonTable.capacity,
                    description = "Описание для ${jsonTable.name}",
                    isActive = true,
                    label = jsonTable.name, // Table label
                    posX = null, posY = null, photoUrl = null
                )
            )
            jsonTable.id?.let { createdTablesMap[it] = created }
        }

        fixture.bookings.forEach { jsonBooking ->
            val dbUser = createdUsersMap[jsonBooking.userId] // userId in JSON is telegramId
                ?: throw IllegalStateException("User with telegramId ${jsonBooking.userId} not found in createdUsersMap")
            val dbTable = createdTablesMap[jsonBooking.tableId]
                ?: throw IllegalStateException("Table with original ID ${jsonBooking.tableId} not found in createdTablesMap")

            val bookingDate = LocalDate.parse(jsonBooking.date, DateTimeFormatter.ISO_LOCAL_DATE)
            val bookingTime = LocalTime.parse(jsonBooking.time, DateTimeFormatter.ISO_LOCAL_TIME)
            val dateTimeStart = LocalDateTime.of(bookingDate, bookingTime)
            val dateTimeEnd = dateTimeStart.plusHours(2) // Assuming 2-hour bookings for test

            bookingsRepo.create(
                Booking(
                    clubId = dbTable.clubId, // Get clubId from the dbTable
                    tableId = dbTable.id,
                    userId = dbUser.id, // Use internal DB user ID
                    guestsCount = jsonBooking.guestCount,
                    dateStart = dateTimeStart,
                    dateEnd = dateTimeEnd,
                    status = BookingStatus.valueOf(jsonBooking.status.uppercase()),
                    comment = "Тестовое бронирование",
                    guestName = dbUser.firstName ?: jsonUser.fullName, // Use name from user
                    guestPhone = dbUser.phone,
                    loyaltyPointsEarned = jsonBooking.guestCount * 10,
                    createdAt = LocalDateTime.now(), // DB default
                    updatedAt = null
                )
            )
        }
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

        bookingsRepo.updateStatus(b.id, BookingStatus.CANCELLED)
        val updated = bookingsRepo.findById(b.id)!!
        assertEquals(BookingStatus.CANCELLED, updated.status)
    }
}