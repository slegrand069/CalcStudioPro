package com.example.calcstudiopro.functions.graph

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.example.calcstudiopro.functions.graph.interaction.FunctionGraphViewportController
import com.example.calcstudiopro.functions.graph.interaction.functionGraphGestures
import com.example.calcstudiopro.statistics.graph.model.GraphViewport
import com.example.calcstudiopro.statistics.graph.model.PlotRect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import kotlin.math.abs

class FunctionGraphGestureRouterTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun simpleTapDoesNotPanAndDoubleTapResets() {
        var viewport by mutableStateOf(GraphViewport(
            com.example.calcstudiopro.statistics.graph.model.AxisRange(-5.0, 15.0),
            com.example.calcstudiopro.statistics.graph.model.AxisRange(-8.0, 12.0)
        ))
        val initial = viewport
        var taps = 0
        composeRule.setContent {
            Box(
                Modifier.size(320.dp, 220.dp).testTag("gesture-host").functionGraphGestures(
                    viewport = { viewport },
                    plot = PlotRect(0.0, 0.0, 320.0, 220.0),
                    onViewportChange = { viewport = it },
                    onGestureStart = {}, onGestureEnd = {}, onTap = { taps++ },
                    onDoubleTap = { viewport = FunctionGraphViewportController.reset() }
                )
            )
        }
        composeRule.onNodeWithTag("gesture-host").performTouchInput { down(center); up() }
        composeRule.runOnIdle {
            assertEquals(initial, viewport)
            assertEquals(1, taps)
        }

        composeRule.onNodeWithTag("gesture-host").performTouchInput {
            down(center); up()
            advanceEventTime(120)
            down(center); up()
        }
        composeRule.runOnIdle {
            assertEquals(FunctionGraphViewportController.INITIAL, viewport)
        }
    }

    @Test fun pointerCountChangesRebaseWithoutViewportJumps() {
        var viewport by mutableStateOf(FunctionGraphViewportController.INITIAL)
        val history = mutableListOf<GraphViewport>()
        var starts = 0
        var ends = 0
        composeRule.setContent {
            Box(
                Modifier.size(320.dp, 220.dp).testTag("gesture-host").functionGraphGestures(
                    viewport = { viewport },
                    plot = PlotRect(0.0, 0.0, 320.0, 220.0),
                    onViewportChange = { viewport = it; history += it },
                    onGestureStart = { starts++ }, onGestureEnd = { ends++ },
                    onDoubleTap = { viewport = FunctionGraphViewportController.reset() }
                )
            )
        }
        composeRule.onNodeWithTag("gesture-host").performTouchInput {
            down(0, Offset(100f, 100f))
            moveTo(0, Offset(112f, 100f))
            down(1, Offset(212f, 100f))
            moveTo(0, Offset(105f, 100f))
            moveTo(1, Offset(235f, 100f))
            up(1)
            moveTo(0, Offset(120f, 110f))
            up(0)
        }
        composeRule.runOnIdle {
            assertTrue(starts == 1 && ends == 1)
            assertTrue(history.size >= 3)
            assertTrue(viewport.x.maximum - viewport.x.minimum < 20.0)
            assertTrue(viewport.x.minimum.isFinite() && viewport.x.maximum.isFinite())
            history.zipWithNext().forEach { (a, b) ->
                assertTrue(abs(b.x.minimum - a.x.minimum) < 2.0)
                assertTrue(abs(b.y.minimum - a.y.minimum) < 2.0)
            }
        }
    }
}
