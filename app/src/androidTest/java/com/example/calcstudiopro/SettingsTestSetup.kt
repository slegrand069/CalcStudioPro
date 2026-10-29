package com.example.calcstudiopro

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.calcstudiopro.settings.AppSettings
import com.example.calcstudiopro.settings.DataStoreSettingsRepository
import kotlinx.coroutines.runBlocking

fun resetSettingsToDefaults() = runBlocking {
    DataStoreSettingsRepository(ApplicationProvider.getApplicationContext<Context>())
        .update(AppSettings())
}
