package com.chordx.app.ui.song

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import com.chordx.app.data.buildSongExport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chordx.app.data.Song
import com.chordx.app.chordpro.beatsPerBar
import com.chordx.app.music.DEFAULT_BPM
import com.chordx.app.ui.chart.DiagnosticsCard
import com.chordx.app.ui.chart.MetronomeControls
import com.chordx.app.ui.chart.MetronomeIcon
import com.chordx.app.ui.chart.ShowLyricsCheckbox
import com.chordx.app.ui.chart.SongChart
import com.chordx.app.ui.chart.TransposeControls
import com.chordx.app.ui.chart.formatSemitones
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongViewScreen(
    viewModel: SongViewModel,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onDeleted: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val showLyrics by viewModel.showLyrics.collectAsStateWithLifecycle()
    val loaded = state as? SongState.Loaded
    val song = loaded?.song
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val exportSong = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        val current = song
        if (uri == null || current == null) return@rememberLauncherForActivityResult
        scope.launch {
            val ok = runCatching {
                withContext(Dispatchers.IO) {
                    val out = context.contentResolver.openOutputStream(uri, "wt") ?: error("Couldn't open the file")
                    out.use { it.write(buildSongExport(listOf(current)).toByteArray(Charsets.UTF_8)) }
                }
            }.isSuccess
            Toast.makeText(context, if (ok) "Exported \"${current.title}\"." else "Couldn't export the song.", Toast.LENGTH_LONG).show()
        }
    }
    // Holds the setlist-use count while the delete confirmation is open.
    var pendingDeleteUses by remember { mutableStateOf<Int?>(null) }
    var showTranspose by rememberSaveable { mutableStateOf(false) }
    var showMetronome by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(song?.title.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    if (song != null) {
                        IconToggleButton(checked = showMetronome, onCheckedChange = { showMetronome = it }) {
                            Icon(MetronomeIcon, contentDescription = "Metronome")
                        }
                        IconToggleButton(checked = showTranspose, onCheckedChange = { showTranspose = it }) {
                            Icon(Icons.Default.SwapVert, contentDescription = "Transpose")
                        }
                        IconButton(onClick = { exportSong.launch(songFileName(song.title)) }, modifier = Modifier.testTag("export_song")) {
                            Icon(Icons.Default.FileUpload, contentDescription = "Export song")
                        }
                        IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, contentDescription = "Edit") }
                        IconButton(onClick = { scope.launch { pendingDeleteUses = viewModel.setlistUses() } }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete")
                        }
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (val s = state) {
                SongState.Loading -> Unit
                SongState.Missing -> Text(
                    "This song no longer exists.",
                    modifier = Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                is SongState.Loaded -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
                    SongHeader(s.song, s.plan.displayKey, s.plan.semitones)
                    if (showMetronome) {
                        MetronomeControls(
                            bpm = s.song.tempo ?: DEFAULT_BPM,
                            beatsPerBar = beatsPerBar(s.song.timeSignature ?: s.document.metadata["time"]),
                            onBpmChange = viewModel::setTempo,
                            modifier = Modifier.padding(top = 12.dp),
                        )
                        HorizontalDivider(Modifier.padding(top = 12.dp))
                    }
                    if (showTranspose) {
                        TransposeControls(
                            semitones = s.plan.semitones,
                            displayKey = s.plan.displayKey,
                            songAccidentals = s.song.accidentals,
                            defaultAccidentals = s.defaultAccidentals,
                            onTranspose = viewModel::transposeBy,
                            onReset = viewModel::resetTranspose,
                            onAccidentals = viewModel::setAccidentals,
                            modifier = Modifier.padding(top = 12.dp),
                        )
                        HorizontalDivider(Modifier.padding(top = 12.dp))
                    }
                    DiagnosticsCard(s.document.diagnostics, Modifier.padding(top = 12.dp))
                    if (s.document.convertedForDisplay) {
                        Text(
                            "Some chords here were recognised automatically so they transpose. To save them in the chart, tap Edit, then Convert to ChordPro.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 12.dp),
                        )
                    }
                    if (s.song.content.isBlank()) {
                        Spacer(Modifier.height(16.dp))
                        Text("No chart yet. Tap Edit to add one.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        ShowLyricsCheckbox(showLyrics, viewModel::setShowLyrics, Modifier.padding(vertical = 4.dp), note = "(if applicable)")
                        SongChart(s.document, s.plan.renderer, showLyrics = showLyrics, timeSignature = s.song.timeSignature)
                    }
                }
            }
        }
    }

    val uses = pendingDeleteUses
    if (uses != null && song != null) {
        AlertDialog(
            onDismissRequest = { pendingDeleteUses = null },
            title = { Text("Delete “${song.title}”?") },
            text = {
                Text(
                    if (uses == 0) "This can't be undone."
                    else "It will also be removed from ${if (uses == 1) "1 setlist slot" else "$uses setlist slots"}. This can't be undone."
                )
            },
            confirmButton = {
                TextButton(onClick = { pendingDeleteUses = null; viewModel.delete(onDeleted) }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { pendingDeleteUses = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SongHeader(song: Song, displayKey: String?, semitones: Int) {
    Text(song.title, style = MaterialTheme.typography.headlineSmall)
    song.artist?.let {
        Text(it, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    val details = songDetails(song, displayKey, semitones)
    if (details.isNotEmpty()) {
        Text(
            details,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

/** "Key A (G +2)  ·  Capo 2  ·  72 BPM  ·  3/4", leaving out whatever isn't set. */
internal fun songDetails(song: Song, displayKey: String? = song.key, semitones: Int = 0): String = listOfNotNull(
    displayKey?.let { key -> if (semitones != 0 && song.key != null) "Key $key (${song.key} ${formatSemitones(semitones)})" else "Key $key" },
    song.capo.takeIf { it > 0 }?.let { "Capo $it" },
    song.tempo?.let { "$it BPM" },
    song.timeSignature,
).joinToString("  ·  ")

/** A file name safe on Android storage; the .cho file imports back into ChordX on any device. */
internal fun songFileName(title: String): String =
    title.ifBlank { "Song" }.replace(Regex("""[\/:*?"<>|\n]"""), "-").trim() + ".cho"
