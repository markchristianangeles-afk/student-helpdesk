package com.chordx.app

import android.app.Application
import android.util.Log
import com.chordx.app.data.ChordXDatabase
import com.chordx.app.data.DataStoreSettings
import com.chordx.app.data.DevotionDao
import com.chordx.app.data.ImportedSong
import com.chordx.app.data.NoteDao
import com.chordx.app.data.SettingsStore
import com.chordx.app.data.SetlistDao
import com.chordx.app.data.SongDao
import com.chordx.app.data.normaliseExistingTitles
import com.chordx.app.data.settingsDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/** Everything the UI needs, built once per process. Tests build their own with an in-memory database. */
class AppContainer(val database: ChordXDatabase, val settings: SettingsStore) {
    val songDao: SongDao get() = database.songDao()
    val setlistDao: SetlistDao get() = database.setlistDao()
    val noteDao: NoteDao get() = database.noteDao()
    val devotionDao: DevotionDao get() = database.devotionDao()

    /** A file read from the picker, waiting for the editor to pick it up. */
    private var pendingImport: ImportedSong? = null

    fun offerImport(song: ImportedSong) { pendingImport = song }

    fun takeImport(): ImportedSong? = pendingImport.also { pendingImport = null }
}

class ChordXApplication : Application() {
    lateinit var container: AppContainer
        private set

    /** Work that must outlive any one screen, such as the first-launch song import. */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val bundledImportStarted = AtomicBoolean(false)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(ChordXDatabase.open(this), DataStoreSettings(settingsDataStore))
    }

    /** Adds the charts shipped in the APK to the library, once per [BundledSongs.VERSION]. */
    fun importBundledSongsOnce() {
        if (!bundledImportStarted.compareAndSet(false, true)) return
        appScope.launch {
            runCatching { BundledSongs.importIfNeeded(this@ChordXApplication, container.songDao) }
                .onFailure { Log.e("ChordX", "Bundled song import failed", it) }
            runCatching { normaliseExistingTitles(this@ChordXApplication, container.songDao) }
                .onFailure { Log.e("ChordX", "Title formatting failed", it) }
        }
    }
}
