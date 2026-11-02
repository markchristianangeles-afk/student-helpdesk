package com.chordx.app.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.down
import androidx.compose.ui.test.moveBy
import androidx.compose.ui.test.up
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.chordx.app.AppContainer
import com.chordx.app.data.ChordXDatabase
import com.chordx.app.data.Setlist
import com.chordx.app.data.Song
import com.chordx.app.ui.notes.BibleVersion
import com.chordx.app.ui.notes.passageUrl
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class NotesAndLiveTransposeTest {
    @get:Rule(order = 0) val dbRule = InMemoryDatabaseRule()
    @get:Rule(order = 1) val rule = createAndroidComposeRule<ComponentActivity>()

    private val db: ChordXDatabase get() = dbRule.db

    private fun launch() = rule.setContent { ChordXRoot(AppContainer(db, FakeSettings())) }
    private fun waitForText(text: String) = rule.waitUntilAtLeastOneExists(hasText(text), 5_000)

    /** A "Sunday" setlist holding Alpha then Bravo, both in G. Returns the entry ids. */
    private fun setlistOfTwo(): List<Long> = runBlocking {
        val a = db.songDao().insert(Song(title = "Alpha", key = "G", content = "[G]Alpha"))
        val b = db.songDao().insert(Song(title = "Bravo", key = "G", content = "[G]Bravo"))
        val id = db.setlistDao().insert(Setlist(name = "Sunday"))
        db.setlistDao().addSongs(id, listOf(a, b))
        db.setlistDao().getItems(id).map { it.entry.id }
    }

    private fun openSunday() {
        rule.onNodeWithText("Setlists").performClick()
        waitForText("Sunday")
        rule.onNodeWithText("Sunday").performClick()
        rule.waitUntilAtLeastOneExists(hasContentDescription("Options for Alpha"), 5_000)
    }

    @Test
    fun noteBetweenSongsIsSavedAndShown() {
        val entries = setlistOfTwo()
        launch()
        openSunday()

        rule.onNodeWithContentDescription("Options for Alpha").performClick()
        rule.onNodeWithText("Add note after").performClick()
        rule.onNodeWithTag("note_text").performTextInput("Modulate up a tone")
        rule.onNodeWithText("Save").performClick()

        waitForText("Modulate up a tone")
        val saved = runBlocking { db.setlistDao().getItems(db.setlistDao().observeAll().first().single().id) }
        assertEquals("Modulate up a tone", saved.first { it.entry.id == entries[0] }.entry.notes)
        assertNull(saved.first { it.entry.id == entries[1] }.entry.notes)
    }

    @Test
    fun keyListOffersMajorAndMinorKeys() {
        setlistOfTwo()
        launch()
        openSunday()

        rule.onNodeWithContentDescription("Options for Alpha").performClick()
        rule.onNodeWithText("Change key").performClick()
        waitForText("Major keys")
        waitForText("Minor keys")
        waitForText("Em")
    }

    @Test
    fun liveTransposeChangesOnlyThisSetlistsKey() {
        val entries = setlistOfTwo()
        launch()
        openSunday()

        rule.onNodeWithContentDescription("Perform").performClick()
        rule.waitUntilAtLeastOneExists(hasContentDescription("Transpose"), 5_000)
        rule.onNodeWithContentDescription("Transpose").performClick()
        rule.onNodeWithContentDescription("Transpose up").performClick()

        waitForText("Key Ab  ·  +1")
        val items = runBlocking { db.setlistDao().getItems(db.setlistDao().observeAll().first().single().id) }
        assertEquals("G", items.first { it.entry.id == entries[0] }.song.key)
        assertNull(items.first { it.entry.id == entries[1] }.entry.keyOverride)

        rule.onNodeWithText("Reset").performClick()
        rule.waitUntilDoesNotExist(hasText("Reset"), 5_000)
        assertTrue(runBlocking { db.setlistDao().getItems(db.setlistDao().observeAll().first().single().id) }.all { it.entry.keyOverride == null })
    }

    @Test
    fun noteCanBeAddedAboveNextSongInLiveView() {
        val entries = setlistOfTwo()
        launch()
        openSunday()

        rule.onNodeWithContentDescription("Perform").performClick()
        rule.waitUntilAtLeastOneExists(hasText("Next: Bravo (G)"), 5_000)
        rule.onNodeWithTag("add_live_note").performClick()
        rule.onNodeWithTag("note_text").performTextInput("Up a tone")
        rule.onNodeWithText("Save").performClick()

        rule.waitUntilAtLeastOneExists(hasText("Up a tone"), 5_000)
        val items = runBlocking { db.setlistDao().getItems(db.setlistDao().observeAll().first().single().id) }
        assertEquals("Up a tone", items.first { it.entry.id == entries[0] }.entry.notes)
        // The top bar keeps all its buttons, and the lyrics box has no "(if applicable)" hint.
        rule.onNodeWithContentDescription("Larger text").assertIsDisplayed()
        assertTrue(rule.onAllNodesWithText("(if applicable)").fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun longPressAndDragReordersTheSetlist() {
        val ids = runBlocking {
            listOf("Alpha", "Bravo", "Charlie").map { db.songDao().insert(Song(title = it, key = "G", content = "[G]$it")) }
        }
        val setlistId = runBlocking {
            val id = db.setlistDao().insert(Setlist(name = "Sunday"))
            db.setlistDao().addSongs(id, ids)
            id
        }
        launch()
        openSunday()

        rule.onNodeWithText("Alpha").performTouchInput {
            down(center)
            advanceEventTime(800)
            moveBy(Offset(0f, 60f))
            moveBy(Offset(0f, 240f))
            up()
        }

        rule.waitUntil(5_000) {
            runBlocking { db.setlistDao().getItems(setlistId).map { it.song.title } } == listOf("Bravo", "Charlie", "Alpha")
        }
    }

    @Test
    fun sermonNoteIsCreatedAndListed() {
        launch()
        rule.onNodeWithText("Notes").performClick()
        waitForText("No notes yet.\nTap New note to start one, for example for Sunday's sermon.")

        rule.onNodeWithTag("new_note").performClick()
        rule.onNodeWithTag("note_title").performTextInput("Sunday sermon")

        // The Bible section starts folded away so the note has the room; one tap opens it.
        assertTrue(rule.onAllNodesWithTag("verse_reference").fetchSemanticsNodes().isEmpty())
        rule.onNodeWithTag("bible_header").performClick()
        rule.onNodeWithTag("bible_version").performClick()
        rule.onNodeWithText("The Message").performClick()
        rule.onNodeWithTag("verse_reference").performTextInput("John 3:16")
        rule.onNodeWithText("Search").assertIsDisplayed()
        assertTrue(rule.onAllNodesWithText("Add reference to note").fetchSemanticsNodes().isEmpty())
        rule.onNodeWithTag("bible_header").performClick()
        assertTrue(rule.onAllNodesWithTag("verse_reference").fetchSemanticsNodes().isEmpty())

        rule.onNodeWithTag("note_body").performTextInput("Grace alone")
        rule.onNodeWithContentDescription("Back").performClick()

        waitForText("Sunday sermon")
        val notes = runBlocking { db.noteDao().observeAll().first() }
        assertEquals(1, notes.size)
        assertEquals("Grace alone", notes.single().body)
        assertTrue(passageUrl("John 3:16", BibleVersion.TPT).endsWith("search=John%203%3A16&version=TPT"))
        assertTrue(passageUrl("John 3:16", BibleVersion.NIV).endsWith("version=NIV"))
        assertEquals(listOf("NIV", "NKJV", "KJV", "ESV", "NLT", "AMP", "GNT", "CEV", "MSG", "TPT", "MBBTAG"), BibleVersion.entries.map { it.code })
        assertTrue(passageUrl("Juan 3:16", BibleVersion.MBB).endsWith("version=MBBTAG"))
    }
}
