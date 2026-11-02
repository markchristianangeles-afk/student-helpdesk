package com.chordx.app.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class MusicTest {
    private fun chord(text: String) = Chord.parse(text)!!

    @Test
    fun parsesCommonChords() {
        listOf(
            "C", "Am", "G7", "Cmaj7", "Dsus4", "Dsus2", "Esus", "Asus2/C#", "F#m7b5", "Bb", "Ebmaj9",
            "C2", "G/B", "D/F#", "Cadd9", "Am(add9)", "E7#9", "C6/9", "Bdim", "C°7", "Faug", "C+",
            "G5", "Cm/Eb", "A7alt", "CM7", "C-7", "Bb2/D", "F♯", "Gm(maj7)",
        ).forEach { assertNotNull(it, Chord.parse(it)) }
    }

    @Test
    fun rejectsWordsAndJunk() {
        listOf("Chorus", "Verse", "Hello", "H", "c", "am", "Amazing", "Bridge", "G/H", "", "Cx", "Dear", "Bm--", "G7--").forEach {
            assertNull(it, Chord.parse(it))
        }
    }

    @Test
    fun splitsRootSuffixAndBass() {
        val c = chord("F#m7/C#")
        assertEquals(Note('F', 1), c.root)
        assertEquals("m7", c.suffix)
        assertEquals(Note('C', 1), c.bass)
        assertEquals("C6/9", chord("C6/9").toString())
        assertNull(chord("C6/9").bass)
    }

    @Test
    fun unicodeAccidentalsAreReadAndWrittenAsAscii() {
        assertEquals("F#m", chord("F♯m").toString())
        assertEquals("Bb", chord("B♭").toString())
    }

    @Test
    fun transposesWithSharpsOrFlats() {
        assertEquals("A#m7", chord("Gm7").transpose(3, SharpSpelling).toString())
        assertEquals("Bbm7", chord("Gm7").transpose(3, FlatSpelling).toString())
        assertEquals("E/G#", chord("D/F#").transpose(2, SharpSpelling).toString())
        assertEquals("C", chord("C").transpose(12, SharpSpelling).toString())
        assertEquals("B", chord("C").transpose(-1, FlatSpelling).toString())
    }

    @Test
    fun keyParsingAndConventionalNames() {
        assertEquals(Key(Note('B', -1), minor = true), Key.parse("Bbm"))
        assertNull(Key.parse("H"))
        assertNull(Key.parse("Gmaj"))
        assertEquals("Bb", Key.parse("G")!!.transpose(3).toString())
        assertEquals("F#", Key.parse("E")!!.transpose(2).toString())
        assertEquals("Db", Key.parse("C")!!.transpose(1).toString())
        assertEquals("C#m", Key.parse("Am")!!.transpose(4).toString())
        assertEquals("G#m", Key.parse("Em")!!.transpose(4).toString())
        assertEquals(listOf("C", "Db", "D", "Eb", "E", "F", "F#", "G", "Ab", "A", "Bb", "B"), Key.all(minor = false).map { it.toString() })
    }

    @Test
    fun semitoneDistanceTakesShortestWay() {
        assertEquals(2, Key.parse("G")!!.semitonesTo(Key.parse("A")!!))
        assertEquals(-2, Key.parse("A")!!.semitonesTo(Key.parse("G")!!))
        assertEquals(6, Key.parse("C")!!.semitonesTo(Key.parse("F#")!!))
        assertEquals(-5, Key.parse("C")!!.semitonesTo(Key.parse("G")!!))
    }

    @Test
    fun keySpellingFollowsScaleDegrees() {
        fun names(key: String) = (0..11).map { KeySpelling(Key.parse(key)!!).name(it).toString() }
        assertEquals(listOf("C", "Db", "D", "Eb", "E", "F", "F#", "G", "Ab", "A", "Bb", "B"), names("C"))
        assertEquals(listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"), names("E"))
        assertEquals(listOf("C", "Db", "D", "Eb", "E", "F", "Gb", "G", "Ab", "A", "Bb", "B"), names("Eb"))
        // Minor keys spell like their relative major.
        assertEquals(names("Bb"), names("Gm"))
        assertEquals(names("D"), names("Bm"))
    }

    @Test
    fun keySpellingAvoidsAwkwardNames() {
        // F# major's 7th is E#, and Gb major's 4th is Cb: shown as F and B instead.
        assertEquals("F", KeySpelling(Key.parse("F#")!!).name(5).toString())
        assertEquals("B", KeySpelling(Key.parse("Gb")!!).name(11).toString())
    }

    @Test
    fun normalisesSemitonesIntoMinusFiveToSix() {
        assertEquals(listOf(0, 1, 6, -5, -1, 0, 2), listOf(0, 1, 6, 7, 11, 12, -10).map(::normalizeSemitones))
    }
}
