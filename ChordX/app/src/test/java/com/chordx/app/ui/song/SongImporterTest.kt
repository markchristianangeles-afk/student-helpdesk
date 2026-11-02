package com.chordx.app.ui.song

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.chordx.app.data.ChordXDatabase
import com.chordx.app.data.ImportedSong
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SongImporterTest {
    private lateinit var db: ChordXDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), ChordXDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun importsSkipsDuplicatesAndCountsFailures() = runTest {
        val first = listOf(
            ImportedSong(title = "One", key = "G", content = "[G]a"),
            ImportedSong(title = "Two", key = "H", content = "[C]b"),
        )
        assertEquals(ImportSummary(imported = 2, duplicates = 0, failedFiles = 1), importSongs(db.songDao(), first, failedFiles = 1))

        // Re-importing the same files adds nothing; a changed chart counts as new.
        val again = first + ImportedSong(title = "one", content = "[G]a changed")
        assertEquals(ImportSummary(imported = 1, duplicates = 2, failedFiles = 0), importSongs(db.songDao(), again))

        val stored = db.songDao().observeAll().first()
        assertEquals(listOf("ONE", "ONE V2", "TWO"), stored.map { it.title })
        assertEquals(null, stored.single { it.title == "TWO" }.key) // invalid {key: H} dropped, song kept
    }
}
