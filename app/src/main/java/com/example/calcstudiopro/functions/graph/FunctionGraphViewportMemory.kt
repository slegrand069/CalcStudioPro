package com.example.calcstudiopro.functions.graph

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.calcstudiopro.domain.functions.ExpressionEvaluation
import com.example.calcstudiopro.domain.functions.MathExpressionError
import com.example.calcstudiopro.functions.domain.FunctionStudy
import com.example.calcstudiopro.functions.domain.FunctionStudyId
import com.example.calcstudiopro.functions.graph.interaction.FunctionGraphCalculationCoordinator
import com.example.calcstudiopro.functions.graph.interaction.FunctionGraphMarkerEvaluation
import com.example.calcstudiopro.functions.graph.interaction.FunctionGraphPointEvaluationCoordinator
import com.example.calcstudiopro.functions.graph.interaction.FunctionGraphSelectionController
import com.example.calcstudiopro.functions.graph.interaction.FunctionGraphViewportController
import com.example.calcstudiopro.statistics.graph.model.GraphViewport
import java.util.LinkedHashMap

/** One study's transient F2 state. F1 remains the only source of expressions and presentation. */
class FunctionGraphStudySession internal constructor(
    study: FunctionStudy,
    initialViewport: GraphViewport,
    plotWidthPixels: Int,
    plotHeightPixels: Int,
    hitRadiusPixels: Double,
    scope: kotlinx.coroutines.CoroutineScope
) {
    private val tracker = FunctionGraphStudyContentTracker(study)
    private val initial = tracker.update(study)
    val coordinator = FunctionGraphCalculationCoordinator(
        scope = scope,
        initialContent = initial.snapshot,
        initialViewport = initialViewport,
        initialPlotWidthPixels = plotWidthPixels,
        initialPlotHeightPixels = plotHeightPixels,
        calculator = FunctionGraphCalculator(scope)
    )
    val selection = FunctionGraphSelectionController(hitRadiusPixels)
    val evaluationCoordinator = FunctionGraphPointEvaluationCoordinator(scope, onResult = { request, result ->
        val evaluation = when (result) {
            is ExpressionEvaluation.Success -> FunctionGraphMarkerEvaluation.Confirmed(result.value)
            is ExpressionEvaluation.Failure -> if (result.error is MathExpressionError.NonFiniteResult)
                FunctionGraphMarkerEvaluation.Undefined else FunctionGraphMarkerEvaluation.Error(result.error)
        }
        selection.applyEvaluation(request.requestId, request.slotId, request.x, request.contentRevision, evaluation)
    })

    fun updateStudy(study: FunctionStudy): AdaptedFunctionGraphContent = tracker.update(study)
    val contentRevision: Long get() = tracker.revision

    fun close() {
        coordinator.close()
        evaluationCoordinator.close()
    }
}

/** Retains small per-study interactive sessions across A1/E1/graph route transitions. */
class FunctionGraphViewportMemory : ViewModel() {
    private val viewports = LinkedHashMap<FunctionStudyId, GraphViewport>(16, 0.75f, true)
    private val sessions = LinkedHashMap<FunctionStudyId, FunctionGraphStudySession>(8, 0.75f, true)

    @Synchronized
    fun get(studyId: FunctionStudyId): GraphViewport =
        viewports[studyId] ?: FunctionGraphViewportController.INITIAL

    @Synchronized
    fun save(studyId: FunctionStudyId, viewport: GraphViewport) {
        viewports[studyId] = viewport
    }

    @Synchronized
    fun session(
        study: FunctionStudy,
        widthPixels: Int,
        heightPixels: Int,
        hitRadiusPixels: Double
    ): FunctionGraphStudySession {
        sessions[study.id]?.let { return it }
        val created = FunctionGraphStudySession(
            study = study,
            initialViewport = get(study.id),
            plotWidthPixels = widthPixels,
            plotHeightPixels = heightPixels,
            hitRadiusPixels = hitRadiusPixels,
            scope = viewModelScope
        )
        sessions[study.id] = created
        while (sessions.size > MAX_RETAINED_SESSIONS) {
            val oldest = sessions.keys.firstOrNull() ?: break
            sessions.remove(oldest)?.close()
        }
        return created
    }

    override fun onCleared() {
        synchronized(this) {
            sessions.values.forEach(FunctionGraphStudySession::close)
            sessions.clear()
        }
    }

    private companion object {
        const val MAX_RETAINED_SESSIONS = 4
    }
}
