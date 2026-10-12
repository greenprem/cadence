package com.cadence.music.data

import android.content.ContentUris
import android.net.Uri

data class Track(
    val id: Long,
    val uri: Uri,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val albumArtist: String,
    val genre: String,
    val durationMs: Long,
    val trackNumber: Int,
    val discNumber: Int,
    val year: Int,
    val path: String,
    val folder: String,
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val dateAdded: Long,
    val bitrate: Int,
    val isMusic: Boolean,
) {
    /** The artist an album is filed under (album artist when tagged, otherwise the track artist). */
    val primaryArtist: String get() = albumArtist.ifBlank { artist }

    val extension: String get() = fileName.substringAfterLast('.', "").lowercase()

    /** Lossless judged from the container alone; ALAC inside .m4a is confirmed later by [AudioQualityReader]. */
    val isLosslessFile: Boolean
        get() = extension in LOSSLESS_EXTENSIONS ||
            mimeType.contains("flac") || mimeType.contains("wav") || mimeType.contains("aiff")

    companion object {
        val LOSSLESS_EXTENSIONS = setOf("flac", "wav", "aif", "aiff", "ape", "wv", "dsf", "dff", "alac")
    }
}

data class Album(
    val id: Long,
    val title: String,
    val artist: String,
    val year: Int,
    val genre: String,
    val dateAdded: Long,
    val tracks: List<Track>,
) {
    val cover: Track get() = tracks.first()
    val durationMs: Long get() = tracks.sumOf { it.durationMs }
    val isLossless: Boolean get() = tracks.all { it.isLosslessFile }
    val hasMultipleDiscs: Boolean get() = tracks.map { it.discNumber }.distinct().size > 1
}

data class Artist(
    val name: String,
    val albums: List<Album>,
    val tracks: List<Track>,
)

data class Genre(val name: String, val tracks: List<Track>)

data class Folder(val path: String, val tracks: List<Track>) {
    val name: String get() = path.substringAfterLast('/').ifBlank { path }
}

/** A directory that holds audio, used by the folder picker (counts are before any filtering). */
data class FolderInfo(val path: String, val trackCount: Int)

data class Playlist(
    val id: Long,
    val name: String,
    val trackIds: List<Long>,
    val createdAt: Long,
)

data class Library(
    val tracks: List<Track>,
    val albums: List<Album>,
    val artists: List<Artist>,
    val genres: List<Genre>,
    val folders: List<Folder>,
) {
    val byId: Map<Long, Track> = tracks.associateBy { it.id }
    val albumsById: Map<Long, Album> = albums.associateBy { it.id }
    val artistsByName: Map<String, Artist> = artists.associateBy { it.name }

    companion object {
        val EMPTY = Library(emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
    }
}

private val ALBUM_ART_BASE: Uri = Uri.parse("content://media/external/audio/albumart")

fun albumArtUri(albumId: Long): Uri = ContentUris.withAppendedId(ALBUM_ART_BASE, albumId)

/** Sort key that ignores case and a leading "The ", the way Apple Music orders names. */
fun sortKey(s: String): String {
    val lower = s.trim().lowercase()
    return if (lower.startsWith("the ")) lower.removePrefix("the ") else lower
}

fun formatDuration(ms: Long): String {
    if (ms <= 0) return "0:00"
    val totalSec = ms / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

fun formatTotalDuration(ms: Long): String {
    val minutes = ms / 60000
    return if (minutes >= 60) {
        val h = minutes / 60
        val m = minutes % 60
        if (m == 0L) "$h hr" else "$h hr $m min"
    } else "$minutes min"
}

fun songCount(n: Int): String = if (n == 1) "1 song" else "$n songs"

/** Shows a storage path the way people think of it: "Internal storage/Music/FLAC". */
fun prettyPath(path: String): String = when {
    path.startsWith("/storage/emulated/0") -> "Internal storage" + path.removePrefix("/storage/emulated/0")
    path.startsWith("/storage/") -> {
        val rest = path.removePrefix("/storage/")
        "SD card" + rest.substringAfter('/', "").let { if (it.isEmpty()) "" else "/$it" }
    }
    else -> path
}
