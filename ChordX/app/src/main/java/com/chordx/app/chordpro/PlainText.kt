package com.chordx.app.chordpro

import com.chordx.app.music.Chord
import com.chordx.app.music.Key

/*
 * Converts "chords over lyrics" text (the usual format of chord sheets in .txt files
 * and web pages) into ChordPro:
 *
 *     G         C          G            [G]Amazing [C]grace, how [G]sweet
 *     Amazing   grace, how sweet   →
 *
 * Chords land at the column they were typed above. Section headings ("Verse 1",
 * "[Chorus]", "Bridge:", "CHO:") become sections, "Key: G" / "Capo 2" style lines become
 * metadata, and dash-timing lines ("C--G/B--Am7--G--") get their chords bracketed with
 * the dashes left in place. Anything that isn't recognised is kept as a lyric line.
 */

private const val TAB_WIDTH = 8

// The team's charts also use short forms: "Cho", "Pre", "Ref", "Bri"/"Brid", "Inst"/"Instr",
// "Into" (a common slip for Intro), and "Last 3" for the closing repeat.
private val SECTION_WORDS = "intro|into|verse|chorus|pre[\\s-]?chorus|cho|pre[\\s-]?cho|pre|ref|bridge|brid|bri|outro|tag|interlude" +
    "|instrumental|instr|inst|ending|refrain|hook|turnaround|transition|vamp|coda|solo|break|last"
/** "VERSE:[BASS LINE]" or "Chorus (soft)" — a note in brackets after a heading. */
private val BRACKET_NOTE = Regex("""^[\[(]([^\])]*)[\])]$""")
/** A label in square brackets that isn't a chord ("[ADLIB]"); "[*Riff]" annotations are left alone. */
private val BRACKET_LABEL = Regex("""\[(?!\*)([^\]\n]*)\]""")
/** "[F]F //": the chord written again right before its rhythm slashes. */
private val DUPLICATED_CHORD = Regex("""\[([^\]\s]+)\]\1(?=\s*/)""")
/** "Intro: Keyboard" — a heading naming who plays it, rather than chords. */
private val PLAYED_BY = Regex("""^(?:keyboards?|piano|guitars?|acoustic|electric|drums|bass|band|all|vocals?|a\s*cappella)$""", RegexOption.IGNORE_CASE)
private val SECTION_HEADER = Regex("""^\s*[\[(]?\s*((?:$SECTION_WORDS)(?:\s*\d+)?)\s*[\])]?\s*[:.]?\s*(.*)$""", RegexOption.IGNORE_CASE)
/** "Key:" on a line of its own, with the key on the next line. */
private val KEY_LABEL = Regex("""^\s*key\s*:?\s*$""", RegexOption.IGNORE_CASE)
/** A ruled line between alternate versions: "_____", "=====". */
private val RULE_LINE = Regex("""^\s*[_=*~]{3,}\s*$""")
private val TRANSPOSE_NOTE = Regex("""^\s*transpose\b.*$""", RegexOption.IGNORE_CASE)
private val META_LINE = Regex("""^\s*(key|capo|tempo|bpm|time)\s*[:\-]?\s*(.+?)\s*$""", RegexOption.IGNORE_CASE)
private val CAPO_VALUE = Regex("""^(\d{1,2})(?:st|nd|rd|th)?(?:\s+fret)?$""", RegexOption.IGNORE_CASE)
private val TIME_VALUE = Regex("""^\d{1,2}/\d{1,2}$""")
private val BAR_TOKEN = Regex("""^(?:\|+|/+|-+|%|:?\|\|?:?|\(?[x×]\d+\)?|\(?\d+[x×]\)?)$""", RegexOption.IGNORE_CASE)
private val NO_CHORD_TOKENS = setOf("N.C.", "N.C", "NC")
private val TOKEN = Regex("""\S+""")
private val INLINE_CHORD = Regex("""\[([^\]\n]+)\]""")

private fun expandTabs(line: String): String {
    if ('\t' !in line) return line
    val out = StringBuilder()
    for (c in line) {
        if (c == '\t') do out.append(' ') while (out.length % TAB_WIDTH != 0) else out.append(c)
    }
    return out.toString()
}

/** Chord text from a token, allowing it to be wrapped in parentheses: "(G)". */
private fun chordToken(token: String): String? {
    val bare = token.removeSurrounding("(", ")")
    return when {
        bare in NO_CHORD_TOKENS -> "N.C."
        Chord.parse(bare) != null -> bare
        else -> null
    }
}

/** A line made only of chords (and bar/repeat marks), with at least one chord. */
fun isChordLine(line: String): Boolean {
    val tokens = TOKEN.findAll(line).map { it.value }.toList()
    if (tokens.isEmpty()) return false
    var chords = 0
    for (token in tokens) {
        when {
            chordToken(token) != null -> chords++
            BAR_TOKEN.matches(token) -> Unit
            else -> return false
        }
    }
    return chords > 0
}

/*
 * Chord lines the way the team writes them, scanned chord by chord (as the Chords Library
 * does) so every chord gets bracketed — and so transposes — wherever it sits:
 *
 *   C--G/B--Am7--G--2x          dash timing (dashes are beats)
 *   A--E--(Em7-A-A/C#)          chords in parentheses
 *   C---,---F---Dm---           commas as beat placeholders
 *   Gm/ | F/ | C/E / / |        rhythm slashes
 *   D--A--C--G--*G*G*G          accent marks: * ^ ° > (bagsakan / pushes)
 *   c#m-B-E-A-4x                lower-case chords (only on lines with dashes)
 *   G to D,  V. A----E----      words mixed in
 *
 * Everything that isn't a chord — dashes, commas, brackets, accents, words — is kept
 * exactly as typed. A line counts as a chord line only when it has more chords than
 * words, so lyrics such as "A-men" or "Hallelu-jah" are left alone.
 */
private val DASH = Regex("""[-–—]""")
private val REPEAT_MARK = Regex("""^\(?(?:[xX×]\d+|\d+[xX×])\)?$""")
/** Accent marks directly before a chord. Only a mark right before a note letter counts, so "C°7" stays whole. */
private val ACCENT_SPLIT = Regex("""(?<![°^>*])(?=[°^>*]+[A-Ga-g])""")
private val ACCENTS = Regex("""^[°^>*]+""")
/** Playing directions and joining words written among chords. */
private val DIRECTION = Regex("""^(?:repeat|rpt|hold|fade|stop|end|last|only|slow(?:ly)?|ad-?lib|to|&|and)$""", RegexOption.IGNORE_CASE)
/** A candidate token: anything between spaces, dashes, bars, commas, brackets, stars, carets, colons. */
private val SCAN_TOKEN = Regex("""N\.C\.?|[^\s\-–—|,()\[\]*^:;]+""")
private val INLINE_GROUP = Regex("""\[([^\]\n]*)\]""")

/** A chord name, accepting a lower-case root ("c#m" → "C#m") only when [lowerCase] is allowed. */
private fun chordName(text: String, lowerCase: Boolean): String? {
    chordToken(text)?.let { return it }
    if (!lowerCase || text.isEmpty() || text[0] !in 'a'..'g') return null
    val upper = text.replaceFirstChar { it.uppercaseChar() }
    return upper.takeIf { Chord.parse(it) != null }
}

private sealed interface Scanned {
    /** A token that is one or more chords; [render] is the token with each chord bracketed. */
    data class Chords(val count: Int, val render: String, val upperCase: Boolean) : Scanned
    data object Noise : Scanned
    data object Word : Scanned
}

private fun scanToken(token: String, lowerCase: Boolean): Scanned {
    if (REPEAT_MARK.matches(token) || DIRECTION.matches(token)) return Scanned.Noise
    // Leading "/" ("G-/F-") and trailing rhythm slashes or full stops ("Gm/", "C//") stay as text.
    val lead = if (token.startsWith("/")) "/" else ""
    val core = token.removePrefix("/").trimEnd('/', '.')
    val trail = token.substring(lead.length + core.length)
    if (core.isEmpty()) return Scanned.Noise
    // "+M7" after a chord ("Bm--+M7"): an addition to that chord, kept exactly as written.
    if (Chord.isAddition(core)) return Scanned.Chords(1, "$lead[$core]$trail", upperCase = false)
    val parts = core.split(ACCENT_SPLIT).filter { it.isNotEmpty() }
    val rendered = StringBuilder(lead)
    var upper = false
    for (part in parts) {
        val accent = ACCENTS.find(part)?.value.orEmpty()
        val bare = part.removePrefix(accent)
        val name = chordName(bare, lowerCase) ?: return Scanned.Word
        if (bare.first().isUpperCase()) upper = true
        rendered.append(accent).append('[').append(name).append(']')
    }
    return Scanned.Chords(parts.size, rendered.append(trail).toString(), upper)
}

/**
 * Brackets every chord in a plain chord line, or returns null when [line] isn't one.
 * Chords already in [brackets] are kept; other bracketed text ("[Bm--]") becomes
 * parentheses so it isn't mistaken for a ChordPro chord. Words written after the chords
 * are a note to the band and become a comment on the next line:
 *
 *   D/F#--G--A--Bm--(REPEAT)if pa chorus,wala ng Bm
 *   → [D/F#]--[G]--[A]--[Bm]--(REPEAT)
 *     {comment: if pa chorus,wala ng Bm}
 */
fun scanChordLine(line: String): String? = scan(line)?.first

/** The rewritten line and how many chords were newly bracketed. */
private fun scan(original: String, splitNote: Boolean = true): Pair<String, Int>? {
    // "[F]F //" writes the chord twice: keep the transposable [F] and drop the plain copy.
    val deduplicated = DUPLICATED_CHORD.replace(original) { "[${it.groupValues[1]}]" }
    val line = deduplicated
    val lowerCase = DASH.containsMatchIn(line)
    // Rhythm slashes ("F // | C/E /") mark a chord line just as dashes do.
    var rhythmSlashes = false
    var chords = 0
    var newChords = 0
    var words = 0
    var wordsInParens = 0
    var leadingChords = 0
    var anyUpper = false
    // Where a note written after the chords begins: the first word outside brackets that follows a chord.
    var noteStart = -1
    val out = StringBuilder()
    var last = 0
    /** [base] is where [text] starts in [line], or -1 for text that isn't from the line as typed. */
    fun scanText(text: String, base: Int) {
        // Bracket depth at each position, so words written as notes "(FOR 2ND VERSE)" can be told apart.
        val depth = IntArray(text.length + 1)
        text.forEachIndexed { i, c -> depth[i + 1] = (depth[i] + if (c == '(') 1 else if (c == ')') -1 else 0).coerceAtLeast(0) }
        out.append(SCAN_TOKEN.replace(text) { token ->
            when (val s = scanToken(token.value, lowerCase)) {
                is Scanned.Chords -> {
                    chords += s.count; newChords += s.count; anyUpper = anyUpper || s.upperCase
                    if (words == 0) leadingChords += s.count
                    s.render
                }
                Scanned.Noise -> {
                    if (token.value.all { it == '/' }) rhythmSlashes = true
                    token.value
                }
                Scanned.Word -> {
                    words++
                    val inParens = depth[token.range.first] > 0
                    if (inParens) wordsInParens++
                    // A note starts at a real word ("if", "intro"), not at a misspelt chord such as "E/GE#".
                    val looksLikeWord = token.value.length >= 2 && (token.value.any { it.isLowerCase() } || token.value.all { it.isLetter() })
                    if (!inParens && noteStart < 0 && chords > 0 && base >= 0 && looksLikeWord) noteStart = base + token.range.first
                    token.value
                }
            }
        })
    }
    for (group in INLINE_GROUP.findAll(line)) {
        scanText(line.substring(last, group.range.first), last)
        val inner = group.groupValues[1].trim()
        if (inner.isNotEmpty() && (inner.startsWith("*") || Chord.isAddition(inner) || chordToken(inner) != null)) {
            chords++; anyUpper = true
            out.append(group.value)
        } else {
            scanText("(${group.groupValues[1]})", -1)
        }
        last = group.range.last + 1
    }
    scanText(line.substring(last), last)
    // On a line that already has [chords], the rest is lyrics unless it's purely chords and
    // dashes ("[Bm]--Em--A--D--"); "[G]A [C]B" keeps A and B as words.
    val hasBracketed = chords > newChords
    // On dash lines, words in brackets are notes, and a line that opens with a run of chords
    // is a chord line even when a note follows ("D/F#--G--A--Bm--(REPEAT)if pa chorus").
    val notesAside = lowerCase && (chords > words - wordsInParens || leadingChords >= 3)
    val isChordLine = chords > 0 && anyUpper &&
        if (hasBracketed && newChords > 0) words == 0 && (lowerCase || rhythmSlashes) else words == 0 || chords > words || notesAside
    if (!isChordLine) return if (deduplicated != original) deduplicated to 0 else null
    if (splitNote && noteStart > 0) {
        val chordPart = scan(line.substring(0, noteStart).trimEnd(), splitNote = false)
        val note = line.substring(noteStart).trim()
        if (chordPart != null && note.isNotEmpty()) return "${chordPart.first}\n{comment: $note}" to chordPart.second
    }
    return out.toString().trim() to newChords
}

/** A plain chord line such as "C--G/B--Am7--G--2x" or "°D-°E-" (with dashes, so not a spaced chords-over-lyrics line). */
fun isDashTimingLine(line: String): Boolean = DASH.containsMatchIn(line) && (scan(line)?.second ?: 0) > 0

/** True when the line has chords written without brackets, so converting would change it. */
private fun hasPlainChords(line: String): Boolean = (scan(line)?.second ?: 0) > 0

private fun bracketDashLine(line: String): String = scanChordLine(line) ?: line

private data class Header(val kind: String, val label: String, val trailingChords: String?)

private fun header(line: String): Header? {
    val match = SECTION_HEADER.matchEntire(line) ?: return null
    val label = match.groupValues[1].trim().replace(Regex("\\s+"), " ")
    val rest = match.groupValues[2].trim()
    // "Chorus of angels" is a lyric, not a heading; trailing chords ("Intro: G C D") or repeat marks ("Chorus x2") are fine.
    val onlyRepeatMarks = rest.isNotEmpty() && TOKEN.findAll(rest).all { BAR_TOKEN.matches(it.value) }
    // A note, not chords: "Transition (^Bb-^F/A-^A/B)" keeps its chords on the line so they transpose.
    val bracketNote = BRACKET_NOTE.matchEntire(rest)?.groupValues?.get(1)?.trim()
        ?.takeIf { Chord.parse(it) == null && scanChordLine(it) == null }
    val playedBy = rest.isNotEmpty() && (PLAYED_BY.matches(rest) || bracketNote != null)
    if (rest.isNotEmpty() && !onlyRepeatMarks && !playedBy && !isChordLine(rest) && scanChordLine(rest) == null) return null
    val trailing = rest.takeIf { it.isNotEmpty() && !onlyRepeatMarks && !playedBy }
    val word = label.lowercase().substringBefore(' ').filter { it.isLetter() }
    val kind = when (word) {
        "chorus", "refrain", "cho" -> "chorus"
        "bridge", "brid", "bri" -> "bridge"
        else -> "verse"
    }
    val shown = label.replaceFirstChar { it.uppercaseChar() } + if (playedBy) " (${bracketNote ?: rest})" else ""
    return Header(kind, shown, trailing)
}

private fun metadataDirective(line: String): String? {
    val match = META_LINE.matchEntire(line) ?: return null
    val value = match.groupValues[2]
    return when (match.groupValues[1].lowercase()) {
        "key" -> Key.parse(value)?.let { "{key: $value}" }
        "capo" -> CAPO_VALUE.matchEntire(value)?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it in 0..11 }?.let { "{capo: $it}" }
        "tempo", "bpm" -> value.removeSuffix("bpm").removeSuffix("BPM").trim().toIntOrNull()?.takeIf { it > 0 }?.let { "{tempo: $it}" }
        "time" -> value.takeIf { TIME_VALUE.matches(it) }?.let { "{time: $it}" }
        else -> null
    }
}

/** Chord line with no lyrics under it: "G  C | D" → "[G]  [C] | [D]". */
private fun chordsOnly(line: String): String =
    TOKEN.replace(line.trim()) { token -> chordToken(token.value)?.let { "[$it]" } ?: token.value }

/** Inserts each chord into [lyric] at the column it sat above. */
private fun merge(chordLine: String, lyric: String): String {
    val chords = TOKEN.findAll(chordLine).mapNotNull { token -> chordToken(token.value)?.let { token.range.first to it } }.toList()
    val out = StringBuilder(lyric.trimEnd())
    for ((column, chord) in chords.asReversed()) {
        while (out.length < column) out.append(' ')
        out.insert(column, "[$chord]")
    }
    return out.toString().trimEnd()
}

/**
 * Square-bracketed labels that aren't chords would be read as (invalid) chords, so they
 * become plain notes: "Hallelujah [ADLIB]" → "Hallelujah (ADLIB)", and a label alone on its
 * line → a comment. Real chords in brackets are left as they are.
 */
private fun labelsAsNotes(line: String): String {
    fun isChordLike(text: String) = Chord.parse(text) != null || Chord.isAddition(text)
    val alone = BRACKET_LABEL.matchEntire(line.trim())
    if (alone != null && !isChordLike(alone.groupValues[1].trim())) return "{comment: ${alone.groupValues[1].trim()}}"
    return BRACKET_LABEL.replace(line) { m -> if (!isChordLike(m.groupValues[1].trim())) "(${m.groupValues[1]})" else m.value }
}

private fun hasInlineChords(line: String): Boolean = INLINE_CHORD.findAll(line).any { Chord.parse(it.groupValues[1].trim()) != null }

/**
 * True when [text] has chords written without brackets — chords over lyrics, dash-timing
 * lines, or a stray plain chord line in an otherwise-ChordPro chart — so converting would
 * make them transposable. Chords already in [brackets] are left as they are.
 */
fun looksLikeChordsOverLyrics(text: String): Boolean = text.lines().any { line ->
    if (line.trimStart().startsWith("{")) return@any false
    val heading = header(line)
    when {
        // Chords may share the heading's line: "VERSE:  B/E--^C#m7-F#m7--A--REPEAT".
        heading != null -> heading.trailingChords?.let { isChordLine(it) || hasPlainChords(it) } == true
        hasPlainChords(line) -> true
        hasWordLabel(line) -> true
        else -> isChordLine(line) && !hasInlineChords(line)
    }
}

/** A word label in square brackets, such as "[ADLIB]" or "[BASS LINE]" — not a chord, and longer than a typo like "[Hx]". */
private val WORD_LABEL = Regex("""^[A-Za-z][A-Za-z0-9 .'-]{2,}$""")

private fun hasWordLabel(line: String): Boolean = BRACKET_LABEL.findAll(line).any { m ->
    val inner = m.groupValues[1].trim()
    WORD_LABEL.matches(inner) && Chord.parse(inner) == null
}

/**
 * Parses a stored chart for display. A plain chord sheet saved without converting is
 * converted on the fly so its chords show, colour and transpose; the stored text is
 * untouched. Problems are not reported for it, since line numbers would refer to the
 * converted text rather than what's stored.
 */
fun parseForDisplay(content: String): ChordProDocument =
    if (looksLikeChordsOverLyrics(content)) {
        parseChordPro(convertChordsOverLyrics(content)).copy(diagnostics = emptyList(), convertedForDisplay = true)
    } else {
        parseChordPro(content)
    }

fun convertChordsOverLyrics(text: String): String {
    // A "// note" at the end of a line goes on its own line first, so it's never read as chords or lyrics.
    val lines = text.replace("\r\n", "\n").replace('\r', '\n').split('\n').map { expandTabs(it.trimEnd()) }
        .flatMap { line ->
            if (line.trimStart().startsWith("//")) return@flatMap listOf(line)
            splitLineComment(line)?.let { (content, note) -> listOfNotNull(content.trimEnd().ifBlank { null }, "// $note") } ?: listOf(line)
        }
    val out = mutableListOf<String>()
    var openSection: String? = null

    fun closeSection() {
        val section = openSection ?: return
        while (out.isNotEmpty() && out.last().isBlank()) out.removeAt(out.lastIndex)
        out += "{end_of_$section}"
        out += ""
        openSection = null
    }

    var i = 0
    while (i < lines.size) {
        val line = lines[i]
        val trimmed = line.trim()
        val heading = if (trimmed.startsWith("{")) null else header(line)
        when {
            heading != null -> {
                closeSection()
                out += "{start_of_${heading.kind}: ${heading.label}}"
                openSection = heading.kind
                heading.trailingChords?.let { out += scanChordLine(it) ?: chordsOnly(it) }
            }
            trimmed.startsWith("{") -> out += line
            // "Key:" with the key on the next line.
            KEY_LABEL.matches(line) && lines.getOrNull(i + 1)?.trim()?.let(Key::parse) != null -> {
                out += "{key: ${lines[i + 1].trim()}}"
                i++
            }
            RULE_LINE.matches(line) -> out += trimmed
            TRANSPOSE_NOTE.matches(line) -> out += "{comment: $trimmed}"
            metadataDirective(line) != null -> out += metadataDirective(line)!!
            isDashTimingLine(line) -> out += bracketDashLine(line)
            isChordLine(line) -> {
                val next = lines.getOrNull(i + 1)
                // Never merge into a chord line or a line that's already ChordPro.
                val nextIsLyric = next != null && next.isNotBlank() && !isChordLine(next) && scanChordLine(next) == null &&
                    !hasInlineChords(next) && header(next) == null && !next.trimStart().startsWith("{") &&
                    !next.trimStart().startsWith("//") &&
                    metadataDirective(next) == null && !KEY_LABEL.matches(next) && !RULE_LINE.matches(next) &&
                    !TRANSPOSE_NOTE.matches(next)
                if (nextIsLyric) {
                    out += merge(line, next)
                    i++
                } else {
                    out += scanChordLine(line) ?: chordsOnly(line)
                }
            }
            // Any other line that is mostly chords ("ARISE(D--A--Bm--G--)", "G to D", "Gm/ | F/ |").
            else -> out += scanChordLine(line) ?: labelsAsNotes(line)
        }
        i++
    }
    closeSection()
    while (out.isNotEmpty() && out.last().isBlank()) out.removeAt(out.lastIndex)
    return out.joinToString("\n")
}
