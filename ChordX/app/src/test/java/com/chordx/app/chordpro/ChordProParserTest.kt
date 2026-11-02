package com.chordx.app.chordpro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChordProParserTest {
    private fun messages(text: String) = parseChordPro(text).diagnostics.map { "${it.line}:${it.severity.name.first()}: ${it.message}" }

    @Test
    fun parsesChordsIntoSegments() {
        val doc = parseChordPro("[G]Amazing [C]grace, how [G]sweet")
        assertEquals(emptyList<Diagnostic>(), doc.diagnostics)
        val line = doc.sections.single().lines.single() as Line.Lyrics
        assertEquals(listOf("G" to "Amazing ", "C" to "grace, how ", "G" to "sweet"), line.segments.map { (it.chord as ChordRef.Known).raw to it.lyrics })
        assertEquals("Amazing grace, how sweet", line.text)
    }

    @Test
    fun keepsLeadingLyricsAndTrailingChord() {
        val line = parseChordPro("A-[D]ma-zing [G]").sections.single().lines.single() as Line.Lyrics
        assertEquals(listOf(null to "A-", "D" to "ma-zing ", "G" to ""), line.segments.map { (it.chord as ChordRef.Known?)?.raw to it.lyrics })
    }

    @Test
    fun annotationsAndNoChord() {
        val line = parseChordPro("[*Riff]Intro [N.C.]Stop").sections.single().lines.single() as Line.Lyrics
        assertEquals(ChordRef.Annotation("Riff"), line.segments[0].chord)
        assertEquals(ChordRef.NoChord, line.segments[1].chord)
    }

    @Test
    fun reportsBadChordsWithLineNumbers() {
        assertEquals(
            listOf(
                "2:E: “Hx” isn't a chord",
                "3:E: Empty chord []",
                "4:E: Chord bracket at column 4 is never closed",
                "5:E: Unexpected ] at column 3",
            ),
            messages("[G]fine\n[Hx]bad\n[]empty\nla [G oops\nla] x"),
        )
    }

    @Test
    fun invalidChordIsKeptAsWritten() {
        val line = parseChordPro("[Chorus]la").sections.single().lines.single() as Line.Lyrics
        assertEquals(ChordRef.Invalid("Chorus"), line.segments[0].chord)
    }

    @Test
    fun readsMetadataAndValidatesIt() {
        val doc = parseChordPro("{title: Amazing Grace}\n{st: Newton}\n{key: G}\n{capo: 2}\n{tempo: 72}\n{time: 3/4}")
        assertEquals(emptyList<Diagnostic>(), doc.diagnostics)
        assertEquals(mapOf("title" to "Amazing Grace", "subtitle" to "Newton", "key" to "G", "capo" to "2", "tempo" to "72", "time" to "3/4"), doc.metadata)
        assertTrue(doc.sections.isEmpty())

        assertEquals(
            listOf(
                "1:E: “H” isn't a key; use e.g. G, Bb or F#m",
                "2:E: Capo must be a fret number from 0 to 11",
                "3:E: Tempo must be a number of beats per minute",
                "4:E: Time must look like 4/4 or 6/8",
                "5:E: {title} needs a value",
            ),
            messages("{key: H}\n{capo: 14}\n{tempo: fast}\n{time: 4-4}\n{title}"),
        )
    }

    @Test
    fun malformedAndUnknownDirectives() {
        assertEquals(
            listOf(
                "1:E: Directive is missing its closing }",
                "2:E: Malformed directive; use {name} or {name: value}",
                "3:W: Unknown directive {flavour} was ignored",
                "4:W: {transpose} is ignored; use the Transpose control instead",
            ),
            messages("{title: oops\n{title Amazing}\n{flavour: vanilla}\n{transpose: 2}\n{x_custom: fine}\n{new_page}\n{textsize: 12}"),
        )
    }

    @Test
    fun sectionsWithLabelsAndDefaults() {
        val doc = parseChordPro(
            """
            {start_of_verse: Verse 1}
            [G]One
            {end_of_verse}
            {soc}
            [C]Two
            {eoc}
            {start_of_bridge: label="Middle 8"}
            [D]Three
            {end_of_bridge}
            {start_of_pre_chorus}
            Four
            {end_of_pre_chorus}
            """.trimIndent()
        )
        assertEquals(emptyList<Diagnostic>(), doc.diagnostics)
        assertEquals(
            listOf(SectionKind.VERSE to "Verse 1", SectionKind.CHORUS to "Chorus", SectionKind.BRIDGE to "Middle 8", SectionKind.CUSTOM to "Pre chorus"),
            doc.sections.map { it.kind to it.label },
        )
    }

    @Test
    fun looseLinesBetweenSectionsFormTheirOwnSections() {
        val doc = parseChordPro("Intro line\n\n{start_of_chorus}\nla\n{end_of_chorus}\n\nOutro line\n")
        assertEquals(listOf(null, SectionKind.CHORUS, null), doc.sections.map { it.kind })
        assertEquals(1, doc.sections[0].lines.size) // trailing blank trimmed
    }

    @Test
    fun sectionNestingErrors() {
        assertEquals(
            listOf(
                "3:E: {start_of_chorus} begins before {end_of_verse} closed the section on line 1",
                "5:E: {end_of_verse} doesn't match {start_of_chorus} on line 3",
                "6:E: {end_of_bridge} has no matching {start_of_bridge}",
                "7:E: {start_of_tab} is never closed; add {end_of_tab}",
            ),
            messages("{sov}\na\n{soc}\nb\n{eov}\n{eob}\n{sot}\ne|---"),
        )
    }

    @Test
    fun chorusRecallRepeatsTheLastChorus() {
        val doc = parseChordPro("{soc}\n[G]Sing\n{eoc}\n{sov}\nv\n{eov}\n{chorus}\n{chorus: Final chorus}")
        val recalls = doc.sections.filter { it.isRecall }
        assertEquals(listOf("Chorus", "Final chorus"), recalls.map { it.label })
        assertEquals(doc.sections[0].lines, recalls[0].lines)
    }

    @Test
    fun chorusRecallBeforeAnyChorusWarns() {
        assertEquals(listOf("1:W: {chorus} is used before any chorus is defined"), messages("{chorus}"))
    }

    @Test
    fun tabSectionsAreLiteral() {
        val doc = parseChordPro("{sot}\ne|--[3]--|\n{not a directive\n{eot}")
        assertEquals(emptyList<Diagnostic>(), doc.diagnostics)
        assertEquals(listOf(Line.Preformatted("e|--[3]--|"), Line.Preformatted("{not a directive")), doc.sections.single().lines)
    }

    @Test
    fun gridSectionsFindChords() {
        val doc = parseChordPro("{start_of_grid}\n| G . . . | C . . . |\n{end_of_grid}")
        val grid = doc.sections.single().lines.single() as Line.Grid
        assertEquals(listOf("G", "C"), grid.tokens.mapNotNull { (it.chord as? ChordRef.Known)?.raw })
        assertEquals("| G . . . | C . . . |", grid.tokens.joinToString("") { (it.chord as? ChordRef.Known)?.raw ?: it.lyrics })
    }

    @Test
    fun commentsAndHashLines() {
        val doc = parseChordPro("# hidden note\n{c: Slowly}\n{ci: softly}\n{cb: Key change}")
        assertEquals(
            listOf(Line.Comment("Slowly"), Line.Comment("softly", italic = true), Line.Comment("Key change", boxed = true)),
            doc.sections.single().lines,
        )
    }

    @Test
    fun doubleSlashLinesAreComments() {
        val doc = parseChordPro("[D]--[G]--\n// if pa chorus, wala ng Bm\n//\n[A]--")
        assertEquals(emptyList<Diagnostic>(), doc.diagnostics)
        assertEquals(Line.Comment("if pa chorus, wala ng Bm"), doc.sections.single().lines[1])
        assertEquals(3, doc.sections.single().lines.size) // a bare "//" shows nothing
    }

    @Test
    fun doubleSlashAtTheEndOfALineIsAComment() {
        val lines = parseChordPro("[D/F#]--[G]--[A]--[Bm]--(REPEAT) // if pa chorus, wala ng Bm").sections.single().lines
        assertEquals(2, lines.size)
        assertEquals(listOf("D/F#", "G", "A", "Bm"), (lines[0] as Line.Lyrics).segments.mapNotNull { (it.chord as? ChordRef.Known)?.raw })
        assertEquals(Line.Comment("if pa chorus, wala ng Bm"), lines[1])
        // Not comments: rhythm slashes, a chord after //, and web links.
        listOf("[F]F // | [C/E]C/E //", "[Bm]la // [G]la", "see http://example.com").forEach { text ->
            assertTrue(text, parseChordPro(text).sections.single().lines.none { it is Line.Comment })
        }
    }

    @Test
    fun windowsLineEndingsCountLinesCorrectly() {
        assertEquals(listOf("2:E: “Q” isn't a chord"), messages("[G]a\r\n[Q]b\r\n"))
    }

    @Test
    fun flatPreferenceCountsAccidentals() {
        assertTrue(parseChordPro("[Bb]a [Eb]b [F]c").prefersFlats())
        assertTrue(!parseChordPro("[F#m]a [Bb]b [C#]c").prefersFlats())
    }
}
