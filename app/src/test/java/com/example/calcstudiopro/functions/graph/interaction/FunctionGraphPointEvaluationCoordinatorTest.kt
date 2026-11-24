package com.example.calcstudiopro.functions.graph.interaction

import com.example.calcstudiopro.domain.functions.ExpressionCompilation
import com.example.calcstudiopro.domain.functions.ExpressionEvaluation
import com.example.calcstudiopro.domain.functions.FunctionAngleMode
import com.example.calcstudiopro.domain.functions.MathExpressionEngine
import com.example.calcstudiopro.functions.domain.FunctionSlotId
import com.example.calcstudiopro.functions.graph.FunctionGraphPoint
import com.example.calcstudiopro.statistics.graph.model.ScreenPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.*
import org.junit.Test
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class FunctionGraphPointEvaluationCoordinatorTest {
    @Test fun coalescesToOneLatestPendingRequestAndKeepsEvaluationOffCallerThread() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val completed = CountDownLatch(2)
        val results = Collections.synchronizedList(mutableListOf<Pair<Long, Double>>())
        val engine = MathExpressionEngine()
        val expression = (engine.compile("x") as ExpressionCompilation.Success).expression
        val coordinator = FunctionGraphPointEvaluationCoordinator(
            scope = scope,
            evaluate = { request ->
                if (request.requestId == 1L) {
                    started.countDown()
                    check(release.await(2, TimeUnit.SECONDS))
                }
                request.expression.evaluate(request.x, request.parameters, request.angleMode)
            },
            onResult = { request, result ->
                if (result is ExpressionEvaluation.Success) results += request.requestId to result.value
                completed.countDown()
            }
        )
        val first = coordinator.submit(FunctionSlotId.F1, 1.0, 9, expression, emptyMap(), FunctionAngleMode.RAD)
        assertEquals(1L, first)
        assertTrue(started.await(2, TimeUnit.SECONDS))
        val superseded = coordinator.submit(FunctionSlotId.F1, 2.0, 9, expression, emptyMap(), FunctionAngleMode.RAD)
        val latest = coordinator.submit(FunctionSlotId.F1, 3.0, 9, expression, emptyMap(), FunctionAngleMode.DEG, isFinal = true)
        assertEquals(2L, superseded)
        assertEquals(3L, latest)
        release.countDown()
        assertTrue(completed.await(3, TimeUnit.SECONDS))
        assertEquals(listOf(1L, 3L), results.map { it.first })
        assertEquals(listOf(1.0, 3.0), results.map { it.second })
        coordinator.close()
        scope.coroutineContext[Job]?.cancel()
    }

    @Test fun invalidatedActiveEvaluationMayFinishButCannotUpdateAChangedSelection() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val completed = CountDownLatch(1)
        val controller = FunctionGraphSelectionController(24.0)
        val marker = controller.select(
            listOf(FunctionGraphCurveHit(FunctionSlotId.F1, 0.0, 0, 0, 0.5, FunctionGraphPoint(2.0, 2.0))),
            ScreenPoint(10.0, 10.0), FunctionGraphSelectionContext(1, 1, 1), 1
        )!!
        val engine = MathExpressionEngine()
        val expression = (engine.compile("x") as ExpressionCompilation.Success).expression
        val coordinator = FunctionGraphPointEvaluationCoordinator(
            scope = scope,
            evaluate = { request ->
                started.countDown()
                check(release.await(3, TimeUnit.SECONDS))
                request.expression.evaluate(request.x, request.parameters, request.angleMode)
            },
            onResult = { request, result ->
                val evaluation = (result as? ExpressionEvaluation.Success)?.let { FunctionGraphMarkerEvaluation.Confirmed(it.value) }
                    ?: FunctionGraphMarkerEvaluation.Undefined
                controller.applyEvaluation(request.requestId, request.slotId, request.x, request.contentRevision, evaluation)
                completed.countDown()
            }
        )
        try {
            val requestId = coordinator.submit(FunctionSlotId.F1, 2.0, 1, expression, emptyMap(), FunctionAngleMode.RAD)
            controller.setEvaluationRequest(marker, requestId)
            assertTrue(started.await(2, TimeUnit.SECONDS))

            coordinator.invalidate() // Clears pending work; active evaluation is logically invalidated by selection state.
            controller.onContentChanged(newRevision = 2, selectedSlotStillValid = true)
            release.countDown()

            assertTrue(completed.await(2, TimeUnit.SECONDS))
            assertEquals(2L, controller.state.value.marker?.contentRevision)
            assertEquals(FunctionGraphMarkerEvaluation.Provisional, controller.state.value.marker?.evaluation)
        } finally {
            release.countDown()
            coordinator.close()
            scope.coroutineContext[Job]?.cancel()
        }
    }
}
