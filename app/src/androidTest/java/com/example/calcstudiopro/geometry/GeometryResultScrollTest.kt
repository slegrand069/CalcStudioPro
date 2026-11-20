package com.example.calcstudiopro.geometry

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.espresso.Espresso.pressBack
import com.example.calcstudiopro.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GeometryResultScrollTest {
    @get:Rule val composeRule = createAndroidComposeRule<MainActivity>()

    @Test fun triangleResultsCanScrollIntoTheirDiagram() {
        openGeometry()
        composeRule.onNodeWithTag("geometry-calculate").performClick()
        composeRule.onNodeWithTag("geometry-diagram").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("geometry-result-a").performScrollTo()
        composeRule.onAllNodesWithTag("geometry-result-a").assertCountEquals(1)
    }

    @Test fun circleResultsCanScrollIntoTheirDiagram() {
        openGeometry()
        selectFigure("Cercle")
        composeRule.onNodeWithTag("geometry-input-radius").performScrollTo().performTextInput("3")
        pressBack()
        composeRule.onNodeWithTag("geometry-calculate").performScrollTo().performClick()
        composeRule.onNodeWithTag("geometry-diagram").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("geometry-result-radius").performScrollTo().assertIsDisplayed()
    }

    @Test fun parallelogramResultsCanScrollIntoTheirDiagram() {
        openGeometry()
        selectFigure("Parallélogramme")
        composeRule.onNodeWithTag("geometry-input-a").performScrollTo().performTextInput("3")
        composeRule.onNodeWithTag("geometry-input-b").performScrollTo().performTextInput("4")
        composeRule.onNodeWithTag("geometry-input-angle").performScrollTo().performTextInput("90")
        pressBack()
        composeRule.onNodeWithTag("geometry-calculate").performScrollTo().performClick()
        composeRule.onNodeWithTag("geometry-diagram").performScrollTo().assertIsDisplayed()
    }

    @Test fun isoscelesBaseIsVisibleEditableAndRecalculates() {
        openGeometry()
        composeRule.onNodeWithTag("geometry-variant").performScrollTo().performClick()
        composeRule.onNodeWithText("Isocèle").performClick()
        composeRule.onNodeWithTag("geometry-input-c").performScrollTo().performTextClearance()
        composeRule.onNodeWithTag("geometry-input-c").performTextInput("8.5")
        composeRule.onNodeWithTag("geometry-input-c").assertTextContains("8.5")
        pressBack()
        composeRule.onNodeWithTag("geometry-calculate").performScrollTo().performClick()
        composeRule.onAllNodesWithTag("geometry-error").assertCountEquals(0)
        composeRule.onNodeWithTag("geometry-diagram").performScrollTo().assertIsDisplayed()
    }

    private fun openGeometry() {
        composeRule.onNodeWithTag("calculator-key-MODE").performClick()
        composeRule.onNodeWithTag("mode-option-geometry").performClick()
        composeRule.onNodeWithTag("geometry-calculate").performScrollTo().assertIsDisplayed()
    }

    private fun selectFigure(label: String) {
        composeRule.onNodeWithTag("geometry-figure").performScrollTo().performClick()
        composeRule.onNodeWithText(label).performClick()
    }
}
