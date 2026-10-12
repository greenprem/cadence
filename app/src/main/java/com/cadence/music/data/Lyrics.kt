package com.cadence.music.data

import android.content.Context
import android.net.Uri
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.security.MessageDigest

data class LyricLine(val timeMs: Long, val text: String)

/** Lyrics for one song. [lines] is non-empty only for time-synced lyrics; [plain] holds unsynced text. */
data class Lyrics(val lines: List<LyricLine>, val plain: String?, val source: String) {
    val isSynced: Boolean get() = lines.isNotEmpty()

    companion object {
        /** Parses LRC ("[01:23.45] text"); returns null when the text has no timestamps. */
        fun fromText(text: String, source: String): Lyrics? {
            val clean = text.replace("\r\n", "\n").replace('\r', '\n').trim()
            if (clean.isEmpty()) return null
            val synced = parseLrc(clean)
            return if (synced.isNotEmpty()) Lyrics(synced, null, source) else Lyrics(emptyList(), clean, source)
        }

        private val TIME = Regex("""\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?]""")
        private val OFFSET = Regex("""\[offset:\s*([+-]?\d+)\s*]""", RegexOption.IGNORE_CASE)

        fun parseLrc(text: String): List<LyricLine> {
            val offset = OFFSET.find(text)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
            val out = ArrayList<LyricLine>()
            for (raw in text.lines()) {
                val stamps = TIME.findAll(raw).toList()
                if (stamps.isEmpty()) continue
                // Strip word-level <mm:ss.xx> timings (enhanced LRC) from the shown text.
                val words = raw.substring(stamps.last().range.last + 1)
                    .replace(Regex("""<\d{1,3}:\d{1,2}(?:[.:]\d{1,3})?>"""), "")
                    .trim()
                for (m in stamps) {
                    val min = m.groupValues[1].toLong()
                    val sec = m.groupValues[2].toLong()
                    val frac = m.groupValues[3]
                    val ms = when (frac.length) {
                        0 -> 0L
                        1 -> frac.toLong() * 100
                        2 -> frac.toLong() * 10
                        else -> frac.take(3).toLong()
                    }
                    out += LyricLine((min * 60_000 + sec * 1000 + ms - offset).coerceAtLeast(0), words)
                }
            }
            return out.sortedBy { it.timeMs }
        }
    }
}

/**
 * Finds lyrics for a song, best first:
 * 1. synced lyrics in the file's own tags, or a .lrc file next to it;
 * 2. LRCLIB (lrclib.net, free and open), synced if it has them;
 * 3. plain lyrics from the tags, then plain lyrics from LRCLIB.
 * Results (including "none found") are cached in memory and on disk.
 */
object LyricsRepository {
    private val memory = LruCache<Long, Result>(64)

    sealed interface Result {
        data class Found(val lyrics: Lyrics) : Result
        data object None : Result
    }

    fun cached(id: Long): Result? = memory.get(id)

    suspend fun load(context: Context, track: Track, allowOnline: Boolean): Result = memory.get(track.id) ?: withContext(Dispatchers.IO) {
        val local = runCatching { readEmbedded(context, track) }.getOrNull()
            ?: runCatching { readSidecar(track) }.getOrNull()
        val result = when {
            local?.isSynced == true -> Result.Found(local)
            else -> {
                val online = if (allowOnline) fetchOnlineCached(context, track) else null
                when {
                    online?.isSynced == true -> Result.Found(online)
                    local != null -> Result.Found(local)
                    online != null -> Result.Found(online)
                    else -> Result.None
                }
            }
        }
        // Don't remember a miss that was only a miss because we stayed offline.
        if (result !is Result.None || allowOnline) memory.put(track.id, result)
        result
    }

    // ---------------------------------------------------------------------------------------
    // LRCLIB
    // ---------------------------------------------------------------------------------------

    private fun cacheFile(context: Context, track: Track): File {
        val key = "${track.artist}\u0000${track.title}\u0000${track.album}\u0000${track.durationMs / 1000}"
        val hash = MessageDigest.getInstance("SHA-1").digest(key.toByteArray()).joinToString("") { "%02x".format(it) }
        return File(File(context.cacheDir, "lyrics").apply { mkdirs() }, "$hash.json")
    }

    private fun fetchOnlineCached(context: Context, track: Track): Lyrics? {
        val file = cacheFile(context, track)
        if (file.exists()) {
            val cached = runCatching { JSONObject(file.readText()) }.getOrNull()
            if (cached != null) {
                // Re-try misses after a week; hits are kept.
                val miss = cached.optBoolean("miss")
                if (!miss) return fromLrclib(cached)
                if (System.currentTimeMillis() - file.lastModified() < 7L * 24 * 3600 * 1000) return null
            }
        }
        val json = runCatching { queryLrclib(track) }.getOrElse { return null } // network error: try again next time
        runCatching { file.writeText((json ?: JSONObject().put("miss", true)).toString()) }
        return json?.let { fromLrclib(it) }
    }

    private fun fromLrclib(o: JSONObject): Lyrics? {
        if (o.optBoolean("instrumental")) return Lyrics(emptyList(), "♪ Instrumental", "LRCLIB")
        val synced = o.optString("syncedLyrics").takeIf { it.isNotBlank() && it != "null" }
        val plain = o.optString("plainLyrics").takeIf { it.isNotBlank() && it != "null" }
        synced?.let { Lyrics.fromText(it, "LRCLIB") }?.takeIf { it.isSynced }?.let { return it }
        return plain?.let { Lyrics(emptyList(), it.trim(), "LRCLIB") }
    }

    /** Returns the best LRCLIB record, or null when there is none. Throws on network errors. */
    private fun queryLrclib(track: Track): JSONObject? {
        val artist = track.artist.takeUnless { it.isBlank() || it == "Unknown Artist" || it == "<unknown>" } ?: track.primaryArtist
        val title = track.title
        val seconds = track.durationMs / 1000
        val exact = buildString {
            append("https://lrclib.net/api/get?artist_name=").append(enc(artist))
            append("&track_name=").append(enc(title))
            if (track.album.isNotBlank()) append("&album_name=").append(enc(track.album))
            if (seconds > 0) append("&duration=").append(seconds)
        }
        httpGet(exact)?.let { return JSONObject(it) }

        val search = "https://lrclib.net/api/search?track_name=${enc(title)}&artist_name=${enc(artist)}"
        val arr = httpGet(search)?.let { JSONArray(it) } ?: return null
        var best: JSONObject? = null
        var bestScore = Int.MIN_VALUE
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val d = o.optDouble("duration", 0.0).toLong()
            var score = 0
            if (seconds > 0 && d > 0) score -= (kotlin.math.abs(d - seconds) * 4).toInt()
            if (seconds > 0 && d > 0 && kotlin.math.abs(d - seconds) > 8) continue
            if (o.optString("syncedLyrics").let { it.isNotBlank() && it != "null" }) score += 20
            if (score > bestScore) {
                best = o
                bestScore = score
            }
        }
        return best
    }

    private fun enc(s: String) = Uri.encode(s.trim())

    /** Body on 200, null on 404; throws for anything else so the miss isn't cached. */
    private fun httpGet(url: String): String? {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 6000
            conn.readTimeout = 8000
            conn.setRequestProperty("User-Agent", "Cadence/1.2 (local Android music player)")
            conn.setRequestProperty("Accept", "application/json")
            return when (conn.responseCode) {
                200 -> conn.inputStream.bufferedReader().use { it.readText() }
                404 -> null
                else -> error("HTTP ${conn.responseCode}")
            }
        } finally {
            conn.disconnect()
        }
    }

    // ---------------------------------------------------------------------------------------
    // Local: tags inside the file, or a .lrc next to it
    // ---------------------------------------------------------------------------------------

    private fun readSidecar(track: Track): Lyrics? {
        val base = track.path.substringBeforeLast('.', track.path)
        for (ext in listOf(".lrc", ".LRC", ".txt")) {
            val f = File(base + ext)
            if (f.canRead() && f.length() in 1..512_000) return Lyrics.fromText(f.readText(), "File")
        }
        return null
    }

    private fun readEmbedded(context: Context, track: Track): Lyrics? {
        val pfd = context.contentResolver.openFileDescriptor(track.uri, "r") ?: return null
        return pfd.use {
            FileInputStream(it.fileDescriptor).use { stream ->
                val ch = stream.channel
                val head = ByteArray(4)
                if (readAt(ch, 0, head) < 4) return null
                val text = when {
                    head.ascii(0, 4) == "fLaC" -> flacLyrics(ch)
                    head.ascii(0, 3) == "ID3" -> id3Lyrics(ch)
                    else -> {
                        val box = ByteArray(8)
                        if (readAt(ch, 0, box) == 8 && box.ascii(4, 4) == "ftyp") mp4Lyrics(ch) else null
                    }
                }
                text?.let { t -> Lyrics.fromText(t, "Embedded") }
            }
        }
    }

    private fun readAt(ch: FileChannel, pos: Long, dst: ByteArray): Int {
        val buf = ByteBuffer.wrap(dst)
        var p = pos
        while (buf.hasRemaining()) {
            val n = ch.read(buf, p)
            if (n <= 0) break
            p += n
        }
        return buf.position()
    }

    private fun ByteArray.ascii(off: Int, len: Int) = String(this, off, len, Charsets.ISO_8859_1)

    /** FLAC: VORBIS_COMMENT block, keys LYRICS / UNSYNCEDLYRICS / SYNCEDLYRICS. */
    private fun flacLyrics(ch: FileChannel): String? {
        var pos = 4L
        val hdr = ByteArray(4)
        while (true) {
            if (readAt(ch, pos, hdr) < 4) return null
            val last = hdr[0].toInt() and 0x80 != 0
            val type = hdr[0].toInt() and 0x7F
            val len = ((hdr[1].toInt() and 0xFF) shl 16) or ((hdr[2].toInt() and 0xFF) shl 8) or (hdr[3].toInt() and 0xFF)
            if (type == 4 && len in 8..16_000_000) {
                val block = ByteArray(len)
                if (readAt(ch, pos + 4, block) < len) return null
                return vorbisLyrics(block)
            }
            pos += 4 + len
            if (last) return null
        }
    }

    private fun vorbisLyrics(b: ByteArray): String? {
        val bb = ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN)
        val vendor = bb.int
        if (vendor < 0 || vendor > bb.remaining()) return null
        bb.position(bb.position() + vendor)
        val count = bb.int
        val found = HashMap<String, String>()
        repeat(count.coerceIn(0, 10_000)) {
            if (bb.remaining() < 4) return@repeat
            val n = bb.int
            if (n < 0 || n > bb.remaining()) return@repeat
            val s = String(b, bb.position(), n, Charsets.UTF_8)
            bb.position(bb.position() + n)
            val eq = s.indexOf('=')
            if (eq > 0) found[s.substring(0, eq).uppercase()] = s.substring(eq + 1)
        }
        return found["SYNCEDLYRICS"] ?: found["LYRICS"] ?: found["UNSYNCEDLYRICS"] ?: found["UNSYNCED LYRICS"]
    }

    /** MP3: ID3v2.3/2.4 USLT frame. */
    private fun id3Lyrics(ch: FileChannel): String? {
        val h = ByteArray(10)
        if (readAt(ch, 0, h) < 10) return null
        val major = h[3].toInt()
        val size = syncsafe(h, 6)
        if (major < 3 || size <= 0 || size > 32_000_000) return null
        val tag = ByteArray(size)
        readAt(ch, 10, tag)
        var p = 0
        if (h[5].toInt() and 0x40 != 0) { // extended header
            p += if (major == 4) syncsafe(tag, 0) else (be32(tag, 0) + 4)
        }
        while (p + 10 <= tag.size) {
            val id = tag.ascii(p, 4)
            if (id[0] == '\u0000') break
            val len = if (major == 4) syncsafe(tag, p + 4) else be32(tag, p + 4)
            if (len <= 0 || p + 10 + len > tag.size) break
            if (id == "USLT" && len > 4) {
                val enc = tag[p + 10].toInt()
                // Skip encoding (1) + language (3), then the null-terminated content descriptor.
                var q = p + 14
                val end = p + 10 + len
                q = skipTerminated(tag, q, end, enc)
                return decode(tag, q, end - q, enc).trim('\u0000').takeIf { it.isNotBlank() }
            }
            p += 10 + len
        }
        return null
    }

    private fun skipTerminated(b: ByteArray, start: Int, end: Int, enc: Int): Int {
        var q = start
        if (enc == 1 || enc == 2) {
            while (q + 1 < end && !(b[q].toInt() == 0 && b[q + 1].toInt() == 0)) q += 2
            return (q + 2).coerceAtMost(end)
        }
        while (q < end && b[q].toInt() != 0) q++
        return (q + 1).coerceAtMost(end)
    }

    private fun decode(b: ByteArray, off: Int, len: Int, enc: Int): String = when (enc) {
        1 -> String(b, off, len, Charsets.UTF_16)
        2 -> String(b, off, len, Charsets.UTF_16BE)
        3 -> String(b, off, len, Charsets.UTF_8)
        else -> String(b, off, len, Charsets.ISO_8859_1)
    }

    private fun syncsafe(b: ByteArray, o: Int) =
        ((b[o].toInt() and 0x7F) shl 21) or ((b[o + 1].toInt() and 0x7F) shl 14) or
            ((b[o + 2].toInt() and 0x7F) shl 7) or (b[o + 3].toInt() and 0x7F)

    private fun be32(b: ByteArray, o: Int) =
        ((b[o].toInt() and 0xFF) shl 24) or ((b[o + 1].toInt() and 0xFF) shl 16) or
            ((b[o + 2].toInt() and 0xFF) shl 8) or (b[o + 3].toInt() and 0xFF)

    /** MP4 / M4A (AAC and ALAC): moov › udta › meta › ilst › ©lyr › data. */
    private fun mp4Lyrics(ch: FileChannel): String? {
        val moov = findBox(ch, 0, ch.size(), "moov") ?: return null
        val udta = findBox(ch, moov.first, moov.second, "udta") ?: return null
        val meta = findBox(ch, udta.first, udta.second, "meta") ?: return null
        val ilst = findBox(ch, meta.first + 4, meta.second, "ilst") ?: return null // meta is a full box
        val lyr = findBox(ch, ilst.first, ilst.second, "©lyr") ?: return null
        val data = findBox(ch, lyr.first, lyr.second, "data") ?: return null
        val len = (data.second - data.first - 8).toInt()
        if (len <= 0 || len > 4_000_000) return null
        val bytes = ByteArray(len)
        readAt(ch, data.first + 8, bytes) // skip type + locale
        return String(bytes, Charsets.UTF_8)
    }

    /** Finds a child box in [start, end); returns its payload range. */
    private fun findBox(ch: FileChannel, start: Long, end: Long, type: String): Pair<Long, Long>? {
        var pos = start
        val h = ByteArray(16)
        while (pos + 8 <= end) {
            if (readAt(ch, pos, h) < 8) return null
            var size = be32(h, 0).toLong() and 0xFFFFFFFFL
            val name = h.ascii(4, 4)
            var header = 8L
            if (size == 1L) {
                size = ByteBuffer.wrap(h, 8, 8).long
                header = 16
            } else if (size == 0L) {
                size = end - pos
            }
            if (size < header) return null
            if (name == type) return (pos + header) to (pos + size).coerceAtMost(end)
            pos += size
        }
        return null
    }
}
