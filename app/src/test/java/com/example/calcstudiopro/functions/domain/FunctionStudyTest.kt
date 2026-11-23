package com.example.calcstudiopro.functions.domain

import com.example.calcstudiopro.domain.functions.ExpressionEvaluation
import com.example.calcstudiopro.domain.functions.FunctionAngleMode
import com.example.calcstudiopro.domain.functions.MathExpressionEngine
import com.example.calcstudiopro.domain.functions.MathExpressionError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI

class FunctionStudyTest {
    private val engine = MathExpressionEngine()

    @Test fun newWorkingSessionAlwaysCreatesSixStableOrderedSlots() {
        val study = FunctionStudy.newWorkingSession(FunctionStudyId("session-a"))
        assertEquals(6, study.slots.size)
        assertEquals(FunctionSlotId.ordered, study.slots.map { it.id })
        assertEquals(listOf("f1", "f2", "f3", "f4", "f5", "f6"), study.slots.map { it.id.code })
        assertEquals((1..6).toList(), study.slots.map { it.id.number })
    }

    @Test fun paletteDefaultsAreDistinctAndDuplicateSlotColorsAreAllowed() {
        val study = FunctionStudy.newWorkingSession()
        assertEquals(12, FunctionColorPalette.colors.size)
        assertEquals(
            listOf(
                FunctionColor.BLUE, FunctionColor.RED, FunctionColor.GREEN,
                FunctionColor.AMBER, FunctionColor.VIOLET, FunctionColor.CYAN
            ),
            FunctionColorPalette.defaults
        )
        assertEquals(6, study.slots.map { it.color }.toSet().size)
        assertEquals(FunctionColorPalette.defaults, study.slots.map { it.color })
        val duplicate = study.withColor(FunctionSlotId.F2, study.slot(FunctionSlotId.F1).color)
        assertEquals(duplicate.slot(FunctionSlotId.F1).color, duplicate.slot(FunctionSlotId.F2).color)
    }

    @Test fun visibilityIsIndependentFromAnEmptySlot() {
        val study = FunctionStudy.newWorkingSession().withVisibility(FunctionSlotId.F3, false)
        val slot = study.slot(FunctionSlotId.F3)
        assertEquals(null, slot.validatedExpression)
        assertFalse(slot.isVisible)
        assertTrue(study.evaluate(FunctionSlotId.F3, 2.0) is FunctionEvaluationResult.Empty)
        assertFalse(study.withVisibility(FunctionSlotId.F3, true).slot(FunctionSlotId.F3).isDraftModified)
    }

    @Test fun invalidDraftPreservesPreviouslyValidatedExpressionAndText() {
        val accepted = validate(FunctionStudy.newWorkingSession(), FunctionSlotId.F1, "x+1")
        val invalid = accepted.withDraft(FunctionSlotId.F1, "sqrt(")
        val result = invalid.validateFunction(FunctionSlotId.F1, engine) as StudyValidationResult.Rejected
        assertEquals("x+1", result.study.slot(FunctionSlotId.F1).validatedExpression)
        assertEquals("sqrt(", result.study.slot(FunctionSlotId.F1).draft)
        assertTrue(result.study.slot(FunctionSlotId.F1).isDraftModified)
        assertTrue(result.error is MathExpressionError.MissingParenthesis)
        assertEquals("x+1", accepted.slot(FunctionSlotId.F1).validatedExpression)
    }

    @Test fun successfulValidationPromotesDraftAndReturnsExplicitAcceptedResult() {
        val edited = FunctionStudy.newWorkingSession().withDraft(FunctionSlotId.F2, "x^2")
        val result = edited.validateFunction(FunctionSlotId.F2, engine) as StudyValidationResult.Accepted
        assertEquals("x^2", result.expression)
        assertEquals("x^2", result.study.slot(FunctionSlotId.F2).validatedExpression)
        assertEquals("x^2", result.study.slot(FunctionSlotId.F2).draft)
        assertFalse(result.study.slot(FunctionSlotId.F2).isDraftModified)
    }

    @Test fun validatingBlankDraftClearsAFunction() {
        val withFunction = validate(FunctionStudy.newWorkingSession(), FunctionSlotId.F1, "x+1")
            .withVisibility(FunctionSlotId.F1, false)
        val cleared = withFunction.withDraft(FunctionSlotId.F1, "  ")
            .validateFunction(FunctionSlotId.F1, engine) as StudyValidationResult.Accepted
        assertEquals(null, cleared.expression)
        assertEquals(null, cleared.study.slot(FunctionSlotId.F1).validatedExpression)
        assertEquals("", cleared.study.slot(FunctionSlotId.F1).draft)
        assertFalse(cleared.study.slot(FunctionSlotId.F1).isVisible)
    }

    @Test fun draftModifiedIndicatorTracksOnlyDraftVersusValidatedText() {
        val empty = FunctionStudy.newWorkingSession().slot(FunctionSlotId.F1)
        assertFalse(empty.isDraftModified)
        val edited = empty.editDraft("x+")
        assertTrue(edited.isDraftModified)
        assertFalse(edited.editDraft("").isDraftModified)
        val accepted = (validate(FunctionStudy.newWorkingSession(), FunctionSlotId.F1, "x")
            .slot(FunctionSlotId.F1)).editDraft("x+1")
        assertTrue(accepted.isDraftModified)
    }

    @Test fun parametersAreSharedAcrossAllFunctionsInOneStudy() {
        val study = validate(
            validate(FunctionStudy.newWorkingSession(), FunctionSlotId.F1, "a*x"),
            FunctionSlotId.F2,
            "a+x"
        )
        val updated = study.withParameter('A', 4.0) as StudyParameterUpdate.Updated
        assertEquals(8.0, value(updated.study.evaluate(FunctionSlotId.F1, 2.0)), 0.0)
        assertEquals(6.0, value(updated.study.evaluate(FunctionSlotId.F2, 2.0)), 0.0)
        assertEquals(1.0, study.parameters.value('a'), 0.0)
    }

    @Test fun absentParameterDefaultsToOne() {
        val study = validate(FunctionStudy.newWorkingSession(), FunctionSlotId.F1, "a*x")
        assertEquals(6.0, value(study.evaluate(FunctionSlotId.F1, 6.0)), 0.0)
        assertEquals(1.0, study.parameters.value('A'), 0.0)
    }

    @Test fun parameterRegistryRejectsReservedNamesAndNonFiniteValues() {
        val params = StudyParameters.defaults
        for (invalid in listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)) {
            assertEquals(ParameterUpdateError.NON_FINITE_VALUE,
                (params.withValue('a', invalid) as ParameterUpdateResult.Rejected).error)
        }
        for (reserved in listOf('x', 'X', 'e', 't', 'π', '1')) {
            assertEquals(ParameterUpdateError.INVALID_NAME,
                (params.withValue(reserved, 2.0) as ParameterUpdateResult.Rejected).error)
        }
    }

    @Test fun resettingParameterRemovesExplicitEntryAndRestoresDefaultValue() {
        val study = FunctionStudy.newWorkingSession()
        val set = (study.withParameter('A', 5.0) as StudyParameterUpdate.Updated).study
        assertEquals(mapOf('a' to 5.0), set.revision().parameters)
        val reset = (set.resetParameter('a') as StudyParameterUpdate.Updated).study
        assertEquals(emptyMap<Char, Double>(), reset.revision().parameters)
        assertEquals(1.0, reset.parameters.value('A'), 0.0)
        assertTrue(reset.hasUnsavedChangesSince(study.revision()).not())
    }

    @Test fun angleModeIsStudyLocalAndChangesEvaluationWithoutRecompilation() {
        val radStudy = validate(FunctionStudy.newWorkingSession(), FunctionSlotId.F1, "sin(x)")
        val compiled = radStudy.slot(FunctionSlotId.F1).compiledExpression
        assertEquals(1.0, value(radStudy.evaluate(FunctionSlotId.F1, PI / 2.0)), 1e-12)
        val degreeStudy = radStudy.withAngleMode(FunctionAngleMode.DEG)
        assertEquals(1.0, value(degreeStudy.evaluate(FunctionSlotId.F1, 90.0)), 1e-12)
        assertSame(compiled, degreeStudy.slot(FunctionSlotId.F1).compiledExpression)
        assertEquals(FunctionAngleMode.RAD, radStudy.angleMode)
    }

    @Test fun evaluationUsesValidatedExpressionInsteadOfCurrentDraft() {
        val accepted = validate(FunctionStudy.newWorkingSession(), FunctionSlotId.F1, "x+1")
        val edited = accepted.withDraft(FunctionSlotId.F1, "x+100")
        assertEquals(3.0, value(edited.evaluate(FunctionSlotId.F1, 2.0)), 0.0)
        assertEquals("x+100", edited.slot(FunctionSlotId.F1).draft)
        assertEquals("x+1", edited.slot(FunctionSlotId.F1).validatedExpression)
    }

    @Test fun draftModificationAndUnsavedStudyModificationAreIndependent() {
        val initial = FunctionStudy.newWorkingSession()
        val reference = initial.revision()
        val editedDraft = initial.withDraft(FunctionSlotId.F1, "x+")
        assertTrue(editedDraft.slot(FunctionSlotId.F1).isDraftModified)
        assertTrue(editedDraft.hasUnsavedChangesSince(reference))

        val draftReference = editedDraft.revision()
        assertTrue(editedDraft.slot(FunctionSlotId.F1).isDraftModified)
        assertFalse(editedDraft.hasUnsavedChangesSince(draftReference))

        val accepted = validate(initial, FunctionSlotId.F1, "x+1")
        assertFalse(accepted.slot(FunctionSlotId.F1).isDraftModified)
        assertTrue(accepted.hasUnsavedChangesSince(reference))
    }

    @Test fun parameterUsageExcludesFunctionsConstantsAndIndependentVariable() {
        val study = validate(
            FunctionStudy.newWorkingSession(),
            FunctionSlotId.F1,
            "ASIN(a)+EXP(B)+PI+π+E+x"
        )
        assertEquals(setOf('a', 'b'), study.usedParameters)
    }

    @Test fun unusedParameterValuesAreRetainedForFutureExpressions() {
        val initial = FunctionStudy.newWorkingSession()
        val updated = initial.withParameter('q', 7.0) as StudyParameterUpdate.Updated
        val expressionStudy = validate(updated.study, FunctionSlotId.F1, "x+1")
        assertEquals(emptySet<Char>(), expressionStudy.usedParameters)
        assertEquals(7.0, expressionStudy.parameters.value('Q'), 0.0)
    }

    @Test fun studiesHaveIndependentIdsParametersSlotsAndModes() {
        val first = FunctionStudy.newWorkingSession(FunctionStudyId("first"))
        val second = FunctionStudy.newWorkingSession(FunctionStudyId("second"))
        val firstUpdated = validate(first, FunctionSlotId.F1, "a+x")
        val firstWithParameter = (firstUpdated.withParameter('a', 9.0) as StudyParameterUpdate.Updated)
            .study.withAngleMode(FunctionAngleMode.DEG)
        assertNotSame(firstWithParameter, second)
        assertEquals(FunctionStudyId("first"), firstWithParameter.id)
        assertEquals(FunctionStudyId("second"), second.id)
        assertEquals(1.0, second.parameters.value('a'), 0.0)
        assertTrue(second.slot(FunctionSlotId.F1).validatedExpression == null)
        assertEquals(FunctionAngleMode.RAD, second.angleMode)
    }

    @Test fun studyKindsEnforceNamedStudyNamesWithoutAddingLifecycleOperations() {
        assertEquals(FunctionStudyKind.WORKING_SESSION, FunctionStudy.newWorkingSession().kind)
        val named = FunctionStudy.newNamedStudy("Étude 1")
        assertEquals(FunctionStudyKind.NAMED_STUDY, named.kind)
        assertEquals("Étude 1", named.name)
        assertEquals(FunctionStudyKind.RECOVERY_SESSION, FunctionStudyKind.valueOf("RECOVERY_SESSION"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun namedStudiesRequireANonBlankName() {
        FunctionStudy.newNamedStudy("  ")
    }

    @Test fun mathErrorsArePropagatedAsTypedFunctionEvaluationFailures() {
        val study = validate(FunctionStudy.newWorkingSession(), FunctionSlotId.F1, "sqrt(x)")
        val result = study.evaluate(FunctionSlotId.F1, -1.0) as FunctionEvaluationResult.Failure
        assertTrue(result.error is MathExpressionError.Domain)
    }

    private fun validate(study: FunctionStudy, id: FunctionSlotId, expression: String): FunctionStudy {
        val result = study.withDraft(id, expression).validateFunction(id, engine)
        return when (result) {
            is StudyValidationResult.Accepted -> result.study
            is StudyValidationResult.Rejected -> throw AssertionError("Validation failed: ${result.error}")
        }
    }

    private fun value(result: FunctionEvaluationResult): Double = when (result) {
        is FunctionEvaluationResult.Value -> result.value
        is FunctionEvaluationResult.Empty -> throw AssertionError("Function slot is empty: ${result.slotId}")
        is FunctionEvaluationResult.Failure -> throw AssertionError("Evaluation failed: ${result.error}")
    }
}
