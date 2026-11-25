package com.example.calcstudiopro.functions.graph

import com.example.calcstudiopro.domain.functions.FunctionAngleMode
import com.example.calcstudiopro.domain.functions.MathExpressionEngine
import com.example.calcstudiopro.functions.domain.FunctionColor
import com.example.calcstudiopro.functions.domain.FunctionSlotId
import com.example.calcstudiopro.functions.domain.FunctionStudy
import com.example.calcstudiopro.functions.domain.StudyParameterUpdate
import com.example.calcstudiopro.functions.domain.StudyValidationResult
import com.example.calcstudiopro.functions.graph.interaction.FunctionGraphAcceptedResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class FunctionGraphStudyAdapterTest {
    private val engine = MathExpressionEngine()

    @Test fun adapterKeepsStableSixSlotsReusesAstAndIgnoresDrafts() {
        val working = FunctionStudy.newWorkingSession().withDraft(FunctionSlotId.F1, "x+a")
        val study = (working.validateFunction(FunctionSlotId.F1, engine) as StudyValidationResult.Accepted).study
        val content = FunctionGraphStudyAdapter.content(study, revision = 7)

        assertEquals(FunctionSlotId.ordered, content.snapshot.functions.map { it.id })
        assertSame(study.slot(FunctionSlotId.F1).compiledExpression, content.snapshot.functions.first().expression)
        assertEquals(null, content.snapshot.functions[1].expression)
        assertEquals(emptyMap<Char, Double>(), content.snapshot.parameters)

        val editedDraft = study.withDraft(FunctionSlotId.F1, "sin(")
            .withColor(FunctionSlotId.F1, FunctionColor.SUN_YELLOW)
            .withVisibility(FunctionSlotId.F1, false)
        assertEquals(FunctionGraphStudyAdapter.mathKey(study), FunctionGraphStudyAdapter.mathKey(editedDraft))
        val presentation = FunctionGraphStudyAdapter.renderModel(editedDraft, 7, null)
        assertEquals(FunctionColor.SUN_YELLOW, presentation.curves.first().color)
        assertFalse(presentation.curves.first().isVisible)
        assertEquals("x+a", editedDraft.slot(FunctionSlotId.F1).validatedExpression)
        assertEquals("sin(", editedDraft.slot(FunctionSlotId.F1).draft)
    }

    @Test fun trackerAdvancesOnlyForValidatedExpressionsParametersAndAngleMode() {
        val base = FunctionStudy.newWorkingSession().withDraft(FunctionSlotId.F1, "sin(x)+a")
        val first = (base.validateFunction(FunctionSlotId.F1, engine) as StudyValidationResult.Accepted).study
        val tracker = FunctionGraphStudyContentTracker(first)
        val initial = tracker.update(first)

        val presentationOnly = first.withVisibility(FunctionSlotId.F1, false)
            .withColor(FunctionSlotId.F1, FunctionColor.RASPBERRY)
            .withDraft(FunctionSlotId.F1, "not valid (")
        val unchanged = tracker.update(presentationOnly)
        assertEquals(initial.snapshot.revision, unchanged.snapshot.revision)
        assertSame(initial.snapshot.functions.first().expression, unchanged.snapshot.functions.first().expression)

        val withParameter = (first.withParameter('a', 5.0) as StudyParameterUpdate.Updated).study
        val changedParameter = tracker.update(withParameter)
        assertEquals(initial.snapshot.revision + 1, changedParameter.snapshot.revision)
        assertEquals(5.0, changedParameter.snapshot.parameters['a']!!, 0.0)
        assertSame(initial.snapshot.functions.first().expression, changedParameter.snapshot.functions.first().expression)

        val degrees = withParameter.withAngleMode(FunctionAngleMode.DEG)
        val changedMode = tracker.update(degrees)
        assertEquals(changedParameter.snapshot.revision + 1, changedMode.snapshot.revision)
        assertEquals(FunctionAngleMode.DEG, changedMode.snapshot.angleMode)
        assertSame(initial.snapshot.functions.first().expression, changedMode.snapshot.functions.first().expression)
        assertTrue(changedMode.snapshot.functions.all { it.id in FunctionSlotId.ordered })
    }

    @Test fun adapterProvidesValidatedPerSlotParameterDependenciesAndVisibility() {
        var study = FunctionStudy.newWorkingSession()
        listOf("ax^2+bx+c", "log(abx^2)", "(a+b)cos(ax)sin(bx)").forEachIndexed { index, expression ->
            val id = FunctionSlotId.ordered[index]
            study = study.withDraft(id, expression)
            study = (study.validateFunction(id, engine) as StudyValidationResult.Accepted).study
        }
        study = study.withVisibility(FunctionSlotId.F1, false).withVisibility(FunctionSlotId.F2, false)

        val inputs = FunctionGraphStudyAdapter.content(study, 10).snapshot.functions
        assertEquals(setOf('a', 'b', 'c'), inputs[0].parameterDependencies)
        assertEquals(setOf('a', 'b'), inputs[1].parameterDependencies)
        assertEquals(setOf('a', 'b'), inputs[2].parameterDependencies)
        assertFalse(inputs[0].isVisible)
        assertFalse(inputs[1].isVisible)
        assertTrue(inputs[2].isVisible)
        assertEquals(emptySet<Char>(), inputs[3].parameterDependencies)
        assertTrue(inputs[3].isVisible)
    }

    @Test fun renderingRejectsCachedSegmentsForARevealedSlotWithStaleParameters() {
        var study = FunctionStudy.newWorkingSession().withDraft(FunctionSlotId.F1, "c*x")
        study = (study.validateFunction(FunctionSlotId.F1, engine) as StudyValidationResult.Accepted).study
        study = study.withVisibility(FunctionSlotId.F1, false)
        val oldInput = FunctionGraphStudyAdapter.content(study, 4).snapshot.functions.first()
        val oldRequest = com.example.calcstudiopro.functions.graph.FunctionGraphRequest(
            generation = 2,
            xRange = com.example.calcstudiopro.statistics.graph.model.AxisRange(-1.0, 1.0),
            yRange = com.example.calcstudiopro.statistics.graph.model.AxisRange(-1.0, 1.0),
            plotWidthPixels = 100,
            plotHeightPixels = 100,
            functions = listOf(oldInput),
            parameters = mapOf('c' to 3.0)
        )
        val oldBatch = FunctionGraphBatchResult(
            2,
            listOf(com.example.calcstudiopro.functions.graph.FunctionGraphResult(
                FunctionSlotId.F1,
                listOf(com.example.calcstudiopro.functions.graph.FunctionGraphSegment(listOf(
                    com.example.calcstudiopro.functions.graph.FunctionGraphPoint(-1.0, -3.0),
                    com.example.calcstudiopro.functions.graph.FunctionGraphPoint(1.0, 3.0)
                ))),
                com.example.calcstudiopro.functions.graph.FunctionGraphStatus.Success,
                com.example.calcstudiopro.functions.graph.FunctionGraphDiagnostics(2, 0, 0, 0)
            )), 2
        )
        val accepted = FunctionGraphAcceptedResult(4, oldRequest, oldBatch)
        val changed = (study.withParameter('c', 9.0) as StudyParameterUpdate.Updated).study
            .withVisibility(FunctionSlotId.F1, true)
        // Rebasing a batch after a change irrelevant to visible functions must not expose stale hidden paths later.
        val model = FunctionGraphStudyAdapter.renderModel(changed, 4, accepted.copy(contentRevision = 4))
        assertTrue(model.curves.first().segments.isEmpty())
    }
}
