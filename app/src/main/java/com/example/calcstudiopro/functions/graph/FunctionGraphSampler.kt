package com.example.calcstudiopro.functions.graph

import com.example.calcstudiopro.domain.functions.ExpressionEvaluation
import java.util.TreeMap
import kotlin.math.abs

/** CPU-only adaptive sampler. It never creates screen coordinates or Compose objects. */
class FunctionGraphSampler {
    fun sample(
        request: FunctionGraphRequest,
        input: FunctionGraphInput,
        cancellationCheck: () -> Unit = {}
    ): FunctionGraphResult = sample(request, input, EvaluationCounter(request.budget.maximumTotalEvaluations), cancellationCheck)

    internal fun sample(
        request: FunctionGraphRequest,
        input: FunctionGraphInput,
        counter: EvaluationCounter,
        cancellationCheck: () -> Unit
    ): FunctionGraphResult {
        input.compilationError?.let {
            return result(input.id, emptyList(), FunctionGraphStatus.Error(it), 0, 0, 0, 0, null)
        }
        val expression = input.expression
            ?: return result(input.id, emptyList(), FunctionGraphStatus.Empty, 0, 0, 0, 0, null)

        val sampler = SamplingRun(request, expression, counter, cancellationCheck)
        var budgetLimit = try {
            sampler.run()
            null
        } catch (limit: BudgetReached) {
            limit.limit
        }

        val segments = sampler.toSegments()
        if (sampler.segmentBudgetReached) budgetLimit = FunctionGraphBudgetLimit.SEGMENTS
        val status = when {
            segments.isNotEmpty() && budgetLimit == null -> FunctionGraphStatus.Success
            segments.isNotEmpty() -> FunctionGraphStatus.Partial(budgetLimit!!)
            budgetLimit != null -> FunctionGraphStatus.BudgetExceeded(budgetLimit)
            else -> FunctionGraphStatus.NoDefinedPortion
        }
        return result(
            input.id,
            segments,
            status,
            sampler.evaluationCount,
            sampler.subdivisionCount,
            sampler.maximumDepthReached,
            sampler.discontinuityBreakCount,
            budgetLimit
        )
    }

    private fun result(
        id: com.example.calcstudiopro.functions.domain.FunctionSlotId,
        segments: List<FunctionGraphSegment>,
        status: FunctionGraphStatus,
        evaluations: Int,
        subdivisions: Int,
        maxDepth: Int,
        breaks: Int,
        budget: FunctionGraphBudgetLimit?
    ) = FunctionGraphResult(id, segments, status, FunctionGraphDiagnostics(evaluations, subdivisions, maxDepth, breaks, budget))

    private inner class SamplingRun(
        private val request: FunctionGraphRequest,
        private val expression: com.example.calcstudiopro.domain.functions.CompiledMathExpression,
        private val counter: EvaluationCounter,
        private val cancellationCheck: () -> Unit
    ) {
        private val samples = TreeMap<Double, Sample>()
        var evaluationCount = 0
            private set
        var subdivisionCount = 0
            private set
        var maximumDepthReached = 0
            private set
        val discontinuityBreakCount: Int
            get() = samples.values.count { it.y == null }
        private var reachedLimit: FunctionGraphBudgetLimit? = null
        var segmentBudgetReached = false
            private set
        private val budget get() = request.budget

        fun run() {
            val intervals = budget.initialIntervals(request.plotWidthPixels)
            for (index in 0 until intervals) {
                cancellationCheck()
                val leftX = interpolateX(index.toDouble() / intervals)
                val rightX = interpolateX((index + 1.0) / intervals)
                val left = evaluate(leftX)
                val right = evaluate(rightX)
                refine(left, right, 0)
            }
            reachedLimit?.let { throw BudgetReached(it) }
        }

        private fun refine(left: Sample, right: Sample, depth: Int) {
            cancellationCheck()
            maximumDepthReached = maxOf(maximumDepthReached, depth)
            val middleX = midpoint(left.x, right.x)
            if (middleX <= left.x || middleX >= right.x) {
                record(left)
                record(right)
                return
            }
            val middle = evaluate(middleX)
            val leftDefined = left.y != null
            val middleDefined = middle.y != null
            val rightDefined = right.y != null

            val containsGap = !leftDefined || !middleDefined || !rightDefined
            if (!leftDefined && !middleDefined && !rightDefined) {
                record(left)
                record(middle)
                record(right)
                return
            }
            val errorPixels = if (!containsGap) chordErrorPixels(left.y!!, middle.y!!, right.y!!) else Double.POSITIVE_INFINITY
            val needsRefinement = containsGap || errorPixels > budget.chordTolerancePixels

            if (!needsRefinement) {
                record(left)
                record(middle)
                record(right)
                return
            }

            if (depth >= budget.maximumSubdivisionDepth) {
                record(left)
                record(middle)
                record(right)
                if (leftDefined && middleDefined && rightDefined) {
                    // The samples did not settle continuity within the configured budget.
                    samples[middle.x] = Sample(middle.x, null)
                }
                reachedLimit = FunctionGraphBudgetLimit.SUBDIVISION_DEPTH
                return
            }

            subdivisionCount++
            refine(left, middle, depth + 1)
            refine(middle, right, depth + 1)
        }

        private fun evaluate(x: Double): Sample {
            samples[x]?.let { return it }
            cancellationCheck()
            if (samples.size >= budget.maximumPointsPerFunction) throw BudgetReached(FunctionGraphBudgetLimit.POINTS)
            if (evaluationCount >= budget.maximumEvaluationsPerFunction) {
                throw BudgetReached(FunctionGraphBudgetLimit.FUNCTION_EVALUATIONS)
            }
            counter.consume()
            evaluationCount++
            val y = when (val evaluated = expression.evaluate(x, request.parameters, request.angleMode)) {
                is ExpressionEvaluation.Success -> evaluated.value.takeIf(Double::isFinite)
                is ExpressionEvaluation.Failure -> null
            }
            return Sample(x, y).also { samples[x] = it }
        }

        private fun record(sample: Sample) {
            if (sample.x !in samples) {
                if (samples.size >= budget.maximumPointsPerFunction) throw BudgetReached(FunctionGraphBudgetLimit.POINTS)
                samples[sample.x] = sample
            }
        }

        fun toSegments(): List<FunctionGraphSegment> {
            val result = mutableListOf<FunctionGraphSegment>()
            var current = mutableListOf<FunctionGraphPoint>()
            fun finish() {
                if (current.size >= 2) {
                    if (result.size >= budget.maximumSegmentsPerFunction) {
                        segmentBudgetReached = true
                        current = mutableListOf()
                        return
                    }
                    result += FunctionGraphSegment(current)
                }
                current = mutableListOf()
            }
            for ((x, sample) in samples) {
                if (sample.y == null) finish()
                else current += FunctionGraphPoint(x, sample.y)
            }
            finish()
            return result
        }

        private fun chordErrorPixels(leftY: Double, middleY: Double, rightY: Double): Double {
            val min = request.yRange.minimum
            val max = request.yRange.maximum
            val scale = maxOf(abs(min), abs(max), abs(leftY), abs(middleY), abs(rightY))
            if (scale == 0.0) return 0.0
            val low = min / scale
            val high = max / scale
            val span = high - low
            if (!span.isFinite() || span <= 0.0) return Double.POSITIVE_INFINITY
            val left = (leftY / scale - low) / span
            val middle = (middleY / scale - low) / span
            val right = (rightY / scale - low) / span
            val deviation = abs(middle - (left / 2.0 + right / 2.0)) * request.plotHeightPixels
            return if (deviation.isFinite()) deviation else Double.POSITIVE_INFINITY
        }

        private fun interpolateX(fraction: Double): Double {
            val min = request.xRange.minimum
            val max = request.xRange.maximum
            val scale = maxOf(abs(min), abs(max))
            if (scale == 0.0) return 0.0
            return ((min / scale) * (1.0 - fraction) + (max / scale) * fraction) * scale
        }

        private fun midpoint(a: Double, b: Double): Double {
            val span = b - a
            return if (span.isFinite()) a + span / 2.0 else a / 2.0 + b / 2.0
        }
    }

    private data class Sample(val x: Double, val y: Double?)

    internal class EvaluationCounter(private val maximum: Int) {
        var count: Int = 0
            private set

        fun consume() {
            if (count >= maximum) throw BudgetReached(FunctionGraphBudgetLimit.TOTAL_EVALUATIONS)
            count++
        }
    }

    private class BudgetReached(val limit: FunctionGraphBudgetLimit) : RuntimeException()
}
