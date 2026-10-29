package com.example.calcstudiopro

import org.junit.Assert.assertEquals
import org.junit.Test

class ResultFormatterTest {
    @Test
    fun keepsDecimalFormattingBelowThreshold() {
        assertEquals("99999999999", formatResult(99_999_999_999.0))
    }

    @Test
    fun formatsThresholdInScientificNotation() {
        assertEquals("1 × 10¹¹", formatResult(100_000_000_000.0))
    }

    @Test
    fun formatsPositiveFractionalMantissa() {
        assertEquals("1,25 × 10¹¹", formatResult(125_000_000_000.0))
    }

    @Test
    fun formatsNegativeResultInScientificNotation() {
        assertEquals("-2,5 × 10¹¹", formatResult(-250_000_000_000.0))
    }

    @Test
    fun formatsNegativeThresholdInScientificNotation() {
        assertEquals("-1 × 10¹¹", formatResult(-100_000_000_000.0))
    }

    @Test
    fun formatsScientificResultsWithExponentsInMultiplesOfThree() {
        assertEquals("150,5 × 10³", formatEngineeringResult(150_500.0))
        assertEquals("1,5 × 10⁶", formatEngineeringResult(1_500_000.0))
        assertEquals("-1,2 × 10⁻⁶", formatEngineeringResult(-0.0000012))
        assertEquals("123.45", formatEngineeringResult(123.45))
        assertEquals("0", formatEngineeringResult(0.0))
    }
}
