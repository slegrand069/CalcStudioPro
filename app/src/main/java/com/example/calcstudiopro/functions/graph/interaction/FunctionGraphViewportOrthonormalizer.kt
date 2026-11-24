package com.example.calcstudiopro.functions.graph.interaction

import com.example.calcstudiopro.statistics.graph.model.AxisRange
import com.example.calcstudiopro.statistics.graph.model.GraphViewport
import com.example.calcstudiopro.statistics.graph.model.PlotRect
import kotlin.math.abs

sealed interface FunctionGraphOrthonormalizeResult {
    data class Applied(val viewport: GraphViewport, val alreadyOrthonormal: Boolean = false) : FunctionGraphOrthonormalizeResult
    data object Rejected : FunctionGraphOrthonormalizeResult
}

/** One-shot equal-pixel-scale operation. It preserves the center and never narrows either range. */
object FunctionGraphViewportOrthonormalizer {
    private const val RELATIVE_SCALE_TOLERANCE = 1e-10

    fun orthonormalize(viewport: GraphViewport, plot: PlotRect): FunctionGraphOrthonormalizeResult {
        if (!plot.width.isFinite() || !plot.height.isFinite() || plot.width <= 0.0 || plot.height <= 0.0) {
            return FunctionGraphOrthonormalizeResult.Rejected
        }
        val xSpan = viewport.x.maximum - viewport.x.minimum
        val ySpan = viewport.y.maximum - viewport.y.minimum
        if (!xSpan.isFinite() || !ySpan.isFinite() || xSpan <= 0.0 || ySpan <= 0.0) return FunctionGraphOrthonormalizeResult.Rejected
        val xScale = plot.width / xSpan
        val yScale = plot.height / ySpan
        if (!xScale.isFinite() || !yScale.isFinite() || xScale <= 0.0 || yScale <= 0.0) return FunctionGraphOrthonormalizeResult.Rejected
        val larger = maxOf(xScale, yScale)
        if (abs(xScale - yScale) <= larger * RELATIVE_SCALE_TOLERANCE) {
            return FunctionGraphOrthonormalizeResult.Applied(viewport, alreadyOrthonormal = true)
        }

        // The lower existing pixels-per-unit scale determines the common scale; only the axis
        // currently displaying more pixels per unit must expand.
        val targetScale = minOf(xScale, yScale)
        val desiredX = maxOf(xSpan, plot.width / targetScale)
        val desiredY = maxOf(ySpan, plot.height / targetScale)
        val x = expandedAroundCenter(viewport.x, desiredX) ?: return FunctionGraphOrthonormalizeResult.Rejected
        val y = expandedAroundCenter(viewport.y, desiredY) ?: return FunctionGraphOrthonormalizeResult.Rejected
        if (!contains(x, viewport.x) || !contains(y, viewport.y) ||
            !FunctionGraphViewportController.isSafe(x, plot.width.toInt().coerceAtLeast(1)) ||
            !FunctionGraphViewportController.isSafe(y, plot.height.toInt().coerceAtLeast(1))
        ) return FunctionGraphOrthonormalizeResult.Rejected

        val actualX = plot.width / (x.maximum - x.minimum)
        val actualY = plot.height / (y.maximum - y.minimum)
        if (!actualX.isFinite() || !actualY.isFinite() ||
            abs(actualX - actualY) > maxOf(actualX, actualY) * RELATIVE_SCALE_TOLERANCE
        ) return FunctionGraphOrthonormalizeResult.Rejected
        return FunctionGraphOrthonormalizeResult.Applied(GraphViewport(x, y))
    }

    private fun expandedAroundCenter(range: AxisRange, requestedSpan: Double): AxisRange? {
        if (!requestedSpan.isFinite() || requestedSpan <= 0.0) return null
        val existing = range.maximum - range.minimum
        val span = maxOf(existing, requestedSpan)
        if (span > FunctionGraphViewportController.MAX_SPAN) return null
        val center = range.minimum / 2.0 + range.maximum / 2.0
        val half = span / 2.0
        return runCatching { AxisRange(center - half, center + half) }.getOrNull()
    }

    private fun contains(outer: AxisRange, inner: AxisRange) = outer.minimum <= inner.minimum && outer.maximum >= inner.maximum
}
