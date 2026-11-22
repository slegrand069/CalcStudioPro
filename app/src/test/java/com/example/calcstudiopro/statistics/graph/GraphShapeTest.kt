package com.example.calcstudiopro.statistics.graph

import com.example.calcstudiopro.statistics.graph.domain.GraphHitTesting
import com.example.calcstudiopro.statistics.graph.domain.GraphRange
import com.example.calcstudiopro.statistics.graph.domain.GraphTransform
import com.example.calcstudiopro.statistics.graph.model.DataPoint
import com.example.calcstudiopro.statistics.graph.model.GraphBar
import com.example.calcstudiopro.statistics.graph.model.GraphSegment
import com.example.calcstudiopro.statistics.graph.model.GraphSeries
import com.example.calcstudiopro.statistics.graph.model.GraphSeriesKind
import com.example.calcstudiopro.statistics.graph.model.GraphSnapshot
import com.example.calcstudiopro.statistics.graph.model.PlotRect
import com.example.calcstudiopro.statistics.graph.model.ScreenPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GraphShapeTest {
    private val plot = PlotRect(0.0, 0.0, 400.0, 240.0)

    @Test fun histogramBarsAffectAutoFrameAndCanBeSelectedFromTheirInterior() {
        val bar = GraphBar("class:0", -2.0, 2.0, 0.0, 5.0)
        val snapshot = GraphSnapshot("Histogram", "Valeur", "Effectif", listOf(
            GraphSeries("histogram", "Classes", points = emptyList(), kind = GraphSeriesKind.BARS, bars = listOf(bar))
        ))
        val viewport = GraphRange.autoFit(snapshot)
        assertTrue(viewport.x.minimum < -2.0 && viewport.x.maximum > 2.0)
        assertTrue(viewport.y.minimum < 0.0 && viewport.y.maximum > 5.0)
        val screen = GraphTransform(viewport, plot).dataToScreen(DataPoint(0.0, 2.5))
        val hit = GraphHitTesting.pointsNear(snapshot, viewport, plot, ScreenPoint(screen.x, screen.y), 1.0).single()
        assertEquals("class:0", hit.point.id)
    }

    @Test fun boxPlotSegmentsCanBeSelectedAlongTheirLength() {
        val segment = GraphSegment("median", 4.0, -0.5, 4.0, 0.5)
        val snapshot = GraphSnapshot("Tukey", "Valeur", "Décalage visuel", listOf(
            GraphSeries("box", "Box", points = emptyList(), segments = listOf(segment))
        ))
        val viewport = GraphRange.autoFit(snapshot)
        val screen = GraphTransform(viewport, plot).dataToScreen(DataPoint(4.0, 0.0))
        val hit = GraphHitTesting.pointsNear(snapshot, viewport, plot, ScreenPoint(screen.x, screen.y), 2.0).single()
        assertEquals("median", hit.point.id)
        assertEquals(4.0, hit.point.x, 1e-12) // The hit reports its exact interpolated value-axis coordinate.
    }
}
