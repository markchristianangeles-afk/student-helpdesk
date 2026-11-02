package com.chordx.app.ui.song

import com.chordx.app.data.ImportedSong
import com.chordx.app.data.Song
import com.chordx.app.music.Key

/** Editor state: every field is raw text exactly as typed. */
data class SongForm(
    val title: String = "",
    val artist: String = "",
    val key: String = "",
    val capo: String = "",
    val tempo: String = "",
    val timeSignature: String = "",
    val content: String = "",
) {
    companion object {
        fun from(song: Song) = SongForm(
            title = song.title,
            artist = song.artist.orEmpty(),
            key = song.key.orEmpty(),
            capo = if (song.capo == 0) "" else song.capo.toString(),
            tempo = song.tempo?.toString().orEmpty(),
            timeSignature = song.timeSignature.orEmpty(),
            content = song.content,
        )

        fun from(imported: ImportedSong) = SongForm(
            title = imported.title,
            artist = imported.artist.orEmpty(),
            key = imported.key.orEmpty(),
            capo = imported.capo.orEmpty(),
            tempo = imported.tempo.orEmpty(),
            timeSignature = imported.timeSignature.orEmpty(),
            content = imported.content,
        )
    }
}

enum class SongField { TITLE, KEY, CAPO, TEMPO, TIME_SIGNATURE }

sealed interface SongFormResult {
    data class Valid(
        val title: String,
        val artist: String?,
        val key: String?,
        val capo: Int,
        val tempo: Int?,
        val timeSignature: String?,
        val content: String,
    ) : SongFormResult

    data class Invalid(val errors: Map<SongField, String>) : SongFormResult
}

private val TIME_SIGNATURE_PATTERN = Regex("^\\d{1,2}/\\d{1,2}$")

const val MAX_CAPO = 11
const val MIN_TEMPO = 20
const val MAX_TEMPO = 400

/** Trims and checks the form; blank optional fields become null (or 0 for capo). */
fun validate(form: SongForm): SongFormResult {
    val errors = mutableMapOf<SongField, String>()

    val title = form.title.trim().uppercase()
    if (title.isEmpty()) errors[SongField.TITLE] = "Title is required"

    val key = form.key.trim().ifEmpty { null }
    if (key != null && Key.parse(key) == null) errors[SongField.KEY] = "Use a key like G, Bb or F#m"

    val capoText = form.capo.trim()
    val capo = if (capoText.isEmpty()) 0 else capoText.toIntOrNull()
    if (capo == null || capo !in 0..MAX_CAPO) errors[SongField.CAPO] = "Capo must be 0–$MAX_CAPO"

    val tempoText = form.tempo.trim()
    val tempo = if (tempoText.isEmpty()) null else tempoText.toIntOrNull()
    if (tempoText.isNotEmpty() && (tempo == null || tempo !in MIN_TEMPO..MAX_TEMPO)) {
        errors[SongField.TEMPO] = "Tempo must be $MIN_TEMPO–$MAX_TEMPO BPM"
    }

    val timeSignature = form.timeSignature.trim().ifEmpty { null }
    if (timeSignature != null && !TIME_SIGNATURE_PATTERN.matches(timeSignature)) {
        errors[SongField.TIME_SIGNATURE] = "Use a time signature like 4/4 or 6/8"
    }

    if (errors.isNotEmpty()) return SongFormResult.Invalid(errors)
    return SongFormResult.Valid(
        title = title,
        artist = form.artist.trim().ifEmpty { null },
        key = key,
        capo = capo!!,
        tempo = tempo,
        timeSignature = timeSignature,
        content = form.content,
    )
}
