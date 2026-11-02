package com.chordx.app.chordpro

import com.chordx.app.music.Accidentals
import com.chordx.app.music.FlatSpelling
import com.chordx.app.music.Key
import com.chordx.app.music.KeySpelling
import com.chordx.app.music.SharpSpelling
import com.chordx.app.music.Spelling
import com.chordx.app.music.normalizeSemitones

/** Turns chord references into display text. A null [spelling] shows chords exactly as written. */
class ChordRenderer(val semitones: Int, private val spelling: Spelling?) {
    fun display(ref: ChordRef): String = when (ref) {
        is ChordRef.Known -> if (spelling == null) ref.raw else ref.chord.transpose(semitones, spelling).toString()
        is ChordRef.Annotation -> ref.text
        ChordRef.NoChord -> "N.C."
        is ChordRef.Invalid -> ref.raw
    }

    companion object {
        val AsWritten = ChordRenderer(0, null)
    }
}

/**
 * Picks how to name notes. Auto keeps the chart as written when not transposing;
 * otherwise it follows the target key, or the chart's own sharp/flat habit when the
 * key is unknown. Sharps/Flats always respell, even untransposed.
 */
fun chooseSpelling(preference: Accidentals, semitones: Int, targetKey: Key?, sourcePrefersFlats: Boolean): Spelling? =
    when (preference) {
        Accidentals.SHARPS -> SharpSpelling
        Accidentals.FLATS -> FlatSpelling
        Accidentals.AUTO -> when {
            normalizeSemitones(semitones) == 0 -> null
            targetKey != null -> KeySpelling(targetKey)
            sourcePrefersFlats -> FlatSpelling
            else -> SharpSpelling
        }
    }

data class ChartPlan(
    val renderer: ChordRenderer,
    /** Shift actually applied, in -5..+6. */
    val semitones: Int,
    /** Key to show, already transposed and spelled; null when the song has no key. */
    val displayKey: String?,
)

/**
 * Works out how to show a chart. A setlist [keyOverride] wins over the song's own
 * [transpose] but only when the song's key is known (otherwise the shift is unknown).
 */
fun planChart(
    document: ChordProDocument,
    songKey: String?,
    transpose: Int,
    keyOverride: String?,
    preference: Accidentals,
): ChartPlan {
    val original = songKey?.let(Key::parse)
    val override = keyOverride?.let(Key::parse)
    val semitones = if (original != null && override != null) original.semitonesTo(override) else normalizeSemitones(transpose)
    val target = if (original != null && override != null) override else original?.transpose(semitones)
    val spelling = chooseSpelling(preference, semitones, target, document.prefersFlats())

    val displayKey = when {
        target == null -> songKey
        spelling == null -> if (override != null) keyOverride else songKey
        spelling === SharpSpelling || spelling === FlatSpelling -> Key(spelling.name(target.tonic.pitch), target.minor).toString()
        else -> target.toString()
    }
    return ChartPlan(ChordRenderer(semitones, spelling), semitones, displayKey)
}
