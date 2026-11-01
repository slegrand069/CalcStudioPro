package com.example.calcstudiopro.history.domain

data class HistoryEntry(
    val id: Long,
    val sourceMode: String,
    val description: String,
    val displayResult: String,
    val reusableValue: String?,
    val reusableValueType: String?,
    val createdAtEpochMillis: Long,
    val detailsJson: String? = null
)

data class NewHistoryEntry(
    val sourceMode: String,
    val description: String,
    val displayResult: String,
    val reusableValue: String?,
    val reusableValueType: String?,
    val createdAtEpochMillis: Long = System.currentTimeMillis(),
    val detailsJson: String? = null
)

interface HistoryRepository {
    val entries: kotlinx.coroutines.flow.Flow<List<HistoryEntry>>

    suspend fun add(entry: NewHistoryEntry): Long
    suspend fun delete(id: Long)
    suspend fun clear()
}

object HistoryModes {
    const val SCIENTIFIC_CALCULATOR = "scientific_calculator"
    const val CONVERTER = "converter"
    const val STATISTICS = "statistics"
}

object HistoryValueTypes {
    const val DECIMAL_DOUBLE = "decimal_double"
}
