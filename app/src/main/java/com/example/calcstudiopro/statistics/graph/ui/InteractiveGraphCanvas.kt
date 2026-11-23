package com.example.calcstudiopro.statistics.graph.ui

import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Dp
import com.example.calcstudiopro.statistics.graph.domain.GraphHitTesting
import com.example.calcstudiopro.statistics.graph.domain.GraphNumberFormatter
import com.example.calcstudiopro.statistics.graph.domain.GraphTransform
import com.example.calcstudiopro.statistics.graph.domain.panByPixels
import com.example.calcstudiopro.statistics.graph.domain.zoomAt
import com.example.calcstudiopro.statistics.graph.model.GraphSelection
import com.example.calcstudiopro.statistics.graph.model.GraphSelectionDetails
import com.example.calcstudiopro.statistics.graph.model.GraphSnapshot
import com.example.calcstudiopro.statistics.graph.model.GraphViewport
import com.example.calcstudiopro.statistics.graph.model.PointHit
import com.example.calcstudiopro.statistics.graph.model.ScreenPoint
import com.example.calcstudiopro.statistics.graph.render.GraphRenderStyle
import com.example.calcstudiopro.statistics.graph.render.drawGraph
import com.example.calcstudiopro.statistics.graph.render.plotRectForCanvas

private data class TapCycle(val signature: List<Pair<String, String>>, val index: Int, val x: Float, val y: Float, val time: Long)

@Composable
fun InteractiveGraphCanvas(
    snapshot: GraphSnapshot,
    viewport: GraphViewport,
    selection: GraphSelectionDetails?,
    onViewportChange: (GraphViewport) -> Unit,
    onSelectionChange: (GraphSelectionDetails?) -> Unit,
    onCycleSelection: () -> Unit,
    onResetViewport: () -> Unit,
    modifier: Modifier = Modifier,
    testTag: String = "interactive-graph-canvas",
    canvasHeight: Dp = 300.dp,
    showDetailsPanel: Boolean = true
) {
    var tapCycle by remember { mutableStateOf<TapCycle?>(null) }
    val density = LocalDensity.current.density
    val textMeasurer = rememberTextMeasurer()
    val colors = MaterialTheme.colorScheme
    val renderStyle = GraphRenderStyle(
        axisColor = colors.onSurface,
        gridColor = colors.outlineVariant.copy(alpha = 0.55f),
        pointColors = listOf(colors.primary, colors.tertiary, colors.secondary, colors.error),
        selectedColor = colors.error,
        labelColor = colors.onSurfaceVariant
    )
    val currentViewport = rememberUpdatedState(viewport)
    val currentSnapshot = rememberUpdatedState(snapshot)
    val changeViewport = rememberUpdatedState(onViewportChange)
    val changeSelection = rememberUpdatedState(onSelectionChange)
    val cycleSelection = rememberUpdatedState(onCycleSelection)
    val resetViewport = rememberUpdatedState(onResetViewport)
    val selectedPoint = selection?.selection?.point
    val hasSemanticDetails = selectedPoint?.details?.isNotEmpty() == true
    val exactSelection = selection?.let { details ->
        val coordinates = if (hasSemanticDetails) "" else "X = ${GraphNumberFormatter.format(details.selection.point.x)} · Y = ${GraphNumberFormatter.format(details.selection.point.y)}"
        val group = if (details.hitGroupSize > 1) "${details.positionInHitGroup} sur ${details.hitGroupSize} éléments superposés" else ""
        listOf(coordinates, group).filter(String::isNotBlank).joinToString(" · ")
    }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Surface(color = colors.surfaceVariant, shape = MaterialTheme.shapes.small) {
            Canvas(
                modifier = (if (canvasHeight == Dp.Unspecified) Modifier.fillMaxSize() else Modifier.fillMaxWidth().height(canvasHeight))
                    .testTag(testTag)
                    .semantics {
                        contentDescription = "${snapshot.title}. Graphique interactif. Pincez pour zoomer, glissez pour déplacer, touchez un élément pour afficher ses valeurs."
                        stateDescription = exactSelection ?: "Aucun point sélectionné."
                        customActions = listOf(
                            CustomAccessibilityAction("Sélectionner l’élément suivant") { cycleSelection.value(); true },
                            CustomAccessibilityAction("Réinitialiser le cadrage") { resetViewport.value(); true }
                        )
                    }
                    .pointerInput(snapshot, density) {
                        detectTransformGestures { centroid, pan, zoom, _ ->
                            val plot = plotRectForCanvas(size.width.toDouble(), size.height.toDouble(), density.toDouble())
                            val transform = GraphTransform(currentViewport.value, plot)
                            val focus = transform.screenToData(ScreenPoint(centroid.x.toDouble(), centroid.y.toDouble()))
                            changeViewport.value(currentViewport.value.zoomAt(focus, zoom.toDouble()).panByPixels(pan.x.toDouble(), pan.y.toDouble(), plot))
                        }
                    }
                    .pointerInput(snapshot, density) {
                        detectTapGestures(
                            onDoubleTap = { resetViewport.value() },
                            onTap = { position ->
                                val plot = plotRectForCanvas(size.width.toDouble(), size.height.toDouble(), density.toDouble())
                                val hits = GraphHitTesting.pointsNear(
                                    currentSnapshot.value,
                                    currentViewport.value,
                                    plot,
                                    ScreenPoint(position.x.toDouble(), position.y.toDouble()),
                                    28.0 * density
                                )
                                if (hits.isEmpty()) changeSelection.value(null)
                                else {
                                    val now = SystemClock.uptimeMillis()
                                    val signature = hits.map { it.seriesId to it.point.id }
                                    val previous = tapCycle
                                    val sameGroup = previous?.signature == signature && now - previous.time < 900L &&
                                        kotlin.math.hypot((position.x - previous.x).toDouble(), (position.y - previous.y).toDouble()) < 28.0 * density
                                    val index = if (sameGroup) (previous!!.index + 1) % hits.size else 0
                                    tapCycle = TapCycle(signature, index, position.x, position.y, now)
                                    val hit = hits[index]
                                    changeSelection.value(GraphSelectionDetails(GraphSelection(hit.seriesId, hit.point), index + 1, hits.size))
                                }
                            }
                        )
                    }
            ) {
                drawGraph(snapshot, viewport, selection?.selection, renderStyle, textMeasurer)
            }
        }
        if (showDetailsPanel && selection == null) Text("Touchez un élément pour afficher ses détails.", Modifier.fillMaxWidth().testTag("graph-selected-coordinates"),
            style = MaterialTheme.typography.bodyMedium, fontSize = 14.sp)
        else if (showDetailsPanel && selection != null) Surface(color = colors.surfaceContainerLow, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth().testTag("graph-selected-coordinates").semantics(mergeDescendants = true) {}) {
            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(exactSelection?.takeIf(String::isNotBlank) ?: "Élément sélectionné", style = MaterialTheme.typography.titleSmall)
                if (hasSemanticDetails) {
                    HorizontalDivider()
                    selectedPoint!!.details.forEach { detail ->
                        Text("${detail.label} : ${GraphNumberFormatter.format(detail.value, detail.wholeNumber, detail.percentage)}",
                            style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}
