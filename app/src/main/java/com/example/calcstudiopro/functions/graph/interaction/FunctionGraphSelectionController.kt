package com.example.calcstudiopro.functions.graph.interaction

import com.example.calcstudiopro.domain.functions.MathExpressionError
import com.example.calcstudiopro.functions.domain.FunctionSlotId
import com.example.calcstudiopro.functions.graph.FunctionGraphPoint
import com.example.calcstudiopro.functions.graph.FunctionGraphStatus
import com.example.calcstudiopro.functions.graph.render.FunctionGraphRenderModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Collections
import kotlin.math.hypot

sealed interface FunctionGraphMarkerEvaluation {
    data object Provisional : FunctionGraphMarkerEvaluation
    data object Pending : FunctionGraphMarkerEvaluation
    data class Confirmed(val y: Double) : FunctionGraphMarkerEvaluation
    data object Undefined : FunctionGraphMarkerEvaluation
    data class Error(val error: MathExpressionError) : FunctionGraphMarkerEvaluation
}

/** The marker's mathematical x is independent of its current screen position and visibility. */
data class FunctionGraphMarkerState(
    val slotId: FunctionSlotId,
    val x: Double,
    val provisionalY: Double?,
    val segmentIndex: Int?,
    val edgeIndex: Int?,
    val dataGeneration: Long?,
    val contentRevision: Long,
    val evaluationRequestId: Long? = null,
    val evaluation: FunctionGraphMarkerEvaluation = FunctionGraphMarkerEvaluation.Provisional
) {
    val displayedY: Double?
        get() = when (val status = evaluation) {
            is FunctionGraphMarkerEvaluation.Confirmed -> status.y
            else -> provisionalY
        }
}

data class FunctionGraphSelectionState(val marker: FunctionGraphMarkerState? = null)

data class FunctionGraphSelectionContext(
    val contentRevision: Long,
    val dataGeneration: Long?,
    val viewportGeneration: Long
)

/** Selection/candidate cycling and rollback state for the first tap of a possible double tap. */
class FunctionGraphSelectionController(
    private val cycleRadiusPixels: Double,
    private val cycleDurationMillis: Long = DEFAULT_CYCLE_DURATION_MILLIS
) {
    private val mutableState = MutableStateFlow(FunctionGraphSelectionState())
    val state: StateFlow<FunctionGraphSelectionState> = mutableState.asStateFlow()
    private var cycle: CandidateCycle? = null
    private var pendingDoubleTapSnapshot: Snapshot? = null

    init {
        require(cycleRadiusPixels.isFinite() && cycleRadiusPixels > 0.0)
        require(cycleDurationMillis > 0L)
    }

    /** Applies the nearest candidate immediately; a later recognized double tap can roll it back. */
    @Synchronized
    fun select(
        candidates: List<FunctionGraphCurveHit>,
        touch: com.example.calcstudiopro.statistics.graph.model.ScreenPoint,
        context: FunctionGraphSelectionContext,
        nowMillis: Long
    ): FunctionGraphMarkerState? {
        if (pendingDoubleTapSnapshot == null) pendingDoubleTapSnapshot = Snapshot(mutableState.value, cycle)
        if (candidates.isEmpty()) {
            cycle = null
            return null
        }

        val ids = candidates.map { it.slotId }
        val idSet = ids.toSet()
        val existing = cycle
        val continueCycle = existing != null &&
            nowMillis >= existing.lastTapMillis && nowMillis - existing.lastTapMillis <= cycleDurationMillis &&
            distance(touch, existing.anchor) <= cycleRadiusPixels &&
            existing.context == context && existing.candidateIds == idSet
        val order = if (continueCycle) existing!!.order else ids
        val selectedIndex = if (continueCycle) (existing!!.selectedIndex + 1) % order.size else 0
        val selectedId = order[selectedIndex]
        val hit = candidates.firstOrNull { it.slotId == selectedId } ?: return null
        val marker = FunctionGraphMarkerState(
            slotId = hit.slotId,
            x = hit.point.x,
            provisionalY = hit.point.y,
            segmentIndex = hit.segmentIndex,
            edgeIndex = hit.edgeIndex,
            dataGeneration = context.dataGeneration,
            contentRevision = context.contentRevision
        )
        cycle = CandidateCycle(
            anchor = if (continueCycle) existing!!.anchor else touch,
            lastTapMillis = nowMillis,
            context = context,
            candidateIds = idSet,
            order = Collections.unmodifiableList(order.toList()),
            selectedIndex = selectedIndex
        )
        mutableState.value = FunctionGraphSelectionState(marker)
        return marker
    }

    /** Commits the immediate selection once the double-tap window was not used for a reset. */
    @Synchronized
    fun commitPendingTap() { pendingDoubleTapSnapshot = null }

    /** Restores the prior marker and candidate cycle before the viewport's double-tap reset. */
    @Synchronized
    fun rollbackPendingTapForDoubleTap(): FunctionGraphMarkerState? {
        val snapshot = pendingDoubleTapSnapshot ?: return mutableState.value.marker
        mutableState.value = snapshot.selection
        cycle = snapshot.cycle
        pendingDoubleTapSnapshot = null
        return mutableState.value.marker
    }

    @Synchronized
    fun invalidateCycle() { cycle = null }

    @Synchronized
    fun clear() {
        mutableState.value = FunctionGraphSelectionState()
        cycle = null
        pendingDoubleTapSnapshot = null
    }

    @Synchronized
    fun setEvaluationRequest(marker: FunctionGraphMarkerState, requestId: Long): FunctionGraphMarkerState? {
        val current = mutableState.value.marker ?: return null
        if (!sameMarker(current, marker)) return null
        val updated = current.copy(evaluationRequestId = requestId, evaluation = FunctionGraphMarkerEvaluation.Pending)
        mutableState.value = FunctionGraphSelectionState(updated)
        return updated
    }

    @Synchronized
    fun applyEvaluation(
        requestId: Long,
        slotId: FunctionSlotId,
        x: Double,
        contentRevision: Long,
        result: FunctionGraphMarkerEvaluation
    ): Boolean {
        val current = mutableState.value.marker ?: return false
        if (current.evaluationRequestId != requestId || current.slotId != slotId ||
            current.x.toBits() != x.toBits() || current.contentRevision != contentRevision
        ) return false
        mutableState.value = FunctionGraphSelectionState(current.copy(evaluation = result))
        return true
    }

    @Synchronized
    fun moveMarker(hit: FunctionGraphCurveHit, context: FunctionGraphSelectionContext): FunctionGraphMarkerState? {
        val current = mutableState.value.marker ?: return null
        if (current.slotId != hit.slotId || current.contentRevision != context.contentRevision) return null
        val moved = FunctionGraphMarkerState(
            slotId = hit.slotId,
            x = hit.point.x,
            provisionalY = hit.point.y,
            segmentIndex = hit.segmentIndex,
            edgeIndex = hit.edgeIndex,
            dataGeneration = context.dataGeneration,
            contentRevision = context.contentRevision
        )
        mutableState.value = FunctionGraphSelectionState(moved)
        return moved
    }

    /** Content edits invalidate numerical values but preserve the selected x when its slot remains valid. */
    @Synchronized
    fun onContentChanged(newRevision: Long, selectedSlotStillValid: Boolean): FunctionGraphMarkerState? {
        cycle = null
        pendingDoubleTapSnapshot = null
        val current = mutableState.value.marker ?: return null
        if (!selectedSlotStillValid) {
            clear()
            return null
        }
        val updated = current.copy(
            provisionalY = null,
            segmentIndex = null,
            edgeIndex = null,
            dataGeneration = null,
            contentRevision = newRevision,
            evaluationRequestId = null,
            evaluation = FunctionGraphMarkerEvaluation.Provisional
        )
        mutableState.value = FunctionGraphSelectionState(updated)
        return updated
    }

    /** Remaps a marker after new sampling; missing geometry is temporary unless the slot is empty. */
    @Synchronized
    fun onRenderModelChanged(model: FunctionGraphRenderModel, contentRevision: Long): FunctionGraphMarkerState? {
        val current = mutableState.value.marker ?: return null
        if (current.contentRevision != contentRevision) return null
        val curve = model.curves.firstOrNull { it.id == current.slotId } ?: return null
        if (curve.status == FunctionGraphStatus.Empty) {
            clear()
            return null
        }
        val match = curve.segments.withIndex().firstNotNullOfOrNull { (segmentIndex, segment) ->
            val points = segment.points
            if (current.x < points.first().x || current.x > points.last().x) return@firstNotNullOfOrNull null
            val edgeIndex = edgeIndexForX(points, current.x)
            val first = points[edgeIndex]
            val second = points[edgeIndex + 1]
            val span = second.x - first.x
            val t = if (span == 0.0) 0.0 else ((current.x - first.x) / span).coerceIn(0.0, 1.0)
            val y = first.y * (1.0 - t) + second.y * t
            if (!y.isFinite()) null else Triple(segmentIndex, edgeIndex, y)
        }
        val updated = current.copy(
            provisionalY = match?.third,
            segmentIndex = match?.first,
            edgeIndex = match?.second,
            dataGeneration = model.dataGeneration
        )
        mutableState.value = FunctionGraphSelectionState(updated)
        return updated
    }

    private fun sameMarker(a: FunctionGraphMarkerState, b: FunctionGraphMarkerState) =
        a.slotId == b.slotId && a.x.toBits() == b.x.toBits() && a.contentRevision == b.contentRevision

    private fun edgeIndexForX(points: List<FunctionGraphPoint>, x: Double): Int {
        var low = 0
        var high = points.lastIndex
        while (low + 1 < high) {
            val middle = (low + high) ushr 1
            if (points[middle].x <= x) low = middle else high = middle
        }
        return low.coerceIn(0, points.lastIndex - 1)
    }

    private fun distance(a: com.example.calcstudiopro.statistics.graph.model.ScreenPoint,
                         b: com.example.calcstudiopro.statistics.graph.model.ScreenPoint) =
        hypot(a.x - b.x, a.y - b.y)

    private data class CandidateCycle(
        val anchor: com.example.calcstudiopro.statistics.graph.model.ScreenPoint,
        val lastTapMillis: Long,
        val context: FunctionGraphSelectionContext,
        val candidateIds: Set<FunctionSlotId>,
        val order: List<FunctionSlotId>,
        val selectedIndex: Int
    )

    private data class Snapshot(val selection: FunctionGraphSelectionState, val cycle: CandidateCycle?)

    companion object { const val DEFAULT_CYCLE_DURATION_MILLIS = 1_000L }
}
