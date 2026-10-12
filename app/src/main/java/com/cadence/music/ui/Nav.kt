package com.cadence.music.ui

import android.net.Uri
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController

object Routes {
    const val HOME = "home"
    const val LIBRARY = "library"
    const val SEARCH = "search"
    const val SETTINGS = "settings"
    const val SONGS = "songs"
    const val ALBUMS = "albums"
    const val ARTISTS = "artists"
    const val GENRES = "genres"
    const val FOLDERS = "folders"
    const val PLAYLISTS = "playlists"
    const val FAVORITES = "favorites"
    const val MUSIC_FOLDERS = "settings/folders"
    const val ALBUM = "album/{id}"
    const val ARTIST = "artist/{name}"
    const val GENRE = "genre/{name}"
    const val FOLDER = "folder/{path}"
    const val PLAYLIST = "playlist/{id}"
    const val BROWSE = "browse/{category}"
}

/** Key under which a screen publishes its current title for the next screen's back button. */
const val TITLE_KEY = "title"

class Nav(private val controller: NavController) {
    fun back() {
        controller.popBackStack()
    }

    fun go(route: String) = controller.navigate(route)
    fun album(id: Long) = go("album/$id")
    fun artist(name: String) = go("artist/${Uri.encode(name)}")
    fun genre(name: String) = go("genre/${Uri.encode(name)}")
    fun folder(path: String) = go("folder/${Uri.encode(path)}")
    fun playlist(id: Long) = go("playlist/$id")
    fun browse(category: String) = go("browse/${Uri.encode(category)}")

    /** Pops back to this tab's first screen. Returns false when it was already there. */
    fun popToRoot(): Boolean {
        val root = controller.graph.startDestinationId
        if (controller.currentDestination?.id == root) return false
        return controller.popBackStack(root, inclusive = false)
    }

    /** The previous screen's title, shown on the back button the way iOS does. */
    fun backLabel(): String {
        val prev: NavBackStackEntry = controller.previousBackStackEntry ?: return "Back"
        prev.savedStateHandle.get<String>(TITLE_KEY)?.let { return it }
        return when (prev.destination.route) {
            Routes.HOME -> "Home"
            Routes.LIBRARY -> "Library"
            Routes.SEARCH -> "Search"
            Routes.SETTINGS -> "Settings"
            Routes.SONGS -> "Songs"
            Routes.ALBUMS -> "Albums"
            Routes.ARTISTS -> "Artists"
            Routes.GENRES -> "Genres"
            Routes.FOLDERS -> "Folders"
            Routes.PLAYLISTS -> "Playlists"
            Routes.FAVORITES -> "Favorites"
            Routes.ARTIST, Routes.GENRE -> prev.arguments?.getString("name") ?: "Back"
            Routes.FOLDER -> prev.arguments?.getString("path")?.substringAfterLast('/') ?: "Back"
            Routes.ALBUM -> "Album"
            Routes.PLAYLIST -> "Playlist"
            Routes.BROWSE -> prev.arguments?.getString("category") ?: "Library"
            else -> "Back"
        }
    }
}
