package com.example.calcstudiopro.statistics.graph

import com.example.calcstudiopro.statistics.StatisticsEngine
import java.math.BigDecimal

data class TukeySummary(
    val observationCount: Int,
    val minimum: Double,
    val q1: Double,
    val median: Double,
    val q3: Double,
    val maximum: Double,
    val interquartileRange: Double?,
    val lowerFence: Double,
    val upperFence: Double,
    val lowerWhisker: Double,
    val upperWhisker: Double,
    /** Original order and duplicate values are preserved. */
    val outliers: List<Double>,
    val outlierMultiplicities: Map<Double, Int>,
    val nonFiniteCount: Int
)

data class TukeyResult(val summary: TukeySummary?, val message: String? = null)

/** Tukey summaries reuse STAT 1D's existing linearly interpolated quantiles. */
object TukeyStatistics {
    fun calculate(values: List<Double>): TukeyResult {
        val finite = values.filter(Double::isFinite)
        val ignored = values.size - finite.size
        if (finite.isEmpty()) return TukeyResult(null, "Aucune observation finie à représenter.")

        // StatisticsEngine.quantile uses the exact STAT 1D (n - 1) × p convention.
        val q1 = StatisticsEngine.quantile(finite, 25.0)
        val median = StatisticsEngine.quantile(finite, 50.0)
        val q3 = StatisticsEngine.quantile(finite, 75.0)
        if (q1 == null || median == null || q3 == null) {
            return TukeyResult(null, "Les quartiles ne sont pas calculables pour ces observations.")
        }

        val iqrValue = q3 - q1
        val exactIqr = if (iqrValue.isFinite()) BigDecimal.valueOf(iqrValue)
            else BigDecimal.valueOf(q3).subtract(BigDecimal.valueOf(q1))
        val onePointFiveIqr = exactIqr.multiply(BigDecimal("1.5"))
        val lowerFenceExact = BigDecimal.valueOf(q1).subtract(onePointFiveIqr)
        val upperFenceExact = BigDecimal.valueOf(q3).add(onePointFiveIqr)
        val inliers = finite.filter { value ->
            val decimal = BigDecimal.valueOf(value)
            decimal >= lowerFenceExact && decimal <= upperFenceExact
        }
        val outliers = finite.filter { value ->
            val decimal = BigDecimal.valueOf(value)
            decimal < lowerFenceExact || decimal > upperFenceExact
        }
        val iqr = iqrValue.takeIf(Double::isFinite)
        val summary = TukeySummary(
            observationCount = finite.size,
            minimum = finite.minOrNull()!!,
            q1 = q1,
            median = median,
            q3 = q3,
            maximum = finite.maxOrNull()!!,
            interquartileRange = iqr,
            lowerFence = saturatingDouble(lowerFenceExact),
            upperFence = saturatingDouble(upperFenceExact),
            lowerWhisker = inliers.minOrNull() ?: q1,
            upperWhisker = inliers.maxOrNull() ?: q3,
            outliers = outliers,
            outlierMultiplicities = outliers.groupingBy { it }.eachCount(),
            nonFiniteCount = ignored
        )
        val message = buildList {
            if (iqr == null) add("IQR hors de la plage numérique représentable; les quartiles et moustaches restent affichés.")
            if (ignored > 0) add("$ignored valeur(s) non finie(s) ignorée(s).")
        }.joinToString(" ").ifBlank { null }
        return TukeyResult(summary, message)
    }

    private fun saturatingDouble(value: BigDecimal): Double {
        val max = BigDecimal.valueOf(Double.MAX_VALUE)
        return when {
            value > max -> Double.MAX_VALUE
            value < max.negate() -> -Double.MAX_VALUE
            else -> value.toDouble()
        }
    }
}
