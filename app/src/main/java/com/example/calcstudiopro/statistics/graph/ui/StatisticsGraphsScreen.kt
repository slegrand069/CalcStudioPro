package com.example.calcstudiopro.statistics.graph.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.PrimaryScrollableTabRow
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
    var viewport by remember(graphData.snapshot) { mutableStateOf(GraphRange.autoFit(graphData.snapshot)) }
    var selection by remember(graphData.snapshot) { mutableStateOf<GraphSelectionDetails?>(null) }
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
                }
            )
        }
    ) { insets ->
        Column(
            Modifier.fillMaxSize().padding(insets).padding(horizontal = 16.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PrimaryScrollableTabRow(
                selectedTabIndex = selectedTab.ordinal,
                edgePadding = 0.dp,
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
                snapshot = graphData.snapshot,
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
                    viewport = GraphRange.autoFit(graphData.snapshot)
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
}

private fun modelDisplayName(model: Statistics2DEngine.Model): String = when (model.family) {
    Statistics2DEngine.Family.LINEAR -> "Linéaire"
    Statistics2DEngine.Family.POLYNOMIAL -> "Polynôme degré ${model.degree}"
    Statistics2DEngine.Family.EXPONENTIAL -> "Exponentiel"
    Statistics2DEngine.Family.LOGARITHMIC -> "Logarithmique"
    Statistics2DEngine.Family.POWER -> "Puissance"
}
