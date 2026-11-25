package com.example.calcstudiopro.functions.graph

import com.example.calcstudiopro.domain.functions.FunctionAngleMode
import com.example.calcstudiopro.domain.functions.CompiledMathExpression
import com.example.calcstudiopro.functions.domain.FunctionSlotId
import com.example.calcstudiopro.functions.domain.FunctionStudy
import com.example.calcstudiopro.functions.graph.interaction.FunctionGraphContentSnapshot
import com.example.calcstudiopro.functions.graph.interaction.FunctionGraphAcceptedResult
import com.example.calcstudiopro.functions.graph.FunctionGraphBatchResult
import com.example.calcstudiopro.functions.graph.render.FunctionGraphRenderModel

/** Math-only identity: drafts and presentation choices do not invalidate sampled points. */
data class FunctionGraphStudyMathKey(
    val validatedExpressions: List<String?>,
    val compiledExpressions: List<CompiledMathExpression?>,
    val parameters: Map<Char, Double>,
    val angleMode: FunctionAngleMode
)

data class AdaptedFunctionGraphContent(
    val mathKey: FunctionGraphStudyMathKey,
    val snapshot: FunctionGraphContentSnapshot
)

/** Tracks only math-affecting study inputs and issues strictly increasing graph revisions. */
class FunctionGraphStudyContentTracker(initialStudy: FunctionStudy) {
    private var key = FunctionGraphStudyAdapter.mathKey(initialStudy)
    private var currentRevision = 1L

    val revision: Long get() = currentRevision

    fun update(study: FunctionStudy): AdaptedFunctionGraphContent {
        val nextKey = FunctionGraphStudyAdapter.mathKey(study)
        if (nextKey != key) {
            check(currentRevision < Long.MAX_VALUE) { "Graph content revision exhausted." }
            key = nextKey
            currentRevision++
        }
        return FunctionGraphStudyAdapter.content(study, currentRevision)
    }
}

/** Bridges the F1 study to F2 without compiling drafts or replacing the study as source of truth. */
object FunctionGraphStudyAdapter {
    fun mathKey(study: FunctionStudy): FunctionGraphStudyMathKey = FunctionGraphStudyMathKey(
        validatedExpressions = FunctionSlotId.ordered.map { study.slot(it).validatedExpression },
        compiledExpressions = FunctionSlotId.ordered.map { study.slot(it).compiledExpression },
        parameters = study.parameters.values,
        angleMode = study.angleMode
    )

    /** All six stable slots are sent; hidden slots carry no sampling work. ASTs are reused. */
    fun content(study: FunctionStudy, revision: Long): AdaptedFunctionGraphContent {
        require(revision >= 0)
        val functions = FunctionSlotId.ordered.map { id ->
            val slot = study.slot(id)
            FunctionGraphInput(
                id = id,
                expression = slot.compiledExpression,
                isVisible = slot.isVisible,
                parameterDependencies = slot.usedParameters
            )
        }
        return AdaptedFunctionGraphContent(
            mathKey = mathKey(study),
            snapshot = FunctionGraphContentSnapshot(
                revision = revision,
                functions = functions,
                parameters = study.parameters.values,
                angleMode = study.angleMode
            )
        )
    }

    /** Presentation is rebuilt from the current study while using only a revision-compatible batch. */
    fun renderModel(
        study: FunctionStudy,
        revision: Long,
        accepted: FunctionGraphAcceptedResult?
    ): FunctionGraphRenderModel {
        val compatibleBatch: FunctionGraphBatchResult? = accepted?.takeIf { it.contentRevision == revision }?.let { result ->
            val inputs = result.request.functions.associateBy { it.id }
            val validResults = result.batch.results.filter { sample ->
                val slot = study.slot(sample.id)
                val input = inputs[sample.id]
                input?.expression === slot.compiledExpression &&
                    result.request.angleMode == study.angleMode &&
                    slot.usedParameters.all { parameter ->
                        (result.request.parameters[parameter] ?: 1.0) == study.parameters.value(parameter)
                    }
            }
            FunctionGraphBatchResult(
                result.batch.generation, validResults, result.batch.totalEvaluationCount
            )
        }
        return FunctionGraphRenderModel.from(study, compatibleBatch)
    }
}
