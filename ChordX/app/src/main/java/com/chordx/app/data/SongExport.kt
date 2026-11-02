package com.chordx.app.data

/**
 * Builds a single ChordPro text file holding [songs], separated by {new_song}.
 * It is the same format [prepareImport] reads, so an exported file can be imported
 * straight back (here or on another device).
 *
 * Title, artist, key, capo, tempo and time signature live in database columns rather
 * than in the chart text, so they are written as directives ahead of each chart unless
 * the chart already carries its own.
 *
 * Not included: per-song transpose and sharp/flat choice, setlists and settings.
 */
fun buildSongExport(songs: List<Song>): String =
    songs.joinToString("\n{new_song}\n") { song ->
        val existing = parseImportedSong(song.content, null, fallbackTitle = "")
        buildList {
            if (existing.title.isEmpty()) add("{title: ${oneLine(song.title)}}")
            if (existing.artist == null) song.artist?.let { add("{artist: ${oneLine(it)}}") }
            if (existing.key == null) song.key?.let { add("{key: $it}") }
            if (existing.capo == null && song.capo > 0) add("{capo: ${song.capo}}")
            if (existing.tempo == null) song.tempo?.let { add("{tempo: $it}") }
            if (existing.timeSignature == null) song.timeSignature?.let { add("{time: $it}") }
        }.plus(song.content.trimEnd()).joinToString("\n")
    }.plus("\n")

/** Directive values must stay on one line and can't contain a closing brace. */
private fun oneLine(text: String): String = text.replace('\n', ' ').replace('}', ')').trim()
