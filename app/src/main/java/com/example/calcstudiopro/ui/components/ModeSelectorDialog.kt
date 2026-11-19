package com.example.calcstudiopro.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Functions
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.ChangeHistory
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.calcstudiopro.navigation.ModeDestination

private fun modeIcon(route: String): ImageVector = when (route) {
    "calculator" -> Icons.Default.Calculate
    "history" -> Icons.Default.History
    "converter" -> Icons.Default.SwapHoriz
    "statistics" -> Icons.Default.QueryStats
    "geometry" -> Icons.Default.ChangeHistory
    "arithmetic" -> Icons.Default.Straighten
    else -> Icons.Default.Functions
}

@Composable
fun ModeSelectorDialog(
    modes: List<ModeDestination>,
    selectedRoute: String,
    onSelect: (ModeDestination) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Mode") },
        text = {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(modes, key = { it.route }) { mode ->
                    val selected = mode.route == selectedRoute
                    val contentColor = when {
                        !mode.enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        selected -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(0.88f)
                            .clickable(enabled = mode.enabled) { onSelect(mode) }
                            .testTag("mode-option-${mode.route}")
                            .padding(horizontal = 4.dp, vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = modeIcon(mode.route),
                            contentDescription = null,
                            tint = contentColor
                        )
                        Text(
                            text = mode.label,
                            modifier = Modifier.padding(top = 6.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = contentColor,
                            textAlign = TextAlign.Center,
                            maxLines = 2
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Fermer")
            }
        }
    )
}
