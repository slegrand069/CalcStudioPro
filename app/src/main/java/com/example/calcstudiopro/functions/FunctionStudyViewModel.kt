package com.example.calcstudiopro.functions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.calcstudiopro.domain.functions.MathExpressionEngine
import com.example.calcstudiopro.functions.domain.FunctionColor
import com.example.calcstudiopro.functions.domain.FunctionSlotId
import com.example.calcstudiopro.functions.domain.FunctionStudy
import com.example.calcstudiopro.functions.domain.FunctionStudyRevision
import com.example.calcstudiopro.functions.persistence.FunctionStudyStore
import com.example.calcstudiopro.functions.persistence.StudyStoreError
import com.example.calcstudiopro.functions.persistence.StudyStoreResult
import com.example.calcstudiopro.functions.domain.StudyValidationResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class FunctionStudyUiState(
    val study: FunctionStudy? = null,
    val loadError: String? = null,
    val saveError: String? = null
)

/** A1 state holder. Only the working session is autosaved; named studies are untouched here. */
class FunctionStudyViewModel(
    private val store: FunctionStudyStore,
    private val engine: MathExpressionEngine = MathExpressionEngine()
) : ViewModel() {
    private var savedRevision: FunctionStudyRevision? = null

    private val mutableState = MutableStateFlow(loadInitialState())
    val state = mutableState.asStateFlow()

    private fun loadInitialState(): FunctionStudyUiState = when (val result = store.loadWorkingSession()) {
        is StudyStoreResult.Failure -> FunctionStudyUiState(loadError = loadErrorMessage(result.error))
        is StudyStoreResult.Success -> {
            val study = result.value ?: FunctionStudy.newWorkingSession()
            savedRevision = result.value?.revision()
            FunctionStudyUiState(study = study)
        }
    }

    fun chooseColor(slotId: FunctionSlotId, color: FunctionColor) {
        update { it.withColor(slotId, color) }
    }

    fun toggleVisibility(slotId: FunctionSlotId) {
        update { study ->
            val slot = study.slot(slotId)
            study.withVisibility(slotId, !slot.isVisible)
        }
    }

    fun updateDraft(slotId: FunctionSlotId, draft: String) {
        update { it.withDraft(slotId, draft) }
    }

    fun validateDraft(slotId: FunctionSlotId): StudyValidationResult? {
        val study = mutableState.value.study ?: return null
        return when (val result = study.validateFunction(slotId, engine)) {
            is StudyValidationResult.Accepted -> {
                saveStudy(result.study)
                result
            }
            is StudyValidationResult.Rejected -> result
        }
    }

    private inline fun update(change: (FunctionStudy) -> FunctionStudy) {
        val current = mutableState.value
        val study = current.study ?: return
        val updated = change(study)
        val revision = updated.revision()
        if (revision == study.revision()) return

        saveStudy(updated)
    }

    private fun saveStudy(updated: FunctionStudy) {
        val current = mutableState.value
        val revision = updated.revision()
        mutableState.value = current.copy(study = updated, saveError = null)
        when (val result = store.saveWorkingSession(updated, savedRevision)) {
            is StudyStoreResult.Success -> {
                savedRevision = revision
                if (mutableState.value.study?.revision() == revision) {
                    mutableState.value = mutableState.value.copy(saveError = null)
                }
            }
            is StudyStoreResult.Failure -> {
                mutableState.value = mutableState.value.copy(saveError = saveErrorMessage(result.error))
            }
        }
    }

    private fun loadErrorMessage(error: StudyStoreError): String = when (error) {
        StudyStoreError.INCOMPATIBLE_VERSION -> "La session utilise un format incompatible. Les données enregistrées sont conservées."
        StudyStoreError.FILE_TOO_LARGE -> "Le fichier de session dépasse la taille autorisée. Les données enregistrées sont conservées."
        else -> "La session n’a pas pu être chargée. Les données enregistrées sont conservées."
    }

    private fun saveErrorMessage(error: StudyStoreError): String = when (error) {
        StudyStoreError.CONCURRENT_MODIFICATION -> "La session a changé ailleurs. Vos modifications n’ont pas remplacé les données enregistrées."
        else -> "La session n’a pas pu être sauvegardée. La dernière version enregistrée est conservée."
    }

    class Factory(private val store: FunctionStudyStore) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(FunctionStudyViewModel::class.java))
            return FunctionStudyViewModel(store) as T
        }
    }
}
