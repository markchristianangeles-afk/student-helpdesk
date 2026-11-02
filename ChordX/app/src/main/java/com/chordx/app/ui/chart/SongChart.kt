package com.chordx.app.ui.chart

import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.chordx.app.chordpro.ChordProDocument
import com.chordx.app.chordpro.ChordRef
import com.chordx.app.chordpro.ChordRenderer
import com.chordx.app.chordpro.Line
import com.chordx.app.chordpro.Section
import com.chordx.app.chordpro.SectionKind
import com.chordx.app.chordpro.Segment
import com.chordx.app.chordpro.beatsPerBar
import com.chordx.app.chordpro.dashTiming
import com.chordx.app.chordpro.isChordsOnly
import com.chordx.app.music.Chord

private data class ChartStyles(
    val lyric: TextStyle,
    val chord: TextStyle,
    val annotation: TextStyle,
    val invalid: TextStyle,
    val label: TextStyle,
    val comment: TextStyle,
    val mono: TextStyle,
    val dash: TextStyle,
    val chorusBar: Color,
)

/** "+M7" additions look like chords; other annotations ("[*Riff]") are in italics. */
private fun ChartStyles.forAnnotation(annotation: ChordRef.Annotation): TextStyle =
    if (Chord.isAddition(annotation.text)) chord else this.annotation

@Composable
private fun chartStyles(fontScale: Float): ChartStyles {
    val scheme = MaterialTheme.colorScheme
    val base = MaterialTheme.typography.bodyLarge
    val lyric = base.copy(fontSize = base.fontSize * fontScale, lineHeight = base.lineHeight * fontScale)
    return ChartStyles(
        lyric = lyric,
        chord = lyric.copy(color = scheme.primary, fontWeight = FontWeight.Bold),
        annotation = lyric.copy(color = scheme.tertiary, fontStyle = FontStyle.Italic),
        invalid = lyric.copy(color = scheme.error, fontWeight = FontWeight.Bold),
        label = MaterialTheme.typography.labelLarge.let { it.copy(fontSize = it.fontSize * fontScale, color = scheme.secondary, fontWeight = FontWeight.Bold) },
        comment = lyric.copy(color = scheme.onSurfaceVariant, fontStyle = FontStyle.Italic),
        mono = lyric.copy(fontFamily = FontFamily.Monospace),
        dash = lyric.copy(color = scheme.onSurfaceVariant, fontWeight = FontWeight.Bold),
        chorusBar = scheme.primary.copy(alpha = 0.5f),
    )
}

/**
 * Renders a parsed chart with chords above the lyrics. Lines wrap at word boundaries to fit the screen;
 * chord lines with no words are shown inline as written.
 * With [showLyrics] off it's a chords-only chart: each line's chords in order with a dash
 * per beat (see [dashTiming]; [timeSignature] sets the beats in a bar), lyric-only lines dropped.
 */
@Composable
fun SongChart(
    document: ChordProDocument,
    renderer: ChordRenderer,
    modifier: Modifier = Modifier,
    fontScale: Float = 1f,
    showLyrics: Boolean = true,
    timeSignature: String? = null,
) {
    val styles = chartStyles(fontScale)
    val beats = beatsPerBar(timeSignature ?: document.metadata["time"])
    // A chart that's already chords only looks the same with lyrics off: no dashes are added.
    val chordsOnlyChart = remember(document) { document.isChordsOnly() }
    val asWritten = showLyrics || chordsOnlyChart
    Column(modifier.testTag("chart"), verticalArrangement = Arrangement.spacedBy((14 * fontScale).dp)) {
        document.sections.forEach { section -> SectionBlock(section, renderer, styles, fontScale, asWritten, beats) }
    }
}

@Composable
private fun SectionBlock(section: Section, renderer: ChordRenderer, styles: ChartStyles, fontScale: Float, showLyrics: Boolean, beatsPerBar: Int) {
    val isChorus = section.kind == SectionKind.CHORUS
    val barWidth = with(LocalDensity.current) { (3 * fontScale).dp.toPx() }
    val chorusModifier = if (isChorus) {
        Modifier
            .drawBehind { drawLine(styles.chorusBar, Offset(0f, 0f), Offset(0f, size.height), barWidth) }
            .padding(start = (12 * fontScale).dp)
    } else Modifier

    Column(chorusModifier) {
        section.label?.let { Text(it, style = styles.label, modifier = Modifier.padding(bottom = 2.dp)) }
        section.lines.forEach { line ->
            if (showLyrics) ChartLine(line, renderer, styles, fontScale) else ChordsOnlyLine(line, renderer, styles, fontScale, beatsPerBar)
        }
    }
}

/**
 * Chords-only view of a line, with a dash per beat after each chord ("A----D--G--"), the
 * way the team writes chord-only charts. Lyric lines without chords disappear; comments,
 * tabs and grids stay.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChordsOnlyLine(line: Line, renderer: ChordRenderer, styles: ChartStyles, fontScale: Float, beatsPerBar: Int) {
    // Non-lyric lines, and chord lines already written as chords (with their own dashes and
    // accent marks such as "*G" for bagsakan), are shown exactly as written.
    if (line !is Line.Lyrics || line.isChordsOnly()) {
        ChartLine(line, renderer, styles, fontScale)
        return
    }
    val timed = remember(line, beatsPerBar) { dashTiming(line, beatsPerBar) }
    if (timed.isEmpty()) return
    val dashSpan = styles.dash.toSpanStyle()
    // Each chord with its dashes is one unbreakable item, so long lines wrap between chords.
    FlowRow {
        timed.forEach { (chord, dashes) ->
            val chordStyle = when (chord) {
                is ChordRef.Annotation -> styles.forAnnotation(chord)
                is ChordRef.Invalid -> styles.invalid
                else -> styles.chord
            }
            Text(
                buildAnnotatedString {
                    withStyle(chordStyle.toSpanStyle()) { append(renderer.display(chord)) }
                    withStyle(dashSpan) { append("-".repeat(dashes)) }
                },
                style = styles.lyric,
                maxLines = 1,
                softWrap = false,
                modifier = if (dashes == 0) Modifier.padding(end = (8 * fontScale).dp) else Modifier,
            )
        }
    }
}

@Composable
private fun ChartLine(line: Line, renderer: ChordRenderer, styles: ChartStyles, fontScale: Float) {
    when (line) {
        Line.Blank -> Spacer(Modifier.height((10 * fontScale).dp))
        is Line.Comment -> {
            val modifier = if (line.boxed) {
                Modifier
                    .padding(vertical = 2.dp)
                    .border(1.dp, styles.comment.color, RoundedCornerShape(4.dp))
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            } else Modifier
            Text(line.text, style = if (line.italic || !line.boxed) styles.comment else styles.comment.copy(fontStyle = FontStyle.Normal), modifier = modifier)
        }
        is Line.Preformatted -> Text(
            line.text,
            style = styles.mono,
            softWrap = false,
            modifier = Modifier.horizontalScroll(rememberScrollState()),
        )
        is Line.Grid -> {
            val chordSpan = styles.chord.toSpanStyle()
            Text(
                buildAnnotatedString {
                    line.tokens.forEach { token ->
                        val chord = token.chord
                        if (chord != null) withStyle(chordSpan) { append(renderer.display(chord)) } else append(token.lyrics)
                    }
                },
                style = styles.mono,
                softWrap = false,
                modifier = Modifier.horizontalScroll(rememberScrollState()),
            )
        }
        is Line.Lyrics -> when {
            line.isChordsOnly() -> InlineChords(line, renderer, styles)
            line.hasChords -> ChordedLyrics(line.segments, renderer, styles, fontScale)
            else -> Text(line.text, style = styles.lyric)
        }
    }
}

private const val ACCENT_MARKS = "*^°>"

/**
 * A chord line, drawn inline as written: "[C]--[G/B]--" shows as "C--G/B--" rather than
 * chords floating above a row of dashes. Words and marks among the chords are kept.
 */
@Composable
private fun InlineChords(line: Line.Lyrics, renderer: ChordRenderer, styles: ChartStyles) {
    val dashSpan = styles.dash.toSpanStyle()
    Text(
        buildAnnotatedString {
            line.segments.forEach { segment ->
                segment.chord?.let { chord ->
                    val style = when (chord) {
                        is ChordRef.Annotation -> styles.forAnnotation(chord)
                        is ChordRef.Invalid -> styles.invalid
                        else -> styles.chord
                    }
                    withStyle(style.toSpanStyle()) { append(renderer.display(chord)) }
                    // Annotations need a gap before whatever follows them.
                    if (chord is ChordRef.Annotation && !Chord.isAddition(chord.text) && segment.lyrics.firstOrNull()?.isWhitespace() != true) append(' ')
                }
                // Accent marks (* ^ ° >, e.g. "*G" for bagsakan) belong to the chord after them,
                // so they're drawn in the chord colour; everything else in the dash colour.
                val chordSpan = styles.chord.toSpanStyle()
                segment.lyrics.forEach { c -> withStyle(if (c in ACCENT_MARKS) chordSpan else dashSpan) { append(c) } }
            }
        },
        style = styles.lyric,
    )
}

/**
 * One chord (or none) above a run of lyric text that won't be split across lines.
 * Trailing whitespace is turned into [spaceAfter] because text layout doesn't
 * reliably measure trailing spaces.
 */
internal data class Chunk(val chord: ChordRef?, val text: String, val spaceAfter: Boolean)

private val WORD = Regex("""\S*\s*""")

/** Splits segments at word boundaries so a long line can wrap; the chord stays on its first word. */
internal fun chunksOf(segments: List<Segment>): List<Chunk> = segments.flatMap { segment ->
    val words = WORD.findAll(segment.lyrics).map { it.value }.filter { it.isNotEmpty() }.toList()
    if (words.isEmpty()) listOf(Chunk(segment.chord, "", spaceAfter = false))
    else words.mapIndexed { index, word ->
        Chunk(if (index == 0) segment.chord else null, word.trimEnd(), spaceAfter = word.last().isWhitespace())
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChordedLyrics(segments: List<Segment>, renderer: ChordRenderer, styles: ChartStyles, fontScale: Float) {
    val chunks = remember(segments) { chunksOf(segments) }
    val wordGap = with(LocalDensity.current) { (styles.lyric.fontSize * 0.3f).toDp() }
    FlowRow {
        chunks.forEach { chunk ->
            Column(if (chunk.spaceAfter) Modifier.padding(end = wordGap) else Modifier) {
                val chord = chunk.chord
                Text(
                    text = chord?.let(renderer::display).orEmpty(),
                    style = when (chord) {
                        is ChordRef.Annotation -> styles.forAnnotation(chord)
                        is ChordRef.Invalid -> styles.invalid
                        else -> styles.chord
                    },
                    maxLines = 1,
                    softWrap = false,
                    // Keeps back-to-back chords from touching when the lyric under them is short.
                    modifier = if (chord != null) Modifier.padding(end = (6 * fontScale).dp) else Modifier,
                )
                Text(chunk.text, style = styles.lyric, maxLines = 1, softWrap = false)
            }
        }
    }
}
