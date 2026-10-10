package com.multitrack.player.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.multitrack.player.domain.model.AppSettings
import com.multitrack.player.domain.model.MetronomeSubdivision
import com.multitrack.player.domain.repository.AppSettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.appSettingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "app_audio_settings"
)

class AppSettingsRepositoryImpl(
    private val context: Context
) : AppSettingsRepository {

    private object Keys {
        val CountInEnabled = booleanPreferencesKey("count_in_enabled")
        val CountInBeats = intPreferencesKey("count_in_beats")
        val MetronomeVolume = floatPreferencesKey("metronome_volume")
        val MetronomeSubdivision = stringPreferencesKey("metronome_subdivision")
    }

    override val settings: Flow<AppSettings> = context.appSettingsDataStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { preferences ->
            AppSettings(
                countInEnabled = preferences[Keys.CountInEnabled] ?: false,
                countInBeats = (preferences[Keys.CountInBeats] ?: 4).coerceIn(1, 4),
                metronomeVolume = (preferences[Keys.MetronomeVolume] ?: 0.45f).coerceIn(0f, 1f),
                metronomeSubdivision = preferences[Keys.MetronomeSubdivision]
                    ?.let { stored -> MetronomeSubdivision.entries.firstOrNull { it.name == stored } }
                    ?: MetronomeSubdivision.QUARTER_NOTE
            )
        }

    override suspend fun setCountInEnabled(enabled: Boolean) {
        context.appSettingsDataStore.edit { it[Keys.CountInEnabled] = enabled }
    }

    override suspend fun setCountInBeats(beats: Int) {
        context.appSettingsDataStore.edit { it[Keys.CountInBeats] = beats.coerceIn(1, 4) }
    }

    override suspend fun setMetronomeVolume(volume: Float) {
        context.appSettingsDataStore.edit { it[Keys.MetronomeVolume] = volume.coerceIn(0f, 1f) }
    }

    override suspend fun setMetronomeSubdivision(subdivision: MetronomeSubdivision) {
        context.appSettingsDataStore.edit { it[Keys.MetronomeSubdivision] = subdivision.name }
    }
}
