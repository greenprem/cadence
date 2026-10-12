package com.cadence.music.playback

import android.app.PendingIntent
import android.content.ContentUris
import android.content.Intent
import android.media.audiofx.AudioEffect
import android.provider.MediaStore
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ShuffleOrder
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.cadence.music.CadenceApp
import com.cadence.music.MainActivity
import com.cadence.music.data.AppSettings
import com.cadence.music.data.DecoderMode
import com.cadence.music.data.SavedQueue
import com.cadence.music.data.Track
import com.cadence.music.data.albumArtUri
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.random.Random

fun Track.toMediaItem(): MediaItem = MediaItem.Builder()
    .setMediaId(id.toString())
    .setUri(uri)
    .setMediaMetadata(
        MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .setAlbumTitle(album)
            .setAlbumArtist(primaryArtist)
            .setArtworkUri(albumArtUri(albumId))
            .setTrackNumber(trackNumber)
            .setDiscNumber(discNumber)
            .setIsBrowsable(false)
            .setIsPlayable(true)
            .build()
    )
    .build()

@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {

    private var session: MediaSession? = null
    private lateinit var exo: ExoPlayer
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        val app = application as CadenceApp
        // Decoder choice and float output are fixed when the player is built, so read them up front.
        val initial = runBlocking { app.settings.settings.first() }

        val renderers = DefaultRenderersFactory(this)
            .setExtensionRendererMode(
                when (initial.decoderMode) {
                    DecoderMode.PLATFORM_ONLY -> DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF
                    DecoderMode.FALLBACK -> DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON
                    DecoderMode.PREFER_FFMPEG -> DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER
                }
            )
            // 32-bit float output keeps 24-bit FLAC/ALAC at full resolution instead of dithering to 16-bit.
            .setEnableAudioFloatOutput(initial.hiResOutput)

        exo = ExoPlayer.Builder(this, renderers)
            .setAudioAttributes(musicAttributes, initial.handleAudioFocus)
            .setHandleAudioBecomingNoisy(initial.pauseOnDisconnect)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
        applySettings(initial)

        exo.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) = saveQueue()
            override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) = saveQueue()
            override fun onIsPlayingChanged(isPlaying: Boolean) = saveQueue()
            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) = saveQueue()
            override fun onRepeatModeChanged(repeatMode: Int) = saveQueue()
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                audioSession = audioSessionId
                sendBroadcast(Intent(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION).apply {
                    putExtra(AudioEffect.EXTRA_AUDIO_SESSION, audioSessionId)
                    putExtra(AudioEffect.EXTRA_PACKAGE_NAME, packageName)
                    putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
                })
            }
        })
        audioSession = exo.audioSessionId

        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        session = MediaSession.Builder(this, ShuffleAwarePlayer(exo))
            .setSessionActivity(openApp)
            .setCallback(object : MediaSession.Callback {
                override fun onAddMediaItems(
                    mediaSession: MediaSession,
                    controller: MediaSession.ControllerInfo,
                    mediaItems: MutableList<MediaItem>,
                ): ListenableFuture<MutableList<MediaItem>> {
                    // Items sent from a controller may arrive without their URI; rebuild it from the id.
                    val resolved = mediaItems.mapNotNull { item ->
                        if (item.localConfiguration != null) item
                        else item.mediaId.toLongOrNull()?.let { id ->
                            item.buildUpon()
                                .setUri(ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id))
                                .build()
                        }
                    }.toMutableList()
                    return Futures.immediateFuture(resolved)
                }
            })
            .build()

        scope.launch {
            app.settings.settings.collect { applySettings(it) }
        }
    }

    private fun applySettings(s: AppSettings) {
        exo.setAudioAttributes(musicAttributes, s.handleAudioFocus)
        exo.setHandleAudioBecomingNoisy(s.pauseOnDisconnect)
        // Silence skipping only works on 16-bit PCM, so it is off while float output is in use.
        exo.skipSilenceEnabled = s.skipSilence && !s.hiResOutput
        val offload = TrackSelectionParameters.AudioOffloadPreferences.Builder()
            .setAudioOffloadMode(
                if (s.audioOffload) TrackSelectionParameters.AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_ENABLED
                else TrackSelectionParameters.AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_DISABLED
            )
            .build()
        exo.trackSelectionParameters = exo.trackSelectionParameters.buildUpon()
            .setAudioOffloadPreferences(offload)
            .build()
    }

    private fun saveQueue() {
        val count = exo.mediaItemCount
        if (count == 0) return
        val ids = (0 until count).mapNotNull { exo.getMediaItemAt(it).mediaId.toLongOrNull() }
        (application as CadenceApp).userData.saveQueue(
            SavedQueue(ids, exo.currentMediaItemIndex, exo.currentPosition, exo.shuffleModeEnabled, exo.repeatMode)
        )
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = session?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        saveQueue()
        scope.cancel()
        sendBroadcast(Intent(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION).apply {
            putExtra(AudioEffect.EXTRA_AUDIO_SESSION, exo.audioSessionId)
            putExtra(AudioEffect.EXTRA_PACKAGE_NAME, packageName)
        })
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }

    /**
     * ExoPlayer picks a random spot for the current song when shuffle turns on, which can make the
     * rest of the queue end early. This wrapper keeps the current song first in the shuffle order,
     * and makes "Play Next" really play next while shuffling.
     */
    private class ShuffleAwarePlayer(private val exo: ExoPlayer) : ForwardingPlayer(exo) {

        override fun setShuffleModeEnabled(shuffleModeEnabled: Boolean) {
            if (shuffleModeEnabled && exo.mediaItemCount > 0) {
                exo.setShuffleOrder(orderStartingWith(exo.currentMediaItemIndex, exo.mediaItemCount))
            }
            super.setShuffleModeEnabled(shuffleModeEnabled)
        }

        override fun setMediaItems(mediaItems: MutableList<MediaItem>, startIndex: Int, startPositionMs: Long) {
            super.setMediaItems(mediaItems, startIndex, startPositionMs)
            reorderIfShuffling()
        }

        override fun setMediaItems(mediaItems: MutableList<MediaItem>, resetPosition: Boolean) {
            super.setMediaItems(mediaItems, resetPosition)
            reorderIfShuffling()
        }

        override fun setMediaItems(mediaItems: MutableList<MediaItem>) {
            super.setMediaItems(mediaItems)
            reorderIfShuffling()
        }

        override fun addMediaItem(mediaItem: MediaItem) = insert(exo.mediaItemCount, listOf(mediaItem))
        override fun addMediaItem(index: Int, mediaItem: MediaItem) = insert(index, listOf(mediaItem))
        override fun addMediaItems(mediaItems: MutableList<MediaItem>) = insert(exo.mediaItemCount, mediaItems)
        override fun addMediaItems(index: Int, mediaItems: MutableList<MediaItem>) = insert(index, mediaItems)

        private fun insert(index: Int, items: List<MediaItem>) {
            val at = index.coerceIn(0, exo.mediaItemCount)
            val playNext = at == exo.currentMediaItemIndex + 1
            exo.addMediaItems(at, items)
            if (!exo.shuffleModeEnabled || items.isEmpty()) return
            val inserted = (at until at + items.size).toSet()
            val order = currentShuffleOrder().filterNot { it in inserted }.toMutableList()
            val insertPos = if (playNext) order.indexOf(exo.currentMediaItemIndex) + 1 else order.size
            order.addAll(insertPos.coerceIn(0, order.size), inserted.sorted())
            exo.setShuffleOrder(ShuffleOrder.DefaultShuffleOrder(order.toIntArray(), Random.nextLong()))
        }

        private fun reorderIfShuffling() {
            if (exo.shuffleModeEnabled && exo.mediaItemCount > 0) {
                exo.setShuffleOrder(orderStartingWith(exo.currentMediaItemIndex, exo.mediaItemCount))
            }
        }

        private fun currentShuffleOrder(): List<Int> {
            val timeline = exo.currentTimeline
            if (timeline.isEmpty) return emptyList()
            val out = ArrayList<Int>(timeline.windowCount)
            var i = timeline.getFirstWindowIndex(true)
            while (i != C.INDEX_UNSET && out.size < timeline.windowCount) {
                out += i
                i = timeline.getNextWindowIndex(i, Player.REPEAT_MODE_OFF, true)
            }
            return out
        }

        private fun orderStartingWith(first: Int, count: Int): ShuffleOrder.DefaultShuffleOrder {
            val rest = (0 until count).filter { it != first }.shuffled()
            val order = if (first in 0 until count) listOf(first) + rest else rest
            return ShuffleOrder.DefaultShuffleOrder(order.toIntArray(), Random.nextLong())
        }
    }

    companion object {
        /** Audio session of the running player, for the system equalizer. */
        @Volatile
        var audioSession: Int = C.AUDIO_SESSION_ID_UNSET

        private val musicAttributes: AudioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()
    }
}
