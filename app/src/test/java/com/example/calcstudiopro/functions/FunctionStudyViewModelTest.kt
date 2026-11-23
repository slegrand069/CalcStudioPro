package com.example.calcstudiopro.functions

import com.example.calcstudiopro.domain.functions.MathExpressionEngine
import com.example.calcstudiopro.functions.domain.FunctionColor
import com.example.calcstudiopro.functions.domain.FunctionSlotId
import com.example.calcstudiopro.functions.domain.FunctionStudy
import com.example.calcstudiopro.functions.domain.FunctionStudyId
import com.example.calcstudiopro.functions.domain.StudyValidationResult
import com.example.calcstudiopro.functions.persistence.AtomicFileWriter
import com.example.calcstudiopro.functions.persistence.FunctionStudyStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException

class FunctionStudyViewModelTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    @Test fun a1ChangesAutosaveOnlyTheWorkingSession() {
        val directory = temporaryFolder.newFolder("a1-persistence").toPath()
        val store = FunctionStudyStore(directory, MathExpressionEngine())
        val named = FunctionStudy.newNamedStudy("Saved", FunctionStudyId("saved"))
        assertTrue(store.createNamedStudy(named) is com.example.calcstudiopro.functions.persistence.StudyStoreResult.Success)
        val viewModel = FunctionStudyViewModel(store)

        viewModel.chooseColor(FunctionSlotId.F1, FunctionColor.AMBER)
        viewModel.toggleVisibility(FunctionSlotId.F1)

        val restored = (store.loadWorkingSession() as com.example.calcstudiopro.functions.persistence.StudyStoreResult.Success).value!!
        assertEquals(FunctionColor.AMBER, restored.slot(FunctionSlotId.F1).color)
        assertEquals(false, restored.slot(FunctionSlotId.F1).isVisible)
        assertEquals(named.revision(), (store.loadNamedStudy(named.id) as com.example.calcstudiopro.functions.persistence.StudyStoreResult.Success).value.revision())
        assertNull(viewModel.state.value.saveError)
    }

    @Test fun loadFailureIsShownWithoutCreatingADefaultStudy() {
        val parentFile = temporaryFolder.newFile("not-a-directory")
        val store = FunctionStudyStore(parentFile.toPath().resolve("functions"), MathExpressionEngine())
        val viewModel = FunctionStudyViewModel(store)

        assertNull(viewModel.state.value.study)
        assertNotNull(viewModel.state.value.loadError)
    }

    @Test fun saveFailureKeepsTheChangedStudyInMemoryAndExposesTheError() {
        val directory = temporaryFolder.newFolder("a1-write-error").toPath()
        val store = FunctionStudyStore(
            directory,
            MathExpressionEngine(),
            AtomicFileWriter { _, _ -> throw IOException("Injected write failure") }
        )
        val viewModel = FunctionStudyViewModel(store)

        viewModel.toggleVisibility(FunctionSlotId.F1)

        assertEquals(false, viewModel.state.value.study!!.slot(FunctionSlotId.F1).isVisible)
        assertNotNull(viewModel.state.value.saveError)
        assertNull((store.loadWorkingSession() as com.example.calcstudiopro.functions.persistence.StudyStoreResult.Success).value)
    }

    @Test fun incompleteDraftIsPersistedWithoutReplacingTheValidatedExpression() {
        val directory = temporaryFolder.newFolder("editor-draft").toPath()
        val store = FunctionStudyStore(directory, MathExpressionEngine())
        val initial = FunctionStudyViewModel(store)
        initial.updateDraft(FunctionSlotId.F1, "x+2")
        assertTrue(initial.validateDraft(FunctionSlotId.F1) is StudyValidationResult.Accepted)

        initial.updateDraft(FunctionSlotId.F1, "sin(")
        val inMemory = initial.state.value.study!!.slot(FunctionSlotId.F1)
        assertEquals("x+2", inMemory.validatedExpression)
        assertEquals("sin(", inMemory.draft)

        val reopened = FunctionStudyViewModel(store).state.value.study!!.slot(FunctionSlotId.F1)
        assertEquals("x+2", reopened.validatedExpression)
        assertEquals("sin(", reopened.draft)
    }

    @Test fun validationFailureKeepsTheDraftAndSuccessAcceptsOnlyTheDraft() {
        val store = FunctionStudyStore(temporaryFolder.newFolder("editor-validation").toPath(), MathExpressionEngine())
        val viewModel = FunctionStudyViewModel(store)
        viewModel.updateDraft(FunctionSlotId.F2, "x+1")
        assertTrue(viewModel.validateDraft(FunctionSlotId.F2) is StudyValidationResult.Accepted)

        viewModel.updateDraft(FunctionSlotId.F2, "x+")
        val rejected = viewModel.validateDraft(FunctionSlotId.F2)
        assertTrue(rejected is StudyValidationResult.Rejected)
        assertEquals("x+1", viewModel.state.value.study!!.slot(FunctionSlotId.F2).validatedExpression)
        assertEquals("x+", viewModel.state.value.study!!.slot(FunctionSlotId.F2).draft)

        viewModel.updateDraft(FunctionSlotId.F2, "x+5")
        assertTrue(viewModel.validateDraft(FunctionSlotId.F2) is StudyValidationResult.Accepted)
        assertEquals("x+5", viewModel.state.value.study!!.slot(FunctionSlotId.F2).validatedExpression)
        assertEquals("x+5", viewModel.state.value.study!!.slot(FunctionSlotId.F2).draft)
    }
}
