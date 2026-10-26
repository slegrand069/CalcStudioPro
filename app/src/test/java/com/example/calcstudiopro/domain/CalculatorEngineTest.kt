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

    private companion object {
        const val TOLERANCE = 1e-6
    }
}