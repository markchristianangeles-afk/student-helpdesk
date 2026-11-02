package com.chordx.app.chordpro

import com.chordx.app.music.Accidentals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlainTextTest {
    @Test
    fun recognisesChordLines() {
        assertTrue(isChordLine("G        C        G"))
        assertTrue(isChordLine("  Am7  D/F#  | G  (C) x2"))
        assertTrue(isChordLine("N.C.   G"))
        assertFalse(isChordLine("Amazing grace how sweet"))
        assertFalse(isChordLine("A mighty fortress"))
        assertFalse(isChordLine("Am I lost"))
        assertFalse(isChordLine("|  |  -"))
        assertFalse(isChordLine(""))
    }

    @Test
    fun mergesChordsAtTheirColumns() {
        val text = """
            G         C          G
            Amazing   grace, how sweet
        """.trimIndent()
        assertEquals("[G]Amazing   [C]grace, how [G]sweet", convertChordsOverLyrics(text))
    }

    @Test
    fun chordsBeyondTheLyricAreAppended() {
        assertEquals("[G]Hi    [C]", convertChordsOverLyrics("G     C\nHi"))
    }

    @Test
    fun chordInsideAWord() {
        assertEquals("A-[D]ma-zing", convertChordsOverLyrics("  D\nA-ma-zing"))
    }

    @Test
    fun chordLinesWithoutLyricsBecomeChordOnlyLines() {
        assertEquals("[G]  [C] | [D]\n\n[G]la", convertChordsOverLyrics("G  C | D\n\nG\nla"))
    }

    @Test
    fun headingsBecomeSections() {
        val text = """
            Key: G
            Capo 2

            [Verse 1]
            G       C
            Amazing grace

            Chorus:
            D         G
            How sweet the sound

            Intro: G C D
        """.trimIndent()
        val expected = """
            {key: G}
            {capo: 2}

            {start_of_verse: Verse 1}
            [G]Amazing [C]grace
            {end_of_verse}

            {start_of_chorus: Chorus}
            [D]How sweet [G]the sound
            {end_of_chorus}

            {start_of_verse: Intro}
            [G] [C] [D]
            {end_of_verse}
        """.trimIndent()
        assertEquals(expected, convertChordsOverLyrics(text))
        assertEquals(emptyList<Diagnostic>(), parseChordPro(convertChordsOverLyrics(text)).diagnostics)
    }

    @Test
    fun headingLookalikeLyricsStayLyrics() {
        assertEquals("Bridge over troubled water\nChorus of angels", convertChordsOverLyrics("Bridge over troubled water\nChorus of angels"))
    }

    @Test
    fun headingWithRepeatMark() {
        assertEquals("{start_of_chorus: Chorus}\n[G]la\n{end_of_chorus}", convertChordsOverLyrics("Chorus x2\nG\nla"))
    }

    @Test
    fun metadataLookalikeLyricsStayLyrics() {
        assertEquals("Key of David\nTime after time", convertChordsOverLyrics("Key of David\nTime after time"))
    }

    @Test
    fun tabsAreExpanded() {
        assertEquals("[G]Amazing [C]grace", convertChordsOverLyrics("G\tC\nAmazing grace"))
    }

    // --- Dash-timing charts (the team's chord-only format) ---

    @Test
    fun recognisesDashTimingLines() {
        listOf("C----F----Dm--G--C--", "C--G/B--Am7--G--2x", "G-/F-Esus--E--", "Em7--Am7--Dm7--G-/F-", "| C-- | G-- | (2x)", "C––G––")
            .forEach { assertTrue(it, isDashTimingLine(it)) }
        listOf("Hallelu-jah", "Come on-come on", "G C D", "A-men", "Verse", "")
            .forEach { assertFalse(it, isDashTimingLine(it)) }
    }

    @Test
    fun aPureHeartAsStored() {
        // Exactly as the song was saved on the device: Windows line ending, no brackets.
        val stored = "Verse\r\nC----F----Dm--G--C--"
        assertTrue(looksLikeChordsOverLyrics(stored))
        assertEquals("{start_of_verse: Verse}\n[C]----[F]----[Dm]--[G]--[C]--\n{end_of_verse}", convertChordsOverLyrics(stored))
    }

    @Test
    fun teamStyleChartWithAbbreviatedHeadings() {
        val text = "INTRO:\nC--G/B--Am7--G--2x\n\nCHO:\nEm7--Am7--Dm7--G-/F-\n\nIntro: C--G--"
        val expected = """
            {start_of_verse: INTRO}
            [C]--[G/B]--[Am7]--[G]--2x
            {end_of_verse}

            {start_of_chorus: CHO}
            [Em7]--[Am7]--[Dm7]--[G]-/[F]-
            {end_of_chorus}

            {start_of_verse: Intro}
            [C]--[G]--
            {end_of_verse}
        """.trimIndent()
        assertEquals(expected, convertChordsOverLyrics(text))
        assertEquals(emptyList<Diagnostic>(), parseChordPro(expected).diagnostics)
    }

    // --- Patterns from the team's real chart files ---

    @Test
    fun accentMarksDirectionsAndRepeatsInDashLines() {
        assertEquals("[A]----[D]--\n°[D]-°[E]-", convertChordsOverLyrics("A----D--\n°D-°E-"))
        assertEquals("°[D]°[D]-[E]----", convertChordsOverLyrics("°D°D-E----"))
        assertEquals("[Bm]---[A]-[G]---^^[D/A]-3X", convertChordsOverLyrics("Bm---A-G---^^D/A-3X"))
        assertEquals("[D]----[G]-[D]--- ^^^[Em]-", convertChordsOverLyrics("D----G-D--- ^^^Em-"))
        assertEquals("[C°7]--[G]--", convertChordsOverLyrics("C°7--G--")) // ° inside a chord is not an accent
        assertEquals("[D]--[A]--[C]--[G]--*[G]*[G]*[G]", convertChordsOverLyrics("D--A--C--G--*G*G*G")) // ARISE
        assertEquals("[Em]--*[D]-*[Em]---*[D]-*[Em]-", convertChordsOverLyrics("Em--*D-*Em---*D-*Em-")) // ONE GOD
        assertTrue(isDashTimingLine("B/E--^C#m7-B/E--^C#m7-F#m7--A--REPEAT"))
        assertFalse(isDashTimingLine("Come on-come on"))
    }

    @Test
    fun everyChordOnAChordLineIsBracketedAndEverythingElseKept() {
        // Lines from the team's files; only chords gain [brackets], every other character stays.
        mapOf(
            "ARISE(D--A--Bm--G--)" to "ARISE([D]--[A]--[Bm]--[G]--)",
            "A--E/G#--Bm--E--A--(Em7-A-A/C#)" to "[A]--[E/G#]--[Bm]--[E]--[A]--([Em7]-[A]-[A/C#])",
            "C---,---F---Dm---G---,---C---" to "[C]---,---[F]---[Dm]---[G]---,---[C]---",
            "Gm/ | F/ | C/E / / | F / |" to "[Gm]/ | [F]/ | [C/E] / / | [F] / |",
            "c#m-B-E-A-4x" to "[C#m]-[B]-[E]-[A]-4x",
            "G to D" to "[G] to [D]",
            "V. A----E----D--B/D#--E----" to "V. [A]----[E]----[D]--[B/D#]--[E]----",
            "D--A--C--G--*G*G*G" to "[D]--[A]--[C]--[G]--*[G]*[G]*[G]",
            "(FOR 2ND VERSE) Am7--F--G----" to "(FOR 2ND VERSE) [Am7]--[F]--[G]----",
            "--[Bm]--Em--A--D--" to "--[Bm]--[Em]--[A]--[D]--",
        ).forEach { (input, expected) -> assertEquals(input, expected, convertChordsOverLyrics(input)) }
    }

    @Test
    fun wordsAfterTheChordsBecomeAComment() {
        // BE UNTO YOU: a note to the band ("if going to the chorus, no more Bm"), kept word for word.
        assertEquals(
            "[D/F#]--[G]--[A]--[Bm]--(REPEAT)\n{comment: if pa chorus,wala ng Bm}",
            convertChordsOverLyrics("D/F#--G--A--Bm--(REPEAT)if pa chorus,wala ng Bm"),
        )
        assertEquals("[A/C#]-[C]--[D]--\n{comment: intro}", convertChordsOverLyrics("A/C#-C--D--intro"))
        // Directions and bracketed notes stay on the line; words before the chords are a label, not a note.
        assertEquals("[E]------[D/F#]-[A]- REPEAT", convertChordsOverLyrics("E------D/F#-A- REPEAT"))
        assertEquals("[D]--[G]--[D]--(Into 2 lines)", convertChordsOverLyrics("D--G--D--(Into 2 lines)"))
        assertEquals("V. [A]----[E]----", convertChordsOverLyrics("V. A----E----"))
        // The comment is a real ChordPro comment: shown as a note, and its "Bm" isn't a chord.
        // A copy saved by an earlier version, with "(Bm--)" glued on as "[Bm--]", is repaired too.
        assertEquals(
            "([Bm]--)[D/F#]--[G]--[A]--[Bm]--(REPEAT)\n{comment: if pa chorus,wala ng Bm}",
            convertChordsOverLyrics("[Bm--]D/F#--G--A--Bm--(REPEAT)if pa chorus,wala ng Bm"),
        )
        val doc = parseChordPro(convertChordsOverLyrics("D/F#--G--A--Bm--(REPEAT)if pa chorus,wala ng Bm"))
        assertEquals(emptyList<Diagnostic>(), doc.diagnostics)
        assertEquals(Line.Comment("if pa chorus,wala ng Bm"), doc.sections.single().lines[1])
    }

    @Test
    fun doubleSlashCommentsAreLeftForTheParser() {
        // "// note" lines pass through conversion untouched; the chord "Bm" in them is not bracketed.
        assertEquals("[D]--[G]--\n// if pa chorus, wala ng Bm", convertChordsOverLyrics("D--G--\n// if pa chorus, wala ng Bm"))
        // ...and at the end of a plain chord line, like a PHP comment.
        assertEquals("[D/F#]--[G]--[A]--[Bm]--(REPEAT)\n// if pa chorus, wala ng Bm", convertChordsOverLyrics("D/F#--G--A--Bm--(REPEAT) // if pa chorus, wala ng Bm"))
        // A chord line is never merged into a comment line below it.
        assertEquals("[G]  [C]\n// softly", convertChordsOverLyrics("G  C\n// softly"))
    }

    @Test
    fun bracketLabelsAreHeadingsOrNotesNotChords() {
        assertEquals("{start_of_verse: VERSE (BASS LINE)}\n[A]--[D]--\n{end_of_verse}", convertChordsOverLyrics("VERSE:[BASS LINE]\nA--D--"))
        assertTrue(convertChordsOverLyrics("[PRE CHORUS]\nG--C--").startsWith("{start_of_verse: PRE CHORUS}"))
        assertEquals("G--\nHallelujah (ADLIB)", convertChordsOverLyrics("G--\nHallelujah [ADLIB]").replace("[G]", "G"))
        assertEquals("[G]--\n{comment: BASS LINE}", convertChordsOverLyrics("G--\n[BASS LINE]"))
        assertEquals(emptyList<Diagnostic>(), parseChordPro(convertChordsOverLyrics("VERSE:[BASS LINE]\nA--D--\nHallelujah [ADLIB]")).diagnostics)
        // Chords in brackets after a heading stay chords.
        assertEquals("{start_of_verse: Transition}\n([Bb]--[Bb/C]--)\n{end_of_verse}", convertChordsOverLyrics("Transition (Bb--Bb/C--)"))
        // A fully bracketed chart with a word label is fixed for display; a typo like [Hx] still shows as an error.
        assertEquals(emptyList<Diagnostic>(), parseForDisplay("[G]Hallelujah [ADLIB]").diagnostics)
        assertEquals(1, parseForDisplay("[G]Hallelujah [Hx]").diagnostics.size)
    }

    @Test
    fun plusAdditionCountsAsAChordButKeepsItsText() {
        // WALA KANG KAKTULAD: "+M7" adds a major 7th to the Bm before it. E/GE# is a typo and stays text.
        assertEquals("[Bm]--[+M7]--[D/A]--E/GE#", convertChordsOverLyrics("Bm--+M7--D/A--E/GE#")) // the typo stays on the line
        val doc = parseChordPro("[Bm]--[+M7]--[D/A]--")
        assertEquals(emptyList<Diagnostic>(), doc.diagnostics)
        val plan = planChart(doc, "Bm", 2, null, Accidentals.AUTO)
        val shown = (doc.sections.single().lines.single() as Line.Lyrics).segments.mapNotNull { it.chord }.map(plan.renderer::display)
        assertEquals(listOf("C#m", "+M7", "E/B"), shown) // the addition reads the same in every key
    }

    @Test
    fun chordWrittenTwiceBeforeRhythmSlashes() {
        // GRANDEUR SOLEMN CHORDS: each chord appears in brackets and again as text.
        assertEquals(
            "[F] // | [C/E] // | [Bb] /// | [C] / |[A/C] / |",
            convertChordsOverLyrics("[F]F // | [C/E]C/E // | [Bb]Bb /// | [C]C / |A/C / |"),
        )
        // A lyric that starts with the same letter as its chord is left alone.
        assertEquals("[A]A mighty fortress", convertChordsOverLyrics("[A]A mighty fortress"))
    }

    @Test
    fun lyricsAreNotMistakenForChords() {
        listOf("A-men", "Hallelu-jah", "A mighty fortress", "(Rommel G.)", "[G]A [C]B", "Be still and know").forEach {
            assertEquals(it, it, convertChordsOverLyrics(it))
        }
    }

    @Test
    fun headingsWithChordsOnTheSameLine() {
        val text = "Intro: E--B/D#--F#m7--A/B--2X\n\nVERSE:  B/E--^C#m7-F#m7--A--REPEAT\n\nPRE:\tAM7--G#m-C#m-B----\n\nCHO:\tE--B/D#--"
        assertTrue(looksLikeChordsOverLyrics(text))
        val out = convertChordsOverLyrics(text)
        assertTrue(out, out.contains("{start_of_verse: Intro}\n[E]--[B/D#]--[F#m7]--[A/B]--2X"))
        assertTrue(out, out.contains("{start_of_verse: VERSE}\n[B/E]--^[C#m7]-[F#m7]--[A]--REPEAT"))
        assertTrue(out, out.contains("{start_of_verse: PRE}\n[AM7]--[G#m]-[C#m]-[B]----"))
        assertTrue(out, out.contains("{start_of_chorus: CHO}\n[E]--[B/D#]--"))
        assertEquals(emptyList<Diagnostic>(), parseChordPro(out).diagnostics)
    }

    @Test
    fun teamHeadingAbbreviations() {
        val out = convertChordsOverLyrics("Into:\nA--D--\nRef:\nG--\nBRID:\nEm--\nCho.\nC--\nInst:\nD--\nLAST 3\nA--")
        assertEquals(
            listOf("verse: Into", "verse: Ref", "bridge: BRID", "chorus: Cho", "verse: Inst", "verse: LAST 3"),
            Regex("""\{start_of_(\w+): ([^}]+)\}""").findAll(out).map { "${it.groupValues[1]}: ${it.groupValues[2]}" }.toList(),
        )
    }

    @Test
    fun headingNamingTheInstrument() {
        assertEquals("{start_of_verse: Intro (Keyboard)}\n[D]--\n{end_of_verse}", convertChordsOverLyrics("Intro: Keyboard\nD--"))
        assertEquals("Intro the darkness", convertChordsOverLyrics("Intro the darkness")) // still a lyric
    }

    @Test
    fun keyOnTheNextLineRulesAndTransposeNotes() {
        val out = convertChordsOverLyrics("KEY:\nE\n\nE----A----\n______________\n(F7)\nTRANSPOSE: KEY of E")
        assertEquals("{key: E}\n\n[E]----[A]----\n______________\n([F7])\n{comment: TRANSPOSE: KEY of E}", out) // brackets kept as typed
        // A "Key:" whose next line isn't a key is left alone.
        assertEquals("Key:\nhigher", convertChordsOverLyrics("Key:\nhigher"))
    }

    @Test
    fun dashLineIsNeverMergedIntoTheLineAbove() {
        assertEquals("[G]  [C]\n[D]--[G]--", convertChordsOverLyrics("G  C\nD--G--"))
    }

    @Test
    fun storedPlainChartIsConvertedForDisplayOnly() {
        val doc = parseForDisplay("Verse\r\nC----F----Dm--G--C--")
        assertTrue(doc.convertedForDisplay)
        assertTrue(doc.isChordsOnly())
        assertEquals(listOf("Verse"), doc.sections.map { it.label })
        assertEquals(emptyList<Diagnostic>(), doc.diagnostics)

        val chordPro = parseForDisplay("[G]Amazing grace")
        assertFalse(chordPro.convertedForDisplay)
    }

    @Test
    fun detectionOnlyFiresForPlainCharts() {
        assertTrue(looksLikeChordsOverLyrics("G   C\nAmazing grace"))
        assertFalse(looksLikeChordsOverLyrics("[G]Amazing [C]grace"))
        assertFalse(looksLikeChordsOverLyrics("Just some lyrics\nwith no chords"))
        assertFalse(looksLikeChordsOverLyrics("Verse 1\nla la"))
        assertTrue(looksLikeChordsOverLyrics("[Verse 1]\nG   C\nla la")) // UG-style heading is not a chord
    }
}
