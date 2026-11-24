package com.example.calcstudiopro.functions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SaveAs
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.calcstudiopro.functions.domain.FunctionStudyId
import com.example.calcstudiopro.functions.domain.FunctionStudy

private enum class StudyDialog { NONE, SAVE_AS, RENAME, OPEN, DISCARD, REPLACE_RECOVERY, DELETE, RECOVERY }
private enum class PendingStudyAction { NEW, OPEN, RESTORE, DELETE }

@Composable
fun FunctionStudyManagementRoute(viewModel: FunctionStudyViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsState()
    FunctionStudyManagementScreen(state, viewModel, onBack)
}

@Composable
fun FunctionStudyManagementScreen(
    state: FunctionStudyUiState,
    viewModel: FunctionStudyViewModel,
    onBack: () -> Unit
) {
    var dialog by remember { mutableStateOf(StudyDialog.NONE) }
    var pending by remember { mutableStateOf(PendingStudyAction.NEW) }
    var selectedStudyId by remember { mutableStateOf<FunctionStudyId?>(null) }
    var textValue by remember { mutableStateOf("") }
    var nameDialogIsSaveAs by remember { mutableStateOf(false) }
    var deleteConfirmed by remember { mutableStateOf(false) }

    fun execute(action: PendingStudyAction, discard: Boolean = false, replaceRecovery: Boolean = false) {
        pending = action
        val result = when (action) {
            PendingStudyAction.NEW -> viewModel.createNewStudy(discard, replaceRecovery)
            PendingStudyAction.OPEN -> selectedStudyId?.let { viewModel.openNamedStudy(it, discard, replaceRecovery) }
                ?: StudyManagementResult.Failed("Aucune étude sélectionnée.")
            PendingStudyAction.RESTORE -> viewModel.restoreRecovery(discard, replaceRecovery)
            PendingStudyAction.DELETE -> viewModel.deleteCurrentStudy(
                confirmDelete = deleteConfirmed,
                confirmDiscard = discard,
                replaceRecovery = replaceRecovery
            )
        }
        when (result) {
            StudyManagementResult.Completed -> { dialog = StudyDialog.NONE; deleteConfirmed = false }
            StudyManagementResult.RequiresName -> { textValue = ""; nameDialogIsSaveAs = false; dialog = StudyDialog.SAVE_AS }
            StudyManagementResult.RequiresDiscardConfirmation -> dialog = StudyDialog.DISCARD
            StudyManagementResult.RequiresRecoveryReplacementConfirmation -> dialog = StudyDialog.REPLACE_RECOVERY
            StudyManagementResult.RequiresDeleteConfirmation -> { deleteConfirmed = false; dialog = StudyDialog.DELETE }
            StudyManagementResult.RecoveryUnavailable -> dialog = StudyDialog.NONE
            is StudyManagementResult.Failed -> dialog = StudyDialog.NONE
        }
    }

    Scaffold { insets ->
        Column(
            Modifier.fillMaxSize().padding(insets).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, modifier = Modifier.testTag("studies-back")) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                }
                Text("Études", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            }

            StudyActionRow("Nouvelle étude", Icons.Default.Add, "study-new", enabled = true) {
                execute(PendingStudyAction.NEW)
            }
            StudyActionRow("Enregistrer", Icons.Default.Save, "study-save", enabled = true) {
                when (viewModel.saveCurrent()) {
                    StudyManagementResult.RequiresName -> { textValue = ""; nameDialogIsSaveAs = false; dialog = StudyDialog.SAVE_AS }
                    StudyManagementResult.Completed -> Unit
                    is StudyManagementResult.Failed -> Unit
                    else -> Unit
                }
            }
            StudyActionRow("Enregistrer sous…", Icons.Default.SaveAs, "study-save-as", enabled = state.study != null) {
                textValue = ""
                nameDialogIsSaveAs = true
                dialog = StudyDialog.SAVE_AS
            }
            StudyActionRow("Ouvrir une étude…", Icons.Default.FolderOpen, "study-open", enabled = true) {
                viewModel.refreshManagementInfo()
                dialog = StudyDialog.OPEN
            }
            StudyActionRow("Renommer l’étude…", Icons.Default.Edit, "study-rename", enabled = state.study?.name != null) {
                textValue = state.study?.name.orEmpty()
                dialog = StudyDialog.RENAME
            }
            StudyActionRow("Supprimer l’étude…", Icons.Default.Delete, "study-delete", enabled = state.study?.name != null) {
                execute(PendingStudyAction.DELETE)
            }

            Column(Modifier.fillMaxWidth().padding(top = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Étude actuelle", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Card(
                    modifier = Modifier.fillMaxWidth().testTag("study-current-card"),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                ) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(state.displayName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        val status = if (state.hasUnsavedChanges) "Modifications non enregistrées" else "Enregistrée"
                        Text(status, style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("study-current-status"))
                        state.study?.let { Text("${it.angleMode.name} • ${it.slots.count { slot -> slot.validatedExpression != null }} fonction(s)", style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }

            Column(Modifier.fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Récupération", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Card(
                    modifier = Modifier.fillMaxWidth().clickable(enabled = state.recoveryAvailable) {
                        if (viewModel.inspectRecovery() == StudyManagementResult.Completed) dialog = StudyDialog.RECOVERY
                    }.testTag("study-recovery-card"),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.Restore, contentDescription = null)
                            Text("Session précédente", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        }
                        Text(
                            if (state.recoveryAvailable) "Restaurer / examiner" else "Aucune session à récupérer",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            state.loadError?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("study-load-error")) }
            state.saveError?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("study-save-error")) }
            state.operationError?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("study-operation-error")) }
        }
    }

    when (dialog) {
        StudyDialog.SAVE_AS, StudyDialog.RENAME -> {
            val isRename = dialog == StudyDialog.RENAME
            AlertDialog(
                onDismissRequest = { dialog = StudyDialog.NONE },
                title = { Text(if (isRename) "Renommer l’étude" else "Enregistrer l’étude") },
                text = {
                    OutlinedTextField(
                        value = textValue,
                        onValueChange = { textValue = it },
                        singleLine = true,
                        label = { Text("Nom de l’étude") },
                        modifier = Modifier.testTag("study-name-input")
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        if (isRename) viewModel.renameCurrentStudy(textValue)
                        else if (nameDialogIsSaveAs) viewModel.saveAs(textValue)
                        else viewModel.saveCurrent(textValue)
                        dialog = StudyDialog.NONE
                    }, enabled = textValue.isNotBlank(), modifier = Modifier.testTag("study-name-confirm")) { Text("Valider") }
                },
                dismissButton = { TextButton(onClick = { dialog = StudyDialog.NONE }) { Text("Annuler") } }
            )
        }
        StudyDialog.OPEN -> {
            AlertDialog(
                onDismissRequest = { dialog = StudyDialog.NONE },
                title = { Text("Ouvrir une étude") },
                text = {
                    if (state.namedStudies.isEmpty()) Text("Aucune étude enregistrée.")
                    else Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        state.namedStudies.forEach { study ->
                            TextButton(onClick = {
                                selectedStudyId = study.id
                                execute(PendingStudyAction.OPEN)
                            }, modifier = Modifier.fillMaxWidth().testTag("study-open-${study.id.value}")) {
                                Text(study.name, modifier = Modifier.fillMaxWidth())
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = { TextButton(onClick = { dialog = StudyDialog.NONE }) { Text("Fermer") } }
            )
        }
        StudyDialog.DISCARD -> AlertDialog(
            onDismissRequest = { dialog = StudyDialog.NONE },
            title = { Text("Modifications non enregistrées") },
            text = { Text("La session précédente sera conservée dans la récupération avant le changement d’étude.") },
            confirmButton = { Button(onClick = { execute(pending, discard = true) }, modifier = Modifier.testTag("study-discard-confirm")) { Text("Continuer") } },
            dismissButton = { TextButton(onClick = { dialog = StudyDialog.NONE; deleteConfirmed = false }) { Text("Annuler") } }
        )
        StudyDialog.REPLACE_RECOVERY -> AlertDialog(
            onDismissRequest = { dialog = StudyDialog.NONE },
            title = { Text("Récupération existante") },
            text = { Text("Une session précédente existe déjà. La remplacer par la session actuelle ?") },
            confirmButton = { Button(onClick = { execute(pending, discard = true, replaceRecovery = true) }, modifier = Modifier.testTag("study-recovery-replace-confirm")) { Text("Remplacer") } },
            dismissButton = { TextButton(onClick = { dialog = StudyDialog.NONE }) { Text("Annuler") } }
        )
        StudyDialog.DELETE -> AlertDialog(
            onDismissRequest = { dialog = StudyDialog.NONE },
            title = { Text("Supprimer l’étude ?") },
            text = { Text("Cette suppression est définitive.") },
            confirmButton = { Button(onClick = { deleteConfirmed = true; execute(PendingStudyAction.DELETE) }, modifier = Modifier.testTag("study-delete-confirm")) { Text("Supprimer") } },
            dismissButton = { TextButton(onClick = { dialog = StudyDialog.NONE; deleteConfirmed = false }) { Text("Annuler") } }
        )
        StudyDialog.RECOVERY -> AlertDialog(
            onDismissRequest = { dialog = StudyDialog.NONE },
            title = { Text("Session précédente") },
            text = { RecoverySummary(state.recoveryPreview) },
            confirmButton = { Button(onClick = { execute(PendingStudyAction.RESTORE) }, modifier = Modifier.testTag("study-recovery-restore")) { Text("Restaurer") } },
            dismissButton = { TextButton(onClick = { dialog = StudyDialog.NONE }) { Text("Fermer") } }
        )
        StudyDialog.NONE -> Unit
    }
}

@Composable
private fun StudyActionRow(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, tag: String, enabled: Boolean, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag(tag),
        shape = RoundedCornerShape(12.dp),
        contentPadding = ButtonDefaults.ContentPadding
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null)
            Text(label, modifier = Modifier.padding(start = 10.dp))
        }
    }
}

@Composable
private fun RecoverySummary(study: FunctionStudy?) {
    if (study == null) {
        Text("Aucune session de récupération disponible.")
    } else {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(study.name ?: "Session de travail")
            Text("${study.angleMode.name} • paramètres : ${study.parameters.values.size}")
            study.slots.forEach { slot ->
                Text("${slot.id.code}: ${slot.validatedExpression ?: "(vide)"}${if (slot.draft != slot.validatedExpression.orEmpty()) " • brouillon : ${slot.draft}" else ""}")
            }
        }
    }
}
