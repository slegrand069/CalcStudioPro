package com.example.calcstudiopro.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val isLoading: Boolean = true,
    val error: String? = null
)

class SettingsViewModel(
    private val repository: SettingsRepository
) : ViewModel() {
    private val mutableState = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.settings.collect { settings ->
                mutableState.value = SettingsUiState(settings = settings, isLoading = false)
            }
        }
    }

    fun updateContinuedFractions(settings: ContinuedFractionSettings) {
        viewModelScope.launch {
            runCatching { repository.updateContinuedFractions(settings) }
                .onFailure { error -> mutableState.value = mutableState.value.copy(error = error.message) }
        }
    }

    fun updateAngleUnit(angleUnit: com.example.calcstudiopro.domain.AngleUnit) {
        viewModelScope.launch {
            runCatching { repository.updateAngleUnit(angleUnit) }
                .onFailure { error -> mutableState.value = mutableState.value.copy(error = error.message) }
        }
    }

    fun updateNotation(notation: CalculatorNotation) {
        viewModelScope.launch {
            runCatching { repository.updateNotation(notation) }
                .onFailure { error -> mutableState.value = mutableState.value.copy(error = error.message) }
        }
    }

    class Factory(private val repository: SettingsRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(SettingsViewModel::class.java))
            return SettingsViewModel(repository) as T
        }
    }
}
