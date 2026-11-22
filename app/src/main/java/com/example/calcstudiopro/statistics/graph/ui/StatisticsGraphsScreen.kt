package com.example.calcstudiopro.statistics.graph.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material.icons.filled.Tune
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.calcstudiopro.statistics.Statistics2DEngine
import com.example.calcstudiopro.statistics.StatisticsState
import com.example.calcstudiopro.statistics.StatisticsViewModel
import com.example.calcstudiopro.statistics.graph.StatisticsGraphFactory
import com.example.calcstudiopro.statistics.graph.domain.GraphRange
import com.example.calcstudiopro.statistics.graph.model.GraphSelection
import com.example.calcstudiopro.statistics.graph.model.GraphSelectionDetails
import com.example.calcstudiopro.statistics.graph.model.GraphSeriesKind
import com.example.calcstudiopro.statistics.graph.model.GraphViewport

private enum class StatisticsGraphTab(val title: String) {
    REGRESSION("Nuage + régression"),
    RESIDUALS("Résidus")
}

@Composable
fun StatisticsGraphsRoute(
    viewModel: StatisticsViewModel,
    onBack: () -> Unit,
    onOpenDemo: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    StatisticsGraphsScreen(state, onBack, onOpenDemo)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticsGraphsScreen(
    state: StatisticsState,
    onBack: () -> Unit,
    onOpenDemo: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableStateOf(StatisticsGraphTab.REGRESSION) }
    var showGrid by remember { mutableStateOf(true) }
    var showTicks by remember { mutableStateOf(true) }
    var showRegression by remember { mutableStateOf(true) }
    var showOptions by remember { mutableStateOf(false) }
    LaunchedEffect(state.twoDPoints, state.twoDSourceSeriesId, state.twoDDirty, state.model) {
        showGrid = true
        showTicks = true
        showRegression = true
    }
    val activeFit = state.selectedFit.fit?.takeIf { !state.fitLoading && it.model == state.model }
    val fitMessage = when {
        state.fitLoading -> "Ajustement en cours…"
        activeFit == null -> state.selectedFit.unavailableReason ?: "Ajustement indisponible."
        else -> null
    }
    val graphData = remember(state.twoDPoints, state.model, activeFit, fitMessage, selectedTab) {
        when (selectedTab) {
            StatisticsGraphTab.REGRESSION -> StatisticsGraphFactory.regressionScatter(state.twoDPoints, activeFit, fitMessage)
            StatisticsGraphTab.RESIDUALS -> StatisticsGraphFactory.residuals(state.twoDPoints, activeFit, fitMessage)
        }
    }
    var viewport by remember(state.twoDPoints, state.twoDSourceSeriesId, state.twoDDirty, state.model, activeFit, selectedTab) { mutableStateOf(GraphRange.autoFit(graphData.snapshot)) }
    var selection by remember(state.twoDPoints, state.twoDSourceSeriesId, state.twoDDirty, state.model, activeFit, selectedTab) { mutableStateOf<GraphSelectionDetails?>(null) }
    val displaySnapshot = remember(graphData.snapshot, showGrid, showTicks, showRegression) {
        graphData.snapshot.copy(
            showGrid = showGrid,
            showTicks = showTicks,
            series = if (selectedTab == StatisticsGraphTab.REGRESSION && !showRegression)
                graphData.snapshot.series.filterNot { it.id == "selected-fit" } else graphData.snapshot.series
        )
    }
    val modelName = modelDisplayName(state.model)

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Graphiques 2D", maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("statistics-graphs-back")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour aux statistiques")
                    }
                },
                actions = {
                    IconButton(onClick = { showOptions = true }, modifier = Modifier.testTag("statistics-graph-options")) {
                        Icon(Icons.Default.Tune, contentDescription = "Options du graphique")
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
            PrimaryTabRow(
                selectedTabIndex = selectedTab.ordinal,
                modifier = Modifier.fillMaxWidth().testTag("statistics-graph-tabs")
            ) {
                StatisticsGraphTab.entries.forEach { tab ->
                    Tab(
                        selected = tab == selectedTab,
                        onClick = { selectedTab = tab },
                        text = { Text(tab.title, maxLines = 1) },
                        modifier = Modifier.testTag("statistics-graph-tab-${tab.name.lowercase()}")
                    )
                }
            }
            Text("Modèle sélectionné : $modelName", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium,
                modifier = Modifier.testTag("statistics-graph-model"))
            Text("${state.twoDPoints.size} couples", style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.testTag("statistics-graph-observation-count"))
            graphData.unavailableMessage?.let {
                Text(it, color = if (state.fitLoading) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("statistics-graph-message"))
            }
            if (selectedTab == StatisticsGraphTab.RESIDUALS && graphData.excludedResidualCount > 0) {
                Text(
                    "${graphData.excludedResidualCount} résidu(s) non calculable(s) ignoré(s).",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.testTag("statistics-graph-excluded-residuals")
                )
            }
            InteractiveGraphCanvas(
                snapshot = displaySnapshot,
                viewport = viewport,
                selection = selection,
                onViewportChange = { viewport = it },
                onSelectionChange = { selection = it },
                onCycleSelection = {
                    val pointSeries = graphData.snapshot.series.firstOrNull { it.kind != GraphSeriesKind.LINE }
                    val points = pointSeries?.points.orEmpty()
                    if (points.isNotEmpty()) {
                        val currentIndex = points.indexOfFirst { it.id == selection?.selection?.point?.id }
                        val next = points[(currentIndex + 1).mod(points.size)]
                        selection = GraphSelectionDetails(GraphSelection(pointSeries!!.id, next))
                    }
                },
                onResetViewport = {
                    viewport = GraphRange.autoFit(displaySnapshot)
                    selection = null
                },
                modifier = Modifier.fillMaxWidth(),
                testTag = "statistics-graph-canvas"
            )
            if (state.twoDPoints.isEmpty()) {
                Text("Aucun couple X/Y dans la session courante.", style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = onOpenDemo, modifier = Modifier.testTag("statistics-open-graph-demo")) {
                Text("Démo moteur Canvas · lot 5.1")
            }
        }
    }
    if (showOptions) ModalBottomSheet(onDismissRequest = { showOptions = false }) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Options du graphique", style = MaterialTheme.typography.titleLarge)
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text("Afficher la grille"); Switch(checked = showGrid, onCheckedChange = { showGrid = it }, modifier = Modifier.testTag("graph-option-grid").semantics { contentDescription = "Afficher la grille" })
            }
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text("Afficher les graduations"); Switch(checked = showTicks, onCheckedChange = { showTicks = it }, modifier = Modifier.testTag("graph-option-ticks").semantics { contentDescription = "Afficher les graduations" })
            }
            if (selectedTab == StatisticsGraphTab.REGRESSION) Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text("Afficher la régression"); Switch(checked = showRegression, onCheckedChange = { showRegression = it }, modifier = Modifier.testTag("graph-option-regression").semantics { contentDescription = "Afficher la courbe de régression" })
            }
            TextButton(onClick = { viewport = GraphRange.autoFit(displaySnapshot); selection = null }) { Text("Réinitialiser le cadrage") }
        }
    }
}

private fun modelDisplayName(model: Statistics2DEngine.Model): String = when (model.family) {
    Statistics2DEngine.Family.LINEAR -> "Linéaire"
    Statistics2DEngine.Family.POLYNOMIAL -> "Polynôme degré ${model.degree}"
    Statistics2DEngine.Family.EXPONENTIAL -> "Exponentiel"
    Statistics2DEngine.Family.LOGARITHMIC -> "Logarithmique"
    Statistics2DEngine.Family.POWER -> "Puissance"
}
