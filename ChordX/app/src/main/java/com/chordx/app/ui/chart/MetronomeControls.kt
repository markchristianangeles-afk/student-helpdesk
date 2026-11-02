package com.chordx.app.ui.chart

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chordx.app.music.MAX_BPM
import com.chordx.app.music.MIN_BPM
import com.chordx.app.music.TapTempo
import com.chordx.app.music.clampBpm

/**
 * Metronome: play/stop, tempo −/+ and tap tempo, with a dot per beat that lights up in
 * time (beat 1 is larger). [onBpmChange] saves the tempo to the song. The click stops when
 * this leaves the screen.
 */
@Composable
fun MetronomeControls(
    bpm: Int,
    beatsPerBar: Int,
    onBpmChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val metronome = remember { Metronome() }
    DisposableEffect(metronome) { onDispose { metronome.release() } }
    LaunchedEffect(bpm, beatsPerBar) {
        metronome.bpm = bpm
        metronome.beatsPerBar = beatsPerBar
    }
    val running by metronome.running.collectAsStateWithLifecycle()
    val beat by metronome.beat.collectAsStateWithLifecycle()
    val tapTempo = remember { TapTempo() }

    Column(modifier.testTag("metronome"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledIconButton(onClick = { if (running) metronome.stop() else metronome.start() }) {
                Icon(
                    if (running) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = if (running) "Stop metronome" else "Start metronome",
                )
            }
            FilledTonalIconButton(onClick = { onBpmChange(clampBpm(bpm - 1)) }, enabled = bpm > MIN_BPM) {
                Icon(Icons.Default.Remove, contentDescription = "Slower")
            }
            Text(
                "$bpm BPM",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.widthIn(min = 76.dp).testTag("metronome_bpm"),
            )
            FilledTonalIconButton(onClick = { onBpmChange(clampBpm(bpm + 1)) }, enabled = bpm < MAX_BPM) {
                Icon(Icons.Default.Add, contentDescription = "Faster")
            }
            OutlinedButton(onClick = { tapTempo.tap()?.let(onBpmChange) }) { Text("Tap") }
        }
        BeatDots(beatsPerBar, beat)
    }
}

@Composable
private fun BeatDots(beatsPerBar: Int, beat: Int) {
    val scheme = MaterialTheme.colorScheme
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.semantics { contentDescription = "Beat ${if (beat < 0) "stopped" else (beat + 1).toString()} of $beatsPerBar" },
    ) {
        repeat(beatsPerBar) { index ->
            val lit = index == beat
            Box(
                Modifier
                    .size(if (index == 0) 16.dp else 12.dp)
                    .background(
                        when {
                            lit && index == 0 -> scheme.tertiary
                            lit -> scheme.primary
                            else -> scheme.surfaceVariant
                        },
                        CircleShape,
                    )
            )
        }
    }
}
