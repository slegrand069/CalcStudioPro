package com.example.calcstudiopro.history.domain

import com.example.calcstudiopro.formatResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CalculatorHistoryValueTest {
    @Test
    fun canonicalValueReusesTheCalculatedDoubleWithoutUsingRoundedDisplayText() {
        val value = 1.0 / 3.0
        val canonical = CalculatorHistoryValue.canonical(value)

        assertEquals("0.3333333333333333", canonical)
        assertEquals(
            "0.3333333333333333",
            CalculatorHistoryValue.toCalculatorExpression(canonical, HistoryValueTypes.DECIMAL_DOUBLE)
        )
        assertEquals("0.3333333333", formatResult(value))
    }

    @Test
    fun acceptsFiniteValuesWhenCalculatorEvaluationRoundTripsExactly() {
        listOf(0.0, -0.0, 12.5, Double.MIN_VALUE, Double.MAX_VALUE).forEach { value ->
            val expression = CalculatorHistoryValue.toCalculatorExpression(
                CalculatorHistoryValue.canonical(value),
                HistoryValueTypes.DECIMAL_DOUBLE
            )
            requireNotNull(expression)
            val evaluated = com.example.calcstudiopro.domain.CalculatorEngine().evaluate(expression)
            assertEquals(value.toRawBits(), evaluated.toRawBits())
        }
    }

    @Test
    fun refusesUnknownTypesMalformedValuesAndNonCanonicalValues() {
        assertNull(CalculatorHistoryValue.toCalculatorExpression("12.0", "big_integer"))
        assertNull(CalculatorHistoryValue.toCalculatorExpression("NaN", HistoryValueTypes.DECIMAL_DOUBLE))
        assertNull(CalculatorHistoryValue.toCalculatorExpression("Infinity", HistoryValueTypes.DECIMAL_DOUBLE))
        assertNull(CalculatorHistoryValue.toCalculatorExpression("1.0E3", HistoryValueTypes.DECIMAL_DOUBLE))
        assertNull(CalculatorHistoryValue.toCalculatorExpression("1 + 2", HistoryValueTypes.DECIMAL_DOUBLE))
        assertNull(CalculatorHistoryValue.canonical(Double.NaN))
        assertNull(CalculatorHistoryValue.canonical(Double.POSITIVE_INFINITY))
    }
}
