package com.example.calcstudiopro.functions.graph.render

import com.example.calcstudiopro.functions.domain.FunctionColor
import com.example.calcstudiopro.functions.domain.FunctionSlotId
import com.example.calcstudiopro.functions.domain.FunctionStudy
import com.example.calcstudiopro.functions.graph.FunctionGraphBatchResult
import com.example.calcstudiopro.functions.graph.FunctionGraphDiagnostics
import com.example.calcstudiopro.functions.graph.FunctionGraphSegment
import com.example.calcstudiopro.functions.graph.FunctionGraphStatus
import java.util.Collections

/** Render-facing state for one stable F1 slot. Segments remain in mathematical coordinates. */
class FunctionCurveRenderModel(
    val id: FunctionSlotId,
    val color: FunctionColor,
    val isVisible: Boolean,
    segments: List<FunctionGraphSegment>,
    val status: FunctionGraphStatus?,
    val diagnostics: FunctionGraphDiagnostics?
) {
    val segments: List<FunctionGraphSegment> = Collections.unmodifiableList(ArrayList(segments))
}

/** Immutable bridge from F1 study state and one F2.1 calculation generation to a renderer. */
class FunctionGraphRenderModel(
    val dataGeneration: Long?,
    curves: List<FunctionCurveRenderModel>
) {
    val curves: List<FunctionCurveRenderModel> = Collections.unmodifiableList(ArrayList(curves))

    init {
        require(this.curves.map { it.id } == FunctionSlotId.ordered) {
            "Render models must preserve all six F1 slots in their stable order."
        }
        require(dataGeneration == null || dataGeneration >= 0)
    }

    companion object {
        /** Joins by stable ID, never by result-list position. */
        fun from(study: FunctionStudy, batch: FunctionGraphBatchResult?): FunctionGraphRenderModel {
            val resultsById = batch?.results?.associateBy { it.id }.orEmpty()
            val curves = FunctionSlotId.ordered.map { id ->
                val slot = study.slot(id)
                val result = resultsById[id]
                val expressionIsEmpty = slot.validatedExpression == null
                FunctionCurveRenderModel(
                    id = id,
                    color = slot.color,
                    isVisible = slot.isVisible,
                    segments = if (expressionIsEmpty) emptyList() else result?.segments.orEmpty(),
                    status = when {
                        expressionIsEmpty -> FunctionGraphStatus.Empty
                        else -> result?.status
                    },
                    diagnostics = result?.diagnostics
                )
            }
            return FunctionGraphRenderModel(batch?.generation, curves)
        }
    }
}
