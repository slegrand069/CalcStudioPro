package com.example.calcstudiopro.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

private val defaultToleranceLabel = ContinuedFractionSettings.DEFAULT_TOLERANCE
    .toString()
    .replace(".0E", "e")
    .replace("E", "e")

@Composable
fun SettingsRoute(viewModel: SettingsViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsState()
    SettingsScreen(
        state = state,
        onBack = onBack,
        onUpdateContinuedFractions = viewModel::updateContinuedFractions,
        onAngleUnitChange = viewModel::updateAngleUnit,
        onNotationChange = viewModel::updateNotation
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(
    state: SettingsUiState,
    onBack: () -> Unit,
    onUpdateContinuedFractions: (ContinuedFractionSettings) -> Unit,
    onAngleUnitChange: (com.example.calcstudiopro.domain.AngleUnit) -> Unit,
    onNotationChange: (CalculatorNotation) -> Unit
) {
    val settings = state.settings.continuedFractions
    var toleranceText by rememberSaveable { mutableStateOf(settings.tolerance.toReadableTolerance()) }
    var denominatorText by rememberSaveable { mutableStateOf(settings.maxDenominator.toString()) }
    var iterationsText by rememberSaveable { mutableStateOf(settings.maxIterations.toString()) }
    LaunchedEffect(settings) {
        toleranceText = settings.tolerance.toReadableTolerance()
        denominatorText = settings.maxDenominator.toString()
        iterationsText = settings.maxIterations.toString()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("settings-back")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Fractions continues", style = MaterialTheme.typography.titleLarge)
            HorizontalDivider()

            OutlinedTextField(
                value = toleranceText,
                onValueChange = { value ->
                    if (value.matches(Regex("[0-9eE+\\-.]*"))) {
                        toleranceText = value
                        value.toDoubleOrNull()?.takeIf { it.isFinite() && it > 0.0 }?.let {
                            onUpdateContinuedFractions(settings.copy(tolerance = it))
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().testTag("settings-tolerance"),
                label = { Text("Tolérance de l’approximation") },
                supportingText = { Text("Seuil numérique (valeur par défaut : $defaultToleranceLabel)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
            )
            OutlinedTextField(
                value = denominatorText,
                onValueChange = { value ->
                    if (value.all(Char::isDigit)) {
                        denominatorText = value
                        value.toIntOrNull()?.takeIf { it >= 1 }?.let { onUpdateContinuedFractions(settings.copy(maxDenominator = it)) }
                    }
                },
                modifier = Modifier.fillMaxWidth().testTag("settings-max-denominator"),
                label = { Text("Dénominateur maximal") },
                supportingText = { Text("Valeur par défaut : 10 000") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
            OutlinedTextField(
                value = iterationsText,
                onValueChange = { value ->
                    if (value.all(Char::isDigit)) {
                        iterationsText = value
                        value.toIntOrNull()?.takeIf { it > 0 }?.let { onUpdateContinuedFractions(settings.copy(maxIterations = it)) }
                    }
                },
                modifier = Modifier.fillMaxWidth().testTag("settings-max-iterations"),
                label = { Text("Nombre maximal d’itérations") },
                supportingText = { Text("Valeur par défaut : 64") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )

            Text("Présentation du résultat", style = MaterialTheme.typography.titleMedium)
            Row(modifier = Modifier.fillMaxWidth()) {
                ResultPresentation.entries.forEach { presentation ->
                    ChoiceOption(
                        modifier = Modifier.weight(1f),
                        label = if (presentation == ResultPresentation.FRACTION) "Fraction" else "Décimal",
                        selected = settings.resultPresentation == presentation,
                        tag = "settings-presentation-${presentation.name}",
                        onSelect = {
                            onUpdateContinuedFractions(settings.copy(resultPresentation = presentation))
                        }
                    )
                }
            }

            Text("Angles", style = MaterialTheme.typography.titleMedium)
            Row(modifier = Modifier.fillMaxWidth()) {
                com.example.calcstudiopro.domain.AngleUnit.entries.forEach { angleUnit ->
                    ChoiceOption(
                        modifier = Modifier.weight(1f),
                        label = if (angleUnit == com.example.calcstudiopro.domain.AngleUnit.DEGREES) "Degrés" else "Radians",
                        selected = state.settings.angleUnit == angleUnit,
                        tag = "settings-angle-${angleUnit.name}",
                        onSelect = { onAngleUnitChange(angleUnit) }
                    )
                }
            }

            Text("Notation scientifique", style = MaterialTheme.typography.titleMedium)
            Row(modifier = Modifier.fillMaxWidth()) {
                CalculatorNotation.entries.forEach { notation ->
                    ChoiceOption(
                        modifier = Modifier.weight(1f),
                        label = if (notation == CalculatorNotation.STANDARD) "Normale" else "Scientifique",
                        selected = state.settings.notation == notation,
                        tag = "settings-notation-${notation.name}",
                        onSelect = { onNotationChange(notation) }
                    )
                }
            }
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
}

private fun Double.toReadableTolerance(): String = toString().replace(".0E", "e").replace("E", "e")

@Composable
private fun ChoiceOption(
    modifier: Modifier = Modifier,
    label: String,
    selected: Boolean,
    tag: String,
    onSelect: () -> Unit
) {
    Row(
        modifier = modifier
            .selectable(selected = selected, onClick = onSelect, role = Role.RadioButton)
            .testTag(tag)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(label)
    }
}
