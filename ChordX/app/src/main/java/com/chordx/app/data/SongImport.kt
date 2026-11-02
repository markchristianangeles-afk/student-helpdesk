package com.chordx.app.data

import com.chordx.app.chordpro.convertChordsOverLyrics
import com.chordx.app.chordpro.looksLikeChordsOverLyrics
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

/** Raw values read from an imported file. Nothing is validated yet; the editor does that. */
data class ImportedSong(
    val title: String,
    val artist: String? = null,
    val key: String? = null,
    val capo: String? = null,
    val tempo: String? = null,
    val timeSignature: String? = null,
    val content: String,
    /** True when the file was chords-over-lyrics text and has been converted to ChordPro. */
    val convertedFromPlainText: Boolean = false,
)

/** Anything bigger than this is not a song chart. */
const val MAX_IMPORT_BYTES = 1_000_000

/**
 * Decodes a text file of unknown encoding: honours UTF-8/UTF-16 byte-order marks,
 * otherwise tries strict UTF-8 and falls back to Windows-1252 (common for charts
 * saved on Windows). Line endings are normalised to \n.
 */
fun decodeText(bytes: ByteArray): String {
    fun startsWith(vararg prefix: Int) = bytes.size >= prefix.size && prefix.indices.all { bytes[it] == prefix[it].toByte() }

    val text = when {
        startsWith(0xEF, 0xBB, 0xBF) -> String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
        startsWith(0xFF, 0xFE) -> String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE)
        startsWith(0xFE, 0xFF) -> String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
        else -> strictUtf8(bytes) ?: String(bytes, Charset.forName("windows-1252"))
    }
    return text.replace("\r\n", "\n").replace('\r', '\n')
}

private fun strictUtf8(bytes: ByteArray): String? = try {
    Charsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
        .decode(ByteBuffer.wrap(bytes))
        .toString()
} catch (_: CharacterCodingException) {
    null
}

// Braces are always escaped: Android's ICU regex engine rejects a bare "}" that desktop Java accepts.
private val DIRECTIVE = Regex("""^\s*\{\s*([A-Za-z_]+)\s*:\s*(.*?)\s*\}\s*$""")

/** ChordPro directive names (including short forms) mapped to the field they fill. */
private val METADATA_DIRECTIVES = mapOf(
    "title" to "title", "t" to "title",
    "artist" to "artist", "subtitle" to "artist", "st" to "artist",
    "key" to "key",
    "capo" to "capo",
    "tempo" to "tempo",
    "time" to "time",
)

private val NEW_SONG = Regex("""^\s*\{\s*(?:new_song|ns)\s*\}\s*$""", RegexOption.IGNORE_CASE)

/**
 * Turns a file's text into songs: splits on {new_song}, converts chords-over-lyrics
 * text to ChordPro, then reads each song's details. Untitled songs are named after
 * the file, numbered when the file holds several.
 */
fun prepareImport(text: String, fileName: String?): List<ImportedSong> {
    val parts = mutableListOf(StringBuilder())
    for (line in text.lines()) {
        if (NEW_SONG.matches(line)) parts += StringBuilder() else parts.last().append(line).append('\n')
    }
    val songs = parts.map { it.toString().trim('\n') }.filter { it.isNotBlank() }
    val baseTitle = titleFromFileName(fileName)
    return songs.mapIndexed { index, rawPart ->
        // Many charts open with the title on its own line, repeating the file name. Take
        // it as the title (it keeps punctuation the file name loses, e.g. "We're") and
        // drop it from the chart so it isn't shown as a lyric.
        val titleLine = leadingTitleLine(rawPart, baseTitle)
        val part = if (titleLine == null) rawPart else rawPart.lines().dropWhile { it.isBlank() }.drop(1).joinToString("\n").trim('\n')
        val converted = looksLikeChordsOverLyrics(part)
        val body = if (converted) convertChordsOverLyrics(part) else part
        val fallback = when {
            titleLine != null -> titleLine
            songs.size > 1 && baseTitle.isNotEmpty() -> "$baseTitle (${index + 1})"
            else -> baseTitle
        }
        parseImportedSong(body, fileName, fallback).copy(convertedFromPlainText = converted)
    }
}

/** Letters and digits only, lower-cased: "We're Together Again" and "WE_RE TOGETHER AGAIN" both become "weretogetheragain". */
private fun titleKey(text: String): String = text.lowercase().filter { it.isLetterOrDigit() }

/** The first non-blank line when it's just the song's title (matching the file name), else null. */
private fun leadingTitleLine(text: String, fileTitle: String): String? {
    if (fileTitle.isEmpty()) return null
    val first = text.lineSequence().firstOrNull { it.isNotBlank() }?.trim() ?: return null
    return first.takeIf { titleKey(it).isNotEmpty() && titleKey(it) == titleKey(fileTitle) }
}

/**
 * Reads song details from ChordPro metadata directives; the first occurrence of each
 * wins. Without a {title}, [fallbackTitle] (by default the file name) is used.
 * [text] becomes the chart unchanged.
 */
fun parseImportedSong(text: String, fileName: String?, fallbackTitle: String = titleFromFileName(fileName)): ImportedSong {
    val found = mutableMapOf<String, String>()
    for (line in text.lineSequence()) {
        val match = DIRECTIVE.matchEntire(line) ?: continue
        val field = METADATA_DIRECTIVES[match.groupValues[1].lowercase()] ?: continue
        val value = match.groupValues[2]
        if (value.isNotEmpty()) found.putIfAbsent(field, value)
    }
    return ImportedSong(
        title = found["title"] ?: fallbackTitle,
        artist = found["artist"],
        key = found["key"],
        capo = found["capo"],
        tempo = found["tempo"],
        timeSignature = found["time"],
        content = text.trimEnd(),
    )
}

internal fun titleFromFileName(fileName: String?): String =
    fileName?.substringAfterLast('/')?.substringAfterLast('\\')?.substringBeforeLast('.')?.replace('_', ' ')?.trim().orEmpty()
