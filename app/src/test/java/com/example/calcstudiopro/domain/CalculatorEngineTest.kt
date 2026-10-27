package com.example.calcstudiopro.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.PI

class CalculatorEngineTest {
    private val engine = CalculatorEngine()

    @Test
    fun sineUsesDegreesInDegreeMode() {
        assertEquals(1.0, engine.evaluate("sin(90)", AngleUnit.DEGREES), TOLERANCE)
    }

    @Test
    fun cosineUsesDegreesInDegreeMode() {
        assertEquals(-1.0, engine.evaluate("cos(180)", AngleUnit.DEGREES), TOLERANCE)
    }

    @Test
    fun sineUsesRadiansInRadianMode() {
        assertEquals(1.0, engine.evaluate("sin(π/2)", AngleUnit.RADIANS), TOLERANCE)
    }

    @Test
    fun cosineUsesRadiansInRadianMode() {
        assertEquals(-1.0, engine.evaluate("cos(π)", AngleUnit.RADIANS), TOLERANCE)
    }

    @Test
    fun sineUsesRadiansWhenRadianModeReceivesNinety() {
        assertEquals(0.89399666, engine.evaluate("sin(90)", AngleUnit.RADIANS), TOLERANCE)
    }

    @Test
    fun tangentUsesDegreesInDegreeMode() {
        assertEquals(1.0, engine.evaluate("tan(45)", AngleUnit.DEGREES), TOLERANCE)
    }

    @Test
    fun tangentUsesRadiansInRadianMode() {
        assertEquals(1.0, engine.evaluate("tan(π/4)", AngleUnit.RADIANS), TOLERANCE)
    }

    @Test
    fun inverseSineReturnsDegreesOrRadians() {
        assertEquals(90.0, engine.evaluate("asin(1)", AngleUnit.DEGREES), TOLERANCE)
        assertEquals(PI / 2.0, engine.evaluate("asin(1)", AngleUnit.RADIANS), TOLERANCE)
    }

    @Test
    fun inverseCosineReturnsDegreesOrRadians() {
        assertEquals(90.0, engine.evaluate("acos(0)", AngleUnit.DEGREES), TOLERANCE)
        assertEquals(PI / 2.0, engine.evaluate("acos(0)", AngleUnit.RADIANS), TOLERANCE)
    }

    @Test
    fun inverseTangentReturnsDegreesOrRadians() {
        assertEquals(45.0, engine.evaluate("atan(1)", AngleUnit.DEGREES), TOLERANCE)
        assertEquals(PI / 4.0, engine.evaluate("atan(1)", AngleUnit.RADIANS), TOLERANCE)
    }

    @Test
    fun defaultModeRemainsDegrees() {
        assertEquals(1.0, engine.evaluate("sin(90)"), TOLERANCE)
    }

    @Test
    fun exponentialFunctionEvaluates() {
        assertEquals(kotlin.math.exp(2.0), engine.evaluate("exp(2)"), TOLERANCE)
    }

    @Test
    fun powerOfTenFunctionEvaluates() {
        assertEquals(100.0, engine.evaluate("pow(10,2)"), TOLERANCE)
        assertEquals(100.0, engine.evaluate("10^2"), TOLERANCE)
    }

    @Test
    fun squareRootHandlesPositiveValuesAndZero() {
        assertEquals(3.0, engine.evaluate("sqrt(9)"), TOLERANCE)
        assertEquals(0.0, engine.evaluate("sqrt(0)"), TOLERANCE)
    }

    @Test(expected = IllegalArgumentException::class)
    fun squareRootRejectsNegativeValues() {
        engine.evaluate("sqrt(-1)")
    }

    @Test
    fun powerFunctionHandlesIntegerDecimalAndNegativeExponents() {
        assertEquals(8.0, engine.evaluate("pow(2,3)"), TOLERANCE)
        assertEquals(2.0, engine.evaluate("pow(8,1/3)"), TOLERANCE)
        assertEquals(0.125, engine.evaluate("pow(2,-3)"), TOLERANCE)
        assertEquals(3.0, engine.evaluate("pow(9,0.5)"), TOLERANCE)
    }

    @Test
    fun powerFunctionHandlesNegativeBaseInItsDomain() {
        assertEquals(-8.0, engine.evaluate("pow(-2,3)"), TOLERANCE)
    }

    @Test(expected = IllegalArgumentException::class)
    fun powerFunctionRejectsFractionalPowerOfNegativeBase() {
        engine.evaluate("pow(-2,0.5)")
    }

    @Test(expected = IllegalArgumentException::class)
    fun powerFunctionRejectsZeroRaisedToNegativePower() {
        engine.evaluate("pow(0,-1)")
    }

    @Test(expected = IllegalArgumentException::class)
    fun powerFunctionRequiresExactlyTwoArguments() {
        engine.evaluate("pow(2)")
    }

    @Test
    fun powerFunctionSeparatesArgumentsFromDecimalPoints() {
        assertEquals(8.0, engine.evaluate("pow(2,3)"), TOLERANCE)
        assertEquals(6.25, engine.evaluate("pow(2.5,2)"), TOLERANCE)
        assertEquals(2.5, engine.evaluate("2,5"), TOLERANCE)
        assertEquals(1.5, engine.evaluate("sqrt(2,25)"), TOLERANCE)
    }

    @Test
    fun powerFunctionSupportsNestedFunctionArguments() {
        assertEquals(8.0, engine.evaluate("pow(2,sqrt(9))"), TOLERANCE)
        assertEquals(65536.0, engine.evaluate("pow(sqrt(16),pow(2,3))"), TOLERANCE)
        assertEquals(16.0, engine.evaluate("pow(sqrt(16),2)"), TOLERANCE)
        assertEquals(16.0, engine.evaluate("pow(2,sqrt(16))"), TOLERANCE)
        assertEquals(4.9334096679, engine.evaluate("pow(2,ln(10))"), TOLERANCE)
    }

    @Test
    fun eConstantEvaluates() {
        assertEquals(kotlin.math.E, engine.evaluate("e"), TOLERANCE)
    }
    @Test
    fun implicitMultiplicationIsAcceptedBeforeFunctionCalls() {
        assertEquals(2.0 * kotlin.math.cos(Math.toRadians(30.0)), engine.evaluate("2cos(30)"), TOLERANCE)
        assertEquals(2.0 * kotlin.math.cos(Math.toRadians(8.0)), engine.evaluate("2cos(sqrt(64))"), TOLERANCE)
        assertEquals(15.0, engine.evaluate("5sqrt(9)"), TOLERANCE)
    }

    @Test(expected = IllegalArgumentException::class)
    fun implicitMultiplicationBeforeOrdinaryParenthesesIsNotAdded() {
        engine.evaluate("3(2+4)")
    }
    private companion object {
        const val TOLERANCE = 1e-6
    }
}
