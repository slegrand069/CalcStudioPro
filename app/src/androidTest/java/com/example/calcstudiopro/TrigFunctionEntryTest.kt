package com.example.calcstudiopro

import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test

class TrigFunctionEntryTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun directSineInsertsFunctionAndEvaluatesClosedArgument() {
        verifyFunction("Sin", "sin(", "90", "sin(90)", "1")
    }

    @Test
    fun directCosineInsertsFunctionAndEvaluatesClosedArgument() {
        verifyFunction("Cos", "cos(", "180", "cos(180)", "-1")
    }

    @Test
    fun directTangentInsertsFunctionAndEvaluatesClosedArgument() {
        verifyFunction("Tan", "tan(", "45", "tan(45)", "1")
    }

    @Test
    fun inverseSineInsertsFunctionAndEvaluatesClosedArgument() {
        verifyFunction("Sin", "asin(", "1", "asin(1)", "90", inverse = true)
    }

    @Test
    fun inverseCosineInsertsFunctionAndEvaluatesClosedArgument() {
        verifyFunction("Cos", "acos(", "0", "acos(0)", "90", inverse = true)
    }

    @Test
    fun inverseTangentInsertsFunctionAndEvaluatesClosedArgument() {
        verifyFunction("Tan", "atan(", "1", "atan(1)", "45", inverse = true)
    }

    @Test
    fun shiftLnStillTransformsThePreviousOperand() {
        tapKey("2")
        tapKey("Shift")
        tapKey("ln")

        assertExpression("exp(2)")
    }

    @Test
    fun shiftLogStillTransformsThePreviousOperand() {
        tapKey("2")
        tapKey("Shift")
        tapKey("Log")

        assertExpression("10^(2)")
    }

    private fun verifyFunction(
        key: String,
        insertedFunction: String,
        argument: String,
        completedExpression: String,
        expectedResult: String,
        inverse: Boolean = false
    ) {
        if (inverse) {
            tapKey("Shift")
        }
        tapKey(key)
        assertExpression(insertedFunction)

        argument.forEach { digit -> tapKey(digit.toString()) }
        tapKey(")")

        assertExpression(completedExpression)
        composeRule.onNodeWithTag("calculator-result")
            .assertTextEquals(expectedResult)
    }

    private fun tapKey(key: String) {
        composeRule.onNodeWithTag("calculator-key-$key").performClick()
    }

    private fun assertExpression(expected: String) {
        composeRule.onNodeWithTag("calculator-expression")
            .assertTextEquals(expected)
    }
}