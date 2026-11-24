package com.example.calcstudiopro.functions.graph.interaction

import com.example.calcstudiopro.domain.functions.ExpressionCompilation
import com.example.calcstudiopro.domain.functions.ExpressionEvaluation
import com.example.calcstudiopro.domain.functions.FunctionAngleMode
import com.example.calcstudiopro.domain.functions.MathExpressionEngine
import com.example.calcstudiopro.functions.domain.FunctionSlotId
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
}
