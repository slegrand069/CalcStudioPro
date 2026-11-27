package com.example.calcstudiopro.domain.functions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sin
import kotlin.math.sqrt

class MathExpressionDifferentiationTest {
    private val engine = MathExpressionEngine()

    @Test fun analyticPolynomialProductAndQuotientRules() {
        checkJet("5", 2.0, expected = Triple(5.0, 0.0, 0.0))
        checkJet("x", 2.0, expected = Triple(2.0, 1.0, 0.0))
        checkJet("x^2", 3.0, expected = Triple(9.0, 6.0, 2.0))
        checkJet("x^3-3x", 2.0, expected = Triple(2.0, 9.0, 12.0))
        checkJet("x*(x+1)", 2.0, expected = Triple(6.0, 5.0, 2.0))
        checkJet("1/x", 2.0, expected = Triple(0.5, -0.25, 0.25))
        checkJet("-x/2", 4.0, expected = Triple(-2.0, -0.5, 0.0))
    }

    @Test fun exponentialLogarithmicAndComposedRules() {
        checkJet("exp(x)", 0.7, expected = Triple(exp(0.7), exp(0.7), exp(0.7)))
        checkJet("exp(x^2)", 0.5, expected = Triple(exp(0.25), exp(0.25), 3.0 * exp(0.25)))
        checkJet("ln(x)", 2.0, expected = Triple(ln(2.0), 0.5, -0.25))
        checkJet("log(x)", 10.0, expected = Triple(1.0, 1.0 / (10.0 * ln(10.0)), -1.0 / (100.0 * ln(10.0))))
        checkJet("sqrt(x)", 4.0, expected = Triple(2.0, 0.25, -0.03125))
        checkJet("cbrt(x)", -8.0, expected = Triple(-2.0, 1.0 / 12.0, 1.0 / 144.0))
        checkJet("root(3;-8)", 1.0, expected = Triple(-2.0, 0.0, 0.0))
    }

    @Test fun trigonometricRulesRespectRadiansAndDegrees() {
        checkJet("sin(x)", 0.0, mode = FunctionAngleMode.RAD, expected = Triple(0.0, 1.0, 0.0))
        checkJet("sin(x)", 0.0, mode = FunctionAngleMode.DEG, expected = Triple(0.0, Math.PI / 180.0, 0.0))
        checkJet("cos(x)", 0.0, mode = FunctionAngleMode.DEG,
            expected = Triple(1.0, 0.0, -(Math.PI / 180.0) * (Math.PI / 180.0)))
        checkJet("atan(x)", 0.0, mode = FunctionAngleMode.DEG, expected = Triple(0.0, 180.0 / Math.PI, 0.0))
        checkJet("sin(x^2)", 0.7, expected = Triple(sin(0.49), 1.4 * cos(0.49), 2.0 * cos(0.49) - 1.96 * sin(0.49)))
        checkJet("tan(x)", 0.2, expected = Triple(kotlin.math.tan(0.2), 1.0 / cos(0.2).let { it * it }, 2.0 * kotlin.math.tan(0.2) / cos(0.2).let { it * it }))
        checkJet("asin(x)", 0.0, mode = FunctionAngleMode.DEG, expected = Triple(0.0, 180.0 / Math.PI, 0.0))
        checkJet("acos(x)", 0.0, mode = FunctionAngleMode.RAD, expected = Triple(Math.PI / 2.0, -1.0, 0.0))
    }

    @Test fun powersDistinguishStructuralExponentDependency() {
        val xToX = compiled("x^x").evaluateDifferentiated(2.0)
        val expectedValue = 4.0
        val expectedFirst = expectedValue * (ln(2.0) + 1.0)
        val expectedSecond = expectedValue * ((ln(2.0) + 1.0) * (ln(2.0) + 1.0) + 0.5)
        assertJet(xToX, expectedValue, expectedFirst, expectedSecond)

        checkJet("(-2)^3", 1.0, expected = Triple(-8.0, 0.0, 0.0))
        val variableExponentAtStationaryPoint = compiled("(-2)^(x^2+2)").evaluateDifferentiated(0.0)
        assertTrue(variableExponentAtStationaryPoint.value is ExpressionEvaluation.Success)
        assertTrue(variableExponentAtStationaryPoint.firstDerivative is DerivativeEvaluation.Failure)
        assertTrue(variableExponentAtStationaryPoint.secondDerivative is DerivativeEvaluation.Failure)

        checkJet("pow(x;2)", 3.0, expected = Triple(9.0, 6.0, 2.0))
        val negativeBaseVariableExponent = compiled("(-2)^x").evaluateDifferentiated(2.0)
        assertTrue(negativeBaseVariableExponent.value is ExpressionEvaluation.Success)
        assertFailure(negativeBaseVariableExponent.firstDerivative, DerivativeFailure.Indeterminate::class.java)
    }

    @Test fun parameterMetadataIsStructuralImmutableAndAngleAware() {
        val expression = compiled("a*sin(k*x)+b")
        assertTrue(expression.metadata.dependsOnX)
        assertEquals(setOf('a', 'k', 'b'), expression.metadata.parameterDependencies)
        assertTrue(expression.metadata.dependsOnAngleMode)
        assertFalse(compiled("a+b").metadata.dependsOnX)
        assertEquals(setOf('a', 'b'), compiled("a+b").metadata.parameterDependencies)
        assertFalse(compiled("x^2+1").metadata.dependsOnAngleMode)
        @Suppress("UNCHECKED_CAST")
        val mutable = expression.metadata.parameterDependencies as MutableSet<Char>
        assertTrue(runCatching { mutable.add('q') }.isFailure)
    }

    @Test fun parametersAreConstantsAndDiscreteSmoothRegionsHaveZeroDerivatives() {
        checkJet("a*x^2+b", 2.0, parameters = mapOf('a' to 3.0, 'b' to 4.0), expected = Triple(16.0, 12.0, 6.0))
        checkJet("floor(x)", 2.2, expected = Triple(2.0, 0.0, 0.0))
        checkJet("ceil(x)", 2.2, expected = Triple(3.0, 0.0, 0.0))
        checkJet("sign(x)", -2.0, expected = Triple(-1.0, 0.0, 0.0))
        checkJet("min(x;2)", 1.0, expected = Triple(1.0, 1.0, 0.0))
        checkJet("max(x;2)", 3.0, expected = Triple(3.0, 1.0, 0.0))
        checkJet("root(3;x)", -8.0, expected = Triple(-2.0, 1.0 / 12.0, 1.0 / 144.0))
        checkJet("root(-3;x)", 8.0, expected = Triple(0.5, -1.0 / 48.0, 1.0 / 288.0))
        val varyingDegree = compiled("root(x;8)").evaluateDifferentiated(3.0)
        assertTrue(varyingDegree.value is ExpressionEvaluation.Success)
        assertFailure(varyingDegree.firstDerivative, DerivativeFailure.Unsupported::class.java)
    }

    @Test fun constantDerivativeIsNotInferredFromPointwiseCancellation() {
        val expression = compiled("(-2)^(x^2+2)")
        assertTrue(expression.metadata.dependsOnX)
        val atZero = expression.evaluateDifferentiated(0.0)
        assertTrue(atZero.value is ExpressionEvaluation.Success)
        assertTrue(atZero.firstDerivative is DerivativeEvaluation.Failure)
    }

    @Test fun nonsmoothAndDiscreteFunctionsNeverClaimUnsupportedCertainty() {
        val absX = compiled("abs(x)").evaluateDifferentiated(0.0)
        assertFailure(absX.firstDerivative, DerivativeFailure.NotDefined::class.java)
        val absXSquared = compiled("abs(x^2)").evaluateDifferentiated(0.0)
        assertJet(absXSquared, 0.0, 0.0, 2.0)
        val sqrtXSquared = compiled("sqrt(x^2)").evaluateDifferentiated(0.0)
        assertFailure(sqrtXSquared.firstDerivative, DerivativeFailure.NotDefined::class.java)
        val absXCubed = compiled("abs(x^3)").evaluateDifferentiated(0.0)
        assertJet(absXCubed, 0.0, 0.0, 0.0)
        val productWithNonsmoothChild = compiled("x*abs(x)").evaluateDifferentiated(0.0)
        assertFailure(productWithNonsmoothChild.firstDerivative, DerivativeFailure.Indeterminate::class.java)
        val floorAtInteger = compiled("floor(x)").evaluateDifferentiated(2.0)
        assertFailure(floorAtInteger.firstDerivative, DerivativeFailure.NotDefined::class.java)
        val floorOfSquareAtZero = compiled("floor(x^2)").evaluateDifferentiated(0.0)
        assertJet(floorOfSquareAtZero, 0.0, 0.0, 0.0)
        val minCrossing = compiled("min(x;-x)").evaluateDifferentiated(0.0)
        assertFailure(minCrossing.firstDerivative, DerivativeFailure.NotDefined::class.java)
        val minSameSlope = compiled("min(x;x^2)").evaluateDifferentiated(0.0)
        assertFailure(minSameSlope.firstDerivative, DerivativeFailure.NotDefined::class.java)
        val floorDownwardAtThreshold = compiled("floor(-x^2)").evaluateDifferentiated(0.0)
        assertFailure(floorDownwardAtThreshold.firstDerivative, DerivativeFailure.NotDefined::class.java)
        val signOfSquareAtZero = compiled("sign(x^2)").evaluateDifferentiated(0.0)
        assertFailure(signOfSquareAtZero.firstDerivative, DerivativeFailure.NotDefined::class.java)
        val maxQuadraticAndZero = compiled("max(x^2;0)").evaluateDifferentiated(0.0)
        assertEquals(0.0, finite(maxQuadraticAndZero.firstDerivative), 0.0)
        assertEquals(2.0, finite(maxQuadraticAndZero.secondDerivative), 0.0)
        val powerOfSingularBase = compiled("sqrt(x)^3").evaluateDifferentiated(0.0)
        assertFailure(powerOfSingularBase.secondDerivative, DerivativeFailure.Indeterminate::class.java)
    }

    @Test fun valueAndDerivativeStatusesAreIndependent() {
        val domainFailure = compiled("ln(x)").evaluateDifferentiated(-1.0)
        assertTrue(domainFailure.value is ExpressionEvaluation.Failure)
        assertFailure(domainFailure.firstDerivative, DerivativeFailure.FunctionOutOfDomain::class.java)
        assertFailure(domainFailure.secondDerivative, DerivativeFailure.FunctionOutOfDomain::class.java)

        val functionOverflow = compiled("exp(1000*x)").evaluateDifferentiated(1.0)
        assertTrue(functionOverflow.value is ExpressionEvaluation.Failure)
        assertFailure(functionOverflow.firstDerivative, DerivativeFailure.NonFinite::class.java)
        assertFailure(functionOverflow.secondDerivative, DerivativeFailure.NonFinite::class.java)

        val tinyPositive = compiled("sqrt(x)").evaluateDifferentiated(1e-320)
        assertTrue(tinyPositive.value is ExpressionEvaluation.Success)
        assertTrue(tinyPositive.firstDerivative is DerivativeEvaluation.Value)
        assertFailure(tinyPositive.secondDerivative, DerivativeFailure.NonFinite::class.java)

        val boundary = compiled("sqrt(x)").evaluateDifferentiated(0.0)
        assertTrue(boundary.value is ExpressionEvaluation.Success)
        assertFailure(boundary.firstDerivative, DerivativeFailure.NotDefined::class.java)

        val underflow = compiled("exp(-1000*x)").evaluateDifferentiated(1.0)
        assertEquals(0.0, (underflow.value as ExpressionEvaluation.Success).value, 0.0)
        assertFailure(underflow.firstDerivative, DerivativeFailure.NumericallyUnstable::class.java)
        assertTrue(underflow.valueDiagnostic is DerivativeFailure.NumericallyUnstable)
    }

    @Test fun scalarEvaluationAndDifferentiatedValueAgreeAndFiniteDifferencesCheckRegularPoints() {
        val sources = listOf("x^3-3x", "exp(x^2)", "sin(x)", "ln(x)", "sqrt(x)", "x^x")
        for (source in sources) {
            val expression = compiled(source)
            val x = when (source) { "ln(x)", "sqrt(x)", "x^x" -> 1.7; else -> 0.37 }
            val ordinary = expression.evaluate(x)
            val differentiated = expression.evaluateDifferentiated(x)
            assertEquals((ordinary as ExpressionEvaluation.Success).value, (differentiated.value as ExpressionEvaluation.Success).value, 0.0)

            val h = 1e-4 * maxOf(1.0, abs(x))
            val plus = (expression.evaluate(x + h) as ExpressionEvaluation.Success).value
            val center = (expression.evaluate(x) as ExpressionEvaluation.Success).value
            val minus = (expression.evaluate(x - h) as ExpressionEvaluation.Success).value
            val finiteDifferenceFirst = (plus - minus) / (2.0 * h)
            val finiteDifferenceSecond = (plus - 2.0 * center + minus) / (h * h)
            assertEquals(finiteDifferenceFirst, finite(differentiated.firstDerivative), 3e-5 * maxOf(1.0, abs(finiteDifferenceFirst)))
            assertEquals(finiteDifferenceSecond, finite(differentiated.secondDerivative), 3e-3 * maxOf(1.0, abs(finiteDifferenceSecond)))
        }
    }

    @Test fun domainLimitsAndTanThresholdFollowScalarEvaluator() {
        val division = compiled("1/x").evaluateDifferentiated(0.0)
        assertTrue(division.value is ExpressionEvaluation.Failure)
        val log = compiled("log(x)").evaluateDifferentiated(0.0)
        assertTrue(log.value is ExpressionEvaluation.Failure)
        val tanPole = compiled("tan(x)").evaluateDifferentiated(Math.PI / 2.0)
        assertTrue(tanPole.value is ExpressionEvaluation.Failure)
        assertTrue(tanPole.value is ExpressionEvaluation.Failure && tanPole.value.error is MathExpressionError.Domain)
    }

    @Test fun metadataTracksParametersAndNestedAngleFunctions() {
        val nested = compiled("asin(sin(a*x))")
        assertTrue(nested.metadata.dependsOnX)
        assertTrue(nested.metadata.dependsOnAngleMode)
        assertEquals(setOf('a'), nested.metadata.parameterDependencies)
        assertFalse(compiled("sqrt(a)").metadata.dependsOnX)
        assertFalse(compiled("sqrt(a)").metadata.dependsOnAngleMode)
    }

    private fun checkJet(
        source: String,
        x: Double,
        parameters: Map<Char, Double> = emptyMap(),
        mode: FunctionAngleMode = FunctionAngleMode.RAD,
        expected: Triple<Double, Double, Double>
    ) = assertJet(compiled(source).evaluateDifferentiated(x, parameters, mode), expected.first, expected.second, expected.third)

    private fun assertJet(result: DifferentiatedExpressionEvaluation, value: Double, first: Double, second: Double) {
        assertEquals(value, (result.value as ExpressionEvaluation.Success).value, 1e-10 * maxOf(1.0, abs(value)))
        assertEquals(first, finite(result.firstDerivative), 1e-10 * maxOf(1.0, abs(first)))
        assertEquals(second, finite(result.secondDerivative), 1e-10 * maxOf(1.0, abs(second)))
    }

    private fun finite(result: DerivativeEvaluation): Double = (result as DerivativeEvaluation.Value).value

    private fun assertFailure(result: DerivativeEvaluation, expected: Class<out DerivativeFailure>) {
        val failure = (result as DerivativeEvaluation.Failure).reason
        assertTrue("Expected ${expected.simpleName}, got $failure", expected.isInstance(failure))
    }

    private fun compiled(source: String): CompiledMathExpression =
        (engine.compile(source) as ExpressionCompilation.Success).expression
}
