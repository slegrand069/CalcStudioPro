package com.example.calcstudiopro.functions.graph.interaction

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import com.example.calcstudiopro.statistics.graph.model.GraphViewport
import com.example.calcstudiopro.statistics.graph.model.PlotRect
import com.example.calcstudiopro.statistics.graph.model.ScreenPoint
import kotlin.math.hypot

/** One pointer router for pan, pinch, taps and double taps; future curve selection can be added here. */
fun Modifier.functionGraphGestures(
    viewport: () -> GraphViewport,
    plot: PlotRect,
    onViewportChange: (GraphViewport) -> Unit,
    onGestureStart: () -> Unit,
    onGestureEnd: () -> Unit,
    onTap: (ScreenPoint) -> Unit = {},
    onDoubleTap: () -> Unit
): Modifier = pointerInput(plot) {
    var lastTap: TapRecord? = null
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val downId = down.id
        val downPosition = down.position
        var previous = mapOf(down.id to down.position)
        var moved = false
        var multiTouch = false
        var gestureStarted = false
        var previousCentroid = down.position
        var dragging = false

        fun startGesture() {
            if (!gestureStarted) {
                gestureStarted = true
                onGestureStart()
            }
        }

        while (true) {
            val event = awaitPointerEvent()
            val pressed = event.changes.filter { it.pressed }
            if (pressed.isEmpty()) {
                val released = event.changes.firstOrNull { it.id == downId }?.position ?: downPosition
                if (gestureStarted) {
                    onGestureEnd()
                } else if (!moved && !multiTouch) {
                    val now = event.changes.maxOfOrNull(PointerInputChange::uptimeMillis) ?: down.uptimeMillis
                    val prior = lastTap
                    val isDouble = prior != null &&
                        now - prior.timeMillis in viewConfiguration.doubleTapMinTimeMillis..viewConfiguration.doubleTapTimeoutMillis &&
                        hypot((released.x - prior.position.x).toDouble(), (released.y - prior.position.y).toDouble()) <= viewConfiguration.touchSlop * 2.0
                    if (isDouble) {
                        lastTap = null
                        onDoubleTap()
                    } else {
                        lastTap = TapRecord(released, now)
                        onTap(ScreenPoint(released.x.toDouble(), released.y.toDouble()))
                    }
                }
                break
            }

            val current = pressed.associate { it.id to it.position }
            if (current.size != previous.size || current.keys != previous.keys) {
                // Rebase when pointer count/identity changes. No synthetic pan or scale is emitted.
                if (current.size > 1) multiTouch = true
                previous = current
                previousCentroid = centroid(current.values)
                continue
            }

            if (current.size == 1) {
                val id = current.keys.first()
                val oldPosition = previous.getValue(id)
                val position = current.getValue(id)
                val total = position - downPosition
                if (!dragging && hypot(total.x.toDouble(), total.y.toDouble()) > viewConfiguration.touchSlop) {
                    dragging = true
                    moved = true
                    startGesture()
                    onViewportChange(
                        FunctionGraphViewportController.pan(viewport(), total.x.toDouble(), total.y.toDouble(), plot)
                    )
                } else if (dragging) {
                    moved = true
                    startGesture()
                    val delta = position - oldPosition
                    onViewportChange(
                        FunctionGraphViewportController.pan(viewport(), delta.x.toDouble(), delta.y.toDouble(), plot)
                    )
                }
                previousCentroid = position
            } else {
                multiTouch = true
                moved = true
                dragging = true
                startGesture()
                val currentCentroid = centroid(current.values)
                val oldRadius = meanRadius(previous, previousCentroid)
                val newRadius = meanRadius(current, currentCentroid)
                val zoom = if (oldRadius > 0.0 && newRadius.isFinite()) newRadius / oldRadius else 1.0
                onViewportChange(
                    FunctionGraphViewportController.transform(
                        viewport(),
                        ScreenPoint(previousCentroid.x.toDouble(), previousCentroid.y.toDouble()),
                        ScreenPoint(currentCentroid.x.toDouble(), currentCentroid.y.toDouble()),
                        zoom,
                        plot
                    )
                )
                previousCentroid = currentCentroid
            }

            pressed.forEach(PointerInputChange::consume)
            previous = current
        }
    }
}

private data class TapRecord(val position: Offset, val timeMillis: Long)

private fun centroid(points: Collection<Offset>): Offset {
    if (points.isEmpty()) return Offset.Zero
    return Offset(points.sumOf { it.x.toDouble() }.div(points.size).toFloat(), points.sumOf { it.y.toDouble() }.div(points.size).toFloat())
}

private fun meanRadius(points: Map<PointerId, Offset>, center: Offset): Double {
    if (points.isEmpty()) return 0.0
    return points.values.sumOf { hypot((it.x - center.x).toDouble(), (it.y - center.y).toDouble()) } / points.size
}
