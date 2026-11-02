package com.chordx.app.ui.setlist

import android.app.Activity
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.TextDecrease
import androidx.compose.material.icons.filled.TextIncrease
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chordx.app.data.MAX_FONT_SCALE
import com.chordx.app.data.MIN_FONT_SCALE
import com.chordx.app.chordpro.beatsPerBar
import com.chordx.app.music.DEFAULT_BPM
import com.chordx.app.ui.chart.MetronomeControls
import com.chordx.app.ui.chart.MetronomeIcon
import com.chordx.app.ui.chart.ShowLyricsCheckbox
import com.chordx.app.ui.chart.formatSemitones
import com.chordx.app.ui.chart.SongChart
import com.chordx.app.ui.song.EmptyState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.input.key.Key as InputKey

/**
 * Full-screen, swipe-through view of a setlist for playing live. Keeps the screen on,
 * hides the system bars, and responds to page-turner pedals and keyboards:
 * Left/Right change song; Page Down/Down/Space scroll on, then move to the next song.
 */
@Composable
fun PerformanceScreen(viewModel: PerformanceViewModel, startIndex: Int, onClose: () -> Unit) {
    // A Surface sets the content colour for text and icons; without one they default to
    // black, which is unreadable in dark mode. (Other screens get this from Scaffold.)
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        PerformanceContent(viewModel, startIndex, onClose)
    }
}

@Composable
private fun PerformanceContent(viewModel: PerformanceViewModel, startIndex: Int, onClose: () -> Unit) {
    val pages by viewModel.pages.collectAsStateWithLifecycle()
    val fontScale by viewModel.fontScale.collectAsStateWithLifecycle()
    val showLyrics by viewModel.showLyrics.collectAsStateWithLifecycle()
    val list = pages ?: return

    StageMode()

    if (list.isEmpty()) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            EmptyState("This setlist is empty.")
            IconButton(onClick = onClose, modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing)) {
                Icon(Icons.Default.Close, contentDescription = "Close")
            }
        }
        return
    }

    val pager = rememberPagerState(initialPage = startIndex.coerceIn(0, list.lastIndex)) { list.size }
    val scrollStates = remember { mutableMapOf<Long, ScrollState>() }
    val scope = rememberCoroutineScope()
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    var showMetronome by rememberSaveable { mutableStateOf(false) }
    var showTranspose by rememberSaveable { mutableStateOf(false) }
    var noteEditFor by remember { mutableStateOf<Long?>(null) }

    fun scrollOf(page: Int) = scrollStates.getOrPut(list[page].entryId) { ScrollState(0) }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .focusRequester(focus)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    InputKey.DirectionRight -> scope.goTo(pager, pager.currentPage + 1)
                    InputKey.DirectionLeft -> scope.goTo(pager, pager.currentPage - 1)
                    InputKey.PageDown, InputKey.DirectionDown, InputKey.Spacebar -> {
                        val scroll = scrollOf(pager.currentPage)
                        if (scroll.value < scroll.maxValue) scope.launch { scroll.animateScrollBy(scroll.viewportSize * 0.8f) }
                        else scope.goTo(pager, pager.currentPage + 1)
                    }
                    InputKey.PageUp, InputKey.DirectionUp -> {
                        val scroll = scrollOf(pager.currentPage)
                        if (scroll.value > 0) scope.launch { scroll.animateScrollBy(-scroll.viewportSize * 0.8f) }
                        else scope.goTo(pager, pager.currentPage - 1)
                    }
                    else -> return@onPreviewKeyEvent false
                }
                true
            },
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose, modifier = Modifier.size(40.dp)) { Icon(Icons.Default.Close, contentDescription = "Close") }
            Text(
                "${pager.currentPage + 1} / ${list.size}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("performance_position"),
            )
            Spacer(Modifier.weight(1f))
            ShowLyricsCheckbox(showLyrics, viewModel::setShowLyrics, label = "Lyrics")
            IconToggleButton(checked = showTranspose, onCheckedChange = { showTranspose = it }, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Default.SwapVert, contentDescription = "Transpose")
            }
            IconToggleButton(checked = showMetronome, onCheckedChange = { showMetronome = it }, modifier = Modifier.size(40.dp)) {
                Icon(MetronomeIcon, contentDescription = "Metronome")
            }
            IconButton(onClick = { viewModel.changeFontScale(-1) }, enabled = fontScale > MIN_FONT_SCALE + 0.01f, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Default.TextDecrease, contentDescription = "Smaller text")
            }
            IconButton(onClick = { viewModel.changeFontScale(1) }, enabled = fontScale < MAX_FONT_SCALE - 0.01f, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Default.TextIncrease, contentDescription = "Larger text")
            }
        }
        if (showTranspose) {
            // Follows the song on screen, like the metronome. Changes apply to this setlist only.
            val current = list[pager.currentPage.coerceIn(0, list.lastIndex)]
            LiveTransposeBar(
                displayKey = current.plan.displayKey,
                semitones = current.plan.semitones,
                canReset = current.keyChanged || current.plan.semitones != 0,
                onDown = { viewModel.transposeBy(current.entryId, -1) },
                onUp = { viewModel.transposeBy(current.entryId, 1) },
                onReset = { viewModel.resetTranspose(current.entryId) },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
        if (showMetronome) {
            // Follows the song on screen: swiping to the next song switches to its tempo and time.
            val current = list[pager.currentPage.coerceIn(0, list.lastIndex)]
            MetronomeControls(
                bpm = current.song.tempo ?: DEFAULT_BPM,
                beatsPerBar = beatsPerBar(current.song.timeSignature ?: current.document.metadata["time"]),
                onBpmChange = { viewModel.setTempo(current.song.id, it) },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
        noteEditFor?.let { entryId ->
            LiveNoteDialog(
                initial = list.firstOrNull { it.entryId == entryId }?.noteAfter.orEmpty(),
                onSave = { viewModel.setNote(entryId, it); noteEditFor = null },
                onDismiss = { noteEditFor = null },
            )
        }
        HorizontalPager(
            state = pager,
            modifier = Modifier.weight(1f),
            key = { list[it].entryId },
            beyondViewportPageCount = 1,
        ) { index ->
            val page = list[index]
            val next = list.getOrNull(index + 1)
            val scale by animateFloatAsState(fontScale, label = "fontScale")
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollOf(index))
                    .padding(horizontal = 20.dp, vertical = 8.dp),
            ) {
                Text(
                    page.song.title,
                    style = MaterialTheme.typography.headlineSmall.let { it.copy(fontSize = it.fontSize * scale) },
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val details = listOfNotNull(
                    page.plan.displayKey?.let { "Key $it" },
                    page.song.capo.takeIf { it > 0 }?.let { "Capo $it" },
                    page.song.tempo?.let { "$it BPM" },
                    page.song.artist,
                ).joinToString("  ·  ")
                if (details.isNotEmpty()) {
                    Text(details, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                }
                Spacer(Modifier.height(16.dp))
                list.getOrNull(index - 1)?.noteAfter?.let { NoteCard("From the previous song", it, scale) }
                SongChart(page.document, page.plan.renderer, fontScale = scale, showLyrics = showLyrics, timeSignature = page.song.timeSignature)
                Spacer(Modifier.height(32.dp))
                // Right above "Next: …": a cue for modulations, editable on the spot.
                if (page.noteAfter != null) {
                    NoteCard("Before the next song", page.noteAfter, scale, onClick = { noteEditFor = page.entryId })
                } else if (next != null) {
                    TextButton(onClick = { noteEditFor = page.entryId }, modifier = Modifier.testTag("add_live_note")) {
                        Text("+ Add note before next song")
                    }
                }
                if (next != null) {
                    TextButton(onClick = { scope.goTo(pager, index + 1) }) {
                        Text("Next: ${next.song.title}" + (next.plan.displayKey?.let { " ($it)" } ?: ""))
                    }
                } else {
                    Text("End of setlist", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(48.dp))
            }
        }
    }
}

/** A highlighted note between songs, e.g. a modulation cue. */
@Composable
private fun NoteCard(label: String, text: String, scale: Float, onClick: (() -> Unit)? = null) {
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .testTag("setlist_note"),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text(text, style = MaterialTheme.typography.bodyLarge.let { it.copy(fontSize = it.fontSize * scale) })
        }
    }
}

/** Key shown for the current song with − / + to shift it a semitone for this setlist. */
@Composable
private fun LiveTransposeBar(
    displayKey: String?,
    semitones: Int,
    canReset: Boolean,
    onDown: () -> Unit,
    onUp: () -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier.testTag("live_transpose"), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onDown) { Icon(Icons.Default.Remove, contentDescription = "Transpose down") }
        Text(
            listOfNotNull(displayKey?.let { "Key $it" }, formatSemitones(semitones)).joinToString("  ·  "),
            style = MaterialTheme.typography.titleMedium,
        )
        IconButton(onClick = onUp) { Icon(Icons.Default.Add, contentDescription = "Transpose up") }
        if (canReset) TextButton(onClick = onReset) { Text("Reset") }
    }
}

/** Edits the note shown above "Next: …" for one song, e.g. "Modulate up a tone". */
@Composable
private fun LiveNoteDialog(initial: String, onSave: (String?) -> Unit, onDismiss: () -> Unit) {
    var text by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Note before the next song") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("e.g. Modulate up a tone into the last chorus") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth().testTag("note_text"),
            )
        },
        confirmButton = { TextButton(onClick = { onSave(text) }) { Text("Save") } },
        dismissButton = {
            Row {
                if (initial.isNotBlank()) TextButton(onClick = { onSave(null) }) { Text("Remove", color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

private fun CoroutineScope.goTo(pager: PagerState, page: Int) {
    if (page in 0 until pager.pageCount) launch { pager.animateScrollToPage(page) }
}

/** Keeps the screen awake and hides status/navigation bars while performing. */
@Composable
private fun StageMode() {
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        val window = (view.context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            view.keepScreenOn = false
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
    }
}
