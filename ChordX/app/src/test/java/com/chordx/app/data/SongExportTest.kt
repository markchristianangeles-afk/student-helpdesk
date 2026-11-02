package com.chordx.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SongExportTest {
    private val amazing = Song(
        title = "AMAZING GRACE", artist = "John Newton", key = "G", capo = 2, tempo = 72,
        timeSignature = "3/4", content = "[G]Amazing [C]grace\n[G]how sweet the [D]sound",
    )
    private val plain = Song(title = "SECOND SONG", content = "[A]La la [E]la")

    @Test
    fun exportedFileImportsBackWithTheSameDetails() {
        val imported = prepareImport(buildSongExport(listOf(amazing, plain)), "chordx-songs.txt")

        assertEquals(2, imported.size)
        with(imported[0]) {
            assertEquals("AMAZING GRACE", title)
            assertEquals("John Newton", artist)
            assertEquals("G", key)
            assertEquals("2", capo)
            assertEquals("72", tempo)
            assertEquals("3/4", timeSignature)
            assertEquals(true, content.endsWith(amazing.content))
        }
        assertEquals("SECOND SONG", imported[1].title)
        assertEquals(null, imported[1].artist)
        assertEquals(null, imported[1].capo)
    }

    @Test
    fun doesNotRepeatDirectivesTheChartAlreadyHas() {
        val song = Song(title = "X", key = "D", content = "{title: X}\n{key: D}\n[D]hi")
        val text = buildSongExport(listOf(song))
        assertEquals(1, Regex("\\{title:").findAll(text).count())
        assertFalse("{key: D}\n{key: D}" in text)
    }

    @Test
    fun noSongsExportsAnEmptyFile() {
        assertEquals("\n", buildSongExport(emptyList()))
    }
}
