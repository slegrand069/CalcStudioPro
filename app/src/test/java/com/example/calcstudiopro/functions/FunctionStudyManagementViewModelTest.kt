package com.example.calcstudiopro.functions

import com.example.calcstudiopro.domain.functions.MathExpressionEngine
import com.example.calcstudiopro.functions.domain.FunctionSlotId
import com.example.calcstudiopro.functions.domain.FunctionStudy
import com.example.calcstudiopro.functions.domain.FunctionStudyId
import com.example.calcstudiopro.functions.persistence.FunctionStudyStore
import com.example.calcstudiopro.functions.persistence.StudyStoreError
import com.example.calcstudiopro.functions.persistence.StudyStoreResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException

class FunctionStudyManagementViewModelTest {
    @get:Rule val temporaryFolder = TemporaryFolder()
    private val engine = MathExpressionEngine()

    private fun store(name: String = "management") =
        FunctionStudyStore(temporaryFolder.newFolder(name).toPath(), engine)

    @Test fun newStudyCreatesSixEmptyOrderedSlotsAndAutosavesTheNewWorkingCopy() {
        val store = store("new-study")
        val viewModel = FunctionStudyViewModel(store)
        val oldId = viewModel.state.value.study!!.id

        assertEquals(StudyManagementResult.Completed, viewModel.createNewStudy())

        val created = viewModel.state.value.study!!
        assertTrue(created.id != oldId)
        assertEquals(listOf("f1", "f2", "f3", "f4", "f5", "f6"), created.slots.map { it.id.code })
        assertTrue(created.slots.all { it.validatedExpression == null && it.draft.isEmpty() })
        assertEquals(created.revision(), success(store.loadWorkingSession())!!.revision())
        assertFalse(viewModel.state.value.hasUnsavedChanges)
    }

    @Test fun saveAsAndExplicitSaveUpdateOnlyTheNamedStudy() {
        val store = store("save-as")
        val viewModel = FunctionStudyViewModel(store)
        viewModel.updateDraft(FunctionSlotId.F1, "x+1")
        assertTrue(viewModel.validateDraft(FunctionSlotId.F1) is com.example.calcstudiopro.functions.domain.StudyValidationResult.Accepted)
        viewModel.updateDraft(FunctionSlotId.F1, "x+")

        assertEquals(StudyManagementResult.Completed, viewModel.saveAs("Courbe A"))
        val summary = success(store.listNamedStudies()).single()
        val namedRevision = success(store.loadNamedStudy(summary.id))!!.revision()
        assertEquals("x+1", success(store.loadNamedStudy(summary.id))!!.slot(FunctionSlotId.F1).validatedExpression)
        assertEquals("x+", success(store.loadNamedStudy(summary.id))!!.slot(FunctionSlotId.F1).draft)
        assertFalse(viewModel.state.value.hasUnsavedChanges)

        viewModel.updateDraft(FunctionSlotId.F1, "x+2")
        assertTrue(viewModel.state.value.hasUnsavedChanges)
        assertEquals(StudyManagementResult.Completed, viewModel.saveCurrent())
        assertFalse(viewModel.state.value.hasUnsavedChanges)
        assertEquals("x+2", success(store.loadNamedStudy(summary.id))!!.slot(FunctionSlotId.F1).draft)
        assertTrue(namedRevision != success(store.loadNamedStudy(summary.id))!!.revision())
    }

    @Test fun openingNamedStudyLoadsSlotsAndDraftsIntoAnIndependentWorkingSession() {
        val store = store("open-named")
        var named = FunctionStudy.newNamedStudy("Saved", FunctionStudyId("saved-id"))
            .withDraft(FunctionSlotId.F1, "sin(")
            .withColor(FunctionSlotId.F2, com.example.calcstudiopro.functions.domain.FunctionColor.NAVY)
        success(store.createNamedStudy(named))
        val viewModel = FunctionStudyViewModel(store)

        assertEquals(StudyManagementResult.Completed, viewModel.openNamedStudy(named.id))
        assertEquals("Saved", viewModel.state.value.displayName)
        assertEquals("sin(", viewModel.state.value.study!!.slot(FunctionSlotId.F1).draft)
        assertEquals(com.example.calcstudiopro.functions.domain.FunctionColor.NAVY, viewModel.state.value.study!!.slot(FunctionSlotId.F2).color)

        viewModel.updateDraft(FunctionSlotId.F1, "cos(")
        assertEquals("sin(", success(store.loadNamedStudy(named.id))!!.slot(FunctionSlotId.F1).draft)
        assertEquals("cos(", success(store.loadWorkingSession())!!.slot(FunctionSlotId.F1).draft)
    }

    @Test fun renameAndDeleteUseStableIdAndDeletionRequiresConfirmation() {
        val store = store("rename-delete")
        val original = FunctionStudy.newNamedStudy("Before", FunctionStudyId("stable-study-id"))
        success(store.createNamedStudy(original))
        val viewModel = FunctionStudyViewModel(store)
        assertEquals(StudyManagementResult.Completed, viewModel.openNamedStudy(original.id))

        assertEquals(StudyManagementResult.Completed, viewModel.renameCurrentStudy("After"))
        assertEquals(original.id, success(store.listNamedStudies()).single().id)
        assertEquals("After", success(store.loadNamedStudy(original.id))!!.name)
        assertEquals(StudyManagementResult.RequiresDeleteConfirmation, viewModel.deleteCurrentStudy())
        assertNotNull(success(store.loadNamedStudy(original.id)))
        assertEquals(StudyManagementResult.Completed, viewModel.deleteCurrentStudy(confirmDelete = true))
        assertEquals(StudyStoreError.NOT_FOUND, failure(store.loadNamedStudy(original.id)).error)
        assertNull(viewModel.state.value.study!!.name)
    }

    @Test fun unsavedChangesAreDistinctFromAutosaveAndRecoveryProtectsThemOnStudyChange() {
        val store = store("recovery-on-switch")
        val namedBase = FunctionStudy.newNamedStudy("A", FunctionStudyId("study-a")).withDraft(FunctionSlotId.F1, "x+1")
        val namedA = (namedBase.validateFunction(FunctionSlotId.F1, engine) as com.example.calcstudiopro.functions.domain.StudyValidationResult.Accepted)
            .study.withDraft(FunctionSlotId.F1, "x+")
        val namedB = FunctionStudy.newNamedStudy("B", FunctionStudyId("study-b"))
        success(store.createNamedStudy(namedA))
        success(store.createNamedStudy(namedB))
        val viewModel = FunctionStudyViewModel(store)
        successOpen(viewModel, namedA.id)
        viewModel.updateDraft(FunctionSlotId.F1, "sin(")
        assertTrue(viewModel.state.value.hasUnsavedChanges)

        assertEquals(StudyManagementResult.RequiresDiscardConfirmation, viewModel.openNamedStudy(namedB.id))
        assertEquals(StudyManagementResult.Completed, viewModel.openNamedStudy(namedB.id, confirmDiscard = true))
        val recovered = success(store.loadRecoverySession())!!
        assertEquals("sin(", recovered.slot(FunctionSlotId.F1).draft)
        assertEquals("x+1", recovered.slot(FunctionSlotId.F1).validatedExpression)
        assertEquals("B", viewModel.state.value.displayName)
    }

    @Test fun refusingRecoveryReplacementLeavesCurrentStudyAndExistingRecoveryUntouched() {
        val store = store("recovery-refusal")
        val first = FunctionStudy.newWorkingSession(FunctionStudyId("first"))
        val second = FunctionStudy.newRecoverySession(FunctionStudyId("existing-recovery"))
            .withDraft(FunctionSlotId.F3, "sqrt(")
        success(store.saveRecoverySession(second))
        success(store.saveWorkingSession(first))
        val viewModel = FunctionStudyViewModel(store)
        viewModel.updateDraft(FunctionSlotId.F1, "x+")
        assertEquals(StudyManagementResult.RequiresDiscardConfirmation, viewModel.createNewStudy())
        assertEquals(
            StudyManagementResult.RequiresRecoveryReplacementConfirmation,
            viewModel.createNewStudy(confirmDiscard = true)
        )
        assertEquals(first.id, viewModel.state.value.study!!.id)
        assertEquals("sqrt(", success(store.loadRecoverySession())!!.slot(FunctionSlotId.F3).draft)
        assertEquals(StudyManagementResult.Completed, viewModel.createNewStudy(confirmDiscard = true, replaceRecovery = true))
        assertEquals("x+", success(store.loadRecoverySession())!!.slot(FunctionSlotId.F1).draft)
    }

    @Test fun recoverySurvivesViewModelRestartAndCanBeRestoredWithoutRecompilingDrafts() {
        val store = store("recovery-restart")
        val viewModel = FunctionStudyViewModel(store)
        viewModel.updateDraft(FunctionSlotId.F4, "root(")
        assertEquals(StudyManagementResult.RequiresDiscardConfirmation, viewModel.createNewStudy())
        assertEquals(StudyManagementResult.Completed, viewModel.createNewStudy(confirmDiscard = true))

        val reopened = FunctionStudyViewModel(store)
        assertTrue(reopened.state.value.recoveryAvailable)
        assertEquals(StudyManagementResult.Completed, reopened.inspectRecovery())
        assertEquals("root(", reopened.state.value.recoveryPreview!!.slot(FunctionSlotId.F4).draft)
        assertEquals(StudyManagementResult.Completed, reopened.restoreRecovery())
        assertEquals("root(", reopened.state.value.study!!.slot(FunctionSlotId.F4).draft)
        assertNull(reopened.state.value.study!!.slot(FunctionSlotId.F4).validatedExpression)
    }

    @Test fun failedNamedStudyUpdateReportsConflictWithoutChangingTheWorkingCopy() {
        val store = store("revision-conflict")
        val named = FunctionStudy.newNamedStudy("Conflict", FunctionStudyId("conflict"))
        success(store.createNamedStudy(named))
        val viewModel = FunctionStudyViewModel(store)
        successOpen(viewModel, named.id)
        viewModel.updateDraft(FunctionSlotId.F2, "x+1")
        val external = success(store.loadNamedStudy(named.id))!!.asNamedStudy("Conflict").withDraft(FunctionSlotId.F3, "x+3")
        success(store.updateNamedStudy(external, named.revision()))

        assertTrue(viewModel.saveCurrent() is StudyManagementResult.Failed)
        assertTrue(viewModel.state.value.operationError!!.contains("Conflit de révision"))
        assertEquals("x+1", viewModel.state.value.study!!.slot(FunctionSlotId.F2).draft)
        assertEquals("x+3", success(store.loadNamedStudy(named.id))!!.slot(FunctionSlotId.F3).draft)
    }

    @Test fun storageFailureIsReportedAndLastNamedDataRemainsReadable() {
        val directory = temporaryFolder.newFolder("write-failure").toPath()
        val normalStore = FunctionStudyStore(directory, engine)
        val named = FunctionStudy.newNamedStudy("Stored", FunctionStudyId("stored"))
        success(normalStore.createNamedStudy(named))
        val brokenStore = FunctionStudyStore(directory, engine, com.example.calcstudiopro.functions.persistence.AtomicFileWriter { _, _ -> throw IOException("blocked") })
        val viewModel = FunctionStudyViewModel(brokenStore)
        viewModel.updateDraft(FunctionSlotId.F1, "x+1")
        assertNotNull(viewModel.state.value.saveError)
        assertEquals("", success(normalStore.loadNamedStudy(named.id))!!.slot(FunctionSlotId.F1).draft)
    }

    @Test fun unsavedDraftInPlainSessionRemainsMarkedAfterRestartDespiteAutosave() {
        val store = store("dirty-session-restart")
        val original = FunctionStudyViewModel(store)
        original.updateDraft(FunctionSlotId.F5, "sin(")
        assertTrue(original.state.value.hasUnsavedChanges)
        val reopened = FunctionStudyViewModel(store)
        assertEquals("sin(", reopened.state.value.study!!.slot(FunctionSlotId.F5).draft)
        assertTrue(reopened.state.value.hasUnsavedChanges)
    }

    private fun <T> success(result: StudyStoreResult<T>): T = when (result) {
        is StudyStoreResult.Success -> result.value
        is StudyStoreResult.Failure -> error("Unexpected store failure: ${result.error}")
    }

    private fun successOpen(viewModel: FunctionStudyViewModel, id: FunctionStudyId) {
        assertEquals(StudyManagementResult.Completed, viewModel.openNamedStudy(id))
    }

    private fun failure(result: StudyStoreResult<FunctionStudy>) = result as StudyStoreResult.Failure
}
