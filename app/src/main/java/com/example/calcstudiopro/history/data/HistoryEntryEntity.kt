package com.example.calcstudiopro.history.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.calcstudiopro.history.domain.HistoryEntry
import com.example.calcstudiopro.history.domain.NewHistoryEntry

@Entity(tableName = "history_entries")
data class HistoryEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourceMode: String,
    val description: String,
    val displayResult: String,
    val reusableValue: String?,
    val reusableValueType: String?,
    val createdAtEpochMillis: Long,
    val detailsJson: String?
) {
    fun toDomain() = HistoryEntry(
        id = id,
        sourceMode = sourceMode,
        description = description,
        displayResult = displayResult,
        reusableValue = reusableValue,
        reusableValueType = reusableValueType,
        createdAtEpochMillis = createdAtEpochMillis,
        detailsJson = detailsJson
    )

    companion object {
        fun from(entry: NewHistoryEntry) = HistoryEntryEntity(
            sourceMode = entry.sourceMode,
            description = entry.description,
            displayResult = entry.displayResult,
            reusableValue = entry.reusableValue,
            reusableValueType = entry.reusableValueType,
            createdAtEpochMillis = entry.createdAtEpochMillis,
            detailsJson = entry.detailsJson
        )
    }
}
