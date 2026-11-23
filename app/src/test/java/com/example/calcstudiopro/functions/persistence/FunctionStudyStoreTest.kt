package com.example.calcstudiopro.functions.persistence

import com.example.calcstudiopro.domain.functions.FunctionAngleMode
import com.example.calcstudiopro.domain.functions.MathExpressionEngine
import com.example.calcstudiopro.functions.domain.FunctionColor
import com.example.calcstudiopro.functions.domain.FunctionStudy
import com.example.calcstudiopro.functions.domain.FunctionStudyId
import com.example.calcstudiopro.functions.domain.FunctionStudyKind
import com.example.calcstudiopro.functions.domain.FunctionStudyRestoreResult
import com.example.calcstudiopro.functions.domain.FunctionStudyRevision
import com.example.calcstudiopro.functions.domain.FunctionSlotId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.file.Files

class FunctionStudyStoreTest {
    @get:Rule val temporaryFolder = TemporaryFolder()
    private val engine = MathExpressionEngine()

    @Test fun workingSessionRoundTripsAllFieldsAndRebuildsValidatedAsts() {
        val directory = temporaryFolder.newFolder("round-trip").toPath()
        val store = FunctionStudyStore(directory, engine)
        var study = FunctionStudy.newWorkingSession(FunctionStudyId("working-id"))
        study = validate(study, FunctionSlotId.F1, "sin(x)").withDraft(FunctionSlotId.F1, "sin(")
        study = validate(study, FunctionSlotId.F2, "a*x").withDraft(FunctionSlotId.F2, "a*x+")
        study = study.withColor(FunctionSlotId.F1, FunctionColor.CYAN)
            .withColor(FunctionSlotId.F2, FunctionColor.CYAN)
            .withVisibility(FunctionSlotId.F1, false)
            .withVisibility(FunctionSlotId.F5, false)
            .withAngleMode(FunctionAngleMode.DEG)
        study = (study.withParameter('A', 4.0) as com.example.calcstudiopro.functions.domain.StudyParameterUpdate.Updated).study

        assertTrue(store.saveWorkingSession(study) is StudyStoreResult.Success)
        val loaded = success(store.loadWorkingSession())!!
        assertEquals(study.revision(), loaded.revision())
        assertEquals(6, loaded.slots.size)
        assertEquals(listOf("f1", "f2", "f3", "f4", "f5", "f6"), loaded.slots.map { it.id.code })
        assertEquals("sin(x)", loaded.slot(FunctionSlotId.F1).validatedExpression)
        assertEquals("sin(", loaded.slot(FunctionSlotId.F1).draft)
        assertEquals("a*x", loaded.slot(FunctionSlotId.F2).validatedExpression)
        assertEquals("a*x+", loaded.slot(FunctionSlotId.F2).draft)
        assertNull(loaded.slot(FunctionSlotId.F3).validatedExpression)
        assertEquals(FunctionColor.CYAN, loaded.slot(FunctionSlotId.F1).color)
        assertEquals(FunctionColor.CYAN, loaded.slot(FunctionSlotId.F2).color)
        assertFalse(loaded.slot(FunctionSlotId.F1).isVisible)
        assertFalse(loaded.slot(FunctionSlotId.F5).isVisible)
        assertEquals(4.0, loaded.parameters.value('a'), 0.0)
        assertEquals(FunctionAngleMode.DEG, loaded.angleMode)
        assertEquals(1.0, value(loaded.evaluate(FunctionSlotId.F1, 90.0)), 1e-12)
        assertEquals(8.0, value(loaded.evaluate(FunctionSlotId.F2, 2.0)), 0.0)
    }

    @Test fun absentWorkingSessionReturnsAUsableDefaultWithoutPersistingIt() {
        val directory = temporaryFolder.newFolder("absent-session").toPath()
        val store = FunctionStudyStore(directory, engine)
        val result = success(store.loadOrCreateWorkingSession())
        assertEquals(FunctionStudyKind.WORKING_SESSION, result.kind)
        assertEquals(6, result.slots.size)
        assertFalse(Files.exists(directory.resolve("working.cspf")))
        assertNull(success(store.loadWorkingSession()))
    }

    @Test fun namedStudiesRemainIndependentFromWorkingSessionAndSupportCrud() {
        val directory = temporaryFolder.newFolder("named").toPath()
        val store = FunctionStudyStore(directory, engine)
        var working = FunctionStudy.newWorkingSession(FunctionStudyId("work"))
        working = validate(working, FunctionSlotId.F1, "x+1")
        success(store.saveWorkingSession(working))

        var named = FunctionStudy.newNamedStudy("Courbe A", FunctionStudyId("named-a"))
        named = validate(named, FunctionSlotId.F1, "x+10")
        success(store.createNamedStudy(named))
        assertEquals(StudyStoreError.DUPLICATE_ID, failure(store.createNamedStudy(named)).error)
        assertEquals(listOf(NamedStudySummary(named.id, "Courbe A")), success(store.listNamedStudies()))

        val expectedWorking = working.revision()
        working = validate(working.withDraft(FunctionSlotId.F1, "x+2"), FunctionSlotId.F1, "x+2")
        success(store.saveWorkingSession(working, expectedWorking))
        assertEquals("x+10", success(store.loadNamedStudy(named.id)).slot(FunctionSlotId.F1).validatedExpression)

        val loadedNamed = success(store.loadNamedStudy(named.id))
        named = validate(loadedNamed.withDraft(FunctionSlotId.F1, "x+20"), FunctionSlotId.F1, "x+20")
        success(store.updateNamedStudy(named, loadedNamed.revision()))
        success(store.renameNamedStudy(named.id, "Courbe renommée"))
        val reloaded = success(store.loadNamedStudy(named.id))
        assertEquals("Courbe renommée", reloaded.name)
        assertEquals("x+20", reloaded.slot(FunctionSlotId.F1).validatedExpression)
        assertEquals(1, success(store.listNamedStudies()).size)

        success(store.deleteNamedStudy(named.id))
        assertEquals(StudyStoreError.NOT_FOUND, failure(store.loadNamedStudy(named.id)).error)
        assertEquals(StudyStoreError.NOT_FOUND, failure(store.deleteNamedStudy(named.id)).error)
    }

    @Test fun recoverySessionIsSeparateAndReplacementRequiresExplicitOptIn() {
        val store = FunctionStudyStore(temporaryFolder.newFolder("recovery").toPath(), engine)
        val named = validate(FunctionStudy.newNamedStudy("Saved", FunctionStudyId("saved")), FunctionSlotId.F1, "x+4")
        success(store.createNamedStudy(named))
        val first = FunctionStudy.newRecoverySession(FunctionStudyId("recovery-1"))
            .withDraft(FunctionSlotId.F1, "sqrt(")
        success(store.saveRecoverySession(first))
        val second = FunctionStudy.newRecoverySession(FunctionStudyId("recovery-2"))
            .withDraft(FunctionSlotId.F2, "a*x+")
        assertEquals(StudyStoreError.RECOVERY_ALREADY_EXISTS, failure(store.saveRecoverySession(second)).error)
        assertEquals("sqrt(", success(store.loadRecoverySession())!!.slot(FunctionSlotId.F1).draft)

        val expectedRecovery = success(store.loadRecoverySession())!!.revision()
        success(store.saveRecoverySession(second, allowReplace = true, expectedRevision = expectedRecovery))
        val recovered = success(store.loadRecoverySession())!!
        assertEquals("a*x+", recovered.slot(FunctionSlotId.F2).draft)
        assertEquals("x+4", success(store.loadNamedStudy(named.id)).slot(FunctionSlotId.F1).validatedExpression)
        success(store.deleteRecoverySession())
        assertNull(success(store.loadRecoverySession()))
    }

    @Test fun unknownVersionIsReportedAndCannotBeOverwritten() {
        val directory = temporaryFolder.newFolder("version").toPath()
        val store = FunctionStudyStore(directory, engine)
        val original = validate(FunctionStudy.newWorkingSession(FunctionStudyId("versioned")), FunctionSlotId.F1, "x+1")
        success(store.saveWorkingSession(original))
        val file = directory.resolve("working.cspf")
        val bytes = Files.readAllBytes(file)
        ByteBuffer.wrap(bytes).putInt(4, 99)
        Files.write(file, bytes)

        assertEquals(StudyStoreError.INCOMPATIBLE_VERSION, failure(store.loadWorkingSession()).error)
        val replacement = validate(original.withDraft(FunctionSlotId.F1, "x+2"), FunctionSlotId.F1, "x+2")
        assertEquals(StudyStoreError.INCOMPATIBLE_VERSION, failure(store.saveWorkingSession(replacement)).error)
        assertEquals(99, ByteBuffer.wrap(Files.readAllBytes(file)).getInt(4))
    }

    @Test fun checksumAndMalformedPayloadAreReportedAsCorruption() {
        val directory = temporaryFolder.newFolder("corrupt").toPath()
        val store = FunctionStudyStore(directory, engine)
        val study = FunctionStudy.newWorkingSession(FunctionStudyId("corruptible"))
        success(store.saveWorkingSession(study))
        val file = directory.resolve("working.cspf")
        val bytes = Files.readAllBytes(file)
        bytes[bytes.lastIndex - 8] = (bytes[bytes.lastIndex - 8].toInt() xor 0x40).toByte()
        Files.write(file, bytes)
        assertEquals(StudyStoreError.CORRUPT_DATA, failure(store.loadWorkingSession()).error)
    }

    @Test fun writeFailureLeavesPreviouslyCommittedRecordReadable() {
        val directory = temporaryFolder.newFolder("write-failure").toPath()
        val goodStore = FunctionStudyStore(directory, engine)
        val old = validate(FunctionStudy.newWorkingSession(FunctionStudyId("stable-session")), FunctionSlotId.F1, "x+1")
        success(goodStore.saveWorkingSession(old))

        val failingStore = FunctionStudyStore(directory, engine, AtomicFileWriter { _, _ -> throw IOException("Injected write failure") })
        val changed = validate(old.withDraft(FunctionSlotId.F1, "x+2"), FunctionSlotId.F1, "x+2")
        val expected = success(failingStore.loadWorkingSession())!!.revision()
        assertEquals(StudyStoreError.STORAGE, failure(failingStore.saveWorkingSession(changed, expected)).error)
        assertEquals(old.revision(), success(goodStore.loadWorkingSession())!!.revision())
    }

    @Test fun staleWriterCannotOverwriteARevisionSavedByAnotherStoreInstance() {
        val directory = temporaryFolder.newFolder("stale-writer").toPath()
        val firstStore = FunctionStudyStore(directory, engine)
        val secondStore = FunctionStudyStore(directory, engine)
        val initial = validate(FunctionStudy.newWorkingSession(FunctionStudyId("shared")), FunctionSlotId.F1, "x+1")
        success(firstStore.saveWorkingSession(initial))
        val firstLoaded = success(firstStore.loadWorkingSession())!!
        val secondLoaded = success(secondStore.loadWorkingSession())!!
        val firstUpdate = validate(firstLoaded.withDraft(FunctionSlotId.F1, "x+2"), FunctionSlotId.F1, "x+2")
        val staleUpdate = validate(secondLoaded.withDraft(FunctionSlotId.F1, "x+3"), FunctionSlotId.F1, "x+3")

        success(firstStore.saveWorkingSession(firstUpdate, firstLoaded.revision()))
        assertEquals(
            StudyStoreError.CONCURRENT_MODIFICATION,
            failure(secondStore.saveWorkingSession(staleUpdate, secondLoaded.revision())).error
        )
        assertEquals("x+2", success(secondStore.loadWorkingSession())!!.slot(FunctionSlotId.F1).validatedExpression)
    }

    @Test fun storageAccessFailureIsReturnedAsAResult() {
        val parentFile = temporaryFolder.newFile("not-a-directory")
        val store = FunctionStudyStore(parentFile.toPath().resolve("child"), engine)
        assertEquals(StudyStoreError.STORAGE, failure(store.loadWorkingSession()).error)
    }

    @Test fun restoreRecompilesValidatedExpressionsButIgnoresInvalidDrafts() {
        val source = validate(FunctionStudy.newWorkingSession(), FunctionSlotId.F1, "x+1")
            .withDraft(FunctionSlotId.F1, "sqrt(")
        val restored = FunctionStudy.restore(source.revision(), engine) as FunctionStudyRestoreResult.Restored
        assertEquals("sqrt(", restored.study.slot(FunctionSlotId.F1).draft)
        assertEquals(3.0, value(restored.study.evaluate(FunctionSlotId.F1, 2.0)), 0.0)
    }

    @Test fun previouslyValidatedExpressionThatNoLongerCompilesIsNotSilentlyReplaced() {
        val source = validate(FunctionStudy.newWorkingSession(), FunctionSlotId.F1, "x+1")
        val revision = source.revision()
        val changedSlots = revision.slots.map { slot ->
            if (slot.id == FunctionSlotId.F1) slot.copy(validatedExpression = "x+") else slot
        }
        val invalidRevision = FunctionStudyRevision(
            revision.studyId, revision.kind, revision.name, changedSlots, revision.parameters, revision.angleMode
        )
        val result = FunctionStudy.restore(invalidRevision, engine)
        assertTrue(result is FunctionStudyRestoreResult.SavedExpressionNoLongerCompiles)
        assertEquals("x+1", source.slot(FunctionSlotId.F1).validatedExpression)
    }

    private fun validate(study: FunctionStudy, id: FunctionSlotId, text: String): FunctionStudy {
        val edited = study.withDraft(id, text)
        return when (val result = edited.validateFunction(id, engine)) {
            is com.example.calcstudiopro.functions.domain.StudyValidationResult.Accepted -> result.study
            is com.example.calcstudiopro.functions.domain.StudyValidationResult.Rejected -> throw AssertionError(result.error)
        }
    }

    private fun <T> success(result: StudyStoreResult<T>): T = when (result) {
        is StudyStoreResult.Success -> result.value
        is StudyStoreResult.Failure -> throw AssertionError("Store failed: ${result.error}: ${result.detail}")
    }

    private fun failure(result: StudyStoreResult<*>): StudyStoreResult.Failure = when (result) {
        is StudyStoreResult.Failure -> result
        is StudyStoreResult.Success -> throw AssertionError("Expected store failure, received ${result.value}")
    }

    private fun value(result: com.example.calcstudiopro.functions.domain.FunctionEvaluationResult): Double = when (result) {
        is com.example.calcstudiopro.functions.domain.FunctionEvaluationResult.Value -> result.value
        else -> throw AssertionError("Unexpected evaluation result: $result")
    }
}
