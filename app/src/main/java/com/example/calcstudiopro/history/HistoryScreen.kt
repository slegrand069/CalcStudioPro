package com.example.calcstudiopro.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.calcstudiopro.history.domain.CalculatorHistoryValue
import com.example.calcstudiopro.history.domain.HistoryEntry

@Composable
fun HistoryRoute(
    viewModel: HistoryViewModel,
    onOpenModeSelector: () -> Unit,
    onReuseValue: (String) -> Unit
) {
    val state by viewModel.state.collectAsState()
    HistoryScreen(
        state = state,
        onOpenModeSelector = onOpenModeSelector,
        onDelete = viewModel::delete,
        onClear = viewModel::clear,
        onReuseValue = onReuseValue
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    state: HistoryUiState,
    onOpenModeSelector: () -> Unit,
    onDelete: (Long) -> Unit,
    onClear: () -> Unit,
    onReuseValue: (String) -> Unit
) {
    var showClearConfirmation by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Historique") },
                actions = {
                    TextButton(onClick = onOpenModeSelector, modifier = Modifier.testTag("history-mode")) {
                        Text("MODE")
                    }
                    TextButton(
                        onClick = { showClearConfirmation = true },
                        enabled = state.entries.isNotEmpty(),
                        modifier = Modifier.testTag("history-clear")
                    ) {
                        Text("RAZ")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            state.errorMessage?.let { message ->
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            when {
                state.isLoading -> Text("Chargement…", modifier = Modifier.padding(16.dp))
                state.entries.isEmpty() -> Text(
                    text = "Aucun calcul enregistré",
                    modifier = Modifier
                        .padding(16.dp)
                        .testTag("history-empty")
                )
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(state.entries, key = HistoryEntry::id) { entry ->
                        HistoryEntryCard(
                            entry = entry,
                            onDelete = { onDelete(entry.id) },
                            onReuseValue = onReuseValue
                        )
                    }
                }
            }
        }
    }

    if (showClearConfirmation) {
        AlertDialog(
            onDismissRequest = { showClearConfirmation = false },
            title = { Text("Effacer l’historique ?") },
            text = { Text("Tous les calculs enregistrés seront supprimés.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearConfirmation = false
                        onClear()
                    },
                    modifier = Modifier.testTag("history-clear-confirm")
                ) { Text("Effacer") }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmation = false }) { Text("Annuler") }
            }
        )
    }
}

@Composable
private fun HistoryEntryCard(
    entry: HistoryEntry,
    onDelete: () -> Unit,
    onReuseValue: (String) -> Unit
) {
    val reusableExpression = remember(entry.reusableValue, entry.reusableValueType) {
        CalculatorHistoryValue.toCalculatorExpression(entry.reusableValue, entry.reusableValueType)
    }

    Card(modifier = Modifier.fillMaxWidth().testTag("history-entry-${entry.id}")) {
        Column(modifier = Modifier.padding(start = 16.dp, top = 12.dp, end = 8.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = entry.description,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("history-expression-${entry.id}")
                    )
                    Text(
                        text = entry.displayResult,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("history-result-${entry.id}")
                    )
                }
                IconButton(onClick = onDelete, modifier = Modifier.testTag("history-delete-${entry.id}")) {
                    Icon(Icons.Default.Delete, contentDescription = "Supprimer ce calcul")
                }
            }
            if (reusableExpression != null) {
                Text(
                    text = "Réutiliser dans la calculatrice",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onReuseValue(reusableExpression) }
                        .testTag("history-reuse-${entry.id}")
                        .padding(vertical = 8.dp)
                )
            }
        }
    }
}
