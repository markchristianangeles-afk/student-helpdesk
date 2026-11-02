package com.chordx.app.ui.song

import com.chordx.app.data.ImportedSong
import com.chordx.app.data.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SongFormTest {
    private fun errorsOf(form: SongForm) = (validate(form) as SongFormResult.Invalid).errors

    @Test
    fun minimalFormIsValidWithDefaults() {
        val result = validate(SongForm(title = "  Way Maker  ")) as SongFormResult.Valid
        assertEquals("WAY MAKER", result.title)
        assertEquals(null, result.artist)
        assertEquals(null, result.key)
        assertEquals(0, result.capo)
        assertEquals(null, result.tempo)
        assertEquals(null, result.timeSignature)
    }

    @Test
    fun fullFormParsesNumbers() {
        val result = validate(
            SongForm(title = "T", artist = " A ", key = "F#m", capo = "3", tempo = "72", timeSignature = "6/8", content = "[G]x")
        ) as SongFormResult.Valid
        assertEquals("A", result.artist)
        assertEquals("F#m", result.key)
        assertEquals(3, result.capo)
        assertEquals(72, result.tempo)
        assertEquals("6/8", result.timeSignature)
        assertEquals("[G]x", result.content)
    }

    @Test
    fun blankTitleIsRejected() {
        assertEquals(setOf(SongField.TITLE), errorsOf(SongForm(title = "   ")).keys)
    }

    @Test
    fun badValuesAreRejected() {
        val errors = errorsOf(SongForm(title = "T", key = "H", capo = "12", tempo = "fast", timeSignature = "4-4"))
        assertEquals(setOf(SongField.KEY, SongField.CAPO, SongField.TEMPO, SongField.TIME_SIGNATURE), errors.keys)
    }

    @Test
    fun acceptsCommonKeys() {
        listOf("C", "G", "Bb", "Eb", "F#", "Am", "C#m", "Bbm").forEach { key ->
            assertTrue(key, validate(SongForm(title = "T", key = key)) is SongFormResult.Valid)
        }
    }

    @Test
    fun formRoundTripsFromSong() {
        val song = Song(title = "T", artist = "A", key = "D", capo = 0, tempo = 120, timeSignature = "3/4", content = "c")
        val form = SongForm.from(song)
        assertEquals("", form.capo) // 0 shows as empty rather than "0"
        val result = validate(form) as SongFormResult.Valid
        assertEquals(listOf("T", "A", "D", 0, 120, "3/4", "c"), with(result) { listOf(title, artist, key, capo, tempo, timeSignature, content) })
    }

    @Test
    fun detailsLineSkipsMissingValues() {
        assertEquals("Key G  ·  Capo 2  ·  4/4", songDetails(Song(title = "T", key = "G", capo = 2, timeSignature = "4/4")))
        assertEquals("", songDetails(Song(title = "T")))
    }

    @Test
    fun detailsLineShowsTransposedKey() {
        assertEquals("Key A (G +2)", songDetails(Song(title = "T", key = "G"), displayKey = "A", semitones = 2))
        assertEquals("Key F (G −2)", songDetails(Song(title = "T", key = "G"), displayKey = "F", semitones = -2))
    }

    @Test
    fun keyValidationMatchesTheMusicParser() {
        assertTrue(validate(SongForm(title = "T", key = "B♭m")) is SongFormResult.Valid)
        assertTrue(validate(SongForm(title = "T", key = "Gmaj")) is SongFormResult.Invalid)
    }

    @Test
    fun invalidImportDetailsAreDroppedNotFatal() {
        val song = toValidSong(ImportedSong(title = "", key = "H", capo = "99", tempo = "fast", timeSignature = "x", content = "[G]la"))
        assertEquals("UNTITLED", song.title)
        assertEquals(listOf(null, 0, null, null), listOf(song.key, song.capo, song.tempo, song.timeSignature))
        assertEquals("[G]la", song.content)
    }

    @Test
    fun importSummaryMessages() {
        assertEquals("Imported 1 song.", ImportSummary(1, 0, 0).message())
        assertEquals("Imported 3 songs. Skipped 2 already in your library. 1 file couldn't be read.", ImportSummary(3, 2, 1).message())
    }
}
