package com.chordx.app.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.chordx.app.AppContainer
import com.chordx.app.data.ChordXDatabase
import com.chordx.app.data.Setlist
import com.chordx.app.data.Song
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class SetlistScreensTest {
    @get:Rule(order = 0) val dbRule = InMemoryDatabaseRule()
    @get:Rule(order = 1) val rule = createAndroidComposeRule<ComponentActivity>()

    private val db: ChordXDatabase get() = dbRule.db
    private val settings = FakeSettings()

    private fun launch() = rule.setContent { ChordXRoot(AppContainer(db, settings)) }
    private fun waitForText(text: String) = rule.waitUntilAtLeastOneExists(hasText(text), 5_000)
    private fun song(title: String, key: String? = "G", content: String = "[G]$title") =
        runBlocking { db.songDao().insert(Song(title = title, key = key, content = content)) }

    private fun setlistWith(vararg songIds: Long, name: String = "Sunday"): Long = runBlocking {
        val id = db.setlistDao().insert(Setlist(name = name))
        songIds.forEach { db.setlistDao().addSong(id, it) }
        id
    }

    private fun titles(setlistId: Long) = runBlocking { db.setlistDao().getItems(setlistId).map { it.song.title } }

    /** Waits for the row's menu button (the setlist loads asynchronously) and opens it. */
    private fun openRowMenu(title: String) {
        rule.waitUntilAtLeastOneExists(hasContentDescription("Options for $title"), 5_000)
        rule.onNodeWithContentDescription("Options for $title").performClick()
    }

    private fun openSetlistsTab() {
        rule.onNodeWithText("Setlists").performClick()
    }

    @Test
    fun createSetlistAndAddSongsInPickedOrder() {
        song("Alpha"); song("Bravo"); song("Charlie")
        launch()
        openSetlistsTab()
        waitForText("No setlists yet.\nTap New setlist to plan a service or gig.")

        rule.onNodeWithTag("new_setlist").performClick()
        rule.onNodeWithTag("setlist_name").performTextInput("Sunday AM")
        rule.onNodeWithText("Save").performClick()

        waitForText("No songs yet.\nTap Add songs to build the set.")
        rule.onNodeWithTag("add_songs").performClick()
        waitForText("Charlie")
        rule.onNodeWithText("Charlie").performClick()
        rule.onNodeWithText("Alpha").performClick()
        rule.onNodeWithText("Add (2)").performClick()

        // Wait on the UI (which lets the app's main thread run) until it's back on the setlist.
        rule.waitUntilDoesNotExist(hasText("Add (2)"), 5_000)
        waitForText("Alpha")
        val setlist = runBlocking { db.setlistDao().observeAll().first() }.single()
        assertEquals("Sunday AM", setlist.name)
        assertEquals(listOf("Charlie", "Alpha"), titles(setlist.id))

        rule.onNodeWithContentDescription("Back").performClick()
        waitForText("2 songs")
    }

    @Test
    fun reorderAndRemoveFromRowMenu() {
        val set = setlistWith(song("One"), song("Two"), song("Three"))
        launch()
        openSetlistsTab()
        waitForText("Sunday")
        rule.onNodeWithText("Sunday").performClick()

        openRowMenu("Three")
        rule.onNodeWithText("Move to top").performClick()
        rule.waitUntil(5_000) { titles(set) == listOf("Three", "One", "Two") }

        openRowMenu("One")
        rule.onNodeWithText("Move down").performClick()
        rule.waitUntil(5_000) { titles(set) == listOf("Three", "Two", "One") }

        openRowMenu("Two")
        rule.onNodeWithText("Remove from setlist").performClick()
        rule.waitUntil(5_000) { titles(set) == listOf("Three", "One") }
    }

    @Test
    fun keyForSetlistOnly() {
        val songId = song("Grace", key = "G")
        val set = setlistWith(songId)
        launch()
        openSetlistsTab()
        waitForText("Sunday")
        rule.onNodeWithText("Sunday").performClick()

        openRowMenu("Grace")
        rule.onNodeWithText("Change key").performClick()
        rule.onNodeWithText("A").performClick()
        rule.waitUntil(5_000) { runBlocking { db.setlistDao().getItems(set) }.single().entry.keyOverride == "A" }
        assertEquals(0, runBlocking { db.songDao().getById(songId) }!!.transpose) // the song itself is untouched

        openRowMenu("Grace")
        rule.onNodeWithText("Change key").performClick()
        rule.onNodeWithText("Use song's key").performClick()
        rule.waitUntil(5_000) { runBlocking { db.setlistDao().getItems(set) }.single().entry.keyOverride == null }
    }

    @Test
    fun songWithoutKeyExplainsWhyKeyCantChange() {
        setlistWith(song("Free", key = null))
        launch()
        openSetlistsTab()
        waitForText("Sunday")
        rule.onNodeWithText("Sunday").performClick()
        openRowMenu("Free")
        rule.onNodeWithText("Change key").performClick()
        rule.onNodeWithText("This song has no key set", substring = true).assertIsDisplayed()
    }

    @Test
    fun editAndDeleteSetlistKeepsSongs() {
        val songId = song("Stay")
        val set = setlistWith(songId)
        launch()
        openSetlistsTab()
        waitForText("Sunday")
        rule.onNodeWithText("Sunday").performClick()

        rule.onNodeWithContentDescription("Edit setlist").performClick()
        rule.onNodeWithTag("setlist_name").performTextClearance()
        rule.onNodeWithTag("setlist_name").performTextInput("Sunday evening")
        rule.onNodeWithText("Save").performClick()
        rule.waitUntil(5_000) { runBlocking { db.setlistDao().getById(set) }?.name == "Sunday evening" }

        rule.onNodeWithContentDescription("Delete setlist").performClick()
        rule.onNodeWithText("The songs stay in your library. This can't be undone.").assertIsDisplayed()
        rule.onNodeWithText("Delete").performClick()
        waitForText("No setlists yet.\nTap New setlist to plan a service or gig.")
        assertNull(runBlocking { db.setlistDao().getById(set) })
        assertEquals("Stay", runBlocking { db.songDao().getById(songId) }!!.title)
    }

    @Test
    fun emptySetlistCantBePerformed() {
        setlistWith()
        launch()
        openSetlistsTab()
        waitForText("Sunday")
        rule.onNodeWithText("Sunday").performClick()
        rule.onNodeWithContentDescription("Perform").assertIsNotEnabled()
    }

    @Test
    fun performanceModeShowsSongsInOrderWithSetlistKeys() {
        val a = song("First", key = "G", content = "[G]one [C]two")
        val b = song("Second", key = "C", content = "[C]three")
        val set = setlistWith(a, b)
        runBlocking { db.setlistDao().setKeyOverride(db.setlistDao().getItems(set).first().entry.id, "A") }
        launch()
        openSetlistsTab()
        waitForText("Sunday")
        rule.onNodeWithText("Sunday").performClick()
        waitForText("First")
        rule.onNodeWithContentDescription("Perform").performClick()

        rule.waitUntilAtLeastOneExists(hasText("1 / 2"), 5_000)
        waitForText("Key A")
        rule.onNodeWithText("D").assertIsDisplayed() // C transposed G->A
        rule.onNodeWithText("Next: Second (C)").performClick()
        rule.onNodeWithTag("performance_position").assertTextEquals("2 / 2")
        waitForText("End of setlist")
    }

    @Test
    fun pageTurnerKeysMoveBetweenSongs() {
        val set = setlistWith(song("First"), song("Second"), song("Third"))
        launch()
        openSetlistsTab()
        waitForText("Sunday")
        rule.onNodeWithText("Sunday").performClick()
        waitForText("Second")
        rule.onNodeWithText("Second").performClick() // tap a row = perform from that song

        rule.waitUntilAtLeastOneExists(hasText("2 / 3"), 5_000)
        rule.onRoot().performKeyInput { pressKey(Key.DirectionRight) }
        rule.waitUntilAtLeastOneExists(hasText("3 / 3"), 5_000)
        rule.onRoot().performKeyInput { pressKey(Key.DirectionLeft) }
        rule.waitUntilAtLeastOneExists(hasText("2 / 3"), 5_000)
        // Short songs don't scroll, so Page Down goes straight to the next song.
        rule.onRoot().performKeyInput { pressKey(Key.PageDown) }
        rule.waitUntilAtLeastOneExists(hasText("3 / 3"), 5_000)
        assertEquals(3, titles(set).size)
    }

    @Test
    fun performanceModeCanHideLyrics() {
        setlistWith(song("Solo", content = "[G]Sing [D]out"))
        settings.showLyrics.value = false
        launch()
        openSetlistsTab()
        waitForText("Sunday")
        rule.onNodeWithText("Sunday").performClick()
        waitForText("Solo")
        rule.onNodeWithContentDescription("Perform").performClick()

        rule.waitUntilAtLeastOneExists(hasText("1 / 1"), 5_000)
        rule.onNodeWithText("D----").assertIsDisplayed()
        rule.onNodeWithText("Sing").assertDoesNotExist()

        rule.onNodeWithTag("show_lyrics").performClick()
        rule.waitUntilAtLeastOneExists(hasText("Sing"), 5_000)
        assertEquals(true, settings.showLyrics.value)
    }

    @Test
    fun textSizeIsRememberedAndBounded() {
        setlistWith(song("Big"))
        launch()
        openSetlistsTab()
        waitForText("Sunday")
        rule.onNodeWithText("Sunday").performClick()
        waitForText("Big")
        rule.onNodeWithContentDescription("Perform").performClick()

        rule.waitUntilAtLeastOneExists(hasText("1 / 1"), 5_000)
        repeat(2) { rule.onNodeWithContentDescription("Larger text").performClick() }
        rule.waitUntil(5_000) { settings.performanceFontScale.value == 1.2f }

        settings.performanceFontScale.value = 0.7f
        rule.waitUntil(5_000) { runCatching { rule.onNodeWithContentDescription("Smaller text").assertIsNotEnabled() }.isSuccess }

        rule.onNodeWithContentDescription("Close").performClick()
        waitForText("Big")
    }
}
