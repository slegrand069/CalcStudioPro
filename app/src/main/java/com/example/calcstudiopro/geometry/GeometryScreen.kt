package com.example.calcstudiopro.geometry

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.max
import kotlin.math.min

@Composable
fun GeometryRoute(viewModel: GeometryViewModel, onOpenModeSelector: () -> Unit) {
    val state by viewModel.state.collectAsState()
    GeometryScreen(state, onOpenModeSelector, viewModel::editA, viewModel::editB, viewModel::editC, viewModel::save)
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun GeometryScreen(
    state: GeometryState, onOpenModeSelector: () -> Unit,
    onEditA: (String) -> Unit, onEditB: (String) -> Unit, onEditC: (String) -> Unit, onSave: () -> Unit
) {
    val r = state.result
    val rows = r?.let { listOf(
        "Périmètre" to TriangleEngine.format(it.perimeter), "Aire" to "${TriangleEngine.format(it.area)} unités²",
        "Angle A" to "${TriangleEngine.format(it.angleA)}°", "Angle B" to "${TriangleEngine.format(it.angleB)}°",
        "Angle C" to "${TriangleEngine.format(it.angleC)}°", "Hauteur sur a" to TriangleEngine.format(it.heightA),
        "Hauteur sur b" to TriangleEngine.format(it.heightB), "Hauteur sur c" to TriangleEngine.format(it.heightC),
        "Nature" to it.nature
    ) }.orEmpty()
    Scaffold(topBar = { TopAppBar(title = { Text("Géométrie — Triangle") }, actions = {
        TextButton(onClick = onOpenModeSelector, modifier = Modifier.testTag("geometry-mode")) { Text("MODE") }
    }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                listOf(Triple("a = BC", state.aText, onEditA), Triple("b = AC", state.bText, onEditB), Triple("c = AB", state.cText, onEditC)).forEach { (label, value, callback) ->
                    OutlinedTextField(value, callback, label = { Text(label) }, singleLine = true,
                        modifier = Modifier.weight(1f).testTag("geometry-side-${label.first()}") )
                }
            }
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("geometry-error")) }
            if (r != null) {
                TriangleCanvas(r, Modifier.fillMaxWidth().height(220.dp).testTag("geometry-triangle"))
                LazyVerticalGrid(columns = GridCells.Adaptive(minSize = 145.dp), modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(rows) { (label, value) -> Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(horizontal = 10.dp, vertical = 7.dp)) {
                            Text(label, style = MaterialTheme.typography.labelMedium)
                            Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        }
                    } }
                }
            } else {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { Text("Saisissez trois côtés valides pour afficher le triangle.") }
            }
            state.saveError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            if (state.saved) Text("Triangle enregistré dans l’historique.", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
            Button(onClick = onSave, enabled = state.canSave, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp).testTag("geometry-save")) {
                Text("Enregistrer dans l’historique")
            }
        }
    }
}

/** Scales supplied model coordinates uniformly and draws the triangle and its labels with Compose Canvas. */
@Composable
fun TriangleCanvas(result: TriangleEngine.Result, modifier: Modifier = Modifier) {
    val primary = MaterialTheme.colorScheme.primary
    val onSurface = MaterialTheme.colorScheme.onSurface
    val surfaceVariant = MaterialTheme.colorScheme.secondary
    val surface = MaterialTheme.colorScheme.surface
    Canvas(modifier) {
        val points = listOf(result.pointA, result.pointB, result.pointC)
        val minX = points.minOf { it.x }; val maxX = points.maxOf { it.x }
        val minY = points.minOf { it.y }; val maxY = points.maxOf { it.y }
        val padX = size.width * .18f; val padY = size.height * .22f
        val scale = min((size.width - 2 * padX) / (maxX - minX).toFloat(), (size.height - 2 * padY) / max(maxY - minY, 1e-9).toFloat())
        val usedW = (maxX - minX).toFloat() * scale; val usedH = (maxY - minY).toFloat() * scale
        val ox = (size.width - usedW) / 2f; val oy = (size.height + usedH) / 2f
        fun pos(p: TriangleEngine.Point) = Offset(ox + (p.x - minX).toFloat() * scale, oy - (p.y - minY).toFloat() * scale)
        val a = pos(result.pointA); val b = pos(result.pointB); val c = pos(result.pointC)
        drawLine(primary, a, b, 3.dp.toPx(), cap = StrokeCap.Round)
        drawLine(primary, b, c, 3.dp.toPx(), cap = StrokeCap.Round)
        drawLine(primary, c, a, 3.dp.toPx(), cap = StrokeCap.Round)
        listOf(a, b, c).forEach { drawCircle(primary, 4.dp.toPx(), it) }
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 13.sp.toPx(); color = onSurface.toArgb(); textAlign = android.graphics.Paint.Align.CENTER
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        }
        val labelPaint = android.graphics.Paint(paint).apply {
            color = surfaceVariant.toArgb(); textSize = 12.sp.toPx()
            setShadowLayer(2.dp.toPx(), 0f, 0f, surface.toArgb())
        }
        val anglePaint = android.graphics.Paint(labelPaint).apply { color = Color(0xFFFFB74D).toArgb() }
        drawIntoCanvas { canvas ->
            val native = canvas.nativeCanvas
            fun text(value: String, point: Offset, dy: Float = 0f, p: android.graphics.Paint = paint) { native.drawText(value, point.x, point.y + dy, p) }
            text("A", a, -12.dp.toPx()); text("B", b, -12.dp.toPx()); text("C", c, 18.dp.toPx())
            text("c = ${TriangleEngine.format(result.c)}", Offset((a.x + b.x) / 2, a.y), 18.dp.toPx(), labelPaint)
            text("a = ${TriangleEngine.format(result.a)}", Offset((b.x + c.x) / 2 + 12.dp.toPx(), (b.y + c.y) / 2), -7.dp.toPx(), labelPaint)
            text("b = ${TriangleEngine.format(result.b)}", Offset((a.x + c.x) / 2 - 12.dp.toPx(), (a.y + c.y) / 2), -7.dp.toPx(), labelPaint)
            text("${TriangleEngine.formatCanvasAngle(result.angleA)}°", Offset(a.x + 4.dp.toPx(), a.y + 18.dp.toPx()), 0f, anglePaint)
            text("${TriangleEngine.formatCanvasAngle(result.angleB)}°", Offset(b.x - 4.dp.toPx(), b.y + 18.dp.toPx()), 0f, anglePaint)
            text("${TriangleEngine.formatCanvasAngle(result.angleC)}°", Offset(c.x, c.y - 14.dp.toPx()), 0f, anglePaint)
        }
    }
}
