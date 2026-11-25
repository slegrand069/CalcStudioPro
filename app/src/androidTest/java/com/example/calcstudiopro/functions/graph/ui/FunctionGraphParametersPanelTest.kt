package com.example.calcstudiopro.functions.graph.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.mutableStateOf
import com.example.calcstudiopro.functions.domain.FunctionStudy
import com.example.calcstudiopro.functions.domain.StudyParameterUpdate
import com.example.calcstudiopro.ui.theme.CalcStudioProTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class FunctionGraphParametersPanelTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun panelHasOneTitleReadOnlyValuesAndFortyOneSliderStops() {
        val base = FunctionStudy.newWorkingSession()
        val withA = (base.withParameter('a', 50.0) as StudyParameterUpdate.Updated).study
        val study = (withA.withParameter('b', 0.4) as StudyParameterUpdate.Updated).study
        val changes = mutableListOf<Pair<Char, Double>>()

        composeRule.setContent {
            CalcStudioProTheme(darkTheme = true) {
                FunctionGraphParametersPanel(
                    study = study,
                    onParameterChange = { name, value -> changes += name to value },
                    onParameterChangeFinished = {},
                    onDismiss = {},
                    modifier = Modifier.fillMaxWidth(),
                    availableHeight = 600.dp
                )
            }
        }

        composeRule.onNodeWithText("Paramètres").assertIsDisplayed()
        composeRule.onAllNodesWithText("Mode angulaire", substring = true).assertCountEquals(0)
        composeRule.onNodeWithTag("graph-parameter-min-a").assertTextEquals("-20")
        composeRule.onNodeWithTag("graph-parameter-max-a").assertTextEquals("+20")
        composeRule.onNodeWithTag("graph-parameter-value-a").assertIsDisplayed()
        composeRule.onNodeWithText("50").assertIsDisplayed()
        composeRule.onNodeWithText("0.4").assertIsDisplayed()
        composeRule.onAllNodesWithTag("graph-parameter-a").assertCountEquals(0)

        val slider = composeRule.onNodeWithTag("graph-parameter-slider-a")
        val range = slider.fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo]
        assertEquals(-20f..20f, range.range)
        assertEquals(39, range.steps)
        assertTrue("Opening the panel must not normalize legacy values", changes.isEmpty())

        slider.performSemanticsAction(SemanticsActions.SetProgress) { setProgress ->
            assertTrue(setProgress(-20f))
        }
        composeRule.runOnIdle {
            assertEquals(listOf('a' to -20.0), changes)
        }
    }

    @Test fun sliderTrackHasNoRenderedTickDotsAndRemainsDiscretized() {
        val study = (FunctionStudy.newWorkingSession().withParameter('a', 0.0) as StudyParameterUpdate.Updated).study
        val changes = mutableListOf<Double>()
        composeRule.setContent {
            CalcStudioProTheme(darkTheme = true) {
                FunctionGraphParametersPanel(
                    study = study,
                    onParameterChange = { _, value -> changes += value },
                    onParameterChangeFinished = {},
                    onDismiss = {},
                    modifier = Modifier.fillMaxWidth(),
                    availableHeight = 600.dp
                )
            }
        }
        val slider = composeRule.onNodeWithTag("graph-parameter-slider-a")
        val image = slider.captureToImage().toPixelMap()
        val row = image.height / 2
        val thumbCenter = image.width / 2
        val trackColors = (8 until image.width - 8)
            .filter { it !in (thumbCenter - 16)..(thumbCenter + 16) }
            .map { image[it, row].value.toLong() }
            .toSet()
        // The Compose track is antialiased (the captured center scanline contains 7 blended colors
        // on the reference device). Stop markers add further isolated colors; the drawTick callback
        // is intentionally empty and the scanline remains within that measured antialias palette.
        assertTrue("Unexpected isolated stop-marker colors on the track: $trackColors", trackColors.size <= 7)

        val range = slider.fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo]
        assertEquals(-20f..20f, range.range)
        assertEquals(39, range.steps)
        slider.performSemanticsAction(SemanticsActions.SetProgress) { setProgress -> assertTrue(setProgress(2.4f)) }
        composeRule.runOnIdle { assertEquals(listOf(2.0), changes) }
    }

    @Test fun parameterPanelUsesReadableSurfacesInBothThemes() {
        val parameter = (FunctionStudy.newWorkingSession().withParameter('a', 7.0) as StudyParameterUpdate.Updated).study
        val dark = mutableStateOf(true)
        composeRule.setContent {
            CalcStudioProTheme(darkTheme = dark.value) {
                FunctionGraphParametersPanel(
                    study = parameter,
                    onParameterChange = { _, _ -> },
                    onParameterChangeFinished = {},
                    onDismiss = {},
                    modifier = Modifier.fillMaxWidth(),
                    availableHeight = 600.dp
                )
            }
        }
        val darkPixel = composeRule.onNodeWithTag("function-graph-parameter-panel")
            .captureToImage().toPixelMap()[20, 20]
        assertTrue(darkPixel.red < 0.2f && darkPixel.green < 0.25f && darkPixel.blue < 0.4f)

        composeRule.runOnIdle { dark.value = false }
        composeRule.waitForIdle()
        val lightPixel = composeRule.onNodeWithTag("function-graph-parameter-panel")
            .captureToImage().toPixelMap()[20, 20]
        assertTrue(lightPixel.red > 0.8f && lightPixel.green > 0.8f && lightPixel.blue > 0.8f)
    }
}
