package com.example.calcstudiopro.functions.graph.render

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.dp

internal data class CachedFunctionPath(val path: Path, val color: Color, val outline: Color?)
internal data class CachedTickLabel(val x: Float, val y: Float, val layout: TextLayoutResult)

/** Canvas-only drawing; this layer never evaluates or compiles expressions. */
internal fun DrawScope.drawFunctionGraph(
    geometry: FunctionGraphGeometry,
    paths: List<List<CachedFunctionPath>>,
    xLabels: List<CachedTickLabel>,
    yLabels: List<CachedTickLabel>,
    background: Color,
    axisColor: Color,
    majorGridColor: Color,
    minorGridColor: Color,
    labelColor: Color
) {
    drawRect(background)
    geometry.minorGrid.forEach { line -> drawLine(minorGridColor, line.start.toOffset(), line.end.toOffset(), strokeWidth = 1f) }
    geometry.majorGrid.forEach { line -> drawLine(majorGridColor, line.start.toOffset(), line.end.toOffset(), strokeWidth = 1f) }

    val plotRight = (geometry.plot.left + geometry.plot.width).toFloat()
    val plotBottom = (geometry.plot.top + geometry.plot.height).toFloat()
    geometry.xTicks.forEach { tick ->
        val x = tick.position.x.toFloat()
        drawLine(axisColor.copy(alpha = 0.65f), Offset(x, plotBottom), Offset(x, plotBottom + 4.dp.toPx()), 1f)
    }
    geometry.yTicks.forEach { tick ->
        val y = tick.position.y.toFloat()
        val left = geometry.plot.left.toFloat()
        drawLine(axisColor.copy(alpha = 0.65f), Offset(left - 4.dp.toPx(), y), Offset(left, y), 1f)
    }

    geometry.verticalAxis?.let { drawLine(axisColor, it.start.toOffset(), it.end.toOffset(), strokeWidth = 1.5.dp.toPx()) }
    geometry.horizontalAxis?.let { drawLine(axisColor, it.start.toOffset(), it.end.toOffset(), strokeWidth = 1.5.dp.toPx()) }

    // Labels remain available even when either mathematical axis lies outside the viewport.
    xLabels.forEach { drawText(it.layout, color = labelColor, topLeft = Offset(it.x, it.y)) }
    yLabels.forEach { drawText(it.layout, color = labelColor, topLeft = Offset(it.x, it.y)) }

    clipRect(
        left = geometry.plot.left.toFloat(),
        top = geometry.plot.top.toFloat(),
        right = (geometry.plot.left + geometry.plot.width).toFloat(),
        bottom = (geometry.plot.top + geometry.plot.height).toFloat()
    ) {
        paths.forEach { curvePaths ->
            curvePaths.forEach { cached ->
                cached.outline?.let { outline ->
                    drawPath(cached.path, outline, style = Stroke(width = 4.dp.toPx()))
                }
                drawPath(cached.path, cached.color, style = Stroke(width = 2.dp.toPx()))
            }
        }
    }
}

private fun com.example.calcstudiopro.statistics.graph.model.ScreenPoint.toOffset() = Offset(x.toFloat(), y.toFloat())
