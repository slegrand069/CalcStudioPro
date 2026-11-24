package com.example.calcstudiopro.functions.graph.interaction

import com.example.calcstudiopro.functions.domain.FunctionSlotId
import com.example.calcstudiopro.functions.graph.FunctionGraphPoint
import com.example.calcstudiopro.functions.graph.FunctionGraphStatus
import com.example.calcstudiopro.functions.graph.render.FunctionGraphRenderModel
import com.example.calcstudiopro.statistics.graph.model.AxisRange
import com.example.calcstudiopro.statistics.graph.model.GraphViewport
import java.util.Collections
import kotlin.math.abs
import kotlin.math.max

/** Result of fitting only the vertical range. X is always preserved verbatim. */
sealed interface FunctionGraphFitResult {
    data class Applied(
        val viewport: GraphViewport,
        val usedPartialData: Boolean,
        val includedSlots: Set<FunctionSlotId>
    ) : FunctionGraphFitResult
    data class Rejected(val reason: Reason) : FunctionGraphFitResult

    enum class Reason { NO_DEFINED_PORTION, INCOMPATIBLE_BATCH, NUMERIC_LIMIT }
}

/** Deterministic robust Y fitting over the currently displayed X interval. */
object FunctionGraphViewportFitter {
    const val LOWER_QUANTILE = 0.01
    const val UPPER_QUANTILE = 0.99
    const val MARGIN_FRACTION = 0.08
    private const val QUANTILE_STEPS = 64

    /**
     * Each piecewise-linear edge contributes weight equal to its X length. A slot gets its own
     * weighted 1/99% envelope; envelopes are then united, so adaptive point density in one slot
     * cannot dominate the others. Edges from distinct F2.1 segments are never joined.
     */
    fun fit(
        viewport: GraphViewport,
        accepted: FunctionGraphAcceptedResult,
        model: FunctionGraphRenderModel,
        contentRevision: Long,
        plotHeightPixels: Int
    ): FunctionGraphFitResult {
        val sampledX = accepted.request.xRange
        if (plotHeightPixels <= 0 || model.dataGeneration == null ||
            accepted.contentRevision != contentRevision || model.dataGeneration != accepted.batch.generation ||
            accepted.batch.generation != accepted.request.generation ||
            sampledX.minimum > viewport.x.minimum || sampledX.maximum < viewport.x.maximum
        ) {
            return FunctionGraphFitResult.Rejected(FunctionGraphFitResult.Reason.INCOMPATIBLE_BATCH)
        }

        val envelopes = mutableListOf<Pair<Double, Double>>()
        var partial = false
        var allIncludedValuesAreZero = true
        val included = linkedSetOf<FunctionSlotId>()
        for (curve in model.curves) {
            if (!curve.isVisible) continue
            val functionResult = accepted.batch.results.firstOrNull { it.id == curve.id } ?: continue
            val resultStatus = functionResult.status
            if (resultStatus != FunctionGraphStatus.Success && resultStatus !is FunctionGraphStatus.Partial) continue
            if (functionResult.segments.isEmpty()) continue
            val edges = functionResult.segments.flatMap { segment -> clipEdges(segment.points, viewport.x) }
            if (edges.isEmpty()) continue
            if (edges.any { it.y0 != 0.0 || it.y1 != 0.0 }) allIncludedValuesAreZero = false
            val low = weightedQuantile(edges, LOWER_QUANTILE) ?: continue
            val high = weightedQuantile(edges, UPPER_QUANTILE) ?: continue
            if (low.isFinite() && high.isFinite() && low <= high) {
                envelopes += low to high
                included += curve.id
                if (resultStatus is FunctionGraphStatus.Partial) partial = true
            }
        }
        if (envelopes.isEmpty()) return FunctionGraphFitResult.Rejected(FunctionGraphFitResult.Reason.NO_DEFINED_PORTION)

        val low = envelopes.minOf { it.first }
        val high = envelopes.maxOf { it.second }
        val proposed = when {
            allIncludedValuesAreZero -> AxisRange(-1.0, 1.0)
            low == high -> constantRange(low)
            else -> paddedRange(low, high)
        } ?: return FunctionGraphFitResult.Rejected(FunctionGraphFitResult.Reason.NUMERIC_LIMIT)

        if (!FunctionGraphViewportController.isSafe(proposed, plotHeightPixels)) {
            return FunctionGraphFitResult.Rejected(FunctionGraphFitResult.Reason.NUMERIC_LIMIT)
        }
        return FunctionGraphFitResult.Applied(viewport.copy(y = proposed), partial, Collections.unmodifiableSet(included))
    }

    private data class WeightedEdge(val x0: Double, val x1: Double, val y0: Double, val y1: Double) {
        val weight get() = x1 - x0
    }

    private fun clipEdges(points: List<FunctionGraphPoint>, range: AxisRange): List<WeightedEdge> {
        val result = ArrayList<WeightedEdge>(points.size - 1)
        for ((a, b) in points.zipWithNext()) {
            val left = maxOf(a.x, range.minimum)
            val right = minOf(b.x, range.maximum)
            if (!(left < right)) continue
            val dx = b.x - a.x
            if (!dx.isFinite() || dx <= 0.0) continue
            val t0 = ((left - a.x) / dx).coerceIn(0.0, 1.0)
            val t1 = ((right - a.x) / dx).coerceIn(0.0, 1.0)
            val y0 = interpolate(a.y, b.y, t0)
            val y1 = interpolate(a.y, b.y, t1)
            if (y0.isFinite() && y1.isFinite() && right - left > 0.0) {
                result += WeightedEdge(left, right, y0, y1)
            }
        }
        return result
    }

    private fun interpolate(a: Double, b: Double, t: Double): Double = a * (1.0 - t) + b * t

    /** Binary inversion of the exact piecewise-linear weighted CDF (uniform in x on each edge). */
    private fun weightedQuantile(edges: List<WeightedEdge>, quantile: Double): Double? {
        val minY = edges.minOf { minOf(it.y0, it.y1) }
        val maxY = edges.maxOf { maxOf(it.y0, it.y1) }
        if (!minY.isFinite() || !maxY.isFinite()) return null
        if (minY == maxY) return minY
        var low = minY
        var high = maxY
        repeat(QUANTILE_STEPS) {
            val middle = if (low < 0.0 && high > 0.0) low / 2.0 + high / 2.0 else low + (high - low) / 2.0
            if (middle <= low || middle >= high) return@repeat
            if (cdf(edges, middle) >= quantile) high = middle else low = middle
        }
        return high.takeIf(Double::isFinite)
    }

    private fun cdf(edges: List<WeightedEdge>, y: Double): Double {
        var total = 0.0
        var below = 0.0
        for (edge in edges) {
            val weight = edge.weight
            if (!weight.isFinite() || weight <= 0.0) continue
            total += weight
            val lo = minOf(edge.y0, edge.y1)
            val hi = maxOf(edge.y0, edge.y1)
            val fraction = when {
                y < lo -> 0.0
                y >= hi -> 1.0
                edge.y0 == edge.y1 -> 1.0
                else -> {
                    // Scale before subtraction to avoid overflow for opposite, extreme ordinates.
                    val scale = max(abs(edge.y0), max(abs(edge.y1), abs(y)))
                    if (scale == 0.0) 0.0 else ((y / scale - edge.y0 / scale) /
                        (edge.y1 / scale - edge.y0 / scale)).let { raw ->
                        (if (edge.y1 >= edge.y0) raw else 1.0 - raw).coerceIn(0.0, 1.0)
                    }
                }
            }
            below += weight * fraction
        }
        return if (total > 0.0 && total.isFinite()) (below / total).coerceIn(0.0, 1.0) else 0.0
    }

    private fun paddedRange(low: Double, high: Double): AxisRange? {
        val halfSpan = high / 2.0 - low / 2.0
        val span = halfSpan * 2.0
        val expanded = span * (1.0 + 2.0 * MARGIN_FRACTION)
        val center = low / 2.0 + high / 2.0
        if (!span.isFinite() || span <= 0.0 || !expanded.isFinite() || !center.isFinite()) return null
        return centeredRange(center, max(expanded, FunctionGraphViewportController.MIN_SPAN))
    }

    private fun constantRange(value: Double): AxisRange? {
        // A 20% span follows the order of magnitude; the global minimum and ULP guard prevent a
        // zero-width or unrepresentable interval near zero and at large coordinates.
        val span = max(abs(value) * 0.20, FunctionGraphViewportController.MIN_SPAN)
        val expanded = span * (1.0 + 2.0 * MARGIN_FRACTION)
        return if (expanded.isFinite()) centeredRange(value, expanded) else null
    }

    private fun centeredRange(center: Double, span: Double): AxisRange? {
        if (!center.isFinite() || !span.isFinite() || span <= 0.0) return null
        val half = span / 2.0
        val low = center - half
        val high = center + half
        return runCatching { AxisRange(low, high) }.getOrNull()
    }
}
