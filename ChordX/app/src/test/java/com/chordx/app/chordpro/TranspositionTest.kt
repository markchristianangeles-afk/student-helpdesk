package com.chordx.app.chordpro

import com.chordx.app.music.Accidentals
import org.junit.Assert.assertEquals
import org.junit.Test

class TranspositionTest {
    private fun shown(content: String, key: String?, transpose: Int, pref: Accidentals = Accidentals.AUTO, override: String? = null): Pair<String?, List<String>> {
        val doc = parseChordPro(content)
        val plan = planChart(doc, key, transpose, override, pref)
        val chords = doc.sections.flatMap { it.lines }.filterIsInstance<Line.Lyrics>().flatMap { it.segments }.mapNotNull { it.chord }.map(plan.renderer::display)
        return plan.displayKey to chords
    }

    private val chart = "[G]a [C]b [D/F#]c [Em7]d [Bb]e [*riff]f [N.C.]g"

    @Test
    fun noTransposeKeepsChartExactlyAsWritten() {
        assertEquals("G" to listOf("G", "C", "D/F#", "Em7", "Bb", "riff", "N.C."), shown(chart, "G", 0))
    }

    @Test
    fun autoFollowsTargetKey() {
        // G -> A (sharp key)
        assertEquals("A" to listOf("A", "D", "E/G#", "F#m7", "C", "riff", "N.C."), shown(chart, "G", 2))
        // G -> Bb (flat key): Bb's b7 is Ab
        assertEquals("Bb" to listOf("Bb", "Eb", "F/A", "Gm7", "Db", "riff", "N.C."), shown(chart, "G", 3))
    }

    @Test
    fun forcedSpellingAppliesEvenWithoutTransposing() {
        assertEquals("G" to listOf("G", "C", "D/F#", "Em7", "A#", "riff", "N.C."), shown(chart, "G", 0, Accidentals.SHARPS))
        assertEquals("Ab" to listOf("Ab", "Db", "Eb/G", "Fm7", "B", "riff", "N.C."), shown(chart, "G", 1, Accidentals.FLATS))
        assertEquals("G#" to listOf("G#", "C#", "D#/G", "Fm7", "B", "riff", "N.C."), shown(chart, "G", 1, Accidentals.SHARPS))
    }

    @Test
    fun unknownKeyUsesChartsOwnHabit() {
        assertEquals(null to listOf("Eb", "Ab"), shown("[Db]a [Gb]b", null, 2))
        assertEquals(null to listOf("D#", "G#"), shown("[C#]a [F#]b", null, 2))
    }

    @Test
    fun largeShiftsWrapAround() {
        assertEquals("A" to listOf("A"), shown("[G]a", "G", 14))
        assertEquals("F" to listOf("F"), shown("[G]a", "G", -2))
    }

    @Test
    fun setlistKeyOverrideBeatsSongTranspose() {
        assertEquals("E" to listOf("E", "A"), shown("[G]a [C]b", "G", 2, override = "E"))
        // Same key as written: shown as-is
        assertEquals("G" to listOf("G", "C"), shown("[G]a [C]b", "G", 5, override = "G"))
    }

    @Test
    fun overrideIsIgnoredWithoutSongKey() {
        assertEquals(null to listOf("A"), shown("[G]a", null, 2, override = "E"))
    }

    @Test
    fun minorKeys() {
        assertEquals("Bm" to listOf("Bm", "G", "A"), shown("[Am]a [F]b [G]c", "Am", 2))
        assertEquals("Cm" to listOf("Cm", "Ab", "Bb"), shown("[Am]a [F]b [G]c", "Am", 3))
    }

    @Test
    fun gridChordsTranspose() {
        val doc = parseChordPro("{sog}\n| G . | C . |\n{eog}")
        val plan = planChart(doc, "G", 2, null, Accidentals.AUTO)
        val grid = doc.sections.single().lines.single() as Line.Grid
        assertEquals("| A . | D . |", grid.tokens.joinToString("") { seg -> seg.chord?.let(plan.renderer::display) ?: seg.lyrics })
    }
}
