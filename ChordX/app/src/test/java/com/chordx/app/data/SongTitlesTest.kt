package com.chordx.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class SongTitlesTest {
    @Test fun `title is capitalised`() {
        assertEquals("AMAZING GRACE" to null, formatTitle("Amazing Grace", null))
    }

    @Test fun `by artist moves to the artist field`() {
        assertEquals("HEALER" to "Kari Jobe", formatTitle("Healer by Kari Jobe", null))
    }

    @Test fun `JIL moves to the artist field`() {
        assertEquals("I STAND IN AWE" to "JIL", formatTitle("I STAND IN AWE - JIL", null))
        assertEquals("HOLY SPIRIT COME" to "JIL", formatTitle("HOLY SPIRIT COME (JIL)", null))
    }

    @Test fun `an artist already set wins`() {
        assertEquals("HEALER" to "Hillsong", formatTitle("Healer by Kari Jobe", "Hillsong"))
    }

    @Test fun `capitalised BY inside a title is not split`() {
        assertEquals("STAND BY ME" to null, formatTitle("STAND BY ME", null))
    }

    @Test fun `repeated titles are numbered`() {
        assertEquals("GLORY", uniqueTitle("GLORY", emptyList()))
        assertEquals("GLORY V2", uniqueTitle("GLORY", listOf("GLORY")))
        assertEquals("GLORY V3", uniqueTitle("GLORY", listOf("glory", "GLORY V2")))
    }
}
