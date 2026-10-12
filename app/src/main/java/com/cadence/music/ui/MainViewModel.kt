package com.cadence.music.ui

import android.app.Application
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cadence.music.CadenceApp
import com.cadence.music.data.AppSettings
import com.cadence.music.data.ArtworkLoader
import com.cadence.music.data.LibraryRepository
import com.cadence.music.data.Track
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as CadenceApp

    val library = app.library.library
    val allFolders = app.library.allFolders
    val scanning = app.scanner.scanning
    val scanLoaded = app.scanner.loaded
    val userData = app.userData.state
    val playerState = app.player.state
    val sleepTimer = app.player.sleepTimer
    val player = app.player
    val user = app.userData

    val settings: StateFlow<AppSettings?> = app.settings.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun onPermissionGranted() = app.scanner.start()

    fun <T> set(key: Preferences.Key<T>, value: T) {
        viewModelScope.launch { app.settings.set(key, value) }
    }

    fun setFolders(folders: Set<String>, useAll: Boolean) {
        viewModelScope.launch { app.settings.setFolders(folders, useAll) }
    }

    fun rescan() {
        val s = settings.value
        val folders = if (s == null || s.useAllFolders || s.includedFolders.isEmpty()) {
            allFolders.value.map { it.path }
        } else s.includedFolders.toList()
        ArtworkLoader.clear()
        app.scanner.rescan(folders)
    }

    /** Tracks under a folder from the full, unfiltered scan (for the folder picker preview). */
    fun isIncluded(track: Track): Boolean {
        val s = settings.value ?: return false
        return if (s.useAllFolders) track.isMusic else LibraryRepository.isInFolders(track.path, s.includedFolders)
    }
}
