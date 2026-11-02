package com.chordx.app.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TempoTest {
    private var clock = 0L
    private fun tapper() = TapTempo(now = { clock })

    @Test
    fun twoTapsHalfASecondApartIs120() {
        val tap = tapper()
        assertNull(tap.tap())
        clock += 500
        assertEquals(120, tap.tap())
    }

    @Test
    fun averagesTheLastFewTaps() {
        val tap = tapper()
        tap.tap()
        listOf(600L, 500L, 500L, 500L).forEach { clock += it; tap.tap() }
        clock += 500
        assertEquals(120, tap.tap()) // the slow first gap has dropped out: last five taps are 500 ms apart
    }

    @Test
    fun aLongPauseStartsAgain() {
        val tap = tapper()
        tap.tap(); clock += 500; tap.tap()
        clock += 5_000
        assertNull(tap.tap())
        clock += 1_000
        assertEquals(60, tap.tap())
    }

    @Test
    fun tempoIsKeptInRange() {
        val tap = tapper()
        tap.tap(); clock += 50
        assertEquals(MAX_BPM, tap.tap())
        assertEquals(MIN_BPM, clampBpm(5))
        assertEquals(MAX_BPM, clampBpm(999))
    }
}
