package com.example.calcstudiopro.functions.graph

import com.example.calcstudiopro.domain.functions.ExpressionCompilation
import com.example.calcstudiopro.domain.functions.FunctionAngleMode
import com.example.calcstudiopro.domain.functions.MathExpressionEngine
import com.example.calcstudiopro.functions.domain.FunctionSlotId
import com.example.calcstudiopro.statistics.graph.model.AxisRange
import com.example.calcstudiopro.statistics.graph.model.GraphViewport
import com.example.calcstudiopro.statistics.graph.model.PlotRect
import com.example.calcstudiopro.functions.graph.interaction.FunctionGraphOrthonormalizeResult
import com.example.calcstudiopro.functions.graph.interaction.FunctionGraphViewportOrthonormalizer
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.system.measureTimeMillis

class FunctionGraphCoverageTest {
    private val engine = MathExpressionEngine()
    private val sampler = FunctionGraphSampler()

    @Test fun cotangentUserViewportsReportExplorationAndRepresentedCoverage() {
        val cases = listOf(
            SampleCase("reset", -10.0, 10.0, -2830.0, 2830.0, 360, 620),
            SampleCase("orthonormal", -1415.0, 1415.0, -2830.0, 2830.0, 360, 620),
            SampleCase("left120", -5104.0, -4984.0, -2100.0, 2400.0, 360, 620),
            SampleCase("wideLeft", -6180.0, -3910.0, -2100.0, 2400.0, 360, 620),
            SampleCase("farLeft", -12940.0, -10480.0, -1300.0, 1300.0, 360, 620)
        )
        for (case in cases) {
            val expression = compile("n(cos(kx)/sin(kx))")
            lateinit var result: FunctionGraphResult
            val elapsed = measureTimeMillis {
                result = sampler.sample(
                    FunctionGraphRequest(
                        generation = 1,
                        xRange = AxisRange(case.xMin, case.xMax),
                        yRange = AxisRange(case.yMin, case.yMax),
                        plotWidthPixels = case.width,
                        plotHeightPixels = case.height,
                        functions = listOf(FunctionGraphInput(FunctionSlotId.F1, expression)),
                        parameters = mapOf('n' to 50.0, 'k' to 0.1),
                        angleMode = FunctionAngleMode.RAD
                    ),
                    FunctionGraphInput(FunctionSlotId.F1, expression)
                )
            }
            val points = result.segments.flatMap { it.points }
            val represented = if (points.isEmpty()) 0.0 else
                ((result.segments.sumOf { it.points.last().x - it.points.first().x }) / (case.xMax - case.xMin)).coerceIn(0.0, 1.0)
            val representedBounds = if (points.isEmpty()) "none" else "${points.minOf { it.x }}..${points.maxOf { it.x }}"
            println("COVERAGE ${case.name}: evaluations=${result.diagnostics.evaluationCount}, initialIntervals=${result.diagnostics.initialIntervalCount}, exploredIntervals=${result.diagnostics.intervalsExplored}, subdivisions=${result.diagnostics.subdivisionCount}, domainOrDepthBreaks=${result.diagnostics.discontinuityBreakCount}, unresolvedIntervals=${result.diagnostics.unresolvedIntervalCount}, segments=${result.segments.size}, status=${result.status}, explored=${result.diagnostics.sampledMinimumX}..${result.diagnostics.sampledMaximumX}, represented=${"%.4f".format(represented)}, visibleX=${result.diagnostics.visibleXFraction}, diagnosticsRepresented=${result.diagnostics.representedXFraction}, bounds=$representedBounds, elapsedMs=$elapsed")
            assertTrue(result.diagnostics.evaluationCount <= 4096)
            assertTrue(points.all { it.x.isFinite() && it.y.isFinite() })
            assertTrue(result.diagnostics.sampledMinimumX == case.xMin)
            assertTrue(result.diagnostics.sampledMaximumX == case.xMax)
            assertTrue(result.diagnostics.representedXFraction >= 0.80)
            val period = Math.PI / 0.1
            val firstPole = kotlin.math.ceil(case.xMin / period).toInt()
            val lastPole = kotlin.math.floor(case.xMax / period).toInt()
            for (multiple in firstPole..lastPole) {
                val pole = multiple * period
                assertTrue("${case.name} connected through pole x=$pole", result.segments.none { segment ->
                    segment.points.first().x < pole && segment.points.last().x > pole
                })
            }
        }
    }

    @Test fun representativeFunctionFamiliesStayFiniteAndWithinPerFunctionBudgets() {
        val sources = listOf(
            "x", "x^2-4", "x^3-3x", "sin(x)", "sin(30x)", "1/x", "tan(x)",
            "cos(x)/sin(x)", "50*cos(0.1*x)/sin(0.1*x)", "50*sin(0.1*x)/cos(0.1*x)",
            "sin(1/x)", "ln(x)", "sqrt(x)", "exp(x)", "1000000*x", "0", "1000000000000"
        )
        for (source in sources) {
            val expression = compile(source)
            val input = FunctionGraphInput(FunctionSlotId.F1, expression)
            val result = sampler.sample(
                FunctionGraphRequest(
                    generation = 1,
                    xRange = AxisRange(-10.0, 10.0),
                    yRange = AxisRange(-10.0, 10.0),
                    plotWidthPixels = 360,
                    plotHeightPixels = 620,
                    functions = listOf(input),
                    angleMode = FunctionAngleMode.RAD
                ), input
            )
            assertTrue("$source exceeded its evaluation budget", result.diagnostics.evaluationCount <= 4096)
            assertTrue("$source emitted a non-finite point", result.segments.flatMap { it.points }.all { it.x.isFinite() && it.y.isFinite() })
            assertTrue("$source did not sample the full requested X range", result.diagnostics.sampledMinimumX == -10.0 && result.diagnostics.sampledMaximumX == 10.0)
            assertTrue("$source has an unbounded status", result.status is FunctionGraphStatus.Success || result.status is FunctionGraphStatus.NoDefinedPortion || result.status is FunctionGraphStatus.Partial || result.status is FunctionGraphStatus.BudgetExceeded)
        }
    }

    @Test fun sixDifficultVisibleFunctionsRetainGlobalCoverageWithoutExceedingLimits() {
        val sources = listOf("x^3-3x", "sin(30x)", "1/x", "tan(x)", "50*cos(0.1*x)/sin(0.1*x)", "sin(1/x)")
        val inputs = sources.mapIndexed { index, source -> FunctionGraphInput(FunctionSlotId.ordered[index], compile(source)) }
        val request = FunctionGraphRequest(
            generation = 7,
            xRange = AxisRange(-100.0, 100.0),
            yRange = AxisRange(-10.0, 10.0),
            plotWidthPixels = 360,
            plotHeightPixels = 620,
            functions = inputs
        )
        val started = System.nanoTime()
        val batch = FunctionGraphCalculator(kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined)).calculateDirect(request)
        val elapsedMs = (System.nanoTime() - started) / 1_000_000.0
        println("SIX-CURVES total=${batch.totalEvaluationCount} perFunction=${batch.results.map { it.diagnostics.evaluationCount }} segments=${batch.results.map { it.segments.size }} status=${batch.results.map { it.status }} elapsedMs=$elapsedMs")
        assertTrue(batch.totalEvaluationCount <= 24_576)
        assertEquals(6, batch.results.size)
        batch.results.forEach { result ->
            assertTrue(result.diagnostics.evaluationCount <= 4096)
            assertEquals(-100.0, result.diagnostics.sampledMinimumX!!, 0.0)
            assertEquals(100.0, result.diagnostics.sampledMaximumX!!, 0.0)
            assertTrue(result.segments.flatMap { it.points }.all { it.x.isFinite() && it.y.isFinite() })
        }
    }

    @Test fun reportedViewportRangesOrthonormalizeWithinTheRealisticPlotDimensions() {
        val cases = listOf(
            GraphViewport(AxisRange(-10.0, 10.0), AxisRange(-2830.0, 2830.0)),
            GraphViewport(AxisRange(-1415.0, 1415.0), AxisRange(-2830.0, 2830.0)),
            GraphViewport(AxisRange(-5104.0, -4984.0), AxisRange(-2100.0, 2400.0)),
            GraphViewport(AxisRange(-6180.0, -3910.0), AxisRange(-2100.0, 2400.0)),
            GraphViewport(AxisRange(-12940.0, -10480.0), AxisRange(-1300.0, 1300.0))
        )
        val plotSizes = listOf(PlotRect(168.0, 36.0, 1044.0, 1782.0), PlotRect(168.0, 36.0, 2244.0, 876.0))
        cases.forEachIndexed { index, viewport ->
            val plot = plotSizes[index % plotSizes.size]
            val result = FunctionGraphViewportOrthonormalizer.orthonormalize(viewport, plot)
            val xScale = plot.width / (viewport.x.maximum - viewport.x.minimum)
            val yScale = plot.height / (viewport.y.maximum - viewport.y.minimum)
            println("ORTHO case=$index x=${viewport.x.minimum}..${viewport.x.maximum} y=${viewport.y.minimum}..${viewport.y.maximum} plot=${plot.width}x${plot.height} scales=$xScale/$yScale result=$result")
            if (result is FunctionGraphOrthonormalizeResult.Applied) {
                val next = result.viewport
                assertTrue(next.x.minimum <= viewport.x.minimum && next.x.maximum >= viewport.x.maximum)
                assertTrue(next.y.minimum <= viewport.y.minimum && next.y.maximum >= viewport.y.maximum)
                val nextXScale = plot.width / (next.x.maximum - next.x.minimum)
                val nextYScale = plot.height / (next.y.maximum - next.y.minimum)
                assertTrue(kotlin.math.abs(nextXScale - nextYScale) <= maxOf(nextXScale, nextYScale) * 1e-10)
            }
        }
    }

    private fun compile(source: String) = (engine.compile(source) as ExpressionCompilation.Success).expression

    private data class SampleCase(
        val name: String, val xMin: Double, val xMax: Double,
        val yMin: Double, val yMax: Double, val width: Int, val height: Int
    )
}
