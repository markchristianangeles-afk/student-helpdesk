package com.chordx.app.ui.setlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.chordx.app.chordpro.ChartPlan
import com.chordx.app.chordpro.ChordProDocument
import com.chordx.app.chordpro.parseForDisplay
import com.chordx.app.chordpro.planChart
import com.chordx.app.data.MAX_FONT_SCALE
import com.chordx.app.data.MIN_FONT_SCALE
import com.chordx.app.data.SettingsStore
import com.chordx.app.data.Setlist
import com.chordx.app.data.SetlistDao
import com.chordx.app.data.SetlistEntry
import com.chordx.app.data.SetlistItem
import com.chordx.app.data.SetlistSummary
import com.chordx.app.data.Song
import com.chordx.app.data.SongDao
import com.chordx.app.music.clampBpm
import com.chordx.app.music.Accidentals
import com.chordx.app.music.Key
import com.chordx.app.music.normalizeSemitones
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

fun formatEventDate(epochDay: Long): String =
    LocalDate.ofEpochDay(epochDay).format(DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.getDefault()))

/** How a song will be played in a setlist: its chart, key and transposition after any per-setlist key. */
private fun plan(item: SetlistItem, default: Accidentals): Pair<ChordProDocument, ChartPlan> {
    val song = item.song
    val document = parseForDisplay(song.content)
    return document to planChart(document, song.key, song.transpose, item.entry.keyOverride, song.accidentals ?: default)
}

class SetlistListViewModel(private val setlists: SetlistDao) : ViewModel() {
    val summaries: StateFlow<List<SetlistSummary>?> = setlists.observeSummaries()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun create(name: String, eventDate: Long?, onCreated: (Long) -> Unit) {
        viewModelScope.launch { onCreated(setlists.insert(Setlist(name = name.trim(), eventDate = eventDate))) }
    }

    companion object {
        fun factory(setlists: SetlistDao): ViewModelProvider.Factory = viewModelFactory {
            initializer { SetlistListViewModel(setlists) }
        }
    }
}

data class SetlistRow(val entry: SetlistEntry, val song: Song, val displayKey: String?, val keyChanged: Boolean)

sealed interface SetlistState {
    data object Loading : SetlistState
    data object Missing : SetlistState
    data class Loaded(val setlist: Setlist, val rows: List<SetlistRow>) : SetlistState
}

class SetlistViewModel(
    private val setlists: SetlistDao,
    private val settings: SettingsStore,
    private val setlistId: Long,
) : ViewModel() {
    /** The songs as they will be played (per-setlist keys applied), with notes and charts, for the PDF. */
    suspend fun pdfSongs(): List<PdfSong> {
        val default = settings.accidentals.first()
        return withContext(Dispatchers.Default) {
            setlists.getItems(setlistId).mapIndexed { index, item ->
                val (document, chart) = plan(item, default)
                val song = item.song
                PdfSong(
                    number = index + 1,
                    title = song.title,
                    artist = song.artist?.takeIf { it.isNotBlank() },
                    key = chart.displayKey,
                    details = listOfNotNull(
                        song.capo.takeIf { it > 0 }?.let { "Capo $it" },
                        song.tempo?.let { "$it BPM" },
                        song.timeSignature?.takeIf { it.isNotBlank() },
                    ).joinToString(" · ").ifEmpty { null },
                    note = item.entry.notes?.takeIf { it.isNotBlank() },
                    chart = chartTextLines(document, chart.renderer),
                )
            }
        }
    }

    val state: StateFlow<SetlistState> =
        combine(setlists.observeById(setlistId), setlists.observeItems(setlistId), settings.accidentals) { setlist, items, default ->
            if (setlist == null) {
                SetlistState.Missing
            } else {
                SetlistState.Loaded(
                    setlist,
                    items.map { item ->
                        val (_, chart) = plan(item, default)
                        SetlistRow(item.entry, item.song, chart.displayKey, keyChanged = chart.semitones != 0)
                    },
                )
            }
        }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SetlistState.Loading)

    fun update(name: String, eventDate: Long?) {
        val current = (state.value as? SetlistState.Loaded)?.setlist ?: return
        viewModelScope.launch {
            setlists.update(current.copy(name = name.trim(), eventDate = eventDate, updatedAt = System.currentTimeMillis()))
        }
    }

    fun delete(onDeleted: () -> Unit) {
        viewModelScope.launch {
            setlists.getById(setlistId)?.let { setlists.delete(it) }
            onDeleted()
        }
    }

    fun move(from: Int, to: Int) {
        viewModelScope.launch { setlists.moveEntry(setlistId, from, to) }
    }

    fun remove(entryId: Long) {
        viewModelScope.launch { setlists.removeEntry(setlistId, entryId) }
    }

    fun setKey(entryId: Long, key: String?) {
        viewModelScope.launch { setlists.setKeyOverride(entryId, key) }
    }

    /** Saves the note shown between this song and the next; blank clears it. */
    fun setNote(entryId: Long, note: String?) {
        viewModelScope.launch { setlists.setNote(entryId, note?.trim()?.ifEmpty { null }) }
    }

    fun addSongs(songIds: List<Long>, onDone: () -> Unit) {
        viewModelScope.launch {
            setlists.addSongs(setlistId, songIds)
            onDone()
        }
    }

    companion object {
        fun factory(setlists: SetlistDao, settings: SettingsStore, setlistId: Long): ViewModelProvider.Factory = viewModelFactory {
            initializer { SetlistViewModel(setlists, settings, setlistId) }
        }
    }
}

data class PerformancePage(
    val entryId: Long,
    val song: Song,
    val document: ChordProDocument,
    val plan: ChartPlan,
    /** Note shown between this song and the next. */
    val noteAfter: String? = null,
    /** True when this setlist plays the song in a different key from the song's own. */
    val keyChanged: Boolean = false,
)

class PerformanceViewModel(
    private val setlists: SetlistDao,
    private val songs: SongDao,
    private val settings: SettingsStore,
    private val setlistId: Long,
) : ViewModel() {
    val pages: StateFlow<List<PerformancePage>?> =
        combine(setlists.observeItems(setlistId), settings.accidentals) { items, default ->
            items.map { item ->
                val (document, chart) = plan(item, default)
                PerformancePage(
                    item.entry.id, item.song, document, chart,
                    noteAfter = item.entry.notes?.takeIf { it.isNotBlank() },
                    keyChanged = item.entry.keyOverride != null,
                )
            }
        }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val fontScale: StateFlow<Float> = settings.performanceFontScale
        .stateIn(viewModelScope, SharingStarted.Eagerly, 1f)

    val showLyrics: StateFlow<Boolean> = settings.showLyrics
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    fun setShowLyrics(show: Boolean) {
        viewModelScope.launch { settings.setShowLyrics(show) }
    }

    /** Steps text size in tenths, within the allowed range. */
    fun changeFontScale(steps: Int) {
        val next = ((fontScale.value * 10).roundToInt() + steps) / 10f
        viewModelScope.launch { settings.setPerformanceFontScale(next.coerceIn(MIN_FONT_SCALE, MAX_FONT_SCALE)) }
    }

    /** Saves the note shown above "Next: …" after this song; blank clears it. */
    fun setNote(entryId: Long, note: String?) {
        viewModelScope.launch { setlists.setNote(entryId, note?.trim()?.ifEmpty { null }) }
    }

    // Taps are applied one at a time against the stored values, so quick repeated taps all count.
    private val transposeLock = Mutex()

    /**
     * Shifts a song by [delta] semitones for this setlist only (the song itself is untouched).
     * A song without a key can't be given a per-setlist key, so its own transposition changes instead.
     */
    fun transposeBy(entryId: Long, delta: Int) = change(entryId) { item ->
        val original = item.song.key?.let(Key::parse)
        if (original != null) {
            val current = item.entry.keyOverride?.let(Key::parse)?.let(original::semitonesTo) ?: normalizeSemitones(item.song.transpose)
            val next = normalizeSemitones(current + delta)
            setlists.setKeyOverride(entryId, if (next == 0) null else original.transpose(next).toString())
        } else {
            songs.setTransposition(item.song.id, normalizeSemitones(item.song.transpose + delta), item.song.accidentals)
        }
    }

    /** Back to the song's own key for this setlist. */
    fun resetTranspose(entryId: Long) = change(entryId) { item ->
        if (item.song.key?.let(Key::parse) != null) setlists.setKeyOverride(entryId, null)
        else songs.setTransposition(item.song.id, 0, item.song.accidentals)
    }

    private fun change(entryId: Long, action: suspend (SetlistItem) -> Unit) {
        viewModelScope.launch {
            transposeLock.withLock {
                val item = setlists.getItems(setlistId).firstOrNull { it.entry.id == entryId } ?: return@withLock
                action(item)
            }
        }
    }

    /** Saves the metronome's tempo as that song's tempo. */
    fun setTempo(songId: Long, bpm: Int) {
        viewModelScope.launch { songs.setTempo(songId, clampBpm(bpm)) }
    }

    companion object {
        fun factory(setlists: SetlistDao, songs: SongDao, settings: SettingsStore, setlistId: Long): ViewModelProvider.Factory = viewModelFactory {
            initializer { PerformanceViewModel(setlists, songs, settings, setlistId) }
        }
    }
}
