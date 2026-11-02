package com.chordx.app.chordpro

/*
 * Dash timing for the chords-only view, in the worship-team style used by the
 * Chords Library: each dash is one beat and the chord name itself takes none, so
 * in 4/4 "A----D--G--" is a bar of A, then half a bar each of D and G.
 *
 * ChordPro says where a chord falls in the lyrics, not how long it lasts, so:
 *  - a chord whose text is only dashes ("[A]--[D]--") keeps exactly the dashes written;
 *  - any other chord is assumed to last one bar (beats per bar from the time signature).
 */

/** A chord to show in chords-only view, followed by [dashes] beat dashes (0 for annotations). */
data class TimedChord(val chord: ChordRef, val dashes: Int)

private const val DEFAULT_BEATS_PER_BAR = 4

/** Text that is nothing but beat dashes (and spaces or bar lines): the chart spells out the timing. */
// Character classes only: Android's ICU regex engine rejects some bare symbols desktop Java accepts.
private val WRITTEN_TIMING = Regex("""^[\s|]*[-–—][-–—\s|]*$""")
private val DASH = Regex("""[-–—]""")

/**
 * Text that holds no words: only spacing, beat dashes, bar lines, slashes, punctuation
 * and repeat marks ("2x", "x2", "×3"). A chord line made of this is already a chord chart.
 */
private val NO_WORDS = Regex("""^(?:[\s\-\u2013\u2014|/%.,:;()_=*~]|[xX\u00D7]\d+|\d+[xX\u00D7])*$""")

/**
 * Marks that sit among chords but aren't lyrics: push/accent marks ("°D", "^C#m7"),
 * repeat counts and playing directions ("--REPEAT", "(hold)").
 */
private val TIMING_NOISE = Regex(
    """[\u00B0^>*()]|\b[xX\u00D7]\d+\b|\b\d+[xX\u00D7]\b|(?i:\b(?:repeat|rpt|hold|fade|stop|end|last|only|slowly|slow|ad-?lib)\b)"""
)

private fun String.withoutTimingNoise(): String = replace(TIMING_NOISE, "")

private fun hasNoWords(text: String): Boolean = NO_WORDS.matches(text.withoutTimingNoise())

private val WORD_SPLIT = Regex("""[\s\-–—|,/.:;_=~]+""")

/** Words (or numbers) in the line's text outside its chords, ignoring accents, repeats and directions. */
private fun wordCount(text: String): Int =
    WORD_SPLIT.split(text.withoutTimingNoise()).count { token -> token.any { it.isLetterOrDigit() } }

/**
 * True when the line is a chord line rather than lyrics: it has chords and more chords than
 * words, e.g. "[C]--[G/B]--", "| [G] [C] | x2" or "ARISE([D]--[A]--[Bm]--[G]--)". Such lines
 * are shown exactly as written, accents and all.
 */
fun Line.Lyrics.isChordsOnly(): Boolean {
    if (!hasChords) return false
    // A chord sung on a syllable ("[D]Hallelu-jah", "[D]Yeah [G] [A]") makes it a lyric line;
    // on a chord line, chords are followed by dashes, spaces, marks or brackets.
    if (segments.any { it.chord != null && it.chord !is ChordRef.Annotation && it.lyrics.firstOrNull()?.isLetterOrDigit() == true }) return false
    val chords = segments.count { it.chord != null }
    val words = wordCount(text)
    if (words == 0) return true
    // Words among chords ("ARISE([D]--[A]--)", "[D/F#]--[G]--(REPEAT)if pa chorus") only on dash
    // lines: bracketed notes don't count, and a line opening with a run of chords is a chord line.
    if (!DASH.containsMatchIn(text)) return false
    if (chords > words || chords > wordCount(text.replace(PARENTHESISED, " "))) return true
    val leading = segments.takeWhile { wordCount(it.lyrics) == 0 }.count { it.chord != null }
    return leading >= 3
}

private val PARENTHESISED = Regex("""\([^)]*\)""")

/**
 * True when the chart is already chords only: it has chord lines and no line carries lyric
 * words. Such charts are shown as written whether or not lyrics are switched on.
 */
fun ChordProDocument.isChordsOnly(): Boolean {
    val lines = sections.asSequence().flatMap { it.lines }.filterIsInstance<Line.Lyrics>().toList()
    return lines.any { it.hasChords } && lines.all { if (it.hasChords) it.isChordsOnly() else hasNoWords(it.text) }
}

/** Beats per bar from a time signature like "3/4" or "6/8"; 4 when missing or unreadable. */
fun beatsPerBar(timeSignature: String?): Int =
    timeSignature?.substringBefore('/')?.trim()?.toIntOrNull()?.takeIf { it in 1..16 } ?: DEFAULT_BEATS_PER_BAR

/** The chords of [line] in order, each with its beat dashes. */
fun dashTiming(line: Line.Lyrics, beatsPerBar: Int): List<TimedChord> = line.segments.mapNotNull { segment ->
    val chord = segment.chord ?: return@mapNotNull null
    val dashes = when {
        chord is ChordRef.Annotation -> 0 // a note like [*Riff], not a chord with a length
        WRITTEN_TIMING.matches(segment.lyrics.withoutTimingNoise()) -> DASH.findAll(segment.lyrics).count()
        else -> beatsPerBar
    }
    TimedChord(chord, dashes)
}
