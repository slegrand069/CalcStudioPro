package com.example.calcstudiopro.statistics

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class StatisticsClearDataTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun oneDClearRequiresConfirmationAndCancelKeepsObservations() {
        val viewModel = StatisticsViewModel()
        viewModel.editOneD("1\n2\n3")
        viewModel.finishEditing()
        viewModel.openEditor()
        composeRule.setContent {
            val state by viewModel.state.collectAsState()
            StatisticsScreen(state, {}, viewModel)
        }
        composeRule.onNodeWithTag("statistics-clear-data").performClick()
        composeRule.onNodeWithText("Cette action est irréversible.", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Annuler").performClick()
        assertEquals(3, viewModel.state.value.oneDValues.size)
        composeRule.onNodeWithTag("statistics-clear-data").performClick()
        composeRule.onNodeWithText("Tout supprimer").performClick()
        composeRule.waitForIdle()
        assertTrue(viewModel.state.value.oneDValues.isEmpty())
        assertEquals(StatisticsPage.EDITOR, viewModel.state.value.page)
        composeRule.onNodeWithTag("statistics-clear-data").assertIsNotEnabled()
    }

    @Test fun twoDClearRemovesOnlyPairsAndRemainsInEditor() {
        val viewModel = StatisticsViewModel()
        viewModel.selectDimension(StatisticsDimension.TWO_D)
        viewModel.editTwoD("1\t2\n3\t4")
        viewModel.finishEditing()
        viewModel.openEditor()
        composeRule.setContent {
            val state by viewModel.state.collectAsState()
            StatisticsScreen(state, {}, viewModel)
        }
        composeRule.onNodeWithTag("statistics-clear-data").performClick()
        composeRule.onNodeWithText("Tout supprimer").assertIsDisplayed().performClick()
        composeRule.waitForIdle()
        assertTrue(viewModel.state.value.twoDPoints.isEmpty())
        assertEquals(StatisticsPage.EDITOR, viewModel.state.value.page)
        composeRule.onNodeWithTag("statistics-clear-data").assertIsNotEnabled()
    }
}
