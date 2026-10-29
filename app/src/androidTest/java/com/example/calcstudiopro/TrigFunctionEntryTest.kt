package com.example.calcstudiopro

import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class TrigFunctionEntryTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun resetSettings() = resetSettingsToDefaults()
    private var expectedShiftActive = false

    @Test
    fun equalsStartsANewExpressionForNumbersAndFunctions() {
        enterFiveAndEvaluate()
        tapKey("7")
        assertExpression("7")
        assertResult("7")

        resetExpression()
        enterFiveAndEvaluate()
        tapKey("Sin")
        assertExpression("sin(")

        resetExpression()
        enterFiveAndEvaluate()
        tapShift()
        tapKey("Sin")
        assertExpression("asin(")
        assertShiftState(false)
    }

    @Test
    fun basicOperatorsContinueFromThePreviousResultAfterEquals() {
        assertOperatorContinuation("+", "5+", "4", "9")
        assertOperatorContinuation("−", "5−", "4", "1")
        assertOperatorContinuation("×", "5×", "4", "20")
        assertOperatorContinuation("÷", "5÷", "4", "1.25")
    }

    @Test
    fun squareReciprocalAndFactorialContinueFromThePreviousResultAfterEquals() {
        enterFiveAndEvaluate()
        tapKey("x²")
        assertExpression("5^2")
        assertResult("25")

        resetExpression()
        enterFiveAndEvaluate()
        tapKey("1/x")
        assertExpression("1/(5)")
        assertResult("0.2")

        resetExpression()
        enterFiveAndEvaluate()
        tapKey("x!")
        assertExpression("5!")
        assertResult("120")
    }

    @Test
    fun shiftedSquareRootStartsANewExpressionAfterEquals() {
        enterFiveAndEvaluate()
        tapShift()
        tapKey("x²")
        assertExpression("sqrt(")
        assertShiftState(false)
    }

    @Test
    fun everyShiftFunctionCanStartWithAnEmptyExpression() {
        assertShiftedInsertion("ln", "exp(")
        assertShiftedInsertion("Log", "pow(10,")
        assertShiftedInsertion("Sin", "asin(")
        assertShiftedInsertion("Cos", "acos(")
        assertShiftedInsertion("Tan", "atan(")
        assertShiftedInsertion("x²", "sqrt(")
        assertShiftedInsertion("xʸ", "pow(")
        assertShiftedInsertion("π", "e")
        assertShiftedInsertion("STO/RCL", "0")
    }

    @Test
    fun everyShiftFunctionWorksAfterAnExistingOperand() {
        assertShiftedInsertionAfterOperand("ln", "2×exp(")
        assertShiftedInsertionAfterOperand("Log", "2×pow(10,")
        assertShiftedInsertionAfterOperand("Sin", "2×asin(")
        assertShiftedInsertionAfterOperand("Cos", "2×acos(")
        assertShiftedInsertionAfterOperand("Tan", "2×atan(")
        assertShiftedInsertionAfterOperand("x²", "2×sqrt(")
        assertShiftedInsertionAfterOperand("xʸ", "2×pow(")
        assertShiftedInsertionAfterOperand("π", "2×e")
        assertShiftedInsertionAfterOperand("STO/RCL", "2×0")
    }

    @Test
    fun shiftedFunctionsCanFollowAnOperator() {
        tapKey("2")
        tapKey("+")
        tapShift()
        tapKey("x²")
        assertExpression("2+sqrt(")
        assertShiftState(false)
        tapKey("9")
        tapKey(")")
        assertExpression("2+sqrt(9)")
        assertResult("5")
    }

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
        tapShift()
        verifyFunction("Sin", "asin(", "1", "asin(1)", "90", inverse = true)
    }

    @Test
    fun inverseCosineInsertsFunctionAndEvaluatesClosedArgument() {
        tapShift()
        verifyFunction("Cos", "acos(", "0", "acos(0)", "90", inverse = true)
    }

    @Test
    fun inverseTangentInsertsFunctionAndEvaluatesClosedArgument() {
        tapShift()
        verifyFunction("Tan", "atan(", "1", "atan(1)", "45", inverse = true)
    }

    @Test
    fun shiftedExponentialAndPowerOfTenEvaluateCompleteFunctions() {
        tapShift()
        tapKey("ln")
        assertExpression("exp(")
        tapKey("2")
        tapKey(")")
        assertExpression("exp(2)")
        assertResult("7.3890560989")

        resetExpression()
        tapShift()
        tapKey("Log")
        assertExpression("pow(10,")
        assertShiftState(false)
        tapKey("2")
        tapKey(")")
        assertExpression("pow(10,2)")
        assertResult("100")
    }

    @Test
    fun shiftedSquareRootHandlesPositiveAndZeroArguments() {
        tapShift()
        tapKey("x²")
        assertExpression("sqrt(")
        assertShiftState(false)
        tapKey("9")
        tapKey(")")
        assertExpression("sqrt(9)")
        assertResult("3")

        resetExpression()
        tapShift()
        tapKey("x²")
        tapKey("0")
        tapKey(")")
        assertResult("0")
    }

    @Test
    fun shiftedPowerAcceptsTwoArgumentsAndNestedFunctions() {
        tapShift()
        tapKey("xʸ")
        assertExpression("pow(")
        assertShiftState(false)
        tapShift()
        tapKey("x²")
        assertExpression("pow(sqrt(")
        tapKey("1")
        tapKey("6")
        tapKey(")")
        tapShift()
        tapKey(".", consumesShift = true)
        assertShiftState(false)
        assertExpression("pow(sqrt(16),")
        tapKey("2")
        tapKey(")")
        assertExpression("pow(sqrt(16),2)")
        assertResult("16")
    }

    @Test
    fun shiftedPowerSupportsDecimalArgumentsAndNegativeExponents() {
        tapShift()
        tapKey("xʸ")
        tapKey("2")
        tapKey(".")
        tapKey("5")
        tapShift()
        tapKey(".", consumesShift = true)
        tapKey("2")
        tapKey(")")
        assertExpression("pow(2.5,2)")
        assertResult("6.25")

        resetExpression()
        tapShift()
        tapKey("xʸ")
        tapKey("2")
        tapShift()
        tapKey(".", consumesShift = true)
        tapKey("−")
        tapKey("3")
        tapKey(")")
        assertExpression("pow(2,−3)")
        assertResult("0.125")
    }

    @Test
    fun shiftedPiInsertsEvaluableEAndMultipliesExplicitlyAfterAnOperand() {
        tapShift()
        tapKey("π")
        assertExpression("e")
        assertShiftState(false)
        assertResult("2.7182818285")

        resetExpression()
        tapKey("2")
        tapShift()
        tapKey("π")
        assertExpression("2×e")
        assertResult("5.4365636569")
    }

    @Test
    fun memoryCanBeStoredRecalledAndClearedWithoutImplicitMultiplication() {
        tapKey("3")
        tapKey("STO/RCL")
        assertExpression("3")

        tapKey("AC")
        tapShift()
        tapKey("STO/RCL")
        assertExpression("3")
        assertShiftState(false)

        tapKey("AC")
        tapKey("2")
        tapShift()
        tapKey("STO/RCL")
        assertExpression("2×3")
        assertResult("6")

        tapKey("AC")
        tapKey("CLM")
        tapShift()
        tapKey("STO/RCL")
        assertExpression("0")
        assertResult("0")
    }

    @Test
    fun invalidIncompletePowerCanBeCancelledWithoutLeavingShiftState() {
        tapShift()
        tapKey("xʸ")
        tapKey("2")
        tapKey(")")
        tapKey("=")
        composeRule.onNodeWithTag("calculator-error")
            .assertTextEquals("Syntaxe incorrecte")

        tapKey("AC")
        assertShiftState(false)
        tapKey("7")
        assertExpression("7")
        assertResult("7")
    }

    @Test
    fun shiftedDecimalKeyOnlySeparatesPowArguments() {
        tapShift()
        tapKey(".", consumesShift = false)
        assertExpression("0")
        assertShiftState(true)

        tapKey("AC")
        tapShift()
        tapKey("xʸ")
        tapKey("2")
        tapShift()
        tapKey(".", consumesShift = true)
        assertExpression("pow(2,")
        assertShiftState(false)
    }

    private fun assertShiftedInsertion(key: String, expected: String) {
        tapShift()
        assertShiftState(true)
        tapKey(key)
        assertExpression(expected)
        assertShiftState(false)
        resetExpression()
    }

    private fun assertShiftedInsertionAfterOperand(key: String, expected: String) {
        tapKey("2")
        tapShift()
        tapKey(key)
        assertExpression(expected)
        assertShiftState(false)
        resetExpression()
    }

    private fun enterFiveAndEvaluate() {
        tapKey("2")
        tapKey("+")
        tapKey("3")
        tapKey("=")
        assertResult("5")
    }

    private fun assertOperatorContinuation(
        operator: String,
        expectedExpression: String,
        nextOperand: String,
        expectedResult: String
    ) {
        resetExpression()
        enterFiveAndEvaluate()
        tapKey(operator)
        assertExpression(expectedExpression)
        nextOperand.forEach { tapKey(it.toString()) }
        tapKey("=")
        assertResult(expectedResult)
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
            assertShiftState(true)
        }
        tapKey(key)
        assertExpression(insertedFunction)

        argument.forEach { digit -> tapKey(digit.toString()) }
        tapKey(")")

        assertExpression(completedExpression)
        assertResult(expectedResult)
    }

    private fun resetExpression() {
        tapKey("AC")
        expectedShiftActive = false
        assertShiftState(false)
    }

    private fun tapShift() {
        val tag = if (expectedShiftActive) {
            "calculator-shift-active"
        } else {
            "calculator-shift-inactive"
        }
        composeRule.onNodeWithTag(tag).performClick()
        expectedShiftActive = !expectedShiftActive
    }

    private fun tapKey(key: String, consumesShift: Boolean = key in SHIFT_KEYS) {
        composeRule.onNodeWithTag("calculator-key-$key").performClick()
        if (key == "AC") {
            expectedShiftActive = false
        } else if (expectedShiftActive && consumesShift) {
            expectedShiftActive = false
        }
    }

    private fun assertShiftState(active: Boolean) {
        val tag = if (active) "calculator-shift-active" else "calculator-shift-inactive"
        composeRule.onNodeWithTag(tag).assertTextEquals("Shift")
        expectedShiftActive = active
    }

    private fun assertExpression(expected: String) {
        composeRule.onNodeWithTag("calculator-expression")
            .assertTextEquals(expected)
    }

    private fun assertResult(expected: String) {
        composeRule.onNodeWithTag("calculator-result")
            .assertTextEquals(expected)
    }

    private companion object {
        val SHIFT_KEYS = setOf("STO/RCL", "ln", "Log", "Sin", "Cos", "Tan", "x²", "xʸ", "π")
    }
}
