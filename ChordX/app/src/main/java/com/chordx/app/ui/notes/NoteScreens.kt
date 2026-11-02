package com.chordx.app.ui.notes

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.chordx.app.data.NoteDao
import com.chordx.app.data.SermonNote
import com.chordx.app.ui.song.EmptyState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

class NoteListViewModel(notes: NoteDao) : ViewModel() {
    /** Null until the first database result arrives. */
    val notes: StateFlow<List<SermonNote>?> = notes.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    companion object {
        fun factory(notes: NoteDao): ViewModelProvider.Factory = viewModelFactory {
            initializer { NoteListViewModel(notes) }
        }
    }
}

/** Creates a note when [noteId] is null, otherwise edits that note. Empty new notes aren't saved. */
class NoteEditViewModel(private val notes: NoteDao, noteId: Long?) : ViewModel() {
    var title by mutableStateOf("")
    var body by mutableStateOf("")
    var loaded by mutableStateOf(noteId == null)
        private set

    private var existing: SermonNote? = null

    init {
        if (noteId != null) {
            viewModelScope.launch {
                notes.getById(noteId)?.let {
                    existing = it
                    title = it.title
                    body = it.body
                }
                loaded = true
            }
        }
    }

    fun save(onDone: () -> Unit) {
        if (!loaded) { onDone(); return }
        viewModelScope.launch {
            val current = existing
            when {
                current == null && title.isBlank() && body.isBlank() -> Unit
                current == null -> {
                    val note = SermonNote(title = title.trim(), body = body)
                    existing = note.copy(id = notes.insert(note))
                }
                current.title != title.trim() || current.body != body -> {
                    val note = current.copy(title = title.trim(), body = body, updatedAt = System.currentTimeMillis())
                    notes.update(note)
                    existing = note
                }
            }
            onDone()
        }
    }

    fun delete(onDone: () -> Unit) {
        viewModelScope.launch {
            existing?.let { notes.delete(it) }
            existing = null
            onDone()
        }
    }

    companion object {
        fun factory(notes: NoteDao, noteId: Long?): ViewModelProvider.Factory = viewModelFactory {
            initializer { NoteEditViewModel(notes, noteId) }
        }
    }
}

/** Bible versions offered for verse search. [code] is the BibleGateway version code. */
enum class BibleVersion(val code: String, val label: String) {
    NIV("NIV", "NIV – New International Version"),
    NKJV("NKJV", "NKJV – New King James Version"),
    KJV("KJV", "KJV – King James Version"),
    ESV("ESV", "ESV – English Standard Version"),
    NLT("NLT", "NLT – New Living Translation"),
    AMP("AMP", "AMP – Amplified Bible"),
    GNT("GNT", "GNT – Good News Translation"),
    CEV("CEV", "CEV – Contemporary English Version"),
    MSG("MSG", "The Message"),
    TPT("TPT", "Passion Translation"),
    MBB("MBBTAG", "Mabuting Balita Biblia"),
}

/**
 * The passage on BibleGateway. These translations are copyrighted, so the text is opened
 * online rather than stored in the app.
 */
internal fun passageUrl(reference: String, version: BibleVersion): String =
    "https://www.biblegateway.com/passage/?search=${Uri.encode(reference.trim())}&version=${version.code}"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteListScreen(
    viewModel: NoteListViewModel,
    onOpenNote: (Long) -> Unit,
    onNewNote: () -> Unit,
    bottomBar: @Composable () -> Unit = {},
) {
    val notes by viewModel.notes.collectAsStateWithLifecycle()
    val dateFormat = remember { DateFormat.getDateInstance(DateFormat.MEDIUM) }
    Scaffold(
        topBar = { TopAppBar(title = { Text("Notes") }) },
        bottomBar = bottomBar,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNewNote,
                modifier = Modifier.testTag("new_note"),
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("New note") },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            val list = notes
            when {
                list == null -> Unit
                list.isEmpty() -> EmptyState("No notes yet.\nTap New note to start one, for example for Sunday's sermon.")
                else -> LazyColumn {
                    items(list, key = { it.id }) { note ->
                        ListItem(
                            modifier = Modifier.clickable { onOpenNote(note.id) },
                            headlineContent = { Text(note.title.ifBlank { "Untitled note" }, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            supportingContent = {
                                Text(
                                    note.body.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty(),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            trailingContent = { Text(dateFormat.format(Date(note.updatedAt)), style = MaterialTheme.typography.labelSmall) },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteEditScreen(viewModel: NoteEditViewModel, onDone: () -> Unit) {
    val context = LocalContext.current
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var reference by rememberSaveable { mutableStateOf("") }
    var version by rememberSaveable { mutableStateOf(BibleVersion.NIV) }
    var versionMenu by remember { mutableStateOf(false) }
    var bibleOpen by rememberSaveable { mutableStateOf(false) }
    val canLookUp by remember { derivedStateOf { reference.isNotBlank() } }
    var lookupFailed by remember { mutableStateOf(false) }

    BackHandler { viewModel.save(onDone) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Note") },
                navigationIcon = {
                    IconButton(onClick = { viewModel.save(onDone) }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.Delete, contentDescription = "Delete note") }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().imePadding().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = viewModel.title,
                onValueChange = { viewModel.title = it },
                label = { Text("Title (e.g. Sunday sermon)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("note_title"),
            )
            // One collapsible section holds the version list, the verse box and the Search button,
            // so it can be folded away and the note gets the whole screen while typing.
            Card(Modifier.fillMaxWidth().testTag("bible_section")) {
                Row(
                    Modifier.fillMaxWidth().clickable { bibleOpen = !bibleOpen }.padding(horizontal = 16.dp, vertical = 12.dp).testTag("bible_header"),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Bible search", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    Text(version.code, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Icon(
                        if (bibleOpen) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                        contentDescription = if (bibleOpen) "Hide Bible search" else "Show Bible search",
                    )
                }
                if (bibleOpen) {
                    Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box {
                            OutlinedButton(onClick = { versionMenu = true }, modifier = Modifier.fillMaxWidth().testTag("bible_version")) {
                                Text("Bible version: ${version.label}", modifier = Modifier.weight(1f))
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                            }
                            DropdownMenu(expanded = versionMenu, onDismissRequest = { versionMenu = false }) {
                                BibleVersion.entries.forEach { option ->
                                    DropdownMenuItem(
                                        text = { Text(option.label) },
                                        onClick = { version = option; versionMenu = false },
                                    )
                                }
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = reference,
                                onValueChange = { reference = it; lookupFailed = false },
                                label = { Text("Search Bible verse, e.g. John 3:16") },
                                singleLine = true,
                                modifier = Modifier.weight(1f).testTag("verse_reference"),
                            )
                            Button(
                                enabled = canLookUp,
                                onClick = {
                                    try {
                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(passageUrl(reference, version))))
                                    } catch (_: ActivityNotFoundException) {
                                        lookupFailed = true
                                    }
                                },
                            ) { Text("Search") }
                        }
                        if (lookupFailed) Text("No app can open the link.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            OutlinedTextField(
                value = viewModel.body,
                onValueChange = { viewModel.body = it },
                label = { Text("Notes") },
                minLines = 8,
                modifier = Modifier.fillMaxWidth().weight(1f).testTag("note_body"),
            )
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this note?") },
            text = { Text("This can't be undone.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; viewModel.delete(onDone) }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}
