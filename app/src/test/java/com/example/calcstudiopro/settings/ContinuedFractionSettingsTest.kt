package com.example.calcstudiopro.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class ContinuedFractionSettingsTest {
    @Test
    fun calculatorPreferencesDefaultToDegreesAndStandardNotation() {
        assertEquals(com.example.calcstudiopro.domain.AngleUnit.DEGREES, AppSettings().angleUnit)
        assertEquals(CalculatorNotation.STANDARD, AppSettings().notation)
    }

    @Test
    fun defaultsAreCentralizedAndExpected() {
        assertEquals(1e-10, ContinuedFractionSettings().tolerance, 0.0)
        assertEquals(10_000, ContinuedFractionSettings().maxDenominator)
        assertEquals(64, ContinuedFractionSettings().maxIterations)
        assertEquals(ResultPresentation.FRACTION, ContinuedFractionSettings().resultPresentation)
    }

    @Test
    fun acceptsValidNumericSettings() {
        assertEquals(ContinuedFractionSettings(1e-8, 1, 1), ContinuedFractionSettings(1e-8, 1, 1))
    }

    @Test
    fun rejectsInvalidNumericSettings() {
        listOf(0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY).forEach { tolerance ->
            kotlin.runCatching { ContinuedFractionSettings(tolerance = tolerance) }
                .onSuccess { throw AssertionError("Accepted invalid tolerance: $tolerance") }
        }
        listOf(0, -1).forEach { value ->
            kotlin.runCatching { ContinuedFractionSettings(maxDenominator = value) }
                .onSuccess { throw AssertionError("Accepted invalid denominator: $value") }
            kotlin.runCatching { ContinuedFractionSettings(maxIterations = value) }
                .onSuccess { throw AssertionError("Accepted invalid iteration count: $value") }
        }
    }
}
