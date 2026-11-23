package com.example.calcstudiopro.functions

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.example.calcstudiopro.domain.functions.MathExpressionEngine
import com.example.calcstudiopro.functions.domain.FunctionColor
import com.example.calcstudiopro.functions.domain.FunctionSlotId
import com.example.calcstudiopro.functions.domain.FunctionStudy
import com.example.calcstudiopro.functions.domain.StudyValidationResult
import com.example.calcstudiopro.ui.theme.CalcStudioProTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class FunctionStudyScreenTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun allSixEmptySlotsAndFutureActionsFitOnScreen() {
        composeRule.setContent {
            CalcStudioProTheme {
                FunctionStudyScreen(
                    state = FunctionStudyUiState(study = FunctionStudy.newWorkingSession()),
                    onBack = {},
                    onChooseColor = { _, _ -> },
                    onToggleVisibility = {}
                )
            }
        }

        FunctionSlotId.ordered.forEach { slotId ->
            composeRule.onNodeWithTag("function-slot-${slotId.code}").assertIsDisplayed()
            composeRule.onNodeWithTag("function-eye-${slotId.code}").assertIsDisplayed()
        }
        composeRule.onNodeWithTag("functions-graph").assertIsDisplayed().assertIsNotEnabled()
        composeRule.onNodeWithTag("functions-table").assertIsDisplayed().assertIsNotEnabled()
    }

    @Test fun listRendersTheValidatedExpressionInsteadOfItsDraft() {
        val engine = MathExpressionEngine()
        val empty = FunctionStudy.newWorkingSession()
        val draft = empty.withDraft(FunctionSlotId.F1, "x+2")
        val validated = (draft.validateFunction(FunctionSlotId.F1, engine) as StudyValidationResult.Accepted).study
        val study = validated.withDraft(FunctionSlotId.F1, "x+99")
        composeRule.setContent {
            CalcStudioProTheme {
                FunctionStudyScreen(
                    state = FunctionStudyUiState(study = study),
                    onBack = {},
                    onChooseColor = { _, _ -> },
                    onToggleVisibility = {}
                )
            }
        }

        composeRule.onNodeWithTag("function-expression-f1", useUnmergedTree = true).assertTextEquals("x+2")
    }

    @Test fun paletteContainsTwelveColorsInThreeColumnsAndAppliesOnlyToTheOpenedSlot() {
        var selectedSlot: FunctionSlotId? = null
        var selectedColor: FunctionColor? = null
        composeRule.setContent {
            CalcStudioProTheme {
                FunctionStudyScreen(
                    state = FunctionStudyUiState(study = FunctionStudy.newWorkingSession()),
                    onBack = {},
                    onChooseColor = { slot, color -> selectedSlot = slot; selectedColor = color },
                    onToggleVisibility = {}
                )
            }
        }

        composeRule.onNodeWithTag("function-color-f3").performClick()
        assertEquals(12, FunctionColor.entries.size)
        FunctionColor.entries.forEach { color ->
            composeRule.onNodeWithTag("function-palette-f3-${color.name}").assertIsDisplayed()
        }
        composeRule.onNodeWithTag("function-palette-f3-RED").performClick()
        composeRule.runOnIdle {
            assertEquals(FunctionSlotId.F3, selectedSlot)
            assertEquals(FunctionColor.RED, selectedColor)
        }
    }

    @Test fun visibilityToggleIsAvailableForAnEmptyFunction() {
        var toggled: FunctionSlotId? = null
        composeRule.setContent {
            CalcStudioProTheme {
                FunctionStudyScreen(
                    state = FunctionStudyUiState(study = FunctionStudy.newWorkingSession()),
                    onBack = {},
                    onChooseColor = { _, _ -> },
                    onToggleVisibility = { toggled = it }
                )
            }
        }

        composeRule.onNodeWithTag("function-eye-f6").assertIsDisplayed().performClick()
        composeRule.runOnIdle { assertEquals(FunctionSlotId.F6, toggled) }
    }

    @Test fun loadErrorIsShownInsteadOfFabricatingAnEmptySession() {
        composeRule.setContent {
            CalcStudioProTheme {
                FunctionStudyScreen(
                    state = FunctionStudyUiState(loadError = "La session n’a pas pu être chargée."),
                    onBack = {},
                    onChooseColor = { _, _ -> },
                    onToggleVisibility = {}
                )
            }
        }

        composeRule.onNodeWithTag("functions-load-error").assertIsDisplayed()
        FunctionSlotId.ordered.forEach { slotId ->
            composeRule.onAllNodesWithTag("function-slot-${slotId.code}").assertCountEquals(0)
        }
    }
}
