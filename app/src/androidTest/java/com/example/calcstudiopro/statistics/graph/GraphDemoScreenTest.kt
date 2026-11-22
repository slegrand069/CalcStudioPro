package com.example.calcstudiopro.statistics.graph

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.calcstudiopro.MainActivity
import org.junit.Rule
import org.junit.Test

class GraphDemoScreenTest {
    @get:Rule val composeRule = createAndroidComposeRule<MainActivity>()

    @Test fun prototypeIsReachableCanvasAccessibleAndCanResetSelection() {
        openPrototype()
        composeRule.onNodeWithTag("interactive-graph-canvas").assertIsDisplayed()
        composeRule.onNodeWithTag("graph-demo-next-point").performClick()
        composeRule.onNodeWithTag("graph-selected-coordinates").assertTextContains("X =", substring = true)
        composeRule.onNodeWithTag("graph-demo-reset").performClick()
        composeRule.onNodeWithTag("graph-demo-status").assertTextContains("Cadrage réinitialisé", substring = true)
        composeRule.onNodeWithTag("graph-selected-coordinates").assertTextContains("Touchez un point", substring = true)
    }

    @Test fun tenThousandObservationModeCanBeEnabledAndHasAccessibleCanvas() {
        openPrototype()
        composeRule.onNodeWithTag("graph-demo-large-data-toggle").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("10 000 observations").assertIsDisplayed()
        composeRule.onNodeWithTag("interactive-graph-canvas").assertIsDisplayed()
    }

    private fun openPrototype() {
        composeRule.onNodeWithTag("calculator-key-MODE").performClick()
        composeRule.onNodeWithTag("mode-option-statistics").performClick()
        composeRule.onNodeWithTag("statistics-open-graph-demo").performScrollTo().performClick()
        composeRule.onNodeWithTag("interactive-graph-canvas").assertIsDisplayed()
    }
}
