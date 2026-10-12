package com.cadence.music.data

import android.content.Context
import android.media.AudioFormat
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap

data class AudioQuality(
    val codec: String,
    val lossless: Boolean,
    val sampleRate: Int?,
    val bitDepth: Int?,
    val channels: Int?,
    val bitrate: Int?,
) {
    val isHiRes: Boolean get() = lossless && ((sampleRate ?: 0) > 48_000 || (bitDepth ?: 0) > 16)

    /** Apple-style badge text. */
    val badge: String
        get() = when {
            codec == "DSD" -> "DSD"
            isHiRes -> "Hi-Res Lossless"
            lossless -> "Lossless"
            bitrate != null && bitrate > 0 -> "$codec ${bitrate / 1000} kbps"
            else -> codec
        }

    /** e.g. "24-bit / 96 kHz FLAC". */
    val detail: String
        get() = buildList {
            if (bitDepth != null && bitDepth > 0) add("$bitDepth-bit")
            if (sampleRate != null && sampleRate > 0) add(formatRate(sampleRate))
        }.joinToString(" / ").let { if (it.isEmpty()) codec else "$it $codec" }

    companion object {
        fun formatRate(hz: Int): String {
            val khz = hz / 1000.0
            return if (khz % 1.0 == 0.0) "${khz.toInt()} kHz" else "%.1f kHz".format(khz)
        }
    }
}

/**
 * Works out codec, bit depth and sample rate. FLAC and WAV headers are parsed directly (fast and
 * exact); everything else goes through the platform extractor and metadata retriever.
 */
object AudioQualityReader {
    private val cache = ConcurrentHashMap<Long, AudioQuality>()

    fun cached(id: Long): AudioQuality? = cache[id]

    suspend fun read(context: Context, track: Track): AudioQuality = cache[track.id] ?: withContext(Dispatchers.IO) {
        val q = runCatching { probe(context, track) }.getOrNull() ?: fallback(track)
        cache[track.id] = q
        q
    }

    private fun fallback(track: Track) = AudioQuality(
        codec = codecFor(track, null),
        lossless = track.isLosslessFile,
        sampleRate = null, bitDepth = null, channels = null,
        bitrate = track.bitrate.takeIf { it > 0 },
    )

    private fun probe(context: Context, track: Track): AudioQuality {
        val ext = track.extension
        if (ext == "flac" || track.mimeType.contains("flac")) {
            context.contentResolver.openInputStream(track.uri)?.use { s -> parseFlac(s) }?.let { (rate, ch, bits) ->
                return AudioQuality("FLAC", true, rate, bits, ch, averageBitrate(track))
            }
        }
        if (ext == "wav" || track.mimeType.contains("wav")) {
            context.contentResolver.openInputStream(track.uri)?.use { s -> parseWav(s) }?.let { (rate, ch, bits) ->
                return AudioQuality("WAV", true, rate, bits, ch, averageBitrate(track))
            }
        }
        if (ext == "dsf" || ext == "dff") {
            return AudioQuality("DSD", true, null, 1, null, averageBitrate(track))
        }

        var mime: String? = null
        var rate: Int? = null
        var channels: Int? = null
        var bits: Int? = null
        var bitrate: Int? = null

        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(context, track.uri, null)
            for (i in 0 until extractor.trackCount) {
                val f = extractor.getTrackFormat(i)
                val m = f.getString(MediaFormat.KEY_MIME) ?: continue
                if (!m.startsWith("audio/")) continue
                mime = m
                if (f.containsKey(MediaFormat.KEY_SAMPLE_RATE)) rate = f.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                if (f.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) channels = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                if (f.containsKey(MediaFormat.KEY_BIT_RATE)) bitrate = f.getInteger(MediaFormat.KEY_BIT_RATE)
                if (f.containsKey(MediaFormat.KEY_PCM_ENCODING)) bits = pcmBits(f.getInteger(MediaFormat.KEY_PCM_ENCODING))
                break
            }
        } catch (_: Exception) {
        } finally {
            extractor.release()
        }

        if (Build.VERSION.SDK_INT >= 31 && (bits == null || rate == null)) {
            val r = MediaMetadataRetriever()
            try {
                r.setDataSource(context, track.uri)
                if (bits == null) bits = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITS_PER_SAMPLE)?.toIntOrNull()
                if (rate == null) rate = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE)?.toIntOrNull()
            } catch (_: Exception) {
            } finally {
                runCatching { r.release() }
            }
        }

        val codec = codecFor(track, mime)
        val lossless = codec in setOf("FLAC", "ALAC", "WAV", "AIFF", "APE", "WavPack", "DSD")
        if (!lossless) bits = null
        return AudioQuality(
            codec = codec,
            lossless = lossless,
            sampleRate = rate,
            bitDepth = bits,
            channels = channels,
            bitrate = bitrate ?: track.bitrate.takeIf { it > 0 } ?: averageBitrate(track),
        )
    }

    private fun averageBitrate(track: Track): Int? =
        if (track.durationMs > 0 && track.sizeBytes > 0) (track.sizeBytes * 8_000 / track.durationMs).toInt() else null

    private fun pcmBits(encoding: Int): Int? = when (encoding) {
        AudioFormat.ENCODING_PCM_8BIT -> 8
        AudioFormat.ENCODING_PCM_16BIT -> 16
        AudioFormat.ENCODING_PCM_FLOAT -> 32
        else -> if (Build.VERSION.SDK_INT >= 31) when (encoding) {
            AudioFormat.ENCODING_PCM_24BIT_PACKED -> 24
            AudioFormat.ENCODING_PCM_32BIT -> 32
            else -> null
        } else null
    }

    fun codecFor(track: Track, mime: String?): String {
        val m = (mime ?: track.mimeType).lowercase()
        return when {
            m.contains("flac") || track.extension == "flac" -> "FLAC"
            m.contains("alac") -> "ALAC"
            m.contains("wav") || track.extension == "wav" -> "WAV"
            m.contains("aiff") || track.extension == "aif" || track.extension == "aiff" -> "AIFF"
            m.contains("opus") || track.extension == "opus" -> "Opus"
            m.contains("vorbis") || track.extension == "ogg" -> "Vorbis"
            m.contains("mpeg") || track.extension == "mp3" -> "MP3"
            m.contains("ape") || track.extension == "ape" -> "APE"
            track.extension == "wv" -> "WavPack"
            track.extension == "dsf" || track.extension == "dff" -> "DSD"
            m.contains("wma") || track.extension == "wma" -> "WMA"
            m.contains("ac3") -> "Dolby Digital"
            m.contains("mp4a") || m.contains("aac") || track.extension == "m4a" || track.extension == "aac" -> "AAC"
            else -> track.extension.uppercase().ifEmpty { "Audio" }
        }
    }

    /** Returns (sampleRate, channels, bitsPerSample) from the FLAC STREAMINFO block. */
    private fun parseFlac(input: InputStream): Triple<Int, Int, Int>? {
        val s = input.buffered()
        var head = ByteArray(4)
        if (s.readFully(head) < 4) return null
        if (head[0] == 'I'.code.toByte() && head[1] == 'D'.code.toByte() && head[2] == '3'.code.toByte()) {
            // Skip a prepended ID3v2 tag.
            val rest = ByteArray(6)
            if (s.readFully(rest) < 6) return null
            val flags = rest[1].toInt()
            val size = ((rest[2].toInt() and 0x7f) shl 21) or ((rest[3].toInt() and 0x7f) shl 14) or
                ((rest[4].toInt() and 0x7f) shl 7) or (rest[5].toInt() and 0x7f)
            val skip = size + if (flags and 0x10 != 0) 10 else 0
            s.skipFully(skip.toLong())
            head = ByteArray(4)
            if (s.readFully(head) < 4) return null
        }
        if (String(head, Charsets.US_ASCII) != "fLaC") return null
        val blockHeader = ByteArray(4)
        if (s.readFully(blockHeader) < 4) return null
        if (blockHeader[0].toInt() and 0x7f != 0) return null // first block must be STREAMINFO
        val info = ByteArray(34)
        if (s.readFully(info) < 34) return null
        val b10 = info[10].toInt() and 0xff
        val b11 = info[11].toInt() and 0xff
        val b12 = info[12].toInt() and 0xff
        val b13 = info[13].toInt() and 0xff
        val sampleRate = (b10 shl 12) or (b11 shl 4) or (b12 shr 4)
        val channels = ((b12 shr 1) and 0x07) + 1
        val bits = (((b12 and 0x01) shl 4) or (b13 shr 4)) + 1
        return Triple(sampleRate, channels, bits)
    }

    /** Returns (sampleRate, channels, bitsPerSample) from a RIFF/WAVE fmt chunk. */
    private fun parseWav(input: InputStream): Triple<Int, Int, Int>? {
        val s = input.buffered()
        val riff = ByteArray(12)
        if (s.readFully(riff) < 12) return null
        if (String(riff, 0, 4, Charsets.US_ASCII) != "RIFF" || String(riff, 8, 4, Charsets.US_ASCII) != "WAVE") return null
        val chunk = ByteArray(8)
        repeat(64) {
            if (s.readFully(chunk) < 8) return null
            val id = String(chunk, 0, 4, Charsets.US_ASCII)
            val size = le32(chunk, 4)
            if (id == "fmt ") {
                val fmt = ByteArray(16)
                if (s.readFully(fmt) < 16) return null
                val channels = (fmt[2].toInt() and 0xff) or ((fmt[3].toInt() and 0xff) shl 8)
                val rate = le32(fmt, 4)
                val bits = (fmt[14].toInt() and 0xff) or ((fmt[15].toInt() and 0xff) shl 8)
                return Triple(rate, channels, bits)
            }
            s.skipFully(size.toLong() + (size and 1))
        }
        return null
    }

    private fun le32(b: ByteArray, off: Int): Int =
        (b[off].toInt() and 0xff) or ((b[off + 1].toInt() and 0xff) shl 8) or
            ((b[off + 2].toInt() and 0xff) shl 16) or ((b[off + 3].toInt() and 0xff) shl 24)

    private fun InputStream.readFully(buf: ByteArray): Int {
        var read = 0
        while (read < buf.size) {
            val n = read(buf, read, buf.size - read)
            if (n < 0) break
            read += n
        }
        return read
    }

    private fun InputStream.skipFully(n: Long) {
        var left = n
        while (left > 0) {
            val skipped = skip(left)
            if (skipped <= 0) {
                if (read() < 0) return
                left--
            } else left -= skipped
        }
    }
}
