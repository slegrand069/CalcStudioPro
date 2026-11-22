package com.example.calcstudiopro.statistics.graph

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HistogramStatisticsTest {
    @Test fun sturgesRuleProducesAtLeastOneClass() {
        assertEquals(1, HistogramStatistics.sturgesClassCount(0))
        assertEquals(1, HistogramStatistics.sturgesClassCount(1))
        assertEquals(2, HistogramStatistics.sturgesClassCount(2))
        assertEquals(4, HistogramStatistics.sturgesClassCount(8))
        assertEquals(5, HistogramStatistics.sturgesClassCount(10))
    }

    @Test fun halfOpenBoundariesSendExactEdgeValuesToTheFollowingClass() {
        val result = HistogramStatistics.calculate(listOf(0.0, 1.0, 2.0, 3.0, 4.0), HistogramClassMode.MANUAL, 2)
        assertEquals(listOf(0.0, 2.0, 4.0), listOf(result.bins.first().lower, result.bins.first().upper, result.bins.last().upper))
        assertEquals(listOf(2, 3), result.bins.map { it.count })
        assertEquals(5, result.bins.sumOf { it.count })
    }

    @Test fun manualCountsAreCappedAndPercentagesSumToOneHundred() {
        val result = HistogramStatistics.calculate(
            listOf(-3.5, -2.0, -0.25, 0.0, 1.75, 4.0), HistogramClassMode.MANUAL, 99
        )
        assertEquals(6, result.bins.size)
        assertEquals(6, result.requestedClassCount)
        assertEquals(6, result.bins.sumOf { it.count })
        assertEquals(100.0, result.bins.sumOf { it.percent }, 1e-12)
        assertTrue(result.bins.zipWithNext().all { (a, b) -> a.upper == b.lower })
    }

    @Test fun automaticModeAndManualModeHandleConstantAndExtremeValues() {
        val constant = HistogramStatistics.calculate(List(7) { -12.5 })
        assertEquals(1, constant.bins.size)
        assertEquals(7, constant.bins.single().count)
        assertTrue(constant.bins.single().lower < -12.5 && constant.bins.single().upper > -12.5)

        val extremes = HistogramStatistics.calculate(listOf(-Double.MAX_VALUE, 0.0, Double.MAX_VALUE), HistogramClassMode.MANUAL, 2)
        assertEquals(3, extremes.bins.sumOf { it.count })
        assertTrue(extremes.bins.all { it.lower.isFinite() && it.upper.isFinite() && it.lower < it.upper })
        assertTrue(extremes.bins.zipWithNext().all { (a, b) -> a.upper == b.lower })

        val tiny = HistogramStatistics.calculate(listOf(Double.MIN_VALUE, Math.nextUp(Double.MIN_VALUE)), HistogramClassMode.MANUAL, 10)
        assertTrue(tiny.bins.isNotEmpty())
        assertEquals(2, tiny.bins.sumOf { it.count })
        assertTrue(tiny.bins.all { it.lower < it.upper })
    }

    @Test fun emptyAndNonFiniteValuesAreReportedWithoutLosingFiniteCounts() {
        assertTrue(HistogramStatistics.calculate(emptyList()).bins.isEmpty())
        val result = HistogramStatistics.calculate(listOf(1.0, Double.NaN, 2.0, Double.POSITIVE_INFINITY))
        assertEquals(2, result.observationCount)
        assertEquals(2, result.nonFiniteCount)
        assertEquals(2, result.bins.sumOf { it.count })
        assertTrue(result.message!!.contains("2 valeur(s) non finie(s)"))
    }

    @Test fun manualClassLimitIsOneHundredAndNeverExceedsObservationCount() {
        val small = HistogramStatistics.calculate(listOf(1.0, 2.0), HistogramClassMode.MANUAL, 1000)
        assertTrue(small.bins.size <= 2)
        val large = HistogramStatistics.calculate((0 until 10_000).map(Int::toDouble), HistogramClassMode.MANUAL, 1000)
        assertEquals(100, large.bins.size)
        assertEquals(10_000, large.bins.sumOf { it.count })
    }
}
