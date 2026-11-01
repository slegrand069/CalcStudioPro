package com.example.calcstudiopro.statistics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun StatisticsRoute(viewModel: StatisticsViewModel, onOpenModeSelector: () -> Unit) {
    val state by viewModel.state.collectAsState()
    StatisticsScreen(state, onOpenModeSelector, viewModel::edit, viewModel::save)
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun StatisticsScreen(state: StatisticsState, onOpenModeSelector: () -> Unit, onEdit: (String) -> Unit, onSave: () -> Unit) {
    val result = state.result
    val rows = result?.let { listOf(
        "Effectif" to it.count.toString(), "Somme" to StatisticsEngine.format(it.sum),
        "Moyenne" to StatisticsEngine.format(it.mean), "Médiane" to StatisticsEngine.format(it.median),
        "Minimum" to StatisticsEngine.format(it.minimum), "Maximum" to StatisticsEngine.format(it.maximum),
        "Écart-type (population)" to StatisticsEngine.format(it.populationStandardDeviation)
    ) }.orEmpty()
    Scaffold(topBar = { TopAppBar(title = { Text("Statistiques") }, actions = {
        TextButton(onClick = onOpenModeSelector, modifier = Modifier.testTag("statistics-mode")) { Text("MODE") }
    }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(value = state.input, onValueChange = onEdit, label = { Text("Série de valeurs") },
                supportingText = { Text("Sépare les valeurs par des espaces ou des retours à la ligne. Virgule ou point pour les décimales.") },
                minLines = 3, maxLines = 5, modifier = Modifier.fillMaxWidth().testTag("statistics-input"))
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("statistics-error")) }
            state.saveError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            if (rows.isNotEmpty()) {
                LazyVerticalGrid(columns = GridCells.Adaptive(minSize = 145.dp), modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(rows) { (label, value) -> Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(label, style = MaterialTheme.typography.labelMedium)
                            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        }
                    } }
                }
            }
            Button(onClick = onSave, enabled = state.canSave, modifier = Modifier.fillMaxWidth().testTag("statistics-save")) {
                Text("Enregistrer dans l’historique")
            }
        }
    }
}
