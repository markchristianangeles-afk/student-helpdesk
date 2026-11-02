package com.chordx.app.chordpro

import com.chordx.app.music.Chord
import com.chordx.app.music.Key

enum class Severity { ERROR, WARNING }

/** A problem found while parsing; [line] is 1-based. */
data class Diagnostic(val line: Int, val message: String, val severity: Severity = Severity.ERROR)

sealed interface ChordRef {
    /** A valid chord; [raw] is exactly as written, for showing untransposed. */
    data class Known(val chord: Chord, val raw: String) : ChordRef
    /** ChordPro annotation, written [*text]: shown above the lyrics but never transposed. */
    data class Annotation(val text: String) : ChordRef
    data object NoChord : ChordRef
    /** Something in brackets that isn't a chord. Reported as an error and shown as written. */
    data class Invalid(val raw: String) : ChordRef
}

/** Lyrics with the chord that sits above their first character. */
data class Segment(val chord: ChordRef?, val lyrics: String)

sealed interface Line {
    data class Lyrics(val segments: List<Segment>) : Line {
        val hasChords: Boolean get() = segments.any { it.chord != null }
        val text: String get() = segments.joinToString("") { it.lyrics }
    }
    data class Comment(val text: String, val italic: Boolean = false, val boxed: Boolean = false) : Line
    /** Tab lines: shown exactly as written in a fixed-width font. */
    data class Preformatted(val text: String) : Line
    /** Grid lines: chord tokens (lyrics "") and everything else (chord null), in order. */
    data class Grid(val tokens: List<Segment>) : Line
    data object Blank : Line
}

enum class SectionKind { VERSE, CHORUS, BRIDGE, TAB, GRID, CUSTOM }

/** [kind] is null for lines that aren't inside any {start_of_…} block. */
data class Section(
    val kind: SectionKind?,
    val label: String?,
    val lines: List<Line>,
    val isRecall: Boolean = false,
)

data class ChordProDocument(
    val metadata: Map<String, String>,
    val sections: List<Section>,
    val diagnostics: List<Diagnostic>,
    /** True when built from a plain chord sheet converted for display (see parseForDisplay). */
    val convertedForDisplay: Boolean = false,
) {
    val errors: List<Diagnostic> get() = diagnostics.filter { it.severity == Severity.ERROR }

    fun knownChords(): Sequence<Chord> = sections.asSequence()
        .flatMap { it.lines }
        .flatMap { line ->
            when (line) {
                is Line.Lyrics -> line.segments.asSequence().map { it.chord }
                is Line.Grid -> line.tokens.asSequence().map { it.chord }
                else -> emptySequence()
            }
        }
        .mapNotNull { (it as? ChordRef.Known)?.chord }

    /** True when the chart is written mostly with flats, used to spell transposed chords when the key is unknown. */
    fun prefersFlats(): Boolean {
        var flats = 0
        var sharps = 0
        for (chord in knownChords()) {
            for (note in listOfNotNull(chord.root, chord.bass)) {
                if (note.accidental < 0) flats++ else if (note.accidental > 0) sharps++
            }
        }
        return flats > sharps
    }
}

// Braces/brackets are always escaped: Android's ICU regex engine rejects a bare "}" that desktop Java accepts.
private val DIRECTIVE = Regex("""^\{\s*([A-Za-z_][A-Za-z0-9_-]*)\s*(?::\s*(.*?))?\s*\}$""")
private val LABEL_ATTRIBUTE = Regex("""^label\s*=\s*"(.*)"$""")
private val TIME_SIGNATURE = Regex("""^\d{1,2}/\d{1,2}$""")
private val GRID_TOKEN = Regex("""\S+|\s+""")
private val NO_CHORD = setOf("N.C.", "N.C", "NC")

private val METADATA_ALIASES = mapOf("t" to "title", "st" to "subtitle")
private val METADATA = setOf(
    "title", "subtitle", "artist", "composer", "lyricist", "arranger", "album", "year",
    "key", "capo", "tempo", "time", "duration", "copyright", "sorttitle",
)
private val SECTION_SHORT = mapOf(
    "soc" to ("start" to "chorus"), "eoc" to ("end" to "chorus"),
    "sov" to ("start" to "verse"), "eov" to ("end" to "verse"),
    "sob" to ("start" to "bridge"), "eob" to ("end" to "bridge"),
    "sot" to ("start" to "tab"), "eot" to ("end" to "tab"),
    "sog" to ("start" to "grid"), "eog" to ("end" to "grid"),
)

/** Layout and font directives that matter for printing, not for this app. Accepted and ignored. */
private val FORMATTING = setOf(
    "new_page", "np", "new_physical_page", "npp", "column_break", "colb", "columns", "col",
    "pagetype", "titles", "grid", "g", "no_grid", "ng", "define", "chord",
    "textfont", "textsize", "textcolour", "textcolor", "chordfont", "chordsize", "chordcolour", "chordcolor",
    "tabfont", "tabsize", "tabcolour", "tabcolor", "chorusfont", "chorussize", "choruscolour", "choruscolor",
    "footerfont", "footersize", "footercolour", "footercolor", "titlefont", "titlesize", "titlecolour", "titlecolor",
)

private fun sectionKind(name: String): SectionKind = when (name) {
    "verse" -> SectionKind.VERSE
    "chorus" -> SectionKind.CHORUS
    "bridge" -> SectionKind.BRIDGE
    "tab" -> SectionKind.TAB
    "grid" -> SectionKind.GRID
    else -> SectionKind.CUSTOM
}

private fun defaultLabel(kind: SectionKind, name: String): String? = when (kind) {
    SectionKind.CHORUS -> "Chorus"
    SectionKind.BRIDGE -> "Bridge"
    SectionKind.CUSTOM -> name.replace('_', ' ').replaceFirstChar { it.uppercaseChar() }
    else -> null
}

/**
 * Parses a ChordPro chart strictly: every problem is reported with its line number
 * instead of being silently guessed at. Parsing never fails — problem lines are kept
 * as written so the chart still displays.
 */
fun parseChordPro(text: String): ChordProDocument = Parser().parse(text)

private class Parser {
    private class Open(val name: String, val kind: SectionKind, val label: String?, val startLine: Int) {
        val lines = mutableListOf<Line>()
    }

    private val metadata = linkedMapOf<String, String>()
    private val sections = mutableListOf<Section>()
    private val diagnostics = mutableListOf<Diagnostic>()
    private val loose = mutableListOf<Line>()
    private var open: Open? = null
    private var lastChorus: Section? = null

    fun parse(text: String): ChordProDocument {
        text.replace("\r\n", "\n").replace('\r', '\n').split('\n').forEachIndexed { index, raw ->
            parseLine(raw.trimEnd(), index + 1)
        }
        open?.let {
            error(it.startLine, "{start_of_${it.name}} is never closed; add {end_of_${it.name}}")
            finish(it)
        }
        closeLoose()
        return ChordProDocument(metadata, sections, diagnostics)
    }

    private fun error(line: Int, message: String) { diagnostics += Diagnostic(line, message, Severity.ERROR) }
    private fun warn(line: Int, message: String) { diagnostics += Diagnostic(line, message, Severity.WARNING) }

    private fun add(line: Line) {
        open?.lines?.add(line) ?: loose.add(line)
    }

    private fun closeLoose() {
        val lines = loose.trimBlank()
        if (lines.isNotEmpty()) sections += Section(kind = null, label = null, lines = lines)
        loose.clear()
    }

    private fun finish(section: Open) {
        val done = Section(section.kind, section.label ?: defaultLabel(section.kind, section.name), section.lines.trimBlank())
        sections += done
        if (section.kind == SectionKind.CHORUS) lastChorus = done
    }

    private fun parseLine(raw: String, lineNo: Int) {
        val trimmed = raw.trim()
        val inLiteral = open?.kind == SectionKind.TAB || open?.kind == SectionKind.GRID

        if (inLiteral) {
            val match = DIRECTIVE.matchEntire(trimmed)
            val name = match?.groupValues?.get(1)?.lowercase()
            val isEnd = name != null && (name.startsWith("end_of_") || SECTION_SHORT[name]?.first == "end")
            if (!isEnd) {
                add(if (open!!.kind == SectionKind.TAB) Line.Preformatted(raw) else gridLine(raw))
                return
            }
        }

        when {
            trimmed.isEmpty() -> add(Line.Blank)
            trimmed.startsWith("#") -> Unit // ChordPro source comment, never shown
            // "// note" — a quick way to write a comment shown to the band, same as {comment: note}.
            trimmed.startsWith("//") -> trimmed.removePrefix("//").trim().takeIf { it.isNotEmpty() }?.let { add(Line.Comment(it)) }
            trimmed.startsWith("{") -> directive(trimmed, lineNo)
            else -> {
                val note = splitLineComment(raw)
                if (note == null) {
                    add(lyrics(raw, lineNo))
                } else {
                    if (note.first.isNotBlank()) add(lyrics(note.first.trimEnd(), lineNo))
                    add(Line.Comment(note.second))
                }
            }
        }
    }

    private fun directive(text: String, lineNo: Int) {
        if (!text.endsWith("}")) {
            error(lineNo, "Directive is missing its closing }")
            add(Line.Lyrics(listOf(Segment(null, text))))
            return
        }
        val match = DIRECTIVE.matchEntire(text)
        if (match == null) {
            error(lineNo, "Malformed directive; use {name} or {name: value}")
            return
        }
        val rawName = match.groupValues[1].lowercase()
        val name = METADATA_ALIASES[rawName] ?: rawName
        val value = match.groups[2]?.value?.trim().orEmpty()

        SECTION_SHORT[name]?.let { (edge, section) ->
            if (edge == "start") startSection(section, value, lineNo) else endSection(section, lineNo)
            return
        }

        when {
            name.startsWith("start_of_") -> startSection(name.removePrefix("start_of_"), value, lineNo)
            name.startsWith("end_of_") -> endSection(name.removePrefix("end_of_"), lineNo)
            name in METADATA -> metadata(name, value, lineNo)
            name == "meta" -> {
                val parts = value.split(Regex("\\s+"), limit = 2)
                if (parts.size < 2 || parts[1].isBlank()) error(lineNo, "{meta} needs a name and a value, e.g. {meta: album Hymns}")
                else metadata.putIfAbsent(parts[0].lowercase(), parts[1])
            }
            name == "comment" || name == "c" || name == "highlight" -> comment(value, lineNo, name)
            name == "comment_italic" || name == "ci" -> comment(value, lineNo, name, italic = true)
            name == "comment_box" || name == "cb" -> comment(value, lineNo, name, boxed = true)
            name == "chorus" -> recallChorus(value, lineNo)
            name == "transpose" -> warn(lineNo, "{transpose} is ignored; use the Transpose control instead")
            name == "new_song" || name == "ns" -> warn(lineNo, "{$name} is ignored here; import the file to split it into separate songs")
            name in FORMATTING || name.startsWith("x_") -> Unit
            else -> warn(lineNo, "Unknown directive {$rawName} was ignored")
        }
    }

    private fun metadata(name: String, value: String, lineNo: Int) {
        if (value.isEmpty()) {
            error(lineNo, "{$name} needs a value")
            return
        }
        when (name) {
            "key" -> if (Key.parse(value) == null) error(lineNo, "“$value” isn't a key; use e.g. G, Bb or F#m")
            "capo" -> if (value.toIntOrNull()?.takeIf { it in 0..11 } == null) error(lineNo, "Capo must be a fret number from 0 to 11")
            "tempo" -> if (value.toIntOrNull()?.takeIf { it > 0 } == null) error(lineNo, "Tempo must be a number of beats per minute")
            "time" -> if (!TIME_SIGNATURE.matches(value)) error(lineNo, "Time must look like 4/4 or 6/8")
        }
        metadata.putIfAbsent(name, value)
    }

    private fun comment(value: String, lineNo: Int, name: String, italic: Boolean = false, boxed: Boolean = false) {
        if (value.isEmpty()) error(lineNo, "{$name} needs some text") else add(Line.Comment(value, italic, boxed))
    }

    private fun startSection(name: String, value: String, lineNo: Int) {
        open?.let {
            error(lineNo, "{start_of_$name} begins before {end_of_${it.name}} closed the section on line ${it.startLine}")
            finish(it)
            open = null
        }
        closeLoose()
        val label = value.ifEmpty { null }?.let { LABEL_ATTRIBUTE.matchEntire(it)?.groupValues?.get(1) ?: it }
        open = Open(name, sectionKind(name), label, lineNo)
    }

    private fun endSection(name: String, lineNo: Int) {
        val current = open
        when {
            current == null -> error(lineNo, "{end_of_$name} has no matching {start_of_$name}")
            current.name != name -> {
                error(lineNo, "{end_of_$name} doesn't match {start_of_${current.name}} on line ${current.startLine}")
                finish(current)
                open = null
            }
            else -> {
                finish(current)
                open = null
            }
        }
    }

    private fun recallChorus(value: String, lineNo: Int) {
        if (open != null) {
            error(lineNo, "{chorus} can't be used inside another section")
            return
        }
        closeLoose()
        val chorus = lastChorus
        if (chorus == null) {
            warn(lineNo, "{chorus} is used before any chorus is defined")
            sections += Section(SectionKind.CHORUS, value.ifEmpty { "Chorus" }, emptyList(), isRecall = true)
        } else {
            sections += chorus.copy(label = value.ifEmpty { chorus.label }, isRecall = true)
        }
    }

    private fun lyrics(raw: String, lineNo: Int): Line.Lyrics {
        val segments = mutableListOf<Segment>()
        val text = StringBuilder()
        var chord: ChordRef? = null
        var i = 0
        while (i < raw.length) {
            when (val c = raw[i]) {
                '[' -> {
                    val close = raw.indexOf(']', i + 1)
                    if (close < 0) {
                        error(lineNo, "Chord bracket at column ${i + 1} is never closed")
                        text.append(raw, i, raw.length)
                        i = raw.length
                    } else {
                        if (text.isNotEmpty() || chord != null) segments += Segment(chord, text.toString())
                        text.clear()
                        chord = chordRef(raw.substring(i + 1, close), lineNo)
                        i = close + 1
                    }
                }
                ']' -> {
                    error(lineNo, "Unexpected ] at column ${i + 1}")
                    i++
                }
                else -> {
                    text.append(c)
                    i++
                }
            }
        }
        if (text.isNotEmpty() || chord != null) segments += Segment(chord, text.toString())
        return Line.Lyrics(segments)
    }

    private fun chordRef(inner: String, lineNo: Int): ChordRef {
        val name = inner.trim()
        return when {
            name.isEmpty() -> ChordRef.Invalid("").also { error(lineNo, "Empty chord []") }
            name.startsWith("*") -> ChordRef.Annotation(name.substring(1))
            // "[+M7]": an addition to the previous chord — shown as written, never transposed.
            Chord.isAddition(name) -> ChordRef.Annotation(name)
            name in NO_CHORD -> ChordRef.NoChord
            else -> Chord.parse(name)?.let { ChordRef.Known(it, name) }
                ?: ChordRef.Invalid(name).also { error(lineNo, "“$name” isn't a chord") }
        }
    }

    private fun gridLine(raw: String): Line.Grid = Line.Grid(
        GRID_TOKEN.findAll(raw).map { token ->
            val value = token.value
            when {
                value in NO_CHORD -> Segment(ChordRef.NoChord, "")
                else -> Chord.parse(value)?.let { Segment(ChordRef.Known(it, value), "") } ?: Segment(null, value)
            }
        }.toList()
    )
}

private val FIRST_TOKEN = Regex("""^[^\s\-–—|,()\[\]]+""")

/**
 * Splits "chords or lyrics // note" (like a PHP comment) into the content and the note.
 * Only when words follow the "//": rhythm slashes ("F // | C/E //", "Bm // G") and links
 * ("http://…") are not comments. Returns null when the line has no such note.
 */
internal fun splitLineComment(raw: String): Pair<String, String>? {
    var from = 0
    while (true) {
        val at = raw.indexOf("//", from)
        if (at < 0) return null
        from = at + 2
        val insideChord = raw.lastIndexOf('[', at) > raw.lastIndexOf(']', at)
        if (insideChord || (at > 0 && raw[at - 1] == ':')) continue
        val note = raw.substring(at + 2).trim()
        val first = FIRST_TOKEN.find(note)?.value ?: continue
        val isWord = first.any { it.isLetter() } && Chord.parse(first) == null && Chord.parse(first.trimEnd('/', '.')) == null
        if (isWord) return raw.substring(0, at) to note
    }
}

private fun List<Line>.trimBlank(): List<Line> {
    val first = indexOfFirst { it != Line.Blank }
    if (first < 0) return emptyList()
    val last = indexOfLast { it != Line.Blank }
    return subList(first, last + 1).toList()
}
