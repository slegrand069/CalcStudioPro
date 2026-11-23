package com.example.calcstudiopro.functions

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
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
}
