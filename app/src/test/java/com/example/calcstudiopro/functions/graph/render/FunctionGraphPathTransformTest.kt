package com.example.calcstudiopro.functions.graph.render

import com.example.calcstudiopro.functions.graph.interaction.FunctionGraphViewportController
import com.example.calcstudiopro.statistics.graph.domain.GraphTransform
import com.example.calcstudiopro.statistics.graph.model.PlotRect
import com.example.calcstudiopro.statistics.graph.model.ScreenPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FunctionGraphPathTransformTest {
    @Test fun cachedPathAffineTransformMatchesCurrentMathematicalViewport() {
        val plot = PlotRect(20.0, 10.0, 400.0, 200.0)
        val source = FunctionGraphViewportController.INITIAL
        val previousCentroid = ScreenPoint(120.0, 160.0)
        val currentCentroid = ScreenPoint(165.0, 145.0)
        val target = FunctionGraphViewportController.transform(source, previousCentroid, currentCentroid, 2.0, plot)
        val transform = FunctionGraphPathTransform.between(source, target, plot, plot)
        assertTrue(transform.isFinite)
        assertEquals(transform.scaleX, transform.scaleY, 1e-12)

        val dataPoint = com.example.calcstudiopro.statistics.graph.model.DataPoint(3.25, -1.75)
        val cached = GraphTransform(source, plot).dataToScreen(dataPoint)
        val expected = GraphTransform(target, plot).dataToScreen(dataPoint)
        assertEquals(expected.x, cached.x * transform.scaleX + transform.translateX, 1e-9)
        assertEquals(expected.y, cached.y * transform.scaleY + transform.translateY, 1e-9)
    }

    @Test fun resizeTransformationMapsPlotCornersAndDoesNotTouchMathData() {
        val viewport = FunctionGraphViewportController.INITIAL
        val oldPlot = PlotRect(10.0, 8.0, 300.0, 180.0)
        val newPlot = PlotRect(24.0, 12.0, 680.0, 260.0)
        val transform = FunctionGraphPathTransform.between(viewport, viewport, oldPlot, newPlot)
        assertTrue(transform.isFinite)
        assertEquals(newPlot.left, oldPlot.left * transform.scaleX + transform.translateX, 1e-9)
        assertEquals(newPlot.top, oldPlot.top * transform.scaleY + transform.translateY, 1e-9)
        assertEquals(newPlot.left + newPlot.width,
            (oldPlot.left + oldPlot.width) * transform.scaleX + transform.translateX, 1e-9)
        assertEquals(newPlot.top + newPlot.height,
            (oldPlot.top + oldPlot.height) * transform.scaleY + transform.translateY, 1e-9)
    }
}
