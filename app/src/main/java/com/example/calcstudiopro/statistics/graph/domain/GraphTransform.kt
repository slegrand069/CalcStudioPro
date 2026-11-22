package com.example.calcstudiopro.statistics.graph.domain

import com.example.calcstudiopro.statistics.graph.model.AxisRange
import com.example.calcstudiopro.statistics.graph.model.DataPoint
import com.example.calcstudiopro.statistics.graph.model.GraphViewport
import com.example.calcstudiopro.statistics.graph.model.PlotRect
import com.example.calcstudiopro.statistics.graph.model.ScreenPoint
import kotlin.math.abs

class GraphTransform(private val viewport: GraphViewport, private val plot: PlotRect) {
    fun dataToScreen(point: DataPoint): ScreenPoint {
        val xFraction = fraction(point.x, viewport.x)
        val yFraction = fraction(point.y, viewport.y)
        return ScreenPoint(plot.left + xFraction * plot.width, plot.top + (1.0 - yFraction) * plot.height)
    }

    fun screenToData(point: ScreenPoint): DataPoint {
        val xFraction = (point.x - plot.left) / plot.width
        val yFraction = 1.0 - (point.y - plot.top) / plot.height
        return DataPoint(interpolate(viewport.x.minimum, viewport.x.maximum, xFraction),
            interpolate(viewport.y.minimum, viewport.y.maximum, yFraction))
    }

    private fun fraction(value: Double, range: AxisRange): Double {
        val span = range.maximum - range.minimum
        val offset = value - range.minimum
        if (span.isFinite() && offset.isFinite()) return offset / span
        val scale = maxOf(abs(range.minimum), abs(range.maximum), abs(value))
        val low = range.minimum / scale
        val high = range.maximum / scale
        return (value / scale - low) / (high - low)
    }

    companion object {
        /** Overflow-resistant interpolation, including extrapolation for pan/zoom operations. */
        fun interpolate(a: Double, b: Double, fraction: Double): Double {
            val scale = maxOf(abs(a), abs(b))
            if (scale == 0.0) return 0.0
            val normalized = a / scale + (b / scale - a / scale) * fraction
            val value = normalized * scale
            return when {
                value.isFinite() -> value
                normalized > 0.0 -> Double.MAX_VALUE
                else -> -Double.MAX_VALUE
            }
        }
    }
}

fun GraphViewport.zoomAt(focus: DataPoint, factor: Double): GraphViewport {
    if (!factor.isFinite() || factor <= 0.0) return this
    val shrink = (1.0 / factor).coerceIn(0.25, 4.0)
    fun zoom(range: AxisRange, value: Double): AxisRange {
        val center = value.coerceIn(range.minimum, range.maximum)
        val min = GraphTransform.interpolate(center, range.minimum, shrink)
        val max = GraphTransform.interpolate(center, range.maximum, shrink)
        return if (min.isFinite() && max.isFinite() && min < max) AxisRange(min, max) else range
    }
    return GraphViewport(zoom(x, focus.x), zoom(y, focus.y))
}

/** Moves the data under the finger in the same direction as the finger. */
fun GraphViewport.panByPixels(deltaX: Double, deltaY: Double, plot: PlotRect): GraphViewport {
    if (!deltaX.isFinite() || !deltaY.isFinite()) return this
    fun shift(range: AxisRange, fraction: Double): AxisRange {
        val min = GraphTransform.interpolate(range.minimum, range.maximum, fraction)
        val max = GraphTransform.interpolate(range.maximum, range.minimum, -fraction)
        return if (min.isFinite() && max.isFinite() && min < max) AxisRange(min, max) else range
    }
    return GraphViewport(shift(x, -deltaX / plot.width), shift(y, deltaY / plot.height))
}
