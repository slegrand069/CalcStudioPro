package com.example.calcstudiopro.statistics.graph.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.example.calcstudiopro.statistics.graph.domain.GraphRange
import com.example.calcstudiopro.statistics.graph.render.GraphRenderStyle
import com.example.calcstudiopro.statistics.graph.render.drawGraph

@Composable
fun GraphPreview(snapshot: com.example.calcstudiopro.statistics.graph.model.GraphSnapshot?, onOpen: () -> Unit, emptyMessage: String, testTag: String = "graph-preview") {
    val colors = MaterialTheme.colorScheme
    val measurer = rememberTextMeasurer()
    val style = GraphRenderStyle(colors.onSurface, colors.outlineVariant.copy(alpha = 0.5f),
        listOf(colors.primary, colors.tertiary, colors.secondary, colors.error), colors.error, colors.onSurfaceVariant,
        pointRadiusDp = 2.8f, lineWidthDp = 1.4f)
    val viewport = snapshot?.let { remember(it) { GraphRange.autoFit(it) } }
    Box(Modifier.fillMaxWidth().height(196.dp).clickable(onClick = onOpen).testTag(testTag).semantics {
        contentDescription = if (snapshot == null) emptyMessage else "Aperçu ${snapshot.title}. Toucher pour ouvrir l’explorateur graphique."
    }, contentAlignment = Alignment.Center) {
        if (snapshot == null || snapshot.finitePointCount == 0 && snapshot.series.none { it.bars.isNotEmpty() || it.segments.isNotEmpty() }) {
            Text(emptyMessage, color = colors.onSurfaceVariant)
        } else Canvas(Modifier.fillMaxWidth().height(196.dp)) {
            drawGraph(snapshot, viewport!!, null, style, measurer)
        }
    }
}
