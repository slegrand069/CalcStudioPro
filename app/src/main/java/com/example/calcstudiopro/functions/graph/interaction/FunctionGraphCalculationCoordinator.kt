package com.example.calcstudiopro.functions.graph.interaction

import com.example.calcstudiopro.domain.functions.FunctionAngleMode
import com.example.calcstudiopro.functions.graph.FunctionGraphBatchResult
import com.example.calcstudiopro.functions.graph.FunctionGraphBudget
import com.example.calcstudiopro.functions.graph.FunctionGraphCalculationOutcome
import com.example.calcstudiopro.functions.graph.FunctionGraphCalculator
import com.example.calcstudiopro.functions.graph.FunctionGraphInput
import com.example.calcstudiopro.functions.graph.FunctionGraphRequest
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

    fun toRequest(generation: Long) = FunctionGraphRequest(
        generation = generation,
        xRange = viewport.x,
        yRange = viewport.y,
        plotWidthPixels = plotWidthPixels,
        plotHeightPixels = plotHeightPixels,
        functions = content.functions,
        parameters = content.parameters,
        angleMode = content.angleMode,
        budget = content.budget
    )

    fun sameCalculation(other: FunctionGraphRequestContext): Boolean =
        content.revision == other.content.revision &&
            content.functions == other.content.functions &&
            content.parameters == other.content.parameters &&
            content.angleMode == other.content.angleMode && content.budget == other.content.budget &&
            viewport == other.viewport && plotWidthPixels == other.plotWidthPixels && plotHeightPixels == other.plotHeightPixels
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
        if (newContent.revision == content.revision &&
            newContent.functions == content.functions && newContent.parameters == content.parameters &&
            newContent.angleMode == content.angleMode && newContent.budget == content.budget
        ) return@synchronized
        require(newContent.revision > content.revision) {
            "Content changes must use a strictly newer content revision."
        }
        content = newContent
        val current = mutableState.value
        val stillCompatible = current.available?.contentRevision == newContent.revision
        pending = context(current.displayedViewport)
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
                available.contentRevision == context.content.revision &&
                    available.request.let { request ->
                        request.xRange == context.viewport.x && request.yRange == context.viewport.y &&
                            request.plotWidthPixels == context.plotWidthPixels && request.plotHeightPixels == context.plotHeightPixels
                    }
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
        val request = context.toRequest(nextGeneration++)
        val deferred = submit(request)
        val job = ActiveCalculation(context, request, deferred)
        active = job
        pending = null
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
                val revisionStillCurrent = content.revision == job.context.content.revision
                val isNewerThanAccepted = old.available == null || batch.generation > old.available.batch.generation
                if (revisionStillCurrent && isNewerThanAccepted && batch.generation == job.request.generation) {
                    FunctionGraphAcceptedResult(job.context.content.revision, job.request, batch)
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
        val deferred: Deferred<FunctionGraphCalculationOutcome>
    )

    companion object { const val DEFAULT_INTERVAL_MILLIS = 250L }
}
