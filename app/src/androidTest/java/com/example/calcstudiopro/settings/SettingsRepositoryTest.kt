package com.example.calcstudiopro.settings

import android.content.Context
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class SettingsRepositoryTest {
    private fun createRepository(): DataStoreSettingsRepository {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = File(context.cacheDir, "settings-${UUID.randomUUID()}.preferences_pb")
        val store = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
            produceFile = { file }
        )
        return DataStoreSettingsRepository(store)
    }

    @Test
    fun emptyStoreReturnsDefaultsAndWrittenValuesCanBeReadBack() = runBlocking {
        val repository = createRepository()
        assertEquals(AppSettings(), repository.settings.first())
        val expected = AppSettings(
            ContinuedFractionSettings(1e-8, 500, 32, ResultPresentation.DECIMAL),
            com.example.calcstudiopro.domain.AngleUnit.RADIANS,
            CalculatorNotation.SCIENTIFIC
        )
        repository.update(expected)
        assertEquals(expected, repository.settings.first())

        repository.updateAngleUnit(com.example.calcstudiopro.domain.AngleUnit.DEGREES)
        assertEquals(CalculatorNotation.SCIENTIFIC, repository.settings.first().notation)
    }

    @Test
    fun missingAndInvalidPreferencesFallBackToDefaults() = runBlocking {
        val repository = createRepository()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = File(context.cacheDir, "settings-${UUID.randomUUID()}.preferences_pb")
        val store = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
            produceFile = { file }
        )
        store.edit { prefs ->
            prefs[doublePreferencesKey("tolerance")] = Double.NaN
            prefs[intPreferencesKey("max_denominator")] = 0
            prefs[intPreferencesKey("max_iterations")] = -4
            prefs[stringPreferencesKey("result_presentation")] = "UNKNOWN"
            prefs[stringPreferencesKey("angle_unit")] = "UNKNOWN"
            prefs[stringPreferencesKey("calculator_notation")] = "UNKNOWN"
        }
        assertEquals(AppSettings(), DataStoreSettingsRepository(store).settings.first())
        assertEquals(AppSettings(), repository.settings.first())
    }

    @Test
    fun validPreviouslyStoredToleranceIsNotReplacedByTheNewDefault() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = File(context.cacheDir, "settings-${UUID.randomUUID()}.preferences_pb")
        val store = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
            produceFile = { file }
        )
        store.edit { prefs -> prefs[doublePreferencesKey("tolerance")] = 1e-10 }

        assertEquals(
            1e-10,
            DataStoreSettingsRepository(store).settings.first().continuedFractions.tolerance,
            0.0
        )
    }
}
