package com.chordx.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.chordx.app.music.Accidentals
import kotlinx.coroutines.flow.Flow

@Dao
interface SongDao {
    @Insert
    suspend fun insert(song: Song): Long

    @Update
    suspend fun update(song: Song)

    @Delete
    suspend fun delete(song: Song)

    @Query("SELECT * FROM songs WHERE id = :id")
    suspend fun getById(id: Long): Song?

    @Query("SELECT * FROM songs WHERE id = :id")
    fun observeById(id: Long): Flow<Song?>

    @Query("SELECT * FROM songs ORDER BY title COLLATE NOCASE, id")
    fun observeAll(): Flow<List<Song>>

    /** Case-insensitive match on title or artist. */
    @Query(
        """
        SELECT * FROM songs
        WHERE title LIKE '%' || :query || '%' OR artist LIKE '%' || :query || '%'
        ORDER BY title COLLATE NOCASE, id
        """
    )
    fun search(query: String): Flow<List<Song>>

    @Query("UPDATE songs SET transpose = :semitones, accidentals = :accidentals, updated_at = :now WHERE id = :id")
    suspend fun setTransposition(id: Long, semitones: Int, accidentals: Accidentals?, now: Long = System.currentTimeMillis())

    /** Saves the tempo set from the metronome. */
    @Query("UPDATE songs SET tempo = :bpm, updated_at = :now WHERE id = :id")
    suspend fun setTempo(id: Long, bpm: Int, now: Long = System.currentTimeMillis())

    /** Exact duplicates (same title ignoring case, same chart), used to skip re-imports. */
    @Query("SELECT COUNT(*) FROM songs WHERE title = :title COLLATE NOCASE AND content = :content")
    suspend fun countDuplicates(title: String, content: String): Int

    @Query("SELECT * FROM songs ORDER BY id")
    suspend fun getAll(): List<Song>

    @Query("DELETE FROM songs")
    suspend fun deleteAll()

    /**
     * Titles equal to [title] or numbered from it ("TITLE V2"), ignoring song [excludeId].
     * LIKE wildcards in a title can over-match, which only makes the numbering more cautious.
     */
    @Query("SELECT title FROM songs WHERE id != :excludeId AND (title = :title COLLATE NOCASE OR title LIKE :title || ' V%')")
    suspend fun titlesLike(title: String, excludeId: Long = 0): List<String>

    /** How many setlist entries use this song, so the UI can warn before deleting. */
    @Query("SELECT COUNT(*) FROM setlist_entries WHERE song_id = :songId")
    suspend fun countSetlistUses(songId: Long): Int
}
