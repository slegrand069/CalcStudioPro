package com.example.calcstudiopro.functions.graph.interaction

import com.example.calcstudiopro.domain.functions.FunctionAngleMode
import com.example.calcstudiopro.domain.functions.ExpressionCompilation
import com.example.calcstudiopro.domain.functions.MathExpressionEngine
import com.example.calcstudiopro.functions.graph.FunctionGraphBatchResult
import com.example.calcstudiopro.functions.graph.FunctionGraphCalculationOutcome
import com.example.calcstudiopro.functions.graph.FunctionGraphInput
import com.example.calcstudiopro.functions.graph.FunctionGraphRequest
import com.example.calcstudiopro.functions.graph.FunctionGraphResult
import com.example.calcstudiopro.functions.graph.FunctionGraphStatus
import com.example.calcstudiopro.functions.graph.FunctionGraphDiagnostics
import com.example.calcstudiopro.functions.domain.FunctionSlotId
import com.example.calcstudiopro.statistics.graph.model.AxisRange
import com.example.calcstudiopro.statistics.graph.model.GraphViewport
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FunctionGraphCalculationCoordinatorTest {
    @Test fun viewportPublishesImmediatelyAndOnlyLatestPendingViewportIsSubmitted() {
        val fixture = Fixture()
        try {
            val first = fixture.requests.single()
            val firstDeferred = fixture.deferreds.single()
            fixture.coordinator.beginGesture()
            fixture.coordinator.updateDisplayedViewport(viewport(1.0))
            fixture.coordinator.updateDisplayedViewport(viewport(2.0))
            assertEquals(viewport(2.0), fixture.coordinator.state.value.displayedViewport)
            assertEquals(viewport(2.0), fixture.coordinator.state.value.pending?.viewport)
            assertEquals(1, fixture.requests.size)

            fixture.scheduler.advanceBy(250)
            assertEquals(1, fixture.requests.size) // active work is never duplicated
            firstDeferred.complete(completed(first))
            assertEquals(2, fixture.requests.size)
            assertEquals(viewport(2.0).x, fixture.requests.last().xRange)
            assertEquals(viewport(2.0).y, fixture.requests.last().yRange)
            assertTrue(fixture.coordinator.state.value.calculationActive)
        } finally { fixture.close() }
    }

    @Test fun intermediateSubmissionWaitsForTheConfigured250MillisecondInterval() {
        val fixture = Fixture()
        try {
            fixture.deferreds[0].complete(completed(fixture.requests[0]))
            fixture.coordinator.beginGesture()
            fixture.coordinator.updateDisplayedViewport(viewport(1.0))
            fixture.scheduler.advanceBy(249)
            assertEquals(1, fixture.requests.size)
            fixture.scheduler.advanceBy(1)
            assertEquals(2, fixture.requests.size)
            assertEquals(viewport(1.0).x, fixture.requests[1].xRange)
        } finally { fixture.close() }
    }

    @Test fun gestureEndCancelsAnObsoleteActiveRequestAndSubmitsFinalViewportImmediately() {
        val fixture = Fixture()
        try {
            fixture.coordinator.beginGesture()
            fixture.coordinator.updateDisplayedViewport(viewport(3.0))
            fixture.coordinator.endGesture()
            assertTrue(fixture.deferreds[0].isCancelled)
            assertEquals(2, fixture.requests.size)
            assertEquals(viewport(3.0).x, fixture.requests[1].xRange)
            assertFalse(fixture.coordinator.state.value.gestureActive)
        } finally { fixture.close() }
    }

    @Test fun compatibleIntermediateResultIsAcceptedWithoutRestoringItsOldViewport() {
        val fixture = Fixture()
        try {
            val request = fixture.requests.single()
            fixture.coordinator.beginGesture()
            fixture.coordinator.updateDisplayedViewport(viewport(4.0))
            fixture.deferreds.single().complete(completed(request))
            val state = fixture.coordinator.state.value
            assertEquals(viewport(4.0), state.displayedViewport)
            assertNotNull(state.available)
            assertEquals(request.xRange, state.available!!.request.xRange)
            assertEquals(1, fixture.requests.size)
        } finally { fixture.close() }
    }

    @Test fun resultAndRequestContextArePublishedAtomicallyAndDuplicatesAreSuppressed() {
        val fixture = Fixture()
        try {
            val request = fixture.requests.single()
            fixture.deferreds.single().complete(completed(request))
            val published = fixture.coordinator.state.value
            assertEquals(request.generation, published.requestedGeneration)
            assertEquals(published.requested!!.viewport.x, published.available!!.request.xRange)
            fixture.coordinator.updateDisplayedViewport(published.displayedViewport)
            assertEquals(1, fixture.requests.size)
        } finally { fixture.close() }
    }

    @Test fun contentRevisionInvalidatesOldResultButKeepsViewport() {
        val fixture = Fixture(contentWithParameterA())
        try {
            val initial = fixture.requests.single()
            fixture.deferreds.single().complete(completed(initial))
            fixture.coordinator.updateDisplayedViewport(viewport(2.0))
            fixture.deferreds[1].complete(completed(fixture.requests[1]))
            val content = FunctionGraphContentSnapshot(
                2, listOf(input('a')), mapOf('a' to 7.0), FunctionAngleMode.DEG
            )
            fixture.coordinator.updateContent(content)
            val state = fixture.coordinator.state.value
            assertEquals(viewport(2.0), state.displayedViewport)
            assertNull(state.available)
            assertEquals(3, fixture.requests.size)
            assertEquals(2, fixture.requests.last().generation)
            assertEquals(content.parameters, fixture.requests.last().parameters)
            assertEquals(FunctionAngleMode.DEG, fixture.requests.last().angleMode)
        } finally { fixture.close() }
    }

    @Test fun resizingRequestsNewPixelsWithoutChangingMathematicalBounds() {
        val fixture = Fixture()
        try {
            fixture.coordinator.updateDisplayedViewport(viewport(2.0))
            fixture.coordinator.updatePlotSize(700, 320)
            assertEquals(viewport(2.0), fixture.coordinator.state.value.displayedViewport)
            assertEquals(700, fixture.requests.last().plotWidthPixels)
            assertEquals(320, fixture.requests.last().plotHeightPixels)
        } finally { fixture.close() }
    }

    @Test fun delayedCancellationKeepsOneReplaceablePendingViewportAndAcceptsOnlyTheLatest() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val scheduler = FakeScheduler()
        val requests = mutableListOf<FunctionGraphRequest>()
        val gate = CompletableDeferred<Unit>()
        val latestDeferred = CompletableDeferred<FunctionGraphCalculationOutcome>()
        val coordinator = FunctionGraphCalculationCoordinator(
            scope = scope,
            initialContent = contentWithParameterA(),
            initialPlotWidthPixels = 400,
            initialPlotHeightPixels = 240,
            scheduler = scheduler,
            submitCalculation = { request ->
                requests += request
                if (requests.size == 1) scope.async(start = CoroutineStart.UNDISPATCHED) {
                    withContext(NonCancellable) { gate.await() }
                    completed(request)
                } else latestDeferred
            }
        )
        try {
            coordinator.beginGesture()
            repeat(20) { coordinator.updateDisplayedViewport(viewport(it.toDouble())) }
            val finalViewport = viewport(21.0)
            coordinator.updateDisplayedViewport(finalViewport)
            coordinator.endGesture()

            assertEquals("Cancellation must not submit parallel work while the old job is still unwinding.", 1, requests.size)
            assertEquals(finalViewport, coordinator.state.value.pending?.viewport)

            gate.complete(Unit)
            assertEquals(2, requests.size)
            assertEquals(finalViewport.x, requests.last().xRange)
            assertEquals(finalViewport.y, requests.last().yRange)

            latestDeferred.complete(completed(requests.last()))
            assertEquals(requests.last().generation, coordinator.state.value.available?.batch?.generation)
        } finally { coordinator.close(); scope.cancel() }
    }

    @Test fun contentRevisionChangeDuringDelayedCancellationCannotPublishTheOldBatch() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val scheduler = FakeScheduler()
        val requests = mutableListOf<FunctionGraphRequest>()
        val gate = CompletableDeferred<Unit>()
        val currentDeferred = CompletableDeferred<FunctionGraphCalculationOutcome>()
        val coordinator = FunctionGraphCalculationCoordinator(
            scope = scope,
            initialContent = contentWithParameterA(),
            initialPlotWidthPixels = 400,
            initialPlotHeightPixels = 240,
            scheduler = scheduler,
            submitCalculation = { request ->
                requests += request
                if (requests.size == 1) scope.async(start = CoroutineStart.UNDISPATCHED) {
                    withContext(NonCancellable) { gate.await() }
                    completed(request)
                } else currentDeferred
            }
        )
        try {
            val parameterInput = input('a')
            repeat(40) { index ->
                coordinator.updateContent(FunctionGraphContentSnapshot(index + 2L, listOf(parameterInput), mapOf('a' to index + 5.0)))
            }
            assertNull(coordinator.state.value.available)
            assertEquals(1, requests.size)

            gate.complete(Unit)
            assertEquals(2, requests.size)
            assertEquals("Rapid slider changes coalesce to the last selected value.", mapOf('a' to 44.0), requests.last().parameters)
            assertNull("The completion of the cancelled generation must not be published.", coordinator.state.value.available)

            currentDeferred.complete(completed(requests.last()))
            assertEquals(41L, coordinator.state.value.available?.contentRevision)
            assertEquals(requests.last().generation, coordinator.state.value.available?.batch?.generation)
        } finally { coordinator.close(); scope.cancel() }
    }

    @Test fun parameterUsedOnlyByHiddenFunctionsDoesNotInvalidateVisibleCalculation() {
        val visible = input('a', visible = true)
        val hiddenA = input('c', visible = false, id = FunctionSlotId.F1)
        val hiddenB = input('b', visible = false, id = FunctionSlotId.F2)
        val fixture = Fixture(
            FunctionGraphContentSnapshot(1, listOf(hiddenA, hiddenB, visible), mapOf('a' to 2.0, 'b' to 3.0, 'c' to 4.0))
        )
        try {
            val initial = fixture.requests.single()
            fixture.deferreds.single().complete(completed(initial))
            fixture.coordinator.updateContent(
                FunctionGraphContentSnapshot(2, listOf(hiddenA, hiddenB, visible), mapOf('a' to 2.0, 'b' to 3.0, 'c' to 9.0))
            )
            assertEquals("A parameter absent from visible expressions must not submit another sample.", 1, fixture.requests.size)
            assertEquals(2L, fixture.coordinator.state.value.available?.contentRevision)
            assertEquals("The accepted path retains the exact request context that produced it.", 4.0, fixture.coordinator.state.value.available?.request?.parameters?.get('c')!!, 0.0)
        } finally { fixture.close() }
    }

    @Test fun parameterSharedByVisibleAndHiddenFunctionsResamplesVisibleSlotsOnly() {
        val visible = input('a', visible = true, id = FunctionSlotId.F3)
        val hidden = input('a', visible = false, id = FunctionSlotId.F1)
        val fixture = Fixture(FunctionGraphContentSnapshot(1, listOf(hidden, visible), mapOf('a' to 1.0)))
        try {
            fixture.deferreds.single().complete(completed(fixture.requests.single()))
            fixture.coordinator.updateContent(
                FunctionGraphContentSnapshot(2, listOf(hidden, visible), mapOf('a' to 4.0))
            )
            assertEquals(2, fixture.requests.size)
            assertEquals("Only the visible slot whose dependency changed is submitted.", listOf(FunctionSlotId.F3), fixture.requests.last().functions.map { it.id })
            assertTrue(fixture.requests.last().functions.single().isVisible)
        } finally { fixture.close() }
    }

    @Test fun parameterChangeResamplesOnlyDependentVisibleSlotsAndKeepsOtherCurveData() {
        val curveA = input('a', id = FunctionSlotId.F3)
        val curveB = input('b', id = FunctionSlotId.F4)
        val fixture = Fixture(FunctionGraphContentSnapshot(1, listOf(curveA, curveB), mapOf('a' to 1.0, 'b' to 2.0)))
        try {
            fixture.deferreds.single().complete(completed(fixture.requests.single()))
            fixture.coordinator.updateContent(
                FunctionGraphContentSnapshot(2, listOf(curveA, curveB), mapOf('a' to 5.0, 'b' to 2.0))
            )
            assertEquals(listOf(FunctionSlotId.F3), fixture.requests.last().functions.map { it.id })
            fixture.deferreds.last().complete(completed(fixture.requests.last()))
            val accepted = fixture.coordinator.state.value.available!!
            assertEquals(listOf(FunctionSlotId.F3, FunctionSlotId.F4), accepted.batch.results.map { it.id })
            assertEquals(2, accepted.batch.results.size)
        } finally { fixture.close() }
    }

    @Test fun makingPreviouslyHiddenFunctionVisibleRequestsCurrentParameters() {
        val hidden = input('a', visible = false, id = FunctionSlotId.F1)
        val fixture = Fixture(FunctionGraphContentSnapshot(1, listOf(hidden), mapOf('a' to 1.0)))
        try {
            fixture.deferreds.single().complete(completed(fixture.requests.single()))
            fixture.coordinator.updateContent(
                FunctionGraphContentSnapshot(2, listOf(input('a', visible = true, id = FunctionSlotId.F1)), mapOf('a' to 7.0))
            )
            assertEquals(2, fixture.requests.size)
            assertEquals(7.0, fixture.requests.last().parameters['a']!!, 0.0)
            assertTrue(fixture.requests.last().functions.single().isVisible)
        } finally { fixture.close() }
    }

    @Test fun visibilityChangesDoNotChangeMathRevisionButRevealUsesTheCurrentContext() {
        val initiallyVisible = input('a', visible = true, id = FunctionSlotId.F1)
        val fixture = Fixture(FunctionGraphContentSnapshot(1, listOf(initiallyVisible), mapOf('a' to 2.0)))
        try {
            fixture.deferreds.single().complete(completed(fixture.requests.single()))
            val hidden = initiallyVisible.copy(isVisible = false)
            fixture.coordinator.updateContent(FunctionGraphContentSnapshot(1, listOf(hidden), mapOf('a' to 2.0)))
            assertEquals("Hiding a function reuses the available visible paths.", 1, fixture.requests.size)

            val shown = initiallyVisible.copy(isVisible = true)
            fixture.coordinator.updateContent(FunctionGraphContentSnapshot(2, listOf(shown), mapOf('a' to 8.0)))
            assertEquals("A newly shown curve must be sampled with current parameters.", 2, fixture.requests.size)
            assertEquals(8.0, fixture.requests.last().parameters['a']!!, 0.0)
        } finally { fixture.close() }
    }

    @Test fun revealingPreviouslyUnsampledSlotAtSameMathRevisionIsNotMistakenForCoveredResult() {
        val hidden = input('a', visible = false, id = FunctionSlotId.F1)
        val fixture = Fixture(FunctionGraphContentSnapshot(1, listOf(hidden), mapOf('a' to 2.0)))
        try {
            fixture.deferreds.single().complete(completed(fixture.requests.single()))
            fixture.coordinator.updateContent(
                FunctionGraphContentSnapshot(1, listOf(hidden.copy(isVisible = true)), mapOf('a' to 2.0))
            )
            assertEquals("A batch sampled while the slot was hidden does not cover its newly visible expression.", 2, fixture.requests.size)
            assertEquals(listOf(FunctionSlotId.F1), fixture.requests.last().functions.map { it.id })
        } finally { fixture.close() }
    }

    @Test fun hidingAFunctionDuringAnActiveSampleCancelsItsWorkAndKeepsVisibleCurves() {
        val costly = input('a', id = FunctionSlotId.F1)
        val visible = input('b', id = FunctionSlotId.F3)
        val fixture = Fixture(FunctionGraphContentSnapshot(1, listOf(costly, visible), mapOf('a' to 2.0, 'b' to 3.0)))
        try {
            fixture.coordinator.updateContent(
                FunctionGraphContentSnapshot(1, listOf(costly.copy(isVisible = false), visible), mapOf('a' to 2.0, 'b' to 3.0))
            )
            assertTrue("An active request spending time on the newly hidden slot is canceled.", fixture.deferreds.first().isCancelled)
            assertEquals(listOf(FunctionSlotId.F3), fixture.requests.last().functions.map { it.id })
        } finally { fixture.close() }
    }

    private class Fixture(content: FunctionGraphContentSnapshot = FunctionGraphContentSnapshot(1, emptyList())) {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val scheduler = FakeScheduler()
        val requests = mutableListOf<FunctionGraphRequest>()
        val deferreds = mutableListOf<CompletableDeferred<FunctionGraphCalculationOutcome>>()
        val coordinator = FunctionGraphCalculationCoordinator(
            scope = scope,
            initialContent = content,
            initialPlotWidthPixels = 400,
            initialPlotHeightPixels = 240,
            scheduler = scheduler,
            submitCalculation = { request ->
                requests += request
                CompletableDeferred<FunctionGraphCalculationOutcome>().also(deferreds::add)
            }
        )
        fun close() { coordinator.close(); scope.cancel() }
    }

    private class FakeScheduler : FunctionGraphScheduler {
        private data class Entry(val deadline: Long, val action: () -> Unit, var cancelled: Boolean = false)
        private val entries = mutableListOf<Entry>()
        private var now = 10_000L
        override fun nowMillis() = now
        override fun schedule(delayMillis: Long, action: () -> Unit): FunctionGraphScheduledTask {
            val entry = Entry(now + delayMillis, action)
            entries += entry
            return FunctionGraphScheduledTask { entry.cancelled = true }
        }
        fun advanceBy(deltaMillis: Long) {
            now += deltaMillis
            while (true) {
                val entry = entries.filter { !it.cancelled && it.deadline <= now }.minByOrNull { it.deadline } ?: return
                entries.remove(entry)
                entry.action()
            }
        }
    }

    private fun completed(request: FunctionGraphRequest) = FunctionGraphCalculationOutcome.Completed(
        FunctionGraphBatchResult(
            request.generation,
            request.functions.map { FunctionGraphResult(it.id, emptyList(), FunctionGraphStatus.NoDefinedPortion,
                FunctionGraphDiagnostics(1, 0, 0, 0)) },
            request.functions.size
        )
    )

    private fun contentWithParameterA() = FunctionGraphContentSnapshot(
        1, listOf(input('a')), mapOf('a' to 1.0)
    )

    private fun input(parameter: Char, visible: Boolean = true, id: FunctionSlotId = FunctionSlotId.F3): FunctionGraphInput {
        val expression = (MathExpressionEngine().compile("$parameter*x") as ExpressionCompilation.Success).expression
        return FunctionGraphInput(id, expression, isVisible = visible, parameterDependencies = setOf(parameter))
    }

    private fun viewport(shift: Double) = GraphViewport(
        AxisRange(-10.0 + shift, 10.0 + shift), AxisRange(-10.0 - shift, 10.0 - shift)
    )
}
