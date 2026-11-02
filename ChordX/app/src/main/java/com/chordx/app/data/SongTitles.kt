package com.chordx.app.data

import android.content.Context

/** Only a lower-case "by", so titles like "STAND BY ME" are never split. */
private val BY_ARTIST = Regex("""^(.*\S)\s+by\s+(\S.*)$""")

/** "TITLE - JIL" or "TITLE (JIL)": JIL is the church the chart comes from. */
private val JIL_ARTIST = Regex("""^(.*?\S)\s*(?:-\s*JIL|\(\s*JIL\s*\))$""", RegexOption.IGNORE_CASE)

/**
 * Titles are stored in capitals. A trailing "by Artist" (as in the file name
 * "Healer by Kari Jobe") is moved to the artist; an artist that is already set wins.
 */
fun formatTitle(title: String, artist: String?): Pair<String, String?> {
    val jil = JIL_ARTIST.matchEntire(title.trim())
    val match = if (jil == null) BY_ARTIST.matchEntire(title.trim()) else null
    val name = jil?.groupValues?.get(1) ?: match?.groupValues?.get(1) ?: title.trim()
    val found = if (jil != null) "JIL" else match?.groupValues?.get(2)?.trim()
    return name.uppercase() to (artist?.trim().takeUnless { it.isNullOrEmpty() } ?: found)
}

/** [title] if no other song has it, otherwise "TITLE V2", "TITLE V3", ... (the first free one). */
fun uniqueTitle(title: String, taken: Collection<String>): String {
    if (taken.none { it.equals(title, ignoreCase = true) }) return title
    var n = 2
    while (taken.any { it.equals("$title V$n", ignoreCase = true) }) n++
    return "$title V$n"
}

/** [uniqueTitle] against the library, ignoring the song with [excludeId] (the one being edited). */
suspend fun SongDao.uniqueTitle(title: String, excludeId: Long = 0): String =
    uniqueTitle(title, titlesLike(title, excludeId))

/**
 * Brings songs saved before titles were capitalised into the current format: capitals,
 * "by Artist" or "- JIL" moved to the artist, and repeated titles numbered V2, V3 in the order added.
 * Charts are untouched. Runs once per install.
 */
suspend fun normaliseExistingTitles(context: Context, songs: SongDao) {
    val prefs = context.getSharedPreferences("song_titles", Context.MODE_PRIVATE)
    if (prefs.getInt("format_version", 0) >= 2) return
    val taken = mutableListOf<String>()
    val now = System.currentTimeMillis()
    for (song in songs.getAll()) {
        val (name, artist) = formatTitle(song.title, song.artist)
        val title = uniqueTitle(name, taken)
        taken += title
        if (title != song.title || artist != song.artist) songs.update(song.copy(title = title, artist = artist, updatedAt = now))
    }
    prefs.edit().putInt("format_version", 2).apply()
}
