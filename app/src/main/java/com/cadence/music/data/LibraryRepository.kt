package com.cadence.music.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

private data class FilterSpec(val folders: Set<String>, val useAll: Boolean, val minDurationSec: Int)

/** Applies the music-folder and minimum-length filters and groups what's left into a [Library]. */
class LibraryRepository(scanner: MediaScanner, settings: SettingsRepository, scope: CoroutineScope) {

    private val spec = settings.settings
        .map { FilterSpec(it.includedFolders, it.useAllFolders, it.minDurationSec) }
        .distinctUntilChanged()

    val library: StateFlow<Library> = combine(scanner.tracks, spec) { tracks, spec ->
        build(tracks.filter { matches(it, spec) })
    }.flowOn(Dispatchers.Default).stateIn(scope, SharingStarted.Eagerly, Library.EMPTY)

    /** Every folder on the device that holds audio, for the folder picker. */
    val allFolders: StateFlow<List<FolderInfo>> = scanner.tracks.map { tracks ->
        tracks.filter { it.folder.isNotEmpty() }
            .groupingBy { it.folder }.eachCount()
            .map { (path, count) -> FolderInfo(path, count) }
            .sortedBy { it.path.lowercase() }
    }.flowOn(Dispatchers.Default).stateIn(scope, SharingStarted.Eagerly, emptyList())

    private fun matches(t: Track, spec: FilterSpec): Boolean {
        if (spec.minDurationSec > 0 && t.durationMs in 1 until spec.minDurationSec * 1000L) return false
        return if (spec.useAll) t.isMusic else isInFolders(t.path, spec.folders)
    }

    private fun build(tracks: List<Track>): Library {
        val trackOrder = compareBy<Track>({ it.discNumber }, { it.trackNumber }, { sortKey(it.title) })

        val albums = tracks.groupBy { it.albumId }.map { (id, list) ->
            val sorted = list.sortedWith(trackOrder)
            Album(
                id = id,
                title = sorted.first().album,
                artist = mostCommon(sorted.map { it.albumArtist }.filter { it.isNotBlank() })
                    ?: mostCommon(sorted.map { it.artist }) ?: "Unknown Artist",
                year = sorted.maxOf { it.year },
                genre = mostCommon(sorted.map { it.genre }.filter { it.isNotBlank() }).orEmpty(),
                dateAdded = sorted.maxOf { it.dateAdded },
                tracks = sorted,
            )
        }.sortedBy { sortKey(it.title) }

        val artists = albums.groupBy { it.artist }.map { (name, artistAlbums) ->
            val byYear = artistAlbums.sortedByDescending { it.year }
            Artist(name, byYear, byYear.flatMap { it.tracks })
        }.sortedBy { sortKey(it.name) }

        val genres = tracks.filter { it.genre.isNotBlank() }
            .groupBy { it.genre }
            .map { (name, list) -> Genre(name, list.sortedBy { sortKey(it.title) }) }
            .sortedBy { it.name.lowercase() }

        val folders = tracks.groupBy { it.folder }
            .map { (path, list) -> Folder(path, list.sortedWith(compareBy({ it.discNumber }, { it.trackNumber }, { it.fileName.lowercase() }))) }
            .sortedBy { it.name.lowercase() }

        return Library(
            tracks = tracks.sortedBy { sortKey(it.title) },
            albums = albums,
            artists = artists,
            genres = genres,
            folders = folders,
        )
    }

    private fun mostCommon(values: List<String>): String? =
        values.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key

    companion object {
        fun isInFolders(path: String, folders: Set<String>): Boolean =
            folders.any { f -> path.startsWith(f.trimEnd('/') + "/") }
    }
}
