package com.example.calcstudiopro.functions.graph.render

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.dp

internal data class CachedFunctionPath(val path: Path, val color: Color, val outline: Color?)
internal data class CachedTickLabel(val x: Float, val y: Float, val layout: TextLayoutResult)

data class FunctionGraphPathTransform(
    val scaleX: Double,
    val scaleY: Double,
    val translateX: Double,
    val translateY: Double
) {
    val isFinite: Boolean get() = scaleX.isFinite() && scaleY.isFinite() &&
        translateX.isFinite() && translateY.isFinite() && scaleX > 0.0 && scaleY > 0.0

    companion object {
        /** Affine mapping between two viewports for a path cached in [sourcePlot] screen coordinates. */
        fun between(
            sourceViewport: com.example.calcstudiopro.statistics.graph.model.GraphViewport,
            targetViewport: com.example.calcstudiopro.statistics.graph.model.GraphViewport,
            sourcePlot: com.example.calcstudiopro.statistics.graph.model.PlotRect,
            targetPlot: com.example.calcstudiopro.statistics.graph.model.PlotRect
        ): FunctionGraphPathTransform {
            val sourceXSpan = sourceViewport.x.maximum - sourceViewport.x.minimum
            val targetXSpan = targetViewport.x.maximum - targetViewport.x.minimum
            val sourceYSpan = sourceViewport.y.maximum - sourceViewport.y.minimum
            val targetYSpan = targetViewport.y.maximum - targetViewport.y.minimum
            val sx = sourceXSpan / targetXSpan * targetPlot.width / sourcePlot.width
            val sy = sourceYSpan / targetYSpan * targetPlot.height / sourcePlot.height
            val tx = targetPlot.left + (sourceViewport.x.minimum - targetViewport.x.minimum) / targetXSpan * targetPlot.width - sx * sourcePlot.left
            val ty = targetPlot.top + (targetViewport.y.maximum - sourceViewport.y.maximum) / targetYSpan * targetPlot.height - sy * sourcePlot.top
            return FunctionGraphPathTransform(sx, sy, tx, ty)
        }
    }
}

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
    labelColor: Color,
    pathTransform: FunctionGraphPathTransform = FunctionGraphPathTransform(1.0, 1.0, 0.0, 0.0)
) {
    drawRect(background)
    geometry.minorGrid.forEach { line -> drawLine(minorGridColor, line.start.toOffset(), line.end.toOffset(), strokeWidth = 1f) }
    geometry.majorGrid.forEach { line -> drawLine(majorGridColor, line.start.toOffset(), line.end.toOffset(), strokeWidth = 1f) }

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
        if (pathTransform.isFinite) {
            val sx = pathTransform.scaleX.toFloat()
            val sy = pathTransform.scaleY.toFloat()
            val tx = pathTransform.translateX.toFloat()
            val ty = pathTransform.translateY.toFloat()
            if (sx.isFinite() && sy.isFinite() && tx.isFinite() && ty.isFinite()) {
                val strokeScale = kotlin.math.sqrt((sx.toDouble() * sy.toDouble()).coerceAtLeast(1e-30)).toFloat()
                    .coerceIn(1e-20f, 1e20f)
                withTransform({
                    translate(tx, ty)
                    scale(sx, sy, pivot = Offset.Zero)
                }) {
                    paths.forEach { curvePaths ->
                        curvePaths.forEach { cached ->
                            cached.outline?.let { outline ->
                                drawPath(cached.path, outline, style = Stroke(width = 4.dp.toPx() / strokeScale))
                            }
                            drawPath(cached.path, cached.color, style = Stroke(width = 2.dp.toPx() / strokeScale))
                        }
                    }
                }
            }
        }
    }
}

private fun com.example.calcstudiopro.statistics.graph.model.ScreenPoint.toOffset() = Offset(x.toFloat(), y.toFloat())
