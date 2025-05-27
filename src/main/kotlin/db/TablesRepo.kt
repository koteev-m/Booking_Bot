package db

import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.select
import java.time.LocalDate

class TablesRepoImpl(private val bookingsRepo: BookingsRepo) : TablesRepo {
    override suspend fun listByClub(clubId: Int, activeOnly: Boolean): List<TableInfo> = dbQuery {
        val query = TablesTable.select { TablesTable.clubId eq clubId }
        val filtered = if (activeOnly) query.andWhere { TablesTable.isActive eq true } else query
        filtered.map { it.toTableInfo() }
    }

    override suspend fun listAvailableByClubAndDate(clubId: Int, date: LocalDate, activeOnly: Boolean): List<TableInfo> = dbQuery {
        // Все столы клуба (учитываем activeOnly)
        val allTables = TablesTable.select { TablesTable.clubId eq clubId }
            .let { if (activeOnly) it.andWhere { TablesTable.isActive eq true } else it }
            .map { it.toTableInfo() }

        // Бронирования на выбранную дату
        val bookings = bookingsRepo.byClubAndDate(clubId, date)
            .filter { it.status == BookingStatus.NEW || it.status == BookingStatus.CONFIRMED }

        // Исключаем занятые столы
        val bookedTableIds = bookings.map { it.tableId }.toSet()
        allTables.filterNot { it.id in bookedTableIds }
    }

    override suspend fun find(tableId: Int): TableInfo? = dbQuery {
        TablesTable.select { TablesTable.id eq tableId }
            .singleOrNull()
            ?.toTableInfo()
    }

    private fun ResultRow.toTableInfo(): TableInfo = TableInfo(
        id = this[TablesTable.id].value,
        clubId = this[TablesTable.clubId].value,
        number = this[TablesTable.number],
        seats = this[TablesTable.seats],
        posX = this[TablesTable.posX],
        posY = this[TablesTable.posY],
        photoUrl = this[TablesTable.photoUrl],
        isActive = this[TablesTable.isActive],
        description = this[TablesTable.description]
    )
}