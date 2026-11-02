package com.chordx.app.ui.chart

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.chordx.app.music.clampBpm
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin

/**
 * Click track. Audio is written beat by beat into a streaming AudioTrack, so timing comes
 * from the audio clock (steady even if the screen is busy), not from timers. Beat 1 of
 * each bar gets a higher, accented click. Tempo and beats per bar can change while playing;
 * they take effect from the next beat.
 */
class Metronome {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null
    @Volatile private var track: AudioTrack? = null

    @Volatile var bpm: Int = 80
        set(value) { field = clampBpm(value) }
    @Volatile var beatsPerBar: Int = 4
        set(value) { field = value.coerceIn(1, 16) }

    private val _beat = MutableStateFlow(-1)
    /** The beat currently sounding (0 = first beat of the bar), or -1 when stopped. */
    val beat: StateFlow<Int> = _beat

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running

    fun start() {
        if (job?.isActive == true) return
        _running.value = true
        job = scope.launch { play() }
    }

    fun stop() {
        job?.cancel()
        job = null
        // Cut the sound now rather than letting the buffered beat finish.
        runCatching { track?.pause(); track?.flush() }
        _running.value = false
        _beat.value = -1
    }

    /** Stops and frees the audio thread; the metronome can't be restarted afterwards. */
    fun release() {
        stop()
        scope.cancel()
    }

    private fun CoroutineScope.play() {
        val newTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(SAMPLE_RATE)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(
                AudioTrack.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT) * 2
            )
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        track = newTrack
        try {
            newTrack.play()
            var beatIndex = 0
            // isActive is this play loop's own coroutine: stop() cancels it, and flushing the
            // track releases a write that's waiting, so the loop ends within one beat.
            while (isActive) {
                val bar = beatsPerBar
                if (beatIndex >= bar) beatIndex = 0
                val samplesPerBeat = (SAMPLE_RATE * 60.0 / bpm).toInt()
                val buffer = ShortArray(samplesPerBeat)
                val click = if (beatIndex == 0) ACCENT else CLICK
                click.copyInto(buffer, 0, 0, min(click.size, buffer.size))
                _beat.value = beatIndex
                // Blocks until there's room, which is what keeps the beat steady.
                if (newTrack.write(buffer, 0, buffer.size) < 0) break
                beatIndex++
            }
        } finally {
            track = null
            runCatching { newTrack.stop() }
            newTrack.release()
            _beat.value = -1
        }
    }

    private companion object {
        const val SAMPLE_RATE = 44_100
        val ACCENT = click(1_600.0)
        val CLICK = click(1_000.0)

        /** A short sine "tick" with a fast decay. */
        fun click(frequency: Double, millis: Int = 35): ShortArray {
            val length = SAMPLE_RATE * millis / 1_000
            return ShortArray(length) { i ->
                val t = i.toDouble() / SAMPLE_RATE
                (sin(2 * PI * frequency * t) * exp(-t * 90) * Short.MAX_VALUE * 0.8).toInt().toShort()
            }
        }
    }
}
