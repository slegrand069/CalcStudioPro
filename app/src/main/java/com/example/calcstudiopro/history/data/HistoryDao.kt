package com.example.calcstudiopro.history.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
abstract class HistoryDao {
    @Query("SELECT * FROM history_entries ORDER BY createdAtEpochMillis DESC, id DESC")
    abstract fun observeEntries(): Flow<List<HistoryEntryEntity>>

    @Insert
    abstract suspend fun insert(entry: HistoryEntryEntity): Long

    @Query(
        "DELETE FROM history_entries WHERE id NOT IN " +
            "(SELECT id FROM history_entries " +
            "ORDER BY createdAtEpochMillis DESC, id DESC LIMIT :maxEntries)"
    )
    abstract suspend fun deleteEntriesBeyondLimit(maxEntries: Int): Int

    @Transaction
    open suspend fun insertAndKeepLatest(entry: HistoryEntryEntity): Long {
        val insertedId = insert(entry)
        deleteEntriesBeyondLimit(MAX_ENTRIES)
        return insertedId
    }

    @Query("DELETE FROM history_entries WHERE id = :id")
    abstract suspend fun delete(id: Long): Int

    @Query("DELETE FROM history_entries")
    abstract suspend fun clear()

    companion object {
        const val MAX_ENTRIES = 1_000
    }
}
