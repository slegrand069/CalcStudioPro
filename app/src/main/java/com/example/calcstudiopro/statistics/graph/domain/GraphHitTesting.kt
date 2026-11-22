package com.example.calcstudiopro.statistics.graph.domain

import com.example.calcstudiopro.statistics.graph.model.DataPoint
import com.example.calcstudiopro.statistics.graph.model.GraphSeriesKind
import com.example.calcstudiopro.statistics.graph.model.GraphSnapshot
import com.example.calcstudiopro.statistics.graph.model.GraphViewport
import com.example.calcstudiopro.statistics.graph.model.PointHit
import com.example.calcstudiopro.statistics.graph.model.PlotRect
import com.example.calcstudiopro.statistics.graph.model.ScreenPoint

object GraphHitTesting {
    /** Returns every observation within the touch radius, nearest first; duplicates are retained. */
    fun pointsNear(
        snapshot: GraphSnapshot,
        viewport: GraphViewport,
        plot: PlotRect,
        touch: ScreenPoint,
        radiusPixels: Double
    ): List<PointHit> {
        if (!touch.x.isFinite() || !touch.y.isFinite() || !radiusPixels.isFinite() || radiusPixels < 0.0) return emptyList()
        val transform = GraphTransform(viewport, plot)
        val radiusSquared = radiusPixels * radiusPixels
        return snapshot.series.asSequence()
            .filter { it.kind != GraphSeriesKind.LINE }
            .flatMap { series -> series.points.asSequence().mapNotNull { point ->
                if (!point.x.isFinite() || !point.y.isFinite()) return@mapNotNull null
                val screen = transform.dataToScreen(DataPoint(point.x, point.y))
                if (screen.x !in plot.left..(plot.left + plot.width) || screen.y !in plot.top..(plot.top + plot.height)) return@mapNotNull null
                val dx = screen.x - touch.x
                val dy = screen.y - touch.y
                val distanceSquared = dx * dx + dy * dy
                if (distanceSquared <= radiusSquared) PointHit(series.id, point, kotlin.math.sqrt(distanceSquared)) else null
            } }
            .sortedWith(compareBy<PointHit> { it.distancePixels }.thenBy { it.seriesId }.thenBy { it.point.id })
            .toList()
    }
}
