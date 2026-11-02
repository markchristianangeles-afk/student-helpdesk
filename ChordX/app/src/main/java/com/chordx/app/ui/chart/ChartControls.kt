package com.chordx.app.ui.chart

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.chordx.app.chordpro.Diagnostic
import com.chordx.app.chordpro.Severity
import com.chordx.app.music.Accidentals

fun Accidentals.label(): String = when (this) {
    Accidentals.AUTO -> "Auto"
    Accidentals.SHARPS -> "Sharps ♯"
    Accidentals.FLATS -> "Flats ♭"
}

fun formatSemitones(semitones: Int): String = when {
    semitones > 0 -> "+$semitones"
    semitones < 0 -> "−${-semitones}"
    else -> "0"
}

/** Transpose up/down plus the song's sharp/flat choice ("Default" follows the app setting). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransposeControls(
    semitones: Int,
    displayKey: String?,
    songAccidentals: Accidentals?,
    defaultAccidentals: Accidentals,
    onTranspose: (Int) -> Unit,
    onReset: () -> Unit,
    onAccidentals: (Accidentals?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.testTag("transpose_controls"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalIconButton(onClick = { onTranspose(-1) }) { Icon(Icons.Default.Remove, contentDescription = "Transpose down") }
            Column(Modifier.widthIn(min = 72.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    displayKey?.let { "Key $it" } ?: "Transpose",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                Text(formatSemitones(semitones), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            FilledTonalIconButton(onClick = { onTranspose(1) }) { Icon(Icons.Default.Add, contentDescription = "Transpose up") }
            if (semitones != 0) TextButton(onClick = onReset) { Text("Reset") }
        }
        val options = listOf<Accidentals?>(null, Accidentals.AUTO, Accidentals.SHARPS, Accidentals.FLATS)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, option ->
                SegmentedButton(
                    selected = songAccidentals == option,
                    onClick = { onAccidentals(option) },
                    shape = SegmentedButtonDefaults.itemShape(index, options.size),
                    label = {
                        Text(
                            when (option) {
                                null -> "Default"
                                Accidentals.AUTO -> "Auto"
                                Accidentals.SHARPS -> "Sharp"
                                Accidentals.FLATS -> "Flat"
                            },
                            maxLines = 1,
                            style = MaterialTheme.typography.labelLarge,
                        )
                    },
                )
            }
        }
        if (songAccidentals == null) {
            Text(
                "Default spelling: ${defaultAccidentals.label()} (change in Settings)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * "Show lyrics" checkbox; unticked shows the chart as chords only. The whole row is tappable.
 * [note], when given, is shown beside the label (the song view explains it only matters for charts with lyrics).
 */
@Composable
fun ShowLyricsCheckbox(
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "Show lyrics",
    note: String? = null,
) {
    Row(
        modifier
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = onChange)
            .padding(end = 8.dp)
            .testTag("show_lyrics"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = null, modifier = Modifier.padding(8.dp))
        Text(label, style = MaterialTheme.typography.labelLarge)
        if (note != null) {
            Text(" $note", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

/** Lists parser problems with line numbers; errors first. */
@Composable
fun DiagnosticsCard(diagnostics: List<Diagnostic>, modifier: Modifier = Modifier, maxShown: Int = 5) {
    if (diagnostics.isEmpty()) return
    val errors = diagnostics.count { it.severity == Severity.ERROR }
    val warnings = diagnostics.size - errors
    val sorted = diagnostics.sortedWith(compareBy({ it.severity != Severity.ERROR }, { it.line }))
    Card(
        modifier = modifier.fillMaxWidth().testTag("diagnostics"),
        colors = CardDefaults.cardColors(
            containerColor = if (errors > 0) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = if (errors > 0) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                listOfNotNull(
                    errors.takeIf { it > 0 }?.let { if (it == 1) "1 problem" else "$it problems" },
                    warnings.takeIf { it > 0 }?.let { if (it == 1) "1 note" else "$it notes" },
                ).joinToString(", ") + " in this chart",
                style = MaterialTheme.typography.titleSmall,
            )
            sorted.take(maxShown).forEach { d ->
                Text(
                    "Line ${d.line}: ${d.message}",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = if (d.severity == Severity.ERROR) FontWeight.Medium else FontWeight.Normal,
                )
            }
            if (sorted.size > maxShown) {
                Text("…and ${sorted.size - maxShown} more", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
