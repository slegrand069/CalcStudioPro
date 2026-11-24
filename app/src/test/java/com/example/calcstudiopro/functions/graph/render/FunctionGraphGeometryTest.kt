package com.example.calcstudiopro.functions.graph.render

import com.example.calcstudiopro.functions.domain.FunctionColor
import com.example.calcstudiopro.functions.domain.FunctionSlotId
import com.example.calcstudiopro.functions.domain.FunctionStudy
import com.example.calcstudiopro.functions.graph.FunctionGraphPoint
import com.example.calcstudiopro.functions.graph.FunctionGraphSegment
import com.example.calcstudiopro.statistics.graph.model.AxisRange
import com.example.calcstudiopro.statistics.graph.model.GraphViewport
import com.example.calcstudiopro.statistics.graph.model.PlotRect
import com.example.calcstudiopro.statistics.graph.model.ScreenPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FunctionGraphGeometryTest {
    @Test fun axesAppearOnlyAtMathematicalZeroInsideEachIndependentRange() {
        val geometry = build(viewport = GraphViewport(AxisRange(-2.0, 6.0), AxisRange(-4.0, 4.0)))
        assertNotNull(geometry.verticalAxis)
        assertNotNull(geometry.horizontalAxis)
        assertEquals(125.0, geometry.verticalAxis!!.start.x, 1e-12)
        assertEquals(170.0, geometry.horizontalAxis!!.start.y, 1e-12)

        val offWindow = build(viewport = GraphViewport(AxisRange(1.0, 4.0), AxisRange(-8.0, -2.0)))
        assertNull(offWindow.verticalAxis)
        assertNull(offWindow.horizontalAxis)
    }

    @Test fun xAndYTransformUseSeparateRangesAndKeepSegmentBreaks() {
        val model = modelWithCurve()
        val geometry = build(
            model = model,
            viewport = GraphViewport(AxisRange(-2.0, 2.0), AxisRange(-10.0, 10.0)),
            plot = PlotRect(10.0, 20.0, 400.0, 200.0)
        )
        val curve = geometry.curves.first()
        assertEquals(2, curve.segments.size)
        assertEquals(10.0, curve.segments.first().first().x, 1e-12)
        assertEquals(140.0, curve.segments.first().first().y, 1e-12)
        assertEquals(12L, geometry.dataGeneration)
        assertEquals(4L, geometry.viewportGeneration)
    }

    @Test fun gridModesProduceNoMajorOrMinorLinesAsRequested() {
        val none = build(grid = FunctionGridMode.NONE)
        assertTrue(none.majorGrid.isEmpty())
        assertTrue(none.minorGrid.isEmpty())
        assertTrue(none.xTicks.isNotEmpty() && none.yTicks.isNotEmpty())

        val major = build(grid = FunctionGridMode.MAJOR)
        assertTrue(major.majorGrid.isNotEmpty())
        assertTrue(major.minorGrid.isEmpty())

        val detailed = build(grid = FunctionGridMode.MAJOR_AND_MINOR)
        assertEquals(major.majorGrid, detailed.majorGrid)
        assertTrue(detailed.minorGrid.isNotEmpty())
        assertTrue(detailed.minorGrid.size <= 234)
    }

    @Test fun extremeRangesProduceBoundedFiniteTickAndGridGeometry() {
        val geometry = build(
            viewport = GraphViewport(
                AxisRange(-Double.MAX_VALUE, Double.MAX_VALUE),
                AxisRange(-1e-300, 1e-300)
            ),
            grid = FunctionGridMode.MAJOR_AND_MINOR
        )
        assertTrue(geometry.xTicks.size <= 40)
        assertTrue(geometry.yTicks.size <= 40)
        assertTrue((geometry.xTicks + geometry.yTicks).all { it.value.isFinite() && it.position.x.isFinite() && it.position.y.isFinite() })
        assertTrue(geometry.majorGrid.size <= 80)
        assertTrue(geometry.minorGrid.size <= 234)
        assertTrue((geometry.xTicks + geometry.yTicks).all { it.label.isNotBlank() })
    }

    @Test fun geometryCanBeRebuiltForPortraitAndLandscapeWithoutChangingMathData() {
        val model = modelWithCurve()
        val pointsBefore = model.curves.first().segments.first().points.toList()
        val portrait = build(model, plot = PlotRect(0.0, 0.0, 300.0, 600.0), viewportGeneration = 10)
        val landscape = build(model, plot = PlotRect(0.0, 0.0, 700.0, 300.0), viewportGeneration = 11)
        assertNotEquals(portrait.curves.first().segments.first().map { it.x }, landscape.curves.first().segments.first().map { it.x })
        assertEquals(12L, portrait.dataGeneration)
        assertEquals(12L, landscape.dataGeneration)
        assertEquals(10L, portrait.viewportGeneration)
        assertEquals(11L, landscape.viewportGeneration)
        assertEquals(pointsBefore, model.curves.first().segments.first().points)
    }

    @Test fun extremeOffscreenValuesNeverProduceCanvasCoordinates() {
        val viewport = GraphViewport(AxisRange(-1.0, 1.0), AxisRange(-1.0, 1.0))
        for (y in listOf(1e307, Double.MAX_VALUE, -Double.MAX_VALUE)) {
            val geometry = build(model = modelWithSegments(listOf(listOf(
                FunctionGraphPoint(-0.5, y), FunctionGraphPoint(0.5, y)
            ))), viewport = viewport)
            assertTrue(geometry.curves.first().segments.isEmpty())
        }
    }

    @Test fun distantFiniteEndpointsAreClippedToVisiblePlot() {
        val geometry = build(
            model = modelWithSegments(listOf(listOf(
                FunctionGraphPoint(-1e307, -1e307), FunctionGraphPoint(1e307, 1e307)
            ))),
            viewport = GraphViewport(AxisRange(-1.0, 1.0), AxisRange(-1.0, 1.0)),
            plot = PlotRect(10.0, 20.0, 300.0, 180.0)
        )
        val path = geometry.curves.first().segments.single()
        assertEquals(ScreenPoint(10.0, 200.0), path.first())
        assertEquals(ScreenPoint(310.0, 20.0), path.last())
        assertFiniteCanvasPoints(geometry)
    }

    @Test fun whollyOffscreenSegmentsAreOmittedAndDisjointBranchesStaySeparate() {
        val geometry = build(
            model = modelWithSegments(listOf(
                listOf(FunctionGraphPoint(-0.5, 1e307), FunctionGraphPoint(0.5, 1e307)),
                listOf(FunctionGraphPoint(-1e307, -0.5), FunctionGraphPoint(1e307, 0.5)),
                listOf(FunctionGraphPoint(-1e307, 0.8), FunctionGraphPoint(1e307, 0.8))
            )),
            viewport = GraphViewport(AxisRange(-1.0, 1.0), AxisRange(-1.0, 1.0))
        )
        val paths = geometry.curves.first().segments
        assertEquals(2, paths.size)
        assertFiniteCanvasPoints(geometry)
        assertTrue(paths.flatten().all { it.x in 25.0..425.0 && it.y in 30.0..310.0 })
    }

    @Test fun finiteDoubleCoordinatesOutsideFloatRangeAreNeverEmitted() {
        val geometry = build(
            model = modelWithSegments(listOf(listOf(
                FunctionGraphPoint(0.0, Double.MAX_VALUE), FunctionGraphPoint(0.5, Double.MAX_VALUE)
            ))),
            viewport = GraphViewport(AxisRange(-1.0, 1.0), AxisRange(-1.0, 1.0))
        )
        assertFiniteCanvasPoints(geometry)
        geometry.curves.flatMap { it.segments }.flatten().forEach {
            assertTrue(it.x.toFloat().isFinite())
            assertTrue(it.y.toFloat().isFinite())
        }
    }

    @Test fun contrastOutlineIsDeterministicAndDoesNotChangeSavedColor() {
        val navy = FunctionColor.NAVY.argb
        val darkBackground = 0xFF07111DL
        val lightBackground = 0xFFFFFFFFL
        val first = FunctionCurveContrastPolicy.outlineFor(navy, darkBackground)
        assertNotNull(first)
        assertEquals(first, FunctionCurveContrastPolicy.outlineFor(navy, darkBackground))
        assertNull(FunctionCurveContrastPolicy.outlineFor(FunctionColor.SUN_YELLOW.argb, darkBackground))
        assertNull(FunctionCurveContrastPolicy.outlineFor(FunctionColor.BLUE.argb, lightBackground))
        val geometry = build(modelWithCurve(color = FunctionColor.NAVY), background = darkBackground)
        assertEquals(navy, geometry.curves.first().colorArgb)
        assertEquals(first, geometry.curves.first().outlineArgb)
        assertEquals(FunctionColor.NAVY, modelWithCurve(color = FunctionColor.NAVY).curves.first().color)
    }

    private fun build(
        model: FunctionGraphRenderModel = modelWithCurve(),
        viewport: GraphViewport = GraphViewport(AxisRange(-4.0, 4.0), AxisRange(-4.0, 4.0)),
        plot: PlotRect = PlotRect(25.0, 30.0, 400.0, 280.0),
        grid: FunctionGridMode = FunctionGridMode.MAJOR_AND_MINOR,
        background: Long = 0xFFFFFFFFL,
        viewportGeneration: Long = 4
    ) = FunctionGraphGeometryBuilder.build(
        FunctionGraphGeometryRequest(model, viewport, plot, grid, background, viewportGeneration)
    )

    private fun modelWithCurve(color: FunctionColor = FunctionColor.BLUE): FunctionGraphRenderModel {
        val study = FunctionStudy.newWorkingSession()
            .withColor(FunctionSlotId.F1, color)
            .withVisibility(FunctionSlotId.F1, true)
        val points = FunctionGraphSegment(listOf(
            FunctionGraphPoint(-2.0, -2.0), FunctionGraphPoint(0.0, 0.0)
        ))
        val second = FunctionGraphSegment(listOf(
            FunctionGraphPoint(0.1, 0.1), FunctionGraphPoint(2.0, 2.0)
        ))
        val curves = FunctionSlotId.ordered.map { id ->
            FunctionCurveRenderModel(id, study.slot(id).color, study.slot(id).isVisible,
                if (id == FunctionSlotId.F1) listOf(points, second) else emptyList(), null, null)
        }
        return FunctionGraphRenderModel(12, curves)
    }

    private fun modelWithSegments(segments: List<List<FunctionGraphPoint>>): FunctionGraphRenderModel {
        val study = FunctionStudy.newWorkingSession()
        val f1 = FunctionCurveRenderModel(
            FunctionSlotId.F1, study.slot(FunctionSlotId.F1).color, true,
            segments.map(::FunctionGraphSegment), null, null
        )
        return FunctionGraphRenderModel(12, FunctionSlotId.ordered.map { id ->
            if (id == FunctionSlotId.F1) f1 else FunctionCurveRenderModel(
                id, study.slot(id).color, false, emptyList(), null, null
            )
        })
    }

    private fun assertFiniteCanvasPoints(geometry: FunctionGraphGeometry) {
        geometry.curves.flatMap { it.segments }.flatten().forEach { point ->
            assertTrue(point.x.isFinite() && point.y.isFinite())
            assertTrue(point.x.toFloat().isFinite() && point.y.toFloat().isFinite())
        }
    }
}
