package com.example.calcstudiopro.functions.graph.render

import com.example.calcstudiopro.domain.functions.ExpressionCompilation
import com.example.calcstudiopro.domain.functions.MathExpressionEngine
import com.example.calcstudiopro.functions.domain.FunctionColor
import com.example.calcstudiopro.functions.domain.FunctionColorPalette
import com.example.calcstudiopro.functions.domain.FunctionSlotId
import com.example.calcstudiopro.functions.domain.FunctionStudy
import com.example.calcstudiopro.functions.graph.FunctionGraphBatchResult
import com.example.calcstudiopro.functions.graph.FunctionGraphDiagnostics
import com.example.calcstudiopro.functions.graph.FunctionGraphPoint
import com.example.calcstudiopro.functions.graph.FunctionGraphResult
import com.example.calcstudiopro.functions.graph.FunctionGraphSegment
import com.example.calcstudiopro.functions.graph.FunctionGraphStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class FunctionGraphRenderModelTest {
    private val engine = MathExpressionEngine()
    private val diagnostics = FunctionGraphDiagnostics(4, 1, 1, 0)

    @Test fun joinsResultsByStableSlotIdAndKeepsF1ColorVisibility() {
        var study = FunctionStudy.newWorkingSession()
        study = accept(study, FunctionSlotId.F1, "x")
            .withColor(FunctionSlotId.F1, FunctionColor.SLATE)
            .withVisibility(FunctionSlotId.F1, false)
        study = accept(study, FunctionSlotId.F3, "x^2")

        val f1Segment = segment(10.0)
        val f3Segment = segment(30.0)
        val batch = FunctionGraphBatchResult(8, listOf(
            FunctionGraphResult(FunctionSlotId.F3, listOf(f3Segment), FunctionGraphStatus.Success, diagnostics),
            FunctionGraphResult(FunctionSlotId.F1, listOf(f1Segment), FunctionGraphStatus.Success, diagnostics)
        ), 8)

        val model = FunctionGraphRenderModel.from(study, batch)
        assertEquals(8L, model.dataGeneration)
        assertEquals(FunctionSlotId.ordered, model.curves.map { it.id })
        assertSame(f1Segment, model.curves[0].segments.single())
        assertEquals(FunctionColor.SLATE, model.curves[0].color)
        assertFalse(model.curves[0].isVisible)
        assertSame(f3Segment, model.curves[2].segments.single())
        assertEquals(FunctionColorPalette.defaultFor(FunctionSlotId.F3), model.curves[2].color)
    }

    @Test fun emptyAndNotYetCalculatedAreDistinctAndErrorsArePreserved() {
        var study = FunctionStudy.newWorkingSession()
        study = accept(study, FunctionSlotId.F2, "x+1")
        // A valid expression missing from the batch is pending, not mislabeled as empty.
        val noBatch = FunctionGraphRenderModel.from(study, null)
        assertEquals(FunctionGraphStatus.Empty, noBatch.curves[0].status)
        assertNull(noBatch.curves[1].status)
        assertNull(noBatch.curves[1].diagnostics)
        val failure = FunctionGraphStatus.Error(
            (engine.compile("sqrt(-1)") as ExpressionCompilation.Success).expression
                .evaluate(0.0, emptyMap()).let { (it as com.example.calcstudiopro.domain.functions.ExpressionEvaluation.Failure).error }
        )
        val batch = FunctionGraphBatchResult(2, listOf(
            FunctionGraphResult(FunctionSlotId.F2, emptyList(), failure, diagnostics)
        ), 1)
        val model = FunctionGraphRenderModel.from(study, batch)
        assertEquals(failure, model.curves[1].status)
        assertEquals(FunctionGraphStatus.Empty, model.curves[0].status)
    }

    @Test fun disjointMathSegmentsRemainDisjointAndColorsAreNeverReassigned() {
        var study = FunctionStudy.newWorkingSession()
        study = accept(study, FunctionSlotId.F1, "x")
            .withColor(FunctionSlotId.F1, FunctionColor.NAVY)
        val first = segment(-2.0)
        val second = segment(2.0)
        val batch = FunctionGraphBatchResult(3, listOf(
            FunctionGraphResult(FunctionSlotId.F1, listOf(first, second), FunctionGraphStatus.Success, diagnostics)
        ), 4)
        val model = FunctionGraphRenderModel.from(study, batch)
        assertEquals(listOf(first, second), model.curves.first().segments)
        assertEquals(FunctionColor.NAVY, model.curves.first().color)
        assertEquals(2, model.curves.first().segments.size)
    }

    private fun accept(study: FunctionStudy, id: FunctionSlotId, text: String): FunctionStudy {
        val edited = study.withDraft(id, text)
        return (edited.validateFunction(id, engine) as com.example.calcstudiopro.functions.domain.StudyValidationResult.Accepted).study
    }

    private fun segment(y: Double) = FunctionGraphSegment(listOf(
        FunctionGraphPoint(-1.0, y), FunctionGraphPoint(1.0, y)
    ))
}
