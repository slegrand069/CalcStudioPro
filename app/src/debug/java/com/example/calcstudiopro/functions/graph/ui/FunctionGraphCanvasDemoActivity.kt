package com.example.calcstudiopro.functions.graph.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.calcstudiopro.functions.domain.FunctionColorPalette
import com.example.calcstudiopro.functions.domain.FunctionSlotId
import com.example.calcstudiopro.domain.functions.ExpressionCompilation
import com.example.calcstudiopro.domain.functions.ExpressionEvaluation
import com.example.calcstudiopro.domain.functions.FunctionAngleMode
import com.example.calcstudiopro.domain.functions.MathExpressionEngine
import com.example.calcstudiopro.domain.functions.MathExpressionError
import com.example.calcstudiopro.functions.graph.FunctionGraphInput
import com.example.calcstudiopro.functions.graph.FunctionGraphCalculator
import com.example.calcstudiopro.functions.graph.FunctionGraphStatus
import com.example.calcstudiopro.functions.graph.interaction.FunctionGraphCalculationCoordinator
import com.example.calcstudiopro.functions.graph.interaction.FunctionGraphCurveHitTester
import com.example.calcstudiopro.functions.graph.interaction.FunctionGraphContentSnapshot
import com.example.calcstudiopro.functions.graph.interaction.FunctionGraphViewportController
import com.example.calcstudiopro.functions.graph.interaction.FunctionGraphSelectionController
import com.example.calcstudiopro.functions.graph.interaction.FunctionGraphSelectionContext
import com.example.calcstudiopro.functions.graph.interaction.FunctionGraphMarkerEvaluation
import com.example.calcstudiopro.functions.graph.interaction.FunctionGraphPointEvaluationCoordinator
import com.example.calcstudiopro.functions.graph.interaction.FunctionGraphMarkerDrag
import com.example.calcstudiopro.functions.graph.render.FunctionCurveRenderModel
import com.example.calcstudiopro.functions.graph.render.FunctionGraphRenderModel
import com.example.calcstudiopro.functions.graph.render.FunctionGridMode
import com.example.calcstudiopro.statistics.graph.model.AxisRange
import com.example.calcstudiopro.statistics.graph.model.GraphViewport
import com.example.calcstudiopro.statistics.graph.model.ScreenPoint
import com.example.calcstudiopro.statistics.graph.domain.GraphNumberFormatter
import com.example.calcstudiopro.ui.theme.CalcStudioProTheme
import com.example.calcstudiopro.functions.graph.interaction.functionGraphGestures

/** Debug-only, explicit-ADB preview. It is not connected to app navigation. */
class FunctionGraphCanvasDemoActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val darkTheme = intent.getBooleanExtra(EXTRA_DARK_THEME, true)
        val darkBar = SystemBarStyle.dark(Color(0xFF111A2E).toArgb())
        val lightBar = SystemBarStyle.light(Color(0xFFFFFFFF).toArgb(), Color(0xFF17233B).toArgb())
        val barStyle = if (darkTheme) darkBar else lightBar
        enableEdgeToEdge(statusBarStyle = barStyle, navigationBarStyle = barStyle)
        setContent {
            CalcStudioProTheme(darkTheme = darkTheme) {
                FunctionGraphCanvasDemo(::returnFromDemo, ::toggleDemoOrientation)
            }
        }
    }

    private fun returnFromDemo() {
        if (isTaskRoot) finish() else onBackPressedDispatcher.onBackPressed()
    }

    private fun toggleDemoOrientation() {
        requestedOrientation = if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        } else {
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
    }

    companion object {
        const val EXTRA_DARK_THEME = "com.example.calcstudiopro.functions.graph.demo.DARK_THEME"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FunctionGraphCanvasDemo(onBack: () -> Unit = {}, onToggleOrientation: () -> Unit = {}) {
    val colors = MaterialTheme.colorScheme
    val orientation = LocalConfiguration.current.orientation
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = colors.surface,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Grapheur — Démo",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleMedium
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("function-graph-demo-back").semantics {
                        contentDescription = "Retour"
                    }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null) }
                },
                actions = {
                    IconButton(
                        onClick = onToggleOrientation,
                        modifier = Modifier.testTag("function-graph-demo-rotate").semantics {
                            contentDescription = if (orientation == Configuration.ORIENTATION_LANDSCAPE) "Passer en portrait" else "Passer en paysage"
                        }
                    ) { Icon(Icons.Default.ScreenRotation, contentDescription = null) }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colors.surface,
                    titleContentColor = colors.onSurface,
                    navigationIconContentColor = colors.onSurface,
                    actionIconContentColor = colors.onSurface
                )
            )
        }
    ) { contentInsets ->
        Column(
            Modifier.fillMaxSize().padding(contentInsets).padding(horizontal = 12.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                val density = LocalDensity.current
                val widthPx = with(density) { maxWidth.toPx() }
                val heightPx = with(density) { maxHeight.toPx() }
                val plot = remember(widthPx, heightPx, density.density) {
                    functionPlotRectForCanvas(widthPx, heightPx, density.density.toDouble())
                }
                val scope = rememberCoroutineScope()
                val selection = remember(density.density) {
                    FunctionGraphSelectionController(
                        FunctionGraphCurveHitTester.radiusPixels(density = density.density.toDouble())
                    )
                }
                val selectionState by selection.state.collectAsState()
                val evaluationCoordinator = remember(scope, selection) {
                    FunctionGraphPointEvaluationCoordinator(scope, onResult = { request, result ->
                        val evaluation = when (result) {
                            is ExpressionEvaluation.Success -> FunctionGraphMarkerEvaluation.Confirmed(result.value)
                            is ExpressionEvaluation.Failure -> if (result.error is MathExpressionError.NonFiniteResult)
                                FunctionGraphMarkerEvaluation.Undefined else FunctionGraphMarkerEvaluation.Error(result.error)
                        }
                        selection.applyEvaluation(request.requestId, request.slotId, request.x, request.contentRevision, evaluation)
                    })
                }
                val content = remember { FunctionGraphCanvasDemoData.content }
                val coordinator = remember(scope, content) {
                    FunctionGraphCalculationCoordinator(
                        scope = scope,
                        initialContent = content,
                        initialViewport = FunctionGraphViewportController.INITIAL,
                        initialPlotWidthPixels = plot.width.toInt().coerceAtLeast(1),
                        initialPlotHeightPixels = plot.height.toInt().coerceAtLeast(1),
                        calculator = FunctionGraphCalculator(scope)
                    )
                }
                DisposableEffect(coordinator, evaluationCoordinator) { onDispose { coordinator.close(); evaluationCoordinator.close() } }
                LaunchedEffect(coordinator, plot.width, plot.height) {
                    selection.invalidateCycle()
                    coordinator.updatePlotSize(plot.width.toInt().coerceAtLeast(1), plot.height.toInt().coerceAtLeast(1))
                }
                val state by coordinator.state.collectAsState()
                val displayed = rememberUpdatedState(state.displayedViewport)
                val model = remember(state.available, content) {
                    FunctionGraphCanvasDemoData.renderModel(state.available?.batch)
                }
                LaunchedEffect(model, state.available?.contentRevision) {
                    selection.onRenderModelChanged(model, state.available?.contentRevision ?: content.revision)
                }
                val curveViewport = state.available?.request?.let {
                    com.example.calcstudiopro.statistics.graph.model.GraphViewport(it.xRange, it.yRange)
                } ?: FunctionGraphViewportController.INITIAL
                val marker = selectionState.marker?.let { selected ->
                    val y = selected.displayedY
                    val visible = model.curves.firstOrNull { it.id == selected.slotId }?.isVisible == true
                    if (y == null || !visible) null else {
                        val approximate = selected.evaluation !is FunctionGraphMarkerEvaluation.Confirmed
                        val status = when (val evaluation = selected.evaluation) {
                            is FunctionGraphMarkerEvaluation.Error -> "non défini"
                            FunctionGraphMarkerEvaluation.Undefined -> "non défini"
                            FunctionGraphMarkerEvaluation.Provisional, FunctionGraphMarkerEvaluation.Pending -> if (approximate) "provisoire" else ""
                            is FunctionGraphMarkerEvaluation.Confirmed -> ""
                        }
                        FunctionGraphMarkerPresentation(
                            selected.slotId, selected.x, y,
                            "${selected.slotId.code}${if (status.isNotEmpty()) " • $status" else ""}\nx = ${GraphNumberFormatter.format(selected.x)}\ny ${if (approximate) "≈ " else "= "}${GraphNumberFormatter.format(y)}"
                        )
                    }
                }
                fun requestEvaluation(markerState: com.example.calcstudiopro.functions.graph.interaction.FunctionGraphMarkerState, final: Boolean) {
                    val accepted = coordinator.state.value.available ?: return
                    if (accepted.contentRevision != markerState.contentRevision) return
                    val input = accepted.request.functions.firstOrNull { it.id == markerState.slotId } ?: return
                    val expression = input.expression ?: return
                    val id = evaluationCoordinator.submit(
                        markerState.slotId, markerState.x, accepted.contentRevision, expression,
                        accepted.request.parameters, accepted.request.angleMode, isFinal = final
                    )
                    selection.setEvaluationRequest(markerState, id)
                }
                fun context() = FunctionGraphSelectionContext(
                    contentRevision = coordinator.state.value.available?.contentRevision ?: content.revision,
                    dataGeneration = coordinator.state.value.available?.batch?.generation,
                    viewportGeneration = coordinator.state.value.displayedViewportGeneration
                )
                FunctionGraphCanvas(
                    model = model,
                    viewport = state.displayedViewport,
                    curveViewport = curveViewport,
                    gridMode = FunctionGridMode.MAJOR_AND_MINOR,
                    marker = marker,
                    modifier = Modifier.fillMaxSize().functionGraphGestures(
                        viewport = { displayed.value },
                        plot = plot,
                        onViewportChange = { viewport ->
                            coordinator.updateDisplayedViewport(viewport)
                            selection.invalidateCycle()
                        },
                        onGestureStart = { selection.commitPendingTap(); selection.invalidateCycle(); coordinator.beginGesture() },
                        onGestureEnd = coordinator::endGesture,
                        onTap = { touch ->
                            selection.commitPendingTap()
                            val latest = coordinator.state.value
                            val available = latest.available ?: return@functionGraphGestures
                            val sourceViewport = GraphViewport(available.request.xRange, available.request.yRange)
                            val candidates = FunctionGraphCurveHitTester.candidatesNear(
                                model, sourceViewport, latest.displayedViewport, plot, plot, touch,
                                FunctionGraphCurveHitTester.radiusPixels(density = density.density.toDouble())
                            )
                            val chosen = selection.select(candidates, touch, context(), android.os.SystemClock.uptimeMillis())
                            if (chosen != null) requestEvaluation(chosen, final = false)
                        },
                        onDoubleTap = {
                            evaluationCoordinator.invalidate()
                            selection.rollbackPendingTapForDoubleTap()
                            selection.invalidateCycle()
                            coordinator.resetViewport()
                        },
                        isNearMarker = { point ->
                            val current = selection.state.value.marker ?: return@functionGraphGestures false
                            val y = current.displayedY ?: return@functionGraphGestures false
                            val latest = coordinator.state.value
                            if (model.curves.firstOrNull { it.id == current.slotId }?.isVisible != true ||
                                current.x !in latest.displayedViewport.x.minimum..latest.displayedViewport.x.maximum ||
                                y !in latest.displayedViewport.y.minimum..latest.displayedViewport.y.maximum) return@functionGraphGestures false
                            val screen = com.example.calcstudiopro.statistics.graph.domain.GraphTransform(
                                latest.displayedViewport, plot
                            ).dataToScreen(com.example.calcstudiopro.statistics.graph.model.DataPoint(current.x, y))
                            kotlin.math.hypot(screen.x - point.x, screen.y - point.y) <= 36.0 * density.density
                        },
                        onMarkerDrag = { point ->
                            var current = selection.state.value.marker ?: return@functionGraphGestures
                            if (current.dataGeneration != model.dataGeneration) {
                                current = selection.onRenderModelChanged(model, current.contentRevision)
                                    ?: return@functionGraphGestures
                            }
                            val index = current.segmentIndex ?: return@functionGraphGestures
                            val latest = coordinator.state.value
                            val hit = FunctionGraphMarkerDrag.hitOnSelectedSegment(
                                model, current.slotId, index, point, latest.displayedViewport, plot
                            ) ?: return@functionGraphGestures
                            val moved = selection.moveMarker(hit, context())
                            if (moved != null) requestEvaluation(moved, final = false)
                        },
                        onMarkerDragEnd = {
                            selection.state.value.marker?.let { requestEvaluation(it, final = true) }
                        }
                    ),
                    viewportGeneration = state.displayedViewportGeneration,
                    testTag = "function-graph-canvas-demo"
                )
            }
        }
    }
}

private object FunctionGraphCanvasDemoData {
    private val expressions = listOf(
        "sin(x)", "0.5x^2-1", "exp(x/3)", "ln(x)", "1/x", "sin(x + 0.08)"
    )
    val content = FunctionGraphContentSnapshot(
        revision = 1,
        functions = FunctionSlotId.ordered.zip(expressions).map { (id, source) ->
            when (val compiled = MathExpressionEngine().compile(source)) {
                is ExpressionCompilation.Success -> FunctionGraphInput(id, expression = compiled.expression)
                is ExpressionCompilation.Failure -> FunctionGraphInput(id, compilationError = compiled.error)
            }
        }
    )

    fun renderModel(batch: com.example.calcstudiopro.functions.graph.FunctionGraphBatchResult?): FunctionGraphRenderModel {
        val results = batch?.results?.associateBy { it.id }.orEmpty()
        return FunctionGraphRenderModel(batch?.generation, FunctionSlotId.ordered.mapIndexed { index, id ->
            val result = results[id]
            val compilationError = (content.functions[index].compilationError)
            FunctionCurveRenderModel(
                id = id,
                color = FunctionColorPalette.defaultFor(id),
                isVisible = true,
                segments = result?.segments.orEmpty(),
                status = result?.status ?: compilationError?.let(FunctionGraphStatus::Error),
                diagnostics = result?.diagnostics
            )
        })
    }
}
