package com.example.calcstudiopro.functions

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import com.example.calcstudiopro.MainActivity
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import com.example.calcstudiopro.functions.domain.FunctionColor
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class FunctionStudyNavigationTest {
    @get:Rule val composeRule = createAndroidComposeRule<MainActivity>()

    @Test fun modeSelectorOpensTheSixFunctionList() {
        composeRule.onNodeWithTag("calculator-key-MODE").performClick()
        composeRule.onNodeWithTag("mode-option-functions").assertIsDisplayed().performClick()
        composeRule.onNodeWithText("Fonctions").assertIsDisplayed()
        composeRule.onNodeWithTag("functions-session-title").assertIsDisplayed()
        (1..6).forEach { index ->
            composeRule.onNodeWithTag("function-slot-f$index").assertIsDisplayed()
        }
    }

    @Test fun tappingAFunctionRowOpensItsFullScreenEditor() {
        composeRule.onNodeWithTag("calculator-key-MODE").performClick()
        composeRule.onNodeWithTag("mode-option-functions").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("function-slot-f2").performClick()
        composeRule.onNodeWithTag("editor-expression-area").assertIsDisplayed()
        composeRule.onNodeWithText("Édition de f₂(x)").assertIsDisplayed()
    }

    @Test fun studiesEntryOpensS1WithoutChangingTheSixFunctionRows() {
        composeRule.onNodeWithTag("calculator-key-MODE").performClick()
        composeRule.onNodeWithTag("mode-option-functions").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("functions-studies").assertIsDisplayed().performClick()

        composeRule.onNodeWithTag("studies-back").assertIsDisplayed()
        composeRule.onNodeWithTag("study-new").assertIsDisplayed()
        composeRule.onNodeWithTag("study-save").assertIsDisplayed()
        composeRule.onNodeWithTag("study-save-as").assertIsDisplayed()
        composeRule.onNodeWithTag("study-open").assertIsDisplayed()
        composeRule.onNodeWithTag("study-current-card").assertIsDisplayed()
        composeRule.onNodeWithTag("study-recovery-card").assertIsDisplayed()

        composeRule.onNodeWithTag("studies-back").performClick()
        (1..6).forEach { index -> composeRule.onNodeWithTag("function-slot-f$index").assertIsDisplayed() }
    }

    @Test fun graphRouteTogglesAngleModeImmediatelyAndReturnsToTheStudy() {
        composeRule.onNodeWithTag("calculator-key-MODE").performClick()
        composeRule.onNodeWithTag("mode-option-functions").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("functions-graph").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("function-graph-production-canvas").assertIsDisplayed()

        composeRule.onNodeWithTag("function-graph-angle-toggle").assertIsDisplayed().performClick()
        composeRule.onNodeWithText("DEG").assertIsDisplayed()
        composeRule.onNodeWithTag("function-graph-back").performClick()
        composeRule.onNodeWithTag("functions-session-status").assertIsDisplayed()
        composeRule.onNodeWithText("Sauvegarde automatique • DEG").assertIsDisplayed()
    }

    @Test fun orientationIsPortraitByDefaultAndLandscapeIsOnlyVoluntaryFromGraph() {
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, composeRule.activity.requestedOrientation)
        composeRule.onNodeWithTag("calculator-key-MODE").performClick()
        composeRule.onNodeWithTag("mode-option-functions").performClick()
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, composeRule.activity.requestedOrientation)
        composeRule.onNodeWithTag("functions-graph").performClick()

        composeRule.onNodeWithTag("function-graph-orientation").assertIsDisplayed().performClick()
        composeRule.waitUntil(8_000) {
            composeRule.activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        }
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE, composeRule.activity.requestedOrientation)
        composeRule.onNodeWithTag("function-graph-back").performClick()
        composeRule.waitUntil(8_000) {
            composeRule.activity.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
        }
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, composeRule.activity.requestedOrientation)
        (1..6).forEach { composeRule.onNodeWithTag("function-slot-f$it").assertIsDisplayed() }
    }

    @Test fun graphPanelsExposeAllSlotsAndLiveParameterControls() {
        composeRule.onNodeWithTag("calculator-key-MODE").performClick()
        composeRule.onNodeWithTag("mode-option-functions").performClick()
        composeRule.onNodeWithTag("functions-graph").performClick()
        composeRule.onNodeWithTag("function-graph-functions-panel").performClick()
        (1..6).forEach { composeRule.onNodeWithTag("function-graph-slot-f$it").assertIsDisplayed() }
        composeRule.onNodeWithTag("function-graph-functions-close").performClick()

        composeRule.onNodeWithTag("function-graph-parameters-panel").performClick()
        composeRule.onNodeWithTag("function-graph-parameter-panel").assertIsDisplayed()
        composeRule.onNodeWithTag("function-graph-parameter-panel-close").performClick()
    }

    @Test fun graphBottomCommandsKeepTheirFullSingleLineLabels() {
        composeRule.onNodeWithTag("calculator-key-MODE").performClick()
        composeRule.onNodeWithTag("mode-option-functions").performClick()
        composeRule.onNodeWithTag("functions-graph").performClick()

        val density = composeRule.activity.resources.displayMetrics.density
        val labels = listOf(
            "function-graph-label-fit" to "Ajuster",
            "function-graph-label-orthonormalize" to "Orthonormer",
            "function-graph-label-reset" to "Réinitialiser"
        )
        labels.forEach { (tag, text) ->
            val node = composeRule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode()
            composeRule.onNodeWithTag(tag, useUnmergedTree = true).assertTextEquals(text)
            assert(node.boundsInRoot.height <= 28f * density) { "$text wraps or exceeds one-line height: ${node.boundsInRoot}" }
        }
        listOf("function-graph-fit", "function-graph-orthonormalize", "function-graph-reset").forEach { tag ->
            val bounds = composeRule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
            assert(bounds.height >= 48f * density) { "Touch target $tag is too small: $bounds" }
        }
    }

    @Test fun colorChosenInA1IsRenderedIdenticallyInTheGraphFunctionPanel() {
        composeRule.onNodeWithTag("calculator-key-MODE").performClick()
        composeRule.onNodeWithTag("mode-option-functions").performClick()
        composeRule.onNodeWithTag("function-color-f1").performClick()
        composeRule.onNodeWithTag("function-palette-f1-SUN_YELLOW").performClick()
        composeRule.onNodeWithTag("functions-graph").performClick()
        composeRule.onNodeWithTag("function-graph-functions-panel").performClick()

        val pixels = composeRule.onNodeWithTag("function-graph-color-f1").captureToImage().toPixelMap()
        assertEquals(
            FunctionColor.SUN_YELLOW.argb.toInt(),
            pixels[pixels.width / 2, pixels.height / 2].toArgb()
        )
    }
}
