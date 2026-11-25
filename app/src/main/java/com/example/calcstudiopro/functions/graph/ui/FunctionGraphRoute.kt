package com.example.calcstudiopro.functions.graph.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.calcstudiopro.domain.functions.FunctionAngleMode
import com.example.calcstudiopro.functions.FunctionStudyViewModel
import com.example.calcstudiopro.functions.domain.FunctionStudy
import com.example.calcstudiopro.functions.graph.FunctionGraphStudyAdapter
import com.example.calcstudiopro.functions.graph.FunctionGraphViewportMemory
import com.example.calcstudiopro.functions.graph.interaction.FunctionGraphCurveHitTester
import com.example.calcstudiopro.functions.graph.interaction.FunctionGraphFitResult
import com.example.calcstudiopro.functions.graph.interaction.FunctionGraphOrthonormalizeResult
import com.example.calcstudiopro.functions.graph.interaction.FunctionGraphSelectionContext
import com.example.calcstudiopro.functions.graph.interaction.FunctionGraphViewportController
import com.example.calcstudiopro.functions.graph.interaction.FunctionGraphViewportFitter
import com.example.calcstudiopro.functions.graph.interaction.FunctionGraphViewportOrthonormalizer
import com.example.calcstudiopro.functions.graph.interaction.FunctionGraphMarkerState
import com.example.calcstudiopro.functions.graph.interaction.FunctionGraphMarkerEvaluation as MarkerEvaluation
import com.example.calcstudiopro.functions.graph.interaction.functionGraphGestures
import com.example.calcstudiopro.functions.graph.render.FunctionGridMode
import com.example.calcstudiopro.functions.graph.render.FunctionGraphRenderModel
import com.example.calcstudiopro.functions.graph.FunctionGraphStatus
import com.example.calcstudiopro.statistics.graph.domain.GraphNumberFormatter
import com.example.calcstudiopro.statistics.graph.model.DataPoint
import com.example.calcstudiopro.statistics.graph.model.GraphViewport
import com.example.calcstudiopro.statistics.graph.domain.GraphTransform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun FunctionGraphRoute(
    studyViewModel: FunctionStudyViewModel,
    onBack: () -> Unit,
    viewportMemory: FunctionGraphViewportMemory = viewModel()
) {
    val state by studyViewModel.state.collectAsState()
    val study = state.study
    if (study == null) {
        Scaffold(topBar = { TopAppBar(title = { Text("Graphe") }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour") }
        }) }) { padding -> Text(state.loadError ?: "Aucune étude active", Modifier.padding(padding).padding(20.dp)) }
    } else {
        FunctionGraphProductionScreen(study, studyViewModel, viewportMemory, onBack)
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun FunctionGraphProductionScreen(
    study: FunctionStudy,
    studyViewModel: FunctionStudyViewModel,
    viewportMemory: FunctionGraphViewportMemory,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var showParameterPanel by remember { mutableStateOf(false) }
    var showFunctionPanel by remember { mutableStateOf(false) }
    val actions = remember { ProductionGraphActions() }
    val context = LocalContext.current
    val activity = remember(context) { context.findGraphActivity() }
    val orientation = LocalConfiguration.current.orientation
    DisposableEffect(activity) {
        onDispose {
            if (activity != null && !activity.isChangingConfigurations) {
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Graphe", maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("function-graph-back")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                },
                actions = {
                    IconButton(onClick = { showFunctionPanel = !showFunctionPanel }, modifier = Modifier.testTag("function-graph-functions-panel")) {
                        Icon(Icons.AutoMirrored.Filled.FormatListBulleted, contentDescription = "Fonctions")
                    }
                    IconButton(onClick = { showParameterPanel = !showParameterPanel }, modifier = Modifier.testTag("function-graph-parameters-panel")) {
                        Icon(Icons.Default.Tune, contentDescription = "Paramètres")
                    }
                    TextButton(
                        onClick = { studyViewModel.setAngleMode(if (study.angleMode == FunctionAngleMode.RAD) FunctionAngleMode.DEG else FunctionAngleMode.RAD) },
                        modifier = Modifier.testTag("function-graph-angle-toggle")
                    ) { Text(study.angleMode.name, maxLines = 1) }
                    IconButton(onClick = {
                        activity?.requestedOrientation = if (orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) {
                            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                        } else ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                    }, modifier = Modifier.testTag("function-graph-orientation")) {
                        Icon(Icons.Default.ScreenRotation, contentDescription = if (orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) "Passer en portrait" else "Passer en paysage")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { insets ->
        Column(Modifier.fillMaxSize().padding(insets).padding(horizontal = 8.dp, vertical = 4.dp)) {
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                val density = LocalDensity.current
                val widthPx = with(density) { maxWidth.toPx() }
                val heightPx = with(density) { maxHeight.toPx() }
                val plot = remember(widthPx, heightPx, density.density) {
                    functionPlotRectForCanvas(widthPx, heightPx, density.density.toDouble())
                }
                val session = remember(viewportMemory, study.id) {
                    viewportMemory.session(
                        study,
                        plot.width.toInt().coerceAtLeast(1),
                        plot.height.toInt().coerceAtLeast(1),
                        FunctionGraphCurveHitTester.radiusPixels(density = density.density.toDouble())
                    )
                }
                val adapted = session.updateStudy(study)
                val contentRevision = adapted.snapshot.revision
                val coordinator = session.coordinator
                val selection = session.selection
                val evaluationCoordinator = session.evaluationCoordinator
                val graphState by coordinator.state.collectAsState()
                val selectionState by selection.state.collectAsState()
                var fitIntent by remember(coordinator) { mutableStateOf<ProductionFitIntent?>(null) }
                var fitId by remember(coordinator) { mutableStateOf(0L) }

                LaunchedEffect(session, contentRevision) {
                    fitIntent = null
                    evaluationCoordinator.invalidate()
                    val selected = selection.state.value.marker
                    selection.onContentChanged(
                        contentRevision,
                        selected?.let { study.slot(it.slotId).validatedExpression != null } ?: false
                    )
                    coordinator.updateContent(adapted.snapshot)
                }
                val visibilityKey = adapted.snapshot.functions.map { it.id to it.isVisible }
                LaunchedEffect(session, visibilityKey) {
                    // Visibility is presentation state (same math revision), but newly shown slots need sampling.
                    coordinator.updateContent(adapted.snapshot)
                }
                LaunchedEffect(plot.width, plot.height) {
                    fitIntent = null
                    selection.invalidateCycle()
                    coordinator.updatePlotSize(plot.width.toInt().coerceAtLeast(1), plot.height.toInt().coerceAtLeast(1))
                }
                val model = remember(study, contentRevision, graphState.available) {
                    FunctionGraphStudyAdapter.renderModel(study, contentRevision, graphState.available)
                }
                val samplingNotice = remember(model) { functionGraphSamplingNotice(model) }
                val latestModel = rememberUpdatedState(model)
                LaunchedEffect(model, contentRevision) {
                    selection.onRenderModelChanged(model, contentRevision)
                }
                val curveViewport = graphState.available?.request?.let { GraphViewport(it.xRange, it.yRange) }
                    ?: graphState.displayedViewport

                fun requestMarkerEvaluation(marker: FunctionGraphMarkerState, final: Boolean) {
                    val accepted = coordinator.state.value.available ?: return
                    if (accepted.contentRevision != marker.contentRevision) return
                    val input = accepted.request.functions.firstOrNull { it.id == marker.slotId } ?: return
                    val expression = input.expression ?: return
                    val id = evaluationCoordinator.submit(
                        marker.slotId, marker.x, accepted.contentRevision, expression,
                        accepted.request.parameters, accepted.request.angleMode, isFinal = final
                    )
                    selection.setEvaluationRequest(marker, id)
                }
                LaunchedEffect(graphState.available?.batch?.generation, contentRevision) {
                    val marker = selection.state.value.marker ?: return@LaunchedEffect
                    if (marker.contentRevision == contentRevision && marker.evaluation in setOf(MarkerEvaluation.Provisional, MarkerEvaluation.Pending)) {
                        requestMarkerEvaluation(marker, final = true)
                    }
                }
                fun context(): FunctionGraphSelectionContext {
                    val latest = coordinator.state.value
                    return FunctionGraphSelectionContext(
                        session.contentRevision,
                        latest.available?.batch?.generation,
                        latest.displayedViewportGeneration
                    )
                }

                val markerPresentation = selectionState.marker?.let { marker ->
                    val visible = latestModel.value.curves.firstOrNull { it.id == marker.slotId }?.isVisible == true
                    val y = marker.displayedY
                    if (!visible || y == null) null else {
                        val provisional = marker.evaluation !is MarkerEvaluation.Confirmed
                        val status = when (marker.evaluation) {
                            is MarkerEvaluation.Error, MarkerEvaluation.Undefined -> " • non défini"
                            MarkerEvaluation.Provisional, MarkerEvaluation.Pending -> " • provisoire"
                            is MarkerEvaluation.Confirmed -> ""
                        }
                        FunctionGraphMarkerPresentation(
                            marker.slotId, marker.x, y,
                            "${marker.slotId.code}$status\nx = ${GraphNumberFormatter.format(marker.x)}\ny ${if (provisional) "≈" else "="} ${GraphNumberFormatter.format(y)}"
                        )
                    }
                }

                fun requestFit() {
                    val current = coordinator.state.value
                    fitId++
                    fitIntent = ProductionFitIntent(fitId, contentRevision, current.displayedViewport, plot.width.toInt(), plot.height.toInt())
                    scope.launch { snackbar.showSnackbar("Ajustement en attente") }
                    coordinator.updateDisplayedViewport(current.displayedViewport)
                }
                fun orthonormalize() {
                    fitIntent = null
                    when (val result = FunctionGraphViewportOrthonormalizer.orthonormalize(coordinator.state.value.displayedViewport, plot)) {
                        is FunctionGraphOrthonormalizeResult.Applied -> {
                            selection.invalidateCycle()
                            viewportMemory.save(study.id, result.viewport)
                            coordinator.updateDisplayedViewport(result.viewport)
                            scope.launch { snackbar.showSnackbar(if (result.alreadyOrthonormal) "Déjà orthonormé" else "Orthonormé") }
                        }
                        FunctionGraphOrthonormalizeResult.Rejected -> scope.launch { snackbar.showSnackbar("Orthonormalisation refusée : limite numérique") }
                    }
                }
                fun resetViewport() {
                    fitIntent = null
                    selection.invalidateCycle()
                    evaluationCoordinator.invalidate()
                    selection.clear()
                    val initial = FunctionGraphViewportController.reset()
                    viewportMemory.save(study.id, initial)
                    coordinator.resetViewport()
                    scope.launch { snackbar.showSnackbar("Cadrage réinitialisé") }
                }
                actions.fit = { requestFit() }
                actions.orthonormalize = { orthonormalize() }
                actions.reset = { resetViewport() }

                LaunchedEffect(fitIntent?.id, graphState.available?.batch?.generation) {
                    val intent = fitIntent ?: return@LaunchedEffect
                    val accepted = coordinator.state.value.available ?: return@LaunchedEffect
                    if (accepted.contentRevision != intent.revision ||
                        accepted.request.plotWidthPixels != intent.width || accepted.request.plotHeightPixels != intent.height ||
                        accepted.request.xRange.minimum > intent.viewport.x.minimum || accepted.request.xRange.maximum < intent.viewport.x.maximum
                    ) return@LaunchedEffect
                    val fit = withContext(Dispatchers.Default) {
                        FunctionGraphViewportFitter.fit(intent.viewport, accepted, model, intent.revision, intent.height)
                    }
                    if (fitIntent?.id != intent.id || coordinator.state.value.displayedViewport != intent.viewport ||
                        session.contentRevision != intent.revision || plot.width.toInt() != intent.width || plot.height.toInt() != intent.height
                    ) return@LaunchedEffect
                    fitIntent = null
                    when (fit) {
                        is FunctionGraphFitResult.Applied -> {
                            selection.invalidateCycle()
                            viewportMemory.save(study.id, fit.viewport)
                            coordinator.updateDisplayedViewport(fit.viewport)
                            snackbar.showSnackbar(if (fit.usedPartialData) "Ajustement sur portions définies" else "Ajustement appliqué")
                        }
                        is FunctionGraphFitResult.Rejected -> snackbar.showSnackbar(
                            if (fit.reason == FunctionGraphFitResult.Reason.NO_DEFINED_PORTION) "Aucune portion définie à ajuster"
                            else "Ajustement refusé : limite numérique"
                        )
                    }
                }

                FunctionGraphCanvas(
                    model = model,
                    viewport = graphState.displayedViewport,
                    curveViewport = curveViewport,
                    gridMode = FunctionGridMode.MAJOR_AND_MINOR,
                    marker = markerPresentation,
                    modifier = Modifier.fillMaxSize().testTag("function-graph-production").functionGraphGestures(
                        viewport = { coordinator.state.value.displayedViewport },
                        plot = plot,
                        onViewportChange = { viewport ->
                            fitIntent = null
                            viewportMemory.save(study.id, viewport)
                            coordinator.updateDisplayedViewport(viewport)
                            selection.invalidateCycle()
                        },
                        onGestureStart = { fitIntent = null; selection.commitPendingTap(); selection.invalidateCycle(); coordinator.beginGesture() },
                        onGestureEnd = coordinator::endGesture,
                        onTap = { touch ->
                            val accepted = coordinator.state.value.available ?: return@functionGraphGestures
                            if (accepted.contentRevision != session.contentRevision) return@functionGraphGestures
                            val source = GraphViewport(accepted.request.xRange, accepted.request.yRange)
                            val candidates = FunctionGraphCurveHitTester.candidatesNear(
                            latestModel.value, source, coordinator.state.value.displayedViewport, plot, plot, touch,
                                FunctionGraphCurveHitTester.radiusPixels(density = density.density.toDouble())
                            )
                            selection.select(candidates, touch, context(), android.os.SystemClock.uptimeMillis())
                                ?.let { requestMarkerEvaluation(it, false) }
                        },
                        onDoubleTap = {
                            fitIntent = null
                            evaluationCoordinator.invalidate()
                            selection.rollbackPendingTapForDoubleTap()
                            selection.invalidateCycle()
                            val reset = FunctionGraphViewportController.reset()
                            viewportMemory.save(study.id, reset)
                            coordinator.resetViewport()
                        },
                        isNearMarker = { point ->
                            val marker = selection.state.value.marker ?: return@functionGraphGestures false
                            val y = marker.displayedY ?: return@functionGraphGestures false
                            if (latestModel.value.curves.firstOrNull { it.id == marker.slotId }?.isVisible != true) return@functionGraphGestures false
                            val screen = GraphTransform(coordinator.state.value.displayedViewport, plot)
                                .dataToScreen(DataPoint(marker.x, y))
                            hypot(screen.x - point.x, screen.y - point.y) <= 36.0 * density.density
                        },
                        onMarkerDrag = { point ->
                            val marker = selection.state.value.marker ?: return@functionGraphGestures
                            val index = marker.segmentIndex ?: return@functionGraphGestures
                            val hit = com.example.calcstudiopro.functions.graph.interaction.FunctionGraphMarkerDrag.hitOnSelectedSegment(
                                latestModel.value, marker.slotId, index, point, coordinator.state.value.displayedViewport, plot
                            ) ?: return@functionGraphGestures
                            selection.moveMarker(hit, context())?.let { requestMarkerEvaluation(it, false) }
                        },
                        onMarkerDragEnd = { selection.state.value.marker?.let { requestMarkerEvaluation(it, true) } }
                    ),
                    viewportGeneration = graphState.displayedViewportGeneration,
                    testTag = "function-graph-production-canvas"
                )
                if (graphState.calculationActive || graphState.available?.contentRevision != contentRevision) {
                    Surface(
                        modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp).testTag("function-graph-loading"),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                        shape = MaterialTheme.shapes.small
                    ) { Text("Calcul des fonctions…", Modifier.padding(horizontal = 12.dp, vertical = 6.dp), style = MaterialTheme.typography.labelMedium) }
                }
                samplingNotice?.let { notice ->
                    Surface(
                        modifier = Modifier.align(Alignment.TopStart).padding(8.dp).testTag("function-graph-result-status"),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                        shape = MaterialTheme.shapes.small
                    ) { Text(notice, Modifier.padding(horizontal = 10.dp, vertical = 6.dp), style = MaterialTheme.typography.labelSmall) }
                }
                if (showFunctionPanel) {
                    FunctionGraphFunctionsPanel(
                        study = study,
                        onToggleVisibility = studyViewModel::toggleVisibility,
                        onDismiss = { showFunctionPanel = false },
                        modifier = Modifier.align(Alignment.CenterStart).fillMaxHeight().fillMaxWidth(0.82f).widthIn(max = 340.dp)
                    )
                }
                if (showParameterPanel) {
                    FunctionGraphParametersPanel(
                        study = study,
                        onParameterChange = studyViewModel::setGraphParameter,
                        onParameterChangeFinished = studyViewModel::commitGraphParameterChanges,
                        onDismiss = {
                            studyViewModel.commitGraphParameterChanges()
                            showParameterPanel = false
                        },
                        modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().imePadding(),
                        availableHeight = maxHeight
                    )
                }
            }
            Row(Modifier.fillMaxWidth().height(52.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedButton(onClick = {
                    actions.fit?.invoke()
                }, modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("function-graph-fit"), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp)) {
                    Text("Ajuster", Modifier.testTag("function-graph-label-fit"), maxLines = 1, softWrap = false, style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp))
                }
                OutlinedButton(onClick = { actions.orthonormalize?.invoke() }, modifier = Modifier.weight(1.25f).heightIn(min = 48.dp).testTag("function-graph-orthonormalize"), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp)) {
                    Text("Orthonormer", Modifier.testTag("function-graph-label-orthonormalize"), maxLines = 1, softWrap = false, style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp))
                }
                OutlinedButton(onClick = { actions.reset?.invoke() }, modifier = Modifier.weight(1.35f).heightIn(min = 48.dp).testTag("function-graph-reset"), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp)) {
                    Text("Réinitialiser", Modifier.testTag("function-graph-label-reset"), maxLines = 1, softWrap = false, style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp))
                }
            }
        }
    }
}

private data class ProductionFitIntent(val id: Long, val revision: Long, val viewport: GraphViewport, val width: Int, val height: Int)

private class ProductionGraphActions {
    var fit: (() -> Unit)? = null
    var orthonormalize: (() -> Unit)? = null
    var reset: (() -> Unit)? = null
}

@Composable
private fun FunctionGraphFunctionsPanel(
    study: FunctionStudy,
    onToggleVisibility: (com.example.calcstudiopro.functions.domain.FunctionSlotId) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(modifier = modifier.testTag("function-graph-functions-panel-content"), color = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f), shape = RoundedCornerShape(topEnd = 18.dp, bottomEnd = 18.dp), shadowElevation = 8.dp) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Fonctions (6)", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                IconButton(onClick = onDismiss, modifier = Modifier.testTag("function-graph-functions-close")) {
                    Icon(Icons.Default.Close, contentDescription = "Fermer les fonctions")
                }
            }
            study.slots.forEach { slot ->
                Row(Modifier.fillMaxWidth().testTag("function-graph-slot-${slot.id.code}"), verticalAlignment = Alignment.CenterVertically) {
                    Surface(Modifier.width(18.dp).height(18.dp).testTag("function-graph-color-${slot.id.code}"), color = Color(slot.color.argb), shape = RoundedCornerShape(4.dp)) {}
                    Text(slot.id.code, Modifier.padding(horizontal = 8.dp), style = MaterialTheme.typography.labelLarge)
                    Text(slot.validatedExpression ?: "(vide)", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    IconButton(onClick = { onToggleVisibility(slot.id) }, modifier = Modifier.testTag("function-graph-visible-${slot.id.code}")) {
                        Icon(
                            if (slot.isVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = if (slot.isVisible) "Masquer ${slot.id.code}" else "Afficher ${slot.id.code}"
                        )
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

@Composable
internal fun FunctionGraphParametersPanel(
    study: FunctionStudy,
    onParameterChange: (Char, Double) -> Unit,
    onParameterChangeFinished: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier,
    availableHeight: androidx.compose.ui.unit.Dp
) {
    val density = LocalDensity.current
    val availablePx = with(density) { availableHeight.toPx() }.coerceAtLeast(1f)
    val minimumPx = with(density) { 66.dp.toPx() }
    val maximumPx = (availablePx * 0.86f).coerceAtLeast(minimumPx)
    var panelHeightPx by remember(study.id) { mutableFloatStateOf(availablePx * 0.48f) }
    LaunchedEffect(availablePx) { panelHeightPx = panelHeightPx.coerceIn(minimumPx, maximumPx) }
    val names = remember(study.usedParameters, study.parameters.values) { (study.usedParameters + study.parameters.values.keys).toSortedSet() }
    val dragging = rememberDraggableState { delta -> panelHeightPx = (panelHeightPx - delta).coerceIn(minimumPx, maximumPx) }
    Surface(
        modifier = modifier.height(with(density) { panelHeightPx.toDp() }).testTag("function-graph-parameter-panel"),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
        shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp),
        shadowElevation = 10.dp
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().height(64.dp).draggable(
                    state = dragging,
                    orientation = Orientation.Vertical,
                    onDragStopped = {
                        val anchors = listOf(minimumPx, availablePx * 0.48f, maximumPx)
                        panelHeightPx = anchors.minBy { abs(it - panelHeightPx) }
                    }
                ).testTag("function-graph-parameter-panel-handle"),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(Modifier.weight(1f))
                Column(Modifier.weight(4f).clickable {
                    val anchors = listOf(minimumPx, availablePx * 0.48f, maximumPx)
                    val next = anchors.firstOrNull { it > panelHeightPx + 1f } ?: anchors.first()
                    panelHeightPx = next
                }, horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(Modifier.width(36.dp).height(4.dp), color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f), shape = RoundedCornerShape(4.dp)) {}
                    if (panelHeightPx > minimumPx + with(density) { 16.dp.toPx() }) {
                        Text("Paramètres", style = MaterialTheme.typography.titleSmall)
                    }
                }
                IconButton(onClick = onDismiss, modifier = Modifier.weight(1f).testTag("function-graph-parameter-panel-close")) {
                    Icon(Icons.Default.Close, contentDescription = "Fermer les paramètres")
                }
            }
            if (panelHeightPx > minimumPx + with(density) { 24.dp.toPx() }) {
                Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (names.isEmpty()) Text("Aucun paramètre utilisé ou enregistré.", style = MaterialTheme.typography.bodyMedium)
                    names.forEach { name ->
                        FunctionGraphParameterControl(name, study.parameters.value(name), onParameterChange, onParameterChangeFinished)
                    }
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun FunctionGraphParameterControl(
    name: Char,
    value: Double,
    onValue: (Char, Double) -> Unit,
    onValueChangeFinished: () -> Unit
) {
    // Legacy values remain untouched until the user moves the slider. The thumb is
    // displayed at the nearest available integer, clamped to this control's range.
    val sliderValue = value.coerceIn(-20.0, 20.0).roundToInt()
    Column(Modifier.fillMaxWidth().testTag("graph-parameter-row-$name"), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("$name", Modifier.width(32.dp), style = MaterialTheme.typography.titleSmall)
            Column(Modifier.weight(1f)) {
                Slider(
                    value = sliderValue.toFloat(),
                    onValueChange = { selected ->
                        val integerValue = selected.roundToInt().coerceIn(-20, 20).toDouble()
                        if (integerValue != value) onValue(name, integerValue)
                    },
                    onValueChangeFinished = onValueChangeFinished,
                    valueRange = -20f..20f,
                    steps = 39,
                    track = { sliderState ->
                        SliderDefaults.Track(
                            sliderState = sliderState,
                            drawTick = { _, _ -> }
                        )
                    },
                    modifier = Modifier.fillMaxWidth().height(40.dp).testTag("graph-parameter-slider-$name")
                )
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("-20", Modifier.testTag("graph-parameter-min-$name"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("+20", Modifier.testTag("graph-parameter-max-$name"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Surface(
                modifier = Modifier.widthIn(min = 52.dp).padding(start = 8.dp).testTag("graph-parameter-value-$name"),
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(8.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Text(
                    formatParameterValue(value),
                    Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}

private fun formatParameterValue(value: Double): String =
    if (value.isFinite() && value >= Long.MIN_VALUE.toDouble() && value <= Long.MAX_VALUE.toDouble() && value % 1.0 == 0.0) {
        value.toLong().toString()
    } else value.toString()

internal fun functionGraphSamplingNotice(model: FunctionGraphRenderModel): String? {
    val notices = model.curves.asSequence().filter { it.isVisible }.mapNotNull { curve ->
        val status = curve.status ?: return@mapNotNull null
        when (status) {
            FunctionGraphStatus.Empty -> null
            FunctionGraphStatus.NoDefinedPortion -> "${curve.id.code} : aucune portion définie"
            is FunctionGraphStatus.Error -> "${curve.id.code} : erreur mathématique"
            is FunctionGraphStatus.BudgetExceeded, is FunctionGraphStatus.Partial -> "${curve.id.code} : tracé partiel"
            FunctionGraphStatus.Success -> if ((curve.diagnostics?.discontinuityBreakCount ?: 0) > 0) {
                "${curve.id.code} : domaine interrompu"
            } else null
        }
    }.take(2).toList()
    return notices.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}

private fun Context.findGraphActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findGraphActivity()
    else -> null
}
