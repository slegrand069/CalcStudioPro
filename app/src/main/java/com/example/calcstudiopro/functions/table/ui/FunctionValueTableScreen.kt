package com.example.calcstudiopro.functions.table.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Card
import androidx.activity.compose.BackHandler
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.calcstudiopro.domain.functions.FunctionAngleMode
import com.example.calcstudiopro.functions.FunctionStudyViewModel
import com.example.calcstudiopro.functions.FunctionStudyUiState
import com.example.calcstudiopro.functions.domain.FunctionSlotId
import com.example.calcstudiopro.functions.domain.FunctionStudy
import com.example.calcstudiopro.functions.table.FunctionValueTableAbscissaGenerator
import com.example.calcstudiopro.functions.table.FunctionValueTableCalculationCoordinator
import com.example.calcstudiopro.functions.table.FunctionValueTableCalculationPhase
import com.example.calcstudiopro.functions.table.FunctionValueTableCalculationState
import com.example.calcstudiopro.functions.table.FunctionValueTableGenerationMode
import com.example.calcstudiopro.functions.table.FunctionValueTableModeId
import com.example.calcstudiopro.functions.table.FunctionValueTablePresentation
import com.example.calcstudiopro.functions.table.FunctionValueTableSettings
import com.example.calcstudiopro.functions.table.FunctionValueTableValidationError
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun FunctionValueTableRoute(
    studyViewModel: FunctionStudyViewModel,
    onBack: () -> Unit,
    onOpenGraph: () -> Unit = {},
    coordinatorFactory: ((kotlinx.coroutines.CoroutineScope) -> FunctionValueTableCalculationCoordinator)? = null
) {
    val uiState by studyViewModel.state.collectAsState()
    val study = uiState.study
    val scope = rememberCoroutineScope()
    val coordinator = remember(studyViewModel) {
        coordinatorFactory?.invoke(scope) ?: FunctionValueTableCalculationCoordinator(scope)
    }
    DisposableEffect(coordinator) {
        onDispose {
            studyViewModel.commitGraphParameterChanges()
            coordinator.close()
        }
    }
    if (study == null) {
        Scaffold(topBar = {
            TopAppBar(title = { Text("Tableau de valeurs") }, navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour") }
            })
        }) { padding -> Text(uiState.loadError ?: "Aucune étude active", Modifier.padding(padding).padding(20.dp)) }
    } else {
        val calculation by coordinator.state.collectAsState()
        LaunchedEffect(study) { coordinator.submit(study) }
        FunctionValueTableScreen(
            study = study,
            uiState = uiState,
            calculation = calculation,
            onBack = onBack,
            onSaveSettings = studyViewModel::updateValueTableSettings,
            onParameterDraft = studyViewModel::setGraphParameter,
            onCommitParameterDrafts = { studyViewModel.commitGraphParameterChanges() },
            onAngleMode = studyViewModel::setAngleMode,
            onCalculationRequested = { current -> coordinator.submit(current) },
            onOpenGraph = onOpenGraph
        )
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun FunctionValueTableScreen(
    study: FunctionStudy,
    uiState: FunctionStudyUiState,
    calculation: FunctionValueTableCalculationState,
    onBack: () -> Unit,
    onOpenGraph: () -> Unit,
    onSaveSettings: (FunctionValueTableSettings) -> Boolean,
    onParameterDraft: (Char, Double) -> Boolean,
    onCommitParameterDrafts: () -> Unit,
    onAngleMode: (FunctionAngleMode) -> Unit,
    onCalculationRequested: (FunctionStudy) -> Unit
) {
    var settings by remember(study.id) { mutableStateOf(study.valueTableSettings) }
    var showingResults by remember(study.id) { mutableStateOf(false) }
    var showFunctionSettings by remember(study.id) { mutableStateOf(false) }
    var validationMessage by remember(study.id) { mutableStateOf<String?>(null) }
    var showPresentation by remember(study.id) { mutableStateOf(true) }
    val snackbar = remember { SnackbarHostState() }
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val parameterDrafts = remember(study.id) {
        mutableStateMapOf<Char, String>().apply {
            study.parameters.values.forEach { (name, value) -> put(name, inputNumber(value)) }
        }
    }

    var stepMin by remember(study.id) { mutableStateOf(inputNumber(study.valueTableSettings.retainedModeInputs.intervalAndStep.xMin)) }
    var stepMax by remember(study.id) { mutableStateOf(inputNumber(study.valueTableSettings.retainedModeInputs.intervalAndStep.xMax)) }
    var stepSize by remember(study.id) { mutableStateOf(inputNumber(study.valueTableSettings.retainedModeInputs.intervalAndStep.step)) }
    var countMin by remember(study.id) { mutableStateOf(inputNumber(study.valueTableSettings.retainedModeInputs.intervalAndCount.xMin)) }
    var countMax by remember(study.id) { mutableStateOf(inputNumber(study.valueTableSettings.retainedModeInputs.intervalAndCount.xMax)) }
    var pointCount by remember(study.id) { mutableStateOf(study.valueTableSettings.retainedModeInputs.intervalAndCount.pointCount.toString()) }
    var customValues by remember(study.id) { mutableStateOf(study.valueTableSettings.retainedModeInputs.customValues.text) }
    val horizontalState = rememberScrollState()

    LaunchedEffect(study.parameters.values) {
        delay(300)
        onCommitParameterDrafts()
    }
    BackHandler(enabled = showingResults) { showingResults = false }

    val calculate = {
        val mode = parseActiveMode(settings.activeMode, stepMin, stepMax, stepSize, countMin, countMax, pointCount, customValues)
        if (mode == null) {
            validationMessage = "Saisie invalide : vérifiez les abscisses et le pas."
        } else {
            val candidate = settings.copy(generationMode = mode, retainedModeInputs = settings.retainedModeInputs.with(mode))
            val error = candidate.validationError()
            val emptySelected = candidate.selectedSlots.firstOrNull { study.slot(it).validatedExpression == null }
            when {
                error != null -> validationMessage = validationMessage(error)
                emptySelected != null -> validationMessage = "${emptySelected.displayName} ne contient aucune expression validée."
                !onSaveSettings(candidate) -> validationMessage = "Les réglages n’ont pas pu être sauvegardés."
                else -> {
                    settings = candidate
                    validationMessage = null
                    onCalculationRequested(study.copyWithSettings(candidate))
                    showingResults = true
                }
            }
        }
    }

    Scaffold(
        modifier = Modifier.testTag("value-table-screen"),
        topBar = {
            TopAppBar(
                title = {
                    Text("Tableau de valeurs", maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("value-table-back")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour aux fonctions")
                    }
                },
                actions = {
                    if (showingResults) {
                        Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.padding(end = 4.dp)) {
                            Text(study.angleMode.name, Modifier.padding(horizontal = 10.dp, vertical = 5.dp).testTag("value-table-angle-indicator"), style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    IconButton(onClick = {
                        if (showingResults) {
                            showingResults = false
                            showPresentation = true
                        } else showPresentation = !showPresentation
                    }, modifier = Modifier.testTag("value-table-presentation-toggle")) {
                        Icon(Icons.Default.Settings, contentDescription = if (showingResults) "Paramètres du tableau" else "Réglages de présentation")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 10.dp).imePadding(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (showingResults) {
                FunctionLegend(study, calculation, Modifier.fillMaxWidth().heightIn(min = 48.dp))
                TableResults(calculation, settings, Modifier.weight(1f).fillMaxWidth(), horizontalState)
                ResultActions(
                    onCopy = {
                        calculation.result?.let { result ->
                            clipboard.setText(AnnotatedString(FunctionValueTablePresentation.toClipboardText(result, settings.decimalPlaces, settings.scientificNotation)))
                            scope.launch { snackbar.showSnackbar("Tableau copié") }
                        }
                    },
                    onFunctionSettings = { showingResults = false; showFunctionSettings = true },
                    onOpenGraph = onOpenGraph
                )
            } else {
                Card(Modifier.weight(1f).fillMaxWidth().testTag("value-table-configuration")) {
                    Column(
                        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "Choisissez les abscisses et les fonctions à évaluer.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                            ModeButton("Intervalle\net pas", settings.activeMode == FunctionValueTableModeId.INTERVAL_AND_STEP, "value-table-mode-step", Modifier.weight(1f)) {
                                settings = settings.selectMode(FunctionValueTableModeId.INTERVAL_AND_STEP)
                                validationMessage = null
                            }
                            ModeButton("Intervalle\net points", settings.activeMode == FunctionValueTableModeId.INTERVAL_AND_COUNT, "value-table-mode-count", Modifier.weight(1f)) {
                                settings = settings.selectMode(FunctionValueTableModeId.INTERVAL_AND_COUNT)
                                validationMessage = null
                            }
                            ModeButton("Valeurs\npersonnalisées", settings.activeMode == FunctionValueTableModeId.CUSTOM_VALUES, "value-table-mode-custom", Modifier.weight(1f)) {
                                settings = settings.selectMode(FunctionValueTableModeId.CUSTOM_VALUES)
                                validationMessage = null
                            }
                        }
                        when (settings.activeMode) {
                            FunctionValueTableModeId.INTERVAL_AND_STEP -> Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                NumberField("X min", stepMin, { stepMin = it }, Modifier.weight(1f), "value-table-xmin")
                                NumberField("X max", stepMax, { stepMax = it }, Modifier.weight(1f), "value-table-xmax")
                                NumberField("Pas", stepSize, { stepSize = it }, Modifier.weight(1f), "value-table-step")
                            }
                            FunctionValueTableModeId.INTERVAL_AND_COUNT -> Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                NumberField("X min", countMin, { countMin = it }, Modifier.weight(1f), "value-table-count-xmin")
                                NumberField("X max", countMax, { countMax = it }, Modifier.weight(1f), "value-table-count-xmax")
                                NumberField("Points", pointCount, { pointCount = it }, Modifier.weight(1f), "value-table-point-count", integer = true)
                            }
                            FunctionValueTableModeId.CUSTOM_VALUES -> OutlinedTextField(
                                value = customValues,
                                onValueChange = { customValues = it },
                                modifier = Modifier.fillMaxWidth().testTag("value-table-custom-values"),
                                label = { Text("Abscisses (espace, ; ou retour à la ligne)") },
                                minLines = 2,
                                maxLines = 4,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Done)
                            )
                        }
                        Text(
                            "${generatedCountHint(settings.activeMode, stepMin, stepMax, stepSize, countMin, countMax, pointCount, customValues)} valeurs seront calculées.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        HorizontalDivider()
                        Text("Fonctions à évaluer", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        study.slots.forEach { slot ->
                            val selected = slot.id in settings.selectedSlots
                            Row(
                                Modifier.fillMaxWidth().testTag("value-table-function-${slot.id.code}"),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = selected,
                                    enabled = slot.validatedExpression != null,
                                    onCheckedChange = { checked ->
                                        val ids = settings.selectedSlots.toMutableSet().apply { if (checked) add(slot.id) else remove(slot.id) }
                                        settings = settings.copy(selectedSlots = ids)
                                    },
                                    modifier = Modifier.testTag("value-table-select-${slot.id.code}")
                                )
                                Box(Modifier.size(14.dp).background(Color(slot.color.argb.toInt()), RoundedCornerShape(4.dp)))
                                Text("${slot.id.displayName}  ${slot.validatedExpression ?: "Fonction vide"}", Modifier.weight(1f).padding(start = 8.dp), maxLines = 2, overflow = TextOverflow.Ellipsis, color = if (slot.validatedExpression == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
                            }
                        }
                        if (showPresentation) {
                            HorizontalDivider()
                            Text("Présentation", style = MaterialTheme.typography.titleSmall)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Décimales : ${settings.decimalPlaces}", Modifier.weight(1f))
                                TextButton(onClick = { settings = settings.copy(decimalPlaces = (settings.decimalPlaces - 1).coerceAtLeast(0)) }, modifier = Modifier.testTag("value-table-decimals-minus")) { Text("−") }
                                TextButton(onClick = { settings = settings.copy(decimalPlaces = (settings.decimalPlaces + 1).coerceAtMost(12)) }, modifier = Modifier.testTag("value-table-decimals-plus")) { Text("+") }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Notation scientifique", Modifier.weight(1f))
                                Switch(checked = settings.scientificNotation, onCheckedChange = { settings = settings.copy(scientificNotation = it) }, modifier = Modifier.testTag("value-table-scientific"))
                            }
                        }
                        OutlinedButton(
                            onClick = { showFunctionSettings = !showFunctionSettings },
                            modifier = Modifier.fillMaxWidth().testTag("value-table-function-settings-toggle")
                        ) {
                            Icon(Icons.Default.Tune, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Paramètres des fonctions · ${study.angleMode.name}")
                        }
                        if (showFunctionSettings) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Mode angulaire", Modifier.weight(1f))
                                OutlinedButton(
                                    onClick = { onAngleMode(if (study.angleMode == FunctionAngleMode.RAD) FunctionAngleMode.DEG else FunctionAngleMode.RAD) },
                                    modifier = Modifier.testTag("value-table-angle-toggle")
                                ) { Text(study.angleMode.name) }
                            }
                            study.parameters.values.toSortedMap().entries.chunked(2).forEach { pair ->
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                    pair.forEach { (name, value) ->
                                        NumberField(
                                            label = name.toString(),
                                            value = parameterDrafts[name] ?: inputNumber(value),
                                            onValueChange = { text ->
                                                parameterDrafts[name] = text
                                                text.toFiniteDoubleOrNull()?.let { onParameterDraft(name, it) }
                                            },
                                            modifier = Modifier.weight(1f),
                                            tag = "value-table-parameter-$name",
                                            imeAction = ImeAction.Done,
                                            onDone = onCommitParameterDrafts
                                        )
                                    }
                                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                                }
                            }
                        }
                        validationMessage?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.fillMaxWidth().testTag("value-table-validation-error")) }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = {
                        stepMin = "-2"; stepMax = "2"; stepSize = "0.5"
                        countMin = "-2"; countMax = "2"; pointCount = "9"; customValues = "0"
                        settings = FunctionValueTableSettings()
                        validationMessage = null
                    }, modifier = Modifier.weight(1f).heightIn(min = 52.dp).testTag("value-table-reset-settings")) { Text("Réinitialiser") }
                    Button(onClick = calculate, modifier = Modifier.weight(1f).heightIn(min = 52.dp).testTag("value-table-calculate")) { Text("Calculer") }
                }
            }
            if (uiState.saveError != null) Text(uiState.saveError, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("value-table-save-error"))
        }
    }
}

@Composable
private fun ModeButton(label: String, selected: Boolean, tag: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    val contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp)
    if (selected) {
        Button(onClick, modifier.heightIn(min = 48.dp).testTag(tag), shape = shape, contentPadding = contentPadding) {
            Text(label, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, maxLines = 2)
        }
    } else {
        OutlinedButton(onClick, modifier.heightIn(min = 48.dp).testTag(tag), shape = shape, contentPadding = contentPadding) {
            Text(label, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, maxLines = 2)
        }
    }
}

@Composable
private fun NumberField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    tag: String,
    integer: Boolean = false,
    imeAction: ImeAction = ImeAction.Next,
    onDone: () -> Unit = {}
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.testTag(tag),
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = if (integer) KeyboardType.Number else KeyboardType.Decimal, imeAction = imeAction),
        keyboardActions = KeyboardActions(onDone = { onDone() })
    )
}

@Composable
private fun TableResults(
    calculation: FunctionValueTableCalculationState,
    settings: FunctionValueTableSettings,
    modifier: Modifier,
    horizontalState: androidx.compose.foundation.ScrollState
) {
    val result = calculation.result
    if (result == null) {
        Column(modifier, verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            when (calculation.phase) {
                FunctionValueTableCalculationPhase.WAITING, FunctionValueTableCalculationPhase.CALCULATING -> Text("Calcul du tableau…", modifier = Modifier.testTag("value-table-calculating"))
                FunctionValueTableCalculationPhase.VALIDATION_ERROR -> Text(calculation.validationError?.let(::validationMessage) ?: "Réglages invalides", color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("value-table-validation-error-state"))
                FunctionValueTableCalculationPhase.CALCULATION_ERROR -> Text(calculation.calculationError ?: "Le calcul a échoué.", color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("value-table-calculation-error"))
                else -> Text("Configurez les abscisses, puis appuyez sur Calculer.")
            }
        }
        return
    }
    Column(modifier) {
        Row(Modifier.fillMaxWidth().height(42.dp), verticalAlignment = Alignment.CenterVertically) {
            TableCell("x", Modifier.width(82.dp), header = true)
            Row(Modifier.weight(1f).horizontalScroll(horizontalState)) {
                result.columns.forEach { column ->
                    Box(
                        modifier = Modifier.width(154.dp).height(42.dp).background(MaterialTheme.colorScheme.surfaceVariant)
                            .testTag("value-table-header-${column.slotId.code}"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            column.slotId.displayName,
                            Modifier.fillMaxWidth().testTag("value-table-header-label-${column.slotId.code}"),
                            maxLines = 1,
                            textAlign = TextAlign.Center,
                            color = Color(column.color.argb.toInt()),
                            fontWeight = FontWeight.SemiBold
                        )
                        Box(
                            Modifier.align(Alignment.Center).offset(x = (-38).dp).size(10.dp)
                                .background(Color(column.color.argb.toInt()), RoundedCornerShape(4.dp))
                        )
                    }
                }
            }
        }
        HorizontalDivider()
        LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("value-table-rows")) {
            itemsIndexed(result.abscissas, key = { index, _ -> index }) { index, x ->
                val rowColor = if (index % 2 == 0) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                Row(Modifier.fillMaxWidth().heightIn(min = 42.dp).background(rowColor), verticalAlignment = Alignment.CenterVertically) {
                    TableCell(FunctionValueTablePresentation.format(x, settings.decimalPlaces, settings.scientificNotation), Modifier.width(82.dp), numeric = true, header = true)
                    Row(Modifier.weight(1f).horizontalScroll(horizontalState)) {
                        result.columns.forEach { column ->
                            val cell = column.cells[index]
                            val text = FunctionValueTablePresentation.format(cell, settings.decimalPlaces, settings.scientificNotation)
                            TableCell(text, Modifier.width(154.dp), numeric = true, tint = when (cell) {
                                is com.example.calcstudiopro.functions.table.FunctionValueTableCell.Value -> MaterialTheme.colorScheme.onSurface
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }, tag = "value-table-cell-${column.slotId.code}-$index")
                        }
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
            }
        }
        HorizontalDivider()
        Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Text(
                "${result.abscissas.size} valeurs   |   ${result.columns.size} fonctions évaluées",
                Modifier.padding(horizontal = 14.dp, vertical = 12.dp).testTag("value-table-summary"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun FunctionLegend(
    study: FunctionStudy,
    calculation: FunctionValueTableCalculationState,
    modifier: Modifier = Modifier
) {
    val functions = calculation.result?.columns?.map { Triple(it.slotId, it.expression, it.color) }
        ?: study.valueTableSettings.selectedSlots.sortedBy { it.number }.mapNotNull { id ->
            study.slot(id).validatedExpression?.let { Triple(id, it, study.slot(id).color) }
        }
    Row(modifier.horizontalScroll(rememberScrollState()).padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        if (functions.isEmpty()) {
            Text("Aucune fonction sélectionnée", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        } else functions.forEach { (id, expression, color) ->
            Row(Modifier.padding(end = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).background(Color(color.argb.toInt()), RoundedCornerShape(50)))
                Text("${id.displayName} = $expression", Modifier.padding(start = 6.dp), maxLines = 1, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

@Composable
private fun ResultActions(
    onCopy: () -> Unit,
    onFunctionSettings: () -> Unit,
    onOpenGraph: () -> Unit
) {
    Card(Modifier.fillMaxWidth().padding(bottom = 4.dp).testTag("value-table-actions")) {
        Column(Modifier.padding(horizontal = 8.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Actions", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            HorizontalDivider()
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                ActionTile("Copier", Icons.Default.ContentCopy, "value-table-copy", onCopy, Modifier.weight(1f))
                ActionTile("Paramètres\nfonctions", Icons.Default.Tune, "value-table-open-function-settings", onFunctionSettings, Modifier.weight(1f))
                ActionTile("Graphique", Icons.Default.ShowChart, "value-table-open-graph", onOpenGraph, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ActionTile(label: String, icon: ImageVector, tag: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = 76.dp).testTag(tag),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center, modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun TableCell(
    text: String,
    modifier: Modifier = Modifier,
    numeric: Boolean = false,
    header: Boolean = false,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    tag: String? = null
) {
    Box(
        modifier = modifier.heightIn(min = 42.dp).background(if (header) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent).padding(horizontal = 8.dp, vertical = 8.dp),
        contentAlignment = if (numeric) Alignment.CenterEnd else Alignment.Center
    ) {
        Text(text, color = tint, maxLines = 1, fontWeight = if (header) FontWeight.SemiBold else FontWeight.Normal, modifier = if (tag != null) Modifier.testTag(tag) else Modifier)
    }
}

private fun parseActiveMode(
    id: FunctionValueTableModeId,
    stepMin: String,
    stepMax: String,
    stepSize: String,
    countMin: String,
    countMax: String,
    pointCount: String,
    custom: String
): FunctionValueTableGenerationMode? {
    return when (id) {
        FunctionValueTableModeId.INTERVAL_AND_STEP -> {
            val min = stepMin.toFiniteDoubleOrNull() ?: return null
            val max = stepMax.toFiniteDoubleOrNull() ?: return null
            val step = stepSize.toFiniteDoubleOrNull() ?: return null
            FunctionValueTableGenerationMode.IntervalAndStep(min, max, step)
        }
        FunctionValueTableModeId.INTERVAL_AND_COUNT -> {
            val min = countMin.toFiniteDoubleOrNull() ?: return null
            val max = countMax.toFiniteDoubleOrNull() ?: return null
            val count = pointCount.toIntOrNull() ?: return null
            FunctionValueTableGenerationMode.IntervalAndCount(min, max, count)
        }
        FunctionValueTableModeId.CUSTOM_VALUES -> FunctionValueTableGenerationMode.CustomValues(custom)
    }
}

private fun generatedCountHint(
    mode: FunctionValueTableModeId,
    stepMin: String,
    stepMax: String,
    step: String,
    countMin: String,
    countMax: String,
    count: String,
    custom: String
): String {
    return try {
        val generation = parseActiveMode(mode, stepMin, stepMax, step, countMin, countMax, count, custom) ?: return "—"
        when (val output = FunctionValueTableAbscissaGenerator.generate(generation)) {
            is com.example.calcstudiopro.functions.table.FunctionValueTableAbscissas.Valid -> output.values.size.toString()
            is com.example.calcstudiopro.functions.table.FunctionValueTableAbscissas.Invalid -> "—"
        }
    } catch (_: RuntimeException) { "—" }
}

private fun validationMessage(error: FunctionValueTableValidationError): String = when (error) {
    FunctionValueTableValidationError.InvalidDecimalPlaces -> "La précision doit être comprise entre 0 et 12 décimales."
    FunctionValueTableValidationError.EmptyCustomValues -> "Saisissez au moins une abscisse."
    FunctionValueTableValidationError.NonFiniteMinimum -> "X min doit être un nombre fini."
    FunctionValueTableValidationError.NonFiniteMaximum -> "X max doit être un nombre fini."
    FunctionValueTableValidationError.MinimumGreaterThanMaximum -> "X min doit être inférieur ou égal à X max."
    FunctionValueTableValidationError.NonFiniteStep -> "Le pas doit être un nombre fini."
    FunctionValueTableValidationError.NonPositiveStep -> "Le pas doit être strictement positif."
    FunctionValueTableValidationError.StepBelowDoubleResolution -> "Le pas est trop petit pour distinguer les abscisses à cette échelle."
    FunctionValueTableValidationError.InvalidPointCount -> "Le nombre de points doit être compris entre 1 et 1 000."
    FunctionValueTableValidationError.TooManyRows -> "La génération dépasse la limite de 1 000 lignes. Modifiez les réglages."
    is FunctionValueTableValidationError.MalformedCustomValue -> "L’abscisse ${error.oneBasedIndex} (« ${error.text} ») n’est pas un nombre valide."
    is FunctionValueTableValidationError.NonFiniteCustomValue -> "L’abscisse ${error.oneBasedIndex} n’est pas finie."
    is FunctionValueTableValidationError.NonFiniteGeneratedAbscissa -> "L’abscisse ${error.oneBasedIndex} n’est pas représentable."
    is FunctionValueTableValidationError.EmptySelectedSlot -> "${error.slotId.displayName} ne contient aucune expression validée."
}

private fun modeLabel(mode: FunctionValueTableModeId): String = when (mode) {
    FunctionValueTableModeId.INTERVAL_AND_STEP -> "Intervalle et pas"
    FunctionValueTableModeId.INTERVAL_AND_COUNT -> "Intervalle et points"
    FunctionValueTableModeId.CUSTOM_VALUES -> "Valeurs personnalisées"
}

private fun String.toFiniteDoubleOrNull(): Double? = replace(',', '.').toDoubleOrNull()?.takeIf(Double::isFinite)
private fun inputNumber(value: Double): String = String.format(Locale.ROOT, "%s", value)
private val FunctionSlotId.displayName: String get() = "f$number(x)"
private fun FunctionStudy.copyWithSettings(settings: FunctionValueTableSettings): FunctionStudy = withValueTableSettings(settings)
