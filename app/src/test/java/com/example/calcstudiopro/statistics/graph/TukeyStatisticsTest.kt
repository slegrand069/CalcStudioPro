package com.example.calcstudiopro.statistics.graph

import com.example.calcstudiopro.statistics.StatisticsEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TukeyStatisticsTest {
    @Test fun quartilesUseExactlyTheExistingStatisticsOneDConvention() {
        listOf(
            listOf(9.0),
            listOf(1.0, 9.0),
            listOf(1.0, 4.0, 10.0),
            listOf(1.0, 2.0, 3.0, 4.0, 10.0, 11.0)
        ).forEach { values ->
            val summary = TukeyStatistics.calculate(values).summary!!
            assertEquals(StatisticsEngine.quantile(values, 25.0)!!, summary.q1, 0.0)
            assertEquals(StatisticsEngine.quantile(values, 50.0)!!, summary.median, 0.0)
            assertEquals(StatisticsEngine.quantile(values, 75.0)!!, summary.q3, 0.0)
        }
    }

    @Test fun whiskersStopAtTheExtremeInlierAndOutliersRemainSeparate() {
        val summary = TukeyStatistics.calculate((1..10).map(Int::toDouble) + 100.0).summary!!
        assertEquals(1.0, summary.lowerWhisker, 0.0)
        assertEquals(10.0, summary.upperWhisker, 0.0)
        assertEquals(listOf(100.0), summary.outliers)
        assertEquals(5.0, summary.interquartileRange!!, 0.0)
    }

    @Test fun identicalOutliersKeepTheirMultiplicity() {
        val summary = TukeyStatistics.calculate((1..10).map(Int::toDouble) + listOf(100.0, 100.0)).summary!!
        assertEquals(listOf(100.0, 100.0), summary.outliers)
        assertEquals(2, summary.outlierMultiplicities[100.0])
    }

    @Test fun smallSamplesAndConstantSeriesRemainDefinedWithExistingQuartiles() {
        listOf(listOf(4.0), listOf(-2.0, 8.0), listOf(1.0, 2.0, 100.0), List(5) { 7.0 }).forEach { values ->
            val result = TukeyStatistics.calculate(values)
            assertNotNull(result.summary)
            assertTrue(result.summary!!.outliers.isEmpty())
        }
    }

    @Test fun emptyValuesAreExplainedAndNonFiniteValuesAreIgnored() {
        assertNull(TukeyStatistics.calculate(emptyList()).summary)
        val result = TukeyStatistics.calculate(listOf(1.0, 2.0, Double.NaN, Double.POSITIVE_INFINITY))
        assertEquals(2, result.summary!!.observationCount)
        assertEquals(2, result.summary!!.nonFiniteCount)
        assertTrue(result.message!!.contains("2 valeur(s) non finie(s)"))
    }

    @Test fun extremeFiniteValuesKeepFiniteWhiskersEvenWhenIqrOverflows() {
        val result = TukeyStatistics.calculate(listOf(-Double.MAX_VALUE, -Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE))
        val summary = result.summary!!
        assertTrue(summary.lowerWhisker.isFinite())
        assertTrue(summary.upperWhisker.isFinite())
        assertNull(summary.interquartileRange)
        assertTrue(result.message!!.contains("IQR hors de la plage"))
    }
}
