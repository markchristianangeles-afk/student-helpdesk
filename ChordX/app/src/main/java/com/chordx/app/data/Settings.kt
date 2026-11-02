package com.chordx.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.chordx.app.music.Accidentals
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

enum class ThemeMode { SYSTEM, LIGHT, DARK }

const val MIN_FONT_SCALE = 0.7f
const val MAX_FONT_SCALE = 2.5f

interface SettingsStore {
    val themeMode: Flow<ThemeMode>
    suspend fun setThemeMode(mode: ThemeMode)

    /** Default sharp/flat spelling for songs that don't set their own. */
    val accidentals: Flow<Accidentals>
    suspend fun setAccidentals(value: Accidentals)

    /** Chart text size in performance mode, as a multiple of normal. */
    val performanceFontScale: Flow<Float>
    suspend fun setPerformanceFontScale(scale: Float)

    /** False shows charts as chords only (e.g. for the band); shared by the viewer and performance mode. */
    val showLyrics: Flow<Boolean>
    suspend fun setShowLyrics(show: Boolean)
}

val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class DataStoreSettings(private val dataStore: DataStore<Preferences>) : SettingsStore {
    private val prefs = dataStore.data.catch { if (it is IOException) emit(emptyPreferences()) else throw it }

    override val themeMode: Flow<ThemeMode> = prefs.map { it[THEME_MODE].toEnum(ThemeMode.SYSTEM) }

    override suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[THEME_MODE] = mode.name }
    }

    override val accidentals: Flow<Accidentals> = prefs.map { it[ACCIDENTALS].toEnum(Accidentals.AUTO) }

    override suspend fun setAccidentals(value: Accidentals) {
        dataStore.edit { it[ACCIDENTALS] = value.name }
    }

    override val performanceFontScale: Flow<Float> =
        prefs.map { (it[FONT_SCALE] ?: 1f).coerceIn(MIN_FONT_SCALE, MAX_FONT_SCALE) }

    override suspend fun setPerformanceFontScale(scale: Float) {
        dataStore.edit { it[FONT_SCALE] = scale.coerceIn(MIN_FONT_SCALE, MAX_FONT_SCALE) }
    }

    override val showLyrics: Flow<Boolean> = prefs.map { it[SHOW_LYRICS] ?: true }

    override suspend fun setShowLyrics(show: Boolean) {
        dataStore.edit { it[SHOW_LYRICS] = show }
    }

    private companion object {
        val SHOW_LYRICS = booleanPreferencesKey("show_lyrics")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val ACCIDENTALS = stringPreferencesKey("accidentals")
        val FONT_SCALE = floatPreferencesKey("performance_font_scale")

        inline fun <reified E : Enum<E>> String?.toEnum(default: E): E =
            enumValues<E>().firstOrNull { it.name == this } ?: default
    }
}
