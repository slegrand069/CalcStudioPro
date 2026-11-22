package com.example.calcstudiopro.statistics.graph.domain

import com.example.calcstudiopro.statistics.graph.model.DataPoint
import com.example.calcstudiopro.statistics.graph.model.GraphSeriesKind
import com.example.calcstudiopro.statistics.graph.model.GraphPoint
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
        if (touch.x !in plot.left..(plot.left + plot.width) || touch.y !in plot.top..(plot.top + plot.height)) return emptyList()
        val transform = GraphTransform(viewport, plot)
        val hits = mutableListOf<PointHit>()
        snapshot.series.forEach { series ->
            if (series.kind != GraphSeriesKind.LINE && series.kind != GraphSeriesKind.BARS) {
                series.points.forEach pointLoop@{ point ->
                    if (!point.x.isFinite() || !point.y.isFinite()) return@pointLoop
                    val screen = transform.dataToScreen(DataPoint(point.x, point.y))
                    if (screen.x !in plot.left..(plot.left + plot.width) || screen.y !in plot.top..(plot.top + plot.height)) return@pointLoop
                    val distance = kotlin.math.hypot(screen.x - touch.x, screen.y - touch.y)
                    if (distance <= radiusPixels) hits += PointHit(series.id, point, distance)
                }
            }
            series.bars.forEach barLoop@{ bar ->
                if (listOf(bar.xMinimum, bar.xMaximum, bar.yMinimum, bar.yMaximum).any { !it.isFinite() }) return@barLoop
                val first = transform.dataToScreen(DataPoint(bar.xMinimum, bar.yMaximum))
                val second = transform.dataToScreen(DataPoint(bar.xMaximum, bar.yMinimum))
                val left = minOf(first.x, second.x)
                val right = maxOf(first.x, second.x)
                val top = minOf(first.y, second.y)
                val bottom = maxOf(first.y, second.y)
                if (touch.x in left..right && touch.y in top..bottom) {
                    val center = transform.screenToData(ScreenPoint((left + right) / 2.0, (top + bottom) / 2.0))
                    hits += PointHit(series.id, GraphPoint(bar.id, center.x, center.y, details = bar.details), 0.0)
                }
            }
            series.segments.forEach segmentLoop@{ segment ->
                if (listOf(segment.x1, segment.y1, segment.x2, segment.y2).any { !it.isFinite() }) return@segmentLoop
                val start = transform.dataToScreen(DataPoint(segment.x1, segment.y1))
                val end = transform.dataToScreen(DataPoint(segment.x2, segment.y2))
                val dx = end.x - start.x
                val dy = end.y - start.y
                val lengthSquared = dx * dx + dy * dy
                val fraction = if (lengthSquared == 0.0) 0.0 else (((touch.x - start.x) * dx + (touch.y - start.y) * dy) / lengthSquared).coerceIn(0.0, 1.0)
                val nearest = ScreenPoint(start.x + fraction * dx, start.y + fraction * dy)
                val distance = kotlin.math.hypot(touch.x - nearest.x, touch.y - nearest.y)
                if (distance <= radiusPixels) {
                    val data = transform.screenToData(nearest)
                    hits += PointHit(series.id, GraphPoint(segment.id, data.x, data.y, details = segment.details), distance)
                }
            }
        }
        return hits.sortedWith(compareBy<PointHit> { it.distancePixels }.thenBy { it.seriesId }.thenBy { it.point.id })
    }
}
