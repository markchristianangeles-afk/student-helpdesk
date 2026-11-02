package com.chordx.app.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.chordx.app.music.Accidentals
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChordXDatabaseTest {
    private lateinit var db: ChordXDatabase
    private lateinit var songs: SongDao
    private lateinit var setlists: SetlistDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), ChordXDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        songs = db.songDao()
        setlists = db.setlistDao()
    }

    @After
    fun tearDown() = db.close()

    private suspend fun song(title: String, artist: String? = null) =
        songs.insert(Song(title = title, artist = artist, content = "{title: $title}\n[G]Hello"))

    private suspend fun titles(setlistId: Long) = setlists.getItems(setlistId).map { it.song.title }

    private suspend fun positions(setlistId: Long) = setlists.getItems(setlistId).map { it.entry.position }

    @Test
    fun songRoundTripAndUpdate() = runTest {
        val id = songs.insert(Song(title = "Amazing Grace", artist = "Newton", key = "G", capo = 2, content = "[G]A-[C]ma-zing"))
        val stored = songs.getById(id)!!
        assertEquals("Amazing Grace", stored.title)
        assertEquals("G", stored.key)
        assertEquals(2, stored.capo)

        songs.update(stored.copy(key = "A", updatedAt = stored.updatedAt + 1))
        assertEquals("A", songs.getById(id)!!.key)
    }

    @Test
    fun songsGetDistinctUuids() = runTest {
        val a = songs.getById(song("A"))!!
        val b = songs.getById(song("B"))!!
        assertNotEquals(a.uuid, b.uuid)
    }

    @Test
    fun listingIsCaseInsensitiveByTitle() = runTest {
        song("cornerstone"); song("Amazing Grace"); song("Build My Life")
        assertEquals(listOf("Amazing Grace", "Build My Life", "cornerstone"), songs.observeAll().first().map { it.title })
    }

    @Test
    fun searchMatchesTitleOrArtist() = runTest {
        song("Way Maker", "Sinach"); song("Goodness of God", "Bethel"); song("Holy Forever", "Chris Tomlin")
        assertEquals(listOf("Way Maker"), songs.search("maker").first().map { it.title })
        assertEquals(listOf("Goodness of God"), songs.search("bethel").first().map { it.title })
        assertEquals(3, songs.search("").first().size)
    }

    @Test
    fun setlistKeepsOrderAndAllowsRepeats() = runTest {
        val a = song("A"); val b = song("B")
        val set = setlists.insert(Setlist(name = "Sunday"))
        setlists.addSong(set, a)
        setlists.addSong(set, b)
        setlists.addSong(set, a, keyOverride = "D")

        assertEquals(listOf("A", "B", "A"), titles(set))
        assertEquals(listOf(0, 1, 2), positions(set))
        assertEquals("D", setlists.getItems(set)[2].entry.keyOverride)
    }

    @Test
    fun addSongsAppendsInGivenOrder() = runTest {
        val a = song("A"); val b = song("B"); val c = song("C")
        val set = setlists.insert(Setlist(name = "Sunday"))
        setlists.addSong(set, a)
        setlists.addSongs(set, listOf(c, b))
        assertEquals(listOf("A", "C", "B"), titles(set))
        assertEquals(listOf(0, 1, 2), positions(set))
    }

    @Test
    fun moveEntryShiftsOthers() = runTest {
        val set = setlists.insert(Setlist(name = "Sunday"))
        listOf("A", "B", "C", "D").forEach { setlists.addSong(set, song(it)) }

        setlists.moveEntry(set, from = 0, to = 2)
        assertEquals(listOf("B", "C", "A", "D"), titles(set))

        setlists.moveEntry(set, from = 3, to = 0)
        assertEquals(listOf("D", "B", "C", "A"), titles(set))
        assertEquals(listOf(0, 1, 2, 3), positions(set))
    }

    @Test
    fun reorderReplacesOrder() = runTest {
        val set = setlists.insert(Setlist(name = "Sunday"))
        listOf("A", "B", "C").forEach { setlists.addSong(set, song(it)) }
        val ids = setlists.getItems(set).map { it.entry.id }

        setlists.reorder(set, ids.reversed())
        assertEquals(listOf("C", "B", "A"), titles(set))
    }

    @Test(expected = IllegalArgumentException::class)
    fun reorderRejectsIncompleteList() = runTest {
        val set = setlists.insert(Setlist(name = "Sunday"))
        listOf("A", "B").forEach { setlists.addSong(set, song(it)) }
        setlists.reorder(set, listOf(setlists.getItems(set).first().entry.id))
    }

    @Test
    fun removeEntryClosesGap() = runTest {
        val set = setlists.insert(Setlist(name = "Sunday"))
        listOf("A", "B", "C").forEach { setlists.addSong(set, song(it)) }
        val middle = setlists.getItems(set)[1].entry.id

        setlists.removeEntry(set, middle)
        assertEquals(listOf("A", "C"), titles(set))
        assertEquals(listOf(0, 1), positions(set))
    }

    @Test
    fun deletingSongRemovesItFromSetlists() = runTest {
        val a = song("A"); val b = song("B")
        val set = setlists.insert(Setlist(name = "Sunday"))
        setlists.addSong(set, a); setlists.addSong(set, b)
        assertEquals(1, songs.countSetlistUses(a))

        songs.delete(songs.getById(a)!!)
        assertEquals(listOf("B"), titles(set))
    }

    @Test
    fun deletingSetlistRemovesEntriesButKeepsSongs() = runTest {
        val a = song("A")
        val set = setlists.insert(Setlist(name = "Sunday"))
        setlists.addSong(set, a)

        setlists.delete(setlists.getById(set)!!)
        assertNull(setlists.getById(set))
        assertEquals(0, songs.countSetlistUses(a))
        assertEquals("A", songs.getById(a)!!.title)
    }

    @Test
    fun setlistsSortDatedNewestFirstThenUndated() = runTest {
        setlists.insert(Setlist(name = "Undated"))
        setlists.insert(Setlist(name = "Older", eventDate = 20_000))
        setlists.insert(Setlist(name = "Newer", eventDate = 20_100))
        assertEquals(listOf("Newer", "Older", "Undated"), setlists.observeAll().first().map { it.name })
    }

    @Test
    fun transpositionIsStoredPerSong() = runTest {
        val id = song("A")
        songs.setTransposition(id, 3, Accidentals.FLATS, now = 42)
        val stored = songs.getById(id)!!
        assertEquals(3, stored.transpose)
        assertEquals(Accidentals.FLATS, stored.accidentals)
        assertEquals(42, stored.updatedAt)

        songs.setTransposition(id, 0, null)
        assertNull(songs.getById(id)!!.accidentals)
    }

    @Test
    fun duplicatesMatchTitleIgnoringCaseAndExactContent() = runTest {
        songs.insert(Song(title = "Way Maker", content = "[G]x"))
        assertEquals(1, songs.countDuplicates("way maker", "[G]x"))
        assertEquals(0, songs.countDuplicates("Way Maker", "[G]y"))
    }

    @Test
    fun summariesCountSongs() = runTest {
        val a = song("A")
        val full = setlists.insert(Setlist(name = "Full", eventDate = 20_000))
        setlists.insert(Setlist(name = "Empty"))
        setlists.addSong(full, a); setlists.addSong(full, a)
        assertEquals(listOf("Full" to 2, "Empty" to 0), setlists.observeSummaries().first().map { it.setlist.name to it.songCount })
    }

    @Test
    fun keyOverrideCanBeSetAndCleared() = runTest {
        val set = setlists.insert(Setlist(name = "S"))
        val entry = setlists.addSong(set, song("A"))
        setlists.setKeyOverride(entry, "E")
        assertEquals("E", setlists.getItems(set).single().entry.keyOverride)
        setlists.setKeyOverride(entry, null)
        assertNull(setlists.getItems(set).single().entry.keyOverride)
    }
}
