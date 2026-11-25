package com.example.calcstudiopro.functions.graph.interaction

import com.example.calcstudiopro.domain.functions.FunctionAngleMode
import com.example.calcstudiopro.functions.graph.FunctionGraphBatchResult
import com.example.calcstudiopro.functions.graph.FunctionGraphBudget
import com.example.calcstudiopro.functions.graph.FunctionGraphCalculationOutcome
import com.example.calcstudiopro.functions.graph.FunctionGraphCalculator
import com.example.calcstudiopro.functions.graph.FunctionGraphInput
import com.example.calcstudiopro.functions.graph.FunctionGraphRequest
import com.example.calcstudiopro.functions.graph.FunctionGraphResult
import com.example.calcstudiopro.functions.graph.FunctionGraphStatus
import com.example.calcstudiopro.functions.graph.FunctionGraphDiagnostics
import com.example.calcstudiopro.functions.domain.FunctionSlotId
import com.example.calcstudiopro.statistics.graph.model.GraphViewport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Collections

/** Inputs whose revision changes for expression, parameter, or angle-mode edits (not viewport gestures). */
class FunctionGraphContentSnapshot(
    val revision: Long,
    functions: List<FunctionGraphInput>,
    parameters: Map<Char, Double> = emptyMap(),
    val angleMode: FunctionAngleMode = FunctionAngleMode.RAD,
    val budget: FunctionGraphBudget = FunctionGraphBudget()
) {
    val functions = Collections.unmodifiableList(ArrayList(functions))
    val parameters = Collections.unmodifiableMap(LinkedHashMap(parameters))
    init {
        require(revision >= 0)
        require(functions.size <= 6 && functions.map { it.id }.distinct().size == functions.size)
        require(parameters.values.all(Double::isFinite))
    }
}

data class FunctionGraphRequestContext(
    val content: FunctionGraphContentSnapshot,
    val viewport: GraphViewport,
    val plotWidthPixels: Int,
    val plotHeightPixels: Int
) {
    init { require(plotWidthPixels > 0 && plotHeightPixels > 0) }

    fun toRequest(generation: Long, functions: List<FunctionGraphInput> = content.functions) = FunctionGraphRequest(
        generation = generation,
        xRange = viewport.x,
        yRange = viewport.y,
        plotWidthPixels = plotWidthPixels,
        plotHeightPixels = plotHeightPixels,
        functions = functions,
        parameters = content.parameters,
        angleMode = content.angleMode,
        budget = content.budget
    )

    fun sameCalculation(other: FunctionGraphRequestContext): Boolean {
        val visibleIds = content.functions.filter { it.isVisible }.map { it.id }.toSet()
        val otherVisibleIds = other.content.functions.filter { it.isVisible }.map { it.id }.toSet()
        return visibleIds == otherVisibleIds && sameContentCalculation(other) && viewport == other.viewport &&
            plotWidthPixels == other.plotWidthPixels && plotHeightPixels == other.plotHeightPixels
    }

    fun sameContentCalculation(other: FunctionGraphRequestContext): Boolean {
        if (content.angleMode != other.content.angleMode || content.budget != other.content.budget) return false
        val oldVisible = content.functions.filter { it.isVisible }.associateBy { it.id }
        val newVisible = other.content.functions.filter { it.isVisible }.associateBy { it.id }
        // Hiding a slot cannot invalidate remaining visible curves; revealing one requires sampling it.
        if (!oldVisible.keys.containsAll(newVisible.keys)) return false
        return oldVisible.all { (id, old) ->
            if (id !in newVisible) return@all true
            val new = newVisible.getValue(id)
            if (old.expression != new.expression || old.compilationError != new.compilationError) return@all false
            val dependencies = old.parameterDependencies ?: (content.parameters.keys + other.content.parameters.keys)
            dependencies.all { content.parameters[it] == other.content.parameters[it] }
        }
    }

    fun sameMathContent(other: FunctionGraphRequestContext): Boolean =
        content.angleMode == other.content.angleMode && content.budget == other.content.budget &&
            content.functions.map { it.id to (it.expression to it.compilationError) } ==
                other.content.functions.map { it.id to (it.expression to it.compilationError) } &&
            content.parameters == other.content.parameters
}

data class FunctionGraphAcceptedResult(
    val contentRevision: Long,
    val request: FunctionGraphRequest,
    val batch: FunctionGraphBatchResult
)

data class FunctionGraphInteractiveState(
    val displayedViewport: GraphViewport,
    val displayedViewportGeneration: Long = 0,
    val requested: FunctionGraphRequestContext? = null,
    val requestedGeneration: Long? = null,
    val available: FunctionGraphAcceptedResult? = null,
    val gestureActive: Boolean = false,
    val calculationActive: Boolean = false,
    val pending: FunctionGraphRequestContext? = null
)

fun interface FunctionGraphScheduledTask { fun cancel() }

/** Clock/timer seam keeps the 250 ms request policy deterministic in JVM tests. */
interface FunctionGraphScheduler {
    fun nowMillis(): Long
    fun schedule(delayMillis: Long, action: () -> Unit): FunctionGraphScheduledTask
}

class CoroutineFunctionGraphScheduler(private val scope: CoroutineScope) : FunctionGraphScheduler {
    override fun nowMillis(): Long = System.nanoTime() / 1_000_000L
    override fun schedule(delayMillis: Long, action: () -> Unit): FunctionGraphScheduledTask {
        val job: Job = scope.launch { delay(delayMillis); action() }
        return FunctionGraphScheduledTask { job.cancel() }
    }
}

/**
 * Owns the displayed/requested/available split. At most one calculation is submitted at once and
 * every later viewport update replaces one pending context instead of creating a queue.
 */
class FunctionGraphCalculationCoordinator(
    private val scope: CoroutineScope,
    private val initialContent: FunctionGraphContentSnapshot,
    initialViewport: GraphViewport = FunctionGraphViewportController.INITIAL,
    initialPlotWidthPixels: Int,
    initialPlotHeightPixels: Int,
    private val scheduler: FunctionGraphScheduler = CoroutineFunctionGraphScheduler(scope),
    private val intervalMillis: Long = DEFAULT_INTERVAL_MILLIS,
    submitCalculation: ((FunctionGraphRequest) -> Deferred<FunctionGraphCalculationOutcome>)? = null,
    private val calculator: FunctionGraphCalculator? = null
) {
    private val lock = Any()
    private val submit = submitCalculation ?: requireNotNull(calculator) { "Provide a calculator or a test submitter." }::submit
    private val mutableState = MutableStateFlow(FunctionGraphInteractiveState(initialViewport))
    val state: StateFlow<FunctionGraphInteractiveState> = mutableState.asStateFlow()
    private var content = initialContent
    private var width = initialPlotWidthPixels.also { require(it > 0) }
    private var height = initialPlotHeightPixels.also { require(it > 0) }
    private var pending: FunctionGraphRequestContext? = null
    private var pendingReusableResults: Map<FunctionSlotId, FunctionGraphResult> = emptyMap()
    private var active: ActiveCalculation? = null
    private var lastSubmitted: FunctionGraphRequestContext? = null
    private var lastSubmittedAt = Long.MIN_VALUE / 2
    private var timer: FunctionGraphScheduledTask? = null
    private var nextGeneration = 0L
    private var closed = false

    init {
        require(intervalMillis >= 0)
        synchronized(lock) {
            pending = context(initialViewport)
            submitPendingLocked()
        }
    }

    /** Update this on every pointer frame; it only publishes viewport state and schedules work. */
    fun updateDisplayedViewport(viewport: GraphViewport) = synchronized(lock) {
        if (closed) return@synchronized
        val old = mutableState.value
        if (old.displayedViewport != viewport) {
            mutableState.value = old.copy(
                displayedViewport = viewport,
                displayedViewportGeneration = old.displayedViewportGeneration + 1
            )
        }
        pending = context(viewport)
        pendingReusableResults = emptyMap()
        mutableState.value = mutableState.value.copy(pending = pending)
        if (old.gestureActive) requestIntermediateIfDueLocked() else requestFinalLocked()
    }

    fun beginGesture() = synchronized(lock) {
        if (!closed) mutableState.value = mutableState.value.copy(gestureActive = true)
    }

    fun endGesture() = synchronized(lock) {
        if (closed) return@synchronized
        mutableState.value = mutableState.value.copy(gestureActive = false)
        requestFinalLocked()
    }

    /** A new content revision invalidates old results; viewport-only updates leave it unchanged. */
    fun updateContent(newContent: FunctionGraphContentSnapshot) = synchronized(lock) {
        if (closed) return@synchronized
        val current = mutableState.value
        val previousContext = context(current.displayedViewport)
        if (newContent.revision == content.revision &&
            newContent.functions == content.functions && newContent.parameters == content.parameters &&
            newContent.angleMode == content.angleMode && newContent.budget == content.budget
        ) return@synchronized
        if (newContent.revision == content.revision) {
            require(previousContext.sameMathContent(previousContext.copy(content = newContent))) {
                "A reused content revision cannot change expressions, parameters, or angle mode."
            }
        } else {
            require(newContent.revision > content.revision) {
                "Math-affecting content changes must use a strictly newer content revision."
            }
        }
        val equivalentCalculation = previousContext.sameContentCalculation(previousContext.copy(content = newContent))
        content = newContent
        if (equivalentCalculation) {
            val nowHidden = active?.request?.functions.orEmpty().any { requested ->
                requested.isVisible && newContent.functions.firstOrNull { it.id == requested.id }?.isVisible != true
            }
            pending = if (nowHidden) context(current.displayedViewport) else null
            pendingReusableResults = if (nowHidden) reusableResults(current.available, context(current.displayedViewport)) else emptyMap()
            val available = current.available?.let { accepted ->
                accepted.copy(contentRevision = newContent.revision)
            }
            mutableState.value = current.copy(available = available, pending = pending)
            if (nowHidden) {
                cancelActiveLocked()
                if (!mutableState.value.gestureActive) requestFinalLocked()
            }
            return@synchronized
        }
        val newContext = context(current.displayedViewport)
        val reusableResults = reusableResults(current.available, newContext)
        val visibleIds = newContent.functions.filter { it.isVisible }.map { it.id }.toSet()
        if (current.available != null && reusableResults.keys.containsAll(visibleIds) &&
            current.available.request.xRange == current.displayedViewport.x &&
            current.available.request.yRange == current.displayedViewport.y &&
            current.available.request.plotWidthPixels == width && current.available.request.plotHeightPixels == height
        ) {
            val fullRequest = newContext.toRequest(current.available.batch.generation)
            val merged = mergeResults(newContent, reusableResults, emptyList())
            val batch = FunctionGraphBatchResult(current.available.batch.generation, merged, current.available.batch.totalEvaluationCount)
            val rebased = FunctionGraphAcceptedResult(newContent.revision, fullRequest, batch)
            pending = null
            pendingReusableResults = emptyMap()
            mutableState.value = current.copy(available = rebased, pending = null)
            return@synchronized
        }
        val stillCompatible = current.available?.contentRevision == newContent.revision
        pending = context(current.displayedViewport)
        pendingReusableResults = reusableResults
        mutableState.value = current.copy(available = if (stillCompatible) current.available else null, pending = pending)
        cancelActiveLocked()
        if (!mutableState.value.gestureActive) requestFinalLocked()
    }

    /** Size changes schedule new sampling but never alter mathematical bounds. */
    fun updatePlotSize(widthPixels: Int, heightPixels: Int) = synchronized(lock) {
        require(widthPixels > 0 && heightPixels > 0)
        if (closed || (width == widthPixels && height == heightPixels)) return@synchronized
        width = widthPixels
        height = heightPixels
        pending = context(mutableState.value.displayedViewport)
        pendingReusableResults = emptyMap()
        mutableState.value = mutableState.value.copy(pending = pending)
        if (!mutableState.value.gestureActive) requestFinalLocked() else requestIntermediateIfDueLocked()
    }

    fun resetViewport() = updateDisplayedViewport(FunctionGraphViewportController.reset())

    fun close() = synchronized(lock) {
        if (closed) return@synchronized
        closed = true
        timer?.cancel(); timer = null
        active?.deferred?.cancel(); active = null
        calculator?.cancel()
        pending = null
    }

    private fun context(viewport: GraphViewport) = FunctionGraphRequestContext(content, viewport, width, height)

    private fun requestIntermediateIfDueLocked() {
        if (pending == null || active != null || isAlreadyCoveredLocked(pending!!)) return
        val elapsed = scheduler.nowMillis() - lastSubmittedAt
        if (elapsed >= intervalMillis) submitPendingLocked() else scheduleLocked(intervalMillis - elapsed)
    }

    private fun requestFinalLocked() {
        val latest = pending ?: return
        if (isAlreadyCoveredLocked(latest)) {
            pending = null
            mutableState.value = mutableState.value.copy(pending = null)
            return
        }
        timer?.cancel(); timer = null
        if (active != null) {
            // Do not leave a long intermediate request running once the user has released the gesture.
            cancelActiveLocked()
            return // Its completion callback submits this final context.
        }
        submitPendingLocked()
    }

    private fun isAlreadyCoveredLocked(context: FunctionGraphRequestContext): Boolean =
        active?.context?.sameCalculation(context) == true ||
            mutableState.value.available?.let { available ->
                val request = available.request
                FunctionGraphRequestContext(
                    content = FunctionGraphContentSnapshot(
                        available.contentRevision, request.functions, request.parameters, request.angleMode, request.budget
                    ),
                    viewport = GraphViewport(request.xRange, request.yRange),
                    plotWidthPixels = request.plotWidthPixels,
                    plotHeightPixels = request.plotHeightPixels
                ).sameCalculation(context)
            } == true

    private fun submitPendingLocked() {
        if (closed || active != null) return
        val context = pending ?: return
        if (isAlreadyCoveredLocked(context)) {
            pending = null
            mutableState.value = mutableState.value.copy(pending = null)
            return
        }
        check(nextGeneration < Long.MAX_VALUE) { "Graph calculation generation exhausted." }
        val generation = nextGeneration++
        val allRequest = context.toRequest(generation)
        val sampledFunctions = context.content.functions.filter { it.isVisible && it.id !in pendingReusableResults }
        val request = context.toRequest(generation, sampledFunctions)
        val deferred = submit(request)
        val job = ActiveCalculation(context, request, allRequest, pendingReusableResults, deferred)
        active = job
        pending = null
        pendingReusableResults = emptyMap()
        lastSubmitted = context
        lastSubmittedAt = scheduler.nowMillis()
        mutableState.value = mutableState.value.copy(
            requested = context,
            requestedGeneration = request.generation,
            calculationActive = true,
            pending = null
        )
        deferred.invokeOnCompletion {
            scope.launch { finishCalculation(job) }
        }
    }

    private suspend fun finishCalculation(job: ActiveCalculation) {
        val outcome = runCatching { job.deferred.await() }.getOrNull()
        synchronized(lock) {
            if (closed || active !== job) return
            active = null
            val old = mutableState.value
            val completed = outcome as? FunctionGraphCalculationOutcome.Completed
            val available = completed?.result?.let { batch ->
                val currentContext = context(old.displayedViewport)
                val revisionStillCurrent = job.context.sameContentCalculation(currentContext)
                val isNewerThanAccepted = old.available == null || batch.generation > old.available.batch.generation
                if (revisionStillCurrent && isNewerThanAccepted && batch.generation == job.request.generation) {
                    val merged = mergeResults(job.context.content, job.reusedResults, batch.results)
                    val completeBatch = FunctionGraphBatchResult(batch.generation, merged, batch.totalEvaluationCount)
                    FunctionGraphAcceptedResult(content.revision, job.fullRequest, completeBatch)
                } else old.available
            } ?: old.available
            mutableState.value = old.copy(available = available, calculationActive = false)

            if (pending != null) {
                if (!mutableState.value.gestureActive) requestFinalLocked()
                else requestIntermediateIfDueLocked()
            }
        }
    }

    private fun cancelActiveLocked() {
        val job = active ?: return
        pending = context(mutableState.value.displayedViewport)
        mutableState.value = mutableState.value.copy(pending = pending, calculationActive = true)
        job.deferred.cancel()
        // The active slot remains occupied until cooperative cancellation completes.
    }

    private fun scheduleLocked(delayMillis: Long) {
        if (timer != null || closed) return
        timer = scheduler.schedule(delayMillis.coerceAtLeast(0)) {
            synchronized(lock) {
                timer = null
                if (!closed && mutableState.value.gestureActive) requestIntermediateIfDueLocked()
            }
        }
    }

    private data class ActiveCalculation(
        val context: FunctionGraphRequestContext,
        val request: FunctionGraphRequest,
        val fullRequest: FunctionGraphRequest,
        val reusedResults: Map<FunctionSlotId, FunctionGraphResult>,
        val deferred: Deferred<FunctionGraphCalculationOutcome>
    )

    private fun reusableResults(
        accepted: FunctionGraphAcceptedResult?,
        newContext: FunctionGraphRequestContext
    ): Map<FunctionSlotId, FunctionGraphResult> {
        if (accepted == null || accepted.request.xRange != newContext.viewport.x ||
            accepted.request.yRange != newContext.viewport.y ||
            accepted.request.plotWidthPixels != newContext.plotWidthPixels ||
            accepted.request.plotHeightPixels != newContext.plotHeightPixels ||
            accepted.request.angleMode != newContext.content.angleMode
        ) return emptyMap()
        val oldInputs = accepted.request.functions.associateBy { it.id }
        val oldResults = accepted.batch.results.associateBy { it.id }
        return newContext.content.functions.asSequence().filter { it.isVisible }.mapNotNull { input ->
            val oldInput = oldInputs[input.id] ?: return@mapNotNull null
            if (!oldInput.isVisible || oldInput.expression !== input.expression ||
                oldInput.compilationError != input.compilationError
            ) return@mapNotNull null
            val dependencies = if (oldInput.parameterDependencies == null || input.parameterDependencies == null) {
                accepted.request.parameters.keys + newContext.content.parameters.keys
            } else oldInput.parameterDependencies + input.parameterDependencies
            if (dependencies.any { parameter ->
                    (accepted.request.parameters[parameter] ?: 1.0) != (newContext.content.parameters[parameter] ?: 1.0)
                }) return@mapNotNull null
            oldResults[input.id]?.let { input.id to it }
        }.toMap()
    }

    private fun mergeResults(
        snapshot: FunctionGraphContentSnapshot,
        reused: Map<FunctionSlotId, FunctionGraphResult>,
        sampled: List<FunctionGraphResult>
    ): List<FunctionGraphResult> {
        val sampledById = sampled.associateBy { it.id }
        return snapshot.functions.map { input ->
            when {
                !input.isVisible -> emptyResult(input.id)
                input.id in sampledById -> sampledById.getValue(input.id)
                input.id in reused -> reused.getValue(input.id)
                else -> emptyResult(input.id)
            }
        }
    }

    private fun emptyResult(id: FunctionSlotId) = FunctionGraphResult(
        id, emptyList(), FunctionGraphStatus.Empty, FunctionGraphDiagnostics(0, 0, 0, 0)
    )

    companion object { const val DEFAULT_INTERVAL_MILLIS = 250L }
}
