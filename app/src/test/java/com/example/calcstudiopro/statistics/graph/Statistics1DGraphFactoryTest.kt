package com.example.calcstudiopro.statistics.graph

import com.example.calcstudiopro.statistics.graph.model.GraphSeriesKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Statistics1DGraphFactoryTest {
    @Test fun histogramFactoryKeepsClassBoundsAndSelectionDetails() {
        val histogram = HistogramStatistics.calculate(listOf(1.0, 2.0, 3.0, 4.0), HistogramClassMode.MANUAL, 2)
        val graph = Statistics1DGraphFactory.histogram(histogram, HistogramScale.PERCENT)
        val series = graph.series.single()
        assertEquals(GraphSeriesKind.BARS, series.kind)
        assertEquals(histogram.bins.size, series.bars.size)
        assertEquals(50.0, series.bars.first().yMaximum, 0.0)
        assertTrue(series.bars.first().details.any { it.label == "Borne inférieure [incluse]" && it.value == 1.0 })
        assertTrue(series.bars.first().details.any { it.label == "Effectif" && it.value == 2.0 })
        assertTrue(series.bars.first().details.any { it.label == "Fréquence" && it.value == 50.0 && it.percentage })
        assertTrue(series.bars.last().details.any { it.label == "Borne supérieure incluse" })
        assertTrue(series.bars.first().details.any { it.label == "Borne supérieure exclue" })
    }

    @Test fun duplicateOutliersRemainSeparatePointsWithDistinctVisualOffsets() {
        val result = TukeyStatistics.calculate((1..10).map(Int::toDouble) + listOf(100.0, 100.0))
        val graph = Statistics1DGraphFactory.tukeyBoxPlot(result)
        val structures = graph.series.first()
        val outliers = graph.series.last().points
        assertEquals(1, structures.bars.size)
        assertEquals(9, structures.segments.size)
        assertFalse(graph.showYAxis)
        assertFalse(graph.showGrid)
        assertEquals(2, outliers.size)
        assertNotEquals(outliers[0].id, outliers[1].id)
        assertNotEquals(outliers[0].y, outliers[1].y)
        assertTrue(outliers.all { it.details.any { detail -> detail.label == "Multiplicité" && detail.value == 2.0 } })
    }

    @Test fun constantSingletonProducesAVisibleNonZeroBoxAndCanBeFramed() {
        val graph = Statistics1DGraphFactory.tukeyBoxPlot(TukeyStatistics.calculate(listOf(Double.MIN_VALUE)))
        val viewport = com.example.calcstudiopro.statistics.graph.domain.GraphRange.autoFit(graph)
        val box = graph.series.first().bars.single()
        assertTrue(box.xMinimum < box.xMaximum)
        assertTrue(viewport.x.minimum < Double.MIN_VALUE && viewport.x.maximum > Double.MIN_VALUE)
        assertTrue(graph.series.last().points.isEmpty())
    }

    @Test fun tenThousandObservationsProduceBoundedClassesAndTukeyData() {
        val values = (0 until 10_000).map(Int::toDouble)
        val histogram = HistogramStatistics.calculate(values)
        val histogramGraph = Statistics1DGraphFactory.histogram(histogram, HistogramScale.COUNT)
        val tukey = TukeyStatistics.calculate(values)
        val tukeyGraph = Statistics1DGraphFactory.tukeyBoxPlot(tukey)
        assertEquals(10_000, histogram.bins.sumOf { it.count })
        assertEquals(15, histogramGraph.series.single().bars.size)
        assertEquals(0, tukey.summary!!.outliers.size)
        assertEquals(9, tukeyGraph.series.first().segments.size)
    }
}
