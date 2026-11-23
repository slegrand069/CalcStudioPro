package com.example.calcstudiopro.statistics.graph.ui

import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.calcstudiopro.statistics.graph.model.GraphSelectionDetails

@Composable
internal fun GraphFullscreenDialog(
    title: String,
    selection: GraphSelectionDetails?,
    onClose: () -> Unit,
    chart: @Composable (Modifier) -> Unit
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    // 0 keeps the current orientation, 1 requests landscape, 2 requests portrait.
    var orientationMode by rememberSaveable { mutableIntStateOf(0) }
    var originalOrientation by rememberSaveable { mutableIntStateOf(activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED) }
    var showDetails by rememberSaveable { mutableStateOf(false) }

    DisposableEffect(activity) {
        onDispose {
            // A configuration recreation is still part of the active exploration.
            if (activity != null && !activity.isChangingConfigurations) {
                activity.requestedOrientation = originalOrientation
            }
        }
    }
    DisposableEffect(activity, orientationMode) {
        if (activity != null) {
            when (orientationMode) {
                1 -> activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                2 -> activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }
        }
        onDispose { }
    }

    BackHandler(onBack = onClose)
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().padding(horizontal = 8.dpCompat(), vertical = 4.dpCompat())) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(onClick = onClose, modifier = Modifier.testTag("graph-fullscreen-close")) {
                        Icon(Icons.Default.Close, contentDescription = "Quitter le plein écran")
                    }
                    Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { showDetails = !showDetails },
                            enabled = selection != null,
                            modifier = Modifier.testTag("graph-fullscreen-details")
                        ) { Icon(Icons.Default.Visibility, contentDescription = if (showDetails) "Masquer les détails" else "Afficher les détails") }
                        IconButton(
                            onClick = {
                                if (orientationMode == 0) originalOrientation = activity?.requestedOrientation ?: originalOrientation
                                orientationMode = if (orientationMode == 1) 2 else 1
                            },
                            modifier = Modifier.testTag("graph-fullscreen-orientation")
                        ) {
                            Icon(
                                Icons.Default.ScreenRotation,
                                contentDescription = if (orientationMode == 1) "Revenir en portrait" else "Passer en paysage"
                            )
                        }
                    }
                }
                chart(Modifier.weight(1f).fillMaxWidth().testTag("graph-fullscreen-chart"))
                if (showDetails && selection != null) {
                    GraphSelectionDetailsPanel(selection, Modifier.fillMaxWidth().testTag("graph-fullscreen-selection"))
                }
            }
        }
    }
}

@Composable
private fun GraphSelectionDetailsPanel(selection: GraphSelectionDetails, modifier: Modifier = Modifier) {
    val point = selection.selection.point
    Surface(modifier = modifier.padding(vertical = 4.dpCompat()), color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.small) {
        Column(Modifier.fillMaxWidth().padding(10.dpCompat()), verticalArrangement = Arrangement.spacedBy(2.dpCompat())) {
            if (point.details.isEmpty()) {
                Text("X = ${com.example.calcstudiopro.statistics.graph.domain.GraphNumberFormatter.format(point.x)} · Y = ${com.example.calcstudiopro.statistics.graph.domain.GraphNumberFormatter.format(point.y)}")
            } else {
                point.details.forEach { detail ->
                    Text("${detail.label} : ${com.example.calcstudiopro.statistics.graph.domain.GraphNumberFormatter.format(detail.value, detail.wholeNumber, detail.percentage)}")
                }
            }
            if (selection.hitGroupSize > 1) Text("${selection.positionInHitGroup} sur ${selection.hitGroupSize} éléments superposés", style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun android.content.Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is android.content.ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun Int.dpCompat() = this.dp
