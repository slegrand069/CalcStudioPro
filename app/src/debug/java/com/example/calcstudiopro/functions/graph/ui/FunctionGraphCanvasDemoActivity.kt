package com.example.calcstudiopro.functions.graph.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.calcstudiopro.functions.domain.FunctionColorPalette
import com.example.calcstudiopro.functions.domain.FunctionSlotId
import com.example.calcstudiopro.functions.graph.FunctionGraphDiagnostics
import com.example.calcstudiopro.functions.graph.FunctionGraphPoint
import com.example.calcstudiopro.functions.graph.FunctionGraphSegment
import com.example.calcstudiopro.functions.graph.FunctionGraphStatus
import com.example.calcstudiopro.functions.graph.render.FunctionCurveRenderModel
import com.example.calcstudiopro.functions.graph.render.FunctionGraphRenderModel
import com.example.calcstudiopro.functions.graph.render.FunctionGridMode
import com.example.calcstudiopro.statistics.graph.model.AxisRange
import com.example.calcstudiopro.statistics.graph.model.GraphViewport
import com.example.calcstudiopro.ui.theme.CalcStudioProTheme
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sin

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
            FunctionGraphCanvas(
                model = FunctionGraphCanvasDemoData.model,
                viewport = FunctionGraphCanvasDemoData.viewport,
                gridMode = FunctionGridMode.MAJOR_AND_MINOR,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                viewportGeneration = 1,
                testTag = "function-graph-canvas-demo"
            )
        }
    }
}

private object FunctionGraphCanvasDemoData {
    val viewport = GraphViewport(AxisRange(-4.0, 4.0), AxisRange(-3.0, 4.0))
    private val diagnostics = FunctionGraphDiagnostics(321, 28, 4, 1)
    val model: FunctionGraphRenderModel = run {
        val functions: Map<FunctionSlotId, Pair<Boolean, List<FunctionGraphSegment>>> = mapOf(
            FunctionSlotId.F1 to (true to listOf(sample(-4.0, 4.0, 0.04) { sin(it) })),
            FunctionSlotId.F2 to (true to listOf(sample(-3.0, 3.0, 0.04) { 0.5 * it * it - 1.0 })),
            FunctionSlotId.F3 to (true to listOf(sample(-4.0, 1.35, 0.04) { exp(it) })),
            FunctionSlotId.F4 to (true to listOf(sample(0.08, 4.0, 0.04) { ln(it) })),
            FunctionSlotId.F5 to (true to listOf(
                sample(-4.0, -0.18, 0.04) { 1.0 / it },
                sample(0.18, 4.0, 0.04) { 1.0 / it }
            )),
            FunctionSlotId.F6 to (false to listOf(sample(-4.0, 4.0, 0.04) { cos(2.0 * it) }))
        )
        FunctionGraphRenderModel(
            dataGeneration = 1,
            curves = FunctionSlotId.ordered.map { id ->
                val (visible, segments) = functions.getValue(id)
                FunctionCurveRenderModel(
                    id = id,
                    color = FunctionColorPalette.defaultFor(id),
                    isVisible = visible,
                    segments = segments,
                    status = FunctionGraphStatus.Success,
                    diagnostics = diagnostics
                )
            }
        )
    }

    private fun sample(start: Double, end: Double, step: Double, function: (Double) -> Double): FunctionGraphSegment {
        val count = ((end - start) / step).toInt()
        return FunctionGraphSegment((0..count).map { index ->
            val x = if (index == count) end else start + index * step
            FunctionGraphPoint(x, function(x))
        })
    }
}
