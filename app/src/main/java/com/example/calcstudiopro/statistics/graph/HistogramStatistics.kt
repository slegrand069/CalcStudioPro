package com.example.calcstudiopro.statistics.graph

import com.example.calcstudiopro.statistics.graph.domain.GraphTransform
import kotlin.math.ceil
import kotlin.math.ln
import kotlin.math.max

enum class HistogramClassMode { AUTOMATIC, MANUAL }
enum class HistogramScale { COUNT, PERCENT }

data class HistogramBin(val lower: Double, val upper: Double, val count: Int, val percent: Double)

data class HistogramResult(
    val bins: List<HistogramBin>,
    val observationCount: Int,
    val requestedClassCount: Int,
    val nonFiniteCount: Int,
    val message: String? = null
)

/** Equal-width, contiguous histogram bins; manually selected classes are capped at min(100, n). */
object HistogramStatistics {
    const val MAX_MANUAL_CLASSES = 100

    fun sturgesClassCount(observationCount: Int): Int = if (observationCount <= 1) 1 else
        ceil(1.0 + ln(observationCount.toDouble()) / ln(2.0)).toInt().coerceAtLeast(1)

    fun calculate(
        values: List<Double>,
        mode: HistogramClassMode = HistogramClassMode.AUTOMATIC,
        manualClassCount: Int = 1
    ): HistogramResult {
        val finite = values.filter(Double::isFinite)
        val ignored = values.size - finite.size
        if (finite.isEmpty()) return HistogramResult(emptyList(), 0, 0, ignored, "Aucune observation finie à représenter.")
        val limit = minOf(MAX_MANUAL_CLASSES, finite.size).coerceAtLeast(1)
        val requested = when (mode) {
            HistogramClassMode.AUTOMATIC -> sturgesClassCount(finite.size).coerceAtMost(limit)
            HistogramClassMode.MANUAL -> manualClassCount.coerceIn(1, limit)
        }
        val minimum = finite.minOrNull()!!
        val maximum = finite.maxOrNull()!!
        if (minimum == maximum) {
            val (lower, upper) = constantBounds(minimum)
            return HistogramResult(
                bins = listOf(HistogramBin(lower, upper, finite.size, 100.0)),
                observationCount = finite.size,
                requestedClassCount = requested,
                nonFiniteCount = ignored,
                message = messages(ignored, "Série constante : une seule classe contient toutes les observations.")
            )
        }

        val edges = ArrayList<Double>(requested + 1)
        for (index in 0..requested) {
            val edge = when (index) {
                0 -> minimum
                requested -> maximum
                else -> GraphTransform.interpolate(minimum, maximum, index.toDouble() / requested)
            }
            if (edges.isEmpty() || edge > edges.last()) edges += edge
        }
        // If the source range has less representable Double steps than requested bins,
        // merge zero-width bins while preserving a strictly increasing edge list.
        val actualClassCount = (edges.size - 1).coerceAtLeast(1)
        val counts = IntArray(actualClassCount)
        finite.forEach { value -> counts[binIndex(value, edges)]++ }
        val bins = counts.indices.map { index ->
            val count = counts[index]
            HistogramBin(edges[index], edges[index + 1], count, 100.0 * count / finite.size)
        }
        val reductionMessage = if (actualClassCount < requested) "Le nombre de classes a été réduit à $actualClassCount, faute de frontières distinctes représentables." else null
        return HistogramResult(
            bins = bins,
            observationCount = finite.size,
            requestedClassCount = requested,
            nonFiniteCount = ignored,
            message = messages(ignored, reductionMessage)
        )
    }

    private fun binIndex(value: Double, edges: List<Double>): Int {
        val lastBin = edges.lastIndex - 1
        if (value >= edges.last()) return lastBin
        var low = 0
        var high = lastBin
        while (low <= high) {
            val middle = (low + high) ushr 1
            when {
                value < edges[middle] -> high = middle - 1
                value >= edges[middle + 1] -> low = middle + 1
                else -> return middle
            }
        }
        return low.coerceIn(0, lastBin)
    }

    private fun constantBounds(value: Double): Pair<Double, Double> {
        val delta = if (value == 0.0) 0.5 else max(kotlin.math.abs(value) * 0.05, Math.ulp(value) * 4.0)
        var lower = value - delta
        var upper = value + delta
        if (!lower.isFinite() || lower == value) lower = Math.nextDown(value)
        if (!upper.isFinite() || upper == value) upper = Math.nextUp(value)
        if (!lower.isFinite()) lower = value
        if (!upper.isFinite()) upper = value
        if (lower == upper) {
            if (upper < Double.MAX_VALUE) upper = Math.nextUp(upper) else lower = Math.nextDown(lower)
        }
        return lower to upper
    }

    private fun messages(ignored: Int, extra: String?): String? = listOfNotNull(
        extra,
        if (ignored > 0) "$ignored valeur(s) non finie(s) ignorée(s)." else null
    ).joinToString(" ").ifBlank { null }
}
