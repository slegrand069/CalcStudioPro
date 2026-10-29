package com.example.calcstudiopro.settings

import com.example.calcstudiopro.domain.AngleUnit

/** User preferences shared by the calculator and settings screens. */
data class AppSettings(
    val continuedFractions: ContinuedFractionSettings = ContinuedFractionSettings(),
    val angleUnit: AngleUnit = DEFAULT_ANGLE_UNIT,
    val notation: CalculatorNotation = DEFAULT_NOTATION
) {
    companion object {
        val DEFAULT_ANGLE_UNIT = AngleUnit.DEGREES
        val DEFAULT_NOTATION = CalculatorNotation.STANDARD
    }
}

enum class CalculatorNotation { STANDARD, SCIENTIFIC }
