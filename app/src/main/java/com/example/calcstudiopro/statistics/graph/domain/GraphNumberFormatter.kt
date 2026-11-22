package com.example.calcstudiopro.statistics.graph.domain

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlin.math.abs

/** Presentation only: source and transformed graph values remain full-precision Doubles. */
object GraphNumberFormatter {
    fun format(value: Double, wholeNumber: Boolean = false, percentage: Boolean = false): String {
        if (!value.isFinite()) return "—"
        if (value == 0.0) return if (percentage) "0,00 %" else "0"
        if (wholeNumber && value % 1.0 == 0.0) return BigDecimal.valueOf(value).toBigInteger().toString()
        val magnitude = abs(value)
        val symbols = DecimalFormatSymbols(Locale.FRANCE)
        if (magnitude >= 1e9 || magnitude < 1e-5) {
            val pattern = if (percentage) "0.00E0' %'" else "0.#####E0"
            return DecimalFormat(pattern, symbols).format(value)
        }
        if (percentage) {
            val rounded = BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP)
            if (rounded.compareTo(BigDecimal.ZERO) == 0) return "0,00 %"
            return DecimalFormat("0.00", symbols).format(rounded) + " %"
        }
        val rounded = BigDecimal.valueOf(value).round(MathContext(6, RoundingMode.HALF_UP)).stripTrailingZeros()
        val formatted = rounded.toPlainString().replace('.', ',').let { if (it == "-0") "0" else it }
        return formatted
    }
}
