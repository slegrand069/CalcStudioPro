package com.example.calcstudiopro.statistics

import org.junit.Assert.*
import org.junit.Test

class StatisticsEngineTest {
    private fun valid(input: String) = StatisticsEngine.analyze(input) as StatisticsEngine.Analysis.Valid

    @Test fun referenceSeriesAndPopulationDeviation() {
        val a = valid("1 2 3 4 5").result
        assertEquals(5, a.count); assertEquals(15.0, a.sum, 1e-12); assertEquals(3.0, a.mean, 1e-12)
        assertEquals(3.0, a.median, 1e-12); assertEquals(1.0, a.minimum, 0.0); assertEquals(5.0, a.maximum, 0.0)
        assertEquals(kotlin.math.sqrt(2.0), a.populationStandardDeviation, 1e-12)
        val b = valid("2 4 4 4 5 5 7 9").result
        assertEquals(40.0, b.sum, 1e-12); assertEquals(4.5, b.median, 1e-12); assertEquals(2.0, b.populationStandardDeviation, 1e-12)
        val c = valid("42").result
        assertEquals(0.0, c.populationStandardDeviation, 0.0)
    }

    @Test fun parsingAndInvalidInputs() {
        assertEquals(listOf(1.5, 2.75, -3.0, 4.25), valid("1,5 2.75 -3 4.25").values)
        assertEquals(listOf(1.0, 2.0, 3.0), valid("1\n2\t3").values)
        assertEquals(0.0, valid("0 0 0").result.populationStandardDeviation, 0.0)
        listOf("", "  ", "12;;15", "12;15;", "12;abc;15", "1,2,3 4", "NaN", "Infinity", "1e999")
            .forEach { assertTrue("$it should fail", StatisticsEngine.analyze(it) is StatisticsEngine.Analysis.Invalid) }
    }

    @Test fun extremesSizeAndFormatting() {
        assertTrue(valid("1e12 1000000000000,001 1000000000000,002").result.populationStandardDeviation < 0.01) // close large values stay stable
        assertTrue(StatisticsEngine.analyze((1..(StatisticsEngine.MAX_VALUES + 1)).joinToString(" ")) is StatisticsEngine.Analysis.Invalid)
        assertEquals("1,5", StatisticsEngine.format(1.5))
        assertTrue(StatisticsEngine.analyze("1e308 1e308") is StatisticsEngine.Analysis.Invalid)
    }
}
