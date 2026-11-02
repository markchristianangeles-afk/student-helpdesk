package com.chordx.app.ui.chart

import com.chordx.app.chordpro.ChordRef
import com.chordx.app.chordpro.Line
import com.chordx.app.chordpro.parseChordPro
import org.junit.Assert.assertEquals
import org.junit.Test

class ChartLayoutTest {
    private fun chunks(text: String) =
        chunksOf((parseChordPro(text).sections.single().lines.single() as Line.Lyrics).segments)
            .map { Triple((it.chord as? ChordRef.Known)?.raw, it.text, it.spaceAfter) }

    @Test
    fun splitsAtWordsKeepingChordOnFirstWord() {
        assertEquals(
            listOf(Triple("G", "Amazing", true), Triple("C", "grace,", true), Triple(null, "how", true), Triple("G", "sweet", false)),
            chunks("[G]Amazing [C]grace, how [G]sweet"),
        )
    }

    @Test
    fun midWordChordAndChordOnlyChunks() {
        assertEquals(
            listOf(Triple(null, "A-", false), Triple("D", "ma-zing", true), Triple("G", "", false), Triple("C", "", false)),
            chunks("A-[D]ma-zing [G][C]"),
        )
    }
}
