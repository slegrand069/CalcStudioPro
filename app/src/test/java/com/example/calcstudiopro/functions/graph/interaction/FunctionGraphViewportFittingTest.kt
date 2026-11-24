package com.example.calcstudiopro.functions.graph.interaction

import com.example.calcstudiopro.domain.functions.FunctionAngleMode
import com.example.calcstudiopro.functions.domain.FunctionColorPalette
import com.example.calcstudiopro.functions.domain.FunctionSlotId
import com.example.calcstudiopro.functions.graph.FunctionGraphBatchResult
import com.example.calcstudiopro.functions.graph.FunctionGraphDiagnostics
import com.example.calcstudiopro.functions.graph.FunctionGraphInput
import com.example.calcstudiopro.functions.graph.FunctionGraphPoint
import com.example.calcstudiopro.functions.graph.FunctionGraphRequest
import com.example.calcstudiopro.functions.graph.FunctionGraphResult
import com.example.calcstudiopro.functions.graph.FunctionGraphSegment
import com.example.calcstudiopro.functions.graph.FunctionGraphStatus
import com.example.calcstudiopro.functions.graph.render.FunctionCurveRenderModel
import com.example.calcstudiopro.functions.graph.render.FunctionGraphRenderModel
import com.example.calcstudiopro.statistics.graph.model.AxisRange
import com.example.calcstudiopro.statistics.graph.model.GraphViewport
import com.example.calcstudiopro.statistics.graph.model.PlotRect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FunctionGraphViewportFittingTest {
    @Test fun fitPreservesXClipsToDisplayedRangeAndAddsEightPercentMargin() {
        val viewport = viewport(x = AxisRange(-1.0, 1.0))
        val fixture = fixture(listOf(segment(-2.0, -2.0, 2.0, 2.0)))
        val result = FunctionGraphViewportFitter.fit(viewport, fixture.accepted, fixture.model, 4, 300)
        assertTrue(result is FunctionGraphFitResult.Applied)
        val fitted = (result as FunctionGraphFitResult.Applied).viewport
        assertEquals(viewport.x, fitted.x)
        assertTrue(fitted.y.minimum < -1.0)
        assertTrue(fitted.y.maximum > 1.0)
        assertEquals(-fitted.y.minimum, fitted.y.maximum, 1e-8)
        assertEquals(1.96 * 1.16, fitted.y.maximum - fitted.y.minimum, 1e-7)
    }

    @Test fun zeroAndNonzeroConstantsReceiveNondegenerateRanges() {
        val zero = fixture(listOf(segment(-2.0, 0.0, 2.0, 0.0)))
        val zeroResult = FunctionGraphViewportFitter.fit(viewport(), zero.accepted, zero.model, 4, 300) as FunctionGraphFitResult.Applied
        assertEquals(AxisRange(-1.0, 1.0), zeroResult.viewport.y)

        val constant = fixture(listOf(segment(-2.0, 5.0, 2.0, 5.0)))
        val result = FunctionGraphViewportFitter.fit(viewport(), constant.accepted, constant.model, 4, 300) as FunctionGraphFitResult.Applied
        assertTrue(result.viewport.y.minimum < 5.0 && result.viewport.y.maximum > 5.0)
        assertEquals(5.0, result.viewport.y.minimum / 2 + result.viewport.y.maximum / 2, 1e-12)
    }

    @Test fun hiddenCurvesDoNotContributeAndDisjointSegmentsAreNotJoined() {
        val visibleSegments = listOf(
            FunctionGraphSegment(listOf(FunctionGraphPoint(-2.0, -2.0), FunctionGraphPoint(-0.5, -2.0))),
            FunctionGraphSegment(listOf(FunctionGraphPoint(0.5, 2.0), FunctionGraphPoint(2.0, 2.0)))
        )
        val fixture = fixture(visibleSegments, hidden = listOf(segment(-2.0, -1e6, 2.0, 1e6)))
        val result = FunctionGraphViewportFitter.fit(viewport(), fixture.accepted, fixture.model, 4, 300) as FunctionGraphFitResult.Applied
        assertEquals(setOf(FunctionSlotId.F1), result.includedSlots)
        assertTrue(result.viewport.y.minimum > -10.0)
        assertTrue(result.viewport.y.maximum < 10.0)
        assertFalse(result.usedPartialData)
    }

    @Test fun partialStatusIsReportedAndIncompatibleOrUncoveredBatchIsRejected() {
        val partial = fixture(listOf(segment(-2.0, -1.0, 2.0, 1.0)), status = FunctionGraphStatus.Partial(com.example.calcstudiopro.functions.graph.FunctionGraphBudgetLimit.FUNCTION_EVALUATIONS))
        val applied = FunctionGraphViewportFitter.fit(viewport(), partial.accepted, partial.model, 4, 300) as FunctionGraphFitResult.Applied
        assertTrue(applied.usedPartialData)
        assertTrue(FunctionGraphViewportFitter.fit(viewport(), partial.accepted, partial.model, 5, 300) is FunctionGraphFitResult.Rejected)
        assertTrue(FunctionGraphViewportFitter.fit(
            viewport(x = AxisRange(-3.0, 3.0)), partial.accepted, partial.model, 4, 300
        ) is FunctionGraphFitResult.Rejected)
    }

    @Test fun eachVisibleSlotContributesItsOwnEnvelopeAndAdaptivePointDensityDoesNotChangeTheFit() {
        val sparse = fixture(listOf(segment(-2.0, -2.0, 2.0, 2.0)), extraVisible = mapOf(
            FunctionSlotId.F3 to listOf(segment(-2.0, 50.0, 2.0, 50.0))
        ))
        val sparseFit = FunctionGraphViewportFitter.fit(viewport(), sparse.accepted, sparse.model, 4, 300) as FunctionGraphFitResult.Applied
        assertEquals(setOf(FunctionSlotId.F1, FunctionSlotId.F3), sparseFit.includedSlots)
        assertTrue(sparseFit.viewport.y.minimum < -1.0 && sparseFit.viewport.y.maximum > 50.0)

        val densePoints = (0..120).map { index ->
            val fraction = index / 120.0
            val x = -2.0 + 4.0 * fraction * fraction * fraction
            FunctionGraphPoint(x, x)
        }.distinctBy { it.x }
        val dense = fixture(listOf(FunctionGraphSegment(densePoints)))
        val denseFit = FunctionGraphViewportFitter.fit(viewport(), dense.accepted, dense.model, 4, 300) as FunctionGraphFitResult.Applied
        val sparseLine = fixture(listOf(segment(-2.0, -2.0, 2.0, 2.0)))
        val sparseLineFit = FunctionGraphViewportFitter.fit(viewport(), sparseLine.accepted, sparseLine.model, 4, 300) as FunctionGraphFitResult.Applied
        assertEquals(sparseLineFit.viewport.y.minimum, denseFit.viewport.y.minimum, 1e-8)
        assertEquals(sparseLineFit.viewport.y.maximum, denseFit.viewport.y.maximum, 1e-8)
    }

    @Test fun noDefinedPortionAndExtremeYRangeAreReportedWithoutChangingViewport() {
        val noData = fixture(emptyList(), status = FunctionGraphStatus.NoDefinedPortion)
        assertEquals(
            FunctionGraphFitResult.Rejected(FunctionGraphFitResult.Reason.NO_DEFINED_PORTION),
            FunctionGraphViewportFitter.fit(viewport(), noData.accepted, noData.model, 4, 300)
        )
        val extreme = fixture(listOf(segment(-2.0, -1e308, 2.0, 1e308)))
        assertEquals(
            FunctionGraphFitResult.Rejected(FunctionGraphFitResult.Reason.NUMERIC_LIMIT),
            FunctionGraphViewportFitter.fit(viewport(), extreme.accepted, extreme.model, 4, 300)
        )
    }

    @Test fun orthonormalizationUsesActualPlotAndOnlyExpandsOneAxis() {
        val plot = PlotRect(10.0, 20.0, 200.0, 100.0)
        val original = GraphViewport(AxisRange(-5.0, 5.0), AxisRange(-5.0, 5.0))
        val result = FunctionGraphViewportOrthonormalizer.orthonormalize(original, plot) as FunctionGraphOrthonormalizeResult.Applied
        assertEquals(original.y, result.viewport.y)
        assertEquals(-10.0, result.viewport.x.minimum, 1e-10)
        assertEquals(10.0, result.viewport.x.maximum, 1e-10)
        assertEquals(
            200.0 / (result.viewport.x.maximum - result.viewport.x.minimum),
            100.0 / (result.viewport.y.maximum - result.viewport.y.minimum), 1e-10
        )
        assertEquals(0.0, result.viewport.x.minimum / 2 + result.viewport.x.maximum / 2, 0.0)
    }

    @Test fun orthonormalizationIsIdempotentAndRejectsImpossibleExpansion() {
        val plot = PlotRect(0.0, 0.0, 300.0, 100.0)
        val once = (FunctionGraphViewportOrthonormalizer.orthonormalize(viewport(), plot) as FunctionGraphOrthonormalizeResult.Applied).viewport
        val twice = FunctionGraphViewportOrthonormalizer.orthonormalize(once, plot) as FunctionGraphOrthonormalizeResult.Applied
        assertTrue(twice.alreadyOrthonormal)
        assertEquals(once, twice.viewport)

        val extreme = GraphViewport(AxisRange(0.0, 1e12), AxisRange(0.0, 1e-9))
        assertTrue(FunctionGraphViewportOrthonormalizer.orthonormalize(extreme, PlotRect(0.0, 0.0, 1.0, 3_000.0)) is FunctionGraphOrthonormalizeResult.Rejected)
    }

    private data class Fixture(val accepted: FunctionGraphAcceptedResult, val model: FunctionGraphRenderModel)

    private fun fixture(
        segments: List<FunctionGraphSegment>,
        hidden: List<FunctionGraphSegment> = emptyList(),
        status: FunctionGraphStatus = FunctionGraphStatus.Success,
        extraVisible: Map<FunctionSlotId, List<FunctionGraphSegment>> = emptyMap()
    ): Fixture {
        val viewport = viewport()
        val request = FunctionGraphRequest(0, AxisRange(-2.0, 2.0), AxisRange(-100.0, 100.0), 400, 300,
            listOf(FunctionGraphInput(FunctionSlotId.F1)), angleMode = FunctionAngleMode.RAD)
        val diagnostics = FunctionGraphDiagnostics(10, 0, 0, 0)
        val resultSegments = mapOf(FunctionSlotId.F1 to segments, FunctionSlotId.F2 to hidden) + extraVisible
        val results = resultSegments.map { (id, resultPoints) ->
            FunctionGraphResult(id, resultPoints, if (id == FunctionSlotId.F1) status else FunctionGraphStatus.Success, diagnostics)
        }
        val batch = FunctionGraphBatchResult(0, results, 10)
        val model = FunctionGraphRenderModel(0, FunctionSlotId.ordered.map { id ->
            val curveSegments = resultSegments[id].orEmpty()
            FunctionCurveRenderModel(
                id, FunctionColorPalette.defaultFor(id), isVisible = id == FunctionSlotId.F1 || id in extraVisible,
                segments = curveSegments, status = if (id == FunctionSlotId.F1) status else if (id in extraVisible) FunctionGraphStatus.Success else FunctionGraphStatus.Empty,
                diagnostics = diagnostics
            )
        })
        return Fixture(FunctionGraphAcceptedResult(4, request, batch), model)
    }

    private fun segment(x0: Double, y0: Double, x1: Double, y1: Double) =
        FunctionGraphSegment(listOf(FunctionGraphPoint(x0, y0), FunctionGraphPoint(x1, y1)))

    private fun viewport(x: AxisRange = AxisRange(-2.0, 2.0)) = GraphViewport(x, AxisRange(-10.0, 10.0))
}
