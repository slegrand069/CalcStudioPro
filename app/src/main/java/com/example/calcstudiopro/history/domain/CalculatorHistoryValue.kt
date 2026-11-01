package com.example.calcstudiopro.history.domain

import com.example.calcstudiopro.domain.AngleUnit
import com.example.calcstudiopro.domain.CalculatorEngine
import java.math.BigDecimal

/** Converts a stored Double into a calculator expression only when evaluation is lossless. */
object CalculatorHistoryValue {
    fun canonical(value: Double): String? = value.takeIf(Double::isFinite)?.toString()

    fun toCalculatorExpression(canonicalValue: String?, valueType: String?): String? {
        if (valueType != HistoryValueTypes.DECIMAL_DOUBLE || canonicalValue == null) return null
        val value = canonicalValue.toDoubleOrNull()?.takeIf(Double::isFinite) ?: return null
        if (value.toString() != canonicalValue) return null

        val expression = if (value.toRawBits() == NEGATIVE_ZERO_BITS) {
            "-0"
        } else {
            BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()
        }

        return runCatching {
            val evaluated = CalculatorEngine().evaluate(expression, AngleUnit.DEGREES)
            expression.takeIf { evaluated.toRawBits() == value.toRawBits() }
        }.getOrNull()
    }

    private const val NEGATIVE_ZERO_BITS = Long.MIN_VALUE
}
