package com.example.calcstudiopro.geometry

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.calcstudiopro.history.domain.HistoryModes
import com.example.calcstudiopro.history.domain.HistoryRepository
import com.example.calcstudiopro.history.domain.NewHistoryEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class GeometryState(
    val aText: String = "5", val bText: String = "6", val cText: String = "7",
    val result: TriangleEngine.Result? = TriangleEngine.calculate(5.0, 6.0, 7.0),
    val error: String? = null, val saveError: String? = null, val saved: Boolean = false
) { val canSave get() = result != null }

class GeometryViewModel(private val history: HistoryRepository? = null) : ViewModel() {
    private val mutableState = MutableStateFlow(GeometryState())
    val state = mutableState.asStateFlow()

    fun editA(text: String) = edit(text, 0)
    fun editB(text: String) = edit(text, 1)
    fun editC(text: String) = edit(text, 2)
    private fun edit(text: String, index: Int) {
        val old = mutableState.value
        val texts = listOf(old.aText, old.bText, old.cText).toMutableList().also { it[index] = text }
        val parsed = texts.map { it.trim().replace(',', '.').toDoubleOrNull() }
        val result = if (parsed.all { it != null }) runCatching {
            TriangleEngine.calculate(parsed[0]!!, parsed[1]!!, parsed[2]!!)
        }.getOrNull() else null
        val message = when {
            text.trim().isEmpty() || text.trim() in setOf("-", "+", ".", ",", "-.", "+.") -> null
            result == null -> "Saisie invalide ou triangle impossible."
            else -> null
        }
        mutableState.value = GeometryState(texts[0], texts[1], texts[2], result, message)
    }

    fun save() {
        val snapshot = mutableState.value
        val r = snapshot.result ?: return
        val details = """{"version":1,"sides":{"a":${jsonNumber(r.a)},"b":${jsonNumber(r.b)},"c":${jsonNumber(r.c)}},"perimeter":${jsonNumber(r.perimeter)},"area":${jsonNumber(r.area)},"angles":{"A":${jsonNumber(r.angleA)},"B":${jsonNumber(r.angleB)},"C":${jsonNumber(r.angleC)}},"heights":{"a":${jsonNumber(r.heightA)},"b":${jsonNumber(r.heightB)},"c":${jsonNumber(r.heightC)}},"nature":"${r.nature}"}"""
        val repo = history ?: return
        viewModelScope.launch {
            runCatching { repo.add(NewHistoryEntry(
                sourceMode = HistoryModes.GEOMETRY,
                description = "Triangle : a=${snapshot.aText}, b=${snapshot.bText}, c=${snapshot.cText}",
                displayResult = "${r.nature} — P=${TriangleEngine.format(r.perimeter)}, S=${TriangleEngine.format(r.area)} unités²",
                reusableValue = null, reusableValueType = null, detailsJson = details
            )) }.onSuccess { mutableState.value = mutableState.value.copy(saved = true, saveError = null) }
                .onFailure { mutableState.value = mutableState.value.copy(saved = false, saveError = it.message ?: "Enregistrement impossible") }
        }
    }

    private fun jsonNumber(value: Double) = java.math.BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()

    class Factory(private val history: HistoryRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(GeometryViewModel::class.java)); return GeometryViewModel(history) as T
        }
    }
}
