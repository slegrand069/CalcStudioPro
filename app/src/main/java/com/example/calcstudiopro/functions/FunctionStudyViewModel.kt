package com.example.calcstudiopro.functions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.calcstudiopro.domain.functions.MathExpressionEngine
import com.example.calcstudiopro.domain.functions.FunctionAngleMode
import com.example.calcstudiopro.functions.domain.FunctionColor
import com.example.calcstudiopro.functions.domain.FunctionSlotId
import com.example.calcstudiopro.functions.domain.FunctionStudy
import com.example.calcstudiopro.functions.domain.StudyParameterUpdate
import com.example.calcstudiopro.functions.domain.FunctionStudyId
import com.example.calcstudiopro.functions.domain.FunctionStudyKind
import com.example.calcstudiopro.functions.domain.FunctionStudyRevision
import com.example.calcstudiopro.functions.domain.StudyValidationResult
import com.example.calcstudiopro.functions.persistence.FunctionStudyStore
import com.example.calcstudiopro.functions.persistence.NamedStudySummary
import com.example.calcstudiopro.functions.persistence.StudyStoreError
import com.example.calcstudiopro.functions.persistence.StudyStoreResult
import com.example.calcstudiopro.functions.table.FunctionValueTableSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface StudyManagementResult {
    data object Completed : StudyManagementResult
    data object RequiresName : StudyManagementResult
    data object RequiresDiscardConfirmation : StudyManagementResult
    data object RequiresRecoveryReplacementConfirmation : StudyManagementResult
    data object RequiresDeleteConfirmation : StudyManagementResult
    data object RecoveryUnavailable : StudyManagementResult
    data class Failed(val message: String) : StudyManagementResult
}

data class FunctionStudyUiState(
    val study: FunctionStudy? = null,
    val loadError: String? = null,
    val saveError: String? = null,
    val operationError: String? = null,
    val namedStudies: List<NamedStudySummary> = emptyList(),
    val recoveryAvailable: Boolean = false,
    val recoveryPreview: FunctionStudy? = null,
    val savedReference: FunctionStudyRevision? = null
) {
    val hasUnsavedChanges: Boolean
        get() = study != null && savedReference != null && study.hasUnsavedChangesSince(savedReference)

    val displayName: String
        get() = study?.name ?: "Session de travail"
}

/** Owns the active working copy. Named records are changed only by explicit management actions. */
class FunctionStudyViewModel(
    private val store: FunctionStudyStore,
    private val engine: MathExpressionEngine = MathExpressionEngine()
) : ViewModel() {
    private var workingFileRevision: FunctionStudyRevision? = null

    private val mutableState = MutableStateFlow(loadInitialState())
    val state = mutableState.asStateFlow()

    private fun loadInitialState(): FunctionStudyUiState = when (val result = store.loadWorkingSession()) {
        is StudyStoreResult.Failure -> FunctionStudyUiState(loadError = loadErrorMessage(result.error))
        is StudyStoreResult.Success -> {
            val existing = result.value
            val study = existing ?: FunctionStudy.newWorkingSession()
            val revision = study.revision()
            var saveError: String? = null
            if (existing == null) {
                when (val save = store.saveWorkingSession(study)) {
                    is StudyStoreResult.Success -> workingFileRevision = revision
                    is StudyStoreResult.Failure -> saveError = saveErrorMessage(save.error)
                }
            } else {
                workingFileRevision = revision
            }
            val baseline = referenceFor(study)
            val namedResult = store.listNamedStudies()
            val recoveryResult = store.loadRecoverySession()
            val names = (namedResult as? StudyStoreResult.Success)?.value ?: emptyList()
            val recovery = (recoveryResult as? StudyStoreResult.Success)?.value != null
            val managementError = (namedResult as? StudyStoreResult.Failure)?.let { storeErrorMessage(it.error) }
                ?: (recoveryResult as? StudyStoreResult.Failure)?.let { storeErrorMessage(it.error) }
            FunctionStudyUiState(
                study = study,
                saveError = saveError,
                namedStudies = names,
                recoveryAvailable = recovery,
                savedReference = baseline,
                operationError = managementError
            )
        }
    }

    fun chooseColor(slotId: FunctionSlotId, color: FunctionColor) = update { it.withColor(slotId, color) }

    fun toggleVisibility(slotId: FunctionSlotId) = update { study ->
        val slot = study.slot(slotId)
        study.withVisibility(slotId, !slot.isVisible)
    }

    fun updateDraft(slotId: FunctionSlotId, draft: String) = update { it.withDraft(slotId, draft) }

    /** Persists the validated value-table configuration as part of the active study. */
    fun updateValueTableSettings(settings: FunctionValueTableSettings): Boolean {
        if (settings.validationError() != null) return false
        val study = mutableState.value.study ?: return false
        if (settings.selectedSlots.any { selected -> study.slot(selected).validatedExpression == null }) return false
        saveStudy(study.withValueTableSettings(settings))
        return mutableState.value.saveError == null
    }

    /** Updates an explicit shared parameter without recompiling any validated expression. */
    fun setParameter(name: Char, value: Double): Boolean {
        val study = mutableState.value.study ?: return false
        val update = study.withParameter(name, value) as? com.example.calcstudiopro.functions.domain.StudyParameterUpdate.Updated
            ?: return false
        saveStudy(update.study)
        return true
    }

    /** Updates slider state immediately; persistence is deferred until the gesture settles. */
    fun setGraphParameter(name: Char, value: Double): Boolean {
        val currentState = mutableState.value
        val study = currentState.study ?: return false
        val updated = (study.withParameter(name, value) as? StudyParameterUpdate.Updated)?.study ?: return false
        if (updated.revision() == study.revision()) return true
        mutableState.value = currentState.copy(study = updated, saveError = null, operationError = null)
        return true
    }

    /** Persists only the final slider value, avoiding a synchronous file write for each drag frame. */
    fun commitGraphParameterChanges(): Boolean {
        val study = mutableState.value.study ?: return false
        if (study.revision() == workingFileRevision) return true
        saveStudy(study)
        return mutableState.value.saveError == null
    }

    fun resetParameter(name: Char): Boolean {
        val study = mutableState.value.study ?: return false
        val update = study.resetParameter(name) as? com.example.calcstudiopro.functions.domain.StudyParameterUpdate.Updated
            ?: return false
        saveStudy(update.study)
        return true
    }

    /** Angle mode belongs to the function study and is independent of calculator settings. */
    fun setAngleMode(mode: FunctionAngleMode) = update { it.withAngleMode(mode) }

    /** Applies the graph's parameter and angle controls as one working-session transition. */
    fun updateGraphSettings(values: Map<Char, Double>, mode: FunctionAngleMode): Boolean {
        val current = mutableState.value.study ?: return false
        var updated = current
        for ((name, value) in values) {
            updated = (updated.withParameter(name, value) as? StudyParameterUpdate.Updated)?.study ?: return false
        }
        updated = updated.withAngleMode(mode)
        if (updated.revision() == current.revision()) return true
        saveStudy(updated)
        return mutableState.value.saveError == null
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

    fun refreshManagementInfo() {
        val named = store.listNamedStudies()
        val recovery = store.loadRecoverySession()
        val current = mutableState.value
        val namedSuccess = named as? StudyStoreResult.Success
        val recoverySuccess = recovery as? StudyStoreResult.Success
        mutableState.value = current.copy(
            namedStudies = namedSuccess?.value ?: current.namedStudies,
            recoveryAvailable = recoverySuccess?.value?.let { true } ?: if (recovery is StudyStoreResult.Success) false else current.recoveryAvailable,
            operationError = (named as? StudyStoreResult.Failure)?.let { storeErrorMessage(it.error) }
                ?: (recovery as? StudyStoreResult.Failure)?.let { storeErrorMessage(it.error) }
        )
    }

    fun saveCurrent(nameIfNeeded: String? = null): StudyManagementResult {
        val current = mutableState.value.study ?: return failed("Aucune étude active.")
        val reference = mutableState.value.savedReference
        val associated = current.name != null && reference?.studyId == current.id
        if (!associated) {
            if (nameIfNeeded.isNullOrBlank()) return StudyManagementResult.RequiresName
            return saveAs(nameIfNeeded)
        }
        val named = current.asNamedStudy(current.name!!, current.id)
        return when (val saved = store.updateNamedStudy(named, reference!!.withKind(FunctionStudyKind.NAMED_STUDY, current.name))) {
            is StudyStoreResult.Failure -> failed(storeErrorMessage(saved.error))
            is StudyStoreResult.Success -> {
                val normalized = named.asWorkingSession().revision()
                mutableState.value = mutableState.value.copy(savedReference = normalized, operationError = null)
                refreshManagementInfo()
                StudyManagementResult.Completed
            }
        }
    }

    fun saveAs(name: String): StudyManagementResult {
        if (name.isBlank()) return failed("Le nom de l’étude ne peut pas être vide.")
        val current = mutableState.value.study ?: return failed("Aucune étude active.")
        val named = current.asNamedStudy(name.trim(), FunctionStudyId.new())
        when (val created = store.createNamedStudy(named)) {
            is StudyStoreResult.Failure -> return failed(storeErrorMessage(created.error))
            is StudyStoreResult.Success -> Unit
        }
        val working = named.asWorkingSession()
        val transition = persistWorkingTransition(working)
        if (transition != null) return failed("L’étude a été enregistrée, mais la session active n’a pas pu être actualisée. ${storeErrorMessage(transition)}")
        mutableState.value = mutableState.value.copy(
            study = working,
            savedReference = working.revision(),
            operationError = null
        )
        refreshManagementInfo()
        return StudyManagementResult.Completed
    }

    fun createNewStudy(
        confirmDiscard: Boolean = false,
        replaceRecovery: Boolean = false
    ): StudyManagementResult {
        val current = mutableState.value.study ?: return failed("Aucune session active.")
        if (mutableState.value.hasUnsavedChanges && !confirmDiscard) return StudyManagementResult.RequiresDiscardConfirmation
        val next = FunctionStudy.newWorkingSession()
        return transitionTo(next, confirmDiscard, replaceRecovery)
    }

    fun openNamedStudy(
        id: FunctionStudyId,
        confirmDiscard: Boolean = false,
        replaceRecovery: Boolean = false
    ): StudyManagementResult {
        val named = when (val loaded = store.loadNamedStudy(id)) {
            is StudyStoreResult.Failure -> return failed(storeErrorMessage(loaded.error))
            is StudyStoreResult.Success -> loaded.value
        }
        val current = mutableState.value.study
        val requestedWorking = named.asWorkingSession()
        if (current?.revision() == requestedWorking.revision()) return StudyManagementResult.Completed
        if (mutableState.value.hasUnsavedChanges && !confirmDiscard) return StudyManagementResult.RequiresDiscardConfirmation
        return transitionTo(requestedWorking, confirmDiscard, replaceRecovery)
    }

    fun renameCurrentStudy(newName: String): StudyManagementResult {
        if (newName.isBlank()) return failed("Le nom de l’étude ne peut pas être vide.")
        val current = mutableState.value.study ?: return failed("Aucune étude active.")
        val reference = mutableState.value.savedReference
        if (current.name == null || reference?.studyId != current.id) {
            return StudyManagementResult.RequiresName
        }
        return when (val renamed = store.renameNamedStudy(
            current.id,
            newName.trim(),
            reference!!.withKind(FunctionStudyKind.NAMED_STUDY, current.name)
        )) {
            is StudyStoreResult.Failure -> failed(storeErrorMessage(renamed.error))
            is StudyStoreResult.Success -> {
                val updated = current.asWorkingSession(name = newName.trim())
                val saved = persistWorkingTransition(updated)
                if (saved != null) return failed(storeErrorMessage(saved))
                val newReference = reference!!.withKind(FunctionStudyKind.NAMED_STUDY, newName.trim())
                    .withKind(FunctionStudyKind.WORKING_SESSION, newName.trim())
                mutableState.value = mutableState.value.copy(study = updated, savedReference = newReference, operationError = null)
                refreshManagementInfo()
                StudyManagementResult.Completed
            }
        }
    }

    fun deleteCurrentStudy(
        confirmDelete: Boolean = false,
        confirmDiscard: Boolean = false,
        replaceRecovery: Boolean = false
    ): StudyManagementResult {
        val current = mutableState.value.study ?: return failed("Aucune étude active.")
        val reference = mutableState.value.savedReference
        if (current.name == null || reference?.studyId != current.id) {
            return StudyManagementResult.Failed("La session de travail ne peut pas être supprimée comme une étude nommée.")
        }
        if (!confirmDelete) return StudyManagementResult.RequiresDeleteConfirmation
        if (mutableState.value.hasUnsavedChanges && !confirmDiscard) return StudyManagementResult.RequiresDiscardConfirmation
        if (mutableState.value.hasUnsavedChanges) {
            val recoveryResult = preserveCurrent(replaceRecovery)
            if (recoveryResult != null) return recoveryResult
        }
        when (val deleted = store.deleteNamedStudy(
            current.id,
            reference!!.withKind(FunctionStudyKind.NAMED_STUDY, current.name)
        )) {
            is StudyStoreResult.Failure -> return failed(storeErrorMessage(deleted.error))
            is StudyStoreResult.Success -> Unit
        }
        val fresh = FunctionStudy.newWorkingSession()
        val saveError = persistWorkingTransition(fresh)
        if (saveError != null) return failed("L’étude a été supprimée. ${storeErrorMessage(saveError)}")
        mutableState.value = mutableState.value.copy(study = fresh, savedReference = fresh.revision(), operationError = null)
        refreshManagementInfo()
        return StudyManagementResult.Completed
    }

    fun inspectRecovery(): StudyManagementResult {
        return when (val loaded = store.loadRecoverySession()) {
            is StudyStoreResult.Failure -> failed(storeErrorMessage(loaded.error))
            is StudyStoreResult.Success -> {
                val preview = loaded.value
                mutableState.value = mutableState.value.copy(recoveryPreview = preview)
                if (preview == null) StudyManagementResult.RecoveryUnavailable else StudyManagementResult.Completed
            }
        }
    }

    fun restoreRecovery(
        confirmDiscard: Boolean = false,
        replaceRecovery: Boolean = false
    ): StudyManagementResult {
        val recovery = when (val loaded = store.loadRecoverySession()) {
            is StudyStoreResult.Failure -> return failed(storeErrorMessage(loaded.error))
            is StudyStoreResult.Success -> loaded.value ?: return StudyManagementResult.RecoveryUnavailable
        }
        if (mutableState.value.hasUnsavedChanges && !confirmDiscard) return StudyManagementResult.RequiresDiscardConfirmation
        return transitionTo(recovery.asWorkingSession(), confirmDiscard, replaceRecovery)
    }

    private fun transitionTo(
        next: FunctionStudy,
        confirmDiscard: Boolean,
        replaceRecovery: Boolean
    ): StudyManagementResult {
        val dirty = mutableState.value.hasUnsavedChanges
        if (dirty && !confirmDiscard) return StudyManagementResult.RequiresDiscardConfirmation
        if (dirty) {
            val recoveryResult = preserveCurrent(replaceRecovery)
            if (recoveryResult != null) return recoveryResult
        }
        val writeError = persistWorkingTransition(next)
        if (writeError != null) return failed(storeErrorMessage(writeError))
        val baseline = referenceFor(next)
        mutableState.value = mutableState.value.copy(
            study = next,
            savedReference = baseline,
            recoveryPreview = null,
            operationError = null,
            saveError = null
        )
        refreshManagementInfo()
        return StudyManagementResult.Completed
    }

    private fun preserveCurrent(replaceRecovery: Boolean): StudyManagementResult? {
        val current = mutableState.value.study ?: return null
        val existing = when (val loaded = store.loadRecoverySession()) {
            is StudyStoreResult.Failure -> return failed(storeErrorMessage(loaded.error))
            is StudyStoreResult.Success -> loaded.value
        }
        if (existing != null && !replaceRecovery) return StudyManagementResult.RequiresRecoveryReplacementConfirmation
        return when (val saved = store.saveRecoverySession(
            current.asRecoverySession(),
            allowReplace = existing != null,
            expectedRevision = existing?.revision()
        )) {
            is StudyStoreResult.Failure -> failed(storeErrorMessage(saved.error))
            is StudyStoreResult.Success -> null
        }
    }

    private fun persistWorkingTransition(study: FunctionStudy): StudyStoreError? =
        when (val saved = store.saveWorkingSession(study, workingFileRevision)) {
            is StudyStoreResult.Failure -> saved.error
            is StudyStoreResult.Success -> {
                workingFileRevision = study.revision()
                null
            }
        }

    private fun matchingNamedBaseline(study: FunctionStudy): FunctionStudyRevision? {
        if (study.name == null) return null
        return when (val loaded = store.loadNamedStudy(study.id)) {
            is StudyStoreResult.Success -> loaded.value.asWorkingSession(name = loaded.value.name).revision()
            is StudyStoreResult.Failure -> null
        }
    }

    private fun referenceFor(study: FunctionStudy): FunctionStudyRevision =
        matchingNamedBaseline(study)
            ?: FunctionStudy.newWorkingSession(study.id).revision()

    private inline fun update(change: (FunctionStudy) -> FunctionStudy) {
        val current = mutableState.value
        val study = current.study ?: return
        val updated = change(study)
        if (updated.revision() == study.revision()) return
        saveStudy(updated)
    }

    private fun saveStudy(updated: FunctionStudy) {
        val current = mutableState.value
        mutableState.value = current.copy(study = updated, saveError = null, operationError = null)
        when (val result = store.saveWorkingSession(updated, workingFileRevision)) {
            is StudyStoreResult.Success -> workingFileRevision = updated.revision()
            is StudyStoreResult.Failure -> mutableState.value = mutableState.value.copy(saveError = saveErrorMessage(result.error))
        }
    }

    private fun failed(message: String): StudyManagementResult.Failed {
        mutableState.value = mutableState.value.copy(operationError = message)
        return StudyManagementResult.Failed(message)
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

    private fun storeErrorMessage(error: StudyStoreError): String = when (error) {
        StudyStoreError.CONCURRENT_MODIFICATION -> "Conflit de révision : l’étude a changé depuis son chargement."
        StudyStoreError.NOT_FOUND -> "L’étude demandée n’existe plus."
        StudyStoreError.RECOVERY_ALREADY_EXISTS -> "Une session précédente existe déjà. Confirmez son remplacement."
        StudyStoreError.DUPLICATE_ID -> "Une étude avec cet identifiant existe déjà."
        StudyStoreError.STUDY_ID_MISMATCH -> "L’identifiant de l’étude ne correspond pas aux données enregistrées."
        StudyStoreError.INCOMPATIBLE_VERSION -> "Le format de l’étude n’est pas compatible. Les données sont conservées."
        StudyStoreError.CORRUPT_DATA -> "Les données de l’étude sont corrompues. Elles n’ont pas été modifiées."
        StudyStoreError.FILE_TOO_LARGE -> "Le fichier de l’étude dépasse la taille autorisée."
        StudyStoreError.EXPRESSION_RECOMPILE_FAILED -> "Une expression validée ne peut plus être compilée. Les données sont conservées."
        else -> "L’opération de stockage a échoué. Les données enregistrées sont conservées."
    }

    class Factory(private val store: FunctionStudyStore) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(FunctionStudyViewModel::class.java))
            return FunctionStudyViewModel(store) as T
        }
    }
}
