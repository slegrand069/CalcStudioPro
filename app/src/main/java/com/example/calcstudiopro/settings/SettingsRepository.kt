package com.example.calcstudiopro.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

interface SettingsRepository {
    val settings: Flow<AppSettings>
    suspend fun update(settings: AppSettings)
    suspend fun updateContinuedFractions(settings: ContinuedFractionSettings)
    suspend fun updateAngleUnit(angleUnit: com.example.calcstudiopro.domain.AngleUnit)
    suspend fun updateNotation(notation: CalculatorNotation)
}

private val Context.settingsDataStore by preferencesDataStore(name = "continued_fraction_settings")

class DataStoreSettingsRepository(
    private val dataStore: DataStore<Preferences>
) : SettingsRepository {

    constructor(context: Context) : this(context.settingsDataStore)

    override val settings: Flow<AppSettings> = dataStore.data
        .catch { error ->
            if (error is IOException) emit(androidx.datastore.preferences.core.emptyPreferences())
            else throw error
        }
        .map(::decode)

    override suspend fun update(settings: AppSettings) {
        dataStore.edit { preferences ->
            preferences[TOLERANCE] = settings.continuedFractions.tolerance
            preferences[MAX_DENOMINATOR] = settings.continuedFractions.maxDenominator
            preferences[MAX_ITERATIONS] = settings.continuedFractions.maxIterations
            preferences[RESULT_PRESENTATION] = settings.continuedFractions.resultPresentation.name
            preferences[ANGLE_UNIT] = settings.angleUnit.name
            preferences[NOTATION] = settings.notation.name
        }
    }

    override suspend fun updateContinuedFractions(settings: ContinuedFractionSettings) {
        dataStore.edit { preferences ->
            preferences[TOLERANCE] = settings.tolerance
            preferences[MAX_DENOMINATOR] = settings.maxDenominator
            preferences[MAX_ITERATIONS] = settings.maxIterations
            preferences[RESULT_PRESENTATION] = settings.resultPresentation.name
        }
    }

    override suspend fun updateAngleUnit(angleUnit: com.example.calcstudiopro.domain.AngleUnit) {
        dataStore.edit { it[ANGLE_UNIT] = angleUnit.name }
    }

    override suspend fun updateNotation(notation: CalculatorNotation) {
        dataStore.edit { it[NOTATION] = notation.name }
    }

    private fun decode(preferences: Preferences): AppSettings {
        val tolerance = preferences[TOLERANCE]
            ?.takeIf { it.isFinite() && it > 0.0 }
            ?: ContinuedFractionSettings.DEFAULT_TOLERANCE
        val denominator = preferences[MAX_DENOMINATOR]
            ?.takeIf { it >= 1 }
            ?: ContinuedFractionSettings.DEFAULT_MAX_DENOMINATOR
        val iterations = preferences[MAX_ITERATIONS]
            ?.takeIf { it > 0 }
            ?: ContinuedFractionSettings.DEFAULT_MAX_ITERATIONS
        val presentation = preferences[RESULT_PRESENTATION]
            ?.let { runCatching { ResultPresentation.valueOf(it) }.getOrNull() }
            ?: ContinuedFractionSettings.DEFAULT_RESULT_PRESENTATION
        val angleUnit = preferences[ANGLE_UNIT]
            ?.let { runCatching { com.example.calcstudiopro.domain.AngleUnit.valueOf(it) }.getOrNull() }
            ?: AppSettings.DEFAULT_ANGLE_UNIT
        val notation = preferences[NOTATION]
            ?.let { runCatching { CalculatorNotation.valueOf(it) }.getOrNull() }
            ?: AppSettings.DEFAULT_NOTATION
        return AppSettings(
            continuedFractions = ContinuedFractionSettings(tolerance, denominator, iterations, presentation),
            angleUnit = angleUnit,
            notation = notation
        )
    }

    private companion object {
        val TOLERANCE = doublePreferencesKey("tolerance")
        val MAX_DENOMINATOR = intPreferencesKey("max_denominator")
        val MAX_ITERATIONS = intPreferencesKey("max_iterations")
        val RESULT_PRESENTATION = stringPreferencesKey("result_presentation")
        val ANGLE_UNIT = stringPreferencesKey("angle_unit")
        val NOTATION = stringPreferencesKey("calculator_notation")
    }
}
