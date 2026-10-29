package com.example.calcstudiopro.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ProgrammerCalculatorEngineTest {
    private val engine = ProgrammerCalculatorEngine()

    @Test
    fun parsesValuesInBinaryOctalDecimalAndHexadecimal() {
        assertEquals(10L, engine.evaluate("1010", NumberBase.BIN))
        assertEquals(15L, engine.evaluate("17", NumberBase.OCT))
        assertEquals(42L, engine.evaluate("42", NumberBase.DEC))
        assertEquals(106L, engine.evaluate("6A", NumberBase.HEX))
    }

    @Test
    fun evaluatesLogicalOperationsWithRequestedPrecedence() {
        assertEquals(0L, engine.evaluate("1 XOR 1 OR 1", NumberBase.BIN))
        assertEquals(1L, engine.evaluate("1 OR 0 AND 0", NumberBase.BIN))
        assertEquals(0x2AL, engine.evaluate("6A AND 3B", NumberBase.HEX))
        assertEquals(0x7BL, engine.evaluate("6A OR 3B", NumberBase.HEX))
        assertEquals(0x51L, engine.evaluate("6A XOR 3B", NumberBase.HEX))
    }

    @Test
    fun evaluatesNotParenthesesPowerAndFactorial() {
        assertEquals(-1L, engine.evaluate("NOT 0", NumberBase.HEX))
        assertEquals(0x2AL, engine.evaluate("(6A AND 3B)", NumberBase.HEX))
        assertEquals(1024L, engine.evaluate("2^10", NumberBase.DEC))
        assertEquals(1024L, engine.evaluate("pow(2,10)", NumberBase.DEC))
        assertEquals(120L, engine.evaluate("5!", NumberBase.DEC))
    }

    @Test
    fun reportsOverflowForArithmeticAndOversizedLiterals() {
        assertEquals("Overflow", assertThrows(ArithmeticException::class.java) {
            engine.evaluate("9223372036854775807+1", NumberBase.DEC)
        }.message)
        assertEquals("Overflow", assertThrows(ArithmeticException::class.java) {
            engine.evaluate("10000000000000000", NumberBase.HEX)
        }.message)
        assertEquals("Overflow", assertThrows(ArithmeticException::class.java) {
            engine.evaluate("21!", NumberBase.DEC)
        }.message)
    }

    @Test
    fun supportsTheSignedSixtyFourBitMinimum() {
        assertEquals(Long.MIN_VALUE, engine.evaluate("-9223372036854775808", NumberBase.DEC))
        assertEquals(Long.MIN_VALUE, engine.evaluate("8000000000000000", NumberBase.HEX))
    }

    @Test
    fun shiftsByOneAndDetectsLeftShiftOverflow() {
        assertEquals(20L, engine.shiftLeft(10L))
        assertEquals(5L, engine.shiftRight(10L))
        assertEquals("Overflow", assertThrows(ArithmeticException::class.java) {
            engine.shiftLeft(Long.MAX_VALUE)
        }.message)
    }

    @Test
    fun rejectsNegativePowerWithNonIntegerResult() {
        assertThrows(IllegalArgumentException::class.java) {
            engine.evaluate("2^-1", NumberBase.DEC)
        }
    }
}
