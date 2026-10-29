package com.example.calcstudiopro.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ProgrammerExpressionConverterTest {
    private val converter = ProgrammerExpressionConverter()

    @Test
    fun convertsDecimalLiteralsByTruncatingFractionalParts() {
        assertEquals("A+2", converter.convert("10.97+2", NumberBase.DEC, NumberBase.HEX))
        assertEquals("FFFFFFFFFFFFFFF6", converter.convert("-10.97", NumberBase.DEC, NumberBase.HEX))
        assertEquals("FFFFFFFFFFFFFFF6", converter.convert("−10,97", NumberBase.DEC, NumberBase.HEX))
        assertEquals("A", converter.convert("10,97", NumberBase.DEC, NumberBase.HEX))
        assertEquals("8000000000000000", converter.convert("−9223372036854775808", NumberBase.DEC, NumberBase.HEX))
    }

    @Test
    fun convertsEveryLiteralWithoutChangingOperatorsOrParentheses() {
        assertEquals("6A AND 3B", converter.convert("106 AND 59", NumberBase.DEC, NumberBase.HEX))
        assertEquals("106 AND 59", converter.convert("6A AND 3B", NumberBase.HEX, NumberBase.DEC))
        assertEquals("1010+(11)", converter.convert("10+(3)", NumberBase.DEC, NumberBase.BIN))
        assertEquals("pow(A,61)", converter.convert("pow(10,97)", NumberBase.DEC, NumberBase.HEX))
    }

    @Test
    fun reportsOverflowWhenDecimalLiteralCannotFitInSixtyFourBits() {
        assertEquals("Overflow", assertThrows(ArithmeticException::class.java) {
            converter.convert("9223372036854775808", NumberBase.DEC, NumberBase.HEX)
        }.message)
    }
}
