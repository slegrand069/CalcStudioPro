package com.example.calcstudiopro.functions.graph.interaction

import com.example.calcstudiopro.functions.graph.FunctionGraphPoint
import com.example.calcstudiopro.functions.graph.render.FunctionGraphRenderModel
import com.example.calcstudiopro.statistics.graph.domain.GraphTransform
import com.example.calcstudiopro.statistics.graph.model.GraphViewport
import com.example.calcstudiopro.statistics.graph.model.PlotRect
import com.example.calcstudiopro.statistics.graph.model.ScreenPoint

/** Follows only the marker's existing slot and continuous segment; endpoint positions are sticky. */
object FunctionGraphMarkerDrag {
    fun hitOnSelectedSegment(
        model: FunctionGraphRenderModel,
        slotId: com.example.calcstudiopro.functions.domain.FunctionSlotId,
        segmentIndex: Int,
        screen: ScreenPoint,
        viewport: GraphViewport,
        plot: PlotRect
    ): FunctionGraphCurveHit? {
        val curve = model.curves.firstOrNull { it.id == slotId } ?: return null
        val segment = curve.segments.getOrNull(segmentIndex) ?: return null
        val point = pointOnSelectedSegment(model, slotId, segmentIndex, screen, viewport, plot) ?: return null
        val points = segment.points
        var low = 0
        var high = points.lastIndex
        while (low + 1 < high) {
            val mid = (low + high) ushr 1
            if (points[mid].x <= point.x) low = mid else high = mid
        }
        val edge = low.coerceIn(0, points.lastIndex - 1)
        val dx = points[edge + 1].x - points[edge].x
        val t = if (dx == 0.0) 0.0 else ((point.x - points[edge].x) / dx).coerceIn(0.0, 1.0)
        return FunctionGraphCurveHit(slotId, 0.0, segmentIndex, edge, t, point)
    }

    fun pointOnSelectedSegment(
        model: FunctionGraphRenderModel,
        slotId: com.example.calcstudiopro.functions.domain.FunctionSlotId,
        segmentIndex: Int,
        screen: ScreenPoint,
        viewport: GraphViewport,
        plot: PlotRect
    ): FunctionGraphPoint? {
        val segment = model.curves.firstOrNull { it.id == slotId }?.segments?.getOrNull(segmentIndex) ?: return null
        val x = GraphTransform(viewport, plot).screenToData(screen).x
        if (!x.isFinite()) return null
        val points = segment.points
        if (x <= points.first().x) return points.first()
        if (x >= points.last().x) return points.last()
        var low = 0
        var high = points.lastIndex
        while (low + 1 < high) {
            val mid = (low + high) ushr 1
            if (points[mid].x <= x) low = mid else high = mid
        }
        val a = points[low]
        val b = points[high]
        val t = ((x - a.x) / (b.x - a.x)).coerceIn(0.0, 1.0)
        val y = GraphTransform.interpolate(a.y, b.y, t)
        return if (y.isFinite()) FunctionGraphPoint(x, y) else null
    }
}
