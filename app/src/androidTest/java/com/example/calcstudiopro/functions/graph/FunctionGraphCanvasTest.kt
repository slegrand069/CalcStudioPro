package com.example.calcstudiopro.functions.graph

import androidx.compose.foundation.layout.size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.example.calcstudiopro.functions.domain.FunctionColor
import com.example.calcstudiopro.functions.domain.FunctionColorPalette
import com.example.calcstudiopro.functions.domain.FunctionSlotId
import com.example.calcstudiopro.functions.graph.render.FunctionCurveRenderModel
import com.example.calcstudiopro.functions.graph.render.FunctionGraphRenderModel
import com.example.calcstudiopro.functions.graph.render.FunctionGridMode
import com.example.calcstudiopro.functions.graph.ui.FunctionGraphCanvas
import com.example.calcstudiopro.functions.graph.ui.FunctionGraphMarkerPresentation
import com.example.calcstudiopro.statistics.graph.model.AxisRange
import com.example.calcstudiopro.statistics.graph.model.GraphViewport
import com.example.calcstudiopro.ui.theme.CalcStudioProTheme
import com.example.calcstudiopro.ui.theme.BackgroundDark
import com.example.calcstudiopro.ui.theme.BackgroundLight
import com.example.calcstudiopro.ui.theme.OnSurfaceDark
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import kotlin.math.roundToInt

class FunctionGraphCanvasTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun canvasDrawsSixStableSlotsOnLightTheme() {
        composeRule.setContent {
            CalcStudioProTheme(darkTheme = false) {
                FunctionGraphCanvas(
                    model = emptySixSlotModel(),
                    viewport = viewport,
                    modifier = androidx.compose.ui.Modifier.size(320.dp, 220.dp),
                    testTag = "function-canvas-test"
                )
            }
        }
        val node = composeRule.onNodeWithTag("function-canvas-test")
        node.assertIsDisplayed()
        val image = node.captureToImage()
        val pixels = image.toPixelMap()
        assertTrue(image.width > 0 && image.height > 0)
        assertEquals(BackgroundLight.toArgb(), pixels[0, 0].toArgb())
    }

    @Test fun canvasDrawsSixActiveVisibleCurvesWithTheirSavedColors() {
        composeRule.setContent {
            CalcStudioProTheme(darkTheme = false) {
                FunctionGraphCanvas(
                    model = sixVisibleCurvesModel(),
                    viewport = viewport,
                    gridMode = FunctionGridMode.NONE,
                    modifier = androidx.compose.ui.Modifier.size(320.dp, 220.dp),
                    testTag = "six-visible-curves"
                )
            }
        }
        val image = composeRule.onNodeWithTag("six-visible-curves").captureToImage()
        val pixels = image.toPixelMap()
        val density = image.width / 320f
        val centerX = (160f * density).roundToInt()
        val plotHeight = 170f * density
        val yValues = listOf(2.8, 1.8, 0.8, -0.8, -1.8, -2.8)
        val ids = FunctionSlotId.ordered
        ids.zip(yValues).forEach { (id, y) ->
            val screenY = (12.0 * density + (1.0 - (y + 4.0) / 8.0) * plotHeight).roundToInt()
            val expected = FunctionColorPalette.defaultFor(id).argb.toInt()
            val found = (screenY - 1..screenY + 1).any { py ->
                (centerX - 1..centerX + 1).any { px -> pixels[px, py].toArgb() == expected }
            }
            assertTrue("Expected visible $id curve color #${expected.toUInt().toString(16)}", found)
        }
    }

    @Test fun canvasDrawsTheDarkThemeBackgroundAndUsesItForContrastPreparation() {
        composeRule.setContent {
            CalcStudioProTheme(darkTheme = true) {
                FunctionGraphCanvas(
                    model = emptySixSlotModel(FunctionColor.NAVY),
                    viewport = viewport,
                    gridMode = FunctionGridMode.NONE,
                    modifier = androidx.compose.ui.Modifier.size(320.dp, 220.dp),
                    testTag = "function-canvas-test"
                )
            }
        }
        val image = composeRule.onNodeWithTag("function-canvas-test").captureToImage()
        val pixels = image.toPixelMap()
        assertEquals(BackgroundDark.toArgb(), pixels[0, 0].toArgb())
        val density = image.width / 320f
        val axisX = ((56f + 126f) * density).roundToInt()
        val axisY = ((12f + 85f) * density).roundToInt()
        assertTrue("The zero axis must use the dark theme foreground", (-2..2).any { dy ->
            (-2..2).any { dx -> pixels[axisX + dx, axisY + dy].toArgb() == OnSurfaceDark.toArgb() }
        })
    }

    @Test fun canvasDrawsTheExactSavedPaletteColorInDarkMode() {
        composeRule.setContent {
            CalcStudioProTheme(darkTheme = true) {
                FunctionGraphCanvas(
                    model = singleVisibleCurveModel(FunctionColor.SUN_YELLOW, y = 0.75),
                    viewport = viewport,
                    gridMode = FunctionGridMode.NONE,
                    modifier = androidx.compose.ui.Modifier.size(320.dp, 220.dp),
                    testTag = "saved-color-curve"
                )
            }
        }
        val image = composeRule.onNodeWithTag("saved-color-curve").captureToImage()
        val pixels = image.toPixelMap()
        val density = image.width / 320f
        val x = (180f * density).roundToInt()
        val y = (12f + (1f - (0.75f + 4f) / 8f) * 170f) * density
        assertTrue("The saved palette color must be rendered unchanged", (-2..2).any { dy ->
            (-2..2).any { dx -> pixels[x + dx, y.roundToInt() + dy].toArgb() == FunctionColor.SUN_YELLOW.argb.toInt() }
        })
    }

    @Test fun canvasActuallyDrawsContrastHaloUnderSavedNavyCurve() {
        composeRule.setContent {
            CalcStudioProTheme(darkTheme = true) {
                FunctionGraphCanvas(
                    model = singleVisibleCurveModel(FunctionColor.NAVY, y = 0.75),
                    viewport = viewport,
                    gridMode = FunctionGridMode.NONE,
                    modifier = androidx.compose.ui.Modifier.size(320.dp, 220.dp),
                    testTag = "contrast-halo"
                )
            }
        }
        val image = composeRule.onNodeWithTag("contrast-halo").captureToImage()
        val pixels = image.toPixelMap()
        val density = image.width / 320f
        val centerX = (180f * density).roundToInt()
        val lineY = (12f * density + (1f - (0.75f + 4f) / 8f) * (170f * density)).roundToInt()
        val neighborhood = (-4..4).flatMap { dy -> (-2..2).map { dx ->
            pixels[centerX + dx, lineY + dy].toArgb()
        } }
        assertTrue("Saved navy curve should be visible", neighborhood.contains(FunctionColor.NAVY.argb.toInt()))
        assertTrue("White contrast outline should be rendered", neighborhood.contains(Color.White.toArgb()))
    }

    @Test fun selectedMarkerDrawsCalloutAndExposesAccessibleCoordinates() {
        composeRule.setContent {
            CalcStudioProTheme(darkTheme = true) {
                FunctionGraphCanvas(
                    model = singleVisibleCurveModel(FunctionColor.CYAN, y = 0.75),
                    viewport = viewport,
                    gridMode = FunctionGridMode.NONE,
                    marker = FunctionGraphMarkerPresentation(FunctionSlotId.F1, 0.0, 0.75,
                        "f1 • provisoire\nx = 0\ny ≈ 0,75"),
                    modifier = androidx.compose.ui.Modifier.size(320.dp, 220.dp),
                    testTag = "marker-presentation"
                )
            }
        }
        val node = composeRule.onNodeWithTag("marker-presentation")
        val semantics = node.fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.StateDescription]
        assertTrue(semantics.contains("sélection f1") && semantics.contains("provisoire") && semantics.contains("y ≈ 0,75"))
        val pixels = node.captureToImage().toPixelMap()
        val density = pixels.height / 220f
        val plotCenterX = (56f + (320f - 56f - 12f) / 2f) * density
        val plotY = (12f + (1f - (0.75f + 4f) / 8f) * 170f) * density
        val ringRadius = 7f * density
        val markerRingPixels = (-ringRadius.toInt()..ringRadius.toInt()).any { dy ->
            (-ringRadius.toInt()..ringRadius.toInt()).any { dx ->
                val distance = kotlin.math.hypot(dx.toFloat(), dy.toFloat())
                val x = (plotCenterX + dx).roundToInt()
                val y = (plotY + dy).roundToInt()
                x in 0 until pixels.width && y in 0 until pixels.height &&
                    distance in (4f * density)..(8f * density) &&
                    pixels[x, y].let { it.red > 0.98f && it.green > 0.98f && it.blue > 0.98f }
            }
        }
        assertTrue("White marker ring should be drawn around ($plotCenterX,$plotY)", markerRingPixels)
    }

    @Test fun curvesAreClippedAndDisjointSegmentsRemainSeparate() {
        val curve = FunctionCurveRenderModel(
            id = FunctionSlotId.F1,
            color = FunctionColor.RASPBERRY,
            isVisible = true,
            segments = listOf(
                FunctionGraphSegment(listOf(FunctionGraphPoint(1.0, 1.05), FunctionGraphPoint(3.0, 1.05))),
                FunctionGraphSegment(listOf(FunctionGraphPoint(1.0, 0.4), FunctionGraphPoint(1.85, 0.4))),
                FunctionGraphSegment(listOf(FunctionGraphPoint(2.15, 0.4), FunctionGraphPoint(3.0, 0.4)))
            ),
            status = FunctionGraphStatus.Success,
            diagnostics = null
        )
        val model = FunctionGraphRenderModel(1, FunctionSlotId.ordered.map { id ->
            if (id == FunctionSlotId.F1) curve else FunctionCurveRenderModel(
                id, FunctionColorPalette.defaultFor(id), false, emptyList(), FunctionGraphStatus.Empty, null
            )
        })
        composeRule.setContent {
            CalcStudioProTheme(darkTheme = false) {
                FunctionGraphCanvas(
                    model = model,
                    viewport = GraphViewport(AxisRange(1.0, 3.0), AxisRange(0.0, 1.0)),
                    gridMode = FunctionGridMode.NONE,
                    modifier = androidx.compose.ui.Modifier.size(320.dp, 220.dp),
                    testTag = "function-canvas-test"
                )
            }
        }
        val image = composeRule.onNodeWithTag("function-canvas-test").captureToImage()
        val pixels = image.toPixelMap()
        val background = BackgroundLight.toArgb()
        val curveColor = FunctionColor.RASPBERRY.argb.toInt()
        val centerX = image.width / 2
        val density = image.height / 220f
        // The first path lies above the plot and must be clipped to the plot rectangle.
        val clippedPathY = (7.75f * density).roundToInt().coerceIn(0, image.height - 1)
        assertEquals(background, pixels[centerX, clippedPathY].toArgb())
        // The two lower paths leave a visible gap around the middle of the viewport.
        val plotHeight = 170f * density
        val gapY = (12f * density + 0.3f * plotHeight).roundToInt().coerceIn(0, image.height - 1)
        assertNotEquals(curveColor, pixels[centerX, gapY].toArgb())
    }

    private fun emptySixSlotModel(color: FunctionColor = FunctionColor.BLUE) = FunctionGraphRenderModel(
        dataGeneration = null,
        curves = FunctionSlotId.ordered.map { id ->
            FunctionCurveRenderModel(
                id = id,
                color = if (id == FunctionSlotId.F1) color else FunctionColorPalette.defaultFor(id),
                isVisible = true,
                segments = emptyList(),
                status = FunctionGraphStatus.Empty,
                diagnostics = null
            )
        }
    )

    private fun sixVisibleCurvesModel() = FunctionGraphRenderModel(
        dataGeneration = 7,
        curves = FunctionSlotId.ordered.mapIndexed { index, id ->
            val y = listOf(2.8, 1.8, 0.8, -0.8, -1.8, -2.8)[index]
            FunctionCurveRenderModel(
                id = id,
                color = FunctionColorPalette.defaultFor(id),
                isVisible = true,
                segments = listOf(FunctionGraphSegment(listOf(
                    FunctionGraphPoint(-4.0, y), FunctionGraphPoint(4.0, y)
                ))),
                status = FunctionGraphStatus.Success,
                diagnostics = null
            )
        }
    )

    private fun singleVisibleCurveModel(color: FunctionColor, y: Double) = FunctionGraphRenderModel(
        dataGeneration = 8,
        curves = FunctionSlotId.ordered.map { id ->
            FunctionCurveRenderModel(
                id = id,
                color = if (id == FunctionSlotId.F1) color else FunctionColorPalette.defaultFor(id),
                isVisible = id == FunctionSlotId.F1,
                segments = if (id == FunctionSlotId.F1) listOf(FunctionGraphSegment(listOf(
                    FunctionGraphPoint(-4.0, y), FunctionGraphPoint(4.0, y)
                ))) else emptyList(),
                status = if (id == FunctionSlotId.F1) FunctionGraphStatus.Success else FunctionGraphStatus.Empty,
                diagnostics = null
            )
        }
    )

    private companion object {
        val viewport = GraphViewport(AxisRange(-4.0, 4.0), AxisRange(-4.0, 4.0))
    }
}
