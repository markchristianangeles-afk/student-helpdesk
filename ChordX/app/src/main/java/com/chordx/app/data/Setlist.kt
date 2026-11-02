package com.chordx.app.data

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import java.util.UUID

@Entity(
    tableName = "setlists",
    indices = [Index(value = ["uuid"], unique = true)],
)
data class Setlist(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uuid: String = UUID.randomUUID().toString(),
    val name: String,
    /** Service/event date as epoch day (LocalDate.toEpochDay()). Null when undated. */
    @ColumnInfo(name = "event_date") val eventDate: Long? = null,
    val notes: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at") val updatedAt: Long = createdAt,
)

/**
 * One slot in a setlist. Entries have their own id so the same song can appear
 * more than once (e.g. a reprise). [position] is 0-based and kept contiguous by
 * [SetlistDao]. Deleting a setlist or a song removes its entries.
 */
@Entity(
    tableName = "setlist_entries",
    foreignKeys = [
        ForeignKey(
            entity = Setlist::class,
            parentColumns = ["id"],
            childColumns = ["setlist_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = Song::class,
            parentColumns = ["id"],
            childColumns = ["song_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["setlist_id", "position"]),
        Index(value = ["song_id"]),
    ],
)
data class SetlistEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "setlist_id") val setlistId: Long,
    @ColumnInfo(name = "song_id") val songId: Long,
    val position: Int,
    /** Key to play this song in for this setlist only; null means the song's own key. */
    @ColumnInfo(name = "key_override") val keyOverride: String? = null,
    /** A note shown between this song and the next, e.g. "Modulate up a tone into the last chorus". */
    val notes: String? = null,
)

data class SetlistSummary(
    @Embedded val setlist: Setlist,
    @ColumnInfo(name = "song_count") val songCount: Int,
)

/** A setlist entry together with its song, as returned in setlist order. */
data class SetlistItem(
    @Embedded val entry: SetlistEntry,
    @Relation(parentColumn = "song_id", entityColumn = "id")
    val song: Song,
)
