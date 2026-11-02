package com.chordx.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.chordx.app.AppContainer
import com.chordx.app.data.ThemeMode
import com.chordx.app.ui.devotion.DevotionScreen
import com.chordx.app.ui.devotion.DevotionViewModel
import com.chordx.app.ui.devotion.DevotionWeekScreen
import com.chordx.app.ui.devotion.DevotionWeekViewModel
import com.chordx.app.ui.notes.NoteEditScreen
import com.chordx.app.ui.notes.NoteEditViewModel
import com.chordx.app.ui.notes.NoteListScreen
import com.chordx.app.ui.notes.NoteListViewModel
import com.chordx.app.ui.setlist.AddSongsScreen
import com.chordx.app.ui.setlist.PerformanceScreen
import com.chordx.app.ui.setlist.PerformanceViewModel
import com.chordx.app.ui.setlist.SetlistDetailScreen
import com.chordx.app.ui.setlist.SetlistListScreen
import com.chordx.app.ui.setlist.SetlistListViewModel
import com.chordx.app.ui.setlist.SetlistViewModel
import com.chordx.app.ui.song.SongEditScreen
import com.chordx.app.ui.song.SongEditViewModel
import com.chordx.app.ui.song.SongListScreen
import com.chordx.app.ui.song.SongListViewModel
import com.chordx.app.ui.song.SongViewModel
import com.chordx.app.ui.song.SongViewScreen
import com.chordx.app.ui.song.importSongs
import com.chordx.app.ui.theme.ChordXTheme

private object Routes {
    const val SONGS = "songs"
    const val SETLISTS = "setlists"
    const val SETTINGS = "settings"
    const val NOTES = "notes"
    const val DEVOTION = "devotion"
    const val DEVOTION_WEEK = "devotion/week"
    const val NOTE = "note?id={id}"
    const val SONG = "song/{id}"
    const val EDIT = "edit?id={id}"
    const val SETLIST = "setlist/{id}"
    const val ADD_SONGS = "setlist/{id}/add"
    const val PERFORM = "perform/{id}?start={start}"
    fun note(id: Long? = null) = if (id == null) "note" else "note?id=$id"
    fun song(id: Long) = "song/$id"
    fun edit(id: Long? = null) = if (id == null) "edit" else "edit?id=$id"
    fun setlist(id: Long) = "setlist/$id"
    fun addSongs(setlistId: Long) = "setlist/$setlistId/add"
    fun perform(setlistId: Long, start: Int) = "perform/$setlistId?start=$start"
}

@Composable
fun ChordXRoot(container: AppContainer) {
    val themeMode by container.settings.themeMode.collectAsStateWithLifecycle(initialValue = ThemeMode.SYSTEM)
    val dark = useDarkTheme(themeMode, systemDark = isSystemInDarkTheme())
    ChordXTheme(darkTheme = dark) {
        ChordXNavHost(container)
    }
}

internal fun useDarkTheme(mode: ThemeMode, systemDark: Boolean): Boolean = when (mode) {
    ThemeMode.SYSTEM -> systemDark
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

/** Songs / Setlists tabs, shown on the two top-level screens. */
@Composable
private fun TabBar(nav: NavHostController, current: String) {
    NavigationBar {
        listOf(
            Triple(Routes.SONGS, "Songs", Icons.Default.MusicNote),
            Triple(Routes.SETLISTS, "Setlists", Icons.AutoMirrored.Filled.QueueMusic),
            Triple(Routes.DEVOTION, "Devotion", Icons.AutoMirrored.Filled.MenuBook),
            Triple(Routes.NOTES, "Notes", Icons.Default.Edit),
        ).forEach { (route, label, icon) ->
            NavigationBarItem(
                selected = current == route,
                onClick = {
                    if (current != route) {
                        nav.navigate(route) {
                            popUpTo(Routes.SONGS) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                },
                icon = { Icon(icon, contentDescription = null) },
                label = { Text(label) },
            )
        }
    }
}

@Composable
private fun ChordXNavHost(container: AppContainer) {
    val nav = rememberNavController()
    val idArg = navArgument("id") { type = NavType.LongType }

    NavHost(nav, startDestination = Routes.SONGS) {
        composable(Routes.SONGS) {
            SongListScreen(
                viewModel = viewModel(factory = SongListViewModel.factory(container.songDao)),
                onOpenSong = { nav.navigate(Routes.song(it)) },
                onNewSong = { nav.navigate(Routes.edit()) },
                onReviewImport = { song ->
                    container.offerImport(song)
                    nav.navigate(Routes.edit())
                },
                onBatchImport = { songs, failed -> importSongs(container.songDao, songs, failed) },
                onOpenSettings = { nav.navigate(Routes.SETTINGS) },
                bottomBar = { TabBar(nav, Routes.SONGS) },
            )
        }
        composable(Routes.SETLISTS) {
            SetlistListScreen(
                viewModel = viewModel(factory = SetlistListViewModel.factory(container.setlistDao)),
                onOpenSetlist = { nav.navigate(Routes.setlist(it)) },
                onOpenSettings = { nav.navigate(Routes.SETTINGS) },
                bottomBar = { TabBar(nav, Routes.SETLISTS) },
            )
        }
        composable(Routes.DEVOTION) {
            DevotionScreen(
                viewModel = viewModel(factory = DevotionViewModel.factory(container.devotionDao)),
                onOpenWeek = { nav.navigate(Routes.DEVOTION_WEEK) },
                bottomBar = { TabBar(nav, Routes.DEVOTION) },
            )
        }
        composable(Routes.DEVOTION_WEEK) {
            DevotionWeekScreen(
                viewModel = viewModel(factory = DevotionWeekViewModel.factory(container.devotionDao)),
                onBack = { nav.popBackStack() },
            )
        }
        composable(Routes.NOTES) {
            NoteListScreen(
                viewModel = viewModel(factory = NoteListViewModel.factory(container.noteDao)),
                onOpenNote = { nav.navigate(Routes.note(it)) },
                onNewNote = { nav.navigate(Routes.note()) },
                bottomBar = { TabBar(nav, Routes.NOTES) },
            )
        }
        composable(Routes.NOTE, arguments = listOf(navArgument("id") { type = NavType.LongType; defaultValue = -1L })) { entry ->
            val id = entry.arguments!!.getLong("id").takeIf { it > 0 }
            NoteEditScreen(
                viewModel = viewModel(factory = NoteEditViewModel.factory(container.noteDao, id)),
                onDone = { nav.popBackStack() },
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(container.settings, container.songDao, container.database, onBack = { nav.popBackStack() })
        }
        composable(Routes.SONG, arguments = listOf(idArg)) { entry ->
            val id = entry.arguments!!.getLong("id")
            SongViewScreen(
                viewModel = viewModel(factory = SongViewModel.factory(container.songDao, container.settings, id)),
                onBack = { nav.popBackStack() },
                onEdit = { nav.navigate(Routes.edit(id)) },
                onDeleted = { nav.popBackStack(Routes.SONGS, inclusive = false) },
            )
        }
        composable(Routes.EDIT, arguments = listOf(navArgument("id") { type = NavType.LongType; defaultValue = -1L })) { entry ->
            val id = entry.arguments!!.getLong("id").takeIf { it > 0 }
            SongEditScreen(
                viewModel = viewModel(factory = SongEditViewModel.factory(container.songDao, id, container::takeImport)),
                onSaved = { savedId ->
                    if (id == null) {
                        // A new song opens in the viewer, with the list (not the editor) behind it.
                        nav.navigate(Routes.song(savedId)) { popUpTo(Routes.SONGS) }
                    } else {
                        nav.popBackStack()
                    }
                },
                onCancel = { nav.popBackStack() },
            )
        }
        composable(Routes.SETLIST, arguments = listOf(idArg)) { entry ->
            val id = entry.arguments!!.getLong("id")
            SetlistDetailScreen(
                viewModel = viewModel(factory = SetlistViewModel.factory(container.setlistDao, container.settings, id)),
                onBack = { nav.popBackStack() },
                onAddSongs = { nav.navigate(Routes.addSongs(id)) },
                onPerform = { start -> nav.navigate(Routes.perform(id, start)) },
                onDeleted = { nav.popBackStack() },
            )
        }
        composable(Routes.ADD_SONGS, arguments = listOf(idArg)) { entry ->
            val id = entry.arguments!!.getLong("id")
            val setlistViewModel: SetlistViewModel = viewModel(factory = SetlistViewModel.factory(container.setlistDao, container.settings, id))
            AddSongsScreen(
                songsViewModel = viewModel(factory = SongListViewModel.factory(container.songDao)),
                onBack = { nav.popBackStack() },
                onAdd = { ids -> setlistViewModel.addSongs(ids) { nav.popBackStack() } },
            )
        }
        composable(
            Routes.PERFORM,
            arguments = listOf(idArg, navArgument("start") { type = NavType.IntType; defaultValue = 0 }),
        ) { entry ->
            val id = entry.arguments!!.getLong("id")
            PerformanceScreen(
                viewModel = viewModel(factory = PerformanceViewModel.factory(container.setlistDao, container.songDao, container.settings, id)),
                startIndex = entry.arguments!!.getInt("start"),
                onClose = { nav.popBackStack() },
            )
        }
    }
}
