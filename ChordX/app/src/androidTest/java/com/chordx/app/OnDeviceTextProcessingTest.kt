package com.chordx.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.chordx.app.chordpro.Line
import com.chordx.app.chordpro.Severity
import com.chordx.app.chordpro.beatsPerBar
import com.chordx.app.chordpro.dashTiming
import com.chordx.app.chordpro.isChordsOnly
import com.chordx.app.chordpro.isDashTimingLine
import com.chordx.app.chordpro.parseForDisplay
import com.chordx.app.chordpro.convertChordsOverLyrics
import com.chordx.app.chordpro.convertChordsOverLyrics
import com.chordx.app.chordpro.looksLikeChordsOverLyrics
import com.chordx.app.chordpro.parseChordPro
import com.chordx.app.data.prepareImport
import com.chordx.app.music.Chord
import com.chordx.app.music.Key
import com.chordx.app.ui.song.SongForm
import com.chordx.app.ui.song.SongFormResult
import com.chordx.app.ui.song.validate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runs the text-processing code on a real Android runtime. Android's ICU regex engine
 * is stricter than desktop Java's (e.g. it rejects a bare "}"), so patterns that pass
 * the JVM unit tests can still crash on a phone. Touching every file that holds a
 * pattern here catches that.
 */
@RunWith(AndroidJUnit4::class)
class OnDeviceTextProcessingTest {
    @Test
    fun chordProParsing() {
        val doc = parseChordPro("{title: X}\n{start_of_chorus: label=\"C1\"}\n[G]la [Hx]\n{end_of_chorus}\n{sog}\n| G . |\n{eog}")
        assertEquals("X", doc.metadata["title"])
        assertEquals(1, doc.diagnostics.count { it.severity == Severity.ERROR })
    }

    @Test
    fun dashTimingPatterns() {
        val line = parseChordPro("| [G]–– [C]-- [D]Hallelu-jah").sections.single().lines.single() as Line.Lyrics
        assertEquals(listOf(2, 2, 3), dashTiming(line, beatsPerBar("3/4")).map { it.dashes })
        assertTrue(parseChordPro("[C]--[G/B]--[Am7]--[G]-- (2x) | ×2").isChordsOnly())
        assertTrue(!line.isChordsOnly())
        assertTrue(isDashTimingLine("C--G/B--Am7--G-/F-2x"))
        assertTrue(parseForDisplay("CHO:\r\nC----F----Dm--G--C--").isChordsOnly())
    }

    @Test
    fun teamFilePatterns() {
        // Lookbehind/lookahead splitting, inline (?i:) groups and the heading list, on the device's regex engine.
        val out = convertChordsOverLyrics("VERSE:  B/E--^^C#m7-°D°D-A--REPEAT\nKEY:\nE\nBRID:\nIntro: Keyboard\nD--\n____\nTRANSPOSE: KEY of E")
        assertTrue(out, out.contains("[B/E]--^^[C#m7]-°[D]°[D]-[A]--REPEAT"))
        assertTrue(out, out.contains("{key: E}"))
        assertTrue(out, out.contains("{start_of_bridge: BRID}"))
        assertTrue(out, out.contains("Intro (Keyboard)"))
        assertTrue(parseForDisplay(out).isChordsOnly())
        assertEquals("We're Here", prepareImport("We're Here\nG--", "WE_RE HERE.txt").single().title)
    }

    @Test
    fun plainTextConversion() {
        val text = "Key: G\nCapo 2\n[Verse 1]\nG     C | x2\nHello world\nChorus x2\nIntro: G C"
        assertTrue(looksLikeChordsOverLyrics(text))
        assertTrue(convertChordsOverLyrics(text).contains("[G]Hello [C]world"))
    }

    @Test
    fun importPipeline() {
        val songs = prepareImport("{title: A}\n[G]a\n{new_song}\n{t: B}\n[C]b", "Two.cho")
        assertEquals(listOf("A", "B"), songs.map { it.title })
    }

    @Test
    fun musicAndFormPatterns() {
        assertNotNull(Chord.parse("C6/9"))
        assertNotNull(Key.parse("F#m"))
        assertTrue(validate(SongForm(title = "T", key = "Bb", timeSignature = "6/8")) is SongFormResult.Valid)
    }
}
