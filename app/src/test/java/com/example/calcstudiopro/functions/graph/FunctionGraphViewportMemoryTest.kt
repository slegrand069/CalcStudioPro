package com.example.calcstudiopro.functions.graph

import com.example.calcstudiopro.functions.domain.FunctionStudy
import com.example.calcstudiopro.functions.domain.FunctionStudyId
import com.example.calcstudiopro.functions.graph.interaction.FunctionGraphViewportController
import com.example.calcstudiopro.statistics.graph.model.AxisRange
import com.example.calcstudiopro.statistics.graph.model.GraphViewport
import com.example.calcstudiopro.statistics.graph.model.PlotRect
import org.junit.Assert.assertEquals
import org.junit.Test

class FunctionGraphViewportMemoryTest {
    @Test fun viewportBoundsRemainAttachedToTheStudyAcrossLayoutChanges() {
        val memory = FunctionGraphViewportMemory()
        val study = FunctionStudy.newWorkingSession(FunctionStudyId("orientation-test"))
        val requested = GraphViewport(AxisRange(-37.5, 42.5), AxisRange(-120.0, 80.0))

        memory.save(study.id, requested)

        assertEquals(requested, memory.get(study.id))
        val afterRotation = FunctionGraphViewportController.resized(
            requested,
            PlotRect(56.0, 12.0, 330.0, 520.0),
            PlotRect(56.0, 12.0, 780.0, 280.0)
        )
        assertEquals(requested, afterRotation)
        memory.save(study.id, afterRotation)
        assertEquals(requested, memory.get(study.id))
    }
}
