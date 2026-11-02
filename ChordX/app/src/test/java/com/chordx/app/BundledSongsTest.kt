package com.chordx.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.chordx.app.chordpro.Line
import com.chordx.app.chordpro.isChordsOnly
import com.chordx.app.chordpro.parseForDisplay
import com.chordx.app.data.ChordXDatabase
import com.chordx.app.music.Chord
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Runs the real library bundled in app/src/main/assets/library through the first-launch import. */
@RunWith(AndroidJUnit4::class)
class BundledSongsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var db: ChordXDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, ChordXDatabase::class.java).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun loadsTheBundledLibrary() {
        val (songs, failed) = BundledSongs.read(context.assets)
        assertEquals(0, failed)
        assertTrue("expected the bundled library, found ${songs.size}", songs.size >= 400)
        assertEquals(songs.size, songs.map { it.title.lowercase() }.toSet().size)
    }

    @Test
    fun importsOnceWithoutFailures() = runTest {
        val summary = BundledSongs.importIfNeeded(context, db.songDao())!!
        assertEquals(0, summary.failedFiles)
        val songs = db.songDao().observeAll().first()
        assertEquals(summary.imported, songs.size)

        // Running again (e.g. next launch) adds nothing, so deleted songs stay deleted.
        assertNull(BundledSongs.importIfNeeded(context, db.songDao()))
        assertEquals(songs.size, db.songDao().observeAll().first().size)

        // How well the team's format comes through; printed for review, not asserted.
        val docs = songs.associateWith { parseForDisplay(it.content) }
        val noChords = docs.filterValues { doc -> doc.knownChords().none() }.keys.map { it.title }
        println("BUNDLED: bundled=${BundledSongs.read(context.assets).first.size} imported=${summary.imported} duplicates=${summary.duplicates}")
        println("BUNDLED: withKey=${songs.count { it.key != null }} showingChords=${docs.size - noChords.size} noChordsRecognised=${noChords.size}")
        println("BUNDLED: noChords sample=${noChords.take(15)}")
        println("BUNDLED: chordsOnlyCharts=${docs.values.count { it.isChordsOnly() }} convertedForDisplay=${docs.values.count { it.convertedForDisplay }}")
        // Lines left as plain text in songs that otherwise read as chord charts: the formats still not understood.
        val leftovers = docs.filterValues { it.knownChords().any() && !it.isChordsOnly() }.values
            .flatMap { doc -> doc.sections.flatMap { it.lines }.filterIsInstance<Line.Lyrics>().filter { !it.hasChords && it.text.isNotBlank() } }
            .map { it.text.trim().take(40) }
        println("BUNDLED: leftover text lines=${leftovers.size}; most common=" + leftovers.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.take(25).map { "${it.key}=${it.value}" })
    }

    /**
     * Every chord in the team's real charts should end up as a [chord], so it transposes.
     * Finds lines whose plain (unbracketed) text is still mostly chord names after import.
     * The few allowed are not chords (author credits, "KEY G:") or typos in the files.
     */
    @Test
    fun everyChordInTheLibraryTransposes() {
        val (charts, _) = BundledSongs.read(context.assets)
        val split = Regex("""[\s\-–—|,()*^]+""")
        val bracketed = Regex("""\[[^\]]*\]""")
        fun chordName(token: String): Boolean {
            val bare = token.trim('/', '.', ':', '°', '>')
            return bare.isNotEmpty() && bare.length <= 10 && Chord.parse(bare.replaceFirstChar { it.uppercaseChar() }) != null
        }
        val missed = charts.flatMap { song ->
            song.content.lines()
                .filter { it.isNotBlank() && !it.trimStart().startsWith("{") }
                .filter { line ->
                    val tokens = split.split(bracketed.replace(line, " ")).filter { it.isNotBlank() }
                    val chords = tokens.count(::chordName)
                    chords >= 1 && chords * 2 >= tokens.size
                }
                .map { "${song.title} | ${it.trim()}" }
        }
        println("UNCONVERTED chord-like lines=${missed.size}:\n" + missed.joinToString("\n"))
        assertTrue("chords left untransposable:\n" + missed.joinToString("\n"), missed.size <= 5)
    }
}
