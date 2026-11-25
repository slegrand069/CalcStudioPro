package com.example.calcstudiopro.functions.graph.interaction

import com.example.calcstudiopro.functions.domain.FunctionColor
import com.example.calcstudiopro.functions.domain.FunctionSlotId
import com.example.calcstudiopro.functions.graph.FunctionGraphPoint
import com.example.calcstudiopro.functions.graph.FunctionGraphSegment
import com.example.calcstudiopro.functions.graph.FunctionGraphStatus
import com.example.calcstudiopro.functions.graph.render.FunctionCurveRenderModel
import com.example.calcstudiopro.functions.graph.render.FunctionGraphRenderModel
import com.example.calcstudiopro.statistics.graph.model.AxisRange
import com.example.calcstudiopro.statistics.graph.model.GraphViewport
import com.example.calcstudiopro.statistics.graph.model.PlotRect
import com.example.calcstudiopro.statistics.graph.model.ScreenPoint
import org.junit.Assert.*
import org.junit.Test

class FunctionGraphSelectionTest {
    private val viewport = GraphViewport(AxisRange(-1.0, 1.0), AxisRange(-1.0, 1.0))
    private val plot = PlotRect(0.0, 0.0, 100.0, 100.0)

    @Test fun hitTestingUsesVisibleReprojectedLinesAndStableSlotOrder() {
        val model = model(
            curve(FunctionSlotId.F1, visible = false, listOf(listOf(FunctionGraphPoint(-1.0, 0.0), FunctionGraphPoint(1.0, 0.0)))),
            curve(FunctionSlotId.F2, visible = true, listOf(listOf(FunctionGraphPoint(-1.0, 0.0), FunctionGraphPoint(1.0, 0.0)))),
            curve(FunctionSlotId.F3, visible = true, listOf(listOf(FunctionGraphPoint(-1.0, 0.0), FunctionGraphPoint(1.0, 0.0))))
        )
        val hits = FunctionGraphCurveHitTester.candidatesNear(model, viewport, viewport, plot, plot,
            ScreenPoint(50.0, 55.0), radiusPixels = 10.0)
        assertEquals(listOf(FunctionSlotId.F2, FunctionSlotId.F3), hits.map { it.slotId })
        assertEquals(0, hits.first().segmentIndex)
        assertEquals(0, hits.first().edgeIndex)
        assertEquals(0.5, hits.first().interpolationParameter, 1e-9)
        assertEquals(0.0, hits.first().point.y, 1e-9)
        assertEquals(48.0, FunctionGraphCurveHitTester.radiusPixels(24.0, 2.0), 0.0)
    }

    @Test fun hitTestingDoesNotBridgeSeparateSegments() {
        val model = model(curve(FunctionSlotId.F1, true, listOf(
            listOf(FunctionGraphPoint(-1.0, 0.0), FunctionGraphPoint(-0.2, 0.0)),
            listOf(FunctionGraphPoint(0.2, 0.0), FunctionGraphPoint(1.0, 0.0))
        )))
        assertTrue(FunctionGraphCurveHitTester.candidatesNear(model, viewport, viewport, plot, plot,
            ScreenPoint(50.0, 50.0), radiusPixels = 5.0).isEmpty())
    }

    @Test fun hitTestingHandlesDenseCurvesAndRejectsClippedAwayRuns() {
        val points = (0..4096).map { index ->
            val x = -1.0 + 2.0 * index / 4096.0
            FunctionGraphPoint(x, 0.5 * kotlin.math.sin(x * 8.0))
        }
        val model = model(curve(FunctionSlotId.F1, true, listOf(points)))
        assertEquals(FunctionSlotId.F1, FunctionGraphCurveHitTester.candidatesNear(
            model, viewport, viewport, plot, plot, ScreenPoint(50.0, 50.0), 20.0
        ).first().slotId)
        val far = model(curve(FunctionSlotId.F1, true, listOf(listOf(
            FunctionGraphPoint(10.0, 10.0), FunctionGraphPoint(11.0, 11.0)
        ))))
        assertTrue(FunctionGraphCurveHitTester.candidatesNear(far, viewport, viewport, plot, plot,
            ScreenPoint(50.0, 50.0), 10.0).isEmpty())
    }

    @Test fun markerDragClampsAtTheSelectedBranchEndpoints() {
        val model = model(curve(FunctionSlotId.F1, true, listOf(
            listOf(FunctionGraphPoint(-1.0, -0.5), FunctionGraphPoint(0.0, 0.5)),
            listOf(FunctionGraphPoint(0.2, -0.5), FunctionGraphPoint(1.0, 0.5))
        )))
        val left = FunctionGraphMarkerDrag.hitOnSelectedSegment(model, FunctionSlotId.F1, 0,
            ScreenPoint(100.0, 50.0), viewport, plot)!!
        assertEquals(0.0, left.point.x, 0.0)
        assertEquals(0, left.segmentIndex)
        val right = FunctionGraphMarkerDrag.hitOnSelectedSegment(model, FunctionSlotId.F1, 0,
            ScreenPoint(0.0, 50.0), viewport, plot)!!
        assertEquals(-1.0, right.point.x, 0.0)
        assertEquals(0, right.segmentIndex)
    }

    @Test fun tapsCycleCandidatesAndDoubleTapCanRollbackImmediateSelection() {
        val controller = FunctionGraphSelectionController(48.0, cycleDurationMillis = 500)
        val touch = ScreenPoint(50.0, 50.0)
        val context = FunctionGraphSelectionContext(1, 4, 2)
        val first = hit(FunctionSlotId.F1, 0.5)
        val second = hit(FunctionSlotId.F2, 0.5)
        assertEquals(FunctionSlotId.F1, controller.select(listOf(first, second), touch, context, 1000)!!.slotId)
        controller.commitPendingTap()
        assertEquals(FunctionSlotId.F2, controller.select(listOf(first, second), touch, context, 1200)!!.slotId)
        assertEquals(FunctionSlotId.F1, controller.rollbackPendingTapForDoubleTap()!!.slotId)
        assertEquals(FunctionSlotId.F2, controller.select(listOf(first, second), touch, context, 1300)!!.slotId)
        assertEquals(FunctionSlotId.F1, controller.select(listOf(first), ScreenPoint(300.0, 50.0), context.copy(viewportGeneration = 3), 1400)!!.slotId)
        assertEquals(FunctionSlotId.F1, controller.state.value.marker!!.slotId)
    }

    @Test fun evaluationResultsAreAcceptedOnlyForCurrentRequestAndContent() {
        val controller = FunctionGraphSelectionController(48.0)
        val hit = hit(FunctionSlotId.F1, 0.25)
        val marker = controller.select(listOf(hit), ScreenPoint(50.0, 50.0), FunctionGraphSelectionContext(3, 1, 0), 1)!!
        controller.setEvaluationRequest(marker, 20)
        assertFalse(controller.applyEvaluation(19, FunctionSlotId.F1, 0.25, 3, FunctionGraphMarkerEvaluation.Confirmed(1.0)))
        assertFalse(controller.applyEvaluation(20, FunctionSlotId.F1, 0.25, 2, FunctionGraphMarkerEvaluation.Confirmed(1.0)))
        assertTrue(controller.applyEvaluation(20, FunctionSlotId.F1, 0.25, 3, FunctionGraphMarkerEvaluation.Confirmed(7.0)))
        assertEquals(7.0, controller.state.value.marker!!.displayedY!!, 0.0)
    }

    @Test fun clearingSelectionRemovesMarkerAndRejectsItsLateEvaluation() {
        val controller = FunctionGraphSelectionController(48.0)
        val marker = controller.select(
            listOf(hit(FunctionSlotId.F1, 0.25)), ScreenPoint(50.0, 50.0),
            FunctionGraphSelectionContext(3, 1, 0), 1
        )!!
        controller.setEvaluationRequest(marker, 21)

        controller.clear()

        assertNull(controller.state.value.marker)
        assertFalse(controller.applyEvaluation(21, FunctionSlotId.F1, 0.25, 3, FunctionGraphMarkerEvaluation.Confirmed(7.0)))
        assertNull(controller.state.value.marker)
    }

    @Test fun contentRevisionPreservesSelectedXUntilTheSlotIsRemoved() {
        val controller = FunctionGraphSelectionController(48.0)
        val marker = controller.select(listOf(hit(FunctionSlotId.F4, 0.375)), ScreenPoint(40.0, 40.0),
            FunctionGraphSelectionContext(1, 2, 0), 10)!!
        controller.setEvaluationRequest(marker, 12)
        val edited = controller.onContentChanged(newRevision = 2, selectedSlotStillValid = true)!!
        assertEquals(0.375, edited.x, 0.0)
        assertEquals(2L, edited.contentRevision)
        assertNull(edited.evaluationRequestId)
        assertEquals(FunctionGraphMarkerEvaluation.Provisional, edited.evaluation)
        assertNull(controller.onContentChanged(newRevision = 3, selectedSlotStillValid = false))
        assertNull(controller.state.value.marker)
    }

    private fun hit(id: FunctionSlotId, x: Double) = FunctionGraphCurveHit(id, 0.0, 0, 0, 0.5, FunctionGraphPoint(x, 0.0))

    private fun model(vararg actual: FunctionCurveRenderModel): FunctionGraphRenderModel {
        val byId = actual.associateBy { it.id }
        return FunctionGraphRenderModel(4, FunctionSlotId.ordered.map { byId[it] ?: curve(it, false, emptyList()) })
    }

    private fun curve(id: FunctionSlotId, visible: Boolean, groups: List<List<FunctionGraphPoint>>) =
        FunctionCurveRenderModel(id, FunctionColor.values()[id.number - 1], visible,
            groups.map(::FunctionGraphSegment), FunctionGraphStatus.Success, null)
}
