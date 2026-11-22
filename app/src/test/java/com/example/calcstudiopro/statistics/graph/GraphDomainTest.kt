package com.example.calcstudiopro.statistics.graph

import com.example.calcstudiopro.statistics.graph.domain.GraphHitTesting
import com.example.calcstudiopro.statistics.graph.domain.GraphPaths
import com.example.calcstudiopro.statistics.graph.domain.GraphRange
import com.example.calcstudiopro.statistics.graph.domain.GraphTicks
import com.example.calcstudiopro.statistics.graph.domain.GraphNumberFormatter
import com.example.calcstudiopro.statistics.graph.domain.GraphTransform
import com.example.calcstudiopro.statistics.graph.domain.panByPixels
import com.example.calcstudiopro.statistics.graph.domain.zoomAt
import com.example.calcstudiopro.statistics.graph.model.AxisRange
import com.example.calcstudiopro.statistics.graph.model.DataPoint
import com.example.calcstudiopro.statistics.graph.model.GraphPoint
import com.example.calcstudiopro.statistics.graph.model.GraphSelection
import com.example.calcstudiopro.statistics.graph.model.GraphSeries
import com.example.calcstudiopro.statistics.graph.model.GraphSeriesKind
import com.example.calcstudiopro.statistics.graph.model.GraphSnapshot
import com.example.calcstudiopro.statistics.graph.model.GraphViewport
import com.example.calcstudiopro.statistics.graph.model.GraphTickMode
import com.example.calcstudiopro.statistics.graph.model.PlotRect
import com.example.calcstudiopro.statistics.graph.model.ScreenPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class GraphDomainTest {
    @Test fun dataAndScreenTransformsRoundTripAcrossNormalAndExtremeRanges() {
        val ordinary = GraphTransform(GraphViewport(AxisRange(-10.0, 10.0), AxisRange(-5.0, 5.0)), PlotRect(30.0, 20.0, 300.0, 200.0))
        val point = DataPoint(3.25, -1.75)
        val screen = ordinary.dataToScreen(point)
        assertEquals(228.75, screen.x, 1e-12)
        assertEquals(155.0, screen.y, 1e-12)
        val restored = ordinary.screenToData(screen)
        assertEquals(point.x, restored.x, 1e-12)
        assertEquals(point.y, restored.y, 1e-12)

        val extreme = GraphTransform(
            GraphViewport(AxisRange(-Double.MAX_VALUE, Double.MAX_VALUE), AxisRange(-Double.MIN_VALUE, Double.MAX_VALUE)),
            PlotRect(0.0, 0.0, 800.0, 400.0)
        )
        val extremePoint = DataPoint(-Double.MAX_VALUE / 2.0, Double.MAX_VALUE / 3.0)
        val extremeScreen = extreme.dataToScreen(extremePoint)
        assertTrue(extremeScreen.x.isFinite() && extremeScreen.y.isFinite())
        val extremeRestored = extreme.screenToData(extremeScreen)
        assertTrue(abs((extremeRestored.x - extremePoint.x) / extremePoint.x) < 1e-14)
        assertTrue(abs((extremeRestored.y - extremePoint.y) / extremePoint.y) < 1e-14)
    }

    @Test fun autoFitPadsConstantAxesAndIgnoresNonFiniteObservations() {
        val constant = GraphSnapshot("constant", "x", "y", listOf(GraphSeries("s", "s", listOf(
            GraphPoint("a", 0.0, Double.MAX_VALUE), GraphPoint("b", 0.0, Double.MAX_VALUE),
            GraphPoint("ignored", Double.NaN, 1.0)
        ))))
        val bounds = GraphRange.autoFit(constant)
        assertTrue(bounds.x.minimum < 0.0 && bounds.x.maximum > 0.0)
        assertTrue(bounds.y.minimum.isFinite() && bounds.y.maximum.isFinite())
        assertTrue(bounds.y.minimum <= Double.MAX_VALUE && bounds.y.maximum == Double.MAX_VALUE)

        val tiny = GraphRange.autoFit(GraphSnapshot("tiny", "x", "y", listOf(
            GraphSeries("t", "t", listOf(GraphPoint("1", 1e-300, -1e-300), GraphPoint("2", 1e-300, -1e-300)))
        )))
        assertTrue(tiny.x.minimum < 1e-300 && tiny.x.maximum > 1e-300)
        assertTrue(tiny.y.minimum < -1e-300 && tiny.y.maximum > -1e-300)

        val empty = GraphRange.autoFit(GraphSnapshot("empty", "x", "y", emptyList()))
        assertEquals(AxisRange(-1.0, 1.0), empty.x)
        assertEquals(AxisRange(-1.0, 1.0), empty.y)
    }

    @Test fun ticksStayFiniteOrderedReadableAndBoundedForExtremeScales() {
        val ticks = GraphTicks.calculate(AxisRange(-Double.MAX_VALUE, Double.MAX_VALUE))
        assertTrue(ticks.isNotEmpty() && ticks.size <= 20)
        assertTrue(ticks.all { it.value.isFinite() && it.label.isNotBlank() })
        assertTrue(ticks.zipWithNext().all { (a, b) -> a.value < b.value })
        assertEquals("1,23456", GraphTicks.format(1.23456))
        assertTrue(GraphTicks.calculate(AxisRange(-1e-300, 1e-300)).all { it.value.isFinite() })
        val subnormal = GraphTicks.calculate(AxisRange(-Double.MIN_VALUE, 2.0 * Double.MIN_VALUE))
        assertTrue(subnormal.isNotEmpty() && subnormal.all { it.value.isFinite() })
    }

    @Test fun ticksUseReadableNiceStepsAndHistogramSpecificFormats() {
        val ticks = GraphTicks.calculate(AxisRange(0.0, 12.0), targetCount = 6)
        assertTrue(ticks.map { it.value }.zipWithNext().all { (a, b) -> abs((b - a) - 2.0) < 1e-12 })
        val counts = GraphTicks.calculate(AxisRange(-0.1, 4.1), targetCount = 6, mode = GraphTickMode.INTEGER)
        assertTrue(counts.isNotEmpty() && counts.all { it.value % 1.0 == 0.0 })
        val percentages = GraphTicks.calculate(AxisRange(0.0, 100.0), targetCount = 5, mode = GraphTickMode.PERCENT)
        assertTrue(percentages.any { it.label.endsWith("%") })
        assertEquals("23,75", GraphNumberFormatter.format(23.750000000000004))
        assertEquals("0,00 %", GraphNumberFormatter.format(-0.0001, percentage = true))
        assertFalse(GraphNumberFormatter.format(1e-30).contains("-0"))
        assertTrue(GraphNumberFormatter.format(1e-30).contains("E"))
    }

    @Test fun zoomKeepsFocalPointFixedAndPanFollowsTheFinger() {
        val plot = PlotRect(0.0, 0.0, 400.0, 200.0)
        val initial = GraphViewport(AxisRange(-10.0, 10.0), AxisRange(-5.0, 5.0))
        val focus = DataPoint(2.0, -1.0)
        val fixedScreen = GraphTransform(initial, plot).dataToScreen(focus)
        val zoomed = initial.zoomAt(focus, 2.0)
        val zoomedScreen = GraphTransform(zoomed, plot).dataToScreen(focus)
        assertEquals(fixedScreen.x, zoomedScreen.x, 1e-10)
        assertEquals(fixedScreen.y, zoomedScreen.y, 1e-10)

        val moved = initial.panByPixels(20.0, 10.0, plot)
        val movedScreen = GraphTransform(moved, plot).dataToScreen(focus)
        assertEquals(fixedScreen.x + 20.0, movedScreen.x, 1e-10)
        assertEquals(fixedScreen.y + 10.0, movedScreen.y, 1e-10)
    }

    @Test fun touchDetectionRetainsSuperimposedAndNearbyObservationsInDistanceOrder() {
        val a = GraphPoint("a", 10.0, 20.0)
        val b = GraphPoint("b", 10.0, 20.0)
        val c = GraphPoint("c", 10.1, 20.1)
        val points = listOf(GraphPoint("far-1", 0.0, 0.0), a, b, c, GraphPoint("far-2", 100.0, 100.0))
        val snapshot = GraphSnapshot("hits", "X", "Y", listOf(GraphSeries("observations", "data", points)))
        val viewport = GraphRange.autoFit(snapshot)
        val plot = PlotRect(0.0, 0.0, 400.0, 300.0)
        val touch = GraphTransform(viewport, plot).dataToScreen(DataPoint(a.x, a.y))
        val hits = GraphHitTesting.pointsNear(snapshot, viewport, plot, touch, 20.0)
        assertEquals(3, hits.size)
        assertEquals(listOf("a", "b", "c"), hits.map { it.point.id })
        assertEquals(hits[0].distancePixels, hits[1].distancePixels, 0.0)
    }

    @Test fun lineSegmentsBreakOnExplicitDiscontinuitiesAndNonFinitePoints() {
        val points = listOf(
            GraphPoint("a", 0.0, 0.0, connectFromPrevious = false),
            GraphPoint("b", 1.0, 1.0),
            GraphPoint("c", 2.0, 2.0, connectFromPrevious = false),
            GraphPoint("gap", Double.NaN, Double.NaN),
            GraphPoint("d", 3.0, 3.0),
            GraphPoint("e", 4.0, 4.0)
        )
        val segments = GraphPaths.connectedSegments(points)
        assertEquals(listOf(listOf("a", "b"), listOf("c"), listOf("d", "e")), segments.map { it.map(GraphPoint::id) })
        assertNotEquals(segments[0].last().id, segments[1].first().id)
        assertFalse(segments.flatten().any { !it.x.isFinite() || !it.y.isFinite() })
    }
}
