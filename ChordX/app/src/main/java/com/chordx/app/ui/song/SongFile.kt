package com.chordx.app.ui.song

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import com.chordx.app.data.ImportedSong
import com.chordx.app.data.MAX_IMPORT_BYTES
import com.chordx.app.data.Song
import com.chordx.app.data.SongDao
import com.chordx.app.data.decodeText
import com.chordx.app.data.formatTitle
import com.chordx.app.data.prepareImport
import com.chordx.app.data.uniqueTitle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream

sealed interface SongFileResult {
    data class Success(val songs: List<ImportedSong>) : SongFileResult
    data class Failure(val message: String) : SongFileResult
}

/** Reads a picked file off the main thread and turns it into one or more [ImportedSong]s. */
suspend fun readSongFile(resolver: ContentResolver, uri: Uri): SongFileResult = withContext(Dispatchers.IO) {
    try {
        val bytes = resolver.openInputStream(uri)?.use { it.readAtMost(MAX_IMPORT_BYTES + 1) }
            ?: return@withContext SongFileResult.Failure("Couldn't open that file.")
        if (bytes.size > MAX_IMPORT_BYTES) return@withContext SongFileResult.Failure("That file is too large to be a song chart.")

        val text = decodeText(bytes)
        when {
            text.isBlank() -> SongFileResult.Failure("That file is empty.")
            '\u0000' in text -> SongFileResult.Failure("That doesn't look like a text file.")
            else -> SongFileResult.Success(prepareImport(text, displayName(resolver, uri)))
        }
    } catch (_: IOException) {
        SongFileResult.Failure("Couldn't read that file.")
    } catch (_: SecurityException) {
        SongFileResult.Failure("ChordX isn't allowed to read that file.")
    }
}

private fun displayName(resolver: ContentResolver, uri: Uri): String? =
    runCatching {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }.getOrNull() ?: uri.lastPathSegment

private fun InputStream.readAtMost(limit: Int): ByteArray {
    val out = ByteArrayOutputStream()
    val buffer = ByteArray(8 * 1024)
    while (out.size() < limit) {
        val read = read(buffer, 0, minOf(buffer.size, limit - out.size()))
        if (read < 0) break
        out.write(buffer, 0, read)
    }
    return out.toByteArray()
}

data class ImportSummary(val imported: Int, val duplicates: Int, val failedFiles: Int) {
    fun message(): String = buildList {
        add(if (imported == 1) "Imported 1 song." else "Imported $imported songs.")
        if (duplicates > 0) add(if (duplicates == 1) "Skipped 1 already in your library." else "Skipped $duplicates already in your library.")
        if (failedFiles > 0) add(if (failedFiles == 1) "1 file couldn't be read." else "$failedFiles files couldn't be read.")
    }.joinToString(" ")
}

/**
 * Saves imported songs without review. Exact duplicates are skipped, and details that
 * fail validation (e.g. {key: H}) are dropped rather than blocking the import — the
 * chart text is always kept.
 */
suspend fun importSongs(songs: SongDao, imported: List<ImportedSong>, failedFiles: Int = 0): ImportSummary {
    var added = 0
    var duplicates = 0
    for (song in imported) {
        val valid = toValidSong(song)
        // A chart already saved under this title (or its V2, V3 ...) is a duplicate; a different chart with the same title is numbered.
        val sameTitle = songs.titlesLike(valid.title)
        if (sameTitle.any { songs.countDuplicates(it, valid.content) > 0 }) {
            duplicates++
        } else {
            songs.insert(valid.copy(title = uniqueTitle(valid.title, sameTitle)))
            added++
        }
    }
    return ImportSummary(added, duplicates, failedFiles)
}

internal fun toValidSong(imported: ImportedSong): Song {
    val (title, artist) = formatTitle(imported.title, imported.artist)
    var form = SongForm.from(imported.copy(title = title, artist = artist))
    (validate(form) as? SongFormResult.Invalid)?.errors?.keys?.forEach { field ->
        form = when (field) {
            SongField.TITLE -> form.copy(title = "Untitled")
            SongField.KEY -> form.copy(key = "")
            SongField.CAPO -> form.copy(capo = "")
            SongField.TEMPO -> form.copy(tempo = "")
            SongField.TIME_SIGNATURE -> form.copy(timeSignature = "")
        }
    }
    val valid = validate(form) as SongFormResult.Valid
    return Song(
        title = valid.title, artist = valid.artist, key = valid.key, capo = valid.capo,
        tempo = valid.tempo, timeSignature = valid.timeSignature, content = valid.content,
    )
}
