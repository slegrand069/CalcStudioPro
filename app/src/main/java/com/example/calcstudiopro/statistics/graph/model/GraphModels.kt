package com.example.calcstudiopro.statistics.graph.model

/** Stable identity for a source observation; duplicate coordinates remain distinct points. */
data class GraphPoint(
    val id: String,
    val x: Double,
    val y: Double,
    /** False starts a new line segment and prevents connecting across a discontinuity. */
    val connectFromPrevious: Boolean = true,
    /** Optional exact numeric values shown when this point is selected. */
    val details: List<GraphPointDetail> = emptyList()
)

data class GraphPointDetail(val label: String, val value: Double, val wholeNumber: Boolean = false, val percentage: Boolean = false)

enum class GraphTickMode { DECIMAL, INTEGER, PERCENT }

enum class GraphSeriesKind { POINTS, LINE, POINTS_AND_LINE, BARS }

/** Filled rectangle in graph coordinates; used for histogram bins and the Tukey box. */
data class GraphBar(
    val id: String,
    val xMinimum: Double,
    val xMaximum: Double,
    val yMinimum: Double,
    val yMaximum: Double,
    val details: List<GraphPointDetail> = emptyList(),
    val fillAlpha: Float = 0.72f
)

/** Individually selectable line segment in graph coordinates. */
data class GraphSegment(
    val id: String,
    val x1: Double,
    val y1: Double,
    val x2: Double,
    val y2: Double,
    val details: List<GraphPointDetail> = emptyList()
)

data class GraphSeries(
    val id: String,
    val label: String,
    val points: List<GraphPoint>,
    val kind: GraphSeriesKind = GraphSeriesKind.POINTS,
    /** Palette slot, interpreted only by the renderer. */
    val colorIndex: Int = 0,
    val bars: List<GraphBar> = emptyList(),
    val segments: List<GraphSegment> = emptyList()
)

data class GraphSnapshot(
    val title: String,
    val xAxisLabel: String,
    val yAxisLabel: String,
    val series: List<GraphSeries>,
    val showGrid: Boolean = true,
    val showTicks: Boolean = true,
    val showXAxis: Boolean = true,
    val showYAxis: Boolean = true,
    val xTickMode: GraphTickMode = GraphTickMode.DECIMAL,
    val yTickMode: GraphTickMode = GraphTickMode.DECIMAL
) {
    val finitePointCount: Int
        get() = series.sumOf { line -> line.points.count { it.x.isFinite() && it.y.isFinite() } }
}

data class AxisRange(val minimum: Double, val maximum: Double) {
    init {
        require(minimum.isFinite() && maximum.isFinite() && minimum < maximum) {
            "Axis ranges must be finite and strictly increasing."
        }
    }
}

data class GraphViewport(val x: AxisRange, val y: AxisRange)

/** Plot rectangle and screen coordinates are logical pixels held as Double until drawing. */
data class PlotRect(val left: Double, val top: Double, val width: Double, val height: Double) {
    init { require(left.isFinite() && top.isFinite() && width.isFinite() && height.isFinite() && width > 0.0 && height > 0.0) }
}

data class ScreenPoint(val x: Double, val y: Double)
data class DataPoint(val x: Double, val y: Double)

data class GraphSelection(val seriesId: String, val point: GraphPoint)
data class GraphSelectionDetails(
    val selection: GraphSelection,
    val positionInHitGroup: Int = 1,
    val hitGroupSize: Int = 1
)

data class PointHit(val seriesId: String, val point: GraphPoint, val distancePixels: Double)
