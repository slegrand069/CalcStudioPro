package com.example.calcstudiopro.statistics.graph.domain

import com.example.calcstudiopro.statistics.graph.model.AxisRange
import com.example.calcstudiopro.statistics.graph.model.GraphSnapshot
import com.example.calcstudiopro.statistics.graph.model.GraphViewport
import com.example.calcstudiopro.statistics.graph.model.DataPoint
import kotlin.math.abs

object GraphRange {
    fun autoFit(snapshot: GraphSnapshot, paddingFraction: Double = 0.08): GraphViewport {
        require(paddingFraction.isFinite() && paddingFraction >= 0.0)
        val points = snapshot.series.asSequence().flatMap { series -> sequence {
            yieldAll(series.points.asSequence().filter { it.x.isFinite() && it.y.isFinite() }
                .map { DataPoint(it.x, it.y) })
            series.bars.forEach { bar ->
                if (bar.xMinimum.isFinite() && bar.xMaximum.isFinite() && bar.yMinimum.isFinite() && bar.yMaximum.isFinite()) {
                    yield(DataPoint(bar.xMinimum, bar.yMinimum))
                    yield(DataPoint(bar.xMaximum, bar.yMaximum))
                }
            }
            series.segments.forEach { segment ->
                if (segment.x1.isFinite() && segment.y1.isFinite()) yield(DataPoint(segment.x1, segment.y1))
                if (segment.x2.isFinite() && segment.y2.isFinite()) yield(DataPoint(segment.x2, segment.y2))
            }
        } }.toList()
        if (points.isEmpty()) return GraphViewport(AxisRange(-1.0, 1.0), AxisRange(-1.0, 1.0))
        return GraphViewport(
            fitAxis(points.minOf { it.x }, points.maxOf { it.x }, paddingFraction),
            fitAxis(points.minOf { it.y }, points.maxOf { it.y }, paddingFraction)
        )
    }

    private fun fitAxis(minimum: Double, maximum: Double, paddingFraction: Double): AxisRange {
        val scale = maxOf(abs(minimum), abs(maximum))
        val span = maximum - minimum
        val ulpPadding = maxOf(Math.ulp(minimum), Math.ulp(maximum)) * 2.0
        val padding = if (minimum == maximum) {
            if (scale == 0.0) 1.0 else maxOf(scale * maxOf(paddingFraction, 0.05), ulpPadding)
        } else {
            val proportional = if (span.isFinite()) span * paddingFraction else scale * paddingFraction
            maxOf(proportional, ulpPadding)
        }
        var low = saturatingAdd(minimum, -padding)
        var high = saturatingAdd(maximum, padding)
        if (low == high) {
            val delta = if (scale == 0.0) 1.0 else maxOf(Math.ulp(scale) * 8.0, scale * 0.05)
            low = saturatingAdd(minimum, -delta)
            high = saturatingAdd(maximum, delta)
        }
        // A non-zero finite observation always has at least one adjacent finite Double.
        if (low == high) {
            if (high < Double.MAX_VALUE) high = Math.nextUp(high) else low = Math.nextDown(low)
        }
        return AxisRange(low, high)
    }

    internal fun saturatingAdd(a: Double, b: Double): Double {
        val sum = a + b
        if (sum.isFinite()) return sum
        return if (b >= 0.0) Double.MAX_VALUE else -Double.MAX_VALUE
    }
}
