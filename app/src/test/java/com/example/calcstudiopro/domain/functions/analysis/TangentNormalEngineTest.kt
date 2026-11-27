package com.example.calcstudiopro.domain.functions.analysis

import com.example.calcstudiopro.domain.functions.ExpressionCompilation
import com.example.calcstudiopro.domain.functions.FunctionAngleMode
import com.example.calcstudiopro.domain.functions.MathExpressionEngine
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class TangentNormalEngineTest {
    private val compiler = MathExpressionEngine()
    private val engine = TangentNormalEngine()

    private fun request(expression: String, x: Double, parameters: Map<Char, Double> = emptyMap(), mode: FunctionAngleMode = FunctionAngleMode.RAD) =
        TangentNormalRequest((compiler.compile(expression) as ExpressionCompilation.Success).expression, x, parameters, mode)

    @Test fun buildsRegularTangentAndNormalThroughTheContactPoint() {
        val result = engine.calculate(request("x^2", 2.0))
        assertEquals(ContactPoint(2.0, 4.0), result.contactPoint)
        assertEquals(TangentDerivativeStatus.VALID, result.derivativeStatus)
        assertEquals(4.0, result.tangent.slope!!, 1e-12)
        val tangent = result.tangent.slopeIntercept!!
        assertEquals(4.0, tangent.slope, 1e-12)
        assertEquals(-4.0, tangent.intercept, 1e-12)
        assertEquals(-0.25, result.normal.slope!!, 1e-12)
        assertEquals(4.5, result.normal.slopeIntercept!!.intercept, 1e-12)
        assertEquals(0.0, tangent.slope * result.normal.slope!! + 1.0, 1e-12)
    }

    @Test fun handlesHorizontalAndVerticalAxisLinesWithoutInfiniteSlopes() {
        for (expression in listOf("x^3", "5", "3*x+2")) {
            val a = if (expression == "3*x+2") 2.0 else 0.0
            val result = engine.calculate(request(expression, a))
            assertEquals(TangentDerivativeStatus.VALID, result.derivativeStatus)
            if (expression != "3*x+2") {
                assertTrue(result.tangent.equation is LineEquation.Horizontal)
                assertEquals(0.0, result.tangent.slope!!, 0.0)
                assertEquals(LineEquation.Vertical(a), result.normal.equation)
                assertNull(result.normal.slope)
            } else assertEquals(3.0, result.tangent.slope!!, 0.0)
        }
    }

    @Test fun respectsAngularModesAndTrigonometricDerivatives() {
        val rad = engine.calculate(request("sin(x)", 0.0))
        val deg = engine.calculate(request("sin(x)", 0.0, mode = FunctionAngleMode.DEG))
        val cos = engine.calculate(request("cos(x)", 0.0))
        assertEquals(1.0, rad.tangent.slope!!, 1e-12)
        assertEquals(Math.PI / 180.0, deg.tangent.slope!!, 1e-12)
        assertEquals(1.0, cos.contactPoint!!.y, 0.0)
        assertEquals(0.0, cos.tangent.slope!!, 0.0)
    }

    @Test fun mapsUndefinedAndIndeterminateDerivativesWithoutInventingVerticalTangents() {
        val reciprocal = engine.calculate(request("1/x", 1.0))
        assertEquals(-1.0, reciprocal.tangent.slope!!, 1e-12)
        assertEquals(1.0, reciprocal.contactPoint!!.y, 0.0)

        val undefined = engine.calculate(request("1/x", 0.0))
        assertEquals(StudyValueStatus.OUT_OF_DOMAIN, undefined.functionStatus)
        assertNull(undefined.contactPoint)
        val invalidLog = engine.calculate(request("ln(x)", -1.0))
        assertEquals(StudyValueStatus.OUT_OF_DOMAIN, invalidLog.functionStatus)

        for (expression in listOf("abs(x)", "sqrt(x)", "cbrt(x)", "x*abs(x)")) {
            val result = engine.calculate(request(expression, 0.0))
            assertEquals(StudyValueStatus.FINITE, result.functionStatus)
            assertNotEquals(TangentDerivativeStatus.VALID, result.derivativeStatus)
            assertNull(result.tangent.equation)
            assertNull(result.normal.equation)
            assertNotEquals(LineEquation.Vertical(0.0), result.tangent.equation)
        }
    }

    @Test fun retainsStablePointDirectionWhenSlopeOrInterceptIsExtreme() {
        val weak = engine.calculate(request("10^(-300)*x", 1.0))
        assertEquals(1e-300, weak.tangent.slope!!, 0.0)
        assertTrue(weak.tangent.equation is LineEquation.PointDirection)
        assertTrue(weak.normal.equation is LineEquation.PointDirection)
        assertTrue(weak.tangent.slopeIntercept != null)

        val subnormalSlope = engine.calculate(request("10^(-310)*x", 1.0))
        assertTrue(subnormalSlope.tangent.slope!!.isFinite())
        assertTrue(subnormalSlope.normal.equation is LineEquation.PointDirection)
        assertNull(subnormalSlope.normal.slope)
        assertTrue(subnormalSlope.warnings.isNotEmpty())

        val strong = engine.calculate(request("10^300*x", 1.0))
        assertEquals(1e300, strong.tangent.slope!!, 1e285)
        assertTrue(strong.tangent.equation is LineEquation.PointDirection)
        assertTrue(strong.normal.equation is LineEquation.PointDirection)
        assertTrue(strong.tangent.slopeIntercept!!.slope.isFinite())

        val hugeX = engine.calculate(request("x^2", 1e150))
        assertEquals(1e300, hugeX.contactPoint!!.y, 1e285)
        assertTrue(hugeX.tangent.slope!!.isFinite())
        assertTrue(hugeX.tangent.slopeIntercept == null || hugeX.tangent.slopeIntercept.intercept.isFinite())
    }

    @Test fun usesParameterSnapshotsAndCompiledExpressionsAcrossIndependentFunctions() {
        val parameterized = engine.calculate(request("a*x", 2.0, mapOf('A' to 4.0)))
        assertEquals(8.0, parameterized.contactPoint!!.y, 0.0)
        assertEquals(4.0, parameterized.tangent.slope!!, 0.0)
        listOf("x", "x^2", "sin(x)", "cos(x)", "a*x", "5").forEach { expression ->
            val output = engine.calculate(request(expression, 0.5, mapOf('a' to 3.0)))
            assertTrue(expression, output.contactPoint?.y?.isFinite() == true)
            assertTrue(expression, output.tangent.equation != null)
        }
    }

    @Test fun cancellationBeforeEvaluationReturnsTypedCancelledResult() {
        val result = engine.calculate(request("x^2", 2.0), TangentNormalCancellation { true })
        assertTrue(result.cancelled)
        assertTrue(result.contactPoint == null)
        assertTrue(result.warnings.single().contains("annulé", ignoreCase = true))
    }
}
