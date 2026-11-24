package com.example.calcstudiopro.functions.graph.render

import com.example.calcstudiopro.functions.graph.FunctionGraphPoint
import com.example.calcstudiopro.statistics.graph.domain.AxisTick
import com.example.calcstudiopro.statistics.graph.domain.GraphTicks
import com.example.calcstudiopro.statistics.graph.domain.GraphTransform
import com.example.calcstudiopro.statistics.graph.model.AxisRange
import com.example.calcstudiopro.statistics.graph.model.DataPoint
import com.example.calcstudiopro.statistics.graph.model.GraphViewport
import com.example.calcstudiopro.statistics.graph.model.PlotRect
import com.example.calcstudiopro.statistics.graph.model.ScreenPoint
import com.example.calcstudiopro.functions.domain.FunctionSlotId
import com.example.calcstudiopro.functions.graph.FunctionGraphDiagnostics
import com.example.calcstudiopro.functions.graph.FunctionGraphStatus
import java.util.Collections
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import kotlin.math.max
import kotlin.math.abs

enum class FunctionGridMode { NONE, MAJOR, MAJOR_AND_MINOR }

data class FunctionGraphGeometryRequest(
    val model: FunctionGraphRenderModel,
    val viewport: GraphViewport,
    val plot: PlotRect,
    val gridMode: FunctionGridMode,
    /** The actual opaque or translucent canvas background, ARGB encoded. */
    val backgroundArgb: Long,
    /** Identifies displayed framing independently of [FunctionGraphRenderModel.dataGeneration]. */
    val viewportGeneration: Long
) {
    init {
        require(backgroundArgb in 0L..0xFFFFFFFFL)
        require(viewportGeneration >= 0)
    }
}

data class FunctionGraphLine(val start: ScreenPoint, val end: ScreenPoint)
data class FunctionGraphTickGeometry(val value: Double, val label: String, val position: ScreenPoint)

class FunctionCurveGeometry(
    val id: FunctionSlotId,
    val colorArgb: Long,
    val outlineArgb: Long?,
    val isVisible: Boolean,
    val status: FunctionGraphStatus?,
    val diagnostics: FunctionGraphDiagnostics?,
    segments: List<List<ScreenPoint>>
) {
    val segments: List<List<ScreenPoint>> = Collections.unmodifiableList(
        segments.map { Collections.unmodifiableList(ArrayList(it)) }
    )
}

/** All positions are screen-space logical pixels; curve sample data itself remains mathematical in [model]. */
class FunctionGraphGeometry(
    val dataGeneration: Long?,
    val viewportGeneration: Long,
    val viewport: GraphViewport,
    val plot: PlotRect,
    curves: List<FunctionCurveGeometry>,
    val verticalAxis: FunctionGraphLine?,
    val horizontalAxis: FunctionGraphLine?,
    xTicks: List<FunctionGraphTickGeometry>,
    yTicks: List<FunctionGraphTickGeometry>,
    majorGrid: List<FunctionGraphLine>,
    minorGrid: List<FunctionGraphLine>
) {
    val curves: List<FunctionCurveGeometry> = Collections.unmodifiableList(ArrayList(curves))
    val xTicks: List<FunctionGraphTickGeometry> = Collections.unmodifiableList(ArrayList(xTicks))
    val yTicks: List<FunctionGraphTickGeometry> = Collections.unmodifiableList(ArrayList(yTicks))
    val majorGrid: List<FunctionGraphLine> = Collections.unmodifiableList(ArrayList(majorGrid))
    val minorGrid: List<FunctionGraphLine> = Collections.unmodifiableList(ArrayList(minorGrid))
}

object FunctionGraphGeometryBuilder {
    private const val X_PIXELS_PER_TICK = 72.0
    private const val Y_PIXELS_PER_TICK = 48.0
    private const val MAX_TICK_COUNT = 40
    private const val MINOR_DIVISIONS = 4
    private const val MAX_MINOR_LINES_PER_AXIS = (MAX_TICK_COUNT - 1) * (MINOR_DIVISIONS - 1)

    fun build(request: FunctionGraphGeometryRequest, projectCurves: Boolean = true): FunctionGraphGeometry {
        val transform = GraphTransform(request.viewport, request.plot)
        val xTarget = (request.plot.width / X_PIXELS_PER_TICK).toInt().coerceIn(2, 12)
        val yTarget = (request.plot.height / Y_PIXELS_PER_TICK).toInt().coerceIn(2, 10)
        val xTicks = FunctionGraphTickNormalizer.normalize(GraphTicks.calculate(request.viewport.x, xTarget), request.viewport.x).map { tick ->
            FunctionGraphTickGeometry(tick.value, tick.label, transform.dataToScreen(DataPoint(tick.value, request.viewport.y.minimum)))
        }
        val yTicks = FunctionGraphTickNormalizer.normalize(GraphTicks.calculate(request.viewport.y, yTarget), request.viewport.y).map { tick ->
            FunctionGraphTickGeometry(tick.value, tick.label, transform.dataToScreen(DataPoint(request.viewport.x.minimum, tick.value)))
        }
        val vertical = if (request.viewport.x.minimum <= 0.0 && request.viewport.x.maximum >= 0.0) {
            val x = transform.dataToScreen(DataPoint(0.0, request.viewport.y.minimum)).x
            FunctionGraphLine(ScreenPoint(x, request.plot.top), ScreenPoint(x, request.plot.top + request.plot.height))
        } else null
        val horizontal = if (request.viewport.y.minimum <= 0.0 && request.viewport.y.maximum >= 0.0) {
            val y = transform.dataToScreen(DataPoint(request.viewport.x.minimum, 0.0)).y
            FunctionGraphLine(ScreenPoint(request.plot.left, y), ScreenPoint(request.plot.left + request.plot.width, y))
        } else null

        val majorGrid = if (request.gridMode == FunctionGridMode.NONE) emptyList() else buildMajorGrid(xTicks, yTicks, request.plot)
        val minorGrid = if (request.gridMode != FunctionGridMode.MAJOR_AND_MINOR) emptyList() else
            buildMinorGrid(request.viewport.x, request.viewport.y, xTicks, yTicks, transform, request.plot)

        val curves = request.model.curves.map { curve ->
            val color = curve.color.argb
            val outline = FunctionCurveContrastPolicy.outlineFor(color, request.backgroundArgb)
            val projected = if (projectCurves && curve.isVisible) curve.segments.flatMap { segment ->
                projectAndClipSegment(segment.points, request.viewport, request.plot)
            } else emptyList()
            FunctionCurveGeometry(curve.id, color, outline, curve.isVisible, curve.status, curve.diagnostics, projected)
        }

        return FunctionGraphGeometry(
            request.model.dataGeneration, request.viewportGeneration, request.viewport, request.plot,
            curves, vertical, horizontal, xTicks, yTicks, majorGrid, minorGrid
        )
    }

    private fun buildMajorGrid(
        xTicks: List<FunctionGraphTickGeometry>, yTicks: List<FunctionGraphTickGeometry>, plot: PlotRect
    ): List<FunctionGraphLine> = buildList(xTicks.size + yTicks.size) {
        xTicks.forEach { add(FunctionGraphLine(ScreenPoint(it.position.x, plot.top), ScreenPoint(it.position.x, plot.top + plot.height))) }
        yTicks.forEach { add(FunctionGraphLine(ScreenPoint(plot.left, it.position.y), ScreenPoint(plot.left + plot.width, it.position.y))) }
    }

    private fun buildMinorGrid(
        xRange: AxisRange,
        yRange: AxisRange,
        xTicks: List<FunctionGraphTickGeometry>,
        yTicks: List<FunctionGraphTickGeometry>,
        transform: GraphTransform,
        plot: PlotRect
    ): List<FunctionGraphLine> {
        val lines = ArrayList<FunctionGraphLine>()
        appendMinorLines(xTicks.map { it.value }, xRange, MAX_MINOR_LINES_PER_AXIS) { value ->
            val x = transform.dataToScreen(DataPoint(value, yRange.minimum)).x
            lines += FunctionGraphLine(ScreenPoint(x, plot.top), ScreenPoint(x, plot.top + plot.height))
        }
        appendMinorLines(yTicks.map { it.value }, yRange, MAX_MINOR_LINES_PER_AXIS) { value ->
            val y = transform.dataToScreen(DataPoint(xRange.minimum, value)).y
            lines += FunctionGraphLine(ScreenPoint(plot.left, y), ScreenPoint(plot.left + plot.width, y))
        }
        return lines
    }

    private inline fun appendMinorLines(
        majors: List<Double>, range: AxisRange, maxLines: Int, add: (Double) -> Unit
    ) {
        var emitted = 0
        for ((low, high) in majors.zipWithNext()) {
            if (emitted >= maxLines) break
            for (part in 1 until MINOR_DIVISIONS) {
                if (emitted >= maxLines) break
                // Weighted interpolation avoids overflowing (high - low) on extreme ranges.
                val fraction = part.toDouble() / MINOR_DIVISIONS
                val value = low * (1.0 - fraction) + high * fraction
                if (value.isFinite() && value > range.minimum && value < range.maximum) {
                    add(value)
                    emitted++
                }
            }
        }
    }

    /** Project each F2.1 segment independently and clip in normalized Double coordinates. */
    private fun projectAndClipSegment(
        points: List<FunctionGraphPoint>,
        viewport: GraphViewport,
        plot: PlotRect
    ): List<List<ScreenPoint>> {
        val normalized = points.map { point ->
            val x = normalizedCoordinate(point.x, viewport.x) ?: return emptyList()
            val y = normalizedCoordinate(point.y, viewport.y) ?: return emptyList()
            NormalizedPoint(x, y)
        }
        val visible = ArrayList<List<ScreenPoint>>()
        var current = ArrayList<ScreenPoint>()

        fun finish() {
            if (current.size >= 2 && current.zipWithNext().any { (a, b) -> a != b }) {
                visible += Collections.unmodifiableList(ArrayList(current))
            }
            current = ArrayList()
        }

        for ((a, b) in normalized.zipWithNext()) {
            val clipped = clipToUnitSquare(a, b)
            if (clipped == null) {
                finish()
                continue
            }
            val first = toScreen(clipped.first, plot)
            val last = toScreen(clipped.second, plot)
            if (first == null || last == null) {
                finish()
                continue
            }
            if (first == last) {
                finish()
                continue
            }
            if (current.isEmpty()) {
                current += first
                current += last
            } else if (current.last() == first) {
                current += last
            } else {
                finish()
                current += first
                current += last
            }
        }
        finish()
        return visible
    }

    private fun normalizedCoordinate(value: Double, range: AxisRange): Double? {
        if (!value.isFinite()) return null
        if (value == range.minimum) return 0.0
        if (value == range.maximum) return 1.0
        val scale = maxOf(abs(value), abs(range.minimum), abs(range.maximum))
        if (scale == 0.0 || !scale.isFinite()) return null
        val low = range.minimum / scale
        val high = range.maximum / scale
        val normalized = value / scale
        val span = high - low
        if (!span.isFinite() || span <= 0.0) return null
        val fraction = (normalized - low) / span
        return fraction.takeIf(Double::isFinite)
    }

    private data class NormalizedPoint(val x: Double, val y: Double)

    private fun clipToUnitSquare(a: NormalizedPoint, b: NormalizedPoint): Pair<NormalizedPoint, NormalizedPoint>? {
        if (a.x in 0.0..1.0 && a.y in 0.0..1.0 && b.x in 0.0..1.0 && b.y in 0.0..1.0) return a to b
        // Reject common off-screen runs before doing any intersection arithmetic.
        if ((a.x < 0.0 && b.x < 0.0) || (a.x > 1.0 && b.x > 1.0) ||
            (a.y < 0.0 && b.y < 0.0) || (a.y > 1.0 && b.y > 1.0)
        ) return null

        val largest = maxOf(abs(a.x), abs(a.y), abs(b.x), abs(b.y))
        val interval = if (largest <= FAST_CLIP_LIMIT) clipDouble(a, b) else clipDecimal(a, b)
        return interval?.let { (start, end) ->
            val clippedStart = NormalizedPoint(start.x.coerceIn(0.0, 1.0), start.y.coerceIn(0.0, 1.0))
            val clippedEnd = NormalizedPoint(end.x.coerceIn(0.0, 1.0), end.y.coerceIn(0.0, 1.0))
            clippedStart to clippedEnd
        }
    }

    /** Liang–Barsky clipping for ordinary coordinates; all intermediate values stay bounded. */
    private fun clipDouble(a: NormalizedPoint, b: NormalizedPoint): Pair<NormalizedPoint, NormalizedPoint>? {
        val dx = b.x - a.x
        val dy = b.y - a.y
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

        if (!test(-dx, a.x) || !test(dx, 1.0 - a.x) ||
            !test(-dy, a.y) || !test(dy, 1.0 - a.y) || enter > leave
        ) return null

        return interpolate(a, b, enter) to interpolate(a, b, leave)
    }

    /** High precision fallback preserves narrow visible crossings between very distant endpoints. */
    private fun clipDecimal(a: NormalizedPoint, b: NormalizedPoint): Pair<NormalizedPoint, NormalizedPoint>? {
        val x0 = BigDecimal.valueOf(a.x)
        val y0 = BigDecimal.valueOf(a.y)
        val dx = BigDecimal.valueOf(b.x).subtract(x0)
        val dy = BigDecimal.valueOf(b.y).subtract(y0)
        var enter = BigDecimal.ZERO
        var leave = BigDecimal.ONE

        fun test(p: BigDecimal, q: BigDecimal): Boolean {
            val sign = p.signum()
            if (sign == 0) return q.signum() >= 0
            val ratio = q.divide(p, CLIP_MATH_CONTEXT)
            if (sign < 0) {
                if (ratio > leave) return false
                if (ratio > enter) enter = ratio
            } else {
                if (ratio < enter) return false
                if (ratio < leave) leave = ratio
            }
            return true
        }

        if (!test(dx.negate(), x0) || !test(dx, BigDecimal.ONE.subtract(x0)) ||
            !test(dy.negate(), y0) || !test(dy, BigDecimal.ONE.subtract(y0)) || enter > leave
        ) return null

        fun pointAt(t: BigDecimal): NormalizedPoint {
            val x = x0.add(dx.multiply(t, CLIP_MATH_CONTEXT), CLIP_MATH_CONTEXT).toDouble()
            val y = y0.add(dy.multiply(t, CLIP_MATH_CONTEXT), CLIP_MATH_CONTEXT).toDouble()
            if (!x.isFinite() || !y.isFinite()) return NormalizedPoint(Double.NaN, Double.NaN)
            return NormalizedPoint(x, y)
        }

        val start = pointAt(enter)
        val end = pointAt(leave)
        if (!start.x.isFinite() || !start.y.isFinite() || !end.x.isFinite() || !end.y.isFinite()) return null
        return start to end
    }

    private fun interpolate(a: NormalizedPoint, b: NormalizedPoint, t: Double): NormalizedPoint {
        if (t <= 0.0) return a
        if (t >= 1.0) return b
        return NormalizedPoint(
            a.x * (1.0 - t) + b.x * t,
            a.y * (1.0 - t) + b.y * t
        )
    }

    private fun toScreen(point: NormalizedPoint, plot: PlotRect): ScreenPoint? {
        val x = plot.left + point.x * plot.width
        val y = plot.top + (1.0 - point.y) * plot.height
        if (!x.isFinite() || !y.isFinite() || abs(x) > Float.MAX_VALUE || abs(y) > Float.MAX_VALUE) return null
        return ScreenPoint(x, y)
    }

    private const val FAST_CLIP_LIMIT = 1_000_000.0
    private val CLIP_MATH_CONTEXT = MathContext(700, RoundingMode.HALF_EVEN)
}

/** Normalizes only floating-point noise around the mathematical zero tick. */
internal object FunctionGraphTickNormalizer {
    private const val STEP_RELATIVE_TOLERANCE = 1e-12
    private const val RANGE_ULP_TOLERANCE = 4.0

    fun normalize(ticks: List<AxisTick>, range: AxisRange): List<AxisTick> {
        if (ticks.isEmpty()) return ticks
        val step = estimateStep(ticks.map(AxisTick::value), range)
        val normalized = ticks.map { tick ->
            val value = normalizeValue(tick.value, step, range)
            AxisTick(value, GraphTicks.format(value))
        }
        return normalized.distinctBy(AxisTick::value)
    }

    /** Exposed internally for focused numerical tests; [step] is the local major-tick spacing. */
    fun normalizeValue(value: Double, step: Double, range: AxisRange): Double {
        if (!value.isFinite() || !step.isFinite() || step <= 0.0) return value
        val rangeScale = max(abs(range.minimum), abs(range.maximum))
        val tolerance = max(step * STEP_RELATIVE_TOLERANCE, Math.ulp(rangeScale) * RANGE_ULP_TOLERANCE)
        return if (abs(value) <= tolerance) 0.0 else value
    }

    private fun estimateStep(values: List<Double>, range: AxisRange): Double {
        val gaps = values.asSequence()
            .filter(Double::isFinite)
            .sorted()
            .zipWithNext { a, b -> b - a }
            .filter { it.isFinite() && it > 0.0 }
            .sorted()
            .toList()
        if (gaps.isNotEmpty()) {
            val middle = gaps.size / 2
            return if (gaps.size % 2 == 1) gaps[middle] else gaps[middle - 1] / 2.0 + gaps[middle] / 2.0
        }
        val span = range.maximum / 2.0 - range.minimum / 2.0
        return if (span.isFinite() && span > 0.0) span else max(abs(range.minimum), abs(range.maximum)) / 6.0
    }
}

/** Adds a black or white presentation-only halo when the saved curve color lacks 3:1 contrast. */
object FunctionCurveContrastPolicy {
    private const val BLACK: Long = 0xFF000000L
    private const val WHITE: Long = 0xFFFFFFFFL
    private const val MIN_GRAPHIC_CONTRAST = 3.0

    fun outlineFor(foregroundArgb: Long, backgroundArgb: Long): Long? {
        val foreground = luminance(foregroundArgb)
        val background = luminance(backgroundArgb)
        if (contrast(foreground, background) >= MIN_GRAPHIC_CONTRAST) return null
        val blackScore = minOf(contrast(0.0, foreground), contrast(0.0, background))
        val whiteScore = minOf(contrast(1.0, foreground), contrast(1.0, background))
        return if (whiteScore > blackScore) WHITE else BLACK
    }

    private fun luminance(argb: Long): Double {
        fun channel(shift: Int): Double {
            val srgb = ((argb ushr shift) and 0xFFL) / 255.0
            return if (srgb <= 0.04045) srgb / 12.92 else Math.pow((srgb + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
    }

    private fun contrast(a: Double, b: Double): Double = (max(a, b) + 0.05) / (minOf(a, b) + 0.05)
}
