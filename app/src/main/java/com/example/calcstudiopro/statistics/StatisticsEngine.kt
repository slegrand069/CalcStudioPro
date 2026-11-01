package com.example.calcstudiopro.statistics

import com.example.calcstudiopro.converter.ConversionFormatter
import kotlin.math.abs
import kotlin.math.sqrt

/** Pure parser and descriptive statistics engine. At most 10,000 observations are accepted. */
object StatisticsEngine {
    const val MAX_VALUES = 10_000

    data class Result(
        val count: Int,
        val sum: Double,
        val mean: Double,
        val median: Double,
        val minimum: Double,
        val maximum: Double,
        val populationStandardDeviation: Double
    )

    sealed interface Analysis {
        data class Valid(val values: List<Double>, val result: Result) : Analysis
        data class Invalid(val message: String) : Analysis
    }

    fun analyze(text: String): Analysis {
        if (text.isBlank()) return Analysis.Invalid("Saisissez au moins une valeur.")
        val parts = text.trim().split(WHITESPACE)
        if (parts.size > MAX_VALUES) return Analysis.Invalid("La série est limitée à $MAX_VALUES valeurs.")
        val values = ArrayList<Double>(parts.size)
        for (part in parts) {
            val token = part.trim()
            if (token.isEmpty()) return Analysis.Invalid("Une valeur manque entre deux séparateurs.")
            val normalized = token.replace(',', '.')
            if (!NUMBER.matches(normalized)) return Analysis.Invalid("Valeur invalide : « $token ».")
            val value = normalized.toDoubleOrNull()?.takeIf(Double::isFinite)
                ?: return Analysis.Invalid("Valeur non finie ou trop grande : « $token ».")
            values += value
        }

        val sorted = values.sorted()
        val scale = values.maxOf { abs(it) }.coerceAtLeast(1.0)
        // Welford on scaled inputs avoids squaring very large values and catastrophic cancellation.
        var meanScaled = 0.0
        var m2Scaled = 0.0
        values.forEachIndexed { index, value ->
            val x = value / scale
            val delta = x - meanScaled
            meanScaled += delta / (index + 1)
            m2Scaled += delta * (x - meanScaled)
        }
        val mean = meanScaled * scale
        val varianceScaled = (m2Scaled / values.size).coerceAtLeast(0.0)
        val std = sqrt(varianceScaled) * scale
        val sum = mean * values.size
        val median = if (sorted.size % 2 == 1) sorted[sorted.size / 2]
        else sorted[sorted.size / 2 - 1] / 2.0 + sorted[sorted.size / 2] / 2.0
        val resultValues = listOf(sum, mean, median, sorted.first(), sorted.last(), std)
        if (resultValues.any { !it.isFinite() }) return Analysis.Invalid("Résultat hors de la plage numérique représentable.")
        return Analysis.Valid(values, Result(values.size, sum, mean, median, sorted.first(), sorted.last(), std))
    }

    fun format(value: Double): String = ConversionFormatter.format(value)

    private val WHITESPACE = Regex("\\s+")
    private val NUMBER = Regex("[+-]?(?:\\d+(?:\\.\\d*)?|\\.\\d+)(?:[eE][+-]?\\d+)?")
}
