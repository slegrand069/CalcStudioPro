package com.example.calcstudiopro.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.calcstudiopro.history.domain.HistoryEntry
import com.example.calcstudiopro.history.domain.HistoryModes
import com.example.calcstudiopro.history.domain.HistoryRepository
import com.example.calcstudiopro.history.domain.HistoryValueTypes
import com.example.calcstudiopro.history.domain.NewHistoryEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class HistoryUiState(
    val entries: List<HistoryEntry> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null
)

class HistoryViewModel(private val repository: HistoryRepository) : ViewModel() {
    private val mutableState = MutableStateFlow(HistoryUiState())
    val state: StateFlow<HistoryUiState> = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.entries.collect { entries ->
                mutableState.value = mutableState.value.copy(entries = entries, isLoading = false)
            }
        }
    }

    fun recordScientificCalculation(expression: String, displayResult: String, value: Double) {
        val canonicalValue = value.takeIf(Double::isFinite)?.toString() ?: return
        viewModelScope.launch {
            runCatching {
                repository.add(
                    NewHistoryEntry(
                        sourceMode = HistoryModes.SCIENTIFIC_CALCULATOR,
                        description = expression,
                        displayResult = displayResult,
                        reusableValue = canonicalValue,
                        reusableValueType = HistoryValueTypes.DECIMAL_DOUBLE
                    )
                )
            }.onFailure(::showError)
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch {
            runCatching { repository.delete(id) }.onFailure(::showError)
        }
    }

    fun clear() {
        viewModelScope.launch {
            runCatching { repository.clear() }.onFailure(::showError)
        }
    }

    private fun showError(error: Throwable) {
        mutableState.value = mutableState.value.copy(
            errorMessage = error.message ?: "Impossible de modifier l’historique"
        )
    }

    class Factory(private val repository: HistoryRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(HistoryViewModel::class.java))
            return HistoryViewModel(repository) as T
        }
    }
}
