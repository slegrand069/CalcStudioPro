package com.example.calcstudiopro.statistics.graph

import com.example.calcstudiopro.statistics.Statistics2DEngine
import com.example.calcstudiopro.statistics.graph.domain.GraphRange
import com.example.calcstudiopro.statistics.graph.domain.GraphTransform
import com.example.calcstudiopro.statistics.graph.model.GraphPoint
import com.example.calcstudiopro.statistics.graph.model.GraphPointDetail
import com.example.calcstudiopro.statistics.graph.model.GraphSeries
import com.example.calcstudiopro.statistics.graph.model.GraphSeriesKind
import com.example.calcstudiopro.statistics.graph.model.GraphSnapshot

/** Converts the active STAT 2D fit and observations into graph-only data. */
object StatisticsGraphFactory {
    private const val CURVE_INTERVALS = 384

    data class Result(
        val snapshot: GraphSnapshot,
        val unavailableMessage: String? = null,
        val excludedResidualCount: Int = 0
    )

    fun regressionScatter(
        observations: List<Statistics2DEngine.PairValue>,
        fit: Statistics2DEngine.Fit?,
        unavailableReason: String?
    ): Result {
        val multiplicities = observations.groupingBy { it.x to it.y }.eachCount()
        val points = observations.mapIndexed { index, pair ->
            val prediction = fit?.predict(pair.x)
            GraphPoint(
                id = "observation:$index",
                x = pair.x,
                y = pair.y,
                details = buildList {
                    add(GraphPointDetail("Multiplicité", (multiplicities[pair.x to pair.y] ?: 1).toDouble()))
                    prediction?.let {
                        add(GraphPointDetail("ŷ", it))
                        (pair.y - it).takeIf(Double::isFinite)?.let { residual -> add(GraphPointDetail("Résidu", residual)) }
                    }
                }
            )
        }
        val series = buildList {
            add(GraphSeries("observations", "Observations", points, GraphSeriesKind.POINTS))
            if (fit != null && observations.isNotEmpty()) {
                val minimumX = observations.minOf { it.x }
                val maximumX = observations.maxOf { it.x }
                val curve = (0..CURVE_INTERVALS).map { index ->
                    val fraction = index.toDouble() / CURVE_INTERVALS
                    val x = GraphTransform.interpolate(minimumX, maximumX, fraction)
                    val y = fit.predict(x)
                    GraphPoint(
                        id = "fit:$index",
                        x = x,
                        y = y ?: Double.NaN,
                        connectFromPrevious = y != null
                    )
                }
                add(GraphSeries("selected-fit", "Régression sélectionnée", curve, GraphSeriesKind.LINE, colorIndex = 1))
            }
        }
        return Result(
            GraphSnapshot("Nuage X/Y et régression", "X", "Y", series),
            unavailableMessage = if (fit == null) unavailableReason ?: "Ajustement indisponible." else null
        )
    }

    fun residuals(
        observations: List<Statistics2DEngine.PairValue>,
        fit: Statistics2DEngine.Fit?,
        unavailableReason: String?
    ): Result {
        if (fit == null) {
            val line = referenceLine(observations)
            return Result(
                GraphSnapshot("Résidus", "X", "Résidu (Y − ŷ)", listOf(line)),
                unavailableMessage = unavailableReason ?: "Ajustement indisponible."
            )
        }

        val multiplicities = observations.groupingBy { it.x to it.y }.eachCount()
        val plotted = mutableListOf<GraphPoint>()
        var excluded = 0
        observations.forEachIndexed { index, pair ->
            val prediction = fit.predictions.getOrNull(index)
            val residual = fit.residuals.getOrNull(index)
            if (prediction == null || residual == null || !prediction.isFinite() || !residual.isFinite()) {
                excluded++
            } else {
                plotted += GraphPoint(
                    id = "residual:$index",
                    x = pair.x,
                    y = residual,
                    details = listOf(
                        GraphPointDetail("Y observé", pair.y),
                        GraphPointDetail("ŷ", prediction),
                        GraphPointDetail("Résidu", residual),
                        GraphPointDetail("Multiplicité", (multiplicities[pair.x to pair.y] ?: 1).toDouble())
                    )
                )
            }
        }
        val series = listOf(
            GraphSeries("residual-points", "Résidus", plotted, GraphSeriesKind.POINTS),
            referenceLine(observations)
        )
        return Result(
            GraphSnapshot("Résidus du modèle sélectionné", "X", "Résidu (Y − ŷ)", series),
            unavailableMessage = if (plotted.isEmpty()) "Aucun résidu calculable." else null,
            excludedResidualCount = excluded
        )
    }

    private fun referenceLine(observations: List<Statistics2DEngine.PairValue>): GraphSeries {
        val xRange = if (observations.isEmpty()) null else GraphRange.autoFit(
            GraphSnapshot(
                "X bounds",
                "X",
                "",
                listOf(GraphSeries("x-values", "X", observations.mapIndexed { index, point -> GraphPoint("x:$index", point.x, 0.0) }))
            )
        ).x
        val left = xRange?.minimum ?: -1.0
        val right = xRange?.maximum ?: 1.0
        return GraphSeries(
            id = "zero-reference",
            label = "Référence zéro",
            kind = GraphSeriesKind.LINE,
            points = listOf(GraphPoint("zero-left", left, 0.0), GraphPoint("zero-right", right, 0.0)),
            colorIndex = 2
        )
    }
}
