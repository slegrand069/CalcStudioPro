package com.example.calcstudiopro.functions.graph.interaction

import com.example.calcstudiopro.functions.domain.FunctionSlotId
import com.example.calcstudiopro.functions.graph.FunctionGraphPoint
import com.example.calcstudiopro.functions.graph.render.FunctionGraphGeometryBuilder
import com.example.calcstudiopro.functions.graph.render.FunctionGraphPathTransform
import com.example.calcstudiopro.functions.graph.render.FunctionGraphRenderModel
import com.example.calcstudiopro.statistics.graph.domain.GraphTransform
import com.example.calcstudiopro.statistics.graph.model.DataPoint
import com.example.calcstudiopro.statistics.graph.model.GraphViewport
import com.example.calcstudiopro.statistics.graph.model.PlotRect
import com.example.calcstudiopro.statistics.graph.model.ScreenPoint
import java.util.LinkedHashMap
import kotlin.math.abs
import kotlin.math.hypot

data class FunctionGraphCurveHit(
    val slotId: FunctionSlotId,
    val distancePixels: Double,
    val segmentIndex: Int,
    val edgeIndex: Int,
    val interpolationParameter: Double,
    val point: FunctionGraphPoint
)

/** Pure hit-testing over the same source-clipped polylines used to build the Canvas paths. */
object FunctionGraphCurveHitTester {
    const val DEFAULT_SELECTION_RADIUS_DP = 24.0
    const val DEFAULT_DISTANCE_TIE_TOLERANCE_PIXELS = 0.5

    /** Converts the UX radius to physical pixels; callers pass Compose LocalDensity.density. */
    fun radiusPixels(radiusDp: Double = DEFAULT_SELECTION_RADIUS_DP, density: Double): Double {
        require(radiusDp.isFinite() && radiusDp > 0.0)
        require(density.isFinite() && density > 0.0)
        return radiusDp * density
    }

    fun candidatesNear(
        model: FunctionGraphRenderModel,
        sourceViewport: GraphViewport,
        displayedViewport: GraphViewport,
        sourcePlot: PlotRect,
        displayedPlot: PlotRect,
        touch: ScreenPoint,
        radiusPixels: Double,
        distanceTieTolerancePixels: Double = DEFAULT_DISTANCE_TIE_TOLERANCE_PIXELS
    ): List<FunctionGraphCurveHit> {
        if (!touch.x.isFinite() || !touch.y.isFinite() || !radiusPixels.isFinite() || radiusPixels < 0.0 ||
            !distanceTieTolerancePixels.isFinite() || distanceTieTolerancePixels < 0.0 ||
            touch.x !in displayedPlot.left..(displayedPlot.left + displayedPlot.width) ||
            touch.y !in displayedPlot.top..(displayedPlot.top + displayedPlot.height)
        ) return emptyList()

        val pathTransform = FunctionGraphPathTransform.between(sourceViewport, displayedViewport, sourcePlot, displayedPlot)
        if (!pathTransform.isFinite) return emptyList()
        val sourceTransform = GraphTransform(sourceViewport, sourcePlot)
        val bestBySlot = LinkedHashMap<FunctionSlotId, FunctionGraphCurveHit>()

        for (curve in model.curves) {
            if (!curve.isVisible || curve.segments.isEmpty()) continue
            curve.segments.forEachIndexed { segmentIndex, segment ->
                val points = segment.points
                for (edgeIndex in 0 until points.lastIndex) {
                    val first = points[edgeIndex]
                    val second = points[edgeIndex + 1]
                    val sourceLines = FunctionGraphGeometryBuilder.projectAndClipSegment(
                        listOf(first, second), sourceViewport, sourcePlot
                    )
                    for (sourceLine in sourceLines) {
                        for (index in 0 until sourceLine.lastIndex) {
                            val targetStart = transform(sourceLine[index], pathTransform) ?: continue
                            val targetEnd = transform(sourceLine[index + 1], pathTransform) ?: continue
                            val clipped = clipToPlot(targetStart, targetEnd, displayedPlot) ?: continue
                            val nearest = nearestOnLine(touch, clipped.first, clipped.second)
                            if (nearest.distance > radiusPixels) continue

                            // Invert the same affine map used by the Canvas, then map back to the
                            // source math line. This preserves one x Double for preview and evaluation.
                            val sourcePoint = inverseTransform(nearest.point, pathTransform) ?: continue
                            val math = sourceTransform.screenToData(sourcePoint)
                            if (!math.x.isFinite() || !math.y.isFinite()) continue
                            val dx = second.x - first.x
                            if (!dx.isFinite() || dx <= 0.0) continue
                            val parameter = ((math.x - first.x) / dx).takeIf(Double::isFinite)
                                ?.coerceIn(0.0, 1.0) ?: continue
                            val point = FunctionGraphPoint(
                                GraphTransform.interpolate(first.x, second.x, parameter),
                                GraphTransform.interpolate(first.y, second.y, parameter)
                            )
                            val hit = FunctionGraphCurveHit(
                                curve.id, nearest.distance, segmentIndex, edgeIndex, parameter, point
                            )
                            val previous = bestBySlot[curve.id]
                            if (previous == null || hit.distancePixels < previous.distancePixels) {
                                bestBySlot[curve.id] = hit
                            }
                        }
                    }
                }
            }
        }
        return orderWithStableDistanceTies(bestBySlot.values.toList(), distanceTieTolerancePixels)
    }

    private fun transform(point: ScreenPoint, transform: FunctionGraphPathTransform): ScreenPoint? {
        val x = transform.scaleX * point.x + transform.translateX
        val y = transform.scaleY * point.y + transform.translateY
        return if (x.isFinite() && y.isFinite()) ScreenPoint(x, y) else null
    }

    private fun inverseTransform(point: ScreenPoint, transform: FunctionGraphPathTransform): ScreenPoint? {
        val x = (point.x - transform.translateX) / transform.scaleX
        val y = (point.y - transform.translateY) / transform.scaleY
        return if (x.isFinite() && y.isFinite()) ScreenPoint(x, y) else null
    }

    private fun clipToPlot(a: ScreenPoint, b: ScreenPoint, plot: PlotRect): Pair<ScreenPoint, ScreenPoint>? {
        val right = plot.left + plot.width
        val bottom = plot.top + plot.height
        val dx = b.x - a.x
        val dy = b.y - a.y
        if (!dx.isFinite() || !dy.isFinite()) return null
        var enter = 0.0
        var leave = 1.0

        fun test(p: Double, q: Double): Boolean {
            if (p == 0.0) return q >= 0.0
            val ratio = q / p
            if (!ratio.isFinite()) return false
            if (p < 0.0) {
                if (ratio > leave) return false
                if (ratio > enter) enter = ratio
            } else {
                if (ratio < enter) return false
                if (ratio < leave) leave = ratio
            }
            return true
        }

        if (!test(-dx, a.x - plot.left) || !test(dx, right - a.x) ||
            !test(-dy, a.y - plot.top) || !test(dy, bottom - a.y) || enter > leave
        ) return null
        fun at(t: Double) = ScreenPoint(a.x * (1.0 - t) + b.x * t, a.y * (1.0 - t) + b.y * t)
        val first = at(enter)
        val second = at(leave)
        return if (first.x.isFinite() && first.y.isFinite() && second.x.isFinite() && second.y.isFinite()) {
            first to second
        } else null
    }

    private data class Nearest(val point: ScreenPoint, val distance: Double)

    private fun nearestOnLine(touch: ScreenPoint, a: ScreenPoint, b: ScreenPoint): Nearest {
        val dx = b.x - a.x
        val dy = b.y - a.y
        val lengthSquared = dx * dx + dy * dy
        val t = if (lengthSquared == 0.0) 0.0 else
            (((touch.x - a.x) * dx + (touch.y - a.y) * dy) / lengthSquared).coerceIn(0.0, 1.0)
        val point = ScreenPoint(a.x + t * dx, a.y + t * dy)
        return Nearest(point, hypot(touch.x - point.x, touch.y - point.y))
    }

    private fun orderWithStableDistanceTies(
        hits: List<FunctionGraphCurveHit>,
        tieTolerance: Double
    ): List<FunctionGraphCurveHit> {
        val sorted = hits.sortedWith(compareBy<FunctionGraphCurveHit> { it.distancePixels }.thenBy { it.slotId })
        if (tieTolerance == 0.0 || sorted.size < 2) return sorted
        val result = ArrayList<FunctionGraphCurveHit>(sorted.size)
        var start = 0
        while (start < sorted.size) {
            var end = start + 1
            val groupBase = sorted[start].distancePixels
            while (end < sorted.size && abs(sorted[end].distancePixels - groupBase) <= tieTolerance) end++
            result += sorted.subList(start, end).sortedBy { it.slotId }
            start = end
        }
        return result
    }
}
