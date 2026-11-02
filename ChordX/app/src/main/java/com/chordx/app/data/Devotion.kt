package com.chordx.app.data

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

/** One day's SOAP entry (Scripture, Observation, Application, Prayer). [date] is ISO yyyy-MM-dd. */
@Entity(
    tableName = "devotions",
    indices = [Index(value = ["date"], unique = true)],
)
data class Devotion(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    val scripture: String = "",
    val observation: String = "",
    val application: String = "",
    val prayer: String = "",
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis(),
)

@Dao
interface DevotionDao {
    @Query("SELECT * FROM devotions WHERE date = :date")
    suspend fun getByDate(date: String): Devotion?

    /** Replaces any entry already saved for the same date. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(devotion: Devotion)

    /** Entries from [start] to [end] inclusive (ISO dates sort the same as calendar dates). */
    @Query("SELECT * FROM devotions WHERE date BETWEEN :start AND :end ORDER BY date")
    suspend fun getBetween(start: String, end: String): List<Devotion>

    @Query("SELECT * FROM devotions ORDER BY date")
    suspend fun getAll(): List<Devotion>

    @Query("DELETE FROM devotions")
    suspend fun deleteAll()

    @Query("DELETE FROM devotions WHERE date = :date")
    suspend fun deleteByDate(date: String)
}
