package com.example.calcstudiopro

import com.example.calcstudiopro.domain.NumberBase
import org.junit.Assert.assertEquals
import org.junit.Test

class ProgrammerResultFormatterTest {
    @Test
    fun formatsValuesInEveryBase() {
        assertEquals("1010", formatProgrammerResult(10L, NumberBase.BIN))
        assertEquals("12", formatProgrammerResult(10L, NumberBase.OCT))
        assertEquals("10", formatProgrammerResult(10L, NumberBase.DEC))
        assertEquals("A", formatProgrammerResult(10L, NumberBase.HEX))
    }

    @Test
    fun formatsNegativeValuesAsSixtyFourBitTwoComplement() {
        assertEquals("FFFFFFFFFFFFFFFF", formatProgrammerResult(-1L, NumberBase.HEX))
        assertEquals("1" + "0".repeat(63), formatProgrammerResult(Long.MIN_VALUE, NumberBase.BIN))
        assertEquals("-1", formatProgrammerResult(-1L, NumberBase.DEC))
    }

    @Test
    fun groupsProgrammerResultsIntoBlocksOfFourWithoutChangingDecimalDisplay() {
        assertEquals("1100 0101", formatProgrammerResultForDisplay(0b11000101L, NumberBase.BIN))
        assertEquals("12 3456", formatProgrammerResultForDisplay(0x123456L, NumberBase.HEX))
        assertEquals("FFFF FFFF FFFF FFFF", formatProgrammerResultForDisplay(-1L, NumberBase.HEX))
        assertEquals("21", formatProgrammerResultForDisplay(21L, NumberBase.DEC))
    }
}
