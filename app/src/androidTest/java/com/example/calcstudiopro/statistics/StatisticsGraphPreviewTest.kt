package com.example.calcstudiopro.statistics

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class StatisticsGraphPreviewTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun oneDPreviewOpensTheHistogramExplorer() {
        var opened = false
        val viewModel = StatisticsViewModel()
        composeRule.setContent {
            StatisticsScreen(StatisticsState(), {}, viewModel, onOpenOneDGraphs = { opened = true })
        }
        composeRule.onNodeWithTag("statistics-1d-graph-preview").performScrollTo().performClick()
        assertTrue(opened)
    }

    @Test fun twoDPreviewOpensTheScatterExplorer() {
        var opened = false
        val viewModel = StatisticsViewModel()
        composeRule.setContent {
            StatisticsScreen(StatisticsState(dimension = StatisticsDimension.TWO_D), {}, viewModel, onOpenGraphs = { opened = true })
        }
        composeRule.onNodeWithTag("statistics-2d-graph-preview").performScrollTo().performClick()
        assertTrue(opened)
    }
}
