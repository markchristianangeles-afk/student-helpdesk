package com.chordx.app.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chordx.app.data.BackupData
import com.chordx.app.data.BackupParseResult
import com.chordx.app.data.ChordXDatabase
import com.chordx.app.data.MAX_BACKUP_BYTES
import com.chordx.app.data.SettingsStore
import com.chordx.app.data.buildBackup
import com.chordx.app.data.parseBackup
import com.chordx.app.data.restoreBackup
import com.chordx.app.data.SongDao
import com.chordx.app.data.buildSongExport
import com.chordx.app.data.ThemeMode
import com.chordx.app.music.Accidentals
import com.chordx.app.ui.chart.label
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(settings: SettingsStore, songs: SongDao, db: ChordXDatabase, onBack: () -> Unit) {
    val theme by settings.themeMode.collectAsStateWithLifecycle(initialValue = null)
    val accidentals by settings.accidentals.collectAsStateWithLifecycle(initialValue = null)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) scope.launch {
            val message = runCatching {
                withContext(Dispatchers.IO) {
                    val all = songs.getAll()
                    val text = buildSongExport(all)
                    val out = context.contentResolver.openOutputStream(uri, "wt") ?: error("Couldn't open the file")
                    out.use { it.write(text.toByteArray(Charsets.UTF_8)) }
                    all.size
                }
            }.fold(
                onSuccess = { if (it == 1) "Exported 1 song." else "Exported $it songs." },
                onFailure = { "Couldn't export your songs." },
            )
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }

    val backupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            val message = runCatching {
                withContext(Dispatchers.IO) {
                    val json = buildBackup(db, settings)
                    val out = context.contentResolver.openOutputStream(uri, "wt") ?: error("Couldn't open the file")
                    out.use { it.write(json.toByteArray(Charsets.UTF_8)) }
                }
            }.fold(onSuccess = { "Backup saved." }, onFailure = { "Couldn't save the backup." })
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }
    var pendingRestore by remember { mutableStateOf<BackupData?>(null) }
    var confirmReplace by remember { mutableStateOf(false) }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("Couldn't open the file")
                    if (bytes.size > MAX_BACKUP_BYTES) BackupParseResult.Bad("That file is too large to be a ChordX backup.")
                    else parseBackup(String(bytes, Charsets.UTF_8).removePrefix("﻿"))
                }
            }.getOrElse { BackupParseResult.Bad("Couldn't read that file.") }
            when (result) {
                is BackupParseResult.Ok -> pendingRestore = result.data
                is BackupParseResult.Bad -> Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
            }
        }
    }
    fun restore(data: BackupData, replace: Boolean) {
        pendingRestore = null
        confirmReplace = false
        scope.launch {
            val message = runCatching { restoreBackup(db, settings, data, replace).message(replace) }
                .getOrElse { "Couldn't restore the backup. Nothing was changed." }
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())) {
            Heading("Theme")
            Column(Modifier.selectableGroup()) {
                ThemeMode.entries.forEach { mode ->
                    Choice(
                        title = when (mode) {
                            ThemeMode.SYSTEM -> "System default"
                            ThemeMode.LIGHT -> "Light"
                            ThemeMode.DARK -> "Dark"
                        },
                        selected = theme == mode,
                        onClick = { scope.launch { settings.setThemeMode(mode) } },
                    )
                }
            }
            HorizontalDivider()
            Heading("Chord spelling")
            Column(Modifier.selectableGroup()) {
                Accidentals.entries.forEach { option ->
                    Choice(
                        title = option.label(),
                        description = when (option) {
                            Accidentals.AUTO -> "Charts show as written. When transposed, chords follow the new key (Bb in F, F# in D)."
                            Accidentals.SHARPS -> "Always use sharps: C#, D#, F#, G#, A#."
                            Accidentals.FLATS -> "Always use flats: Db, Eb, Gb, Ab, Bb."
                        },
                        selected = accidentals == option,
                        onClick = { scope.launch { settings.setAccidentals(option) } },
                    )
                }
            }
            Text(
                "Individual songs can override this from their Transpose panel.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
            HorizontalDivider()
            Heading("Backup")
            ListItem(
                modifier = Modifier.clickable { exportLauncher.launch("chordx-songs.txt") }.testTag("export_songs"),
                headlineContent = { Text("Export all songs") },
                supportingContent = { Text("Save every song to one ChordPro text file. Import it from the Songs screen on another device.") },
            )
            ListItem(
                modifier = Modifier.clickable { backupLauncher.launch("chordx-backup.json") }.testTag("backup_all"),
                headlineContent = { Text("Back up everything") },
                supportingContent = { Text("Save songs, keys, transposes, setlists, notes, devotion journal and settings to one file.") },
            )
            ListItem(
                modifier = Modifier.clickable { restoreLauncher.launch(arrayOf("*/*")) }.testTag("restore_backup"),
                headlineContent = { Text("Restore from backup") },
                supportingContent = { Text("Load a ChordX backup file. You choose whether to add what's missing or replace everything.") },
            )
            HorizontalDivider()
            AboutFooter()
        }
    }

    pendingRestore?.let { data ->
        AlertDialog(
            onDismissRequest = { pendingRestore = null },
            title = { Text("Restore backup") },
            text = {
                Text(
                    "This backup has ${data.songs.size} songs, ${data.setlists.size} setlists and ${data.notes.size} ${if (data.notes.size == 1) "note" else "notes"}.\n\n" +
                        "Add missing: keeps everything on this device and adds what isn't here yet. Settings are left alone.\n\n" +
                        "Replace everything: deletes this device's songs, setlists, notes and devotion journal and uses the backup instead, settings included.",
                )
            },
            confirmButton = { TextButton(onClick = { restore(data, replace = false) }, modifier = Modifier.testTag("restore_merge")) { Text("Add missing") } },
            dismissButton = {
                Row {
                    TextButton(onClick = { confirmReplace = true }, modifier = Modifier.testTag("restore_replace")) {
                        Text("Replace everything", color = MaterialTheme.colorScheme.error)
                    }
                    TextButton(onClick = { pendingRestore = null }) { Text("Cancel") }
                }
            },
        )
        if (confirmReplace) {
            AlertDialog(
                onDismissRequest = { confirmReplace = false },
                title = { Text("Replace everything?") },
                text = { Text("All songs, setlists and notes on this device will be deleted and replaced by the backup. This can't be undone. Back up first if you might need them.") },
                confirmButton = {
                    TextButton(onClick = { restore(data, replace = true) }, modifier = Modifier.testTag("restore_replace_confirm")) {
                        Text("Replace", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = { TextButton(onClick = { confirmReplace = false }) { Text("Cancel") } },
            )
        }
    }
}

/** App name, version and credit, at the very bottom of Settings. */
@Composable
private fun AboutFooter() {
    val context = LocalContext.current
    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull()
    }
    Column(
        Modifier.fillMaxWidth().padding(24.dp).testTag("about_footer"),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            listOfNotNull("ChordX", version?.let { "v$it" }).joinToString(" "),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "Created by Christian Angeles",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Heading(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun Choice(title: String, selected: Boolean, onClick: () -> Unit, description: String? = null) {
    ListItem(
        modifier = Modifier.selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        headlineContent = { Text(title) },
        supportingContent = description?.let { { Text(it) } },
        leadingContent = { RadioButton(selected = selected, onClick = null) },
    )
}
