package com.chordx.app.ui

import com.chordx.app.chordpro.ChordRenderer
import com.chordx.app.chordpro.parseChordPro
import com.chordx.app.ui.setlist.ChartLineKind
import com.chordx.app.ui.setlist.chartTextLines
import com.chordx.app.ui.setlist.pdfFileName
import org.junit.Assert.assertEquals
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SetlistPdfTest {
    @Test
    fun chordsSitAboveTheirLyricsAndLabelsComeFirst() {
        val doc = parseChordPro("{start_of_verse: Verse 1}\n[G]Amazing [C]grace\n{end_of_verse}")
        val lines = chartTextLines(doc, ChordRenderer.AsWritten)
        assertEquals("Verse 1", lines[0].text)
        assertEquals(ChartLineKind.LABEL, lines[0].kind)
        assertEquals("G       C", lines[1].text)
        assertEquals("Amazing grace", lines[2].text)
    }

    @Test
    fun closeChordsAreNotPushedTogether() {
        val doc = parseChordPro("[Am7]a[D]b")
        val lines = chartTextLines(doc, ChordRenderer.AsWritten)
        assertEquals("Am7 D", lines[0].text)
    }

    @Test
    fun fileNameIsSafe() {
        assertEquals("Sunday- AM - setlist summary.pdf", pdfFileName("Sunday/ AM"))
    }
}
