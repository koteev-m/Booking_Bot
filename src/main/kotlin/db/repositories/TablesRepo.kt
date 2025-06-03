package db.repositories

import db.TableInfo // From db.Entities
import db.Club      // From db.Entities
import java.time.LocalDate

interface TablesRepo {
    /**
     * Вернуть все активные столы (TableInfo) для заданного клуба.
     */
    suspend fun getAllActiveTablesByClub(clubId: Int): List<TableInfo>

    /**
     * Вернуть информацию о конкретном столе по его ID.
     */
    suspend fun findById(id: Int): TableInfo?

    /**
     * Получить доступные даты для бронирования в клубе.
     * (Logic for this might be complex, involving checking existing bookings)
     */
    suspend fun getAvailableDatesForClub(clubId: Int, month: LocalDate, lookAheadMonths: Int): List<LocalDate>

    /**
     * Получить доступные столы в клубе на конкретную дату.
     */
    suspend fun getAvailableTables(clubId: Int, date: LocalDate): List<TableInfo>

    /**
     * Получить доступные временные слоты для стола на конкретную дату.
     * (This requires business logic for slot generation and checking against bookings)
     */
    suspend fun getAvailableSlotsForTable(tableId: Int, date: LocalDate, club: Club): List<String>

    // Method for RepositoriesTest
    suspend fun create(tableInfo: TableInfo): TableInfo
}