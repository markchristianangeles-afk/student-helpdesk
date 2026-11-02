package com.chordx.app.data

import android.content.Context
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Songs and setlists saved by version 1 of the app must survive the upgrade. */
@RunWith(AndroidJUnit4::class)
class MigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), ChordXDatabase::class.java)

    // A full path: given a bare name, Room's test helper mismatches paths on Windows hosts.
    private val dbName = ApplicationProvider.getApplicationContext<Context>().getDatabasePath("migration-test").absolutePath

    @Test
    fun version2To3KeepsDataAndAddsNotesTable() {
        helper.createDatabase(dbName, 2).use { db ->
            db.execSQL(
                "INSERT INTO songs (id, uuid, title, artist, song_key, capo, tempo, time_signature, content, transpose, accidentals, created_at, updated_at) " +
                    "VALUES (1, 'u1', 'Amazing Grace', 'Newton', 'G', 2, 72, '3/4', '[G]Amazing', 3, NULL, 10, 20)"
            )
            db.execSQL("INSERT INTO setlists (id, uuid, name, event_date, notes, created_at, updated_at) VALUES (1, 's1', 'Sunday', NULL, NULL, 1, 1)")
            db.execSQL("INSERT INTO setlist_entries (id, setlist_id, song_id, position, key_override, notes) VALUES (1, 1, 1, 0, 'A', 'Modulate')")
        }

        helper.runMigrationsAndValidate(dbName, 3, true).use { db ->
            db.query("SELECT title, transpose FROM songs").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("Amazing Grace", c.getString(0))
                assertEquals(3, c.getInt(1))
            }
            db.query("SELECT key_override, notes FROM setlist_entries").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("A", c.getString(0))
                assertEquals("Modulate", c.getString(1))
            }
            db.execSQL("INSERT INTO notes (uuid, title, body, created_at, updated_at) VALUES ('n1', 'Sermon', 'John 3:16', 1, 1)")
            db.query("SELECT COUNT(*) FROM notes").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(1, c.getInt(0))
            }
        }
    }

    @Test
    fun version1To2KeepsDataAndAddsTransposeColumns() {
        helper.createDatabase(dbName, 1).use { db ->
            db.execSQL(
                "INSERT INTO songs (id, uuid, title, artist, song_key, capo, tempo, time_signature, content, created_at, updated_at) " +
                    "VALUES (1, 'u1', 'Amazing Grace', 'Newton', 'G', 2, 72, '3/4', '[G]Amazing', 10, 20)"
            )
            db.execSQL("INSERT INTO setlists (id, uuid, name, event_date, notes, created_at, updated_at) VALUES (1, 's1', 'Sunday', NULL, NULL, 1, 1)")
            db.execSQL("INSERT INTO setlist_entries (id, setlist_id, song_id, position, key_override, notes) VALUES (1, 1, 1, 0, 'A', NULL)")
        }

        helper.runMigrationsAndValidate(dbName, 2, true).use { db ->
            db.query("SELECT title, song_key, capo, content, transpose, accidentals FROM songs").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("Amazing Grace", c.getString(0))
                assertEquals("G", c.getString(1))
                assertEquals(2, c.getInt(2))
                assertEquals("[G]Amazing", c.getString(3))
                assertEquals(0, c.getInt(4))
                assertTrue(c.isNull(5))
            }
            db.query("SELECT key_override FROM setlist_entries").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("A", c.getString(0))
            }
        }
    }
}
