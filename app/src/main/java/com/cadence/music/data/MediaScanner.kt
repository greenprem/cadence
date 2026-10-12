package com.cadence.music.data

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.database.Cursor
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

val audioPermission: String
    get() = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO
    else Manifest.permission.READ_EXTERNAL_STORAGE

fun hasAudioPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, audioPermission) == PackageManager.PERMISSION_GRANTED

/**
 * Reads every audio file Android's media index knows about. Folder filtering happens later in
 * [LibraryRepository] so the folder picker can still show folders that are currently excluded.
 */
class MediaScanner(private val context: Context, private val scope: CoroutineScope) {

    private val _tracks = MutableStateFlow<List<Track>>(emptyList())
    val tracks: StateFlow<List<Track>> = _tracks.asStateFlow()

    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    private var observerRegistered = false
    private var pending: Job? = null

    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) = scheduleRefresh()
    }

    fun start() {
        if (!hasAudioPermission(context)) return
        if (!observerRegistered) {
            context.contentResolver.registerContentObserver(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, true, observer
            )
            observerRegistered = true
        }
        refresh()
    }

    private fun scheduleRefresh() {
        pending?.cancel()
        pending = scope.launch {
            delay(1500)
            refresh()
        }
    }

    fun refresh() {
        scope.launch {
            _scanning.value = true
            try {
                _tracks.value = withContext(Dispatchers.IO) { query() }
            } catch (e: Exception) {
                Log.e(TAG, "Media query failed", e)
            } finally {
                _loaded.value = true
                _scanning.value = false
            }
        }
    }

    /** Asks Android to re-index the given folders, for files copied in without a media scan. */
    fun rescan(folders: Collection<String>) {
        scope.launch {
            _scanning.value = true
            val paths = withContext(Dispatchers.IO) {
                val out = ArrayList<String>()
                for (folder in folders) {
                    out += folder
                    runCatching {
                        File(folder).walkTopDown()
                            .onEnter { !it.name.startsWith(".") }
                            .filter { it.isFile && it.extension.lowercase() in AUDIO_EXTENSIONS }
                            .take(20_000)
                            .forEach { out += it.absolutePath }
                    }
                }
                out
            }
            if (paths.isEmpty()) {
                refresh()
                return@launch
            }
            MediaScannerConnection.scanFile(context, paths.toTypedArray(), null) { _, _ -> scheduleRefresh() }
            delay(4000)
            refresh()
        }
    }

    private fun query(): List<Track> {
        val modern = Build.VERSION.SDK_INT >= 30
        val projection = buildList {
            add(MediaStore.Audio.Media._ID)
            add(MediaStore.Audio.Media.TITLE)
            add(MediaStore.Audio.Media.ARTIST)
            add(MediaStore.Audio.Media.ALBUM)
            add(MediaStore.Audio.Media.ALBUM_ID)
            add(MediaStore.Audio.Media.DURATION)
            add(MediaStore.Audio.Media.TRACK)
            add(MediaStore.Audio.Media.YEAR)
            add(MediaStore.Audio.Media.DATA)
            add(MediaStore.Audio.Media.DISPLAY_NAME)
            add(MediaStore.Audio.Media.MIME_TYPE)
            add(MediaStore.Audio.Media.SIZE)
            add(MediaStore.Audio.Media.DATE_ADDED)
            add(MediaStore.Audio.Media.IS_MUSIC)
            if (modern) {
                add("album_artist")
                add("genre")
                add("bitrate")
                add("disc_number")
            }
        }.toTypedArray()

        val result = ArrayList<Track>()
        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, projection, null, null, null
        )?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val albumIdCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val durationCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val trackCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
            val yearCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)
            val dataCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            val nameCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
            val mimeCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
            val sizeCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
            val addedCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
            val isMusicCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.IS_MUSIC)
            val albumArtistCol = c.getColumnIndex("album_artist")
            val genreCol = c.getColumnIndex("genre")
            val bitrateCol = c.getColumnIndex("bitrate")
            val discCol = c.getColumnIndex("disc_number")

            while (c.moveToNext()) {
                val id = c.getLong(idCol)
                val path = c.getStringOrNull(dataCol).orEmpty()
                val fileName = c.getStringOrNull(nameCol) ?: path.substringAfterLast('/')
                val rawTrack = c.getIntOrZero(trackCol)
                val discFromTrack = if (rawTrack >= 1000) rawTrack / 1000 else 0
                val discTag = if (discCol >= 0) parseLeadingInt(c.getStringOrNull(discCol)) else 0
                result += Track(
                    id = id,
                    uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id),
                    title = c.getStringOrNull(titleCol)?.takeIf { it.isNotBlank() }
                        ?: fileName.substringBeforeLast('.'),
                    artist = cleanUnknown(c.getStringOrNull(artistCol)) ?: "Unknown Artist",
                    album = cleanUnknown(c.getStringOrNull(albumCol)) ?: "Unknown Album",
                    albumId = c.getLong(albumIdCol),
                    albumArtist = if (albumArtistCol >= 0) cleanUnknown(c.getStringOrNull(albumArtistCol)).orEmpty() else "",
                    genre = if (genreCol >= 0) c.getStringOrNull(genreCol)?.trim().orEmpty() else "",
                    durationMs = c.getLong(durationCol),
                    trackNumber = rawTrack % 1000,
                    discNumber = maxOf(discTag, discFromTrack, 1),
                    year = c.getIntOrZero(yearCol),
                    path = path,
                    folder = path.substringBeforeLast('/', ""),
                    fileName = fileName,
                    mimeType = c.getStringOrNull(mimeCol).orEmpty(),
                    sizeBytes = c.getLong(sizeCol),
                    dateAdded = c.getLong(addedCol),
                    bitrate = if (bitrateCol >= 0) c.getIntOrZero(bitrateCol) else 0,
                    isMusic = c.getIntOrZero(isMusicCol) != 0,
                )
            }
        }
        return result
    }

    companion object {
        private const val TAG = "MediaScanner"

        val AUDIO_EXTENSIONS = setOf(
            "flac", "wav", "aif", "aiff", "alac", "m4a", "mp4", "aac", "mp3", "ogg", "oga", "opus",
            "wma", "ape", "wv", "mka", "webm", "amr", "3gp", "ac3", "eac3", "dsf", "dff", "mid", "midi"
        )

        private fun cleanUnknown(s: String?): String? =
            s?.trim()?.takeIf { it.isNotEmpty() && it != "<unknown>" }

        private fun parseLeadingInt(s: String?): Int =
            s?.trim()?.takeWhile { it.isDigit() }?.toIntOrNull() ?: 0

        private fun Cursor.getStringOrNull(index: Int): String? = if (isNull(index)) null else getString(index)

        private fun Cursor.getIntOrZero(index: Int): Int = if (isNull(index)) 0 else getInt(index)
    }
}
