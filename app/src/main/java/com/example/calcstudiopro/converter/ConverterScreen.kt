package com.example.calcstudiopro.converter

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@Composable
fun ConverterRoute(viewModel: ConverterViewModel, onOpenModeSelector: () -> Unit) {
    val state by viewModel.state.collectAsState()
    ConverterScreen(state, onOpenModeSelector, viewModel::selectCategory, viewModel::editSource,
        viewModel::editTarget, viewModel::selectUnit, viewModel::invert, viewModel::save)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConverterScreen(
    state: ConverterState,
    onOpenModeSelector: () -> Unit,
    onCategory: (UnitCategory) -> Unit,
    onSource: (String) -> Unit,
    onTarget: (String) -> Unit,
    onUnit: (Boolean, String) -> Unit,
    onInvert: () -> Unit,
    onSave: () -> Unit
) {
    Scaffold(topBar = { TopAppBar(title = { Text("Convertisseur") }, actions = {
        TextButton(onClick = onOpenModeSelector, modifier = Modifier.testTag("converter-mode")) { Text("MODE") }
    }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            UnitCategory.entries.forEach { /* keeps enum order available for semantics */ }
            var categoryExpanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(categoryExpanded, { categoryExpanded = it }) {
                OutlinedTextField(value = state.category.label, onValueChange = {}, readOnly = true,
                    label = { Text("Catégorie") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(categoryExpanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth().testTag("converter-category"))
                ExposedDropdownMenu(categoryExpanded, { categoryExpanded = false }) {
                    UnitCategory.entries.forEach { category -> DropdownMenuItem(text = { Text(category.label) },
                        onClick = { categoryExpanded = false; onCategory(category) }) }
                }
            }
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ConverterValueRow("Valeur source", state.sourceText, state.sourceUnitId, true, state, onSource, onUnit)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        IconButton(onClick = onInvert, modifier = Modifier.testTag("converter-invert").semantics { contentDescription = "Inverser les unités" }) {
                            Icon(Icons.Default.SwapVert, contentDescription = null)
                        }
                    }
                    ConverterValueRow("Résultat", state.targetText, state.targetUnitId, false, state, onTarget, onUnit)
                }
            }
            if (state.error != null) Text(state.error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.testTag("converter-validation"))
            else if (state.canSave) {
                val from = UnitCatalog.get(if (state.activeField == ActiveConverterField.SOURCE) state.sourceUnitId else state.targetUnitId)!!
                val to = UnitCatalog.get(if (state.activeField == ActiveConverterField.SOURCE) state.targetUnitId else state.sourceUnitId)!!
                val input = if (state.activeField == ActiveConverterField.SOURCE) state.sourceText else state.targetText
                Text("$input ${from.symbol} = ${if (state.activeField == ActiveConverterField.SOURCE) state.targetText else state.sourceText} ${to.symbol}",
                    style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("converter-summary"))
            } else Text("Saisissez une valeur à convertir", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.weight(1f))
            Button(onClick = onSave, enabled = state.canSave, modifier = Modifier.fillMaxWidth().testTag("converter-save")) {
                Text("Enregistrer dans l’historique")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConverterValueRow(
    label: String, text: String, unitId: String, isSource: Boolean, state: ConverterState,
    onValue: (String) -> Unit, onUnit: (Boolean, String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val units = UnitCatalog.forCategory(state.category)
    val selected = UnitCatalog.get(unitId)!!
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(value = text, onValueChange = onValue, label = { Text(label) }, singleLine = true,
            modifier = Modifier.weight(1f).testTag(if (isSource) "converter-source" else "converter-target"))
        ExposedDropdownMenuBox(expanded, { expanded = it }, modifier = Modifier.weight(1f)) {
            OutlinedTextField(value = selected.symbol, onValueChange = {}, readOnly = true,
                label = { Text("Unité") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                modifier = Modifier.menuAnchor().fillMaxWidth().testTag(if (isSource) "converter-source-unit" else "converter-target-unit"))
            ExposedDropdownMenu(expanded, { expanded = false }) {
                units.forEach { unit -> DropdownMenuItem(text = { Text("${unit.name} (${unit.symbol})") },
                    onClick = { expanded = false; onUnit(isSource, unit.id) }) }
            }
        }
    }
}
