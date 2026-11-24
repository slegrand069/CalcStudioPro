package com.example.calcstudiopro.functions.graph

import android.content.res.Configuration
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import com.example.calcstudiopro.functions.graph.ui.FunctionGraphCanvasDemoActivity
import com.example.calcstudiopro.functions.domain.FunctionColorPalette
import com.example.calcstudiopro.functions.domain.FunctionSlotId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Confirms the debug-only, explicit-ADB demonstration activity renders the canvas. */
class FunctionGraphCanvasDemoTest {
    @get:Rule val composeRule = createAndroidComposeRule<FunctionGraphCanvasDemoActivity>()

    @Test fun debugDemoActivityShowsFunctionCanvas() {
        composeRule.onNodeWithTag("function-graph-canvas-demo").assertIsDisplayed()
        composeRule.onNodeWithTag("function-graph-demo-back").assertIsDisplayed()
        composeRule.onNodeWithTag("function-graph-demo-rotate").assertIsDisplayed()
    }

    @Test fun rotationControlForcesBothOrientationsAndKeepsCanvas() {
        composeRule.onNodeWithTag("function-graph-canvas-demo").performTouchInput {
            down(center + Offset(-40f, 0f))
            moveTo(center + Offset(15f, 0f), delayMillis = 180)
            up()
        }
        val viewportBeforeRotation = viewportDescription()
        val initial = composeRule.activity.resources.configuration.orientation
        val first = if (initial == Configuration.ORIENTATION_LANDSCAPE) {
            Configuration.ORIENTATION_PORTRAIT
        } else {
            Configuration.ORIENTATION_LANDSCAPE
        }
        composeRule.onNodeWithTag("function-graph-demo-rotate").performClick()
        composeRule.waitUntil(5_000) { composeRule.activity.resources.configuration.orientation == first }
        assertEquals(first, composeRule.activity.resources.configuration.orientation)
        composeRule.onNodeWithTag("function-graph-canvas-demo").assertIsDisplayed()
        assertEquals(viewportBeforeRotation, viewportDescription())

        val second = if (first == Configuration.ORIENTATION_LANDSCAPE) {
            Configuration.ORIENTATION_PORTRAIT
        } else {
            Configuration.ORIENTATION_LANDSCAPE
        }
        composeRule.onNodeWithTag("function-graph-demo-rotate").performClick()
        composeRule.waitUntil(5_000) { composeRule.activity.resources.configuration.orientation == second }
        assertEquals(second, composeRule.activity.resources.configuration.orientation)
        composeRule.onNodeWithTag("function-graph-canvas-demo").assertIsDisplayed()
        assertEquals(viewportBeforeRotation, viewportDescription())
    }

    @Test fun sixEngineCurvesRemainAvailableDuringPanAndPinch() {
        val canvas = composeRule.onNodeWithTag("function-graph-canvas-demo")
        composeRule.waitUntil(20_000) {
            canvas.fetchSemanticsNode().config[SemanticsProperties.ContentDescription]
                .any { it.contains("Génération données 0") }
        }
        val image = canvas.captureToImage().toPixelMap()
        FunctionSlotId.ordered.forEach { id ->
            val argb = FunctionColorPalette.defaultFor(id).argb.toInt()
            val found = (0 until image.height).any { y -> (0 until image.width).any { x -> image[x, y].toArgb() == argb } }
            assertTrue("Expected a rendered pixel for $id", found)
        }

        val before = viewportDescription()
        canvas.performTouchInput {
            down(center + Offset(-50f, 15f))
            moveTo(center + Offset(5f, 20f), delayMillis = 180)
            up()
        }
        val afterPan = viewportDescription()
        assertNotEquals(before, afterPan)
        canvas.performTouchInput {
            down(0, center + Offset(-40f, 0f))
            down(1, center + Offset(40f, 0f))
            moveTo(0, center + Offset(-55f, 4f))
            moveTo(1, center + Offset(55f, 16f))
            up(1)
            up(0)
        }
        assertNotEquals(afterPan, viewportDescription())
        val contentDescription = canvas.fetchSemanticsNode().config[SemanticsProperties.ContentDescription].joinToString(" ")
        assertTrue(contentDescription.contains("f1:") && contentDescription.contains("f6:"))
        assertTrue(!contentDescription.contains("f1: masquée") && !contentDescription.contains("f6: masquée"))
    }

    @Test fun tappingCurvePublishesAccessibleSelection() {
        val canvas = composeRule.onNodeWithTag("function-graph-canvas-demo")
        composeRule.waitUntil(20_000) {
            canvas.fetchSemanticsNode().config[SemanticsProperties.ContentDescription]
                .any { it.contains("Génération données 0") }
        }
        canvas.performTouchInput { click(center + Offset(20f, 0f)) }
        composeRule.waitUntil(2_000) {
            canvas.fetchSemanticsNode().config[SemanticsProperties.StateDescription].contains("sélection f")
        }
        assertTrue(viewportDescription().contains("sélection f"))
    }

    @Test fun doubleTapResetsAndRollsBackItsImmediateFirstTapSelection() {
        val canvas = composeRule.onNodeWithTag("function-graph-canvas-demo")
        composeRule.waitUntil(20_000) {
            canvas.fetchSemanticsNode().config[SemanticsProperties.ContentDescription]
                .any { it.contains("Génération données 0") }
        }
        canvas.performTouchInput { doubleClick(center + Offset(20f, 0f)) }
        composeRule.waitUntil(2_000) {
            viewportDescription() == "X -10.0 à 10.0 ; Y -10.0 à 10.0"
        }
        assertEquals("X -10.0 à 10.0 ; Y -10.0 à 10.0", viewportDescription())
        // With no earlier marker, the immediate first-tap selection is rolled back by double tap.
        assertTrue(!canvas.fetchSemanticsNode().config[SemanticsProperties.StateDescription].contains("sélection f"))
    }

    @Test fun explicitBackLeavesTheDebugActivity() {
        composeRule.onNodeWithTag("function-graph-demo-back").performClick()
        composeRule.waitUntil(5_000) { composeRule.activity.isFinishing || composeRule.activity.isDestroyed }
    }

    @Test fun androidBackExitsWhenDemoHasNoPreviousScreen() {
        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitUntil(5_000) { composeRule.activity.isFinishing || composeRule.activity.isDestroyed }
    }

    private fun viewportDescription(): String = composeRule.onNodeWithTag("function-graph-canvas-demo")
        .fetchSemanticsNode().config[SemanticsProperties.StateDescription]
}
