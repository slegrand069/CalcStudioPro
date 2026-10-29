package com.example.calcstudiopro

import com.example.calcstudiopro.domain.ApproximationResult

internal fun formatFraction(result: ApproximationResult.Success): String =
    "${result.numerator}/${result.denominator}"
