package db.repositories

import db.TableInfo
import db.Club
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

// Constants for booking logic - consider making these configurable
private const val DEFAULT_BOOKING_SLOT_DURATION_MINUTES = 120L // 2 hours
private const val DEFAULT_BUFFER_MINUTES_AROUND_BOOKING = 0L // No buffer for this example

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
        // Assuming format "HH:MM-HH:MM" (e.g., "10:00-02:00" where 02:00 is next day)
        val parts = workingHoursString.split("-")
        if (parts.size != 2) return null // Invalid format

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
            .select { (TablesTable.clubId eq clubId) and (TablesTable.isActive eq true) }
            .map { it.toTableInfo() }
    }

    override suspend fun findById(id: Int): TableInfo? = newSuspendedTransaction {
        TablesTable
            .select { TablesTable.id eq id }
            .map { it.toTableInfo() }
            .singleOrNull()
    }

    override suspend fun getAvailableDatesForClub(clubId: Int, month: LocalDate, lookAheadMonths: Int): List<LocalDate> = newSuspendedTransaction {
        val club = ClubsTable.select { ClubsTable.id eq clubId }.singleOrNull()?.let {
            Club( // Minimal club for working hours and timezone
                id = it[ClubsTable.id].value,
                name = it[ClubsTable.title],
                code = it[ClubsTable.code],
                workingHours = it[ClubsTable.workingHours],
                timezone = it[ClubsTable.timezone],
                // Fill other fields as needed or make Club nullable
                description = null, address = null, phone = null, photoUrl = null, floorPlanImageUrl = null, isActive = true, createdAt = LocalDateTime.now(), updatedAt = null
            )
        } ?: return@newSuspendedTransaction emptyList()

        val clubZoneId = try { ZoneId.of(club.timezone) } catch (e: Exception) { ZoneId.systemDefault() }
        val workingHoursPair = parseWorkingHours(club.workingHours, clubZoneId)
            ?: return@newSuspendedTransaction emptyList() // No working hours, no dates

        val todayInClubTimezone = LocalDate.now(clubZoneId)
        val startDate = if (month.isBefore(todayInClubTimezone) && month.year == todayInClubTimezone.year && month.month == todayInClubTimezone.month) {
            todayInClubTimezone // Start from today if query month is current and in past
        } else {
            month.withDayOfMonth(1)
        }
        val endDate = month.withDayOfMonth(1).plusMonths(lookAheadMonths.toLong()).minusDays(1)
        val allClubTables = getAllActiveTablesByClub(clubId)
        if (allClubTables.isEmpty()) return@newSuspendedTransaction emptyList()

        val availableDates = mutableListOf<LocalDate>()
        var currentDate = startDate
        while (!currentDate.isAfter(endDate)) {
            if (!currentDate.isBefore(todayInClubTimezone)) { // Only check from today onwards
                // Check if any table has any slot available on this date
                val hasAnyAvailability = allClubTables.any { table ->
                    getAvailableSlotsForTable(table.id, currentDate, club).isNotEmpty()
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
        val club = ClubsTable.select { ClubsTable.id eq clubId }.singleOrNull()?.let {
            Club( // Minimal club for working hours and timezone
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
            getAvailableSlotsForTable(table.id, date, club).isNotEmpty()
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

        // Adjust for bookings that might cross midnight into the club's next working day opening
        var currentSlotStartLocal = clubOpensLocal
        val nowInClubTime = ZonedDateTime.now(clubZoneId)
        val queryDateTime = date.atTime(currentSlotStartLocal)

        // Determine the end of the working period for slot generation
        // If closing time is before opening time (e.g. 18:00 - 02:00), it means it closes next day.
        val workingDayEndLocal: LocalDateTime = if (clubClosesLocal.isBefore(clubOpensLocal)) {
            date.plusDays(1).atTime(clubClosesLocal)
        } else {
            date.atTime(clubClosesLocal)
        }

        var slotStartDateTime = date.atTime(currentSlotStartLocal)

        while(true) {
            val slotEndDateTime = slotStartDateTime.plus(slotDuration)
            // Ensure the slot itself is within the working period or ends exactly at closing.
            if (slotEndDateTime.isAfter(workingDayEndLocal)) break

            // Check if current slot start is in the past for today's date
            if (date.isEqual(nowInClubTime.toLocalDate()) && slotStartDateTime.toLocalTime().isBefore(nowInClubTime.toLocalTime())) {
                slotStartDateTime = slotStartDateTime.plusMinutes(30) // Iterate to next possible slot (e.g. 30 min increment)
                continue
            }

            val periodStartWithBuffer = slotStartDateTime.minus(bufferDuration)
            val periodEndWithBuffer = slotEndDateTime.plus(bufferDuration)

            val isBooked = !bookingsRepo.isTableAvailableOnPeriod(tableId, periodStartWithBuffer, periodEndWithBuffer)

            if (!isBooked) {
                availableSlots.add("${slotStartDateTime.toLocalTime().format(DateTimeFormatter.ISO_LOCAL_TIME)}–${slotEndDateTime.toLocalTime().format(DateTimeFormatter.ISO_LOCAL_TIME)}")
            }
            // Increment for next slot, e.g., every 30 minutes or bookingSlotDurationMinutes
            slotStartDateTime = slotStartDateTime.plusMinutes(30) // Or based on a fixed increment or slotDuration
        }
        availableSlots
    }

    override suspend fun create(tableInfo: TableInfo): TableInfo = newSuspendedTransaction {
        val clubExists = ClubsTable.select { ClubsTable.id eq tableInfo.clubId }.count() > 0
        if (!clubExists) throw IllegalArgumentException("Club with id ${tableInfo.clubId} does not exist.")

        val id = TablesTable.insertAndGetId {
            it[clubId] = ClubsTable.id.entityId(tableInfo.clubId)
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