package com.example.calcstudiopro.converter

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

object ConversionFormatter {
    const val SCIENTIFIC_BELOW = -4
    const val SCIENTIFIC_FROM = 10
    fun format(value: Double): String {
        require(value.isFinite())
        if (value == 0.0) return "0"
        val rounded = BigDecimal.valueOf(value).round(MathContext(10, RoundingMode.HALF_UP)).stripTrailingZeros()
        val exponent = rounded.precision() - rounded.scale() - 1
        val symbols = DecimalFormatSymbols(Locale.FRANCE)
        return if (exponent < SCIENTIFIC_BELOW || exponent >= SCIENTIFIC_FROM) {
            DecimalFormat("0.#########E0", symbols).format(rounded.toDouble()).replace("E", " × 10^")
        } else rounded.toPlainString().replace('.', ',')
    }
}
