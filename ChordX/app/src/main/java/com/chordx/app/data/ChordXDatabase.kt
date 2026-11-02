package com.chordx.app.data

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [Song::class, Setlist::class, SetlistEntry::class, SermonNote::class, Devotion::class],
    version = 4,
    exportSchema = true,
    autoMigrations = [
        // v2: per-song transpose and sharp/flat preference.
        AutoMigration(from = 1, to = 2),
        // v3: sermon notes.
        AutoMigration(from = 2, to = 3),
        // v4: daily devotion SOAP entries.
        AutoMigration(from = 3, to = 4),
    ],
)
abstract class ChordXDatabase : RoomDatabase() {
    abstract fun songDao(): SongDao
    abstract fun setlistDao(): SetlistDao
    abstract fun noteDao(): NoteDao
    abstract fun devotionDao(): DevotionDao

    companion object {
        /** Opens the on-device database. Call once; [com.chordx.app.ChordXApplication] owns the instance. */
        fun open(context: Context): ChordXDatabase =
            Room.databaseBuilder(context.applicationContext, ChordXDatabase::class.java, "chordx.db").build()
    }
}
