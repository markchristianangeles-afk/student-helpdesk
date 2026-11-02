package com.chordx.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
abstract class SetlistDao {
    @Insert
    abstract suspend fun insert(setlist: Setlist): Long

    @Update
    abstract suspend fun update(setlist: Setlist)

    @Delete
    abstract suspend fun delete(setlist: Setlist)

    @Query("SELECT * FROM setlists WHERE id = :id")
    abstract suspend fun getById(id: Long): Setlist?

    /** Dated setlists first, most recent first; undated ones last by name. */
    @Query("SELECT * FROM setlists ORDER BY event_date IS NULL, event_date DESC, name COLLATE NOCASE")
    abstract fun observeAll(): Flow<List<Setlist>>

    /** Same order as [observeAll], with how many songs each setlist holds. */
    @Query(
        """
        SELECT s.*, (SELECT COUNT(*) FROM setlist_entries e WHERE e.setlist_id = s.id) AS song_count
        FROM setlists s
        ORDER BY s.event_date IS NULL, s.event_date DESC, s.name COLLATE NOCASE
        """
    )
    abstract fun observeSummaries(): Flow<List<SetlistSummary>>

    @Query("SELECT * FROM setlists WHERE id = :id")
    abstract fun observeById(id: Long): Flow<Setlist?>

    @Query("UPDATE setlist_entries SET key_override = :key WHERE id = :entryId")
    abstract suspend fun setKeyOverride(entryId: Long, key: String?)

    /** Sets the note shown between this song and the next one; null or blank clears it. */
    @Query("UPDATE setlist_entries SET notes = :note WHERE id = :entryId")
    abstract suspend fun setNote(entryId: Long, note: String?)

    @Transaction
    @Query("SELECT * FROM setlist_entries WHERE setlist_id = :setlistId ORDER BY position")
    abstract fun observeItems(setlistId: Long): Flow<List<SetlistItem>>

    @Transaction
    @Query("SELECT * FROM setlist_entries WHERE setlist_id = :setlistId ORDER BY position")
    abstract suspend fun getItems(setlistId: Long): List<SetlistItem>

    @Query("SELECT * FROM setlist_entries WHERE setlist_id = :setlistId ORDER BY position")
    protected abstract suspend fun getEntries(setlistId: Long): List<SetlistEntry>

    @Insert
    protected abstract suspend fun insertEntry(entry: SetlistEntry): Long

    @Update
    abstract suspend fun updateEntry(entry: SetlistEntry)

    @Query("DELETE FROM setlist_entries WHERE id = :entryId")
    protected abstract suspend fun deleteEntry(entryId: Long)

    @Query("UPDATE setlist_entries SET position = :position WHERE id = :entryId")
    protected abstract suspend fun setPosition(entryId: Long, position: Int)

    @Query("UPDATE setlists SET updated_at = :now WHERE id = :setlistId")
    protected abstract suspend fun touch(setlistId: Long, now: Long = System.currentTimeMillis())

    @Query("SELECT * FROM setlists ORDER BY id")
    abstract suspend fun getAllSetlists(): List<Setlist>

    @Query("SELECT * FROM setlist_entries ORDER BY setlist_id, position")
    abstract suspend fun getAllEntries(): List<SetlistEntry>

    /** Removes every setlist (and, by cascade, every entry). */
    @Query("DELETE FROM setlists")
    abstract suspend fun deleteAllSetlists()

    @Insert
    abstract suspend fun insertEntries(entries: List<SetlistEntry>)

    /** Appends [songId] to the end of the setlist and returns the new entry id. */
    @Transaction
    open suspend fun addSong(setlistId: Long, songId: Long, keyOverride: String? = null): Long {
        val position = getEntries(setlistId).size
        val id = insertEntry(SetlistEntry(setlistId = setlistId, songId = songId, position = position, keyOverride = keyOverride))
        touch(setlistId)
        return id
    }

    /** Appends several songs in order, all or nothing. */
    @Transaction
    open suspend fun addSongs(setlistId: Long, songIds: List<Long>) {
        var position = getEntries(setlistId).size
        songIds.forEach { songId -> insertEntry(SetlistEntry(setlistId = setlistId, songId = songId, position = position++)) }
        touch(setlistId)
    }

    /** Removes one entry and closes the gap so positions stay 0..n-1. */
    @Transaction
    open suspend fun removeEntry(setlistId: Long, entryId: Long) {
        deleteEntry(entryId)
        renumber(getEntries(setlistId).map { it.id })
        touch(setlistId)
    }

    /** Moves the entry at index [from] to index [to], shifting the others. */
    @Transaction
    open suspend fun moveEntry(setlistId: Long, from: Int, to: Int) {
        val ids = getEntries(setlistId).map { it.id }.toMutableList()
        require(from in ids.indices && to in ids.indices) { "Position out of range: $from -> $to (size ${ids.size})" }
        ids.add(to, ids.removeAt(from))
        renumber(ids)
        touch(setlistId)
    }

    /** Replaces the order wholesale, e.g. after a drag-and-drop. [entryIds] must be exactly the setlist's entries. */
    @Transaction
    open suspend fun reorder(setlistId: Long, entryIds: List<Long>) {
        val current = getEntries(setlistId).map { it.id }
        require(entryIds.size == current.size && entryIds.toSet() == current.toSet()) {
            "New order must contain each entry of setlist $setlistId exactly once"
        }
        renumber(entryIds)
        touch(setlistId)
    }

    private suspend fun renumber(orderedIds: List<Long>) {
        orderedIds.forEachIndexed { index, id -> setPosition(id, index) }
    }
}
