package com.example.calcstudiopro.statistics

import android.widget.Toast
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Surface
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Save
import com.example.calcstudiopro.ui.components.bringFocusedFieldIntoView

@Composable
fun StatisticsRoute(viewModel: StatisticsViewModel, onOpenModeSelector: () -> Unit) {
    val state by viewModel.state.collectAsState()
    StatisticsScreen(state, onOpenModeSelector, viewModel)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticsScreen(state: StatisticsState, onOpenModeSelector: () -> Unit, viewModel: StatisticsViewModel) {
    var showSaveDialog by remember { mutableStateOf(false) }
    var saveAs by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var pendingOpen by remember { mutableStateOf<Long?>(null) }
    var pendingOpenDimension by remember { mutableStateOf(StatisticsDimension.ONE_D) }
    var openName by remember { mutableStateOf("") }
    var deleteId by remember { mutableStateOf<Long?>(null) }
    var itemActionId by remember { mutableStateOf<Long?>(null) }
    var itemAction by remember { mutableStateOf("") }
    var itemName by remember { mutableStateOf("") }
    var itemDescription by remember { mutableStateOf("") }
    var pasteText by remember { mutableStateOf<String?>(null) }
    var replacePaste by remember { mutableStateOf(false) }
    var confirmReplacePaste by remember { mutableStateOf(false) }
    var search by remember { mutableStateOf("") }
    var libraryFilter by remember { mutableStateOf("Toutes") }
    var showMoreMenu by remember { mutableStateOf(false) }
    var showAllValues by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    val isOneD = state.dimension == StatisticsDimension.ONE_D
    val values = state.oneDValues
    val oneD = StatisticsEngine.describe(values)
    val twoD = state.twoDResult
    val fit = state.selectedFit.fit

    Scaffold(topBar = {
        TopAppBar(title = { Text(when (state.page) { StatisticsPage.DASHBOARD -> "Statistiques"; StatisticsPage.EDITOR -> "Éditeur ${if (isOneD) "1D" else "2D"}"; StatisticsPage.LIBRARY -> "Bibliothèque" }, maxLines = 1) }, actions = {
            if (state.page == StatisticsPage.DASHBOARD) {
                IconButton(onClick = { viewModel.navigate(StatisticsPage.LIBRARY) }, modifier = Modifier.semantics { contentDescription = "Ouvrir la bibliothèque" }) {
                    Icon(Icons.Filled.FolderOpen, contentDescription = null)
                }
                IconButton(onClick = { showSaveDialog = true; saveAs = false; name = ""; description = "" }, modifier = Modifier.semantics { contentDescription = "Enregistrer la série" }) {
                    Icon(Icons.Filled.Save, contentDescription = null)
                }
                Box {
                    IconButton(onClick = { showMoreMenu = true }, modifier = Modifier.semantics { contentDescription = "Autres actions" }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = null)
                    }
                    DropdownMenu(expanded = showMoreMenu, onDismissRequest = { showMoreMenu = false }) {
                        DropdownMenuItem(text = { Text("Enregistrer sous") }, onClick = {
                            showMoreMenu = false; showSaveDialog = true; saveAs = true
                            name = state.library.firstOrNull { it.id == state.sourceSeriesId }?.name?.plus(" (copie)").orEmpty(); description = ""
                        })
                    }
                }
                TextButton(onClick = onOpenModeSelector, modifier = Modifier.testTag("statistics-mode")) { Text("MODE", maxLines = 1, softWrap = false) }
            }
        })
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (state.page == StatisticsPage.DASHBOARD) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChoiceButton("1D", isOneD, Modifier.weight(1f)) { viewModel.selectDimension(StatisticsDimension.ONE_D) }
                    ChoiceButton("2D", !isOneD, Modifier.weight(1f)) { viewModel.selectDimension(StatisticsDimension.TWO_D) }
                }
                if (isOneD) DashboardOneD(state, oneD, viewModel, Modifier.weight(1f)) { showAllValues = true }
                else DashboardTwoD(state, twoD, fit, viewModel, Modifier.weight(1f)) { showAllValues = true }
            } else if (state.page == StatisticsPage.EDITOR) {
                Editor(state, viewModel, clipboard, pasteText = { pasteText = it }, onFinish = viewModel::finishEditing)
            } else {
                Library(state, viewModel, search, { search = it }, onOpen = { id ->
                    val item = state.library.firstOrNull { it.id == id } ?: return@Library
                    val targetDimension = if (item.dimension == StatisticsDimension.TWO_D.name) StatisticsDimension.TWO_D else StatisticsDimension.ONE_D
                    val targetDirty = if (targetDimension == StatisticsDimension.ONE_D) state.oneDDirty else state.twoDDirty
                    if (targetDirty) { pendingOpen = id; pendingOpenDimension = targetDimension; openName = "" }
                    else viewModel.openSeries(id)
                }, onDelete = { deleteId = it }, onItemAction = { id, action ->
                    val item = state.library.firstOrNull { it.id == id } ?: return@Library
                    itemActionId = id; itemAction = action
                    itemName = if (action == "duplicate") "${item.name} (copie)" else item.name
                    itemDescription = item.description
                }, filter = libraryFilter, onFilter = { libraryFilter = it })
            }
            state.storageError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            state.saveError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        }
    }

    if (showSaveDialog) AlertDialog(onDismissRequest = { showSaveDialog = false }, title = { Text(if (saveAs) "Enregistrer sous" else "Enregistrer la série") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(name, { name = it }, label = { Text("Nom obligatoire") }, singleLine = true)
            OutlinedTextField(description, { description = it }, label = { Text("Description (facultative)") })
            if (state.sourceSeriesId != null && !saveAs) Text("La série enregistrée sera mise à jour uniquement après cette confirmation.", style = MaterialTheme.typography.bodySmall)
        } }, confirmButton = { TextButton(onClick = { viewModel.saveLibrary(name, description, saveAs); showSaveDialog = false }) { Text("Enregistrer") } },
        dismissButton = { TextButton(onClick = { showSaveDialog = false }) { Text("Annuler") } })

    if (pendingOpen != null) AlertDialog(onDismissRequest = { pendingOpen = null }, title = { Text("Session modifiée") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Cette session contient des modifications non enregistrées. L’ouverture remplacera les données de la session ${if (pendingOpenDimension == StatisticsDimension.ONE_D) "1D" else "2D"}.")
            val sourceId = if (pendingOpenDimension == StatisticsDimension.ONE_D) state.oneDSourceSeriesId else state.twoDSourceSeriesId
            if (sourceId == null) OutlinedTextField(openName, { openName = it }, label = { Text("Nom pour enregistrer cette session") })
        } }, confirmButton = { TextButton(onClick = { viewModel.saveCurrentAndOpen(pendingOpen!!, openName); pendingOpen = null }) { Text("Enregistrer et ouvrir") } },
        dismissButton = { Row {
            TextButton(onClick = { viewModel.openSeries(pendingOpen!!, discardDirty = true); pendingOpen = null }) { Text("Ouvrir sans enregistrer") }
            TextButton(onClick = { pendingOpen = null }) { Text("Annuler") }
        } })

    if (deleteId != null) AlertDialog(onDismissRequest = { deleteId = null }, title = { Text("Supprimer la série ?") },
        text = { Text("Cette suppression de la bibliothèque est définitive.") },
        confirmButton = { TextButton(onClick = { viewModel.deleteSeries(deleteId!!); deleteId = null }) { Text("Supprimer") } },
        dismissButton = { TextButton(onClick = { deleteId = null }) { Text("Annuler") } })

    if (itemActionId != null) AlertDialog(onDismissRequest = { itemActionId = null }, title = { Text(if (itemAction == "duplicate") "Dupliquer la série" else "Renommer la série") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(itemName, { itemName = it }, label = { Text("Nom obligatoire") })
            OutlinedTextField(itemDescription, { itemDescription = it }, label = { Text("Description") })
        } }, confirmButton = { TextButton(onClick = {
            if (itemAction == "duplicate") viewModel.duplicateSeries(itemActionId!!, itemName, itemDescription)
            else viewModel.renameSeries(itemActionId!!, itemName, itemDescription)
            itemActionId = null
        }) { Text(if (itemAction == "duplicate") "Dupliquer" else "Renommer") } },
        dismissButton = { TextButton(onClick = { itemActionId = null }) { Text("Annuler") } })

    if (pasteText != null) {
        val validation = when {
            !isOneD -> StatisticsInputParser.twoD(pasteText!!)
            state.oneDGrouped -> StatisticsInputParser.frequencies(pasteText!!)
            else -> StatisticsInputParser.oneD(pasteText!!)
        }
        val validPaste = validation is StatisticsInputParser.Parsed.Valid
        val invalidPaste = validation as? StatisticsInputParser.Parsed.Invalid
        val incomingCount = when {
            !isOneD -> pasteText!!.lines().count { it.isNotBlank() } - if (pasteText!!.lineSequence().firstOrNull()?.trim()?.equals("x\ty", true) == true) 1 else 0
            state.oneDGrouped -> (StatisticsInputParser.frequencies(pasteText!!) as? StatisticsInputParser.Parsed.Valid)?.value?.sumOf { it.count } ?: 0
            else -> (StatisticsInputParser.oneD(pasteText!!) as? StatisticsInputParser.Parsed.Valid)?.value?.size ?: 0
        }
        AlertDialog(onDismissRequest = { pasteText = null }, title = { Text("Aperçu du collage") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(pasteText!!.lines().take(8).joinToString("\n"), style = MaterialTheme.typography.bodySmall)
                Text("${pasteText!!.lines().count { it.isNotBlank() }} ligne(s) détectée(s).")
                invalidPaste?.let { Text("${it.message}${it.line?.let { line -> " (ligne $line)" } ?: ""}", color = MaterialTheme.colorScheme.error) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { replacePaste = false }, enabled = replacePaste) { Text("Ajouter") }
                    OutlinedButton(onClick = { replacePaste = true }, enabled = !replacePaste) { Text("Remplacer") }
                }
            } }, confirmButton = { TextButton(enabled = validPaste, onClick = {
                if (replacePaste) confirmReplacePaste = true else {
                    if (isOneD) viewModel.pasteOneD(pasteText!!, replacePaste) else viewModel.pasteTwoD(pasteText!!, replacePaste)
                    pasteText = null; confirmReplacePaste = false
                }
            }) { Text(if (replacePaste) "Continuer" else "Ajouter les données") } },
            dismissButton = { TextButton(onClick = { pasteText = null; confirmReplacePaste = false }) { Text("Annuler") } })
        if (confirmReplacePaste) AlertDialog(onDismissRequest = { confirmReplacePaste = false }, title = { Text("Confirmer le remplacement") },
            text = { Text("Le collage remplacera ${if (isOneD) state.oneDValues.size.toString() + " observations" else state.twoDPoints.size.toString() + " couples"} par $incomingCount nouveau(x) élément(s).") },
            confirmButton = { TextButton(onClick = {
                if (isOneD) viewModel.pasteOneD(pasteText!!, true) else viewModel.pasteTwoD(pasteText!!, true)
                pasteText = null; confirmReplacePaste = false
            }) { Text("Confirmer le remplacement") } },
            dismissButton = { TextButton(onClick = { confirmReplacePaste = false }) { Text("Annuler") } })
    }
    if (showAllValues) ReadOnlyValuesDialog(state, onDismiss = { showAllValues = false })
}

@Composable
private fun DashboardOneD(state: StatisticsState, result: StatisticsEngine.Result?, viewModel: StatisticsViewModel, modifier: Modifier, onShowAll: () -> Unit) {
    val scroll = rememberScrollState()
    Column(modifier.verticalScroll(scroll), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionCard("DONNÉES · ${state.oneDValues.size} observations · ${state.oneDValues.distinct().size} valeurs distinctes") {
            if (state.oneDValues.isEmpty()) Text("Aucune donnée. Modifiez la série pour commencer.") else DataPreview(oneDPreviewEntries(state), onShowAll)
            TextButton(onClick = viewModel::openEditor) { Text("Modifier les données") }
            if (state.oneDDirty) Text("Session modifiée · non enregistrée dans la bibliothèque", color = MaterialTheme.colorScheme.tertiary)
        }
        if (result != null) {
            SectionCard("RÉSULTATS ESSENTIELS") {
                Metric("n", result.count.toString()); Metric("Moyenne", StatisticsEngine.format(result.mean), exactValue = result.mean); Metric("Médiane", StatisticsEngine.format(result.median), exactValue = result.median)
                Metric("Écart-type échantillon", result.sampleStandardDeviation?.let(StatisticsEngine::format) ?: "Non défini", exactValue = result.sampleStandardDeviation)
                Metric("Minimum / Maximum", "${StatisticsEngine.format(result.minimum)} / ${StatisticsEngine.format(result.maximum)}")
                Metric("CV échantillon", result.sampleCoefficientOfVariation?.let { "${StatisticsEngine.format(it)} %" } ?: "Non défini")
            }
            CollapsibleSection("INDICATEURS COMPLÉMENTAIRES") {
                Metric("Somme", StatisticsEngine.format(result.sum), exactValue = result.sum); Metric("Étendue", result.range?.let(StatisticsEngine::format) ?: "Indisponible (dépassement numérique)")
                Metric("Variance population", StatisticsEngine.format(result.populationVariance), exactValue = result.populationVariance); Metric("Variance échantillon", result.sampleVariance?.let(StatisticsEngine::format) ?: "Non défini", exactValue = result.sampleVariance)
                Metric("Écart-type population", StatisticsEngine.format(result.populationStandardDeviation), exactValue = result.populationStandardDeviation)
                Metric("Modes", if (result.hasDominantMode) result.modes.joinToString(", ", transform = StatisticsEngine::format) else "Aucun mode dominant")
                Metric("Q1 / Q2 / Q3", "${StatisticsEngine.format(result.q1)} / ${StatisticsEngine.format(result.median)} / ${StatisticsEngine.format(result.q3)}")
                Metric("IQR", result.interquartileRange?.let(StatisticsEngine::format) ?: "Indisponible (dépassement numérique)")
                Metric("CV population", result.populationCoefficientOfVariation?.let { "${StatisticsEngine.format(it)} %" } ?: if (result.mean == 0.0) "Non défini (moyenne nulle)" else "Indisponible")
                Metric("Asymétrie", result.skewness?.let(StatisticsEngine::format) ?: "Indisponible (n ≥ 3 et dispersion requise)")
                Metric("Kurtosis excédentaire", result.excessKurtosis?.let(StatisticsEngine::format) ?: "Indisponible (n ≥ 4 et dispersion requise)")
            }
            SectionCard("PERCENTILE PERSONNALISÉ · TYPE 7") {
                OutlinedTextField(state.percentileInput, viewModel::setPercentile, label = { Text("Percentile P0 à P100") }, singleLine = true,
                    modifier = Modifier.bringFocusedFieldIntoView())
                val p = state.percentileInput.toDoubleOrNull()
                val value = p?.let { StatisticsEngine.quantile(state.oneDValues, it) }
                Text(value?.let { "Résultat : ${StatisticsEngine.format(it)}" } ?: "Saisissez un percentile entre 0 et 100.")
                value?.let { Metric("Percentile", StatisticsEngine.format(it), exactValue = it) }
            }
            TextButton(onClick = viewModel::save, modifier = Modifier.fillMaxWidth().testTag("statistics-save")) { Text("Ajouter ce résultat à l’historique") }
        }
        OutlinedButton(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) { Text("Graphiques · disponibles au lot 6") }
    }
}

@Composable
private fun DashboardTwoD(state: StatisticsState, result: Statistics2DEngine.Result?, fit: Statistics2DEngine.Fit?, viewModel: StatisticsViewModel, modifier: Modifier, onShowAll: () -> Unit) {
    var modelExpanded by remember { mutableStateOf(false) }
    var comparisonExpanded by remember { mutableStateOf(false) }
    var copyColumn by remember { mutableStateOf<Boolean?>(null) }
    val scroll = rememberScrollState()
    Column(modifier.verticalScroll(scroll), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionCard("DONNÉES · ${state.twoDPoints.size} couples · ${state.twoDPoints.distinct().size} couples distincts") {
            if (state.twoDPoints.isEmpty()) Text("Aucune donnée. Modifiez la série pour commencer.") else DataPreview(state.twoDPoints.map { "(${StatisticsEngine.format(it.x)} ; ${StatisticsEngine.format(it.y)})" }, onShowAll)
            Row {
                TextButton(onClick = viewModel::openEditor) { Text("Modifier") }
                TextButton(onClick = { if (state.oneDDirty) copyColumn = true else viewModel.copyColumnToOneD(true, confirm = true) }) { Text("Copier X vers 1D") }
                TextButton(onClick = { if (state.oneDDirty) copyColumn = false else viewModel.copyColumnToOneD(false, confirm = true) }) { Text("Copier Y vers 1D") }
            }
            if (state.twoDDirty) Text("Session modifiée · non enregistrée dans la bibliothèque", color = MaterialTheme.colorScheme.tertiary)
        }
        result?.let {
            SectionCard("RÉSULTATS ESSENTIELS") {
                Metric("n", it.count.toString()); Metric("Moyenne X", StatisticsEngine.format(it.x.mean), exactValue = it.x.mean); Metric("Moyenne Y", StatisticsEngine.format(it.y.mean), exactValue = it.y.mean)
                Metric("Pearson r", it.pearson?.let(StatisticsEngine::format) ?: "Non défini", exactValue = it.pearson)
                Metric("Spearman ρ", it.spearman?.let(StatisticsEngine::format) ?: "Non défini", exactValue = it.spearman)
                Metric("Covariance population", it.covariancePopulation?.let(StatisticsEngine::format) ?: "Non définie", exactValue = it.covariancePopulation)
                Metric("Covariance échantillon", it.covarianceSample?.let(StatisticsEngine::format) ?: "Non définie", exactValue = it.covarianceSample)
            }
            CollapsibleSection("STATISTIQUES X / Y") {
                Metric("Variance population X / Y", "${StatisticsEngine.format(it.x.populationVariance)} / ${StatisticsEngine.format(it.y.populationVariance)}")
                Metric("Variance échantillon X / Y", "${it.x.sampleVariance?.let(StatisticsEngine::format) ?: "—"} / ${it.y.sampleVariance?.let(StatisticsEngine::format) ?: "—"}")
                Metric("Covariance population", it.covariancePopulation?.let(StatisticsEngine::format) ?: "Non définie")
            }
        }
        SectionCard("RÉGRESSION") {
            TextButton(onClick = { modelExpanded = true }) { Text("Modèle : ${modelLabel(state.model)} ▾") }
            DropdownMenu(expanded = modelExpanded, onDismissRequest = { modelExpanded = false }) {
                DropdownMenuItem(text = { Text("Linéaire") }, onClick = { viewModel.setModel(Statistics2DEngine.Family.LINEAR); modelExpanded = false })
                (2..5).forEach { degree -> DropdownMenuItem(text = { Text("Polynôme degré $degree") }, onClick = { viewModel.setModel(Statistics2DEngine.Family.POLYNOMIAL, degree); modelExpanded = false }) }
                listOf(Statistics2DEngine.Family.EXPONENTIAL, Statistics2DEngine.Family.LOGARITHMIC, Statistics2DEngine.Family.POWER).forEach { family ->
                    DropdownMenuItem(text = { Text(modelLabel(Statistics2DEngine.Model(family))) }, onClick = { viewModel.setModel(family); modelExpanded = false })
                }
            }
            if (state.fitLoading) Text("Calcul du modèle…")
            else if (fit == null) Text(state.selectedFit.unavailableReason ?: "Modèle indisponible", color = MaterialTheme.colorScheme.error)
            else {
                Metric("Équation", fit.equation())
                Metric("Coefficients", fit.coefficients.joinToString(" · ") { StatisticsEngine.format(it) })
                Metric("Paramètres estimés", "${fit.parameterCount}, constante incluse")
                Metric("R²", fit.rSquared?.let(StatisticsEngine::format) ?: "Non défini (Y constant)")
                Metric("R² ajusté", fit.adjustedRSquared?.let(StatisticsEngine::format) ?: "Indisponible (effectif insuffisant)")
                Metric("RMSE (Y original)", StatisticsEngine.format(fit.rmse))
            }
            TextButton(onClick = { comparisonExpanded = !comparisonExpanded; if (!comparisonExpanded) viewModel.compareModels() }) { Text(if (comparisonExpanded) "▾ Comparer les modèles" else "▸ Comparer les modèles") }
            if (comparisonExpanded) {
                Text(state.recommendationMessage, style = MaterialTheme.typography.bodySmall)
                if (state.comparisonLoading) Text("Calcul de la validation croisée…")
                state.comparison.forEach { comparison ->
                    val comparisonFit = comparison.fitResult.fit
                    SectionCard(modelLabel(comparison.model)) {
                        if (comparisonFit == null) Text("Modèle non admissible : ${comparison.fitResult.unavailableReason ?: "ajustement indisponible"}")
                        else {
                            Metric("R² ajusté", comparisonFit.adjustedRSquared?.let(StatisticsEngine::format) ?: "Indisponible")
                            Metric("RMSE (Y original)", StatisticsEngine.format(comparisonFit.rmse))
                            Metric("RMSE LOOCV", comparison.loocvRmse?.let(StatisticsEngine::format)
                                ?: comparison.loocvUnavailableReason ?: "Indisponible")
                        }
                    }
                }
                Text("Aide heuristique au choix du modèle ; elle ne garantit pas la qualité prédictive.", style = MaterialTheme.typography.bodySmall)
            }
            androidx.compose.material3.HorizontalDivider()
            Text("PRÉDICTION")
            OutlinedTextField(state.predictionInput, viewModel::setPrediction, label = { Text("Valeur X") }, singleLine = true,
                modifier = Modifier.bringFocusedFieldIntoView())
            val x = StatisticsEngine.parseNumber(state.predictionInput)
            val y = x?.let { fit?.predict(it) }
            if (y != null) {
                val extrapolation = state.twoDPoints.isNotEmpty() && (x!! < state.twoDPoints.minOf { it.x } || x > state.twoDPoints.maxOf { it.x })
                Text("Y estimé : ${StatisticsEngine.format(y)}${if (extrapolation) " · Attention : extrapolation hors de l’intervalle observé." else ""}")
            } else if (state.predictionInput.isNotBlank()) Text("Prédiction indisponible pour cette valeur.", color = MaterialTheme.colorScheme.error)
        }
        OutlinedButton(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) { Text("Graphiques · disponibles au lot 6") }
    }
    if (copyColumn != null) AlertDialog(onDismissRequest = { copyColumn = null }, title = { Text("Remplacer la session 1D ?") },
        text = { Text("La session 1D contient des modifications non enregistrées. Copier cette colonne la remplacera.") },
        confirmButton = { TextButton(onClick = { viewModel.copyColumnToOneD(copyColumn!!, confirm = true); copyColumn = null }) { Text("Remplacer la session 1D") } },
        dismissButton = { TextButton(onClick = { copyColumn = null }) { Text("Annuler") } })
}

@Composable
private fun Editor(state: StatisticsState, viewModel: StatisticsViewModel, clipboard: androidx.compose.ui.platform.ClipboardManager,
    pasteText: (String) -> Unit, onFinish: () -> Unit) {
    var quickInput by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (state.dimension == StatisticsDimension.ONE_D) {
            Text("Présentation 1D")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChoiceButton("Observations", !state.oneDGrouped) { viewModel.setOneDGrouped(false) }
                ChoiceButton("Valeur + effectif", state.oneDGrouped) { viewModel.setOneDGrouped(true) }
            }
        }
        Text(if (state.dimension == StatisticsDimension.ONE_D) if (state.oneDGrouped) "Valeur ; effectif · effectifs entiers positifs" else "Session 1D · une valeur par ligne" else "Session 2D · colonnes X et Y séparées par tabulation")
        val tableText = when { state.dimension == StatisticsDimension.TWO_D -> state.twoDText; state.oneDGrouped -> state.oneDFrequencyText; else -> state.oneDText }
        DataRows(tableText, state.dimension, state.oneDGrouped, Modifier.weight(1f).testTag("statistics-editor")) { updated ->
            when { state.dimension == StatisticsDimension.TWO_D -> viewModel.editTwoD(updated); state.oneDGrouped -> viewModel.editFrequencyText(updated); else -> viewModel.editOneD(updated) }
        }
        OutlinedTextField(quickInput, { quickInput = it }, label = { Text("Saisie rapide") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { clipboard.getText()?.text?.let(pasteText) }) { Text("Coller") }
            OutlinedButton(onClick = {
                if (state.dimension == StatisticsDimension.ONE_D && state.oneDGrouped) viewModel.editFrequencyText(listOf(state.oneDFrequencyText, quickInput).filter(String::isNotBlank).joinToString("\n"))
                else if (state.dimension == StatisticsDimension.ONE_D) viewModel.editOneD(listOf(state.oneDText, quickInput).filter(String::isNotBlank).joinToString("\n"))
                else viewModel.editTwoD(listOf(state.twoDText, quickInput).filter(String::isNotBlank).joinToString("\n"))
                quickInput = ""
            }) { Text("Ajouter la saisie") }
        }
        state.pasteUndo?.let { TextButton(onClick = viewModel::undoPaste) { Text("Annuler le collage") } }
        state.storageError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(onClick = onFinish, modifier = Modifier.fillMaxWidth()) { Text("Terminer") }
    }
}

@Composable
private fun DataPreview(items: List<String>, onShowAll: () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val itemsPerLine = (maxWidth / 64.dp).toInt().coerceAtLeast(1)
        val limit = (itemsPerLine * 5).coerceIn(4, 20)
        Text(items.take(limit).joinToString(" · "), maxLines = 5, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
    }
    TextButton(onClick = onShowAll, modifier = Modifier.fillMaxWidth()) { Text("Voir toutes les valeurs") }
}

private fun oneDPreviewEntries(state: StatisticsState): List<String> {
    val frequencies = state.oneDValues.groupingBy { it }.eachCount()
    val compressed = state.oneDGrouped || frequencies.values.any { it > 1 }
    return if (compressed) frequencies.entries.map { "(${StatisticsEngine.format(it.key)} ; ${it.value})" }
    else state.oneDValues.map(StatisticsEngine::format)
}

@Composable
private fun ReadOnlyValuesDialog(state: StatisticsState, onDismiss: () -> Unit) {
    val isOneD = state.dimension == StatisticsDimension.ONE_D
    val rows = if (isOneD) {
        if (state.oneDGrouped) state.oneDValues.groupingBy { it }.eachCount().entries.map { "(${StatisticsEngine.format(it.key)} ; ${it.value})" }
        else state.oneDValues.map(StatisticsEngine::format)
    } else state.twoDPoints.map { "(${StatisticsEngine.format(it.x)} ; ${StatisticsEngine.format(it.y)})" }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth().padding(horizontal = 24.dp).heightIn(max = 560.dp), shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface, tonalElevation = 6.dp) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Text(if (isOneD) "Toutes les valeurs · ${state.oneDValues.size} observations" else "Tous les couples · ${state.twoDPoints.size} couples",
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    itemsIndexed(rows) { index, value -> Text("${index + 1}.  $value", style = MaterialTheme.typography.bodyMedium) }
                }
                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Fermer") }
            }
        }
    }
}

@Composable
private fun DataRows(text: String, dimension: StatisticsDimension, grouped: Boolean, modifier: Modifier, onChange: (String) -> Unit) {
    val rows = (if (text.isEmpty()) listOf("") else text.split('\n')).toMutableList()
    Column(modifier) {
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("#", modifier = Modifier.padding(8.dp))
            Text(if (dimension == StatisticsDimension.TWO_D) "X" else "Valeur", modifier = Modifier.weight(1f))
            if (dimension == StatisticsDimension.TWO_D || grouped) Text(if (dimension == StatisticsDimension.TWO_D) "Y" else "Effectif", modifier = Modifier.weight(1f))
            Text("", modifier = Modifier.padding(horizontal = 20.dp))
        }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            itemsIndexed(rows, key = { index, _ -> index }) { index, row ->
                val cells = when {
                    dimension == StatisticsDimension.TWO_D -> row.split('\t').let { if (it.size > 1) it else row.split(';') }
                    grouped -> row.split(';').let { if (it.size > 1) it else row.split('\t') }
                    else -> listOf(row)
                }
                var first by remember(row, index) { mutableStateOf(cells.getOrElse(0) { "" }) }
                var second by remember(row, index) { mutableStateOf(cells.getOrElse(1) { "" }) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text((index + 1).toString(), modifier = Modifier.padding(top = 16.dp))
                    OutlinedTextField(first, { first = it; rows[index] = if (dimension == StatisticsDimension.TWO_D) "$it\t$second" else if (grouped) "$it;$second" else it; onChange(rows.joinToString("\n")) },
                        label = { Text(if (dimension == StatisticsDimension.TWO_D) "X" else "Valeur") }, singleLine = true, modifier = Modifier.weight(1f).bringFocusedFieldIntoView(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    if (dimension == StatisticsDimension.TWO_D || grouped) OutlinedTextField(second, { second = it; rows[index] = if (dimension == StatisticsDimension.TWO_D) "$first\t$it" else "$first;$it"; onChange(rows.joinToString("\n")) },
                        label = { Text(if (dimension == StatisticsDimension.TWO_D) "Y" else "Effectif") }, singleLine = true, modifier = Modifier.weight(1f).bringFocusedFieldIntoView(),
                        keyboardOptions = KeyboardOptions(keyboardType = if (grouped) KeyboardType.Number else KeyboardType.Decimal))
                    TextButton(onClick = { val next = rows.toMutableList().also { it.removeAt(index) }; onChange(next.joinToString("\n")) }) { Text("Suppr.") }
                }
            }
        }
        TextButton(onClick = { onChange((rows + "").joinToString("\n")) }) { Text(if (dimension == StatisticsDimension.TWO_D) "+ Ajouter un couple" else "+ Ajouter une ligne") }
    }
}

@Composable
private fun Library(state: StatisticsState, viewModel: StatisticsViewModel, search: String, onSearch: (String) -> Unit,
    onOpen: (Long) -> Unit, onDelete: (Long) -> Unit, onItemAction: (Long, String) -> Unit, filter: String, onFilter: (String) -> Unit) {
    val filtered = state.library.filter { (it.name.contains(search, true) || it.description.contains(search, true)) && when (filter) {
        "1D" -> it.dimension == StatisticsDimension.ONE_D.name
        "2D" -> it.dimension == StatisticsDimension.TWO_D.name
        else -> true
    } }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(search, onSearch, label = { Text("Rechercher une série") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("Toutes", "1D", "2D").forEach { mode ->
            ChoiceButton(mode, filter == mode) { onFilter(mode) }
        } }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { viewModel.navigate(StatisticsPage.DASHBOARD) }) { Text("Retour") }
            Button(onClick = { viewModel.navigate(StatisticsPage.DASHBOARD) }) { Text("Enregistrer la session") }
        }
        if (filtered.isEmpty()) Text("Aucune série enregistrée.")
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(filtered, key = { it.id }) { item ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(item.name, fontWeight = FontWeight.SemiBold)
                        Text("${item.dimension.replace("ONE_D", "1D").replace("TWO_D", "2D")} · ${item.description} · modifiée ${java.text.DateFormat.getDateTimeInstance().format(java.util.Date(item.updatedAt))}")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { onOpen(item.id) }) { Text("Ouvrir") }
                            TextButton(onClick = { onItemAction(item.id, "rename") }) { Text("Renommer") }
                            TextButton(onClick = { onItemAction(item.id, "duplicate") }) { Text("Dupliquer") }
                            TextButton(onClick = { onDelete(item.id) }) { Text("Supprimer") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

@Composable
private fun CollapsibleSection(title: String, content: @Composable () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            TextButton(onClick = { expanded = !expanded }) { Text("${if (expanded) "▾" else "▸"} $title") }
            if (expanded) content()
        }
    }
}

@Composable
private fun Metric(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(value, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun Metric(label: String, value: String, exactValue: Double?) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        val clipboard = LocalClipboardManager.current
        val context = LocalContext.current
        Text(value, modifier = Modifier.weight(1f).then(if (exactValue != null) Modifier.combinedClickable(
            onClick = {},
            onLongClickLabel = "Copier la valeur avec sa précision complète",
            onLongClick = {
                clipboard.setText(AnnotatedString(exactValue.toString()))
                Toast.makeText(context, "Valeur copiée", Toast.LENGTH_SHORT).show()
            }
        ) else Modifier), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun ChoiceButton(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val colors = if (selected) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary)
    else ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurface)
    Button(onClick = onClick, modifier = modifier, colors = colors, shape = MaterialTheme.shapes.small) { Text(label, maxLines = 1) }
}

private fun modelLabel(model: Statistics2DEngine.Model) = when (model.family) {
    Statistics2DEngine.Family.LINEAR -> "Linéaire"
    Statistics2DEngine.Family.POLYNOMIAL -> "Polynôme degré ${model.degree}"
    Statistics2DEngine.Family.EXPONENTIAL -> "Exponentiel"
    Statistics2DEngine.Family.LOGARITHMIC -> "Logarithmique"
    Statistics2DEngine.Family.POWER -> "Puissance"
}
