package com.chordx.app.ui

import android.net.Uri
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.core.app.ActivityOptionsCompat
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.chordx.app.data.ChordXDatabase
import com.chordx.app.data.MAX_FONT_SCALE
import com.chordx.app.data.MIN_FONT_SCALE
import com.chordx.app.data.SettingsStore
import com.chordx.app.data.ThemeMode
import com.chordx.app.music.Accidentals
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.rules.ExternalResource

/**
 * In-memory database for UI tests. Use it as the outer rule so it closes only after
 * the compose rule has torn the UI down; closing it first leaves screens querying a
 * closed database.
 */
class InMemoryDatabaseRule : ExternalResource() {
    lateinit var db: ChordXDatabase
        private set

    override fun before() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), ChordXDatabase::class.java).build()
    }

    override fun after() = db.close()
}

class FakeSettings : SettingsStore {
    override val themeMode = MutableStateFlow(ThemeMode.SYSTEM)
    override suspend fun setThemeMode(mode: ThemeMode) { themeMode.value = mode }

    override val accidentals = MutableStateFlow(Accidentals.AUTO)
    override suspend fun setAccidentals(value: Accidentals) { accidentals.value = value }

    override val showLyrics = MutableStateFlow(true)
    override suspend fun setShowLyrics(show: Boolean) { showLyrics.value = show }

    override val performanceFontScale = MutableStateFlow(1f)
    override suspend fun setPerformanceFontScale(scale: Float) { performanceFontScale.value = scale.coerceIn(MIN_FONT_SCALE, MAX_FONT_SCALE) }
}

/** Stands in for the system file picker: every launch immediately returns [picked]. */
class FakeFilePicker : ActivityResultRegistryOwner {
    var picked: List<Uri> = emptyList()

    override val activityResultRegistry = object : ActivityResultRegistry() {
        override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>, input: I, options: ActivityOptionsCompat?) {
            @Suppress("UNCHECKED_CAST")
            dispatchResult(requestCode, picked as O)
        }
    }
}
