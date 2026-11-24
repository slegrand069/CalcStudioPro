package com.example.calcstudiopro.functions.graph.interaction

import com.example.calcstudiopro.statistics.graph.domain.GraphTransform
import com.example.calcstudiopro.statistics.graph.domain.panByPixels
import com.example.calcstudiopro.statistics.graph.model.AxisRange
import com.example.calcstudiopro.statistics.graph.model.DataPoint
import com.example.calcstudiopro.statistics.graph.model.GraphViewport
import com.example.calcstudiopro.statistics.graph.model.PlotRect
import com.example.calcstudiopro.statistics.graph.model.ScreenPoint
import kotlin.math.abs

/** Pure viewport math for the Functions grapher. It does not share mutable state with Statistics. */
object FunctionGraphViewportController {
    const val MIN_SPAN = 1e-9
    const val MAX_SPAN = 1e12
    const val MIN_EVENT_ZOOM = 0.25
    const val MAX_EVENT_ZOOM = 4.0
    private const val ULPS_PER_PIXEL = 1.0
    private const val MIN_ULPS_PER_RANGE = 4.0

    val INITIAL = GraphViewport(AxisRange(-10.0, 10.0), AxisRange(-10.0, 10.0))

    fun reset(): GraphViewport = INITIAL

    /** Pan in screen pixels. X and Y remain independent and invalid/unsafe candidates are refused per axis. */
    fun pan(viewport: GraphViewport, deltaX: Double, deltaY: Double, plot: PlotRect): GraphViewport {
        if (!deltaX.isFinite() || !deltaY.isFinite()) return viewport
        val candidate = viewport.panByPixels(deltaX, deltaY, plot)
        return GraphViewport(
            candidate.x.takeIf { isSafe(it, plot.width.toInt().coerceAtLeast(1)) } ?: viewport.x,
            candidate.y.takeIf { isSafe(it, plot.height.toInt().coerceAtLeast(1)) } ?: viewport.y
        )
    }

    /**
     * Applies scale and translation in one operation. The data point under [previousCentroid] remains
     * under [currentCentroid], including when the pinch centroid moves. The same feasible zoom factor
     * is used on both axes; their ranges and pixels-per-unit remain independent.
     */
    fun transform(
        viewport: GraphViewport,
        previousCentroid: ScreenPoint,
        currentCentroid: ScreenPoint,
        requestedFactor: Double,
        plot: PlotRect
    ): GraphViewport {
        if (!previousCentroid.x.isFinite() || !previousCentroid.y.isFinite() ||
            !currentCentroid.x.isFinite() || !currentCentroid.y.isFinite()
        ) return viewport

        val focus = GraphTransform(viewport, plot).screenToData(previousCentroid)
        if (!focus.x.isFinite() || !focus.y.isFinite()) return viewport
        val requested = if (requestedFactor.isFinite() && requestedFactor > 0.0) {
            requestedFactor.coerceIn(MIN_EVENT_ZOOM, MAX_EVENT_ZOOM)
        } else 1.0

        val xPixels = plot.width.toInt().coerceAtLeast(1)
        val yPixels = plot.height.toInt().coerceAtLeast(1)
        val minimumX = minimumRepresentableSpan(focus.x, xPixels)
        val minimumY = minimumRepresentableSpan(focus.y, yPixels)
        if (minimumX > MAX_SPAN || minimumY > MAX_SPAN) return viewport

        val lowerFactor = maxOf(viewport.x.span() / MAX_SPAN, viewport.y.span() / MAX_SPAN, MIN_EVENT_ZOOM)
        val upperFactor = minOf(viewport.x.span() / minimumX, viewport.y.span() / minimumY, MAX_EVENT_ZOOM)
        if (!lowerFactor.isFinite() || !upperFactor.isFinite() || lowerFactor > upperFactor) return viewport
        val factor = requested.coerceIn(lowerFactor, upperFactor)

        val xFraction = ((currentCentroid.x - plot.left) / plot.width).takeIf(Double::isFinite) ?: return viewport
        val yFraction = ((currentCentroid.y - plot.top) / plot.height).takeIf(Double::isFinite) ?: return viewport
        val xRange = anchoredRange(focus.x, viewport.x.span() / factor, xFraction, isY = false)
            ?.takeIf { isSafe(it, xPixels) } ?: viewport.x
        val yRange = anchoredRange(focus.y, viewport.y.span() / factor, yFraction, isY = true)
            ?.takeIf { isSafe(it, yPixels) } ?: viewport.y
        return GraphViewport(xRange, yRange)
    }

    /** A layout/orientation change affects pixels only; mathematical bounds are retained verbatim. */
    fun resized(viewport: GraphViewport, @Suppress("UNUSED_PARAMETER") oldPlot: PlotRect, @Suppress("UNUSED_PARAMETER") newPlot: PlotRect) = viewport

    fun isSafe(range: AxisRange, effectivePixels: Int): Boolean {
        val span = range.span()
        if (!span.isFinite() || span !in MIN_SPAN..MAX_SPAN) return false
        val center = range.minimum / 2.0 + range.maximum / 2.0
        return span >= minimumRepresentableSpan(center, effectivePixels)
    }

    private fun minimumRepresentableSpan(center: Double, effectivePixels: Int): Double {
        val ulp = Math.ulp(center)
        return maxOf(MIN_SPAN, ulp * maxOf(MIN_ULPS_PER_RANGE, effectivePixels * ULPS_PER_PIXEL))
    }

    private fun anchoredRange(focus: Double, span: Double, fraction: Double, isY: Boolean): AxisRange? {
        if (!focus.isFinite() || !span.isFinite() || span <= 0.0 || !fraction.isFinite()) return null
        val first = fraction * span
        val second = (1.0 - fraction) * span
        val minimum = if (isY) focus - second else focus - first
        val maximum = if (isY) focus + first else focus + second
        if (!minimum.isFinite() || !maximum.isFinite() || minimum >= maximum) return null
        return runCatching { AxisRange(minimum, maximum) }.getOrNull()
    }

    private fun AxisRange.span(): Double = maximum - minimum
}
