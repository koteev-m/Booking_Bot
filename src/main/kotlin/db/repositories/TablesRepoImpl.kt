package db.repositories

import db.TableInfo
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction

/**
 * Интерфейс для работы с таблицей столов (TableInfo).
 */
interface TablesRepo {
    /**
     * Вернуть все столы (TableInfo) для заданного клуба.
     */
    suspend fun getAllTablesByClub(clubId: Int): List<TableInfo>

    /**
     * Вернуть информацию о конкретном столе по его ID.
     */
    suspend fun findById(id: Int): TableInfo?
}

/**
 * Реализация [TablesRepo], использует объект [TablesTable] (см. Tables.kt).
 */
class TablesRepoImpl : TablesRepo {

    override suspend fun getAllTablesByClub(clubId: Int): List<TableInfo> = transaction {
        TablesTable
            .select { TablesTable.clubId eq clubId }
            .map { row ->
                TableInfo(
                    id         = row[TablesTable.id].value,
                    clubId     = row[TablesTable.clubId].value,
                    name       = row[TablesTable.name],
                    capacity   = row[TablesTable.capacity],
                    isActive   = row[TablesTable.isActive],
                    createdAt  = row[TablesTable.createdAt].toLocalDateTime(),
                    updatedAt  = row[TablesTable.updatedAt]?.toLocalDateTime()
                )
            }
    }

    override suspend fun findById(id: Int): TableInfo? = transaction {
        TablesTable
            .select { TablesTable.id eq id }
            .mapNotNull { row ->
                TableInfo(
                    id         = row[TablesTable.id].value,
                    clubId     = row[TablesTable.clubId].value,
                    name       = row[TablesTable.name],
                    capacity   = row[TablesTable.capacity],
                    isActive   = row[TablesTable.isActive],
                    createdAt  = row[TablesTable.createdAt].toLocalDateTime(),
                    updatedAt  = row[TablesTable.updatedAt]?.toLocalDateTime()
                )
            }
            .singleOrNull()
    }
}