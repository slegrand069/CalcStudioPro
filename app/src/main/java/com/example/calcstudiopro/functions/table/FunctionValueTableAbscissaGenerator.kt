package com.example.calcstudiopro.functions.table

/** Pure deterministic X generation. It counts/validates before allocating the output list. */
object FunctionValueTableAbscissaGenerator {
    const val MAX_ROWS = 1_000
    private const val BOUND_ROUNDING_ULPS = 4.0

    fun generate(mode: FunctionValueTableGenerationMode): FunctionValueTableAbscissas = when (mode) {
        is FunctionValueTableGenerationMode.IntervalAndStep -> generateByStep(mode)
        is FunctionValueTableGenerationMode.IntervalAndCount -> generateByCount(mode)
        is FunctionValueTableGenerationMode.CustomValues -> generateCustom(mode.text)
    }

    private fun validateBounds(xMin: Double, xMax: Double): FunctionValueTableValidationError? = when {
        !xMin.isFinite() -> FunctionValueTableValidationError.NonFiniteMinimum
        !xMax.isFinite() -> FunctionValueTableValidationError.NonFiniteMaximum
        xMin > xMax -> FunctionValueTableValidationError.MinimumGreaterThanMaximum
        else -> null
    }

    private fun generateByStep(mode: FunctionValueTableGenerationMode.IntervalAndStep): FunctionValueTableAbscissas {
        validateBounds(mode.xMin, mode.xMax)?.let { return FunctionValueTableAbscissas.Invalid(it) }
        if (!mode.step.isFinite()) return FunctionValueTableAbscissas.Invalid(FunctionValueTableValidationError.NonFiniteStep)
        if (mode.step <= 0.0) return FunctionValueTableAbscissas.Invalid(FunctionValueTableValidationError.NonPositiveStep)

        // First pass is capped at MAX_ROWS + 1 and performs no list allocation.
        var count = 0
        var previous = Double.NEGATIVE_INFINITY
        while (count <= MAX_ROWS) {
            val raw = indexedStep(mode.xMin, mode.step, count)
            if (!raw.isFinite()) break // finite xMax is necessarily below a positive overflow
            val candidate = includeRoundedUpperBound(raw, count, mode.xMin, mode.xMax, mode.step)
            if (candidate > mode.xMax) break
            if (count > 0 && candidate <= previous) {
                return FunctionValueTableAbscissas.Invalid(FunctionValueTableValidationError.StepBelowDoubleResolution)
            }
            count++
            previous = candidate
        }
        if (count > MAX_ROWS) return FunctionValueTableAbscissas.Invalid(FunctionValueTableValidationError.TooManyRows)
        if (count == 0) return FunctionValueTableAbscissas.Invalid(FunctionValueTableValidationError.NonFiniteGeneratedAbscissa(1))

        val values = ArrayList<Double>(count)
        repeat(count) { index ->
            val value = includeRoundedUpperBound(indexedStep(mode.xMin, mode.step, index), index, mode.xMin, mode.xMax, mode.step)
            if (!value.isFinite()) return FunctionValueTableAbscissas.Invalid(FunctionValueTableValidationError.NonFiniteGeneratedAbscissa(index + 1))
            values += value
        }
        return FunctionValueTableAbscissas.Valid(values)
    }

    private fun indexedStep(xMin: Double, step: Double, index: Int): Double =
        Math.fma(index.toDouble(), step, xMin)

    /** Clamp only a few operation-error ULPs above xmax; this does not append an off-grid endpoint. */
    private fun includeRoundedUpperBound(value: Double, index: Int, xMin: Double, xMax: Double, step: Double): Double {
        if (value <= xMax || !value.isFinite()) return value
        val errorBound = BOUND_ROUNDING_ULPS * (
            Math.ulp(xMin) + index.toDouble() * Math.ulp(step) + Math.ulp(xMax)
        )
        return if (value - xMax <= errorBound) xMax else value
    }

    private fun generateByCount(mode: FunctionValueTableGenerationMode.IntervalAndCount): FunctionValueTableAbscissas {
        validateBounds(mode.xMin, mode.xMax)?.let { return FunctionValueTableAbscissas.Invalid(it) }
        if (mode.pointCount !in 1..MAX_ROWS) return FunctionValueTableAbscissas.Invalid(FunctionValueTableValidationError.InvalidPointCount)
        val count = mode.pointCount
        val values = ArrayList<Double>(count)
        if (count == 1) {
            values += mode.xMin
        } else {
            val denominator = (count - 1).toDouble()
            for (index in 0 until count) {
                val value = when (index) {
                    0 -> mode.xMin
                    count - 1 -> mode.xMax
                    else -> interpolate(mode.xMin, mode.xMax, index.toDouble() / denominator)
                }
                if (!value.isFinite()) return FunctionValueTableAbscissas.Invalid(
                    FunctionValueTableValidationError.NonFiniteGeneratedAbscissa(index + 1)
                )
                values += value
            }
        }
        return FunctionValueTableAbscissas.Valid(values)
    }

    /** Convex interpolation avoids overflowing (xMax - xMin) for opposite extreme bounds. */
    private fun interpolate(xMin: Double, xMax: Double, fraction: Double): Double {
        val value = if ((xMin < 0.0) != (xMax < 0.0)) {
            xMin * (1.0 - fraction) + xMax * fraction
        } else {
            xMin + (xMax - xMin) * fraction
        }
        return value.coerceIn(xMin, xMax)
    }

    private fun generateCustom(text: String): FunctionValueTableAbscissas {
        val values = ArrayList<Double>()
        var start = 0
        var tokenIndex = 0
        var cursor = 0
        while (cursor <= text.length) {
            val atEnd = cursor == text.length
            val delimiter = !atEnd && (text[cursor] == ';' || text[cursor] == '\n' || text[cursor] == '\r')
            if (atEnd || delimiter) {
                val token = text.substring(start, cursor).trim()
                if (token.isNotEmpty()) {
                    tokenIndex++
                    if (tokenIndex > MAX_ROWS) return FunctionValueTableAbscissas.Invalid(FunctionValueTableValidationError.TooManyRows)
                    val parsed = parseFrenchDouble(token)
                        ?: return FunctionValueTableAbscissas.Invalid(FunctionValueTableValidationError.MalformedCustomValue(tokenIndex, token))
                    if (!parsed.isFinite()) return FunctionValueTableAbscissas.Invalid(FunctionValueTableValidationError.NonFiniteCustomValue(tokenIndex))
                    values += parsed
                }
                if (!atEnd && text[cursor] == '\r' && cursor + 1 < text.length && text[cursor + 1] == '\n') cursor++
                start = cursor + 1
            }
            cursor++
        }
        if (values.isEmpty()) return FunctionValueTableAbscissas.Invalid(FunctionValueTableValidationError.EmptyCustomValues)
        return FunctionValueTableAbscissas.Valid(values)
    }

    private fun parseFrenchDouble(token: String): Double? {
        if (token.count { it == ',' } > 1 || (',' in token && '.' in token)) return null
        return token.replace(',', '.').toDoubleOrNull()
    }
}
