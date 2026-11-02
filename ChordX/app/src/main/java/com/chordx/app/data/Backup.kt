package com.chordx.app.data

import androidx.room.withTransaction
import com.chordx.app.music.Accidentals
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * Full backup: songs (with key, capo, tempo, saved transpose and sharp/flat choice),
 * setlists with their per-setlist keys and between-song notes, sermon notes, daily devotion
 * (SOAP) entries and settings,
 * in one JSON file. Everything is linked by uuid, so a restore rebuilds the same setlists.
 */
const val BACKUP_FORMAT = "chordx-backup"
const val BACKUP_VERSION = 1

/** Anything bigger than this is not a ChordX backup. */
const val MAX_BACKUP_BYTES = 100_000_000

data class BackupEntry(val setlistUuid: String, val songUuid: String, val position: Int, val keyOverride: String?, val notes: String?)

data class BackupSettings(val theme: ThemeMode, val accidentals: Accidentals, val showLyrics: Boolean, val fontScale: Float)

data class BackupData(
    val songs: List<Song>,
    val setlists: List<Setlist>,
    val entries: List<BackupEntry>,
    val notes: List<SermonNote>,
    val settings: BackupSettings?,
    /** Daily devotion (SOAP) entries; older backups simply have none. */
    val devotions: List<Devotion> = emptyList(),
)

sealed interface BackupParseResult {
    data class Ok(val data: BackupData) : BackupParseResult
    data class Bad(val message: String) : BackupParseResult
}

data class RestoreSummary(val songs: Int, val setlists: Int, val notes: Int, val skipped: Int, val devotions: Int = 0) {
    fun message(replaced: Boolean): String {
        val added = if (devotions > 0) {
            "${count(songs, "song")}, ${count(setlists, "setlist")}, ${count(notes, "note")} and ${count(devotions, "devotion entry", "devotion entries")}"
        } else {
            "${count(songs, "song")}, ${count(setlists, "setlist")} and ${count(notes, "note")}"
        }
        return when {
            replaced -> "Restored $added."
            skipped > 0 -> "Added $added. Skipped $skipped already on this device."
            else -> "Added $added."
        }
    }

    private fun count(n: Int, noun: String, plural: String = "${noun}s") = if (n == 1) "1 $noun" else "$n $plural"
}

suspend fun buildBackup(db: ChordXDatabase, settings: SettingsStore): String {
    val songs = db.songDao().getAll()
    val setlists = db.setlistDao().getAllSetlists()
    val entries = db.setlistDao().getAllEntries()
    val notes = db.noteDao().getAll()
    val devotions = db.devotionDao().getAll()
    val songUuid = songs.associate { it.id to it.uuid }
    val setlistUuid = setlists.associate { it.id to it.uuid }

    return JSONObject().apply {
        put("format", BACKUP_FORMAT)
        put("version", BACKUP_VERSION)
        put("exportedAt", System.currentTimeMillis())
        put("songs", JSONArray().apply {
            songs.forEach { s ->
                put(JSONObject().apply {
                    put("uuid", s.uuid); put("title", s.title); put("artist", s.artist ?: JSONObject.NULL)
                    put("key", s.key ?: JSONObject.NULL); put("capo", s.capo); put("tempo", s.tempo ?: JSONObject.NULL)
                    put("timeSignature", s.timeSignature ?: JSONObject.NULL); put("content", s.content)
                    put("transpose", s.transpose); put("accidentals", s.accidentals?.name ?: JSONObject.NULL)
                    put("createdAt", s.createdAt); put("updatedAt", s.updatedAt)
                })
            }
        })
        put("setlists", JSONArray().apply {
            setlists.forEach { l ->
                put(JSONObject().apply {
                    put("uuid", l.uuid); put("name", l.name); put("eventDate", l.eventDate ?: JSONObject.NULL)
                    put("notes", l.notes ?: JSONObject.NULL); put("createdAt", l.createdAt); put("updatedAt", l.updatedAt)
                })
            }
        })
        put("entries", JSONArray().apply {
            entries.forEach { e ->
                val l = setlistUuid[e.setlistId] ?: return@forEach
                val s = songUuid[e.songId] ?: return@forEach
                put(JSONObject().apply {
                    put("setlist", l); put("song", s); put("position", e.position)
                    put("keyOverride", e.keyOverride ?: JSONObject.NULL); put("notes", e.notes ?: JSONObject.NULL)
                })
            }
        })
        put("notes", JSONArray().apply {
            notes.forEach { n ->
                put(JSONObject().apply {
                    put("uuid", n.uuid); put("title", n.title); put("body", n.body)
                    put("createdAt", n.createdAt); put("updatedAt", n.updatedAt)
                })
            }
        })
        put("devotions", JSONArray().apply {
            devotions.forEach { d ->
                put(JSONObject().apply {
                    put("date", d.date); put("scripture", d.scripture); put("observation", d.observation)
                    put("application", d.application); put("prayer", d.prayer); put("updatedAt", d.updatedAt)
                })
            }
        })
        put("settings", JSONObject().apply {
            put("theme", settings.themeMode.first().name)
            put("accidentals", settings.accidentals.first().name)
            put("showLyrics", settings.showLyrics.first())
            put("fontScale", settings.performanceFontScale.first().toDouble())
        })
    }.toString()
}

private fun JSONObject.str(key: String): String? = if (isNull(key)) null else getString(key)
private fun JSONObject.int(key: String): Int? = if (isNull(key)) null else getInt(key)
private fun JSONObject.long(key: String): Long? = if (isNull(key)) null else getLong(key)
private inline fun <T> JSONArray.mapObjects(block: (JSONObject) -> T): List<T> = (0 until length()).map { block(getJSONObject(it)) }

/** Reads and checks a backup completely, without touching the database. */
fun parseBackup(text: String): BackupParseResult = try {
    val root = JSONObject(text)
    if (root.optString("format") != BACKUP_FORMAT) return BackupParseResult.Bad("That isn't a ChordX backup file.")
    if (root.optInt("version", 0) > BACKUP_VERSION) return BackupParseResult.Bad("This backup was made by a newer version of ChordX. Update the app first.")

    val songs = root.getJSONArray("songs").mapObjects { o ->
        Song(
            uuid = o.getString("uuid"), title = o.getString("title"), artist = o.str("artist"), key = o.str("key"),
            capo = o.int("capo") ?: 0, tempo = o.int("tempo"), timeSignature = o.str("timeSignature"),
            content = o.getString("content"), transpose = o.int("transpose") ?: 0,
            accidentals = o.str("accidentals")?.let { runCatching { Accidentals.valueOf(it) }.getOrNull() },
            createdAt = o.long("createdAt") ?: System.currentTimeMillis(), updatedAt = o.long("updatedAt") ?: System.currentTimeMillis(),
        )
    }
    val setlists = root.getJSONArray("setlists").mapObjects { o ->
        Setlist(
            uuid = o.getString("uuid"), name = o.getString("name"), eventDate = o.long("eventDate"), notes = o.str("notes"),
            createdAt = o.long("createdAt") ?: System.currentTimeMillis(), updatedAt = o.long("updatedAt") ?: System.currentTimeMillis(),
        )
    }
    val entries = root.getJSONArray("entries").mapObjects { o ->
        BackupEntry(o.getString("setlist"), o.getString("song"), o.getInt("position"), o.str("keyOverride"), o.str("notes"))
    }
    val notes = root.optJSONArray("notes")?.mapObjects { o ->
        SermonNote(
            uuid = o.getString("uuid"), title = o.getString("title"), body = o.getString("body"),
            createdAt = o.long("createdAt") ?: System.currentTimeMillis(), updatedAt = o.long("updatedAt") ?: System.currentTimeMillis(),
        )
    }.orEmpty()
    val devotions = root.optJSONArray("devotions")?.mapObjects { o ->
        Devotion(
            date = o.getString("date").also { java.time.LocalDate.parse(it) },
            scripture = o.optString("scripture"), observation = o.optString("observation"),
            application = o.optString("application"), prayer = o.optString("prayer"),
            updatedAt = o.long("updatedAt") ?: System.currentTimeMillis(),
        )
    }.orEmpty()
    val settings = root.optJSONObject("settings")?.let { o ->
        BackupSettings(
            theme = runCatching { ThemeMode.valueOf(o.getString("theme")) }.getOrDefault(ThemeMode.SYSTEM),
            accidentals = runCatching { Accidentals.valueOf(o.getString("accidentals")) }.getOrDefault(Accidentals.AUTO),
            showLyrics = o.optBoolean("showLyrics", true),
            fontScale = o.optDouble("fontScale", 1.0).toFloat().coerceIn(MIN_FONT_SCALE, MAX_FONT_SCALE),
        )
    }
    BackupParseResult.Ok(BackupData(songs, setlists, entries, notes, settings, devotions))
} catch (_: java.time.format.DateTimeParseException) {
    BackupParseResult.Bad("That file is damaged or isn't a ChordX backup.")
} catch (_: JSONException) {
    BackupParseResult.Bad("That file is damaged or isn't a ChordX backup.")
}

/**
 * Puts [data] into the database.
 *
 * [replace] = false adds only what this device doesn't already have (matched by uuid) and leaves
 * settings alone. [replace] = true first deletes everything on this device, then restores the
 * backup exactly, settings included. Either way it is all-or-nothing.
 */
suspend fun restoreBackup(db: ChordXDatabase, settings: SettingsStore, data: BackupData, replace: Boolean): RestoreSummary {
    val summary = db.withTransaction {
        val songDao = db.songDao()
        val setlistDao = db.setlistDao()
        val noteDao = db.noteDao()
        val devotionDao = db.devotionDao()

        if (replace) {
            setlistDao.deleteAllSetlists()
            songDao.deleteAll()
            noteDao.deleteAll()
            devotionDao.deleteAll()
        }
        val haveSongs = songDao.getAll().map { it.uuid }.toSet()
        val haveSetlists = setlistDao.getAllSetlists().map { it.uuid }.toSet()
        val haveNotes = noteDao.getAll().map { it.uuid }.toSet()

        val songIds = mutableMapOf<String, Long>()
        var songsAdded = 0
        data.songs.forEach { s ->
            if (s.uuid in haveSongs) return@forEach
            songIds[s.uuid] = songDao.insert(s.copy(id = 0))
            songsAdded++
        }
        // Entries of setlists that already exist are left alone; their songs may still be matched by uuid.
        songDao.getAll().forEach { songIds.putIfAbsent(it.uuid, it.id) }

        val setlistIds = mutableMapOf<String, Long>()
        var setlistsAdded = 0
        data.setlists.forEach { l ->
            if (l.uuid in haveSetlists) return@forEach
            setlistIds[l.uuid] = setlistDao.insert(l.copy(id = 0))
            setlistsAdded++
        }
        setlistDao.insertEntries(
            data.entries.mapNotNull { e ->
                val setlistId = setlistIds[e.setlistUuid] ?: return@mapNotNull null
                val songId = songIds[e.songUuid] ?: return@mapNotNull null
                SetlistEntry(setlistId = setlistId, songId = songId, position = e.position, keyOverride = e.keyOverride, notes = e.notes)
            }
        )

        var notesAdded = 0
        data.notes.forEach { n ->
            if (n.uuid in haveNotes) return@forEach
            noteDao.insert(n.copy(id = 0))
            notesAdded++
        }
        // Entries are matched by date; a day already journaled on this device is kept as it is.
        val haveDays = devotionDao.getAll().map { it.date }.toSet()
        var devotionsAdded = 0
        data.devotions.forEach { d ->
            if (d.date in haveDays) return@forEach
            devotionDao.upsert(d.copy(id = 0))
            devotionsAdded++
        }
        RestoreSummary(
            songs = songsAdded, setlists = setlistsAdded, notes = notesAdded, devotions = devotionsAdded,
            skipped = (data.songs.size - songsAdded) + (data.setlists.size - setlistsAdded) + (data.notes.size - notesAdded) +
                (data.devotions.size - devotionsAdded),
        )
    }
    if (replace) data.settings?.let {
        settings.setThemeMode(it.theme)
        settings.setAccidentals(it.accidentals)
        settings.setShowLyrics(it.showLyrics)
        settings.setPerformanceFontScale(it.fontScale)
    }
    return summary
}
