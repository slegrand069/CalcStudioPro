package com.example.calcstudiopro.history

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.calcstudiopro.history.domain.HistoryEntry
import com.example.calcstudiopro.history.domain.HistoryModes
import com.example.calcstudiopro.history.domain.HistoryValueTypes
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HistoryScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun showsEntriesDeletesOneConfirmsClearAndOffersReuseOnlyWhenCompatible() {
        val entries = listOf(
            HistoryEntry(
                id = 31,
                sourceMode = HistoryModes.SCIENTIFIC_CALCULATOR,
                description = "1 ÷ 3",
                displayResult = "0,3333333333",
                reusableValue = "0.3333333333333333",
                reusableValueType = HistoryValueTypes.DECIMAL_DOUBLE,
                createdAtEpochMillis = 100
            ),
            HistoryEntry(
                id = 32,
                sourceMode = HistoryModes.SCIENTIFIC_CALCULATOR,
                description = "sin(0)",
                displayResult = "0",
                reusableValue = "NaN",
                reusableValueType = HistoryValueTypes.DECIMAL_DOUBLE,
                createdAtEpochMillis = 90
            )
        )
        var deletedId: Long? = null
        var wasCleared = false
        var reusedExpression: String? = null
        var modeOpened = false

        composeRule.setContent {
            HistoryScreen(
                state = HistoryUiState(entries = entries, isLoading = false),
                onOpenModeSelector = { modeOpened = true },
                onDelete = { deletedId = it },
                onClear = { wasCleared = true },
                onReuseValue = { reusedExpression = it }
            )
        }

        composeRule.onNodeWithText("1 ÷ 3").assertIsDisplayed()
        composeRule.onNodeWithText("0,3333333333").assertIsDisplayed()
        composeRule.onNodeWithTag("history-reuse-31").performClick()
        assertEquals("0.3333333333333333", reusedExpression)
        composeRule.onAllNodesWithTag("history-reuse-32").assertCountEquals(0)

        composeRule.onNodeWithTag("history-delete-31").performClick()
        assertEquals(31L, deletedId)

        composeRule.onNodeWithTag("history-mode").performClick()
        assertEquals(true, modeOpened)

        composeRule.onNodeWithTag("history-clear").performClick()
        composeRule.onNodeWithText("Tous les calculs enregistrés seront supprimés.").assertIsDisplayed()
        assertEquals(false, wasCleared)
        composeRule.onNodeWithTag("history-clear-confirm").performClick()
        assertEquals(true, wasCleared)
    }
}
