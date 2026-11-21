package com.example.calcstudiopro.statistics

import org.junit.Assert.*
import org.junit.Test

class StatisticsInputParserTest {
    @Test fun parsesFrenchDecimalListsWithoutTreatingCommaAsSeparator() {
        val parsed = StatisticsInputParser.oneD("1,5\n2.25\t-3") as StatisticsInputParser.Parsed.Valid
        assertEquals(listOf(1.5, 2.25, -3.0), parsed.value)
        assertTrue(StatisticsInputParser.oneD("1,2,3") is StatisticsInputParser.Parsed.Invalid)
        assertTrue(StatisticsInputParser.oneD("1;;3") is StatisticsInputParser.Parsed.Invalid)
    }

    @Test fun parsesSpreadsheetPairsAndReportsInvalidRows() {
        val parsed = StatisticsInputParser.twoD("X\tY\n1,5\t2,5\n3\t4") as StatisticsInputParser.Parsed.Valid
        assertEquals(listOf(Statistics2DEngine.PairValue(1.5, 2.5), Statistics2DEngine.PairValue(3.0, 4.0)), parsed.value)
        val invalid = StatisticsInputParser.twoD("1\t2\n3\t") as StatisticsInputParser.Parsed.Invalid
        assertEquals(2, invalid.line)
        assertTrue(StatisticsInputParser.twoD("1,2,3") is StatisticsInputParser.Parsed.Invalid)
    }
}
