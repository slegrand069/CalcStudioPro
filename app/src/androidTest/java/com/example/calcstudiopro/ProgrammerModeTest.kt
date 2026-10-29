package com.example.calcstudiopro

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class ProgrammerModeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun resetSettings() = resetSettingsToDefaults()

    @Test
    fun changingBaseConvertsTheExpressionAndDisplayedResult() {
        tap("1")
        tap("0")
        tap("=")
        assertResult("10")

        tapBase()

        assertBase("BIN")
        assertExpression("1010")
        assertResult("1010")
    }

    @Test
    fun binaryModeDisablesDigitsOutsideTheBaseAndDecimalPoint() {
        tapBase()
        assertBase("BIN")

        composeRule.onNodeWithTag("calculator-key-2").assertIsNotEnabled()
        composeRule.onNodeWithTag("calculator-key-9").assertIsNotEnabled()
        composeRule.onNodeWithTag("calculator-key-.").assertIsNotEnabled()
        composeRule.onNodeWithTag("calculator-key-1").assertExists()
        composeRule.onNodeWithTag("calculator-key-ln").assertIsNotEnabled()
        composeRule.onNodeWithTag("calculator-key-ln").assertTextContains("ln")
    }

    @Test
    fun shiftedHexDigitsEnterLettersAndAndMatchesTheDocumentedExample() {
        repeat(3) { tapBase() }
        assertBase("HEX")

        tap("6")
        tapShift()
        tap("1") // A
        tap("AND")
        tap("3")
        tapShift()
        tap("2") // B
        assertExpression("6A AND 3B")
        tap("=")
        assertResult("2A")
    }

    @Test
    fun leftShiftChangesOnlyTheDisplayedResult() {
        tapBase()
        tap("1")
        tap("0")
        tap("1")
        assertExpression("101")
        assertResult("101")

        tap("LSh")

        assertExpression("101")
        assertResult("1010")
    }

    @Test
    fun parenthesesWorkInDecimalAndProgrammerExpressions() {
        tap("8")
        tap("−")
        tap("(")
        tap("8")
        tap("÷")
        tap("2")
        tap(")")
        tap("=")
        assertResult("4")

        repeat(3) { tapBase() }
        assertBase("HEX")
        tapShiftedHex("1") // A
        tapShiftedHex("2") // B
        tap("AND")
        tap("(")
        tapShiftedHex("2") // B
        tapShiftedHex("3") // C
        tap("OR")
        tap("1")
        tap("2")
        tap(")")
        tap("=")
        assertResult("AA")
    }

    @Test
    fun storeCompletesTheCurrentCalculationBeforeTheNextDigit() {
        tap("5")
        tap("5")
        tap("STO/RCL")

        composeRule.onNodeWithTag("calculator-key-STO/RCL").assertTextContains("STO")
        composeRule.onNodeWithTag("calculator-key-STO/RCL").assertTextContains("RCL")
        assertResult("55")

        tap("6")
        assertExpression("6")
    }

    private fun tapBase() {
        composeRule.onNodeWithTag("calculator-base-toggle").performClick()
    }

    private fun tapShift() {
        composeRule.onNodeWithTag("calculator-shift-inactive").performClick()
    }

    private fun tapShiftedHex(key: String) {
        tapShift()
        tap(key)
    }

    private fun tap(key: String) {
        composeRule.onNodeWithTag("calculator-key-$key").performClick()
    }

    private fun assertBase(expected: String) {
        composeRule.onNodeWithTag("calculator-base-toggle").assertTextEquals(expected)
    }

    private fun assertExpression(expected: String) {
        composeRule.onNodeWithTag("calculator-expression").assertTextEquals(expected)
    }

    private fun assertResult(expected: String) {
        composeRule.onNodeWithTag("calculator-result").assertTextEquals(expected)
    }
}
