package com.example.calcstudiopro.statistics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.calcstudiopro.history.domain.HistoryModes
import com.example.calcstudiopro.history.domain.HistoryRepository
import com.example.calcstudiopro.history.domain.NewHistoryEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope

data class StatisticsState(
    val input: String = "",
    val result: StatisticsEngine.Result? = null,
    val error: String? = null,
    val saveError: String? = null
) { val canSave: Boolean get() = result != null }

class StatisticsViewModel(
    private val history: HistoryRepository? = null,
    private val coroutineScopeOverride: CoroutineScope? = null
) : ViewModel() {
    private val mutableState = MutableStateFlow(StatisticsState())
    val state = mutableState.asStateFlow()

    fun edit(text: String) {
        mutableState.value = when (val analysis = StatisticsEngine.analyze(text)) {
            is StatisticsEngine.Analysis.Valid -> StatisticsState(text, analysis.result)
            is StatisticsEngine.Analysis.Invalid -> StatisticsState(text, error = if (text.isBlank()) null else analysis.message)
        }
    }

    fun save() {
        val snapshot = mutableState.value
        val result = snapshot.result ?: return
        val details = """{"version":1,"values":"${escapeJson(snapshot.input)}","count":${result.count},"sum":"${result.sum}","mean":"${result.mean}","median":"${result.median}","minimum":"${result.minimum}","maximum":"${result.maximum}","populationStandardDeviation":"${result.populationStandardDeviation}"}"""
        val summary = listOf(
            "Effectif : ${result.count}",
            "Somme : ${StatisticsEngine.format(result.sum)}",
            "Moyenne : ${StatisticsEngine.format(result.mean)}",
            "Médiane : ${StatisticsEngine.format(result.median)}",
            "Minimum : ${StatisticsEngine.format(result.minimum)}",
            "Maximum : ${StatisticsEngine.format(result.maximum)}",
            "Écart-type (population) : ${StatisticsEngine.format(result.populationStandardDeviation)}"
        ).joinToString("\n")
        history?.let { repository -> (coroutineScopeOverride ?: viewModelScope).launch {
            runCatching { repository.add(NewHistoryEntry(
                sourceMode = HistoryModes.STATISTICS,
                description = "Statistiques : ${snapshot.input.trim().replace(Regex("\\s*;\\s*"), " ; ")}",
                displayResult = summary,
                reusableValue = null,
                reusableValueType = null,
                detailsJson = details
            )) }.onFailure { error -> mutableState.value = mutableState.value.copy(saveError = error.message ?: "Enregistrement impossible") }
        } }
    }

    private fun escapeJson(value: String) = buildString(value.length) {
        value.forEach { character ->
            when (character) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (character.code < 0x20) append("\\u%04x".format(character.code)) else append(character)
            }
        }
    }

    class Factory(private val history: HistoryRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(StatisticsViewModel::class.java))
            return StatisticsViewModel(history) as T
        }
    }
}
