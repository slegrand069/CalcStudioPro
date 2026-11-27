package com.example.calcstudiopro.functions.table

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FunctionValueTableAbscissaGeneratorTest {
    private fun valid(mode: FunctionValueTableGenerationMode) =
        FunctionValueTableAbscissaGenerator.generate(mode) as FunctionValueTableAbscissas.Valid

    private fun invalid(mode: FunctionValueTableGenerationMode) =
        FunctionValueTableAbscissaGenerator.generate(mode) as FunctionValueTableAbscissas.Invalid

    @Test fun intervalAndStepUsesIndexedValuesAndDoesNotAppendAnOffGridMaximum() {
        assertEquals(
            listOf(-2.0, -1.5, -1.0, -0.5, 0.0, 0.5, 1.0, 1.5, 2.0),
            valid(FunctionValueTableGenerationMode.IntervalAndStep(-2.0, 2.0, 0.5)).values
        )
        assertDoubles(listOf(-1.0, -0.4, 0.2, 0.8), valid(FunctionValueTableGenerationMode.IntervalAndStep(-1.0, 1.0, 0.6)).values)
        assertEquals(listOf(4.0), valid(FunctionValueTableGenerationMode.IntervalAndStep(4.0, 4.0, 1.0)).values)
    }

    @Test fun intervalAndStepAcceptsARepresentationallyRoundedEndpointOnlyWithinUlpError() {
        val result = valid(FunctionValueTableGenerationMode.IntervalAndStep(-0.3, 0.0, 0.1)).values
        assertEquals(4, result.size)
        assertEquals(0.0, result.last(), 0.0)
        assertDoubles(listOf(0.0, 0.3, 0.6, 0.9), valid(FunctionValueTableGenerationMode.IntervalAndStep(0.0, 1.0, 0.3)).values)
    }

    private fun assertDoubles(expected: List<Double>, actual: List<Double>) {
        assertEquals(expected.size, actual.size)
        expected.zip(actual).forEach { (left, right) -> assertEquals(left, right, 1e-14) }
    }

    @Test fun stepRejectsInvalidBoundsAndNonProgressingDoubleSteps() {
        assertEquals(FunctionValueTableValidationError.NonFiniteMinimum,
            invalid(FunctionValueTableGenerationMode.IntervalAndStep(Double.NaN, 1.0, 0.5)).error)
        assertEquals(FunctionValueTableValidationError.MinimumGreaterThanMaximum,
            invalid(FunctionValueTableGenerationMode.IntervalAndStep(2.0, 1.0, 0.5)).error)
        assertEquals(FunctionValueTableValidationError.NonPositiveStep,
            invalid(FunctionValueTableGenerationMode.IntervalAndStep(0.0, 1.0, 0.0)).error)
        assertEquals(FunctionValueTableValidationError.NonFiniteStep,
            invalid(FunctionValueTableGenerationMode.IntervalAndStep(0.0, 1.0, Double.POSITIVE_INFINITY)).error)
        assertEquals(FunctionValueTableValidationError.StepBelowDoubleResolution,
            invalid(FunctionValueTableGenerationMode.IntervalAndStep(1.0, 2.0, 1e-20)).error)
    }

    @Test fun stepChecksTheRowLimitBeforeCreatingTheOutputList() {
        assertEquals(1_000, valid(FunctionValueTableGenerationMode.IntervalAndStep(0.0, 999.0, 1.0)).values.size)
        assertEquals(FunctionValueTableValidationError.TooManyRows,
            invalid(FunctionValueTableGenerationMode.IntervalAndStep(0.0, 1_000.0, 1.0)).error)
    }

    @Test fun intervalAndCountHonorsOneTwoAndMaximumPointCases() {
        assertEquals(listOf(-4.0), valid(FunctionValueTableGenerationMode.IntervalAndCount(-4.0, 8.0, 1)).values)
        assertEquals(listOf(-4.0, 8.0), valid(FunctionValueTableGenerationMode.IntervalAndCount(-4.0, 8.0, 2)).values)
        val thousand = valid(FunctionValueTableGenerationMode.IntervalAndCount(-5.0, 5.0, 1_000)).values
        assertEquals(1_000, thousand.size)
        assertEquals(-5.0, thousand.first(), 0.0)
        assertEquals(5.0, thousand.last(), 0.0)
        assertTrue(thousand.zipWithNext().all { (a, b) -> a <= b })
    }

    @Test fun intervalAndCountAvoidsOverflowForExtremeFiniteBounds() {
        val values = valid(FunctionValueTableGenerationMode.IntervalAndCount(-Double.MAX_VALUE, Double.MAX_VALUE, 3)).values
        assertEquals(-Double.MAX_VALUE, values[0], 0.0)
        assertEquals(0.0, values[1], 0.0)
        assertEquals(Double.MAX_VALUE, values[2], 0.0)
        assertEquals(FunctionValueTableValidationError.InvalidPointCount,
            invalid(FunctionValueTableGenerationMode.IntervalAndCount(0.0, 1.0, 1_001)).error)
    }

    @Test fun customValuesPreserveOrderDuplicatesAndFrenchDecimals() {
        assertEquals(
            listOf(2.5, -1.25, 2.5, 7.0),
            valid(FunctionValueTableGenerationMode.CustomValues("2,5; -1,25\r\n2,5;7")).values
        )
    }

    @Test fun customValuesAcceptSpacesAsSeparatorsWithFrenchDecimalCommas() {
        assertEquals(
            listOf(-1.0, 2.5, 4.0, -0.25),
            valid(FunctionValueTableGenerationMode.CustomValues("-1 2,5\t4\n-0,25")).values
        )
    }

    @Test fun customValuesRejectMalformedAndNonFiniteTokens() {
        assertEquals(FunctionValueTableValidationError.MalformedCustomValue(2, "oops"),
            invalid(FunctionValueTableGenerationMode.CustomValues("1; oops")).error)
        assertEquals(FunctionValueTableValidationError.NonFiniteCustomValue(1),
            invalid(FunctionValueTableGenerationMode.CustomValues("NaN")).error)
        assertEquals(FunctionValueTableValidationError.EmptyCustomValues,
            invalid(FunctionValueTableGenerationMode.CustomValues(" ; \n ")).error)
    }

    @Test fun customValuesRejectMoreThanOneThousandRowsWithoutTruncating() {
        val input = (0..1_000).joinToString(";")
        assertEquals(FunctionValueTableValidationError.TooManyRows,
            invalid(FunctionValueTableGenerationMode.CustomValues(input)).error)
    }
}
