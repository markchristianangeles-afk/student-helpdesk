package com.chordx.app.chordpro

import org.junit.Assert.assertEquals
import org.junit.Test

class DashTimingTest {
    /** "A----A7----" style text for one line, the way the chords-only view shows it. */
    private fun shown(line: String, beats: Int = 4): String {
        val lyrics = parseChordPro(line).sections.single().lines.single() as Line.Lyrics
        return dashTiming(lyrics, beats).joinToString("") { (chord, dashes) ->
            ChordRenderer.AsWritten.display(chord) + "-".repeat(dashes) + if (dashes == 0) " " else ""
        }.trimEnd()
    }

    @Test
    fun lyricLinesGetOneBarPerChord() {
        assertEquals("A----A7----D----A----", shown("[A]Amazing [A7]grace, how [D]sweet the [A]sound"))
    }

    @Test
    fun barLengthFollowsTimeSignature() {
        assertEquals("G---C---", shown("[G]Amazing [C]grace", beats = 3))
        assertEquals("G------", shown("[G]la", beats = 6))
    }

    @Test
    fun writtenDashesAreKeptExactly() {
        assertEquals("A--A7--D----A----", shown("[A]--[A7]--[D]----[A]----"))
        assertEquals("G-F-", shown("[G]-[F]-"))
    }

    @Test
    fun writtenDashesMayIncludeSpacesBarLinesAndTypographicDashes() {
        assertEquals("G--C--", shown("| [G]- - [C]-- |"))
        assertEquals("G--C---", shown("[G]––[C]———"))
    }

    @Test
    fun hyphenatedLyricsAreNotTiming() {
        assertEquals("G----D----", shown("[G]Hallelu-jah [D]amen"))
        assertEquals("D----", shown("A-[D]ma-zing"))
    }

    @Test
    fun chordWithNoLyricsGetsABar() {
        assertEquals("G----C----", shown("[G][C]"))
    }

    @Test
    fun annotationsHaveNoDashesButNoChordDoes() {
        assertEquals("Riff G----N.C.----", shown("[*Riff][G]la [N.C.]stop"))
    }

    private fun lyricsLine(text: String) = parseChordPro(text).sections.single().lines.single() as Line.Lyrics

    @Test
    fun chordLinesWithoutWordsAreChordsOnly() {
        listOf("[C]--[G/B]--[Am7]--[G]--", "| [G] [C] | [D] x2", "[G]-[F]- (2x)", "[Am]----   [Dm7]--  ×2", "[*Riff][G]--")
            .forEach { assertEquals(it, true, lyricsLine(it).isChordsOnly()) }
        listOf("[G]Amazing grace", "[G]A [C]B", "[G]Hallelu-jah", "no chords at all", "[G]1 2 3 4")
            .forEach { assertEquals(it, false, lyricsLine(it).isChordsOnly()) }
    }

    @Test
    fun wholeChartIsChordsOnlyOnlyWithoutAnyLyrics() {
        assertEquals(true, parseChordPro("{title: X}\n{sov: Intro}\n[C]--[G/B]--\n{eov}\n{c: softly}\n\n[Am]----").isChordsOnly())
        assertEquals(false, parseChordPro("[C]--[G/B]--\n[G]Amazing grace").isChordsOnly())
        assertEquals(false, parseChordPro("[C]--[G]--\nSing it twice").isChordsOnly()) // a lyric-only line counts
        assertEquals(false, parseChordPro("{c: no chord lines}").isChordsOnly())
    }

    @Test
    fun accentsDirectionsAndRepeatsDontBreakWrittenTiming() {
        assertEquals("B/E--C#m7-A--", shown("[B/E]--^[C#m7]-[A]--REPEAT"))
        assertEquals(true, lyricsLine("[B/E]--^[C#m7]-[A]--REPEAT").isChordsOnly())
        assertEquals(true, lyricsLine("°[D]°[D]-[E]---- (3x)").isChordsOnly())
        assertEquals(false, lyricsLine("[G]friend of mine").isChordsOnly()) // "end" inside a word is still lyrics
    }

    @Test
    fun chordLinesWithNotesVersusLyricLinesWithHeldChords() {
        listOf("ARISE([D]--[A]--[Bm]--[G]--)", "[D/F#]--[G]--[A]--[Bm]--(REPEAT)if pa chorus,wala ng [Bm]", "(FOR 2ND VERSE) [Am7]--[F]--[G]----", "[Gm]/ | [F]/ | [C/E] / /")
            .forEach { assertEquals(it, true, lyricsLine(it).isChordsOnly()) }
        listOf("[D]Yeah [G] [A]", "| [G]-- [C]-- [D]Hallelu-jah", "[G] to [D] the end")
            .forEach { assertEquals(it, false, lyricsLine(it).isChordsOnly()) }
    }

    @Test
    fun beatsPerBarParsing() {
        assertEquals(listOf(4, 3, 6, 4, 4, 4), listOf("4/4", "3/4", "6/8", null, "fast", "0/4").map(::beatsPerBar))
    }
}
