package com.example.calcstudiopro.converter

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.calcstudiopro.history.domain.HistoryModes
import com.example.calcstudiopro.history.domain.HistoryRepository
import com.example.calcstudiopro.history.domain.NewHistoryEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class ActiveConverterField { SOURCE, TARGET }
data class ConverterState(
    val category: UnitCategory = UnitCategory.LENGTH,
    val sourceUnitId: String = "mm", val targetUnitId: String = "cm",
    val sourceText: String = "", val targetText: String = "",
    val activeField: ActiveConverterField = ActiveConverterField.SOURCE,
    val error: String? = null,
    val convertedValue: Double? = null
) {
    val canSave get() = convertedValue != null && error == null
}

class ConverterViewModel(private val history: HistoryRepository? = null) : ViewModel() {
    private val mutableState = MutableStateFlow(ConverterState())
    val state = mutableState.asStateFlow()

    fun selectCategory(category: UnitCategory) {
        val defaults = UnitCatalog.defaults(category)
        mutableState.value = ConverterState(category, defaults[0].id, defaults[1].id)
    }
    fun editSource(text: String) = edit(text, ActiveConverterField.SOURCE)
    fun editTarget(text: String) = edit(text, ActiveConverterField.TARGET)
    private fun edit(text: String, field: ActiveConverterField) {
        val old = mutableState.value
        val source = UnitCatalog.get(old.sourceUnitId)!!
        val target = UnitCatalog.get(old.targetUnitId)!!
        val input = ConversionEngine.parse(text)
        if (input == null) {
            mutableState.value = old.copy(
                sourceText = if (field == ActiveConverterField.SOURCE) text else old.sourceText,
                targetText = if (field == ActiveConverterField.TARGET) text else old.targetText,
                activeField = field, convertedValue = null, error = if (text.isBlank() || text.trim() in setOf("-", "+", ".", ",", "-.", "+.")) null else "Saisie invalide"
            ); return
        }
        runCatching {
            if (field == ActiveConverterField.SOURCE) ConversionEngine.convert(input, source, target)
            else ConversionEngine.convert(input, target, source)
        }.onSuccess { result ->
            mutableState.value = old.copy(
                sourceText = if (field == ActiveConverterField.SOURCE) text else ConversionFormatter.format(result),
                targetText = if (field == ActiveConverterField.TARGET) text else ConversionFormatter.format(result),
                activeField = field, convertedValue = result, error = null
            )
        }.onFailure { e -> mutableState.value = old.copy(
            sourceText = if (field == ActiveConverterField.SOURCE) text else old.sourceText,
            targetText = if (field == ActiveConverterField.TARGET) text else old.targetText,
            activeField = field, convertedValue = null, error = e.message ?: "Conversion impossible"
        ) }
    }
    fun selectUnit(isSource: Boolean, unitId: String) {
        val old = mutableState.value
        val unit = UnitCatalog.get(unitId) ?: return
        if (unit.category != old.category) return
        val source = if (isSource) unitId else old.sourceUnitId
        val target = if (isSource) old.targetUnitId else unitId
        val active = old.activeField
        mutableState.value = old.copy(sourceUnitId = source, targetUnitId = target)
        val current = mutableState.value
        val text = if (active == ActiveConverterField.SOURCE) current.sourceText else current.targetText
        val value = ConversionEngine.parse(text)
        if (value != null) edit(text, active) else mutableState.value = current.copy(targetText = "", convertedValue = null, error = null)
    }
    fun invert() {
        val s = mutableState.value
        val active = if (s.activeField == ActiveConverterField.SOURCE) ActiveConverterField.TARGET else ActiveConverterField.SOURCE
        mutableState.value = s.copy(sourceUnitId = s.targetUnitId, targetUnitId = s.sourceUnitId,
            sourceText = s.targetText, targetText = s.sourceText, activeField = active)
    }
    fun save() {
        val s = mutableState.value
        val value = s.convertedValue ?: return
        val from = UnitCatalog.get(if (s.activeField == ActiveConverterField.SOURCE) s.sourceUnitId else s.targetUnitId) ?: return
        val to = UnitCatalog.get(if (s.activeField == ActiveConverterField.SOURCE) s.targetUnitId else s.sourceUnitId) ?: return
        val inputText = if (s.activeField == ActiveConverterField.SOURCE) s.sourceText else s.targetText
        val input = ConversionEngine.parse(inputText) ?: return
        val result = if (s.activeField == ActiveConverterField.SOURCE) value else value
        val display = ConversionFormatter.format(result)
        val details = """{"version":1,"category":"${s.category.id}","sourceUnitId":"${from.id}","targetUnitId":"${to.id}","sourceValue":"$inputText","result":"$display"}"""
        history?.let { repo -> viewModelScope.launch { repo.add(NewHistoryEntry(
            sourceMode = HistoryModes.CONVERTER, description = "${inputText} ${from.symbol} → ${to.symbol}",
            displayResult = "$display ${to.symbol}", reusableValue = null, reusableValueType = null, detailsJson = details
        )) } }
    }
    class Factory(private val history: HistoryRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(ConverterViewModel::class.java)); return ConverterViewModel(history) as T
        }
    }
}
