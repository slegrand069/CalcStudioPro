package com.example.calcstudiopro.functions.graph.interaction

import com.example.calcstudiopro.statistics.graph.domain.GraphTransform
import com.example.calcstudiopro.statistics.graph.model.AxisRange
import com.example.calcstudiopro.statistics.graph.model.DataPoint
import com.example.calcstudiopro.statistics.graph.model.GraphViewport
import com.example.calcstudiopro.statistics.graph.model.PlotRect
import com.example.calcstudiopro.statistics.graph.model.ScreenPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class FunctionGraphViewportControllerTest {
    private val plot = PlotRect(20.0, 10.0, 400.0, 200.0)

    @Test fun panMovesBothAxesIndependentlyAndKeepsTheirSpans() {
        val initial = GraphViewport(AxisRange(-10.0, 10.0), AxisRange(-5.0, 5.0))
        val point = DataPoint(2.0, 1.0)
        val before = GraphTransform(initial, plot).dataToScreen(point)
        val moved = FunctionGraphViewportController.pan(initial, 32.0, 18.0, plot)
        val after = GraphTransform(moved, plot).dataToScreen(point)
        assertEquals(before.x + 32.0, after.x, 1e-9)
        assertEquals(before.y + 18.0, after.y, 1e-9)
        assertEquals(20.0, moved.x.maximum - moved.x.minimum, 1e-12)
        assertEquals(10.0, moved.y.maximum - moved.y.minimum, 1e-12)
        assertNotEquals(moved.x.minimum, moved.y.minimum)
    }

    @Test fun focalPointFollowsCentroidDuringCombinedZoomAndTranslation() {
        val initial = FunctionGraphViewportController.INITIAL
        val previous = ScreenPoint(292.0, 72.0)
        val current = ScreenPoint(337.0, 98.0)
        val fixedData = GraphTransform(initial, plot).screenToData(previous)
        val result = FunctionGraphViewportController.transform(initial, previous, current, 2.25, plot)
        val mapped = GraphTransform(result, plot).dataToScreen(fixedData)
        assertEquals(current.x, mapped.x, 1e-9)
        assertEquals(current.y, mapped.y, 1e-9)
        assertEquals(20.0 / 2.25, result.x.maximum - result.x.minimum, 1e-10)
        assertEquals(20.0 / 2.25, result.y.maximum - result.y.minimum, 1e-10)
    }

    @Test fun eventFactorAndGlobalSpanLimitsAreEnforcedWithoutInvalidRanges() {
        var viewport = FunctionGraphViewportController.INITIAL
        val center = ScreenPoint(plot.left + plot.width / 2.0, plot.top + plot.height / 2.0)
        repeat(100) { viewport = FunctionGraphViewportController.transform(viewport, center, center, 100.0, plot) }
        assertTrue(viewport.x.maximum - viewport.x.minimum >= FunctionGraphViewportController.MIN_SPAN)
        assertTrue(viewport.y.maximum - viewport.y.minimum >= FunctionGraphViewportController.MIN_SPAN)
        repeat(100) { viewport = FunctionGraphViewportController.transform(viewport, center, center, 0.0001, plot) }
        assertTrue(viewport.x.maximum - viewport.x.minimum <= FunctionGraphViewportController.MAX_SPAN)
        assertTrue(viewport.y.maximum - viewport.y.minimum <= FunctionGraphViewportController.MAX_SPAN)
        assertTrue(viewport.x.minimum.isFinite() && viewport.x.maximum.isFinite() && viewport.x.minimum < viewport.x.maximum)
        assertTrue(viewport.y.minimum.isFinite() && viewport.y.maximum.isFinite() && viewport.y.minimum < viewport.y.maximum)
    }

    @Test fun oppositeZoomGesturesDoNotAccumulateMeaningfulDrift() {
        var viewport = FunctionGraphViewportController.INITIAL
        val center = ScreenPoint(plot.left + 0.37 * plot.width, plot.top + 0.61 * plot.height)
        repeat(100) {
            viewport = FunctionGraphViewportController.transform(viewport, center, center, 2.0, plot)
            viewport = FunctionGraphViewportController.transform(viewport, center, center, 0.5, plot)
        }
        assertEquals(-10.0, viewport.x.minimum, 1e-10)
        assertEquals(10.0, viewport.x.maximum, 1e-10)
        assertEquals(-10.0, viewport.y.minimum, 1e-10)
        assertEquals(10.0, viewport.y.maximum, 1e-10)
    }

    @Test fun precisionGuardWidensTheMinimumSpanAtLargeCoordinates() {
        val centerValue = 1e12
        val wide = AxisRange(centerValue - 10.0, centerValue + 10.0)
        assertTrue(FunctionGraphViewportController.isSafe(wide, 400))
        val tooNarrow = AxisRange(centerValue, Math.nextUp(centerValue))
        assertTrue(!FunctionGraphViewportController.isSafe(tooNarrow, 400))
        val viewport = GraphViewport(wide, wide)
        val plotAtLargeOffset = PlotRect(0.0, 0.0, 400.0, 400.0)
        val center = ScreenPoint(200.0, 200.0)
        val zoomed = FunctionGraphViewportController.transform(viewport, center, center, 4.0, plotAtLargeOffset)
        val actualSpan = zoomed.x.maximum - zoomed.x.minimum
        assertTrue(actualSpan >= Math.ulp(centerValue) * 400)
        assertTrue(zoomed.x.minimum.isFinite() && zoomed.x.maximum.isFinite())
    }

    @Test fun resetAndResizeKeepTheSpecifiedMathematicalBounds() {
        val reset = FunctionGraphViewportController.reset()
        assertEquals(AxisRange(-10.0, 10.0), reset.x)
        assertEquals(AxisRange(-10.0, 10.0), reset.y)
        val custom = GraphViewport(AxisRange(-3.0, 7.0), AxisRange(-8.0, 2.0))
        val resized = FunctionGraphViewportController.resized(custom, plot, PlotRect(0.0, 0.0, 700.0, 300.0))
        assertEquals(custom, resized)
    }

    @Test fun inversePanReturnsToTheOriginalViewport() {
        val start = FunctionGraphViewportController.INITIAL
        val moved = FunctionGraphViewportController.pan(start, 35.0, -22.0, plot)
        val returned = FunctionGraphViewportController.pan(moved, -35.0, 22.0, plot)
        assertTrue(abs(returned.x.minimum - start.x.minimum) < 1e-12)
        assertTrue(abs(returned.y.maximum - start.y.maximum) < 1e-12)
    }

    @Test fun finiteEndpointsWithAnOverflowingSpanAreRefusedWithoutCreatingInvalidViewport() {
        val extreme = GraphViewport(
            AxisRange(-Double.MAX_VALUE, Double.MAX_VALUE),
            AxisRange(-Double.MAX_VALUE, Double.MAX_VALUE)
        )
        val center = ScreenPoint(plot.left + plot.width / 2.0, plot.top + plot.height / 2.0)
        val zoomed = FunctionGraphViewportController.transform(extreme, center, center, 2.0, plot)
        assertEquals(extreme, zoomed)
        val panned = FunctionGraphViewportController.pan(extreme, 12.0, -7.0, plot)
        assertEquals(extreme, panned)
        assertTrue(zoomed.x.minimum.isFinite() && zoomed.x.maximum.isFinite())
        assertTrue(zoomed.y.minimum.isFinite() && zoomed.y.maximum.isFinite())
    }

    @Test fun ulpGuardAtTheLargestFiniteCenterRefusesUnrepresentableZoom() {
        val maximum = Double.MAX_VALUE
        val nearMaximum = AxisRange(Math.nextDown(maximum), maximum)
        assertFalse(FunctionGraphViewportController.isSafe(nearMaximum, 400))
        val viewport = GraphViewport(nearMaximum, AxisRange(-1.0, 1.0))
        val center = ScreenPoint(plot.left + plot.width / 2.0, plot.top + plot.height / 2.0)
        val result = FunctionGraphViewportController.transform(viewport, center, center, 4.0, plot)
        assertEquals(viewport, result)
    }
}
