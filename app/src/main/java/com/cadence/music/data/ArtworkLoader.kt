package com.cadence.music.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.os.Build
import android.util.LruCache
import android.util.Size
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Collections

/**
 * Loads album artwork: the system thumbnail for the file first, then the embedded picture, then a
 * cover.jpg / folder.jpg next to the file. Results are cached per album and size.
 */
object ArtworkLoader {
    const val SMALL = 256
    const val LARGE = 1024

    private val cache = object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 8 / 1024).toInt()) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount / 1024
    }
    private val missing = Collections.synchronizedSet(HashSet<String>())
    private val gate = Semaphore(4)

    private fun key(track: Track, size: Int) = "${track.albumId}:${track.folder}:$size"

    fun cached(track: Track, size: Int): Bitmap? = cache.get(key(track, size))

    fun clear() {
        cache.evictAll()
        missing.clear()
    }

    suspend fun load(context: Context, track: Track, size: Int): Bitmap? {
        val k = key(track, size)
        cache.get(k)?.let { return it }
        if (k in missing) return null
        return withContext(Dispatchers.IO) {
            gate.withPermit {
                cache.get(k) ?: decode(context, track, size).also {
                    if (it != null) cache.put(k, it) else missing += k
                }
            }
        }
    }

    private fun decode(context: Context, track: Track, size: Int): Bitmap? {
        if (Build.VERSION.SDK_INT >= 29) {
            runCatching { context.contentResolver.loadThumbnail(track.uri, Size(size, size), null) }
                .getOrNull()?.let { return it }
        } else {
            runCatching {
                context.contentResolver.openInputStream(albumArtUri(track.albumId))?.use { s ->
                    BitmapFactory.decodeStream(s)
                }
            }.getOrNull()?.let { return scaleDown(it, size) }
        }

        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, track.uri)
            retriever.embeddedPicture?.let { bytes -> return decodeBytes(bytes, size) }
        } catch (_: Exception) {
        } finally {
            runCatching { retriever.release() }
        }

        for (name in listOf("cover.jpg", "folder.jpg", "cover.png", "front.jpg", "Cover.jpg", "Folder.jpg", "AlbumArt.jpg")) {
            val f = File(track.folder, name)
            val bmp = runCatching { if (f.canRead()) decodeFile(f, size) else null }.getOrNull()
            if (bmp != null) return bmp
        }
        return null
    }

    private fun decodeBytes(bytes: ByteArray, size: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val opts = BitmapFactory.Options().apply { inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, size) }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)?.let { scaleDown(it, size) }
    }

    private fun decodeFile(file: File, size: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        val opts = BitmapFactory.Options().apply { inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, size) }
        return BitmapFactory.decodeFile(file.path, opts)?.let { scaleDown(it, size) }
    }

    private fun sampleSize(w: Int, h: Int, target: Int): Int {
        var s = 1
        while (w / (s * 2) >= target && h / (s * 2) >= target) s *= 2
        return s
    }

    private fun scaleDown(bmp: Bitmap, size: Int): Bitmap {
        val longest = maxOf(bmp.width, bmp.height)
        if (longest <= size) return bmp
        val scale = size.toFloat() / longest
        return Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt().coerceAtLeast(1), (bmp.height * scale).toInt().coerceAtLeast(1), true)
    }
}
