package com.example.calcstudiopro.domain.functions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI

class MathExpressionEngineTest {
    private val engine = MathExpressionEngine()

    @Test fun arithmeticAndPrecedence() {
        assertValue("1+2*3", 7.0)
        assertValue("8/4*2", 4.0)
        assertValue("6/2(1+2)", 9.0)
        assertValue("(1+2)(4-1)", 9.0)
    }

    @Test fun implicitMultiplicationIncludesVariablesAndFunctions() {
        assertValue("2x", 6.0, x = 3.0)
        assertValue("3(x+1)", 12.0, x = 3.0)
        assertValue("sin(x)cos(x)", kotlin.math.sin(0.5) * kotlin.math.cos(0.5), x = 0.5)
        assertValue("xsin(x)", 0.5 * kotlin.math.sin(0.5), x = 0.5)
    }

    @Test fun powersAreRightAssociativeAndBindBeforeUnarySigns() {
        assertValue("2^3^2", 512.0)
        assertValue("-2^2", -4.0)
        assertValue("(-2)^2", 4.0)
        assertValue("2^-2", 0.25)
    }

    @Test fun trigonometrySupportsRadiansAndDegrees() {
        assertValue("sin(pi/2)", 1.0)
        assertValue("sin(90)", 1.0, angleMode = FunctionAngleMode.DEG)
        assertValue("asin(1)", 90.0, angleMode = FunctionAngleMode.DEG)
        assertValue("ATAN(1)", PI / 4.0)
    }

    @Test fun logarithmsAndRoots() {
        assertValue("ln(e)", 1.0)
        assertValue("log(100)", 2.0)
        assertValue("sqrt(9)+cbrt(-8)", 1.0)
        assertValue("root(3; -8)", -2.0)
        assertValue("root(2; 9)", 3.0)
        assertValue("root(-2; 4)", 0.5)
    }

    @Test fun basicAndVariadicFunctions() {
        assertValue("abs(-3)+floor(2,9)+ceil(2,1)+sign(-5)", 7.0)
        assertValue("min(4; -2; 7; 1)", -2.0)
        assertValue("max(4; -2; 7; 1)", 7.0)
        assertValue("10^2", 100.0)
    }

    @Test fun parametersDefaultToOneAndCanBeOverridden() {
        assertValue("a*x+b", 4.0, x = 3.0)
        assertValue("a*x+b", 11.0, x = 3.0, parameters = mapOf('a' to 2.0, 'B' to 5.0))
    }

    @Test fun compilationCanBeEvaluatedRepeatedlyWithoutChangingExpression() {
        val compiled = compile("a*x^2+sin(x)")
        assertEquals(1.0 + kotlin.math.sin(1.0), value(compiled.evaluate(1.0)), 1e-9)
        assertEquals(4.0 + kotlin.math.sin(2.0), value(compiled.evaluate(2.0)), 1e-9)
        assertEquals(18.0 + kotlin.math.sin(3.0), value(compiled.evaluate(3.0, mapOf('a' to 2.0))), 1e-9)
    }

    @Test fun reportsTypedDomainAndEvaluationErrors() {
        assertTrue(failure("sqrt(-1)") is MathExpressionError.Domain)
        assertTrue(failure("ln(0)") is MathExpressionError.Domain)
        assertTrue(failure("tan(pi/2)") is MathExpressionError.Domain)
        assertTrue(failure("root(0; 1)") is MathExpressionError.Domain)
        assertTrue(failure("root(2,5; 4)") is MathExpressionError.Domain)
        assertTrue(failure("root(4; -16)") is MathExpressionError.Domain)
        assertTrue(failure("root(-2; 0)") is MathExpressionError.Domain)
        assertTrue(failure("1/0") is MathExpressionError.Domain)
        assertTrue(failure("(-2)^0,5") is MathExpressionError.Domain)
        assertTrue(failure("exp(1000)") is MathExpressionError.NonFiniteResult)
        assertTrue(failure("t+1") is MathExpressionError.InvalidVariable)
    }

    @Test fun reportsSyntaxParenthesisUnknownFunctionAndArityErrors() {
        val missing = engine.compile("(x+1") as ExpressionCompilation.Failure
        assertTrue(missing.error is MathExpressionError.MissingParenthesis)
        assertTrue(missing.error.position != null)
        assertTrue(engine.compile("foo(x)") is ExpressionCompilation.Failure)
        assertTrue((engine.compile("foo(x)") as ExpressionCompilation.Failure).error is MathExpressionError.UnknownFunction)
        assertTrue(failure("sqrt(1; 2)") is MathExpressionError.ArgumentCount)
        assertTrue(failure("min(1)") is MathExpressionError.ArgumentCount)
        assertTrue(engine.compile("x+@") is ExpressionCompilation.Failure)
    }

    private fun assertValue(
        source: String,
        expected: Double,
        x: Double = 0.0,
        parameters: Map<Char, Double> = emptyMap(),
        angleMode: FunctionAngleMode = FunctionAngleMode.RAD
    ) {
        assertEquals(source, expected, value(compile(source).evaluate(x, parameters, angleMode)), 1e-9)
    }

    private fun compile(source: String): CompiledMathExpression = when (val result = engine.compile(source)) {
        is ExpressionCompilation.Success -> result.expression
        is ExpressionCompilation.Failure -> throw AssertionError("Could not compile $source: ${result.error}")
    }

    private fun value(result: ExpressionEvaluation): Double = when (result) {
        is ExpressionEvaluation.Success -> result.value
        is ExpressionEvaluation.Failure -> throw AssertionError("Evaluation failed: ${result.error}")
    }

    private fun failure(source: String): MathExpressionError {
        return when (val compilation = engine.compile(source)) {
            is ExpressionCompilation.Failure -> compilation.error
            is ExpressionCompilation.Success -> when (val result = compilation.expression.evaluate(0.0)) {
                is ExpressionEvaluation.Failure -> result.error
                is ExpressionEvaluation.Success -> throw AssertionError("Expected $source to fail, got ${result.value}")
            }
        }
    }
}
