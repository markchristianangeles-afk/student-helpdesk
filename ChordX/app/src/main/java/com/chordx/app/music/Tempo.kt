package com.chordx.app.music

import kotlin.math.roundToInt

const val MIN_BPM = 30
const val MAX_BPM = 300
/** Used when a song has no tempo set. */
const val DEFAULT_BPM = 80

fun clampBpm(bpm: Int): Int = bpm.coerceIn(MIN_BPM, MAX_BPM)

/**
 * Tap tempo: tap along with the band and the BPM is the average gap between the last few
 * taps. A pause of more than [resetAfterMillis] starts a fresh count.
 */
class TapTempo(
    private val resetAfterMillis: Long = 2_000,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val taps = ArrayDeque<Long>()

    /** Records a tap; returns the tempo once there are at least two taps, else null. */
    fun tap(): Int? {
        val time = now()
        if (taps.isNotEmpty() && time - taps.last() > resetAfterMillis) taps.clear()
        taps.addLast(time)
        while (taps.size > 5) taps.removeFirst()
        if (taps.size < 2) return null
        val averageGap = (taps.last() - taps.first()).toDouble() / (taps.size - 1)
        if (averageGap <= 0) return null
        return clampBpm((60_000 / averageGap).roundToInt())
    }
}
