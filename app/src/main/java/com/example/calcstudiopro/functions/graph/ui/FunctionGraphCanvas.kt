package com.example.calcstudiopro.functions.graph.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.calcstudiopro.functions.graph.FunctionGraphStatus
import com.example.calcstudiopro.functions.graph.render.CachedFunctionPath
import com.example.calcstudiopro.functions.graph.render.CachedTickLabel
import com.example.calcstudiopro.functions.graph.render.FunctionGraphGeometryBuilder
import com.example.calcstudiopro.functions.graph.render.FunctionGraphGeometryRequest
import com.example.calcstudiopro.functions.graph.render.FunctionGraphRenderModel
import com.example.calcstudiopro.functions.graph.render.FunctionGridMode
import com.example.calcstudiopro.functions.graph.render.drawFunctionGraph
import com.example.calcstudiopro.statistics.graph.model.GraphViewport
import com.example.calcstudiopro.statistics.graph.model.PlotRect
import kotlin.math.max

/**
 * Function graph display. All expression evaluation must happen before this composable receives [model].
 * The parent must provide bounded width and height; default height is 320dp.
 */
@Composable
fun FunctionGraphCanvas(
    model: FunctionGraphRenderModel,
    viewport: GraphViewport,
    gridMode: FunctionGridMode = FunctionGridMode.MAJOR,
    modifier: Modifier = Modifier.fillMaxWidth().height(320.dp),
    viewportGeneration: Long = 0L,
    testTag: String = "function-graph-canvas"
) {
    val colors = MaterialTheme.colorScheme
    val background = colors.surface
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    BoxWithConstraints(modifier) {
        val canvasWidth = with(density) { maxWidth.toPx() }
        val canvasHeight = with(density) { maxHeight.toPx() }
        val canvasBackgroundArgb = background.toArgb().toLong() and 0xFFFFFFFFL
        val plot = remember(canvasWidth, canvasHeight, density.density) {
            functionPlotRectForCanvas(canvasWidth, canvasHeight, density.density.toDouble())
        }
        val geometry = remember(model, viewport, gridMode, canvasBackgroundArgb, viewportGeneration, plot) {
            FunctionGraphGeometryBuilder.build(
                FunctionGraphGeometryRequest(
                    model = model,
                    viewport = viewport,
                    plot = plot,
                    gridMode = gridMode,
                    backgroundArgb = canvasBackgroundArgb,
                    viewportGeneration = viewportGeneration
                )
            )
        }

        val curvePaths = remember(geometry) {
            geometry.curves.map { curve ->
                if (!curve.isVisible) emptyList() else curve.segments.map { points ->
                    val path = Path()
                    points.forEachIndexed { index, point ->
                        if (index == 0) path.moveTo(point.x.toFloat(), point.y.toFloat())
                        else path.lineTo(point.x.toFloat(), point.y.toFloat())
                    }
                    CachedFunctionPath(
                        path = path,
                        color = Color(curve.colorArgb.toInt()),
                        outline = curve.outlineArgb?.let { Color(it.toInt()) }
                    )
                }
            }
        }

        val labelStyle = TextStyle(color = colors.onSurface, fontSize = 11.sp, fontWeight = FontWeight.Medium)
        val xLabels = remember(geometry.xTicks, labelStyle, canvasWidth, density.density) {
            placeXTickLabels(geometry, textMeasurer, labelStyle, canvasWidth, density.density)
        }
        val yLabels = remember(geometry.yTicks, labelStyle, geometry.plot, density.density) {
            placeYTickLabels(geometry, textMeasurer, labelStyle, density.density)
        }
        val visibleStatus = model.curves.joinToString("; ") { curve ->
            "${curve.id.code}: ${when {
                !curve.isVisible -> "masquée"
                curve.status == null -> "en attente"
                curve.status is FunctionGraphStatus.Error -> "erreur"
                curve.status == FunctionGraphStatus.NoDefinedPortion -> "aucune portion définie"
                curve.status is FunctionGraphStatus.Partial || curve.status is FunctionGraphStatus.BudgetExceeded -> "résultat partiel"
                curve.status == FunctionGraphStatus.Empty -> "vide"
                curve.segments.isEmpty() -> "aucun segment"
                else -> "visible"
            }}"
        }

        Canvas(
            modifier = Modifier.fillMaxSize().testTag(testTag).semantics {
                contentDescription = "Graphe de fonctions. Génération données ${model.dataGeneration ?: "aucune"}. $visibleStatus"
            }
        ) {
            drawFunctionGraph(
                geometry = geometry,
                paths = curvePaths,
                xLabels = xLabels,
                yLabels = yLabels,
                background = background,
                axisColor = colors.onSurface,
                majorGridColor = colors.outlineVariant.copy(alpha = 0.32f),
                minorGridColor = colors.outlineVariant.copy(alpha = 0.14f),
                labelColor = colors.onSurface
            )
        }
    }
}

private fun functionPlotRectForCanvas(width: Float, height: Float, density: Double): PlotRect {
    val left = 56.0 * density
    val right = 12.0 * density
    val top = 12.0 * density
    val bottom = 38.0 * density
    return PlotRect(left, top, max(1.0, width - left - right), max(1.0, height - top - bottom))
}

private fun placeXTickLabels(
    geometry: com.example.calcstudiopro.functions.graph.render.FunctionGraphGeometry,
    measurer: androidx.compose.ui.text.TextMeasurer,
    style: TextStyle,
    canvasWidth: Float,
    density: Float
): List<CachedTickLabel> {
    val measured = geometry.xTicks.map { tick -> tick to measurer.measure(AnnotatedString(tick.label), style) }
    val placed = ArrayList<CachedTickLabel>()
    var rightEdge = Float.NEGATIVE_INFINITY
    for ((tick, layout) in measured) {
        val center = tick.position.x.toFloat()
        val x = (center - layout.size.width / 2f).coerceIn(0f, max(0f, canvasWidth - layout.size.width))
        if (x >= rightEdge + 3f * density || placed.isEmpty()) {
            placed += CachedTickLabel(x, (geometry.plot.top + geometry.plot.height + 5.0 * density).toFloat(), layout)
            rightEdge = x + layout.size.width
        }
    }
    return placed
}

private fun placeYTickLabels(
    geometry: com.example.calcstudiopro.functions.graph.render.FunctionGraphGeometry,
    measurer: androidx.compose.ui.text.TextMeasurer,
    style: TextStyle,
    density: Float
): List<CachedTickLabel> {
    val measured = geometry.yTicks
        .sortedBy { it.position.y }
        .map { tick -> tick to measurer.measure(AnnotatedString(tick.label), style) }
    val placed = ArrayList<CachedTickLabel>()
    var bottomEdge = Float.NEGATIVE_INFINITY
    for ((tick, layout) in measured) {
        val y = (tick.position.y.toFloat() - layout.size.height / 2f).coerceAtLeast(0f)
        if (y >= bottomEdge + 2f * density || placed.isEmpty()) {
            placed += CachedTickLabel(
                (geometry.plot.left - layout.size.width - 6.0 * density).toFloat(), y, layout
            )
            bottomEdge = y + layout.size.height
        }
    }
    return placed
}
