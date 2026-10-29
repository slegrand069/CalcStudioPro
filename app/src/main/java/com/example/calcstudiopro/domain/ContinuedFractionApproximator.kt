package com.example.calcstudiopro.domain

import com.example.calcstudiopro.settings.ContinuedFractionSettings
import java.math.BigDecimal
import java.math.BigInteger

/** Finds the first continued-fraction convergent that satisfies the configured error bound. */
class ContinuedFractionApproximator {
    fun approximate(value: Double, settings: ContinuedFractionSettings): ApproximationResult {
        if (!value.isFinite()) return ApproximationResult.Failure(FailureReason.NON_FINITE_INPUT, 0)

        val input = ExactRational.fromDouble(kotlin.math.abs(value))
        if (input.numerator == BigInteger.ZERO) {
            return ApproximationResult.Success(BigInteger.ZERO, BigInteger.ONE, 0)
        }
        val epsilon = ExactRational.fromDouble(settings.tolerance)
        val negative = value < 0.0

        var remainderNumerator = input.numerator
        var remainderDenominator = input.denominator
        var previousNumerator = BigInteger.ONE
        var previousPreviousNumerator = BigInteger.ZERO
        var previousDenominator = BigInteger.ZERO
        var previousPreviousDenominator = BigInteger.ONE

        for (iteration in 1..settings.maxIterations) {
            val partialQuotient = remainderNumerator.divide(remainderDenominator)
            val remainder = remainderNumerator.remainder(remainderDenominator)
            val denominator = partialQuotient * previousDenominator + previousPreviousDenominator

            if (denominator > BigInteger.valueOf(settings.maxDenominator.toLong())) {
                return ApproximationResult.Failure(FailureReason.MAX_DENOMINATOR_EXCEEDED, iteration)
            }

            val numerator = partialQuotient * previousNumerator + previousPreviousNumerator
            if (withinTolerance(input, epsilon, numerator, denominator)) {
                return ApproximationResult.Success(
                    numerator = if (negative) numerator.negate() else numerator,
                    denominator = denominator,
                    iterations = iteration
                )
            }

            if (remainder == BigInteger.ZERO) {
                return ApproximationResult.Failure(FailureReason.TOLERANCE_NOT_MET, iteration)
            }

            previousPreviousNumerator = previousNumerator
            previousNumerator = numerator
            previousPreviousDenominator = previousDenominator
            previousDenominator = denominator
            remainderNumerator = remainderDenominator
            remainderDenominator = remainder
        }

        return ApproximationResult.Failure(FailureReason.MAX_ITERATIONS_REACHED, settings.maxIterations)
    }

    private fun withinTolerance(
        input: ExactRational,
        epsilon: ExactRational,
        candidateNumerator: BigInteger,
        candidateDenominator: BigInteger
    ): Boolean {
        val differenceNumerator = input.numerator.multiply(candidateDenominator)
            .subtract(candidateNumerator.multiply(input.denominator)).abs()

        // max(epsilon, epsilon * |x|) is epsilon below unit magnitude and
        // epsilon * |x| above it. Compare the exact rational integers directly.
        val left = differenceNumerator.multiply(epsilon.denominator)
        val right = if (input.numerator <= input.denominator) {
            epsilon.numerator.multiply(input.denominator).multiply(candidateDenominator)
        } else {
            epsilon.numerator.multiply(input.numerator).multiply(candidateDenominator)
        }
        return left <= right
    }
}

sealed interface ApproximationResult {
    data class Success(
        val numerator: BigInteger,
        val denominator: BigInteger,
        val iterations: Int
    ) : ApproximationResult

    data class Failure(val reason: FailureReason, val iterations: Int) : ApproximationResult
}

enum class FailureReason {
    NON_FINITE_INPUT,
    MAX_DENOMINATOR_EXCEEDED,
    MAX_ITERATIONS_REACHED,
    TOLERANCE_NOT_MET
}

private data class ExactRational(val numerator: BigInteger, val denominator: BigInteger) {
    companion object {
        fun fromDouble(value: Double): ExactRational {
            val decimal = BigDecimal(value).stripTrailingZeros()
            val scale = decimal.scale()
            val numerator: BigInteger
            val denominator: BigInteger
            if (scale >= 0) {
                numerator = decimal.unscaledValue()
                denominator = BigInteger.TEN.pow(scale)
            } else {
                numerator = decimal.unscaledValue().multiply(BigInteger.TEN.pow(-scale))
                denominator = BigInteger.ONE
            }
            val divisor = numerator.gcd(denominator)
            return ExactRational(numerator.divide(divisor), denominator.divide(divisor))
        }
    }
}
