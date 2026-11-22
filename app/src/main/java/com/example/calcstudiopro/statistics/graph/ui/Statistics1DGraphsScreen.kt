package com.example.calcstudiopro.statistics.graph.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.calcstudiopro.statistics.StatisticsEngine
import com.example.calcstudiopro.statistics.StatisticsViewModel
import com.example.calcstudiopro.statistics.StatisticsState
import com.example.calcstudiopro.statistics.graph.HistogramClassMode
import com.example.calcstudiopro.statistics.graph.HistogramScale
import com.example.calcstudiopro.statistics.graph.HistogramStatistics
import com.example.calcstudiopro.statistics.graph.Statistics1DGraphFactory
import com.example.calcstudiopro.statistics.graph.TukeyStatistics
import com.example.calcstudiopro.statistics.graph.domain.GraphRange
import com.example.calcstudiopro.statistics.graph.domain.GraphTransform
import com.example.calcstudiopro.statistics.graph.model.GraphPoint
import com.example.calcstudiopro.statistics.graph.model.GraphSelection
import com.example.calcstudiopro.statistics.graph.model.GraphSelectionDetails
import com.example.calcstudiopro.statistics.graph.model.GraphSeriesKind

private enum class OneDGraphTab(val title: String) {
    HISTOGRAM("Histogramme"), TUKEY("Boîte à moustaches")
}

@Composable
fun Statistics1DGraphsRoute(viewModel: StatisticsViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsState()
    Statistics1DGraphsScreen(state, onBack)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Statistics1DGraphsScreen(state: StatisticsState, onBack: () -> Unit, modifier: Modifier = Modifier) {
    var selectedTab by remember { mutableStateOf(OneDGraphTab.HISTOGRAM) }
    var classMode by remember { mutableStateOf(HistogramClassMode.AUTOMATIC) }
    var manualClassesText by remember { mutableStateOf("5") }
    var scale by remember { mutableStateOf(HistogramScale.COUNT) }
    val requestedClasses = manualClassesText.toIntOrNull() ?: 1
    val histogram = remember(state.oneDValues, classMode, requestedClasses) {
        HistogramStatistics.calculate(state.oneDValues, classMode, requestedClasses)
    }
    val tukey = remember(state.oneDValues) { TukeyStatistics.calculate(state.oneDValues) }
    val graphData = remember(selectedTab, histogram, tukey, scale) {
        when (selectedTab) {
            OneDGraphTab.HISTOGRAM -> Statistics1DGraphFactory.histogram(histogram, scale)
            OneDGraphTab.TUKEY -> Statistics1DGraphFactory.tukeyBoxPlot(tukey)
        }
    }
    var viewport by remember(graphData) { mutableStateOf(GraphRange.autoFit(graphData)) }
    var selection by remember(graphData) { mutableStateOf<GraphSelectionDetails?>(null) }
    val classLimit = minOf(HistogramStatistics.MAX_MANUAL_CLASSES, state.oneDValues.count(Double::isFinite)).coerceAtLeast(1)

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Graphiques 1D", maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("statistics-1d-graphs-back")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour aux statistiques")
                    }
                }
            )
        }
    ) { insets ->
        Column(
            Modifier.fillMaxSize().padding(insets).padding(horizontal = 16.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PrimaryScrollableTabRow(selectedTabIndex = selectedTab.ordinal, edgePadding = 0.dp, modifier = Modifier.fillMaxWidth().testTag("statistics-1d-graph-tabs")) {
                OneDGraphTab.entries.forEach { tab ->
                    Tab(
                        selected = tab == selectedTab,
                        onClick = { selectedTab = tab },
                        text = { Text(tab.title, maxLines = 1) },
                        modifier = Modifier.testTag("statistics-1d-graph-tab-${tab.name.lowercase()}")
                    )
                }
            }
            Text("${state.oneDValues.size} observations", style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.testTag("statistics-1d-observation-count"))

            if (selectedTab == OneDGraphTab.HISTOGRAM) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = classMode == HistogramClassMode.AUTOMATIC,
                        onClick = { classMode = HistogramClassMode.AUTOMATIC },
                        label = { Text("Automatique") }, modifier = Modifier.testTag("histogram-mode-automatic")
                    )
                    FilterChip(
                        selected = classMode == HistogramClassMode.MANUAL,
                        onClick = { classMode = HistogramClassMode.MANUAL },
                        label = { Text("Manuel") }, modifier = Modifier.testTag("histogram-mode-manual")
                    )
                }
                if (classMode == HistogramClassMode.MANUAL) {
                    OutlinedTextField(
                        value = manualClassesText,
                        onValueChange = { if (it.isEmpty() || it.all(Char::isDigit)) manualClassesText = it },
                        label = { Text("Nombre de classes") },
                        supportingText = { Text("De 1 à $classLimit classes (maximum 100).") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth().testTag("histogram-manual-class-count")
                    )
                    Text("${histogram.bins.size} classe(s) utilisée(s).", style = MaterialTheme.typography.bodySmall)
                } else {
                    Text("${histogram.bins.size} classes · règle de Sturges", style = MaterialTheme.typography.bodySmall)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = scale == HistogramScale.COUNT,
                        onClick = { scale = HistogramScale.COUNT },
                        label = { Text("Effectif") }, modifier = Modifier.testTag("histogram-scale-count")
                    )
                    FilterChip(
                        selected = scale == HistogramScale.PERCENT,
                        onClick = { scale = HistogramScale.PERCENT },
                        label = { Text("Pourcentage") }, modifier = Modifier.testTag("histogram-scale-percent")
                    )
                }
                histogram.message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            } else {
                tukey.message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                tukey.summary?.let { summary ->
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(
                            "Q1 ${StatisticsEngine.format(summary.q1)} · Médiane ${StatisticsEngine.format(summary.median)} · Q3 ${StatisticsEngine.format(summary.q3)}" +
                                (summary.interquartileRange?.let { " · IQR ${StatisticsEngine.format(it)}" } ?: " · IQR non représentable"),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.testTag("tukey-summary-quartiles")
                        )
                        Text(
                            "Moustaches ${StatisticsEngine.format(summary.lowerWhisker)} à ${StatisticsEngine.format(summary.upperWhisker)} · " +
                                "${summary.outliers.size} valeur(s) atypique(s), ${summary.outlierMultiplicities.size} distincte(s)",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.testTag("tukey-summary-whiskers")
                        )
                    }
                }
            }

            InteractiveGraphCanvas(
                snapshot = graphData,
                viewport = viewport,
                selection = selection,
                onViewportChange = { viewport = it },
                onSelectionChange = { selection = it },
                onCycleSelection = {
                    val targets = selectablePoints(graphData)
                    if (targets.isNotEmpty()) {
                        val current = targets.indexOfFirst { it.first.id == selection?.selection?.point?.id }
                        val next = targets[(current + 1).mod(targets.size)]
                        selection = GraphSelectionDetails(GraphSelection(next.second, next.first))
                    }
                },
                onResetViewport = {
                    viewport = GraphRange.autoFit(graphData)
                    selection = null
                },
                modifier = Modifier.fillMaxWidth(),
                testTag = "statistics-1d-graph-canvas"
            )
            if (state.oneDValues.isEmpty()) {
                Text("Aucune observation 1D dans la session courante.", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

private fun selectablePoints(snapshot: com.example.calcstudiopro.statistics.graph.model.GraphSnapshot): List<Pair<GraphPoint, String>> =
    snapshot.series.flatMap { series ->
        buildList {
            if (series.kind != GraphSeriesKind.LINE && series.kind != GraphSeriesKind.BARS) {
                series.points.forEach { add(it to series.id) }
            }
            series.bars.forEach { bar ->
                val x = GraphTransform.interpolate(bar.xMinimum, bar.xMaximum, 0.5)
                val y = GraphTransform.interpolate(bar.yMinimum, bar.yMaximum, 0.5)
                add(GraphPoint(bar.id, x, y, details = bar.details) to series.id)
            }
            series.segments.forEach { segment ->
                add(GraphPoint(segment.id, segment.x1, segment.y1, details = segment.details) to series.id)
            }
        }
    }
