package com.example.calcstudiopro.functions.graph

import com.example.calcstudiopro.domain.functions.ExpressionEvaluation
import java.util.TreeMap
import java.util.ArrayDeque
import kotlin.math.min
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
            budgetLimit,
            sampler.initialIntervalCount,
            sampler.intervalsExplored,
            sampler.sampledMinimumX,
            sampler.sampledMaximumX,
            sampler.representedXFraction(segments),
            sampler.visibleXFraction(segments),
            sampler.unresolvedIntervalCount
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
        budget: FunctionGraphBudgetLimit?,
        initialIntervals: Int = 0,
        intervalsExplored: Int = 0,
        sampledMinimumX: Double? = null,
        sampledMaximumX: Double? = null,
        representedXFraction: Double = 0.0,
        visibleXFraction: Double = 0.0,
        unresolvedIntervalCount: Int = 0
    ) = FunctionGraphResult(
        id, segments, status,
        FunctionGraphDiagnostics(evaluations, subdivisions, maxDepth, breaks, budget, initialIntervals,
            intervalsExplored, sampledMinimumX, sampledMaximumX, representedXFraction, visibleXFraction, unresolvedIntervalCount)
    )

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
        var initialIntervalCount = 0
            private set
        var intervalsExplored = 0
            private set
        val unresolvedIntervalCount: Int get() = unresolved.size
        val sampledMinimumX: Double? get() = samples.firstKeyOrNull()
        val sampledMaximumX: Double? get() = samples.lastKeyOrNull()
        private val unresolved = HashSet<UnresolvedInterval>()
        val discontinuityBreakCount: Int
            get() = samples.values.count { it.y == null }
        private var reachedLimit: FunctionGraphBudgetLimit? = null
        var segmentBudgetReached = false
            private set
        private val budget get() = request.budget

        fun run() {
            val configuredIntervals = budget.initialIntervals(request.plotWidthPixels)
            val available = min(
                min(budget.maximumEvaluationsPerFunction - evaluationCount, counter.remaining),
                budget.maximumPointsPerFunction - samples.size
            ).coerceAtLeast(0)
            initialIntervalCount = min(configuredIntervals, ((available - 1) / 2).coerceAtLeast(0))
            if (initialIntervalCount == 0) {
                try {
                    evaluate(request.xRange.minimum)
                } catch (limit: BudgetReached) {
                    throw limit
                }
                reachedLimit = if (counter.remaining == 0) FunctionGraphBudgetLimit.TOTAL_EVALUATIONS else FunctionGraphBudgetLimit.FUNCTION_EVALUATIONS
                throw BudgetReached(reachedLimit!!)
            }

            val anchors = ArrayList<Sample>(initialIntervalCount + 1)
            try {
                for (index in 0..initialIntervalCount) {
                    cancellationCheck()
                    anchors += evaluate(interpolateX(index.toDouble() / initialIntervalCount))
                }
            } catch (limit: BudgetReached) {
                anchors.zipWithNext().forEach { (left, right) -> unresolved += UnresolvedInterval(left.x, right.x) }
                throw limit
            }

            val queue = ArrayDeque<Interval>()
            for (index in 0 until initialIntervalCount) queue.addLast(Interval(anchors[index], anchors[index + 1], 0))
            while (queue.isNotEmpty()) {
                cancellationCheck()
                val interval = queue.removeFirst()
                intervalsExplored++
                maximumDepthReached = maxOf(maximumDepthReached, interval.depth)
                val middleX = midpoint(interval.left.x, interval.right.x)
                if (middleX <= interval.left.x || middleX >= interval.right.x) continue
                val middle = try {
                    evaluate(middleX)
                } catch (limit: BudgetReached) {
                    unresolved += UnresolvedInterval(interval.left.x, interval.right.x)
                    while (queue.isNotEmpty()) {
                        val pending = queue.removeFirst()
                        unresolved += UnresolvedInterval(pending.left.x, pending.right.x)
                    }
                    throw limit
                }
                val leftDefined = interval.left.y != null
                val middleDefined = middle.y != null
                val rightDefined = interval.right.y != null
                val containsGap = !leftDefined || !middleDefined || !rightDefined
                if (!leftDefined && !middleDefined && !rightDefined) continue
                val errorPixels = if (!containsGap) chordErrorPixels(interval.left.y!!, middle.y!!, interval.right.y!!) else Double.POSITIVE_INFINITY
                if (!containsGap && errorPixels <= budget.chordTolerancePixels) continue

                if (interval.depth >= budget.maximumSubdivisionDepth) {
                    if (leftDefined && middleDefined && rightDefined) samples[middle.x] = Sample(middle.x, null)
                    unresolved += UnresolvedInterval(interval.left.x, interval.right.x)
                    reachedLimit = FunctionGraphBudgetLimit.SUBDIVISION_DEPTH
                    continue
                }
                subdivisionCount++
                queue.addLast(Interval(interval.left, middle, interval.depth + 1))
                queue.addLast(Interval(middle, interval.right, interval.depth + 1))
            }
            reachedLimit?.let { throw BudgetReached(it) }
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
            var previous: Sample? = null
            for ((x, sample) in samples) {
                previous?.let { if (UnresolvedInterval(it.x, x) in unresolved) finish() }
                if (sample.y == null) finish()
                else current += FunctionGraphPoint(x, sample.y)
                previous = sample
            }
            finish()
            return result
        }

        fun representedXFraction(segments: List<FunctionGraphSegment>): Double {
            val span = request.xRange.maximum - request.xRange.minimum
            if (!span.isFinite() || span <= 0.0) return 0.0
            val represented = segments.sumOf { it.points.last().x - it.points.first().x }
            return (represented / span).takeIf(Double::isFinite)?.coerceIn(0.0, 1.0) ?: 0.0
        }

        fun visibleXFraction(segments: List<FunctionGraphSegment>): Double {
            val xSpan = request.xRange.maximum - request.xRange.minimum
            if (!xSpan.isFinite() || xSpan <= 0.0) return 0.0
            var visibleLength = 0.0
            val yMinimum = request.yRange.minimum
            val yMaximum = request.yRange.maximum
            for (segment in segments) for ((first, second) in segment.points.zipWithNext()) {
                val dx = second.x - first.x
                if (!dx.isFinite() || dx <= 0.0) continue
                var start = 0.0
                var end = 1.0
                if (first.y < yMinimum) {
                    if (second.y < yMinimum) continue
                    start = boundaryParameter(first.y, second.y, yMinimum) ?: continue
                } else if (first.y > yMaximum) {
                    if (second.y > yMaximum) continue
                    start = boundaryParameter(first.y, second.y, yMaximum) ?: continue
                }
                if (second.y < yMinimum) {
                    end = boundaryParameter(first.y, second.y, yMinimum) ?: continue
                } else if (second.y > yMaximum) {
                    end = boundaryParameter(first.y, second.y, yMaximum) ?: continue
                }
                if (end > start) visibleLength += dx * (end - start)
            }
            return (visibleLength / xSpan).takeIf(Double::isFinite)?.coerceIn(0.0, 1.0) ?: 0.0
        }

        private fun boundaryParameter(firstY: Double, secondY: Double, boundary: Double): Double? {
            val scale = maxOf(abs(firstY), abs(secondY), abs(boundary))
            if (!scale.isFinite() || scale == 0.0) return null
            val denominator = secondY / scale - firstY / scale
            if (!denominator.isFinite() || denominator == 0.0) return null
            return ((boundary / scale - firstY / scale) / denominator)
                .takeIf(Double::isFinite)?.coerceIn(0.0, 1.0)
        }

        private fun TreeMap<Double, Sample>.firstKeyOrNull(): Double? = if (isEmpty()) null else firstKey()
        private fun TreeMap<Double, Sample>.lastKeyOrNull(): Double? = if (isEmpty()) null else lastKey()

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

    private data class Interval(val left: Sample, val right: Sample, val depth: Int)
    private data class UnresolvedInterval(val minimumX: Double, val maximumX: Double)
    private data class Sample(val x: Double, val y: Double?)

    internal class EvaluationCounter(private val maximum: Int) {
        var count: Int = 0
            private set
        val remaining: Int get() = maximum - count

        fun consume() {
            if (count >= maximum) throw BudgetReached(FunctionGraphBudgetLimit.TOTAL_EVALUATIONS)
            count++
        }
    }

    private class BudgetReached(val limit: FunctionGraphBudgetLimit) : RuntimeException()
}
