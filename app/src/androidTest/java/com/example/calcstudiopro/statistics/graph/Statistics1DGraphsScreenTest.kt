package com.example.calcstudiopro.statistics.graph

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.test.center
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.calcstudiopro.statistics.StatisticsState
import com.example.calcstudiopro.statistics.graph.ui.Statistics1DGraphsScreen
import org.junit.Rule
import org.junit.Test

class Statistics1DGraphsScreenTest {
    @get:Rule val composeRule: ComposeContentTestRule = createComposeRule()

    @Test fun constantSeriesBarsAndBoxCanBeSelected() {
        composeRule.setContent {
            Statistics1DGraphsScreen(StatisticsState(oneDValues = List(8) { -2.5 }), onBack = {})
        }
        val canvas = composeRule.onNodeWithTag("statistics-1d-graph-canvas").performScrollTo()
        canvas.assertIsDisplayed().performTouchInput { click(center) }
        composeRule.mainClock.advanceTimeBy(500)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("graph-selected-coordinates").assertTextContains("Effectif : 8", substring = true)
        composeRule.onNodeWithTag("statistics-1d-graph-tab-tukey").performClick()
        composeRule.onNodeWithTag("statistics-1d-graph-canvas").performScrollTo().performTouchInput { click(center) }
        composeRule.mainClock.advanceTimeBy(500)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("graph-selected-coordinates").assertTextContains("Q1 : -2,5", substring = true)
    }

    @Test fun tenThousandObservationsRenderBothOneDCharts() {
        composeRule.setContent {
            Statistics1DGraphsScreen(StatisticsState(oneDValues = (0 until 10_000).map(Int::toDouble)), onBack = {})
        }
        composeRule.onNodeWithTag("statistics-1d-graph-canvas").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("statistics-1d-graph-tab-tukey").performClick()
        composeRule.onNodeWithTag("statistics-1d-graph-canvas").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("tukey-summary-quartiles").assertIsDisplayed()
    }

    @Test fun graphsRefreshWhenTheObservedSessionChanges() {
        var state by mutableStateOf(StatisticsState(oneDValues = listOf(1.0, 2.0, 3.0)))
        composeRule.setContent { Statistics1DGraphsScreen(state, onBack = {}) }
        composeRule.onNodeWithTag("statistics-1d-observation-count").assertTextContains("3 observations")

        state = state.copy(oneDValues = listOf(-4.0, 2.0, 6.0, 10.0))
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("statistics-1d-observation-count").assertTextContains("4 observations")
        composeRule.onNodeWithTag("statistics-1d-graph-canvas").performScrollTo().assertIsDisplayed()
    }

    @Test fun histogramOptionsLiveInSheetAndApplyImmediately() {
        composeRule.setContent { Statistics1DGraphsScreen(StatisticsState(oneDValues = listOf(1.0, 2.0, 3.0, 4.0)), onBack = {}) }
        composeRule.onNodeWithTag("statistics-1d-graph-options").performClick()
        composeRule.onNodeWithTag("histogram-mode-manual").performScrollTo().assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("histogram-manual-class-count").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("graph-option-grid").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("histogram-scale-percent").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("statistics-1d-graph-canvas").assertIsDisplayed()
    }
}
