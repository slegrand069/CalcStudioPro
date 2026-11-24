package com.example.calcstudiopro.functions.graph

import com.example.calcstudiopro.domain.functions.ExpressionCompilation
import com.example.calcstudiopro.domain.functions.MathExpressionEngine
import com.example.calcstudiopro.functions.domain.FunctionSlotId
import com.example.calcstudiopro.statistics.graph.model.AxisRange
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.coroutines.CoroutineContext

class FunctionGraphCalculatorTest {
    private val engine = MathExpressionEngine()

    @Test fun newerGenerationCancelsTheOldRequestAndOnlyNewResultCompletes() = runBlocking {
        val dispatcher = QueuedDispatcher()
        val calculator = FunctionGraphCalculator(CoroutineScope(SupervisorJob() + dispatcher), dispatcher = dispatcher)
        val old = calculator.submit(request(1))
        val current = calculator.submit(request(2))
        dispatcher.runAll()

        assertTrue(old.isCancelled)
        val outcome = current.await() as FunctionGraphCalculationOutcome.Completed
        assertEquals(2L, outcome.result.generation)
        assertEquals(2L, calculator.currentGeneration())
    }

    @Test fun duplicateOrOlderGenerationIsRejectedAsObsolete() = runBlocking {
        val calculator = FunctionGraphCalculator(CoroutineScope(SupervisorJob() + kotlinx.coroutines.Dispatchers.Unconfined))
        val accepted = calculator.submit(request(5))
        val rejected = calculator.submit(request(5)).await()
        assertTrue(accepted.await() is FunctionGraphCalculationOutcome.Completed)
        assertEquals(FunctionGraphCalculationOutcome.Obsolete(5), rejected)
    }

    @Test fun explicitCancellationStopsActiveGenerationWithoutReturningPartialResult() {
        val dispatcher = QueuedDispatcher()
        val calculator = FunctionGraphCalculator(CoroutineScope(SupervisorJob() + dispatcher), dispatcher = dispatcher)
        val deferred = calculator.submit(request(1))
        calculator.cancel()
        dispatcher.runAll()
        assertTrue(deferred.isCancelled)
    }

    private fun request(generation: Long) = FunctionGraphRequest(
        generation = generation,
        xRange = AxisRange(-5.0, 5.0),
        yRange = AxisRange(-5.0, 5.0),
        plotWidthPixels = 320,
        plotHeightPixels = 240,
        functions = listOf(FunctionGraphInput(FunctionSlotId.F1, (engine.compile("x^2") as ExpressionCompilation.Success).expression))
    )

    private class QueuedDispatcher : CoroutineDispatcher() {
        private val queue = ArrayDeque<Runnable>()
        override fun dispatch(context: CoroutineContext, block: Runnable) { queue.addLast(block) }
        fun runAll() { while (queue.isNotEmpty()) queue.removeFirst().run() }
    }
}
