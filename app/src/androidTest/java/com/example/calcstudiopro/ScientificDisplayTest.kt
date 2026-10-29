package com.example.calcstudiopro

import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class ScientificDisplayTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun resetSettings() = resetSettingsToDefaults()

    @Test
    fun switchingToSciReformatsTheCurrentDecimalResultAndBack() {
        listOf("1", "5", "0", "5", "0", "0", "=").forEach(::tap)
        assertResult("150500")

        toggleFormat()
        composeRule.onNodeWithTag("calculator-number-format-toggle").assertTextEquals("SCI")
        assertResult("150,5 × 10³")
        composeRule.onNodeWithTag("calculator-expression").assertTextEquals("150500")

        toggleFormat()
        composeRule.onNodeWithTag("calculator-number-format-toggle").assertTextEquals("NRM")
        assertResult("150500")
    }

    private fun toggleFormat() {
        composeRule.onNodeWithTag("calculator-number-format-toggle").performClick()
    }

    private fun tap(key: String) {
        composeRule.onNodeWithTag("calculator-key-$key").performClick()
    }

    private fun assertResult(expected: String) {
        composeRule.onNodeWithTag("calculator-result").assertTextEquals(expected)
    }
}
