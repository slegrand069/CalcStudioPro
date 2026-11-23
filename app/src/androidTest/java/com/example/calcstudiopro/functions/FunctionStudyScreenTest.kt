package com.example.calcstudiopro.functions

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextInputSelection
import androidx.compose.ui.text.TextRange
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

    @Test fun editorShowsExactlySevenByFiveKeyboardRows() {
        mountEditor("")
        for (row in 0..6) for (column in 0..4) {
            composeRule.onNodeWithTag("key-$row-$column").assertIsDisplayed()
        }
        composeRule.onNodeWithTag("editor-expression-area").assertIsDisplayed()
    }

    @Test fun darkEditorKeepsEveryMathKeyInsideTheVisibleScreen() {
        mountEditor("x+1", darkTheme = true)
        composeRule.waitForIdle()
        val screenBottom = composeRule.onRoot().fetchSemanticsNode().boundsInRoot.bottom
        for (row in 0..6) for (column in 0..4) {
            val bounds = composeRule.onNodeWithTag("key-$row-$column").fetchSemanticsNode().boundsInRoot
            assert(bounds.top >= 0f && bounds.bottom <= screenBottom) { "Key $row/$column is outside screen: $bounds / $screenBottom" }
        }
        composeRule.onNodeWithTag("editor-back").assertIsDisplayed()
        composeRule.onNodeWithTag("editor-expression-area").assertIsDisplayed()
    }

    @Test fun insertionReplacesSelectionAndShiftSelectsInverseFunctions() {
        mountEditor("x+1")
        composeRule.onNodeWithTag("editor-expression").performTextInputSelection(TextRange(0, 1))
        composeRule.onNodeWithTag("key-0-1").performClick()
        composeRule.onNodeWithTag("editor-expression").assertTextEquals("sin(x)+1")

        composeRule.onNodeWithTag("key-0-0").performClick()
        composeRule.onNodeWithTag("key-0-1").performClick()
        composeRule.onNodeWithTag("editor-expression").assertTextEquals("sin(x)asin(+1")
    }

    @Test fun clearCanBeUndoneAndAdvancedPaletteInsertsFunctions() {
        mountEditor("x+2")
        composeRule.onNodeWithTag("key-6-0").performClick()
        composeRule.onNodeWithTag("editor-confirm-clear").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("editor-undo").performClick()
        composeRule.onNodeWithTag("editor-expression").assertTextEquals("x+2")

        composeRule.onNodeWithTag("key-5-4").performClick()
        composeRule.onNodeWithTag("advanced-min").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("editor-expression").assertTextEquals("x+2min(")
    }

    @Test fun letterPaletteDisablesTAndMathKeyboardIsAlwaysPresent() {
        mountEditor("a+x")
        composeRule.onNodeWithTag("key-4-4").performClick()
        composeRule.onNodeWithTag("letter-t").assertIsNotEnabled()
        composeRule.onNodeWithTag("letter-x").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("editor-expression").assertTextEquals("a+xx")
        composeRule.onNodeWithTag("editor-expression").performClick()
        composeRule.onNodeWithTag("math-keyboard").assertIsDisplayed()
        composeRule.onAllNodesWithTag("editor-keyboard-toggle").assertCountEquals(0)
        composeRule.onNodeWithTag("editor-expression").assertTextEquals("a+xx")
    }

    @Test fun touchingTheExpressionKeepsSystemImeClosed() {
        mountEditor("x+1", reportImeVisibility = true)
        composeRule.onNodeWithTag("editor-expression").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("ime-visible").assertTextEquals("false")
        for (row in 0..6) for (column in 0..4) {
            composeRule.onNodeWithTag("key-$row-$column").assertIsDisplayed()
        }
    }

    @Test fun validationFailureKeepsEditorAndText() {
        mountEditor("x+", validate = { study, id -> study.validateFunction(id, MathExpressionEngine()) })
        composeRule.onNodeWithTag("key-6-4").performClick()
        composeRule.onNodeWithTag("editor-validation-error").assertIsDisplayed()
        composeRule.onNodeWithTag("editor-expression").assertTextEquals("x+")
    }

    @Test fun arrowMovesCursorAndMathKeyInsertsAtTheNewPosition() {
        mountEditor("x+1")
        composeRule.onNodeWithTag("editor-expression").performTextInputSelection(TextRange(2))
        composeRule.onNodeWithTag("editor-cursor-left").performClick()
        composeRule.onNodeWithTag("key-4-0").performClick()
        composeRule.onNodeWithTag("editor-expression").assertTextEquals("x1+1")
    }

    private fun mountEditor(
        expression: String,
        darkTheme: Boolean = false,
        reportImeVisibility: Boolean = false,
        validate: (FunctionStudy, FunctionSlotId) -> StudyValidationResult? = { study, id -> study.validateFunction(id, MathExpressionEngine()) }
    ) {
        val study = FunctionStudy.newWorkingSession().withDraft(FunctionSlotId.F1, expression)
        composeRule.setContent {
            CalcStudioProTheme(darkTheme = darkTheme) {
                Box {
                    FunctionExpressionEditorScreen(
                        slot = study.slot(FunctionSlotId.F1),
                        study = study,
                        onDraftChange = {},
                        onValidate = { validate(study, FunctionSlotId.F1) },
                        onBack = {},
                        onValidated = {}
                    )
                    if (reportImeVisibility) ImeVisibilityProbe()
                }
            }
        }
    }

    @OptIn(ExperimentalLayoutApi::class)
    @Composable
    private fun ImeVisibilityProbe() {
        Text(WindowInsets.isImeVisible.toString(), modifier = Modifier.size(1.dp).testTag("ime-visible"))
    }
}
