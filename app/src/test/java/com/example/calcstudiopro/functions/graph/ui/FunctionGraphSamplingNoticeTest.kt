package com.example.calcstudiopro.functions.graph.ui

import com.example.calcstudiopro.functions.domain.FunctionColorPalette
import com.example.calcstudiopro.functions.domain.FunctionSlotId
import com.example.calcstudiopro.functions.graph.FunctionGraphBudgetLimit
import com.example.calcstudiopro.functions.graph.FunctionGraphStatus
import com.example.calcstudiopro.functions.graph.render.FunctionCurveRenderModel
import com.example.calcstudiopro.functions.graph.render.FunctionGraphRenderModel
import org.junit.Assert.assertEquals
import org.junit.Test

class FunctionGraphSamplingNoticeTest {
    @Test fun budgetAndPrecisionCausesAreNotExposedInTheUserMessage() {
        val model = FunctionGraphRenderModel(1, FunctionSlotId.ordered.map { id ->
            FunctionCurveRenderModel(
                id = id,
                color = FunctionColorPalette.defaultFor(id),
                isVisible = true,
                segments = emptyList(),
                status = when (id) {
                    FunctionSlotId.F2 -> FunctionGraphStatus.BudgetExceeded(FunctionGraphBudgetLimit.FUNCTION_EVALUATIONS)
                    FunctionSlotId.F4 -> FunctionGraphStatus.Partial(FunctionGraphBudgetLimit.SUBDIVISION_DEPTH)
                    else -> FunctionGraphStatus.Empty
                },
                diagnostics = null
            )
        })

        assertEquals("f2 : tracé partiel · f4 : tracé partiel", functionGraphSamplingNotice(model))
    }
}
