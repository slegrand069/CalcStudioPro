package com.example.calcstudiopro.history.data

import com.example.calcstudiopro.history.domain.HistoryRepository
import com.example.calcstudiopro.history.domain.NewHistoryEntry
import kotlinx.coroutines.flow.map

class RoomHistoryRepository(private val dao: HistoryDao) : HistoryRepository {
    override val entries = dao.observeEntries().map { rows -> rows.map(HistoryEntryEntity::toDomain) }

    override suspend fun add(entry: NewHistoryEntry): Long =
        dao.insertAndKeepLatest(HistoryEntryEntity.from(entry))

    override suspend fun delete(id: Long) {
        dao.delete(id)
    }

    override suspend fun clear() {
        dao.clear()
    }
}
