package com.example.calcstudiopro.functions.graph

import com.example.calcstudiopro.domain.functions.CompiledMathExpression
import com.example.calcstudiopro.domain.functions.FunctionAngleMode
import com.example.calcstudiopro.domain.functions.MathExpressionError
import com.example.calcstudiopro.statistics.graph.model.AxisRange
import com.example.calcstudiopro.functions.domain.FunctionSlotId
import java.util.Collections
import java.util.LinkedHashMap

/** A point in mathematical coordinates. It deliberately has no screen-coordinate counterpart. */
data class FunctionGraphPoint(val x: Double, val y: Double)

class FunctionGraphSegment(points: List<FunctionGraphPoint>) {
    val points: List<FunctionGraphPoint> = Collections.unmodifiableList(ArrayList(points))

    init {
        require(this.points.size >= 2) { "A drawable continuous segment needs at least two points." }
        require(this.points.all { it.x.isFinite() && it.y.isFinite() })
        require(this.points.zipWithNext().all { (a, b) -> a.x < b.x })
    }
}

/** Either a compiled expression, an empty slot, or a source expression that failed compilation. */
data class FunctionGraphInput(
    val id: FunctionSlotId,
    val expression: CompiledMathExpression? = null,
    val compilationError: MathExpressionError? = null,
    val isVisible: Boolean = true,
    /** Null is conservative for callers predating dependency metadata. */
    val parameterDependencies: Set<Char>? = null
) {
    init {
        require(expression == null || compilationError == null)
        require(parameterDependencies == null || parameterDependencies.all { it.isLetter() })
    }
}

data class FunctionGraphBudget(
    /** Starting intervals are increased with plot width, up to [maximumInitialIntervals]. */
    val minimumInitialIntervals: Int = 16,
    /** Midpoint interleaving makes the initial mesh about one sample per pixel. */
    val maximumInitialIntervals: Int = 512,
    val pixelsPerInitialInterval: Int = 2,
    val maximumEvaluationsPerFunction: Int = 4096,
    val maximumTotalEvaluations: Int = 24_576,
    val maximumSubdivisionDepth: Int = 12,
    /** Slightly above the evaluation cap so this independent guard does not cut it short. */
    val maximumPointsPerFunction: Int = 4097,
    val maximumSegmentsPerFunction: Int = 256,
    /** Sub-pixel chord error target; calibrated in the sampler tests. */
    val chordTolerancePixels: Double = 0.75
) {
    init {
        require(minimumInitialIntervals >= 1)
        require(maximumInitialIntervals >= minimumInitialIntervals)
        require(pixelsPerInitialInterval >= 1)
        require(maximumEvaluationsPerFunction >= 3)
        require(maximumTotalEvaluations >= 1)
        require(maximumSubdivisionDepth >= 0)
        require(maximumPointsPerFunction >= 2)
        require(maximumSegmentsPerFunction >= 1)
        require(chordTolerancePixels.isFinite() && chordTolerancePixels > 0.0)
    }

    internal fun initialIntervals(plotWidthPixels: Int): Int =
        (plotWidthPixels / pixelsPerInitialInterval).coerceIn(minimumInitialIntervals, maximumInitialIntervals)
}

/** Immutable calculation input. Collection inputs are defensively copied. */
class FunctionGraphRequest(
    val generation: Long,
    val xRange: AxisRange,
    val yRange: AxisRange,
    val plotWidthPixels: Int,
    val plotHeightPixels: Int,
    functions: List<FunctionGraphInput>,
    parameters: Map<Char, Double> = emptyMap(),
    val angleMode: FunctionAngleMode = FunctionAngleMode.RAD,
    val budget: FunctionGraphBudget = FunctionGraphBudget()
) {
    val functions: List<FunctionGraphInput> = Collections.unmodifiableList(ArrayList(functions))
    val parameters: Map<Char, Double> = Collections.unmodifiableMap(LinkedHashMap(parameters))

    init {
        require(generation >= 0)
        require(plotWidthPixels > 0 && plotHeightPixels > 0)
        require(this.functions.size <= 6)
        require(this.functions.map { it.id }.distinct().size == this.functions.size)
        require(this.parameters.values.all(Double::isFinite))
    }
}

enum class FunctionGraphBudgetLimit { FUNCTION_EVALUATIONS, TOTAL_EVALUATIONS, SUBDIVISION_DEPTH, POINTS, SEGMENTS }

sealed interface FunctionGraphStatus {
    data object Success : FunctionGraphStatus
    data object Empty : FunctionGraphStatus
    data object NoDefinedPortion : FunctionGraphStatus
    data class Error(val error: MathExpressionError) : FunctionGraphStatus
    data class Partial(val limit: FunctionGraphBudgetLimit) : FunctionGraphStatus
    data class BudgetExceeded(val limit: FunctionGraphBudgetLimit) : FunctionGraphStatus
}

data class FunctionGraphDiagnostics(
    val evaluationCount: Int,
    val subdivisionCount: Int,
    val maximumDepthReached: Int,
    val discontinuityBreakCount: Int,
    val budgetLimit: FunctionGraphBudgetLimit? = null,
    val initialIntervalCount: Int = 0,
    val intervalsExplored: Int = 0,
    val sampledMinimumX: Double? = null,
    val sampledMaximumX: Double? = null,
    /** Sum of represented segment X lengths divided by the requested X span. */
    val representedXFraction: Double = 0.0,
    /** Fraction of the X window whose piecewise-linear curve lies inside the requested Y window. */
    val visibleXFraction: Double = 0.0,
    /** Intervals deliberately omitted because the available sampling could not resolve them. */
    val unresolvedIntervalCount: Int = 0
)

class FunctionGraphResult(
    val id: FunctionSlotId,
    segments: List<FunctionGraphSegment>,
    val status: FunctionGraphStatus,
    val diagnostics: FunctionGraphDiagnostics
) {
    val segments: List<FunctionGraphSegment> = Collections.unmodifiableList(ArrayList(segments))
}

class FunctionGraphBatchResult(val generation: Long, results: List<FunctionGraphResult>, val totalEvaluationCount: Int) {
    val results: List<FunctionGraphResult> = Collections.unmodifiableList(ArrayList(results))
}

sealed interface FunctionGraphCalculationOutcome {
    data class Completed(val result: FunctionGraphBatchResult) : FunctionGraphCalculationOutcome
    data class Obsolete(val generation: Long) : FunctionGraphCalculationOutcome
}
