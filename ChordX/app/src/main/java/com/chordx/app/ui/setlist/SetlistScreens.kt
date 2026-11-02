package com.chordx.app.ui.setlist

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.zIndex
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.chordx.app.data.Setlist
import com.chordx.app.music.Key
import com.chordx.app.ui.song.EmptyState
import com.chordx.app.ui.song.SongListViewModel

private const val MILLIS_PER_DAY = 86_400_000L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetlistListScreen(
    viewModel: SetlistListViewModel,
    onOpenSetlist: (Long) -> Unit,
    onOpenSettings: () -> Unit,
    bottomBar: @Composable () -> Unit,
) {
    val summaries by viewModel.summaries.collectAsStateWithLifecycle()
    var creating by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Setlists") },
                actions = {
                    IconButton(onClick = onOpenSettings) { Icon(Icons.Default.Settings, contentDescription = "Settings") }
                },
            )
        },
        bottomBar = bottomBar,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { creating = true },
                modifier = Modifier.testTag("new_setlist"),
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("New setlist") },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            val list = summaries
            when {
                list == null -> Unit
                list.isEmpty() -> EmptyState("No setlists yet.\nTap New setlist to plan a service or gig.")
                else -> LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
                    items(list, key = { it.setlist.id }) { summary ->
                        ListItem(
                            modifier = Modifier.clickable { onOpenSetlist(summary.setlist.id) },
                            headlineContent = { Text(summary.setlist.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            supportingContent = {
                                Text(
                                    listOfNotNull(
                                        summary.setlist.eventDate?.let(::formatEventDate),
                                        if (summary.songCount == 1) "1 song" else "${summary.songCount} songs",
                                    ).joinToString("  ·  ")
                                )
                            },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    if (creating) {
        SetlistDialog(
            title = "New setlist",
            initial = null,
            onDismiss = { creating = false },
            onSave = { name, date ->
                creating = false
                viewModel.create(name, date, onOpenSetlist)
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetlistDetailScreen(
    viewModel: SetlistViewModel,
    onBack: () -> Unit,
    onAddSongs: () -> Unit,
    onPerform: (startIndex: Int) -> Unit,
    onDeleted: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val loaded = state as? SetlistState.Loaded
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val exportPdf = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        val setlist = loaded?.setlist
        if (uri == null || setlist == null) return@rememberLauncherForActivityResult
        scope.launch {
            val result = runCatching {
                val songs = viewModel.pdfSongs()
                withContext(Dispatchers.IO) {
                    val out = context.contentResolver.openOutputStream(uri, "wt") ?: error("Could not open the file.")
                    out.use { writeSetlistPdf(it, setlist.name, setlist.eventDate?.let(::formatEventDate), setlist.notes, songs) }
                }
            }
            Toast.makeText(context, if (result.isSuccess) "Exported PDF." else "Could not export the PDF.", Toast.LENGTH_LONG).show()
        }
    }
    var editing by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var keyFor by remember { mutableStateOf<SetlistRow?>(null) }
    var noteFor by remember { mutableStateOf<SetlistRow?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(loaded?.setlist?.name.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    if (loaded != null) {
                        IconButton(onClick = { onPerform(0) }, enabled = loaded.rows.isNotEmpty()) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Perform")
                        }
                        IconButton(
                            onClick = { exportPdf.launch(pdfFileName(loaded.setlist.name)) },
                            enabled = loaded.rows.isNotEmpty(),
                            modifier = Modifier.testTag("export_setlist_pdf"),
                        ) {
                            Icon(Icons.Default.PictureAsPdf, contentDescription = "Export setlist summary to PDF")
                        }
                        IconButton(onClick = { editing = true }) { Icon(Icons.Default.Edit, contentDescription = "Edit setlist") }
                        IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.Delete, contentDescription = "Delete setlist") }
                    }
                },
            )
        },
        floatingActionButton = {
            if (loaded != null) {
                ExtendedFloatingActionButton(
                    onClick = onAddSongs,
                    modifier = Modifier.testTag("add_songs"),
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text("Add songs") },
                )
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (val s = state) {
                SetlistState.Loading -> Unit
                SetlistState.Missing -> EmptyState("This setlist no longer exists.")
                is SetlistState.Loaded -> Column(Modifier.fillMaxSize()) {
                    s.setlist.eventDate?.let {
                        Text(
                            formatEventDate(it),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                    if (s.rows.isEmpty()) {
                        EmptyState("No songs yet.\nTap Add songs to build the set.")
                    } else {
                        val listState = rememberLazyListState()
                        val drag = rememberDragReorderState(listState)
                        val haptics = LocalHapticFeedback.current
                        val count = s.rows.size
                        LazyColumn(state = listState, contentPadding = PaddingValues(bottom = 96.dp)) {
                            itemsIndexed(s.rows, key = { _, row -> row.entry.id }) { index, row ->
                                val currentIndex by rememberUpdatedState(index)
                                val currentCount by rememberUpdatedState(count)
                                val dragged = drag.draggedId == row.entry.id
                                val slide by animateFloatAsState(if (dragged) 0f else drag.shiftFor(index, count), label = "slide")
                                Column(
                                    Modifier
                                        .zIndex(if (dragged) 1f else 0f)
                                        .graphicsLayer {
                                            translationY = if (dragged) drag.offset else slide
                                            shadowElevation = if (dragged) 12.dp.toPx() else 0f
                                        }
                                        .background(MaterialTheme.colorScheme.surface)
                                        .pointerInput(row.entry.id) {
                                            detectDragGesturesAfterLongPress(
                                                onDragStart = {
                                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    drag.start(row.entry.id, currentIndex)
                                                },
                                                onDrag = { change, amount ->
                                                    change.consume()
                                                    drag.drag(amount.y)
                                                },
                                                onDragEnd = { drag.end(currentCount) { from, to -> viewModel.move(from, to) } },
                                                onDragCancel = { drag.cancel() },
                                            )
                                        },
                                ) {
                                SetlistRowItem(
                                    index = index,
                                    count = s.rows.size,
                                    row = row,
                                    onClick = { onPerform(index) },
                                    onMove = { to -> viewModel.move(index, to) },
                                    onChangeKey = { keyFor = row },
                                    onEditNote = { noteFor = row },
                                    onRemove = { viewModel.remove(row.entry.id) },
                                )
                                row.entry.notes?.takeIf { it.isNotBlank() }?.let { note ->
                                    ListItem(
                                        modifier = Modifier.clickable { noteFor = row }.testTag("note_row_${row.entry.id}"),
                                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                                        overlineContent = { Text("Note") },
                                        headlineContent = { Text(note) },
                                    )
                                }
                                HorizontalDivider()
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (editing && loaded != null) {
        SetlistDialog(
            title = "Edit setlist",
            initial = loaded.setlist,
            onDismiss = { editing = false },
            onSave = { name, date -> editing = false; viewModel.update(name, date) },
        )
    }
    if (confirmDelete && loaded != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete “${loaded.setlist.name}”?") },
            text = { Text("The songs stay in your library. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; viewModel.delete(onDeleted) }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
    keyFor?.let { row ->
        KeyDialog(
            row = row,
            onDismiss = { keyFor = null },
            onChoose = { key -> keyFor = null; viewModel.setKey(row.entry.id, key) },
        )
    }
    noteFor?.let { row ->
        NoteDialog(
            row = row,
            nextTitle = loaded?.rows?.getOrNull(loaded.rows.indexOfFirst { it.entry.id == row.entry.id } + 1)?.song?.title,
            onDismiss = { noteFor = null },
            onSave = { text -> noteFor = null; viewModel.setNote(row.entry.id, text) },
        )
    }
}

/** Edits the note shown between this song and the next (e.g. a modulation cue). */
@Composable
private fun NoteDialog(row: SetlistRow, nextTitle: String?, onDismiss: () -> Unit, onSave: (String?) -> Unit) {
    var text by rememberSaveable { mutableStateOf(row.entry.notes.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (nextTitle != null) "Note: “${row.song.title}” → “$nextTitle”" else "Note after “${row.song.title}”") },
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
                if (!row.entry.notes.isNullOrBlank()) {
                    TextButton(onClick = { onSave(null) }) { Text("Remove", color = MaterialTheme.colorScheme.error) }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

@Composable
private fun SetlistRowItem(
    index: Int,
    count: Int,
    row: SetlistRow,
    onClick: () -> Unit,
    onMove: (Int) -> Unit,
    onChangeKey: () -> Unit,
    onEditNote: () -> Unit,
    onRemove: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        leadingContent = {
            Text("${index + 1}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.widthIn(min = 24.dp))
        },
        headlineContent = { Text(row.song.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = row.song.artist?.let { { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis) } },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                row.displayKey?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (row.keyChanged) FontWeight.Bold else FontWeight.Normal,
                        color = if (row.keyChanged) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                    )
                }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, contentDescription = "Options for ${row.song.title}") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        if (index > 0) DropdownMenuItem(text = { Text("Move up") }, onClick = { menu = false; onMove(index - 1) })
                        if (index < count - 1) DropdownMenuItem(text = { Text("Move down") }, onClick = { menu = false; onMove(index + 1) })
                        if (index > 0) DropdownMenuItem(text = { Text("Move to top") }, onClick = { menu = false; onMove(0) })
                        if (index < count - 1) DropdownMenuItem(text = { Text("Move to bottom") }, onClick = { menu = false; onMove(count - 1) })
                        DropdownMenuItem(text = { Text("Change key") }, onClick = { menu = false; onChangeKey() })
                        DropdownMenuItem(
                            text = { Text(if (row.entry.notes.isNullOrBlank()) "Add note after" else "Edit note after") },
                            onClick = { menu = false; onEditNote() },
                        )
                        DropdownMenuItem(text = { Text("Remove from setlist") }, onClick = { menu = false; onRemove() })
                    }
                }
            }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun KeyDialog(row: SetlistRow, onDismiss: () -> Unit, onChoose: (String?) -> Unit) {
    val songKey = row.song.key?.let(Key::parse)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Key for “${row.song.title}”") },
        text = {
            if (songKey == null) {
                Text("This song has no key set. Add its key in the song editor first, then you can choose a key for this setlist.")
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Only changes this setlist. The song itself is written in ${row.song.key}.", style = MaterialTheme.typography.bodySmall)
                    // The song's own mode first, then the other one, so minor keys are always on offer.
                    listOf(songKey.minor, !songKey.minor).forEach { minor ->
                        Text(
                            if (minor) "Minor keys" else "Major keys",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Key.all(minor).forEach { key ->
                                val name = key.toString()
                                val selected = (row.entry.keyOverride ?: row.song.key) == name
                                FilterChip(selected = selected, onClick = { onChoose(if (name == row.song.key) null else name) }, label = { Text(name) })
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (songKey != null && row.entry.keyOverride != null) {
                TextButton(onClick = { onChoose(null) }) { Text("Use song's key") }
            } else {
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        },
        dismissButton = if (songKey != null && row.entry.keyOverride != null) {
            { TextButton(onClick = onDismiss) { Text("Cancel") } }
        } else null,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SetlistDialog(title: String, initial: Setlist?, onDismiss: () -> Unit, onSave: (String, Long?) -> Unit) {
    var name by rememberSaveable { mutableStateOf(initial?.name.orEmpty()) }
    var date by rememberSaveable { mutableStateOf(initial?.eventDate) }
    var picking by rememberSaveable { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("setlist_name"),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(date?.let(::formatEventDate) ?: "No date", modifier = Modifier.weight(1f))
                    TextButton(onClick = { picking = true }) { Text(if (date == null) "Set date" else "Change") }
                    if (date != null) TextButton(onClick = { date = null }) { Text("Clear") }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name, date) }, enabled = name.isNotBlank()) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )

    if (picking) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = (date ?: (System.currentTimeMillis() / MILLIS_PER_DAY)) * MILLIS_PER_DAY)
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { date = it / MILLIS_PER_DAY }
                    picking = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text("Cancel") } },
        ) {
            DatePicker(pickerState)
        }
    }
}

/** Pick songs to append to a setlist, in the order they're ticked. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddSongsScreen(
    songsViewModel: SongListViewModel,
    onBack: () -> Unit,
    onAdd: (List<Long>) -> Unit,
) {
    val songs by songsViewModel.results.collectAsStateWithLifecycle()
    val picked = remember { mutableStateListOf<Long>() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add songs") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    TextButton(onClick = { onAdd(picked.toList()) }, enabled = picked.isNotEmpty()) {
                        Text(if (picked.isEmpty()) "Add" else "Add (${picked.size})")
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            OutlinedTextField(
                value = songsViewModel.query,
                onValueChange = { songsViewModel.query = it },
                placeholder = { Text("Search title or artist") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).testTag("add_search"),
            )
            val list = songs
            when {
                list == null -> Unit
                list.isEmpty() -> EmptyState(if (songsViewModel.query.isBlank()) "Your library is empty. Add songs on the Songs tab first." else "No songs match.")
                else -> LazyColumn {
                    items(list, key = { it.id }) { song ->
                        val order = picked.indexOf(song.id)
                        val toggle = { if (order >= 0) picked.remove(song.id) else picked.add(song.id) }
                        ListItem(
                            modifier = Modifier.clickable { toggle() },
                            leadingContent = { Checkbox(checked = order >= 0, onCheckedChange = { toggle() }) },
                            headlineContent = { Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            supportingContent = song.artist?.let { { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis) } },
                            trailingContent = if (order >= 0) {
                                { Text("#${order + 1}", color = MaterialTheme.colorScheme.primary) }
                            } else null,
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}
