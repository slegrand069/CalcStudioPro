package com.example.calcstudiopro.functions

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.calcstudiopro.MainActivity
import org.junit.Rule
import org.junit.Test

class FunctionStudyNavigationTest {
    @get:Rule val composeRule = createAndroidComposeRule<MainActivity>()

    @Test fun modeSelectorOpensTheSixFunctionList() {
        composeRule.onNodeWithTag("calculator-key-MODE").performClick()
        composeRule.onNodeWithTag("mode-option-functions").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("functions-session-title").assertIsDisplayed()
        (1..6).forEach { index ->
            composeRule.onNodeWithTag("function-slot-f$index").assertIsDisplayed()
        }
    }

    @Test fun tappingAFunctionRowOpensItsFullScreenEditor() {
        composeRule.onNodeWithTag("calculator-key-MODE").performClick()
        composeRule.onNodeWithTag("mode-option-functions").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("function-slot-f2").performClick()
        composeRule.onNodeWithTag("editor-expression-area").assertIsDisplayed()
        composeRule.onNodeWithText("Édition de f₂(x)").assertIsDisplayed()
    }

    @Test fun studiesEntryOpensS1WithoutChangingTheSixFunctionRows() {
        composeRule.onNodeWithTag("calculator-key-MODE").performClick()
        composeRule.onNodeWithTag("mode-option-functions").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("functions-studies").assertIsDisplayed().performClick()

        composeRule.onNodeWithTag("studies-back").assertIsDisplayed()
        composeRule.onNodeWithTag("study-new").assertIsDisplayed()
        composeRule.onNodeWithTag("study-save").assertIsDisplayed()
        composeRule.onNodeWithTag("study-save-as").assertIsDisplayed()
        composeRule.onNodeWithTag("study-open").assertIsDisplayed()
        composeRule.onNodeWithTag("study-current-card").assertIsDisplayed()
        composeRule.onNodeWithTag("study-recovery-card").assertIsDisplayed()

        composeRule.onNodeWithTag("studies-back").performClick()
        (1..6).forEach { index -> composeRule.onNodeWithTag("function-slot-f$index").assertIsDisplayed() }
    }
}
