package com.chordx.app.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.chordx.app.music.Accidentals
import java.util.UUID

/**
 * A song stored as ChordPro source. [content] is the single source of truth;
 * title/artist/key are denormalised copies used for listing and searching.
 */
@Entity(
    tableName = "songs",
    indices = [
        Index(value = ["uuid"], unique = true),
        Index(value = ["title"]),
    ],
)
data class Song(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Stable identifier that survives export/import and future cloud sync. */
    val uuid: String = UUID.randomUUID().toString(),
    val title: String,
    val artist: String? = null,
    /** Original key as written, e.g. "G" or "Bbm". Null when unknown. */
    @ColumnInfo(name = "song_key") val key: String? = null,
    val capo: Int = 0,
    val tempo: Int? = null,
    @ColumnInfo(name = "time_signature") val timeSignature: String? = null,
    val content: String = "",
    /** Semitones to shift the chart when viewing, in -5..+6. The chart text itself is never rewritten. */
    @ColumnInfo(defaultValue = "0") val transpose: Int = 0,
    /** Sharp/flat spelling for this song; null follows the app-wide setting. */
    val accidentals: Accidentals? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at") val updatedAt: Long = createdAt,
)
