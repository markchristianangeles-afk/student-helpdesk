package com.chordx.app.ui.song

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.chordx.app.ui.chart.DiagnosticsCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongEditScreen(
    viewModel: SongEditViewModel,
    onSaved: (Long) -> Unit,
    onCancel: () -> Unit,
) {
    var confirmDiscard by remember { mutableStateOf(false) }
    val leave = { if (viewModel.isDirty) confirmDiscard = true else onCancel() }

    val savedId = viewModel.savedId
    LaunchedEffect(savedId) { if (savedId != null) onSaved(savedId) }
    BackHandler(enabled = viewModel.isDirty && savedId == null) { confirmDiscard = true }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when {
                            viewModel.isImport -> "Review import"
                            viewModel.isNew -> "New song"
                            else -> "Edit song"
                        }
                    )
                },
                navigationIcon = {
                    IconButton(onClick = leave) { Icon(Icons.Default.Close, contentDescription = "Cancel") }
                },
                actions = {
                    IconButton(onClick = viewModel::save, enabled = viewModel.loaded) {
                        Icon(Icons.Default.Check, contentDescription = "Save")
                    }
                },
            )
        },
    ) { padding ->
        if (!viewModel.loaded) return@Scaffold
        val form = viewModel.form
        val errors = viewModel.errors
        Column(
            Modifier
                .padding(padding)
                .consumeWindowInsets(padding)
                .imePadding()
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (viewModel.convertedOnImport) {
                Notice("This file had chords written above the lyrics. It's been converted to ChordPro — check the chords sit on the right syllables.")
            }
            FormField(
                label = "Title", value = form.title, error = errors[SongField.TITLE], tag = "field_title",
                capitalization = KeyboardCapitalization.Words,
            ) { v -> viewModel.update { it.copy(title = v) } }
            FormField(
                label = "Artist", value = form.artist, error = null, tag = "field_artist",
                capitalization = KeyboardCapitalization.Words,
            ) { v -> viewModel.update { it.copy(artist = v) } }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FormField(
                    label = "Key", value = form.key, error = errors[SongField.KEY], tag = "field_key",
                    modifier = Modifier.weight(1f),
                ) { v -> viewModel.update { it.copy(key = v) } }
                FormField(
                    label = "Capo", value = form.capo, error = errors[SongField.CAPO], tag = "field_capo",
                    keyboardType = KeyboardType.Number, modifier = Modifier.weight(1f),
                ) { v -> viewModel.update { it.copy(capo = v) } }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FormField(
                    label = "Tempo (BPM)", value = form.tempo, error = errors[SongField.TEMPO], tag = "field_tempo",
                    keyboardType = KeyboardType.Number, modifier = Modifier.weight(1f),
                ) { v -> viewModel.update { it.copy(tempo = v) } }
                FormField(
                    label = "Time", value = form.timeSignature, error = errors[SongField.TIME_SIGNATURE], tag = "field_time",
                    modifier = Modifier.weight(1f),
                ) { v -> viewModel.update { it.copy(timeSignature = v) } }
            }
            OutlinedTextField(
                value = form.content,
                onValueChange = { v -> viewModel.update { it.copy(content = v) } },
                label = { Text("Chart (ChordPro)") },
                placeholder = { Text("{start_of_verse}\n[G]Amazing [C]grace, how [G]sweet the sound\n{end_of_verse}") },
                textStyle = TextStyle(fontFamily = FontFamily.Monospace),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, autoCorrectEnabled = false),
                modifier = Modifier.fillMaxWidth().heightIn(min = 280.dp).testTag("field_content"),
            )
            if (viewModel.canConvertPlainText) {
                Notice("This looks like a plain chord sheet (chords above lyrics, or dash timing like C--G--).") {
                    TextButton(onClick = viewModel::convertPlainText) { Text("Convert to ChordPro") }
                }
            }
            DiagnosticsCard(viewModel.chartDiagnostics)
        }
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("Discard changes?") },
            text = {
                Text(
                    when {
                        viewModel.isImport -> "The imported song won't be added."
                        viewModel.isNew -> "This song hasn't been saved."
                        else -> "Your edits haven't been saved."
                    }
                )
            },
            confirmButton = { TextButton(onClick = { confirmDiscard = false; onCancel() }) { Text("Discard") } },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("Keep editing") } },
        )
    }
}

@Composable
private fun Notice(text: String, action: (@Composable () -> Unit)? = null) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = if (action == null) 12.dp else 0.dp)) {
            Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
            action?.invoke()
        }
    }
}

@Composable
private fun FormField(
    label: String,
    value: String,
    error: String?,
    tag: String,
    modifier: Modifier = Modifier.fillMaxWidth(),
    keyboardType: KeyboardType = KeyboardType.Text,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.None,
    onValueChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = capitalization, keyboardType = keyboardType, autoCorrectEnabled = false),
        modifier = modifier.testTag(tag),
    )
}
