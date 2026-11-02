package com.chordx.app.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.chordx.app.music.Accidentals
import com.chordx.app.ui.FakeSettings
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackupTest {
    private lateinit var source: ChordXDatabase
    private lateinit var target: ChordXDatabase
    private val sourceSettings = FakeSettings()
    private val targetSettings = FakeSettings()

    private fun newDb() = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), ChordXDatabase::class.java)
        .allowMainThreadQueries().build()

    @Before
    fun setUp() {
        source = newDb()
        target = newDb()
    }

    @After
    fun tearDown() {
        source.close()
        target.close()
    }

    private fun fill() = runBlocking {
        val grace = source.songDao().insert(
            Song(title = "AMAZING GRACE", artist = "Newton", key = "G", capo = 2, tempo = 72, timeSignature = "3/4",
                content = "[G]Amazing", transpose = 3, accidentals = Accidentals.FLATS),
        )
        val other = source.songDao().insert(Song(title = "OTHER", content = "[C]x"))
        val setlist = source.setlistDao().insert(Setlist(name = "Sunday", eventDate = 20000, notes = "AM"))
        source.setlistDao().addSongs(setlist, listOf(grace, other))
        val first = source.setlistDao().getItems(setlist).first().entry
        source.setlistDao().setKeyOverride(first.id, "A")
        source.setlistDao().setNote(first.id, "Modulate up a tone")
        source.noteDao().insert(SermonNote(title = "Sermon", body = "John 3:16 (NIV)"))
        source.devotionDao().upsert(Devotion(date = "2026-11-02", scripture = "John 3:16", observation = "Love", application = "Give", prayer = "Thanks"))
        source.devotionDao().upsert(Devotion(date = "2026-11-03", prayer = "Only a prayer"))
        sourceSettings.setThemeMode(ThemeMode.DARK)
        sourceSettings.setAccidentals(Accidentals.SHARPS)
        sourceSettings.setShowLyrics(false)
        sourceSettings.setPerformanceFontScale(1.6f)
    }

    private fun parse(json: String) = (parseBackup(json) as BackupParseResult.Ok).data

    @Test
    fun replaceRestoresEverythingIncludingSettings() = runBlocking {
        fill()
        // The target starts with unrelated data that must be gone afterwards.
        target.songDao().insert(Song(title = "OLD SONG", content = "[D]old"))
        target.noteDao().insert(SermonNote(title = "old note"))
        target.devotionDao().upsert(Devotion(date = "2020-01-01", prayer = "old"))

        val summary = restoreBackup(target, targetSettings, parse(buildBackup(source, sourceSettings)), replace = true)

        assertEquals(RestoreSummary(songs = 2, setlists = 1, notes = 1, skipped = 0, devotions = 2), summary)
        val songs = target.songDao().getAll()
        assertEquals(listOf("AMAZING GRACE", "OTHER"), songs.map { it.title })
        songs.first().let {
            assertEquals("G", it.key); assertEquals(2, it.capo); assertEquals(72, it.tempo); assertEquals("3/4", it.timeSignature)
            assertEquals(3, it.transpose); assertEquals(Accidentals.FLATS, it.accidentals); assertEquals("[G]Amazing", it.content)
        }
        val setlist = target.setlistDao().getAllSetlists().single()
        assertEquals("Sunday", setlist.name); assertEquals(20000L, setlist.eventDate); assertEquals("AM", setlist.notes)
        val items = target.setlistDao().getItems(setlist.id)
        assertEquals(listOf("AMAZING GRACE", "OTHER"), items.map { it.song.title })
        assertEquals("A", items[0].entry.keyOverride)
        assertEquals("Modulate up a tone", items[0].entry.notes)
        assertEquals(listOf("Sermon"), target.noteDao().getAll().map { it.title })
        target.devotionDao().getAll().let { days ->
            assertEquals(listOf("2026-11-02", "2026-11-03"), days.map { it.date })
            assertEquals("John 3:16", days[0].scripture); assertEquals("Thanks", days[0].prayer); assertEquals("Only a prayer", days[1].prayer)
        }
        assertEquals(ThemeMode.DARK, targetSettings.themeMode.value)
        assertEquals(Accidentals.SHARPS, targetSettings.accidentals.value)
        assertEquals(false, targetSettings.showLyrics.value)
        assertEquals(1.6f, targetSettings.performanceFontScale.value, 0.001f)
    }

    @Test
    fun addMissingKeepsWhatIsThereAndSkipsRepeats() = runBlocking {
        fill()
        target.songDao().insert(Song(title = "KEEP ME", content = "[E]keep"))
        val data = parse(buildBackup(source, sourceSettings))

        val first = restoreBackup(target, targetSettings, data, replace = false)
        assertEquals(RestoreSummary(songs = 2, setlists = 1, notes = 1, skipped = 0, devotions = 2), first)
        assertEquals(setOf("KEEP ME", "AMAZING GRACE", "OTHER"), target.songDao().getAll().map { it.title }.toSet())
        assertEquals(ThemeMode.SYSTEM, targetSettings.themeMode.value) // settings untouched when merging

        val again = restoreBackup(target, targetSettings, data, replace = false)
        assertEquals(RestoreSummary(songs = 0, setlists = 0, notes = 0, skipped = 6), again)
        assertEquals(3, target.songDao().getAll().size)
        assertEquals(1, target.setlistDao().getAllSetlists().size)
        assertEquals(2, target.setlistDao().getAllEntries().size)
    }

    @Test
    fun rejectsFilesThatAreNotBackups() {
        assertTrue(parseBackup("hello") is BackupParseResult.Bad)
        assertTrue(parseBackup("""{"format":"something-else","version":1}""") is BackupParseResult.Bad)
        assertTrue(parseBackup("""{"format":"chordx-backup","version":99,"songs":[],"setlists":[],"entries":[]}""") is BackupParseResult.Bad)
        assertTrue(parseBackup("""{"format":"chordx-backup","version":1}""") is BackupParseResult.Bad)
    }
}
