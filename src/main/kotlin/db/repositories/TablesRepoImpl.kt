package db.repositories

import db.TableInfo
import db.Club
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import org.jetbrains.exposed.dao.id.EntityID // For setting reference column

// ... (constants and helper methods parseWorkingHours, toTableInfo remain same) ...
// Constants for booking logic
private const val DEFAULT_BOOKING_SLOT_DURATION_MINUTES = 120L
private const val DEFAULT_BUFFER_MINUTES_AROUND_BOOKING = 0L

class TablesRepoImpl(
    private val bookingsRepo: BookingsRepo
) : TablesRepo {

    private fun ResultRow.toTableInfo(): TableInfo = TableInfo(
        id = this[TablesTable.id].value,
        clubId = this[TablesTable.clubId].value,
        number = this[TablesTable.number],
        seats = this[TablesTable.seats],
        description = this[TablesTable.description],
        posX = this[TablesTable.posX],
        posY = this[TablesTable.posY],
        photoUrl = this[TablesTable.photoUrl],
        isActive = this[TablesTable.isActive],
        label = "Стол №${this[TablesTable.number]}"
    )

    private fun parseWorkingHours(workingHoursString: String?, clubZoneId: ZoneId): Pair<LocalTime, LocalTime>? {
        if (workingHoursString.isNullOrBlank()) return null
        val parts = workingHoursString.split("-")
        if (parts.size != 2) return null

        return try {
            val startTime = LocalTime.parse(parts[0].trim(), DateTimeFormatter.ISO_LOCAL_TIME)
            val endTime = LocalTime.parse(parts[1].trim(), DateTimeFormatter.ISO_LOCAL_TIME)
            Pair(startTime, endTime)
        } catch (e: DateTimeParseException) {
            null
        }
    }


    override suspend fun getAllActiveTablesByClub(clubId: Int): List<TableInfo> = newSuspendedTransaction {
        TablesTable
            .select { (TablesTable.clubId eq EntityID(clubId, ClubsTable)) and (TablesTable.isActive eq true) }
            .map { it.toTableInfo() }
    }

    override suspend fun findById(id: Int): TableInfo? = newSuspendedTransaction {
        TablesTable
            .select { TablesTable.id eq id }
            .map { it.toTableInfo() }
            .singleOrNull()
    }

    override suspend fun getAvailableDatesForClub(clubId: Int, month: LocalDate, lookAheadMonths: Int): List<LocalDate> = newSuspendedTransaction {
        val clubEntity = ClubsTable.select { ClubsTable.id eq clubId }.singleOrNull()?.let {
            Club(
                id = it[ClubsTable.id].value,
                name = it[ClubsTable.title],
                code = it[ClubsTable.code],
                workingHours = it[ClubsTable.workingHours],
                timezone = it[ClubsTable.timezone],
                description = null, address = null, phone = null, photoUrl = null, floorPlanImageUrl = null, isActive = true, createdAt = LocalDateTime.now(), updatedAt = null
            )
        } ?: return@newSuspendedTransaction emptyList()

        val clubZoneId = try { ZoneId.of(clubEntity.timezone) } catch (e: Exception) { ZoneId.systemDefault() }
        parseWorkingHours(clubEntity.workingHours, clubZoneId)
            ?: return@newSuspendedTransaction emptyList()

        val todayInClubTimezone = LocalDate.now(clubZoneId)
        val startDate = if (month.isBefore(todayInClubTimezone) && month.year == todayInClubTimezone.year && month.month == todayInClubTimezone.month) {
            todayInClubTimezone
        } else {
            month.withDayOfMonth(1)
        }
        val endDate = month.withDayOfMonth(1).plusMonths(lookAheadMonths.toLong()).minusDays(1)
        val allClubTables = getAllActiveTablesByClub(clubId)
        if (allClubTables.isEmpty()) return@newSuspendedTransaction emptyList()

        val availableDates = mutableListOf<LocalDate>()
        var currentDate = startDate
        while (!currentDate.isAfter(endDate)) {
            if (!currentDate.isBefore(todayInClubTimezone)) {
                val hasAnyAvailability = allClubTables.any { table ->
                    getAvailableSlotsForTable(table.id, currentDate, clubEntity).isNotEmpty()
                }
                if (hasAnyAvailability) {
                    availableDates.add(currentDate)
                }
            }
            currentDate = currentDate.plusDays(1)
        }
        availableDates
    }


    override suspend fun getAvailableTables(clubId: Int, date: LocalDate): List<TableInfo> = newSuspendedTransaction {
        val clubEntity = ClubsTable.select { ClubsTable.id eq clubId }.singleOrNull()?.let {
            Club(
                id = it[ClubsTable.id].value,
                name = it[ClubsTable.title],
                code = it[ClubsTable.code],
                workingHours = it[ClubsTable.workingHours],
                timezone = it[ClubsTable.timezone],
                description = null, address = null, phone = null, photoUrl = null, floorPlanImageUrl = null, isActive = true, createdAt = LocalDateTime.now(), updatedAt = null
            )
        } ?: return@newSuspendedTransaction emptyList()

        val allClubTables = getAllActiveTablesByClub(clubId)
        allClubTables.filter { table ->
            getAvailableSlotsForTable(table.id, date, clubEntity).isNotEmpty()
        }
    }

    override suspend fun getAvailableSlotsForTable(tableId: Int, date: LocalDate, club: Club): List<String> = newSuspendedTransaction {
        val clubZoneId = try { ZoneId.of(club.timezone) } catch (e: Exception) { ZoneId.systemDefault() }
        val workingHoursPair = parseWorkingHours(club.workingHours, clubZoneId)
            ?: return@newSuspendedTransaction emptyList()

        var (clubOpensLocal, clubClosesLocal) = workingHoursPair

        val availableSlots = mutableListOf<String>()
        val slotDuration = Duration.ofMinutes(DEFAULT_BOOKING_SLOT_DURATION_MINUTES)
        val bufferDuration = Duration.ofMinutes(DEFAULT_BUFFER_MINUTES_AROUND_BOOKING)

        val nowInClubTime = ZonedDateTime.now(clubZoneId)

        val workingDayEndLocal: LocalDateTime = if (clubClosesLocal.isBefore(clubOpensLocal)) {
            date.plusDays(1).atTime(clubClosesLocal)
        } else {
            date.atTime(clubClosesLocal)
        }

        var slotStartDateTime = date.atTime(clubOpensLocal)

        while(true) {
            val slotEndDateTime = slotStartDateTime.plus(slotDuration)
            if (slotEndDateTime.isAfter(workingDayEndLocal)) break

            if (date.isEqual(nowInClubTime.toLocalDate()) && slotStartDateTime.toLocalTime().isBefore(nowInClubTime.toLocalTime().plusMinutes(1))) { // allow slots starting now
                slotStartDateTime = slotStartDateTime.plusMinutes(30)
                continue
            }

            val periodStartWithBuffer = slotStartDateTime.minus(bufferDuration)
            val periodEndWithBuffer = slotEndDateTime.plus(bufferDuration)

            val isBooked = !bookingsRepo.isTableAvailableOnPeriod(tableId, periodStartWithBuffer, periodEndWithBuffer)

            if (!isBooked) {
                availableSlots.add("${slotStartDateTime.toLocalTime().format(DateTimeFormatter.ISO_LOCAL_TIME)}–${slotEndDateTime.toLocalTime().format(DateTimeFormatter.ISO_LOCAL_TIME)}")
            }
            slotStartDateTime = slotStartDateTime.plusMinutes(30)
        }
        availableSlots
    }

    override suspend fun create(tableInfo: TableInfo): TableInfo = newSuspendedTransaction {
        val clubExists = ClubsTable.select { ClubsTable.id eq tableInfo.clubId }.count() > 0
        if (!clubExists) throw IllegalArgumentException("Club with id ${tableInfo.clubId} does not exist.")

        val id = TablesTable.insertAndGetId {
            it[clubId] = EntityID(tableInfo.clubId, ClubsTable) // Correctly use EntityID
            it[number] = tableInfo.number
            it[seats] = tableInfo.seats
            it[description] = tableInfo.description
            it[posX] = tableInfo.posX
            it[posY] = tableInfo.posY
            it[photoUrl] = tableInfo.photoUrl
            it[isActive] = tableInfo.isActive
        }
        findById(id.value)!!
    }
}