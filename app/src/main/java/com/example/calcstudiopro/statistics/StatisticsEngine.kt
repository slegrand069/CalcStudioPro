package com.example.calcstudiopro.statistics

import com.example.calcstudiopro.converter.ConversionFormatter
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

/** Pure statistical calculations. The UI and persistence layers depend on these value objects only. */
object StatisticsEngine {
    const val MAX_VALUES = 10_000

    data class Result(
        val count: Int,
        val sum: Double,
        val mean: Double,
        val median: Double,
        val minimum: Double,
        val maximum: Double,
        val populationStandardDeviation: Double,
        val populationVariance: Double = populationStandardDeviation * populationStandardDeviation,
        val sampleVariance: Double? = null,
        val sampleStandardDeviation: Double? = null,
        val range: Double? = (maximum - minimum).takeIf(Double::isFinite),
        val modes: List<Double> = emptyList(),
        val hasDominantMode: Boolean = false,
        val q1: Double = median,
        val q3: Double = median,
        val interquartileRange: Double? = (q3 - q1).takeIf(Double::isFinite),
        val populationCoefficientOfVariation: Double? = null,
        val sampleCoefficientOfVariation: Double? = null,
        val skewness: Double? = null,
        val excessKurtosis: Double? = null
    )

    data class Frequency(val value: Double, val count: Int)

    sealed interface Analysis {
        data class Valid(val values: List<Double>, val result: Result) : Analysis
        data class Invalid(val message: String) : Analysis
    }

    /** Backwards-compatible whitespace list parser used by the original statistics screen. */
    fun analyze(text: String): Analysis {
        if (text.isBlank()) return Analysis.Invalid("Saisissez au moins une valeur.")
        val tokens = text.trim().split(WHITESPACE)
        if (tokens.size > MAX_VALUES) return Analysis.Invalid("La série est limitée à $MAX_VALUES valeurs.")
        val values = tokens.map { parseNumber(it) ?: return Analysis.Invalid("Valeur invalide : « $it ».") }
        val result = describe(values) ?: return Analysis.Invalid("Résultat hors de la plage numérique représentable.")
        return Analysis.Valid(values, result)
    }

    fun describe(values: List<Double>): Result? {
        if (values.isEmpty() || values.size > MAX_VALUES || values.any { !it.isFinite() }) return null
        val sorted = values.sorted()
        return describeSorted(sorted)
    }

    /** Frequency counts are multiplicities, never fractional weights. */
    fun describeFrequencies(frequencies: List<Frequency>): Result? {
        if (frequencies.isEmpty() || frequencies.size > MAX_VALUES || frequencies.any { !it.value.isFinite() || it.count <= 0 }) return null
        val total = frequencies.sumOf { it.count.toLong() }
        if (total > MAX_VALUES) return null
        val expanded = ArrayList<Double>(total.toInt())
        frequencies.forEach { item -> repeat(item.count) { expanded += item.value } }
        return describe(expanded)
    }

    fun quantile(values: List<Double>, percentile: Double): Double? {
        if (values.isEmpty() || values.any { !it.isFinite() } || !percentile.isFinite() || percentile !in 0.0..100.0) return null
        return quantileSorted(values.sorted(), percentile)
    }

    private fun describeSorted(sorted: List<Double>): Result? {
        val n = sorted.size
        val scale = sorted.maxOf { abs(it) }.coerceAtLeast(1.0)
        var meanScaled = 0.0
        var m2 = 0.0
        var m3 = 0.0
        var m4 = 0.0
        sorted.forEachIndexed { i, value ->
            val x = value / scale
            val oldN = i.toDouble()
            val count = oldN + 1.0
            val delta = x - meanScaled
            val deltaN = delta / count
            val deltaN2 = deltaN * deltaN
            val term = delta * deltaN * oldN
            m4 += term * deltaN2 * (count * count - 3.0 * count + 3.0) + 6.0 * deltaN2 * m2 - 4.0 * deltaN * m3
            m3 += term * deltaN * (count - 2.0) - 3.0 * deltaN * m2
            m2 += term
            meanScaled += deltaN
        }
        val mean = meanScaled * scale
        val sum = mean * n
        val popVarScaled = (m2 / n).coerceAtLeast(0.0)
        val popVar = popVarScaled * scale * scale
        val popStd = sqrt(popVarScaled) * scale
        val sampleVar = if (n > 1) popVarScaled * n / (n - 1) * scale * scale else null
        val sampleStd = sampleVar?.let(::sqrt)
        val q1 = quantileSorted(sorted, 25.0)
        val median = quantileSorted(sorted, 50.0)
        val q3 = quantileSorted(sorted, 75.0)
        val frequencies = sorted.groupingBy { it }.eachCount()
        val maxFrequency = frequencies.values.maxOrNull() ?: 1
        val dominant = maxFrequency > 1
        val modes = if (dominant) frequencies.filterValues { it == maxFrequency }.keys.sorted() else emptyList()
        val skew = if (n >= 3 && m2 > 0.0) (sqrt(n.toDouble() * (n - 1)) / (n - 2)) * ((m3 / n) / (m2 / n).pow(1.5)) else null
        val kurtosis = if (n >= 4 && m2 > 0.0) {
            val g2 = (m4 / n) / (m2 / n).pow(2) - 3.0
            ((n - 1.0) / ((n - 2.0) * (n - 3.0))) * ((n + 1.0) * g2 + 6.0)
        } else null
        val range = sorted.last() - sorted.first()
        val iqr = q3 - q1
        val popCv = if (mean != 0.0) 100.0 * popStd / abs(mean) else null
        val sampleCv = if (mean != 0.0 && sampleStd != null) 100.0 * sampleStd / abs(mean) else null
        val values = listOf(sum, mean, median, sorted.first(), sorted.last(), popVar, popStd, q1, q3) + listOfNotNull(sampleVar, sampleStd, skew, kurtosis, popCv, sampleCv)
        if (values.any { !it.isFinite() }) return null
        return Result(
            count = n, sum = sum, mean = mean, median = median, minimum = sorted.first(), maximum = sorted.last(),
            populationStandardDeviation = popStd, populationVariance = popVar, sampleVariance = sampleVar,
            sampleStandardDeviation = sampleStd, range = range.takeIf(Double::isFinite), modes = modes,
            hasDominantMode = dominant, q1 = q1, q3 = q3, interquartileRange = iqr.takeIf(Double::isFinite),
            populationCoefficientOfVariation = popCv, sampleCoefficientOfVariation = sampleCv,
            skewness = skew?.takeIf(Double::isFinite), excessKurtosis = kurtosis?.takeIf(Double::isFinite)
        )
    }

    private fun quantileSorted(sorted: List<Double>, percentile: Double): Double {
        if (percentile == 0.0) return sorted.first()
        if (percentile == 100.0) return sorted.last()
        val index = (sorted.size - 1) * percentile / 100.0
        val lower = index.toInt()
        val fraction = index - lower
        return sorted[lower] * (1.0 - fraction) + sorted[(lower + 1).coerceAtMost(sorted.lastIndex)] * fraction
    }

    fun parseNumber(token: String): Double? {
        val normalized = token.trim().replace(',', '.')
        if (!NUMBER.matches(normalized)) return null
        return normalized.toDoubleOrNull()?.takeIf(Double::isFinite)
    }

    fun format(value: Double): String = ConversionFormatter.format(value)

    private val WHITESPACE = Regex("\\s+")
    private val NUMBER = Regex("[+-]?(?:\\d+(?:\\.\\d*)?|\\.\\d+)(?:[eE][+-]?\\d+)?")
}
