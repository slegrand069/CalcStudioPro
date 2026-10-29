package com.example.calcstudiopro

import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.abs

internal fun formatResult(number: Double): String {
    if (abs(number) >= 100_000_000_000.0) {
        val normalized = BigDecimal.valueOf(number).stripTrailingZeros()
        val exponent = normalized.precision() - normalized.scale() - 1
        val mantissa = normalized.movePointLeft(exponent).stripTrailingZeros()
        val mantissaText = mantissa.toPlainString().replace('.', ',')
        return "$mantissaText × 10${exponent.toSuperscript()}"
    }

    return BigDecimal.valueOf(number)
        .setScale(10, RoundingMode.HALF_UP)
        .stripTrailingZeros()
        .toPlainString()
}

/** Formats decimal results with an engineering exponent, always a multiple of three. */
internal fun formatEngineeringResult(number: Double): String {
    if (number == 0.0) return formatResult(number)

    val absoluteValue = abs(number)
    if (absoluteValue >= 0.001 && absoluteValue < 1_000.0) {
        return formatResult(number)
    }

    val normalized = BigDecimal.valueOf(number).stripTrailingZeros()
    val decimalExponent = normalized.precision() - normalized.scale() - 1
    val engineeringExponent = Math.floorDiv(decimalExponent, 3) * 3
    val mantissa = normalized.movePointLeft(engineeringExponent).stripTrailingZeros()
    val mantissaText = mantissa.toPlainString().replace('.', ',')
    return "$mantissaText × 10${engineeringExponent.toSuperscript()}"
}

private fun Int.toSuperscript(): String = toString().map { digit ->
    when (digit) {
        '-' -> '⁻'
        '0' -> '⁰'
        '1' -> '¹'
        '2' -> '²'
        '3' -> '³'
        '4' -> '⁴'
        '5' -> '⁵'
        '6' -> '⁶'
        '7' -> '⁷'
        '8' -> '⁸'
        '9' -> '⁹'
        else -> digit
    }
}.joinToString("")
