package com.chordx.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SongImportTest {
    @Test
    fun decodesPlainUtf8AndNormalisesLineEndings() {
        assertEquals("Café line\nnext\nlast", decodeText("Café line\r\nnext\rlast".toByteArray(Charsets.UTF_8)))
    }

    @Test
    fun stripsUtf8ByteOrderMark() {
        val bytes = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + "{title: X}".toByteArray()
        assertEquals("{title: X}", decodeText(bytes))
    }

    @Test
    fun decodesUtf16WithByteOrderMarks() {
        val le = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + "Grace — G".toByteArray(Charsets.UTF_16LE)
        val be = byteArrayOf(0xFE.toByte(), 0xFF.toByte()) + "Grace — G".toByteArray(Charsets.UTF_16BE)
        assertEquals("Grace — G", decodeText(le))
        assertEquals("Grace — G", decodeText(be))
    }

    @Test
    fun fallsBackToWindows1252ForNonUtf8Bytes() {
        // 0x92 is a curly apostrophe in Windows-1252 and invalid on its own in UTF-8.
        val bytes = "Don".toByteArray() + byteArrayOf(0x92.toByte()) + "t stop".toByteArray()
        assertEquals("Don’t stop", decodeText(bytes))
    }

    @Test
    fun readsMetadataDirectivesIncludingShortForms() {
        val text = """
            {t: Amazing Grace}
            {st: John Newton}
            {key: G}
            {capo: 2}
            {tempo: 72}
            {time: 3/4}
            [G]Amazing [C]grace
        """.trimIndent()
        val song = parseImportedSong(text, "whatever.txt")
        assertEquals("Amazing Grace", song.title)
        assertEquals("John Newton", song.artist)
        assertEquals("G", song.key)
        assertEquals("2", song.capo)
        assertEquals("72", song.tempo)
        assertEquals("3/4", song.timeSignature)
        assertEquals(text, song.content) // the chart is kept exactly, directives included
    }

    @Test
    fun firstDirectiveWinsAndCaseIsIgnored() {
        val song = parseImportedSong("{TITLE: First}\n{title: Second}\n{Artist: A}\n{subtitle: B}", null)
        assertEquals("First", song.title)
        assertEquals("A", song.artist)
    }

    @Test
    fun titleFallsBackToFileName() {
        assertEquals("Way Maker", parseImportedSong("[G]Way maker", "Way_Maker.txt").title)
        assertEquals("Goodness of God", parseImportedSong("x", "Goodness of God.chordpro").title)
        assertEquals("folder name", parseImportedSong("x", "docs/folder name.cho").title)
        assertEquals("Holy Forever", parseImportedSong("x", "C:\\charts\\Holy Forever.txt").title)
        assertEquals("", parseImportedSong("x", null).title)
    }

    @Test
    fun ignoresNonMetadataAndEmptyDirectives() {
        val song = parseImportedSong("{comment: Intro}\n{title: }\n{start_of_chorus}\nla", "Song.txt")
        assertEquals("Song", song.title)
        assertNull(song.artist)
        assertNull(song.key)
    }

    @Test
    fun prepareImportSplitsOnNewSong() {
        val songs = prepareImport("{title: One}\n[G]a\n{new_song}\n{title: Two}\n[C]b\n{ns}\n[D]c", "Medley.cho")
        assertEquals(listOf("One", "Two", "Medley (3)"), songs.map { it.title })
        assertEquals("{title: Two}\n[C]b", songs[1].content)
    }

    @Test
    fun prepareImportConvertsPlainChordSheets() {
        val song = prepareImport("Key: G\n\nG       C\nAmazing grace\n", "Amazing Grace.txt").single()
        assertEquals(true, song.convertedFromPlainText)
        assertEquals("G", song.key) // the "Key: G" line became {key: G}
        assertEquals("{key: G}\n\n[G]Amazing [C]grace", song.content)
    }

    @Test
    fun prepareImportLeavesChordProAlone() {
        val song = prepareImport("{title: X}\n[G]la", "x.cho").single()
        assertEquals(false, song.convertedFromPlainText)
        assertEquals("{title: X}\n[G]la", song.content)
    }

    @Test
    fun leadingTitleLineBecomesTheTitleAndLeavesTheChart() {
        val song = prepareImport("We're Together Again\n\nVerse:\nD----G----\n", "WE_RE TOGETHER AGAIN.txt").single()
        assertEquals("We're Together Again", song.title)
        assertEquals("{start_of_verse: Verse}\n[D]----[G]----\n{end_of_verse}", song.content)
    }

    @Test
    fun firstLineThatIsNotTheTitleStays() {
        val song = prepareImport("Holy is the Lord\nG--C--", "Holy.txt").single()
        assertEquals("Holy", song.title)
        assertTrue(song.content.startsWith("Holy is the Lord"))
    }

    @Test
    fun keyOnTheNextLineIsRead() {
        assertEquals("E", prepareImport("SONG\n\nKEY:\nE\n\nE----A----", "SONG.txt").single().key)
    }

    @Test
    fun singleSongFileKeepsPlainFileNameTitle() {
        assertEquals("Holy Forever", prepareImport("la", "Holy_Forever.txt").single().title)
    }

    @Test
    fun trailingWhitespaceIsTrimmedFromChart() {
        assertEquals("[G]la", parseImportedSong("[G]la\n\n\n", "a.txt").content)
    }
}
