package com.chordx.app.data

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import java.util.UUID

/** A free-text note, typically taken during a sermon. */
@Entity(
    tableName = "notes",
    indices = [Index(value = ["uuid"], unique = true)],
)
data class SermonNote(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uuid: String = UUID.randomUUID().toString(),
    val title: String = "",
    val body: String = "",
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at") val updatedAt: Long = createdAt,
)

@Dao
interface NoteDao {
    @Insert
    suspend fun insert(note: SermonNote): Long

    @Update
    suspend fun update(note: SermonNote)

    @Delete
    suspend fun delete(note: SermonNote)

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun getById(id: Long): SermonNote?

    @Query("SELECT * FROM notes ORDER BY id")
    suspend fun getAll(): List<SermonNote>

    @Query("DELETE FROM notes")
    suspend fun deleteAll()

    /** Most recently edited first. */
    @Query("SELECT * FROM notes ORDER BY updated_at DESC, id DESC")
    fun observeAll(): Flow<List<SermonNote>>
}
