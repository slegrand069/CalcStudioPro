package com.example.calcstudiopro.statistics.graph.render

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.calcstudiopro.statistics.graph.domain.GraphTicks
import com.example.calcstudiopro.statistics.graph.domain.GraphPaths
import com.example.calcstudiopro.statistics.graph.domain.GraphTransform
import com.example.calcstudiopro.statistics.graph.model.DataPoint
import com.example.calcstudiopro.statistics.graph.model.GraphSelection
import com.example.calcstudiopro.statistics.graph.model.GraphSeries
import com.example.calcstudiopro.statistics.graph.model.GraphSeriesKind
import com.example.calcstudiopro.statistics.graph.model.GraphSnapshot
import com.example.calcstudiopro.statistics.graph.model.GraphTickMode
import com.example.calcstudiopro.statistics.graph.model.GraphViewport
import com.example.calcstudiopro.statistics.graph.model.PlotRect
import kotlin.math.max

data class GraphRenderStyle(
    val axisColor: Color,
    val gridColor: Color,
    val pointColors: List<Color>,
    val selectedColor: Color,
    val labelColor: Color,
    val pointRadiusDp: Float = 4f,
    val lineWidthDp: Float = 2f
)

fun plotRectForCanvas(widthPixels: Double, heightPixels: Double, density: Double): PlotRect {
    val left = 62.0 * density
    val right = 16.0 * density
    val top = 16.0 * density
    val bottom = 54.0 * density
    return PlotRect(left, top, max(1.0, widthPixels - left - right), max(1.0, heightPixels - top - bottom))
}

fun DrawScope.drawGraph(
    snapshot: GraphSnapshot,
    viewport: GraphViewport,
    selection: GraphSelection?,
    style: GraphRenderStyle,
    textMeasurer: androidx.compose.ui.text.TextMeasurer
) {
    val plot = plotRectForCanvas(size.width.toDouble(), size.height.toDouble(), density.toDouble())
    val transform = GraphTransform(viewport, plot)
    val tickStyle = TextStyle(color = style.labelColor, fontSize = 11.sp)
    val titleStyle = TextStyle(color = style.labelColor, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    val xTicks = GraphTicks.calculate(viewport.x, (plot.width / (72.0 * density)).toInt().coerceIn(2, 12), snapshot.xTickMode)
    val yTicks = GraphTicks.calculate(viewport.y, (plot.height / (42.0 * density)).toInt().coerceIn(2, 10), snapshot.yTickMode)

    if (snapshot.showGrid) {
        if (snapshot.showXAxis) xTicks.forEach { tick ->
            val x = transform.dataToScreen(DataPoint(tick.value, viewport.y.minimum)).x.toFloat()
            drawLine(style.gridColor, Offset(x, plot.top.toFloat()), Offset(x, (plot.top + plot.height).toFloat()), 1f)
        }
        if (snapshot.showYAxis) yTicks.forEach { tick ->
            val y = transform.dataToScreen(DataPoint(viewport.x.minimum, tick.value)).y.toFloat()
            drawLine(style.gridColor, Offset(plot.left.toFloat(), y), Offset((plot.left + plot.width).toFloat(), y), 1f)
        }
    }

    if (snapshot.showYAxis) drawLine(style.axisColor, Offset(plot.left.toFloat(), plot.top.toFloat()), Offset(plot.left.toFloat(), (plot.top + plot.height).toFloat()), 1.5f)
    if (snapshot.showXAxis) drawLine(style.axisColor, Offset(plot.left.toFloat(), (plot.top + plot.height).toFloat()), Offset((plot.left + plot.width).toFloat(), (plot.top + plot.height).toFloat()), 1.5f)

    if (snapshot.showTicks && snapshot.showXAxis) xTicks.forEach { tick ->
        val measured = textMeasurer.measure(AnnotatedString(tick.label), tickStyle)
        val x = transform.dataToScreen(DataPoint(tick.value, viewport.y.minimum)).x
        drawText(measured, topLeft = Offset((x - measured.size.width / 2.0).toFloat(), (plot.top + plot.height + 4.0 * density).toFloat()))
    }
    if (snapshot.showTicks && snapshot.showYAxis) yTicks.forEach { tick ->
        val measured = textMeasurer.measure(AnnotatedString(tick.label), tickStyle)
        val y = transform.dataToScreen(DataPoint(viewport.x.minimum, tick.value)).y
        drawText(measured, topLeft = Offset((plot.left - measured.size.width - 6.0 * density).toFloat(), (y - measured.size.height / 2.0).toFloat()))
    }

    val xTitle = textMeasurer.measure(AnnotatedString(snapshot.xAxisLabel), titleStyle)
    if (snapshot.showXAxis && snapshot.xAxisLabel.isNotBlank()) drawText(xTitle, topLeft = Offset(
        (plot.left + plot.width / 2.0 - xTitle.size.width / 2.0).toFloat(),
        (plot.top + plot.height + 30.0 * density).toFloat()
    ))
    val yTitle = textMeasurer.measure(AnnotatedString(snapshot.yAxisLabel), titleStyle)
    if (snapshot.showYAxis && snapshot.yAxisLabel.isNotBlank()) rotate(-90f, pivot = Offset((18.0 * density).toFloat(), (plot.top + plot.height / 2.0).toFloat())) {
        drawText(yTitle, topLeft = Offset(
            (18.0 * density - yTitle.size.width / 2.0).toFloat(),
            (plot.top + plot.height / 2.0 - yTitle.size.height / 2.0).toFloat()
        ))
    }

    clipRect(plot.left.toFloat(), plot.top.toFloat(), (plot.left + plot.width).toFloat(), (plot.top + plot.height).toFloat()) {
        snapshot.series.forEach { series -> drawSeries(series, transform, selection, style) }
    }
}

private fun DrawScope.drawSeries(
    series: GraphSeries,
    transform: GraphTransform,
    selection: GraphSelection?,
    style: GraphRenderStyle
) {
    val color = style.pointColors[series.colorIndex.mod(style.pointColors.size)]
    val line = series.kind == GraphSeriesKind.LINE || series.kind == GraphSeriesKind.POINTS_AND_LINE
    val points = series.kind != GraphSeriesKind.LINE && series.kind != GraphSeriesKind.BARS
    series.bars.forEach { bar ->
        if (listOf(bar.xMinimum, bar.xMaximum, bar.yMinimum, bar.yMaximum).all(Double::isFinite)) {
            val first = transform.dataToScreen(DataPoint(bar.xMinimum, bar.yMaximum))
            val second = transform.dataToScreen(DataPoint(bar.xMaximum, bar.yMinimum))
            val left = minOf(first.x, second.x).toFloat()
            val top = minOf(first.y, second.y).toFloat()
            val width = kotlin.math.abs(second.x - first.x).toFloat().coerceAtLeast(0.5f)
            val height = kotlin.math.abs(second.y - first.y).toFloat().coerceAtLeast(0.5f)
            val selected = selection?.seriesId == series.id && selection.point.id == bar.id
            val rectSize = Size(width, height)
            drawRect(color.copy(alpha = bar.fillAlpha), Offset(left, top), rectSize)
            if (selected || series.kind != GraphSeriesKind.BARS) {
                drawRect(
                    if (selected) style.selectedColor else color,
                    Offset(left, top), rectSize,
                    style = Stroke(width = if (selected) style.lineWidthDp.dp.toPx() + 1.dp.toPx() else 1.dp.toPx())
                )
            }
        }
    }
    if (line) {
        GraphPaths.connectedSegments(series.points).forEach { segment ->
            if (segment.size >= 2) {
                val path = Path()
                segment.forEachIndexed { index, point ->
                    val screen = transform.dataToScreen(DataPoint(point.x, point.y))
                    if (index == 0) path.moveTo(screen.x.toFloat(), screen.y.toFloat())
                    else path.lineTo(screen.x.toFloat(), screen.y.toFloat())
                }
                drawPath(path, color, style = Stroke(width = style.lineWidthDp.dp.toPx()))
            }
        }
    }
    series.segments.forEach { segment ->
        if (listOf(segment.x1, segment.y1, segment.x2, segment.y2).all(Double::isFinite)) {
            val start = transform.dataToScreen(DataPoint(segment.x1, segment.y1))
            val end = transform.dataToScreen(DataPoint(segment.x2, segment.y2))
            val selected = selection?.seriesId == series.id && selection.point.id == segment.id
            drawLine(
                if (selected) style.selectedColor else color,
                Offset(start.x.toFloat(), start.y.toFloat()), Offset(end.x.toFloat(), end.y.toFloat()),
                strokeWidth = style.lineWidthDp.dp.toPx() + if (selected) 2.dp.toPx() else 0f
            )
        }
    }
    if (points) {
        val radius = style.pointRadiusDp.dp.toPx()
        series.points.forEach { point ->
            if (point.x.isFinite() && point.y.isFinite()) {
                val screen = transform.dataToScreen(DataPoint(point.x, point.y))
                val selected = selection?.seriesId == series.id && selection.point.id == point.id
                val pointRadius = if (selected) radius * 1.8f else radius
                drawCircle(color, pointRadius, Offset(screen.x.toFloat(), screen.y.toFloat()))
                if (selected) drawCircle(style.selectedColor, pointRadius + 2.0.dp.toPx(), Offset(screen.x.toFloat(), screen.y.toFloat()), style = Stroke(2.0.dp.toPx()))
            }
        }
    }
}
