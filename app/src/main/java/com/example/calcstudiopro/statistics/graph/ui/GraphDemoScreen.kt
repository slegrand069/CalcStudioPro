package com.example.calcstudiopro.statistics.graph.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.calcstudiopro.statistics.graph.domain.GraphRange
import com.example.calcstudiopro.statistics.graph.model.GraphPoint
import com.example.calcstudiopro.statistics.graph.model.GraphSelection
import com.example.calcstudiopro.statistics.graph.model.GraphSelectionDetails
import com.example.calcstudiopro.statistics.graph.model.GraphSeries
import com.example.calcstudiopro.statistics.graph.model.GraphSeriesKind
import com.example.calcstudiopro.statistics.graph.model.GraphSnapshot
import com.example.calcstudiopro.statistics.graph.model.GraphViewport

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GraphDemoScreen(onBack: () -> Unit) {
    var includeTenThousand by rememberSaveable { mutableStateOf(false) }
    var showGrid by rememberSaveable { mutableStateOf(true) }
    var selection by remember { mutableStateOf<GraphSelectionDetails?>(null) }
    var status by remember { mutableStateOf("Prototype isolé · les moteurs statistiques ne sont pas utilisés.") }
    var cycleIndex by remember { mutableIntStateOf(-1) }
    val series = remember(includeTenThousand) { demoSeries(includeTenThousand) }
    val snapshot = remember(series, showGrid) {
        GraphSnapshot("Nuage de points de démonstration", "X", "Y", series, showGrid)
    }
    val autoViewport = remember(series) { GraphRange.autoFit(GraphSnapshot("Demo", "X", "Y", series)) }
    var viewport by remember(series) { mutableStateOf(autoViewport) }
    val observations = series.first().points

    LaunchedEffect(series) {
        viewport = autoViewport
        selection = null
        cycleIndex = -1
        status = if (includeTenThousand) "Jeu de 10 000 observations chargé." else "Jeu de démonstration chargé."
    }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Prototype graphique · 5.1", maxLines = 1) },
            navigationIcon = {
                IconButton(onClick = onBack, modifier = Modifier.semantics { contentDescription = "Retour" }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                }
            }
        )
    }) { inset ->
        Column(
            Modifier.fillMaxSize().padding(inset).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Nuage de points et droite de référence. Les coordonnées sources restent en Double.", style = MaterialTheme.typography.bodyMedium)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Switch(
                    checked = includeTenThousand,
                    onCheckedChange = { includeTenThousand = it },
                    modifier = Modifier.testTag("graph-demo-large-data-toggle").semantics {
                        contentDescription = "Afficher 10 000 observations"
                    }
                )
                Text(if (includeTenThousand) "10 000 observations" else "Jeu compact · 12 observations")
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = showGrid,
                    onCheckedChange = { showGrid = it },
                    modifier = Modifier.testTag("graph-demo-grid-toggle").semantics { contentDescription = "Afficher la grille" }
                )
                Text("Grille")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    viewport = autoViewport
                    selection = null
                    status = "Cadrage réinitialisé."
                }, modifier = Modifier.testTag("graph-demo-reset")) { Text("Réinitialiser le cadrage") }
                TextButton(onClick = {
                    cycleIndex = (cycleIndex + 1) % observations.size
                    val point = observations[cycleIndex]
                    selection = GraphSelectionDetails(GraphSelection(series.first().id, point))
                    status = "Point suivant sélectionné."
                }, modifier = Modifier.testTag("graph-demo-next-point")) { Text("Point suivant") }
            }
            Text("${observations.size} observations · deux points sont superposés. Tap répété sur la même zone pour les parcourir.",
                style = MaterialTheme.typography.bodySmall)
            InteractiveGraphCanvas(
                snapshot = snapshot,
                viewport = viewport,
                selection = selection,
                onViewportChange = { viewport = it },
                onSelectionChange = {
                    selection = it
                    status = if (it == null) "Aucun point sélectionné." else "Point sélectionné avec ses coordonnées exactes."
                },
                onCycleSelection = {
                    cycleIndex = (cycleIndex + 1) % observations.size
                    selection = GraphSelectionDetails(GraphSelection(series.first().id, observations[cycleIndex]))
                    status = "Point suivant sélectionné."
                },
                onResetViewport = {
                    viewport = autoViewport
                    selection = null
                    status = "Cadrage réinitialisé par double-tap."
                },
                modifier = Modifier.fillMaxWidth()
            )
            Text(status, modifier = Modifier.testTag("graph-demo-status"), style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(8.dp))
        }
    }
}

private fun demoSeries(includeTenThousand: Boolean): List<GraphSeries> {
    val observations = if (includeTenThousand) {
        buildList(10_000) {
            repeat(9_998) { index ->
                val x = ((index % 200) - 100) * 0.5
                val y = kotlin.math.sin(x * 0.16) * 12.0 + (index % 17) * 0.08
                add(GraphPoint("sample-$index", x, y))
            }
            add(GraphPoint("overlap-large-a", 0.0, 0.0))
            add(GraphPoint("overlap-large-b", 0.0, 0.0))
        }
    } else {
        listOf(
            GraphPoint("p1", -4.0, -1.5), GraphPoint("p2", -3.0, 2.2), GraphPoint("p3", -2.0, 0.0),
            GraphPoint("p4", -1.0, -0.8), GraphPoint("p5", 0.0, 0.3), GraphPoint("p6", 1.0, 1.7),
            GraphPoint("overlap-a", 2.0, 1.0), GraphPoint("overlap-b", 2.0, 1.0),
            GraphPoint("p9", 3.0, 2.4), GraphPoint("p10", 4.0, 5.0),
            GraphPoint("tiny-positive", 5e-12, -2e-12), GraphPoint("tiny-negative", -5e-12, 3e-12)
        )
    }
    val line = (-60..60).map { index ->
        val x = index / 12.0
        GraphPoint("line-$index", x, 0.35 + 1.15 * x, connectFromPrevious = index != -60)
    }
    return listOf(
        GraphSeries("observations", "Observations", observations, GraphSeriesKind.POINTS, colorIndex = 0),
        GraphSeries("reference-line", "Droite de référence", line, GraphSeriesKind.LINE, colorIndex = 1)
    )
}
