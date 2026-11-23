package com.example.calcstudiopro.statistics

import org.junit.Assert.*
import org.junit.Test

class StatisticsInputParserTest {
    @Test fun parsesFrenchDecimalListsWithoutTreatingCommaAsSeparator() {
        val parsed = StatisticsInputParser.oneD("1,5\n2.25\t-3") as StatisticsInputParser.Parsed.Valid
        assertEquals(listOf(1.5, 2.25, -3.0), parsed.value)
        assertTrue(StatisticsInputParser.oneD("1,2,3") is StatisticsInputParser.Parsed.Invalid)
        assertTrue(StatisticsInputParser.oneD("1;;3") is StatisticsInputParser.Parsed.Invalid)
        assertTrue(StatisticsInputParser.oneD("1\n\n3") is StatisticsInputParser.Parsed.Invalid)
    }

    @Test fun parsesSpreadsheetPairsAndReportsInvalidRows() {
        val parsed = StatisticsInputParser.twoD("X\tY\n1,5\t2,5\n3\t4") as StatisticsInputParser.Parsed.Valid
        assertEquals(listOf(Statistics2DEngine.PairValue(1.5, 2.5), Statistics2DEngine.PairValue(3.0, 4.0)), parsed.value)
        val invalid = StatisticsInputParser.twoD("1\t2\n3\t") as StatisticsInputParser.Parsed.Invalid
        assertEquals(2, invalid.line)
        assertTrue(StatisticsInputParser.twoD("1,2,3") is StatisticsInputParser.Parsed.Invalid)
        assertTrue(StatisticsInputParser.twoD("1\t2\n\n3\t4") is StatisticsInputParser.Parsed.Invalid)
    }

    @Test fun parsesSlashSeparatedInlinePairsIncludingFrenchDecimals() {
        val parsed = StatisticsInputParser.twoD("6;30 / 7;32 / 8,5;64,25") as StatisticsInputParser.Parsed.Valid
        assertEquals(
            listOf(
                Statistics2DEngine.PairValue(6.0, 30.0),
                Statistics2DEngine.PairValue(7.0, 32.0),
                Statistics2DEngine.PairValue(8.5, 64.25)
            ),
            parsed.value
        )
        assertTrue(StatisticsInputParser.twoD("6;30 /") is StatisticsInputParser.Parsed.Invalid)
        assertTrue(StatisticsInputParser.twoD("6;30 / 7;") is StatisticsInputParser.Parsed.Invalid)
    }

    @Test fun parsesGroupedCountsAsPositiveIntegersOnly() {
        val parsed = StatisticsInputParser.frequencies("1,5;2\n3;4") as StatisticsInputParser.Parsed.Valid
        assertEquals(listOf(StatisticsEngine.Frequency(1.5, 2), StatisticsEngine.Frequency(3.0, 4)), parsed.value)
        assertTrue(StatisticsInputParser.frequencies("1;0") is StatisticsInputParser.Parsed.Invalid)
        assertTrue(StatisticsInputParser.frequencies("1;1.5") is StatisticsInputParser.Parsed.Invalid)
    }

    @Test fun sessionDataCodecRoundTripsBothDimensionsAndRejectsCorruption() {
        val oneD = StoredStatisticsData.OneD(listOf(1.5, -2.0, 1.5), grouped = true)
        val twoD = StoredStatisticsData.TwoD(listOf(Statistics2DEngine.PairValue(1.25, -3.0), Statistics2DEngine.PairValue(4.0, 5.5)))
        assertEquals(oneD, StatisticsDataCodec.decode(StatisticsDataCodec.encode(oneD)))
        assertEquals(twoD, StatisticsDataCodec.decode(StatisticsDataCodec.encode(twoD)))
        assertNull(StatisticsDataCodec.decode("{\"version\":1,\"dimension\":\"1D\",\"values\":[NaN]}"))
        assertNull(StatisticsDataCodec.decode("{\"version\":2,\"dimension\":\"2D\",\"pairs\":[]}"))
    }
}
