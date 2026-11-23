package com.example.calcstudiopro.statistics.graph

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.espresso.Espresso.pressBack
import android.content.res.Configuration
import com.example.calcstudiopro.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals

class GraphDemoScreenTest {
    @get:Rule val composeRule = createAndroidComposeRule<MainActivity>()

    @Test fun statisticsChartsExposeBothViewsAndFollowTheSelectedModel() {
        openGraphs()
        composeRule.onNodeWithTag("statistics-graph-tab-regression").assertIsSelected()
        composeRule.onNodeWithTag("statistics-graph-observation-count").assertIsDisplayed()
        composeRule.onNodeWithTag("statistics-graph-tab-residuals").performClick()
        composeRule.onNodeWithTag("statistics-graph-tab-residuals").assertIsSelected()
        composeRule.onNodeWithTag("statistics-graph-canvas").assertIsDisplayed()
        composeRule.onNodeWithTag("statistics-graph-tab-regression").performClick()
        composeRule.onNodeWithTag("statistics-graph-tab-regression").assertIsSelected()
        composeRule.onNodeWithTag("statistics-graph-model").assertIsDisplayed()
        composeRule.onNodeWithTag("statistics-graph-options").performClick()
        composeRule.onNodeWithTag("graph-option-regression").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("graph-option-grid").assertIsDisplayed()
    }

    @Test fun oneDGraphNavigationOffersHistogramSettingsAndTukeyView() {
        composeRule.onNodeWithTag("calculator-key-MODE").performClick()
        composeRule.onNodeWithTag("mode-option-statistics").performClick()
        composeRule.onNodeWithTag("statistics-dimension-1d").performClick()
        composeRule.onNodeWithTag("statistics-open-1d-graphs").performScrollTo().performClick()
        composeRule.onNodeWithTag("statistics-1d-graph-canvas").assertIsDisplayed()
        composeRule.onNodeWithTag("statistics-1d-graph-tab-tukey").performClick()
        composeRule.onNodeWithTag("statistics-1d-graph-tab-tukey").assertIsSelected()
        composeRule.onNodeWithTag("statistics-1d-graph-canvas").assertIsDisplayed()
        composeRule.onNodeWithTag("statistics-1d-graph-tab-histogram").performClick()
        composeRule.onNodeWithTag("statistics-1d-graph-options").performClick()
        composeRule.onNodeWithTag("histogram-mode-automatic").assertIsSelected()
        composeRule.onNodeWithTag("histogram-mode-manual").performScrollTo().performClick()
        composeRule.onNodeWithTag("histogram-mode-manual").assertIsSelected()
        composeRule.onNodeWithTag("histogram-manual-class-count").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("histogram-scale-percent").performScrollTo().performClick()
    }

    @Test fun fullscreenCanRotateTemporarilyAndReturnsToTheActiveChart() {
        openGraphs()
        composeRule.onNodeWithTag("statistics-graph-tab-residuals").performClick()
        composeRule.onNodeWithTag("statistics-graph-fullscreen").performClick()
        composeRule.onNodeWithTag("graph-fullscreen-chart").assertIsDisplayed()
        composeRule.onNodeWithTag("graph-fullscreen-orientation").performClick()
        composeRule.waitUntil(5_000) { composeRule.activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE }
        assertEquals(Configuration.ORIENTATION_LANDSCAPE, composeRule.activity.resources.configuration.orientation)
        composeRule.onNodeWithTag("graph-fullscreen-orientation").performClick()
        composeRule.waitUntil(5_000) { composeRule.activity.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT }
        assertEquals(Configuration.ORIENTATION_PORTRAIT, composeRule.activity.resources.configuration.orientation)
        composeRule.onNodeWithTag("graph-fullscreen-close").performClick()
        composeRule.onNodeWithTag("statistics-graph-tab-residuals").assertIsSelected()
    }

    @Test fun oneDFullscreenShowsTheSelectedChartAndReturnsToItsTab() {
        composeRule.onNodeWithTag("calculator-key-MODE").performClick()
        composeRule.onNodeWithTag("mode-option-statistics").performClick()
        composeRule.onNodeWithTag("statistics-dimension-1d").performClick()
        composeRule.onNodeWithTag("statistics-open-1d-graphs").performScrollTo().performClick()
        composeRule.onNodeWithTag("statistics-1d-graph-tab-tukey").performClick()
        composeRule.onNodeWithTag("statistics-1d-graph-fullscreen").performClick()
        composeRule.onNodeWithTag("graph-fullscreen-chart").assertIsDisplayed()
        composeRule.onNodeWithTag("graph-fullscreen-close").performClick()
        composeRule.onNodeWithTag("statistics-1d-graph-tab-tukey").assertIsSelected()
    }

    @Test fun androidBackLeavesFullscreenAndReturnsToTheExplorer() {
        openGraphs()
        composeRule.onNodeWithTag("statistics-graph-fullscreen").performClick()
        composeRule.onNodeWithTag("graph-fullscreen-chart").assertIsDisplayed()
        pressBack()
        composeRule.onNodeWithTag("statistics-graph-canvas").assertIsDisplayed()
    }

    private fun openGraphs() {
        composeRule.onNodeWithTag("calculator-key-MODE").performClick()
        composeRule.onNodeWithTag("mode-option-statistics").performClick()
        composeRule.onNodeWithTag("statistics-dimension-2d").performClick()
        composeRule.onNodeWithTag("statistics-open-graphs").performScrollTo().performClick()
        composeRule.onNodeWithTag("statistics-graph-canvas").assertIsDisplayed()
    }
}
