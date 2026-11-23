package com.example.calcstudiopro.statistics.graph

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.calcstudiopro.statistics.graph.ui.GraphDemoScreen
import com.example.calcstudiopro.ui.theme.CalcStudioProTheme
import org.junit.Rule
import org.junit.Test

/** The provisional demo remains covered internally without being linked from normal STAT navigation. */
class GraphDemoCanvasTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun demoSelectionAndResetRemainAvailableForEngineValidation() {
        composeRule.setContent { CalcStudioProTheme { GraphDemoScreen(onBack = {}) } }
        composeRule.onNodeWithTag("interactive-graph-canvas").assertIsDisplayed()
        composeRule.onNodeWithTag("graph-demo-next-point").performClick()
        composeRule.onNodeWithTag("graph-selected-coordinates").assertTextContains("X =", substring = true)
        composeRule.onNodeWithTag("graph-demo-reset").performClick()
        composeRule.onNodeWithTag("graph-demo-status").assertTextContains("Cadrage réinitialisé", substring = true)
    }

    @Test fun tenThousandObservationCanvasRemainsAvailableInternally() {
        composeRule.setContent { CalcStudioProTheme { GraphDemoScreen(onBack = {}) } }
        composeRule.onNodeWithTag("graph-demo-large-data-toggle").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("10 000 observations").assertIsDisplayed()
        composeRule.onNodeWithTag("interactive-graph-canvas").assertIsDisplayed()
    }
}
