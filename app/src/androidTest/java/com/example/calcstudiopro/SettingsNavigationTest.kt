package com.example.calcstudiopro

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.calcstudiopro.settings.ContinuedFractionSettings
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsNavigationTest {
    @get:Rule val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun resetSettings() = resetSettingsToDefaults()

    @After
    fun restoreSettings() = resetSettingsToDefaults()

    @Test
    fun opensSettingsShowsDefaultsAndBackPreservesExpression() {
        tap("2")
        tap("+")
        composeRule.onNodeWithTag("calculator-settings").performClick()
        composeRule.onNodeWithText("Settings").assertIsDisplayed()
        assertEditableText("settings-tolerance", "1e-10")
        assertEditableText("settings-max-denominator", "10000")
        assertEditableText("settings-max-iterations", "64")
        composeRule.onNodeWithTag("settings-presentation-FRACTION").assertIsSelected()
        composeRule.onNodeWithTag("settings-angle-DEGREES").assertIsSelected()
        composeRule.onNodeWithTag("settings-notation-STANDARD").assertIsSelected()
        composeRule.onNodeWithTag("settings-back").performClick()
        composeRule.onNodeWithTag("calculator-expression").assertTextEquals("2+")
    }

    @Test
    fun editsToleranceAndChangeSurvivesNavigation() {
        composeRule.onNodeWithTag("calculator-settings").performClick()
        composeRule.onNodeWithTag("settings-tolerance").performTextClearance()
        composeRule.onNodeWithTag("settings-tolerance").performTextInput("1e-8")
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settings-back").performClick()
        composeRule.onNodeWithTag("calculator-settings").performClick()
        assertEditableText("settings-tolerance", "1e-8")
    }

    @Test
    fun angleAndNotationChoicesPersistAndApplyToCalculator() {
        composeRule.onNodeWithTag("calculator-settings").performClick()
        composeRule.onNodeWithTag("settings-angle-RADIANS").performClick()
        composeRule.onNodeWithTag("settings-notation-SCIENTIFIC").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settings-back").performClick()

        composeRule.onNodeWithTag("calculator-angle-toggle").assertTextEquals("RAD")
        composeRule.onNodeWithTag("calculator-number-format-toggle").assertTextEquals("SCI")

        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("calculator-angle-toggle").assertTextEquals("RAD")
        composeRule.onNodeWithTag("calculator-number-format-toggle").assertTextEquals("SCI")

        composeRule.onNodeWithTag("calculator-settings").performClick()
        composeRule.onNodeWithTag("settings-angle-RADIANS").assertIsSelected()
        composeRule.onNodeWithTag("settings-notation-SCIENTIFIC").assertIsSelected()
    }

    private fun tap(key: String) = composeRule.onNodeWithTag("calculator-key-$key").performClick()

    private fun assertEditableText(tag: String, value: String) {
        composeRule.onNodeWithTag(tag).assert(
            SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString(value))
        )
    }
}
