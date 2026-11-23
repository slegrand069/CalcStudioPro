package com.example.calcstudiopro.functions

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.calcstudiopro.functions.domain.FunctionColor
import com.example.calcstudiopro.functions.domain.FunctionSlot
import com.example.calcstudiopro.functions.domain.FunctionSlotId
import com.example.calcstudiopro.functions.domain.FunctionStudy

@Composable
fun FunctionStudyRoute(
    viewModel: FunctionStudyViewModel,
    onBack: () -> Unit,
    onOpenEditor: (FunctionSlotId) -> Unit = {}
) {
    val state by viewModel.state.collectAsState()
    FunctionStudyScreen(
        state = state,
        onBack = onBack,
        onChooseColor = viewModel::chooseColor,
        onToggleVisibility = viewModel::toggleVisibility,
        onOpenEditor = onOpenEditor
    )
}

@Composable
fun FunctionStudyScreen(
    state: FunctionStudyUiState,
    onBack: () -> Unit,
    onChooseColor: (FunctionSlotId, FunctionColor) -> Unit,
    onToggleVisibility: (FunctionSlotId) -> Unit,
    onOpenEditor: (FunctionSlotId) -> Unit = {}
) {
    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().height(40.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack, modifier = Modifier.size(40.dp).testTag("functions-back")) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                }
                Text(
                    text = "Fonctions",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }

            Text(
                text = "Session de travail",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.testTag("functions-session-title")
            )
            Text(
                text = "Sauvegarde automatique • ${state.study?.angleMode?.name ?: "RAD"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("functions-session-status")
            )

            state.loadError?.let { message ->
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.fillMaxWidth().testTag("functions-load-error")
                )
            }
            state.saveError?.let { message ->
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.fillMaxWidth().testTag("functions-save-error")
                )
            }

            val study = state.study
            if (study != null) {
                Column(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    study.slots.forEach { slot ->
                        FunctionSlotRow(
                            slot = slot,
                            onChooseColor = { onChooseColor(slot.id, it) },
                            onToggleVisibility = { onToggleVisibility(slot.id) },
                            onOpenEditor = { onOpenEditor(slot.id) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = {},
                    enabled = false,
                    modifier = Modifier.weight(1f).height(48.dp).testTag("functions-graph"),
                    contentPadding = PaddingValues(horizontal = 12.dp)
                ) { Text("Graphe") }
                OutlinedButton(
                    onClick = {},
                    enabled = false,
                    modifier = Modifier.weight(1f).height(48.dp).testTag("functions-table"),
                    contentPadding = PaddingValues(horizontal = 12.dp)
                ) { Text("Tableau") }
            }
        }
    }
}

@Composable
private fun FunctionSlotRow(
    slot: FunctionSlot,
    onChooseColor: (FunctionColor) -> Unit,
    onToggleVisibility: () -> Unit,
    onOpenEditor: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(14.dp)
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("function-slot-${slot.id.code}")
            .clickable(onClick = onOpenEditor),
        shape = shape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.24f))
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ColorSwatch(slot, onChooseColor)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                Text(
                    text = slot.id.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                Text(
                    text = slot.validatedExpression?.let(::formatA1Expression) ?: "Ajouter une expression…",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (slot.validatedExpression == null) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("function-expression-${slot.id.code}")
                )
            }
            IconButton(
                onClick = onToggleVisibility,
                modifier = Modifier.size(40.dp).testTag("function-eye-${slot.id.code}")
            ) {
                Icon(
                    imageVector = if (slot.isVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                    contentDescription = if (slot.isVisible) "Masquer ${slot.id.displayName}" else "Afficher ${slot.id.displayName}",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(
                onClick = onOpenEditor,
                modifier = Modifier.size(40.dp).testTag("function-edit-${slot.id.code}")
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Modifier ${slot.id.displayName}",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ColorSwatch(slot: FunctionSlot, onChooseColor: (FunctionColor) -> Unit) {
    var expanded by remember(slot.id) { mutableStateOf(false) }
    Box {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(slot.color.argb.toInt()))
                .clickable { expanded = true }
                .semantics {
                    contentDescription = "Changer la couleur de ${slot.id.displayName}. Couleur actuelle : ${slot.color.a1Label}"
                    role = Role.Button
                }
                .testTag("function-color-${slot.id.code}")
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.testTag("function-color-menu-${slot.id.code}")
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FunctionColor.entries.chunked(3).forEach { colors ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        colors.forEach { color ->
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(color.argb.toInt()))
                                    .clickable {
                                        onChooseColor(color)
                                        expanded = false
                                    }
                                    .semantics {
                                        contentDescription = "Choisir la couleur ${color.a1Label}"
                                        role = Role.Button
                                    }
                                    .testTag("function-palette-${slot.id.code}-${color.name}"),
                                contentAlignment = Alignment.Center
                            ) {
                                if (slot.color == color) {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = "Couleur sélectionnée",
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private val FunctionSlotId.displayName: String
    get() = "f${number.toSubscript()}(x)"

private val FunctionColor.a1Label: String
    get() = when (this) {
        FunctionColor.BLUE -> "bleue"
        FunctionColor.RED -> "rouge"
        FunctionColor.GREEN -> "verte"
        FunctionColor.AMBER -> "ambre"
        FunctionColor.VIOLET -> "violette"
        FunctionColor.CYAN -> "cyan"
        FunctionColor.SUN_YELLOW -> "jaune soleil"
        FunctionColor.VIVID_ORANGE -> "orange vif"
        FunctionColor.NAVY -> "bleu nuit"
        FunctionColor.RASPBERRY -> "rose framboise"
        FunctionColor.LIME -> "vert citron"
        FunctionColor.SLATE -> "gris ardoise"
    }

private fun Int.toSubscript(): String = toString().map { digit ->
    when (digit) {
        '0' -> '₀'; '1' -> '₁'; '2' -> '₂'; '3' -> '₃'; '4' -> '₄'
        '5' -> '₅'; '6' -> '₆'; '7' -> '₇'; '8' -> '₈'; else -> '₉'
    }
}.joinToString("")

private val superscripts = mapOf(
    '0' to '⁰', '1' to '¹', '2' to '²', '3' to '³', '4' to '⁴',
    '5' to '⁵', '6' to '⁶', '7' to '⁷', '8' to '⁸', '9' to '⁹', '-' to '⁻', '+' to '⁺'
)

private fun formatA1Expression(expression: String): String = expression
    .replace("*", "×")
    .replace(Regex("\\^([+-]?\\d+)")) { match ->
        match.groupValues[1].map { superscripts[it] ?: it }.joinToString("")
    }
