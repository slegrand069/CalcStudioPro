package com.example.calcstudiopro.settings

/**
 * Settings passed explicitly to the future continued-fraction implementation.
 * The single configured tolerance is intended to serve as both epsilonAbs and
 * epsilonRel in the future acceptance rule:
 * |x - p/q| <= max(tolerance, tolerance * |x|).
 * This is a convention for future work only; this model does not approximate values.
 */
data class ContinuedFractionSettings(
    val tolerance: Double = DEFAULT_TOLERANCE,
    val maxDenominator: Int = DEFAULT_MAX_DENOMINATOR,
    val maxIterations: Int = DEFAULT_MAX_ITERATIONS,
    val resultPresentation: ResultPresentation = DEFAULT_RESULT_PRESENTATION
) {
    init {
        require(tolerance.isFinite() && tolerance > 0.0) { "Tolerance must be positive and finite" }
        require(maxDenominator >= 1) { "Maximum denominator must be at least 1" }
        require(maxIterations > 0) { "Maximum iterations must be positive" }
    }

    companion object {
        const val DEFAULT_TOLERANCE = 1e-8
        const val DEFAULT_MAX_DENOMINATOR = 10_000
        const val DEFAULT_MAX_ITERATIONS = 64
        val DEFAULT_RESULT_PRESENTATION = ResultPresentation.FRACTION
    }
}

enum class ResultPresentation { FRACTION, DECIMAL }
