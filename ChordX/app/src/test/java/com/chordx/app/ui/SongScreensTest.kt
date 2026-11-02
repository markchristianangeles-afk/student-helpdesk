package com.chordx.app.ui

import android.content.Context
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.chordx.app.AppContainer
import com.chordx.app.data.ChordXDatabase
import com.chordx.app.data.Setlist
import com.chordx.app.data.Song
import com.chordx.app.data.ThemeMode
import com.chordx.app.music.Accidentals
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

private const val EMPTY_LIBRARY = "No songs yet.\nTap New song, or import files from the ⋮ menu."

@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class SongScreensTest {
    @get:Rule(order = 0) val dbRule = InMemoryDatabaseRule()
    @get:Rule(order = 1) val rule = createAndroidComposeRule<ComponentActivity>()

    private val db: ChordXDatabase get() = dbRule.db
    private val settings = FakeSettings()
    private val picker = FakeFilePicker()

    private fun launch() = rule.setContent {
        CompositionLocalProvider(LocalActivityResultRegistryOwner provides picker) {
            ChordXRoot(AppContainer(db, settings))
        }
    }

    private fun seed(song: Song): Long = runBlocking { db.songDao().insert(song) }
    private fun allSongs() = runBlocking { db.songDao().observeAll().first() }

    private fun waitForText(text: String) = rule.waitUntilAtLeastOneExists(hasText(text), 5_000)
    private fun waitForNoText(text: String) = rule.waitUntilDoesNotExist(hasText(text), 5_000)
    private fun chartText(text: String) = hasText(text) and hasAnyAncestor(hasTestTag("chart"))

    private fun file(name: String, text: String): Uri {
        val dir = File(ApplicationProvider.getApplicationContext<Context>().cacheDir, "picked").apply { mkdirs() }
        return Uri.fromFile(File(dir, name).apply { writeBytes(text.toByteArray()) })
    }

    private fun importFromMenu(vararg files: Uri) {
        picker.picked = files.toList()
        rule.onNodeWithContentDescription("More options").performClick()
        rule.onNodeWithText("Import files").performClick()
    }

    private fun openSong(title: String) {
        waitForText(title)
        rule.onNodeWithText(title).performClick()
    }

    // --- Library CRUD ---

    @Test
    fun emptyLibraryShowsHint() {
        launch()
        waitForText(EMPTY_LIBRARY)
    }

    @Test
    fun createSongThenSeeChordsAboveLyrics() {
        launch()
        rule.onNodeWithTag("new_song").performClick()
        rule.onNodeWithTag("field_title").performTextInput("Amazing Grace")
        rule.onNodeWithTag("field_artist").performTextInput("John Newton")
        rule.onNodeWithTag("field_key").performTextInput("G")
        rule.onNodeWithTag("field_content").performTextInput("[G]Amazing [C]grace")
        rule.onNodeWithContentDescription("Save").performClick()

        waitForText("Key G")
        rule.onNode(chartText("Amazing")).assertIsDisplayed()
        rule.onNode(chartText("grace")).assertIsDisplayed()
        rule.onNode(chartText("C")).assertIsDisplayed()
        assertEquals("John Newton", allSongs().single().artist)

        // Back goes to the list, not the editor.
        rule.onNodeWithContentDescription("Back").performClick()
        waitForText("John Newton")
        rule.onNodeWithTag("new_song").assertIsDisplayed()
    }

    @Test
    fun saveWithoutTitleShowsError() {
        launch()
        rule.onNodeWithTag("new_song").performClick()
        rule.onNodeWithContentDescription("Save").performClick()
        waitForText("Title is required")
        assertEquals(0, allSongs().size)
    }

    @Test
    fun editSongUpdatesViewer() {
        seed(Song(title = "Old Title", key = "C", content = "[C]la"))
        launch()
        openSong("Old Title")
        rule.onNodeWithContentDescription("Edit").performClick()

        rule.onNodeWithTag("field_title").performTextClearance()
        rule.onNodeWithTag("field_title").performTextInput("New Title")
        rule.onNodeWithTag("field_capo").performTextInput("2")
        rule.onNodeWithContentDescription("Save").performClick()

        waitForText("Key C  ·  Capo 2")
        rule.onNodeWithText("Edit song").assertDoesNotExist()
        assertEquals("NEW TITLE", allSongs().single().title)
    }

    @Test
    fun cancellingDirtyEditAsksBeforeDiscarding() {
        seed(Song(title = "Keep Me"))
        launch()
        openSong("Keep Me")
        rule.onNodeWithContentDescription("Edit").performClick()
        rule.onNodeWithTag("field_artist").performTextInput("Someone")

        rule.onNodeWithContentDescription("Cancel").performClick()
        rule.onNodeWithText("Discard changes?").assertIsDisplayed()
        rule.onNodeWithText("Keep editing").performClick()
        rule.onNodeWithText("Edit song").assertIsDisplayed()

        rule.onNodeWithContentDescription("Cancel").performClick()
        rule.onNodeWithText("Discard").performClick()
        waitForNoText("Edit song")
        assertNull(allSongs().single().artist)
    }

    @Test
    fun deleteWarnsAboutSetlistsAndRemovesSong() {
        val id = seed(Song(title = "Doomed"))
        runBlocking { db.setlistDao().addSong(db.setlistDao().insert(Setlist(name = "Sunday")), id) }
        launch()
        openSong("Doomed")
        rule.onNodeWithContentDescription("Delete").performClick()

        waitForText("It will also be removed from 1 setlist slot. This can't be undone.")
        rule.onNodeWithText("Delete").performClick()

        waitForText(EMPTY_LIBRARY)
        assertNull(runBlocking { db.songDao().getById(id) })
    }

    @Test
    fun searchFiltersList() {
        seed(Song(title = "Way Maker", artist = "Sinach"))
        seed(Song(title = "Goodness of God", artist = "Bethel"))
        launch()
        waitForText("Way Maker")

        rule.onNodeWithTag("search").performTextInput("bethel")
        waitForNoText("Way Maker")
        rule.onNodeWithText("Goodness of God").assertIsDisplayed()

        rule.onNodeWithTag("search").performTextClearance()
        rule.onNodeWithTag("search").performTextInput("zzz")
        waitForText("No songs match “zzz”.")
    }

    // --- ChordPro parsing & display ---

    @Test
    fun viewerShowsSectionsAndChartProblems() {
        seed(Song(title = "Broken", content = "{start_of_chorus}\n[G]Sing [Hx]out\n{end_of_chorus}\n{c: Slowly}"))
        launch()
        openSong("Broken")
        waitForText("1 problem in this chart")
        rule.onNodeWithText("Line 2: “Hx” isn't a chord").assertIsDisplayed()
        rule.onNode(chartText("Chorus")).assertIsDisplayed() // section label
        rule.onNode(chartText("Slowly")).assertIsDisplayed() // comment
        rule.onNode(chartText("Hx")).assertIsDisplayed() // bad chord still shown as written
    }

    @Test
    fun editorReportsProblemsLive() {
        launch()
        rule.onNodeWithTag("new_song").performClick()
        rule.onNodeWithTag("field_content").performTextInput("{start_of_verse}\n[G]la")
        waitForText("Line 1: {start_of_verse} is never closed; add {end_of_verse}")
        rule.onNodeWithTag("field_content").performTextInput("\n{end_of_verse}")
        rule.waitUntilDoesNotExist(hasTestTag("diagnostics"), 5_000)
    }

    // --- Transposition ---

    @Test
    fun transposeIsSavedPerSongAndRespellsChords() {
        val id = seed(Song(title = "Grace", key = "G", content = "[G]A [C]B [D/F#]C"))
        launch()
        openSong("Grace")
        rule.onNodeWithContentDescription("Transpose").performClick()
        repeat(3) { rule.onNodeWithContentDescription("Transpose up").performClick() }

        waitForText("Key Bb (G +3)")
        rule.onNode(chartText("Eb")).assertIsDisplayed()
        rule.onNode(chartText("F/A")).assertIsDisplayed()
        assertEquals(3, runBlocking { db.songDao().getById(id) }!!.transpose)

        rule.onNodeWithText("Sharp").performClick()
        waitForText("Key A# (G +3)")
        assertEquals(Accidentals.SHARPS, runBlocking { db.songDao().getById(id) }!!.accidentals)

        rule.onNodeWithText("Reset").performClick()
        waitForText("Key G")
        rule.onNode(chartText("D/F#")).assertIsDisplayed()
    }

    @Test
    fun defaultSpellingSettingApplies() {
        seed(Song(title = "Grace", key = "G", content = "[Bb]x"))
        settings.accidentals.value = Accidentals.SHARPS
        launch()
        openSong("Grace")
        rule.waitUntilAtLeastOneExists(chartText("A#"), 5_000)
    }

    // --- Import ---

    @Test
    fun importSingleChordProFileOpensReview() {
        launch()
        importFromMenu(file("Amazing_Grace.cho", "{artist: John Newton}\r\n{key: G}\r\n\r\n[G]Amazing [C]grace\r\n"))

        waitForText("Review import")
        rule.onNodeWithTag("field_title").assertTextContains("Amazing Grace")
        rule.onNodeWithTag("field_artist").assertTextContains("John Newton")
        rule.onNodeWithContentDescription("Save").performClick()

        waitForText("Key G")
        assertEquals("{artist: John Newton}\n{key: G}\n\n[G]Amazing [C]grace", allSongs().single().content)
    }

    @Test
    fun importPlainChordSheetIsConvertedForReview() {
        launch()
        importFromMenu(file("Grace.txt", "G       C\nAmazing grace\n"))
        waitForText("Review import")
        rule.onNodeWithText("This file had chords written above the lyrics", substring = true).assertIsDisplayed()
        rule.onNodeWithTag("field_content").assertTextContains("[G]Amazing [C]grace")
    }

    @Test
    fun importShowsProblemsFromTheFile() {
        launch()
        importFromMenu(file("Song.txt", "{key: H}\nla"))
        waitForText("Use a key like G, Bb or F#m")
    }

    @Test
    fun discardingImportAddsNothing() {
        launch()
        importFromMenu(file("Song.txt", "la"))
        waitForText("Review import")
        rule.onNodeWithContentDescription("Cancel").performClick()
        rule.onNodeWithText("The imported song won't be added.").assertIsDisplayed()
        rule.onNodeWithText("Discard").performClick()
        waitForText(EMPTY_LIBRARY)
        assertEquals(0, allSongs().size)
    }

    @Test
    fun importSeveralFilesAtOnce() {
        seed(Song(title = "Old", content = "[G]same"))
        launch()
        importFromMenu(
            file("One.txt", "[G]one"),
            file("Medley.cho", "{title: Two}\n[C]two\n{new_song}\n{title: Three}\n[D]three"),
            file("Old.txt", "[G]same"),
            file("Empty.txt", ""),
        )
        waitForText("Imported 3 songs. Skipped 1 already in your library. 1 file couldn't be read.")
        assertEquals(listOf("Old", "ONE", "THREE", "TWO"), allSongs().map { it.title })
    }

    @Test
    fun emptyFileShowsMessageInsteadOfEditor() {
        launch()
        importFromMenu(file("blank.txt", "  \n\n"))
        waitForText("That file is empty.")
        rule.onNodeWithText("Review import").assertDoesNotExist()
    }

    @Test
    fun cancellingPickerDoesNothing() {
        launch()
        importFromMenu()
        waitForText(EMPTY_LIBRARY)
        rule.onNodeWithText("Review import").assertDoesNotExist()
    }

    // --- Plain-text chord detection in the editor ---

    @Test
    fun editorOffersToConvertPastedChordSheet() {
        launch()
        rule.onNodeWithTag("new_song").performClick()
        rule.onNodeWithTag("field_content").performTextInput("G       C\nAmazing grace")
        rule.onNodeWithText("Convert to ChordPro").performScrollTo().performClick()
        rule.onNodeWithTag("field_content").assertTextContains("[G]Amazing [C]grace")
        rule.onNodeWithText("Convert to ChordPro").assertDoesNotExist()
    }

    // --- With / without lyrics ---

    @Test
    fun showLyricsCheckboxSwitchesToChordsOnly() {
        seed(Song(title = "Grace", content = "{c: Intro}\n[G]Amazing [C]grace\nNo chords here"))
        launch()
        openSong("Grace")
        rule.waitUntilAtLeastOneExists(chartText("Amazing"), 5_000)

        rule.onNodeWithTag("show_lyrics").performClick()
        rule.waitUntil(5_000) { !settings.showLyrics.value }
        rule.waitUntilDoesNotExist(chartText("Amazing"), 5_000)
        rule.onNode(chartText("No chords here")).assertDoesNotExist()
        // One bar per chord in 4/4: a dash per beat after each chord.
        rule.onNode(chartText("G----")).assertIsDisplayed()
        rule.onNode(chartText("C----")).assertIsDisplayed()
        rule.onNode(chartText("Intro")).assertIsDisplayed() // comments stay

        rule.onNodeWithTag("show_lyrics").performClick()
        rule.waitUntilAtLeastOneExists(chartText("Amazing"), 5_000)
        assertEquals(true, settings.showLyrics.value)
    }

    @Test
    fun chordsOnlySongLooksTheSameWithLyricsOff() {
        seed(Song(title = "Dash", timeSignature = "4/4", content = "{sov: Intro}\n[C]--[G/B]--[Am7]--[G]-- 2x\n{eov}"))
        launch()
        openSong("Dash")
        rule.waitUntilAtLeastOneExists(chartText("C--G/B--Am7--G-- 2x"), 5_000) // inline, as written

        rule.onNodeWithTag("show_lyrics").performClick()
        rule.waitUntil(5_000) { !settings.showLyrics.value }
        rule.onNode(chartText("C--G/B--Am7--G-- 2x")).assertIsDisplayed() // unchanged: no bar dashes added
        rule.onNode(chartText("C----")).assertDoesNotExist()
    }

    @Test
    fun bracketlessDashChartShowsAsIsWithLyricsOnAndOff() {
        // "A Pure Heart" as it was stored on the device.
        seed(Song(title = "A Pure Heart", key = "C", content = "Verse\r\nC----F----Dm--G--C--"))
        launch()
        openSong("A Pure Heart")
        rule.waitUntilAtLeastOneExists(chartText("C----F----Dm--G--C--"), 5_000)
        rule.onNode(chartText("Verse")).assertIsDisplayed()
        rule.onNodeWithText("Some chords here were recognised automatically", substring = true).assertIsDisplayed()

        rule.onNodeWithTag("show_lyrics").performClick()
        rule.waitUntil(5_000) { !settings.showLyrics.value }
        rule.onNode(chartText("C----F----Dm--G--C--")).assertIsDisplayed()
        rule.onNode(chartText("Verse")).assertIsDisplayed()
    }

    @Test
    fun instrumentalLineInALyricSongIsInlineWithLyricsOn() {
        seed(Song(title = "Mixed", content = "[G]--[C]--\n[G]Amazing [C]grace"))
        launch()
        openSong("Mixed")
        rule.waitUntilAtLeastOneExists(chartText("G--C--"), 5_000)
        rule.onNode(chartText("Amazing")).assertIsDisplayed()
    }

    // --- Metronome ---

    @Test
    fun metronomeUsesSongTempoAndSavesChanges() {
        val id = seed(Song(title = "Steady", timeSignature = "3/4", content = "[G]la"))
        launch()
        openSong("Steady")
        rule.onNodeWithContentDescription("Metronome").performClick()

        rule.onNodeWithTag("metronome_bpm").assertTextEquals("80 BPM") // no tempo set yet
        rule.onNodeWithContentDescription("Beat stopped of 3").assertExists() // 3/4: three beat dots
        rule.onNodeWithContentDescription("Faster").performClick()
        rule.waitUntil(5_000) { runBlocking { db.songDao().getById(id) }!!.tempo == 81 }
        rule.onNodeWithTag("metronome_bpm").assertTextEquals("81 BPM")
        rule.onNodeWithContentDescription("Start metronome").assertExists()
    }

    // --- Settings ---

    @Test
    fun themeModeResolvesAgainstSystemSetting() {
        assertEquals(true, useDarkTheme(ThemeMode.SYSTEM, systemDark = true))
        assertEquals(false, useDarkTheme(ThemeMode.SYSTEM, systemDark = false))
        assertEquals(false, useDarkTheme(ThemeMode.LIGHT, systemDark = true))
        assertEquals(true, useDarkTheme(ThemeMode.DARK, systemDark = false))
    }

    @Test
    fun settingsSaveThemeAndSpelling() {
        launch()
        rule.onNodeWithContentDescription("More options").performClick()
        rule.onNodeWithText("Settings").performClick()

        rule.onNodeWithText("Dark").performClick()
        rule.waitUntil(5_000) { settings.themeMode.value == ThemeMode.DARK }
        rule.onNodeWithText("Flats ♭").performScrollTo().performClick()
        rule.waitUntil(5_000) { settings.accidentals.value == Accidentals.FLATS }

        rule.onNodeWithText("Created by Christian Angeles").performScrollTo().assertIsDisplayed()

        rule.onNodeWithContentDescription("Back").performClick()
        rule.onNodeWithTag("new_song").assertIsDisplayed()
    }
}
