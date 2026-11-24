package com.example.calcstudiopro.functions.graph

import com.example.calcstudiopro.domain.functions.ExpressionCompilation
import com.example.calcstudiopro.domain.functions.FunctionAngleMode
import com.example.calcstudiopro.domain.functions.MathExpressionEngine
import com.example.calcstudiopro.functions.domain.FunctionSlotId
import com.example.calcstudiopro.statistics.graph.model.AxisRange
import kotlinx.coroutines.ensureActive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI

class FunctionGraphSamplerTest {
    private val engine = MathExpressionEngine()
    private val sampler = FunctionGraphSampler()

    @Test fun regularLinesAndPolynomialsProduceOrderedFiniteSegments() {
        for ((source, expectedAtOne) in listOf("x" to 1.0, "x^2" to 1.0, "sin(x)" to kotlin.math.sin(1.0))) {
            val result = sample(source)
            assertEquals(FunctionGraphStatus.Success, result.status)
            assertEquals(1, result.segments.size)
            val points = result.segments.single().points
            assertTrue(points.size >= 2)
            assertTrue(points.zipWithNext().all { (a, b) -> a.x < b.x })
            assertTrue(points.all { it.x.isFinite() && it.y.isFinite() })
            assertTrue(points.any { kotlin.math.abs(it.x - 1.0) < 1e-12 && kotlin.math.abs(it.y - expectedAtOne) < 0.03 })
        }
    }

    @Test fun aStraightLineNeedsNoAdaptiveSubdivisions() {
        val result = sample("x")
        assertEquals(0, result.diagnostics.subdivisionCount)
        // 320px / 8px gives 40 initial intervals with their midpoints: 81 evaluations.
        assertEquals(81, result.diagnostics.evaluationCount)
    }

    @Test fun refinementRespondsToPlotResolutionAndVerticalZoom() {
        val baseline = sample("x^2", width = 320, height = 240)
        val taller = sample("x^2", width = 320, height = 480)
        val narrowerY = sample("x^2", width = 320, height = 240, yRange = -1.0..1.0)
        assertTrue(taller.diagnostics.evaluationCount >= baseline.diagnostics.evaluationCount)
        assertTrue(narrowerY.diagnostics.evaluationCount >= baseline.diagnostics.evaluationCount)

        val narrowX = sample("x^2", xRange = -1.0..1.0)
        val wideX = sample("x^2", xRange = -10.0..10.0)
        assertTrue(wideX.diagnostics.evaluationCount >= narrowX.diagnostics.evaluationCount)
    }

    @Test fun reciprocalBreaksAtZeroWithoutConnectingBranches() {
        val result = sample("1/x", xRange = -1.0..1.0, yRange = -10.0..10.0)
        assertTrue(result.segments.size >= 2)
        assertTrue(result.segments.any { it.points.last().x < 0.0 })
        assertTrue(result.segments.any { it.points.first().x > 0.0 })
        assertFalse(result.segments.any { segment -> segment.points.first().x < 0 && segment.points.last().x > 0 })
    }

    @Test fun reciprocalAsymptoteBetweenInitialSamplesBreaksAsymmetricWindow() {
        val result = sample("1/x", xRange = -1.0..2.0, yRange = -10.0..10.0)
        val initialIntervals = FunctionGraphBudget().initialIntervals(320)
        val initialSamples = (0..initialIntervals).map { index -> -1.0 + 3.0 * index / initialIntervals }

        assertFalse(initialSamples.any { it == 0.0 })
        assertTrue(result.segments.size >= 2)
        assertTrue(result.segments.any { it.points.last().x < 0.0 })
        assertTrue(result.segments.any { it.points.first().x > 0.0 })
        assertFalse(result.segments.any { segment -> segment.points.first().x < 0.0 && segment.points.last().x > 0.0 })
        assertTrue(result.status == FunctionGraphStatus.Success || result.status is FunctionGraphStatus.Partial)
        assertTrue(result.diagnostics.discontinuityBreakCount > 0)
    }

    @Test fun tangentAsymptoteDoesNotJoinBranches() {
        val result = sample("tan(x)", xRange = 1.0..2.2, yRange = -10.0..10.0)
        assertTrue(result.segments.size >= 2)
        assertFalse(result.segments.any { it.points.first().x < PI / 2 && it.points.last().x > PI / 2 })
        val left = result.segments.filter { it.points.last().x < PI / 2 }.maxByOrNull { it.points.last().x }
        val right = result.segments.filter { it.points.first().x > PI / 2 }.minByOrNull { it.points.first().x }
        assertNotNull(left)
        assertNotNull(right)
        assertTrue(PI / 2 - left!!.points.last().x < 0.001)
        assertTrue(right!!.points.first().x - PI / 2 < 0.001)
        assertTrue(result.status == FunctionGraphStatus.Success || result.status is FunctionGraphStatus.Partial)
        assertEquals(result.status is FunctionGraphStatus.Partial, result.diagnostics.budgetLimit != null)
    }

    @Test fun squareRootInvalidDomainIsBrokenAndValidSideRemainsAvailable() {
        val result = sample("sqrt(x)", xRange = -2.0..2.0)
        assertTrue(result.segments.isNotEmpty())
        assertTrue(result.segments.flatMap { it.points }.all { it.x >= 0.0 && it.y >= 0.0 })
        assertTrue(result.diagnostics.discontinuityBreakCount > 0)
    }

    @Test fun rapidlyOscillatingFunctionIsBoundedAndMarkedPartial() {
        val result = sample(
            "sin(1/x)", xRange = -0.1..0.1, yRange = -1.2..1.2,
            budget = FunctionGraphBudget(maximumEvaluationsPerFunction = 300, maximumTotalEvaluations = 500)
        )
        assertTrue(result.status is FunctionGraphStatus.Partial || result.status is FunctionGraphStatus.BudgetExceeded)
        assertTrue(result.diagnostics.evaluationCount <= 300)
        assertTrue(result.segments.flatMap { it.points }.all { it.x.isFinite() && it.y.isFinite() })
    }

    @Test fun oneInvalidExpressionDoesNotAffectTheOtherFiveFunctions() {
        val inputs = FunctionSlotId.ordered.mapIndexed { index, id ->
            if (index == 2) FunctionGraphInput(id, compilationError = compileFailure("bad("))
            else FunctionGraphInput(id, compile("x+${index}"))
        }
        val results = calculator().calculateDirect(request(inputs))
        assertEquals(6, results.results.size)
        assertTrue(results.results[2].status is FunctionGraphStatus.Error)
        assertTrue(results.results.filterIndexed { index, _ -> index != 2 }.all { it.status == FunctionGraphStatus.Success })
    }

    @Test fun sixFunctionsShareTheGlobalBudgetWithoutExceedingIt() {
        val budget = FunctionGraphBudget(
            minimumInitialIntervals = 1, maximumInitialIntervals = 1,
            maximumEvaluationsPerFunction = 100, maximumTotalEvaluations = 8,
            maximumSubdivisionDepth = 8
        )
        val inputs = FunctionSlotId.ordered.map { FunctionGraphInput(it, compile("x^2")) }
        val result = calculator().calculateDirect(request(inputs, budget = budget))
        assertEquals(8, result.totalEvaluationCount)
        assertTrue(result.results.any { it.status is FunctionGraphStatus.BudgetExceeded || it.status is FunctionGraphStatus.Partial })
        assertTrue(result.results.take(1).any { it.segments.isNotEmpty() })
    }

    @Test fun sixRegularFunctionsAreCalculatedIndependently() {
        val inputs = FunctionSlotId.ordered.mapIndexed { index, id ->
            FunctionGraphInput(id, compile(listOf("x", "x^2", "sin(x)", "cos(x)", "exp(x)", "sqrt(x+6)")[index]))
        }
        val result = calculator().calculateDirect(request(inputs))
        assertEquals(6, result.results.size)
        assertTrue(result.results.all { it.status == FunctionGraphStatus.Success })
    }

    @Test fun sixCostlyFunctionsCanEachReachTheirIndividualBudgetUnderTheDefaultGlobalBudget() {
        val inputs = FunctionSlotId.ordered.map { FunctionGraphInput(it, compile("sin(1/x^2)")) }
        val result = calculator().calculateDirect(request(inputs, xRange = -0.1..0.1, yRange = -1.2..1.2))

        assertEquals(24_576, FunctionGraphBudget().maximumTotalEvaluations)
        assertTrue(result.totalEvaluationCount <= 24_576)
        assertEquals(6, result.results.size)
        assertTrue("per-function counts: ${result.results.map { it.diagnostics.evaluationCount }}", result.results.all { it.diagnostics.evaluationCount == 4096 })
        assertTrue(result.results.all { it.status is FunctionGraphStatus.Partial })
    }

    @Test fun oneCostlyFunctionGetsTheSameIndividualBudgetFirstOrLast() {
        fun evaluate(costlyFirst: Boolean): FunctionGraphResult {
            val orderedIds = FunctionSlotId.ordered
            val inputs = orderedIds.mapIndexed { index, id ->
                val isCostly = if (costlyFirst) index == 0 else index == orderedIds.lastIndex
                FunctionGraphInput(id, compile(if (isCostly) "sin(1/x^2)" else "x"))
            }
            val result = calculator().calculateDirect(request(inputs, xRange = -0.1..0.1, yRange = -1.2..1.2))
            assertTrue(result.totalEvaluationCount <= FunctionGraphBudget().maximumTotalEvaluations)
            val costlyIndex = if (costlyFirst) 0 else orderedIds.lastIndex
            return result.results[costlyIndex]
        }

        assertEquals(4096, evaluate(costlyFirst = true).diagnostics.evaluationCount)
        assertEquals(4096, evaluate(costlyFirst = false).diagnostics.evaluationCount)
    }

    @Test fun invalidAndEmptySlotsDoNotConsumeGlobalEvaluationBudget() {
        val costly = FunctionGraphInput(FunctionSlotId.F3, compile("sin(1/x^2)"))
        val inputs = listOf(
            FunctionGraphInput(FunctionSlotId.F1, compilationError = compileFailure("unknown(")),
            FunctionGraphInput(FunctionSlotId.F2),
            costly,
            FunctionGraphInput(FunctionSlotId.F4, compile("x")),
            FunctionGraphInput(FunctionSlotId.F5, compile("x")),
            FunctionGraphInput(FunctionSlotId.F6, compile("x"))
        )
        val limited = FunctionGraphBudget(maximumTotalEvaluations = 4096)
        val result = calculator().calculateDirect(request(inputs, budget = limited, xRange = -0.1..0.1, yRange = -1.2..1.2))

        assertEquals(0, result.results[0].diagnostics.evaluationCount)
        assertEquals(FunctionGraphStatus.Empty, result.results[1].status)
        assertEquals(4096, result.results[2].diagnostics.evaluationCount)
        assertTrue(result.totalEvaluationCount <= 4096)
    }

    @Test fun perFunctionEvaluationBudgetReturnsExplicitPartialData() {
        val budget = FunctionGraphBudget(
            minimumInitialIntervals = 2, maximumInitialIntervals = 2,
            maximumEvaluationsPerFunction = 5, maximumTotalEvaluations = 50,
            maximumSubdivisionDepth = 10
        )
        val result = sample("x^2", budget = budget)
        assertTrue(result.status is FunctionGraphStatus.Partial || result.status is FunctionGraphStatus.BudgetExceeded)
        assertTrue(result.diagnostics.evaluationCount <= 5)
        assertNotNull(result.diagnostics.budgetLimit)
    }

    @Test fun pointSegmentAndDepthBudgetsAreReportedExplicitly() {
        val pointLimited = sample("x^2", budget = FunctionGraphBudget(maximumPointsPerFunction = 12))
        assertTrue(pointLimited.status is FunctionGraphStatus.Partial || pointLimited.status is FunctionGraphStatus.BudgetExceeded)
        assertEquals(FunctionGraphBudgetLimit.POINTS, pointLimited.diagnostics.budgetLimit)

        val segmentLimited = sample(
            "1/x", xRange = -1.0..1.0, yRange = -10.0..10.0,
            budget = FunctionGraphBudget(maximumSegmentsPerFunction = 1)
        )
        assertTrue(segmentLimited.status is FunctionGraphStatus.Partial || segmentLimited.status is FunctionGraphStatus.BudgetExceeded)
        assertEquals(FunctionGraphBudgetLimit.SEGMENTS, segmentLimited.diagnostics.budgetLimit)
        assertEquals(1, segmentLimited.segments.size)

        val depthLimited = sample("x^2", budget = FunctionGraphBudget(maximumSubdivisionDepth = 0, chordTolerancePixels = 0.01))
        assertEquals(FunctionGraphBudgetLimit.SUBDIVISION_DEPTH, depthLimited.diagnostics.budgetLimit)
    }

    @Test fun aNonFiniteEvaluationIsReportedAsNoDefinedSampledPortion() {
        val result = sample("exp(1000)")
        assertEquals(FunctionGraphStatus.NoDefinedPortion, result.status)
        assertTrue(result.segments.isEmpty())
    }

    @Test fun samplingChecksCancellationDuringSubdivision() {
        var checks = 0
        val job = kotlinx.coroutines.Job()
        try {
            sampler.sample(request(listOf(FunctionGraphInput(FunctionSlotId.F1, compile("sin(1/x)")))),
                FunctionGraphInput(FunctionSlotId.F1, compile("sin(1/x)"))) {
                checks++
                if (checks > 6) job.cancel()
                job.ensureActive()
            }
            throw AssertionError("Expected cooperative cancellation")
        } catch (_: kotlinx.coroutines.CancellationException) {
            assertTrue(checks > 6)
        }
    }

    @Test fun requestCopiesMutableCollectionsAndValidatesCommonInputs() {
        val funcs = mutableListOf(FunctionGraphInput(FunctionSlotId.F1, compile("a*x")))
        val params = mutableMapOf('a' to 2.0)
        val request = request(funcs, params)
        funcs.clear()
        params['a'] = 9.0
        assertEquals(1, request.functions.size)
        assertEquals(2.0, request.parameters['a']!!, 0.0)
        assertEquals(FunctionAngleMode.RAD, request.angleMode)
    }

    @Test fun parametersAndAngleModeArePassedThroughOnEveryEvaluation() {
        val parameterInput = FunctionGraphInput(FunctionSlotId.F1, compile("a*x"))
        val parameterResult = sampler.sample(
            request(listOf(parameterInput), parameters = mapOf('a' to 3.0), xRange = 1.0..2.0), parameterInput
        )
        val nearOne = parameterResult.segments.single().points.minBy { kotlin.math.abs(it.x - 1.0) }
        assertEquals(3.0, nearOne.y, 1e-9)

        val trigInput = FunctionGraphInput(FunctionSlotId.F1, compile("sin(x)"))
        val degreeResult = sampler.sample(
            request(listOf(trigInput), xRange = 80.0..100.0, angleMode = FunctionAngleMode.DEG), trigInput
        )
        val nearNinety = degreeResult.segments.single().points.minBy { kotlin.math.abs(it.x - 90.0) }
        assertEquals(1.0, nearNinety.y, 1e-9)
    }

    private fun sample(
        source: String,
        xRange: ClosedFloatingPointRange<Double> = -5.0..5.0,
        yRange: ClosedFloatingPointRange<Double> = -10.0..10.0,
        width: Int = 320,
        height: Int = 240,
        budget: FunctionGraphBudget = FunctionGraphBudget(),
        angleMode: FunctionAngleMode = FunctionAngleMode.RAD
    ): FunctionGraphResult {
        val input = FunctionGraphInput(FunctionSlotId.F1, compile(source))
        return sampler.sample(request(listOf(input), xRange = xRange, yRange = yRange, width = width, height = height, budget = budget, angleMode = angleMode), input)
    }

    private fun request(
        inputs: List<FunctionGraphInput>,
        parameters: Map<Char, Double> = emptyMap(),
        xRange: ClosedFloatingPointRange<Double> = -5.0..5.0,
        yRange: ClosedFloatingPointRange<Double> = -10.0..10.0,
        width: Int = 320,
        height: Int = 240,
        budget: FunctionGraphBudget = FunctionGraphBudget(),
        angleMode: FunctionAngleMode = FunctionAngleMode.RAD
    ) = FunctionGraphRequest(
        generation = 1,
        xRange = AxisRange(xRange.start, xRange.endInclusive),
        yRange = AxisRange(yRange.start, yRange.endInclusive),
        plotWidthPixels = width,
        plotHeightPixels = height,
        functions = inputs,
        parameters = parameters,
        angleMode = angleMode,
        budget = budget
    )

    private fun compile(source: String) = (engine.compile(source) as ExpressionCompilation.Success).expression
    private fun compileFailure(source: String) = (engine.compile(source) as ExpressionCompilation.Failure).error

    private fun calculator() = FunctionGraphCalculator(kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined))
}
