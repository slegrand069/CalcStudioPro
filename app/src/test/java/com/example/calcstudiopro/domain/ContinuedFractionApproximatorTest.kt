package com.example.calcstudiopro.domain

import com.example.calcstudiopro.settings.ContinuedFractionSettings
import java.math.BigDecimal
import java.math.BigInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContinuedFractionApproximatorTest {
    private val approximator = ContinuedFractionApproximator()

    @Test
    fun handlesZeroIntegersAndSimpleFractionsCanonically() {
        assertSuccess(0.0, 0, 1)
        assertSuccess(7.0, 7, 1)
        assertSuccess(-12.0, -12, 1)
        assertSuccess(1.0 / 2.0, 1, 2)
        assertSuccess(1.0 / 3.0, 1, 3)
        assertSuccess(2.0 / 7.0, 2, 7)
        assertSuccess(-2.0 / 7.0, -2, 7)
    }

    @Test
    fun convergentsApproximateIrrationalInputsWithinTheConfiguredError() {
        val defaultSqrtTwo = assertValidApproximation(kotlin.math.sqrt(2.0), ContinuedFractionSettings())
        assertEquals(BigInteger.valueOf(8119), defaultSqrtTwo.numerator)
        assertEquals(BigInteger.valueOf(5741), defaultSqrtTwo.denominator)

        val sqrtTwoSettings = ContinuedFractionSettings(tolerance = 1e-6)
        val sqrtTwo = assertValidApproximation(kotlin.math.sqrt(2.0), sqrtTwoSettings)
        assertEquals(BigInteger.valueOf(1393), sqrtTwo.numerator)
        assertEquals(BigInteger.valueOf(985), sqrtTwo.denominator)

        assertValidApproximation(kotlin.math.sqrt(3.0) + kotlin.math.sqrt(7.0), sqrtTwoSettings.copy(tolerance = 1e-5))
        assertValidApproximation(Math.PI, sqrtTwoSettings)
    }

    @Test
    fun acceptsZeroAsTheBestApproximationVeryNearZero() {
        val settings = ContinuedFractionSettings()
        val approximation = assertValidApproximation(1e-12, settings)
        assertEquals(BigInteger.ZERO, approximation.numerator)
        assertEquals(BigInteger.ONE, approximation.denominator)
    }

    @Test
    fun supportsVeryLargeValuesWithoutLongConversion() {
        val approximation = assertValidApproximation(Double.MAX_VALUE, ContinuedFractionSettings())
        assertEquals(BigInteger.ONE, approximation.denominator)
        assertEquals(BigDecimal(Double.MAX_VALUE).toBigInteger(), approximation.numerator)
    }

    @Test
    fun rejectsInputsAndSearchesThatCannotSatisfyTheConfiguredLimits() {
        assertFailure(Double.NaN, ContinuedFractionSettings(), FailureReason.NON_FINITE_INPUT)
        assertFailure(Double.POSITIVE_INFINITY, ContinuedFractionSettings(), FailureReason.NON_FINITE_INPUT)

        assertFailure(
            1.0 / 3.0,
            ContinuedFractionSettings(tolerance = 1e-12, maxDenominator = 1),
            FailureReason.MAX_DENOMINATOR_EXCEEDED
        )
        assertFailure(
            1.0 / 3.0,
            ContinuedFractionSettings(tolerance = 1e-12, maxIterations = 1),
            FailureReason.MAX_ITERATIONS_REACHED
        )
        assertFailure(
            kotlin.math.sqrt(2.0),
            ContinuedFractionSettings(tolerance = 1e-10, maxDenominator = 10_000),
            FailureReason.MAX_DENOMINATOR_EXCEEDED
        )
    }

    @Test
    fun everySuccessIsReducedWithinTheDenominatorAndIterationLimits() {
        val settings = ContinuedFractionSettings(tolerance = 1e-6, maxDenominator = 1_000, maxIterations = 8)
        val result = approximator.approximate(Math.PI, settings)
        assertTrue(result is ApproximationResult.Success)
        result as ApproximationResult.Success
        assertTrue(result.denominator > BigInteger.ZERO)
        assertTrue(result.denominator <= BigInteger.valueOf(settings.maxDenominator.toLong()))
        assertEquals(BigInteger.ONE, result.numerator.abs().gcd(result.denominator))
        assertTrue(result.iterations <= settings.maxIterations)
        assertWithinTolerance(Math.PI, result, settings)
    }

    private fun assertSuccess(value: Double, numerator: Long, denominator: Long) {
        val result = approximator.approximate(value, ContinuedFractionSettings())
        assertTrue("Expected success for $value, got $result", result is ApproximationResult.Success)
        result as ApproximationResult.Success
        assertEquals(BigInteger.valueOf(numerator), result.numerator)
        assertEquals(BigInteger.valueOf(denominator), result.denominator)
        assertEquals(BigInteger.ONE, result.numerator.abs().gcd(result.denominator))
        assertWithinTolerance(value, result, ContinuedFractionSettings())
    }

    private fun assertValidApproximation(
        value: Double,
        settings: ContinuedFractionSettings
    ): ApproximationResult.Success {
        val result = approximator.approximate(value, settings)
        assertTrue("Expected success for $value, got $result", result is ApproximationResult.Success)
        result as ApproximationResult.Success
        assertTrue(result.denominator > BigInteger.ZERO)
        assertTrue(result.denominator <= BigInteger.valueOf(settings.maxDenominator.toLong()))
        assertTrue(result.iterations <= settings.maxIterations)
        assertEquals(BigInteger.ONE, result.numerator.abs().gcd(result.denominator))
        assertWithinTolerance(value, result, settings)
        return result
    }

    private fun assertFailure(value: Double, settings: ContinuedFractionSettings, reason: FailureReason) {
        val result = approximator.approximate(value, settings)
        assertFalse("Expected failure for $value, got $result", result is ApproximationResult.Success)
        assertEquals(ApproximationResult.Failure(reason, (result as ApproximationResult.Failure).iterations), result)
        assertTrue(result.iterations <= settings.maxIterations)
    }

    private fun assertWithinTolerance(
        value: Double,
        result: ApproximationResult.Success,
        settings: ContinuedFractionSettings
    ) {
        val approximation = BigDecimal(result.numerator).divide(BigDecimal(result.denominator), java.math.MathContext.DECIMAL128)
        val exactInput = BigDecimal(value)
        val tolerance = BigDecimal(settings.tolerance)
        val allowedError = tolerance.max(tolerance.multiply(exactInput.abs()))
        assertTrue(exactInput.subtract(approximation).abs() <= allowedError)
    }
}
