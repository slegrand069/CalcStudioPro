package com.example.calcstudiopro.functions

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
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
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class FunctionStudyScreenTest {
    @get:Rule val composeRule = createComposeRule()

    @Before fun resetEditorKeyboardMode() {
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
            .getSharedPreferences("function_expression_editor", android.content.Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

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

    @Test fun letterPaletteDisablesTAndKeyboardTogglePreservesDraft() {
        mountEditor("a+x")
        composeRule.onNodeWithTag("key-4-4").performClick()
        composeRule.onNodeWithTag("letter-t").assertIsNotEnabled()
        composeRule.onNodeWithTag("letter-x").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("editor-expression").assertTextEquals("a+xx")
        composeRule.onNodeWithTag("editor-keyboard-toggle").performClick()
        composeRule.onAllNodesWithTag("math-keyboard").assertCountEquals(0)
        composeRule.onNodeWithTag("editor-expression").assertTextEquals("a+xx")
    }

    @Test fun validationFailureKeepsEditorAndText() {
        mountEditor("x+", validate = { study, id -> study.validateFunction(id, MathExpressionEngine()) })
        composeRule.onNodeWithTag("key-6-4").performClick()
        composeRule.onNodeWithTag("editor-validation-error").assertIsDisplayed()
        composeRule.onNodeWithTag("editor-expression").assertTextEquals("x+")
    }

    @Test fun androidInputNormalizesPointWithoutLosingTheCursor() {
        mountEditor("")
        composeRule.onNodeWithTag("editor-keyboard-toggle").performClick()
        composeRule.onNodeWithTag("editor-expression").performTextInput("12.5")
        composeRule.onNodeWithTag("editor-expression").assertTextEquals("12,5")
        composeRule.onNodeWithTag("editor-expression").performTextInputSelection(TextRange(2))
        composeRule.onNodeWithTag("editor-expression").performTextInput("+3")
        composeRule.onNodeWithTag("editor-expression").assertTextEquals("12+3,5")
    }

    private fun mountEditor(
        expression: String,
        validate: (FunctionStudy, FunctionSlotId) -> StudyValidationResult? = { study, id -> study.validateFunction(id, MathExpressionEngine()) }
    ) {
        val study = FunctionStudy.newWorkingSession().withDraft(FunctionSlotId.F1, expression)
        composeRule.setContent {
            CalcStudioProTheme {
                FunctionExpressionEditorScreen(
                    slot = study.slot(FunctionSlotId.F1),
                    study = study,
                    onDraftChange = {},
                    onValidate = { validate(study, FunctionSlotId.F1) },
                    onBack = {},
                    onValidated = {}
                )
            }
        }
    }
}
