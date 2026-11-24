package com.example.calcstudiopro.functions.graph

import android.content.res.Configuration
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.example.calcstudiopro.functions.graph.ui.FunctionGraphCanvasDemoActivity
import org.junit.Assert.assertEquals
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

        val second = if (first == Configuration.ORIENTATION_LANDSCAPE) {
            Configuration.ORIENTATION_PORTRAIT
        } else {
            Configuration.ORIENTATION_LANDSCAPE
        }
        composeRule.onNodeWithTag("function-graph-demo-rotate").performClick()
        composeRule.waitUntil(5_000) { composeRule.activity.resources.configuration.orientation == second }
        assertEquals(second, composeRule.activity.resources.configuration.orientation)
        composeRule.onNodeWithTag("function-graph-canvas-demo").assertIsDisplayed()
    }

    @Test fun explicitBackLeavesTheDebugActivity() {
        composeRule.onNodeWithTag("function-graph-demo-back").performClick()
        composeRule.waitUntil(5_000) { composeRule.activity.isFinishing || composeRule.activity.isDestroyed }
    }

    @Test fun androidBackExitsWhenDemoHasNoPreviousScreen() {
        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitUntil(5_000) { composeRule.activity.isFinishing || composeRule.activity.isDestroyed }
    }
}
