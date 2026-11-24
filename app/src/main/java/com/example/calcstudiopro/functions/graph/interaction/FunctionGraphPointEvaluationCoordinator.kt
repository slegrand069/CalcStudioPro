package com.example.calcstudiopro.functions.graph.interaction

import com.example.calcstudiopro.domain.functions.CompiledMathExpression
import com.example.calcstudiopro.domain.functions.ExpressionEvaluation
import com.example.calcstudiopro.domain.functions.FunctionAngleMode
import com.example.calcstudiopro.domain.functions.MathExpressionError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class FunctionGraphPointEvaluationRequest(
    val requestId: Long,
    val slotId: com.example.calcstudiopro.functions.domain.FunctionSlotId,
    val x: Double,
    val contentRevision: Long,
    val expression: CompiledMathExpression,
    val parameters: Map<Char, Double>,
    val angleMode: FunctionAngleMode,
    val isFinal: Boolean
)

/** Serial CPU evaluator with a single replaceable pending point; viewport changes are irrelevant. */
class FunctionGraphPointEvaluationCoordinator(
    private val scope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val evaluate: (FunctionGraphPointEvaluationRequest) -> ExpressionEvaluation = { request ->
        request.expression.evaluate(request.x, request.parameters, request.angleMode)
    },
    private val onResult: (FunctionGraphPointEvaluationRequest, ExpressionEvaluation) -> Unit
) {
    private val lock = Any()
    private var nextId = 0L
    private var active: Job? = null
    private var pending: FunctionGraphPointEvaluationRequest? = null

    /** Replaces the one pending intermediate request; [isFinal] requests are never delayed by a timer. */
    fun submit(
        slotId: com.example.calcstudiopro.functions.domain.FunctionSlotId,
        x: Double,
        contentRevision: Long,
        expression: CompiledMathExpression,
        parameters: Map<Char, Double>,
        angleMode: FunctionAngleMode,
        isFinal: Boolean = false
    ): Long {
        require(x.isFinite() && contentRevision >= 0 && parameters.values.all(Double::isFinite))
        val request: FunctionGraphPointEvaluationRequest
        synchronized(lock) {
            check(nextId < Long.MAX_VALUE) { "Evaluation request ID exhausted." }
            request = FunctionGraphPointEvaluationRequest(
                ++nextId, slotId, x, contentRevision, expression, parameters.toMap(), angleMode, isFinal
            )
            pending = request
            if (active == null) startWorkerLocked()
        }
        return request.requestId
    }

    /** Invalidates callbacks and pending work for a changed selection/content revision. */
    fun invalidate() = synchronized(lock) {
        pending = null
        // The active computation is allowed to finish; callers reject its stale token.
    }

    fun close() = synchronized(lock) {
        pending = null
        active?.cancel()
        active = null
    }

    private fun startWorkerLocked() {
        if (active != null || pending == null) return
        active = scope.launch {
            while (true) {
                val request = synchronized(lock) {
                    val next = pending
                    pending = null
                    next
                } ?: break
                try {
                    val result = withContext(dispatcher) { evaluate(request) }
                    onResult(request, result)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    // Contain evaluator faults and publish a typed state instead of leaving UI Pending.
                    onResult(request, ExpressionEvaluation.Failure(
                        MathExpressionError.Syntax(null, "Évaluation impossible")
                    ))
                }
            }
            synchronized(lock) {
                active = null
                if (pending != null) startWorkerLocked()
            }
        }
    }
}
