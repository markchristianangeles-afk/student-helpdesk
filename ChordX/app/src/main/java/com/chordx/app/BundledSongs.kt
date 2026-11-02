package com.chordx.app

import android.content.Context
import android.content.res.AssetManager
import com.chordx.app.data.ImportedSong
import com.chordx.app.data.SongDao
import com.chordx.app.data.decodeText
import com.chordx.app.data.prepareImport
import com.chordx.app.ui.song.ImportSummary
import com.chordx.app.ui.song.importSongs
import org.json.JSONArray
import java.io.IOException

/**
 * Song charts shipped inside the APK (app/src/main/assets/songs, .txt/.cho files, any
 * subfolder). They go through the same import as Import files: plain chord sheets are
 * converted, bad details are dropped, and songs already in the library are skipped.
 */
object BundledSongs {
    const val ASSET_DIR = "songs"

    /**
     * Bump this when the bundled set changes, so existing installs import the new
     * charts. Until then the import runs once, so a song the user deletes stays deleted.
     */
    const val VERSION = 1

    private const val PREFS = "bundled_songs"
    private const val KEY_VERSION = "imported_version"
    private val EXTENSIONS = setOf("txt", "cho", "chordpro", "pro")

    /** Asset paths of every bundled chart, e.g. "songs/Kasal/SONG.txt". */
    fun list(assets: AssetManager, dir: String = ASSET_DIR): List<String> =
        (assets.list(dir) ?: emptyArray()).sorted().flatMap { name ->
            val path = "$dir/$name"
            when {
                name.substringAfterLast('.', "").lowercase() in EXTENSIONS -> listOf(path)
                // Folders list their children; files with other extensions list nothing.
                else -> list(assets, path)
            }
        }

    /** The library exactly as curated on the emulator (titles, artists, charts); preferred over the chart files when present. */
    const val LIBRARY_FILE = "library/songs.json"

    private fun readLibrary(assets: AssetManager): List<ImportedSong>? {
        val json = try {
            assets.open(LIBRARY_FILE).use { String(it.readBytes(), Charsets.UTF_8) }
        } catch (_: IOException) {
            return null
        }
        val array = JSONArray(json)
        return List(array.length()) { i ->
            val o = array.getJSONObject(i)
            fun text(name: String) = o.optString(name).ifEmpty { null }
            ImportedSong(
                title = o.getString("title"), artist = text("artist"), key = text("key"), capo = text("capo"),
                tempo = text("tempo"), timeSignature = text("timeSignature"), content = o.getString("content"),
            )
        }
    }

    /** Reads and prepares every bundled chart; unreadable files are counted, not fatal. */
    fun read(assets: AssetManager): Pair<List<ImportedSong>, Int> {
        readLibrary(assets)?.let { return it to 0 }
        val songs = mutableListOf<ImportedSong>()
        var failed = 0
        for (path in list(assets)) {
            try {
                val text = decodeText(assets.open(path).use { it.readBytes() })
                if (text.isBlank()) failed++ else songs += prepareImport(text, path.substringAfterLast('/'))
            } catch (_: IOException) {
                failed++
            }
        }
        return songs to failed
    }

    /** Imports the bundled charts if this install hasn't yet imported [VERSION]. Returns null when there was nothing to do. */
    suspend fun importIfNeeded(context: Context, songs: SongDao): ImportSummary? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getInt(KEY_VERSION, 0) >= VERSION) return null
        val (charts, failed) = read(context.assets)
        val summary = importSongs(songs, charts, failed)
        // Only marked done after the import finishes, so an interrupted first launch retries.
        prefs.edit().putInt(KEY_VERSION, VERSION).apply()
        return summary
    }
}
