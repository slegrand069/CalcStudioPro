package com.example.calcstudiopro.functions.table

import com.example.calcstudiopro.domain.functions.FunctionAngleMode
import com.example.calcstudiopro.domain.functions.MathExpressionEngine
import com.example.calcstudiopro.functions.domain.FunctionSlotId
import com.example.calcstudiopro.functions.domain.FunctionStudy
import com.example.calcstudiopro.functions.domain.StudyValidationResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FunctionValueTableCalculatorTest {
    private val engine = MathExpressionEngine()

    private fun add(study: FunctionStudy, slotId: FunctionSlotId, expression: String): FunctionStudy {
        val edited = study.withDraft(slotId, expression)
        return (edited.validateFunction(slotId, engine) as StudyValidationResult.Accepted).study
    }

    private fun completed(study: FunctionStudy, settings: FunctionValueTableSettings) =
        FunctionValueTableCalculator.calculate(study, settings) as FunctionValueTableCalculation.Completed

    @Test fun noSelectedFunctionsProducesOnlyAbscissasAndNoEvaluations() {
        val result = completed(
            FunctionStudy.newWorkingSession(),
            FunctionValueTableSettings(FunctionValueTableGenerationMode.IntervalAndCount(-1.0, 1.0, 3))
        ).result
        assertEquals(listOf(-1.0, 0.0, 1.0), result.abscissas)
        assertTrue(result.columns.isEmpty())
    }

    @Test fun selectedHiddenFunctionIsEvaluatedUsingValidatedExpressionAndStableColor() {
        var study = add(FunctionStudy.newWorkingSession(), FunctionSlotId.F2, "a*x")
        study = (study.withParameter('a', 3.0) as com.example.calcstudiopro.functions.domain.StudyParameterUpdate.Updated).study
        study = study.withVisibility(FunctionSlotId.F2, false)
        study = study.withDraft(FunctionSlotId.F2, "sqrt(") // invalid, unvalidated edit must not replace a*x
        val settings = FunctionValueTableSettings(
            FunctionValueTableGenerationMode.IntervalAndCount(2.0, 4.0, 2),
            selectedSlots = setOf(FunctionSlotId.F2)
        )

        val result = completed(study, settings).result
        assertEquals("a*x", result.columns.single().expression)
        assertEquals(FunctionSlotId.F2, result.columns.single().slotId)
        assertEquals(study.slot(FunctionSlotId.F2).color, result.columns.single().color)
        assertEquals(6.0, (result.columns.single().cells[0] as FunctionValueTableCell.Value).value, 0.0)
        assertEquals(12.0, (result.columns.single().cells[1] as FunctionValueTableCell.Value).value, 0.0)
    }

    @Test fun selectedFunctionsAreAlwaysReturnedInStableSlotOrder() {
        var study = FunctionStudy.newWorkingSession()
        study = add(study, FunctionSlotId.F1, "x")
        study = add(study, FunctionSlotId.F3, "x^2")
        study = add(study, FunctionSlotId.F6, "x+10")
        val result = completed(study, FunctionValueTableSettings(
            FunctionValueTableGenerationMode.IntervalAndCount(2.0, 2.0, 1),
            selectedSlots = setOf(FunctionSlotId.F6, FunctionSlotId.F3, FunctionSlotId.F1)
        )).result
        assertEquals(listOf(FunctionSlotId.F1, FunctionSlotId.F3, FunctionSlotId.F6), result.columns.map { it.slotId })
    }

    @Test fun emptySelectedSlotIsRejectedAndNeverEvaluated() {
        val result = FunctionValueTableCalculator.calculate(
            FunctionStudy.newWorkingSession(),
            FunctionValueTableSettings(selectedSlots = setOf(FunctionSlotId.F4))
        ) as FunctionValueTableCalculation.Invalid
        assertEquals(FunctionValueTableValidationError.EmptySelectedSlot(FunctionSlotId.F4), result.error)
    }

    @Test fun domainErrorsAndNumericNonFiniteResultsRemainDistinct() {
        var study = FunctionStudy.newWorkingSession()
        study = add(study, FunctionSlotId.F1, "1/x")
        study = add(study, FunctionSlotId.F2, "exp(x)")
        val result = completed(study, FunctionValueTableSettings(
            FunctionValueTableGenerationMode.CustomValues("0;1000"),
            selectedSlots = setOf(FunctionSlotId.F1, FunctionSlotId.F2)
        )).result

        assertTrue(result.columns[0].cells[0] is FunctionValueTableCell.Undefined)
        assertTrue(result.columns[1].cells[1] is FunctionValueTableCell.NumericNonFinite)
        assertTrue(result.columns[0].cells[1] is FunctionValueTableCell.Value)
    }

    @Test fun angleModeAndSharedParametersComeFromTheCurrentStudy() {
        var study = add(FunctionStudy.newWorkingSession(), FunctionSlotId.F1, "a*sin(x)")
        study = (study.withParameter('a', 2.0) as com.example.calcstudiopro.functions.domain.StudyParameterUpdate.Updated).study
        study = study.withAngleMode(FunctionAngleMode.DEG)
        val result = completed(study, FunctionValueTableSettings(
            FunctionValueTableGenerationMode.CustomValues("30"), setOf(FunctionSlotId.F1)
        )).result
        assertEquals(1.0, (result.columns.single().cells.single() as FunctionValueTableCell.Value).value, 1e-14)
    }

    @Test fun valuesAreNotRoundedAndSixFunctionsCanProduceSixThousandCells() {
        var study = FunctionStudy.newWorkingSession()
        FunctionSlotId.ordered.forEachIndexed { index, id -> study = add(study, id, "x+${index + 1}/10") }
        val settings = FunctionValueTableSettings(
            FunctionValueTableGenerationMode.IntervalAndCount(0.0, 999.0, 1_000),
            selectedSlots = FunctionSlotId.ordered.toSet(),
            decimalPlaces = 12
        )
        val result = completed(study, settings).result
        assertEquals(1_000, result.abscissas.size)
        assertEquals(6, result.columns.size)
        assertEquals(6_000, result.columns.sumOf { it.cells.size })
        val value = (result.columns.first().cells[0] as FunctionValueTableCell.Value).value
        assertEquals(0.1, value, 1e-16)
        assertFalse(value == 0.0)
    }

    @Test fun invalidPrecisionAndInvalidGenerationAreReturnedAsBusinessErrors() {
        val study = add(FunctionStudy.newWorkingSession(), FunctionSlotId.F1, "x")
        val invalidPrecision = FunctionValueTableCalculator.calculate(study, FunctionValueTableSettings(decimalPlaces = 13))
            as FunctionValueTableCalculation.Invalid
        assertEquals(FunctionValueTableValidationError.InvalidDecimalPlaces, invalidPrecision.error)
        val invalidMode = FunctionValueTableCalculator.calculate(study, FunctionValueTableSettings(
            FunctionValueTableGenerationMode.IntervalAndStep(0.0, 1.0, -1.0)
        )) as FunctionValueTableCalculation.Invalid
        assertEquals(FunctionValueTableValidationError.NonPositiveStep, invalidMode.error)
    }
}
