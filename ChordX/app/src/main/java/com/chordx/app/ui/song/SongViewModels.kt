package com.chordx.app.ui.song

import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.chordx.app.chordpro.ChartPlan
import com.chordx.app.chordpro.ChordProDocument
import com.chordx.app.chordpro.convertChordsOverLyrics
import com.chordx.app.chordpro.looksLikeChordsOverLyrics
import com.chordx.app.chordpro.parseChordPro
import com.chordx.app.chordpro.parseForDisplay
import com.chordx.app.chordpro.planChart
import com.chordx.app.data.ImportedSong
import com.chordx.app.data.SettingsStore
import com.chordx.app.data.Song
import com.chordx.app.data.SongDao
import com.chordx.app.data.uniqueTitle
import com.chordx.app.music.Accidentals
import com.chordx.app.music.clampBpm
import com.chordx.app.music.normalizeSemitones
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@OptIn(ExperimentalCoroutinesApi::class)
class SongListViewModel(private val songs: SongDao) : ViewModel() {
    var query by mutableStateOf("")

    /** Null until the first database result arrives, so the UI doesn't flash the empty state. */
    val results: StateFlow<List<Song>?> = snapshotFlow { query.trim() }
        .flatMapLatest { q -> if (q.isEmpty()) songs.observeAll() else songs.search(q) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    companion object {
        fun factory(songs: SongDao): ViewModelProvider.Factory = viewModelFactory {
            initializer { SongListViewModel(songs) }
        }
    }
}

sealed interface SongState {
    data object Loading : SongState
    data object Missing : SongState
    data class Loaded(
        val song: Song,
        val document: ChordProDocument,
        val plan: ChartPlan,
        val defaultAccidentals: Accidentals,
    ) : SongState
}

class SongViewModel(private val songs: SongDao, private val settings: SettingsStore, private val songId: Long) : ViewModel() {
    val showLyrics: StateFlow<Boolean> = settings.showLyrics.stateIn(viewModelScope, SharingStarted.Eagerly, true)

    fun setShowLyrics(show: Boolean) {
        viewModelScope.launch { settings.setShowLyrics(show) }
    }

    val state: StateFlow<SongState> = combine(songs.observeById(songId), settings.accidentals) { song, default ->
        if (song == null) {
            SongState.Missing
        } else {
            val document = parseForDisplay(song.content)
            val plan = planChart(document, song.key, song.transpose, keyOverride = null, preference = song.accidentals ?: default)
            SongState.Loaded(song, document, plan, default)
        }
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SongState.Loading)

    // Taps are applied one at a time against the stored value, so quick repeated taps all count.
    private val transposeLock = Mutex()

    private fun changeTransposition(change: (Song) -> Pair<Int, Accidentals?>) {
        viewModelScope.launch {
            transposeLock.withLock {
                val song = songs.getById(songId) ?: return@withLock
                val (semitones, accidentals) = change(song)
                songs.setTransposition(songId, normalizeSemitones(semitones), accidentals)
            }
        }
    }

    fun transposeBy(delta: Int) = changeTransposition { it.transpose + delta to it.accidentals }

    fun resetTranspose() = changeTransposition { 0 to it.accidentals }

    fun setAccidentals(value: Accidentals?) = changeTransposition { it.transpose to value }

    /** Saves the metronome's tempo as the song's tempo. */
    fun setTempo(bpm: Int) {
        viewModelScope.launch { songs.setTempo(songId, clampBpm(bpm)) }
    }

    suspend fun setlistUses(): Int = songs.countSetlistUses(songId)

    fun delete(onDeleted: () -> Unit) {
        viewModelScope.launch {
            songs.getById(songId)?.let { songs.delete(it) }
            onDeleted()
        }
    }

    companion object {
        fun factory(songs: SongDao, settings: SettingsStore, songId: Long): ViewModelProvider.Factory = viewModelFactory {
            initializer { SongViewModel(songs, settings, songId) }
        }
    }
}

/**
 * Creates a song when [songId] is null, otherwise edits that song. An [imported] file
 * pre-fills a new song; it counts as unsaved changes and shows any problems straight away.
 */
class SongEditViewModel(
    private val songs: SongDao,
    private val songId: Long?,
    imported: ImportedSong? = null,
) : ViewModel() {
    val isNew = songId == null
    val isImport = imported != null && isNew
    val convertedOnImport = isImport && imported!!.convertedFromPlainText

    var form by mutableStateOf(if (isImport) SongForm.from(imported!!) else SongForm())
        private set
    var errors by mutableStateOf(if (isImport) (validate(form) as? SongFormResult.Invalid)?.errors.orEmpty() else emptyMap())
        private set
    var loaded by mutableStateOf(isNew)
        private set
    /** Set once saving finishes; the screen navigates away when it sees it. */
    var savedId by mutableStateOf<Long?>(null)
        private set

    /** Live ChordPro check of the chart as it's typed. */
    val chartDiagnostics by derivedStateOf { parseChordPro(form.content).diagnostics }
    val canConvertPlainText by derivedStateOf { looksLikeChordsOverLyrics(form.content) }

    private var original: Song? = null
    private var initialForm = SongForm()
    private var saving = false

    val isDirty: Boolean get() = form != initialForm

    init {
        if (songId != null) {
            viewModelScope.launch {
                songs.getById(songId)?.let {
                    original = it
                    initialForm = SongForm.from(it)
                    form = initialForm
                }
                loaded = true
            }
        }
    }

    fun update(transform: (SongForm) -> SongForm) {
        form = transform(form)
        // Once errors are showing, re-check as the user types so they clear immediately.
        if (errors.isNotEmpty()) errors = (validate(form) as? SongFormResult.Invalid)?.errors.orEmpty()
    }

    fun convertPlainText() = update { it.copy(content = convertChordsOverLyrics(it.content)) }

    fun save() {
        if (saving) return
        val valid = when (val result = validate(form)) {
            is SongFormResult.Invalid -> { errors = result.errors; return }
            is SongFormResult.Valid -> result
        }
        errors = emptyMap()
        saving = true
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val existing = original
            val title = songs.uniqueTitle(valid.title, existing?.id ?: 0)
            savedId = if (existing == null) {
                songs.insert(
                    Song(
                        title = title, artist = valid.artist, key = valid.key, capo = valid.capo,
                        tempo = valid.tempo, timeSignature = valid.timeSignature, content = valid.content,
                        createdAt = now, updatedAt = now,
                    )
                )
            } else {
                songs.update(
                    existing.copy(
                        title = title, artist = valid.artist, key = valid.key, capo = valid.capo,
                        tempo = valid.tempo, timeSignature = valid.timeSignature, content = valid.content,
                        updatedAt = now,
                    )
                )
                existing.id
            }
        }
    }

    companion object {
        /** [takeImport] is only called when the ViewModel is first created, so an import is consumed once. */
        fun factory(songs: SongDao, songId: Long?, takeImport: () -> ImportedSong? = { null }): ViewModelProvider.Factory =
            viewModelFactory {
                initializer { SongEditViewModel(songs, songId, if (songId == null) takeImport() else null) }
            }
    }
}
