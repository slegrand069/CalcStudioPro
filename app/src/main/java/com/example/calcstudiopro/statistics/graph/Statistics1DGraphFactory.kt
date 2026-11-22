package com.example.calcstudiopro.statistics.graph

import com.example.calcstudiopro.statistics.StatisticsEngine
import com.example.calcstudiopro.statistics.graph.model.GraphBar
import com.example.calcstudiopro.statistics.graph.model.GraphPoint
import com.example.calcstudiopro.statistics.graph.model.GraphPointDetail
import com.example.calcstudiopro.statistics.graph.model.GraphSegment
import com.example.calcstudiopro.statistics.graph.model.GraphSeries
import com.example.calcstudiopro.statistics.graph.model.GraphSeriesKind
import com.example.calcstudiopro.statistics.graph.model.GraphSnapshot
import com.example.calcstudiopro.statistics.graph.model.GraphTickMode
import kotlin.math.abs
import kotlin.math.max

object Statistics1DGraphFactory {
    fun histogram(result: HistogramResult, scale: HistogramScale): GraphSnapshot {
        val bars = result.bins.mapIndexed { index, bin ->
            val height = when (scale) {
                HistogramScale.COUNT -> bin.count.toDouble()
                HistogramScale.PERCENT -> bin.percent
            }
            GraphBar(
                id = "class:$index",
                xMinimum = bin.lower,
                xMaximum = bin.upper,
                yMinimum = 0.0,
                yMaximum = height,
                details = listOf(
                    GraphPointDetail("Borne inférieure [incluse]", bin.lower),
                    GraphPointDetail("Borne supérieure ${if (index == result.bins.lastIndex) "incluse" else "exclue"}", bin.upper),
                    GraphPointDetail("Effectif", bin.count.toDouble(), wholeNumber = true),
                    GraphPointDetail("Fréquence", bin.percent, percentage = true)
                )
            )
        }
        return GraphSnapshot(
            "Histogramme des observations 1D",
            "Valeur",
            if (scale == HistogramScale.COUNT) "Effectif" else "Fréquence",
            listOf(GraphSeries("histogram", "Classes", points = emptyList(), kind = GraphSeriesKind.BARS, bars = bars)),
            yTickMode = if (scale == HistogramScale.COUNT) GraphTickMode.INTEGER else GraphTickMode.PERCENT
        )
    }

    fun tukeyBoxPlot(result: TukeyResult): GraphSnapshot {
        val summary = result.summary ?: return GraphSnapshot("Boîte à moustaches de Tukey", "Valeur", "", emptyList(), showGrid = false, showYAxis = false)
        val boxBounds = if (summary.q1 == summary.q3) expandedConstant(summary.q1) else summary.q1 to summary.q3
        val center = 0.0
        val boxHalfHeight = 0.34
        val capHalfHeight = 0.18
        val boxDetails = buildList {
            add(GraphPointDetail("Q1", summary.q1))
            add(GraphPointDetail("Médiane", summary.median))
            add(GraphPointDetail("Q3", summary.q3))
            summary.interquartileRange?.let { add(GraphPointDetail("IQR", it)) }
        }
        val segments = listOf(
            GraphSegment("whisker-lower", summary.lowerWhisker, center, summary.q1, center, listOf(
                GraphPointDetail("Moustache inférieure", summary.lowerWhisker), GraphPointDetail("Q1", summary.q1)
            )),
            GraphSegment("cap-lower", summary.lowerWhisker, -capHalfHeight, summary.lowerWhisker, capHalfHeight,
                listOf(GraphPointDetail("Moustache inférieure", summary.lowerWhisker))),
            GraphSegment("box-left", boxBounds.first, -boxHalfHeight, boxBounds.first, boxHalfHeight,
                listOf(GraphPointDetail("Q1", summary.q1))),
            GraphSegment("box-top", boxBounds.first, boxHalfHeight, boxBounds.second, boxHalfHeight, boxDetails),
            GraphSegment("box-bottom", boxBounds.first, -boxHalfHeight, boxBounds.second, -boxHalfHeight, boxDetails),
            GraphSegment("box-right", boxBounds.second, -boxHalfHeight, boxBounds.second, boxHalfHeight,
                listOf(GraphPointDetail("Q3", summary.q3))),
            GraphSegment("median", summary.median, -boxHalfHeight, summary.median, boxHalfHeight,
                listOf(GraphPointDetail("Médiane", summary.median))),
            GraphSegment("whisker-upper", summary.q3, center, summary.upperWhisker, center, listOf(
                GraphPointDetail("Q3", summary.q3), GraphPointDetail("Moustache supérieure", summary.upperWhisker)
            )),
            GraphSegment("cap-upper", summary.upperWhisker, -capHalfHeight, summary.upperWhisker, capHalfHeight,
                listOf(GraphPointDetail("Moustache supérieure", summary.upperWhisker)))
        )
        val box = GraphBar(
            id = "box-area",
            xMinimum = boxBounds.first,
            xMaximum = boxBounds.second,
            yMinimum = -boxHalfHeight,
            yMaximum = boxHalfHeight,
            details = boxDetails,
            fillAlpha = 0.18f
        )
        val duplicateRank = HashMap<Double, Int>()
        val outliers = summary.outliers.mapIndexed { index, value ->
            val rank = duplicateRank.getOrDefault(value, 0)
            duplicateRank[value] = rank + 1
            val multiplicity = summary.outlierMultiplicities[value] ?: 1
            val visualOffset = if (multiplicity <= 1) 0.0 else -0.24 + 0.48 * rank / (multiplicity - 1).toDouble()
            GraphPoint(
                id = "outlier:$index",
                x = value,
                y = visualOffset,
                details = buildList {
                    add(GraphPointDetail("Valeur atypique", value))
                    if (multiplicity > 1) add(GraphPointDetail("Multiplicité", multiplicity.toDouble(), wholeNumber = true))
                }
            )
        }
        return GraphSnapshot(
            "Boîte à moustaches de Tukey",
            "Valeur",
            "",
            listOf(
                GraphSeries("tukey-structure", "Boîte et moustaches", points = emptyList(), kind = GraphSeriesKind.POINTS, bars = listOf(box), segments = segments),
                GraphSeries("tukey-outliers", "Valeurs atypiques", outliers, colorIndex = 3)
            ),
            showGrid = false,
            showYAxis = false
        )
    }

    private fun expandedConstant(value: Double): Pair<Double, Double> {
        val delta = if (value == 0.0) 0.5 else max(abs(value) * 0.01, Math.ulp(value) * 4.0)
        var low = value - delta
        var high = value + delta
        if (!low.isFinite() || low == value) low = Math.nextDown(value)
        if (!high.isFinite() || high == value) high = Math.nextUp(value)
        if (!low.isFinite()) low = value
        if (!high.isFinite()) high = value
        if (low == high) {
            if (high < Double.MAX_VALUE) high = Math.nextUp(high) else low = Math.nextDown(low)
        }
        return low to high
    }
}
