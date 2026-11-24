package com.example.calcstudiopro.functions.graph

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Coordinates independent function samples and invalidates work from older viewport generations. */
class FunctionGraphCalculator(
    private val scope: CoroutineScope,
    private val sampler: FunctionGraphSampler = FunctionGraphSampler(),
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default
) {
    private val lock = Any()
    private var latestGeneration = -1L
    private var active: Deferred<FunctionGraphCalculationOutcome>? = null

    /** Submit a strictly newer generation. Submitting it cancels the previous calculation. */
    fun submit(request: FunctionGraphRequest): Deferred<FunctionGraphCalculationOutcome> = synchronized(lock) {
        if (request.generation <= latestGeneration) {
            return@synchronized CompletableDeferred(FunctionGraphCalculationOutcome.Obsolete(request.generation))
        }
        latestGeneration = request.generation
        active?.cancel()

        val calculation = scope.async(dispatcher) {
            val context = currentCoroutineContext()
            val completed = calculateDirect(request) { context.ensureActive() }
            synchronized(lock) {
                if (latestGeneration == request.generation) FunctionGraphCalculationOutcome.Completed(completed)
                else FunctionGraphCalculationOutcome.Obsolete(request.generation)
            }
        }
        active = calculation
        calculation.invokeOnCompletion {
            synchronized(lock) {
                if (active === calculation) active = null
            }
        }
        calculation
    }

    /** Cancels the active request. No partial result is produced by a cancelled job. */
    fun cancel() = synchronized(lock) {
        active?.cancel()
        active = null
        if (latestGeneration < Long.MAX_VALUE) latestGeneration++
    }

    fun currentGeneration(): Long = synchronized(lock) { latestGeneration }

    internal fun calculateDirect(request: FunctionGraphRequest, cancellationCheck: () -> Unit = {}): FunctionGraphBatchResult {
        val counter = FunctionGraphSampler.EvaluationCounter(request.budget.maximumTotalEvaluations)
        val results = ArrayList<FunctionGraphResult>(request.functions.size)
        for (function in request.functions) {
            cancellationCheck()
            // A bad source or a difficult curve is isolated to this slot. Cancellation remains global.
            results += sampler.sample(request, function, counter, cancellationCheck)
        }
        cancellationCheck()
        return FunctionGraphBatchResult(request.generation, results, counter.count)
    }
}
