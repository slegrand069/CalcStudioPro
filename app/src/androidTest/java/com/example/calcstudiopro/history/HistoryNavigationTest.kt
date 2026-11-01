package com.example.calcstudiopro.history

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.calcstudiopro.MainActivity
import com.example.calcstudiopro.history.data.CalcStudioDatabase
import com.example.calcstudiopro.history.data.RoomHistoryRepository
import com.example.calcstudiopro.history.domain.HistoryModes
import com.example.calcstudiopro.history.domain.HistoryValueTypes
import com.example.calcstudiopro.history.domain.NewHistoryEntry
import com.example.calcstudiopro.resetSettingsToDefaults
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HistoryNavigationTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val repository by lazy {
        RoomHistoryRepository(
            CalcStudioDatabase.getInstance(
                InstrumentationRegistry.getInstrumentation().targetContext
            ).historyDao()
        )
    }

    @Before
    fun clearAppState() = runBlocking {
        resetSettingsToDefaults()
        repository.clear()
    }

    @Test
    fun modeSelectorOpensHistoryAndDoesNotExposeUnimplementedModes() {
        composeRule.onNodeWithTag("calculator-key-MODE").performClick()
        composeRule.onNodeWithTag("mode-option-history").assertIsDisplayed()
        composeRule.onNodeWithTag("mode-option-calculator").assertIsDisplayed()
        composeRule.onAllNodesWithText("Convertisseur").assertCountEquals(0)
        composeRule.onAllNodesWithText("Statistiques").assertCountEquals(0)
        composeRule.onAllNodesWithText("Géométrie").assertCountEquals(0)
        composeRule.onAllNodesWithText("Graphique").assertCountEquals(0)
        composeRule.onAllNodesWithText("Arithmétique").assertCountEquals(0)

        composeRule.onNodeWithTag("mode-option-history").performClick()
        composeRule.onNodeWithText("Historique").assertIsDisplayed()
        composeRule.onNodeWithTag("history-empty").assertIsDisplayed()
        composeRule.onNodeWithTag("history-mode").performClick()
        composeRule.onNodeWithTag("mode-option-calculator").performClick()
        composeRule.onNodeWithTag("calculator-expression").assertIsDisplayed()
    }

    @Test
    fun onlySuccessfulEqualsAddsEntriesAndShiftEqualsDoesNotAddAnother() {
        tap("1")
        tap("+")
        tap("2")
        openHistory()
        composeRule.onNodeWithTag("history-empty").assertIsDisplayed()

        openCalculatorFromHistory()
        tap("=")
        tap("Shift")
        tap("=")
        openHistory()
        composeRule.onNodeWithText("1+2").assertIsDisplayed()

        openCalculatorFromHistory()
        tap("AC")
        tap("1")
        tap("÷")
        tap("0")
        tap("=")
        openHistory()
        composeRule.onNodeWithText("1+2").assertIsDisplayed()
        composeRule.onAllNodesWithText("1÷0").assertCountEquals(0)

        val rows = runBlocking { repository.entries.first() }
        org.junit.Assert.assertEquals(1, rows.size)
    }

    @Test
    fun tappingCompatibleHistoryValueReinjectsItsCanonicalValue() = runBlocking {
        repository.add(
            NewHistoryEntry(
                sourceMode = HistoryModes.SCIENTIFIC_CALCULATOR,
                description = "1 ÷ 3",
                displayResult = "0,3333333333",
                reusableValue = "0.3333333333333333",
                reusableValueType = HistoryValueTypes.DECIMAL_DOUBLE,
                createdAtEpochMillis = 10
            )
        )

        composeRule.onNodeWithTag("calculator-key-MODE").performClick()
        composeRule.onNodeWithTag("mode-option-history").performClick()
        val id = repository.entries.first().single().id
        composeRule.onNodeWithTag("history-reuse-$id").performClick()
        composeRule.onNodeWithTag("calculator-expression")
            .assertTextEquals("0.3333333333333333")
    }

    private fun openHistory() {
        composeRule.onNodeWithTag("calculator-key-MODE").performClick()
        composeRule.onNodeWithTag("mode-option-history").performClick()
    }

    private fun openCalculatorFromHistory() {
        composeRule.onNodeWithTag("history-mode").performClick()
        composeRule.onNodeWithTag("mode-option-calculator").performClick()
    }

    private fun tap(key: String) {
        val tag = if (key == "Shift") "calculator-shift-inactive" else "calculator-key-$key"
        composeRule.onNodeWithTag(tag).performClick()
    }
}
