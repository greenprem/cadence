package com.cadence.music.playback

import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.cadence.music.data.LibraryRepository
import com.cadence.music.data.SettingsRepository
import com.cadence.music.data.Track
import com.cadence.music.data.UserDataRepository
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.random.Random

data class QueueEntry(val index: Int, val trackId: Long)

data class PlayerState(
    val connected: Boolean = false,
    val currentId: Long? = null,
    val currentIndex: Int = C.INDEX_UNSET,
    val isPlaying: Boolean = false,
    val playWhenReady: Boolean = false,
    val buffering: Boolean = false,
    val shuffle: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val durationMs: Long = 0L,
    /** Songs after the current one, in the order they will play (shuffle-aware). */
    val upNext: List<QueueEntry> = emptyList(),
    val queueSize: Int = 0,
)

sealed interface SleepTimer {
    data object Off : SleepTimer
    data class At(val endsAtMillis: Long) : SleepTimer
    data object EndOfSong : SleepTimer
}

/** App-wide connection to [PlaybackService]; the UI only ever talks to the player through this. */
class PlayerConnection(
    private val context: Context,
    private val scope: CoroutineScope,
    private val userData: UserDataRepository,
    private val library: LibraryRepository,
    private val settings: SettingsRepository,
) {
    private var future: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private val pending = ArrayList<(MediaController) -> Unit>()

    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    private val _sleepTimer = MutableStateFlow<SleepTimer>(SleepTimer.Off)
    val sleepTimer: StateFlow<SleepTimer> = _sleepTimer.asStateFlow()
    private var sleepJob: Job? = null

    private var lastRecordedIndexKey: String? = null

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = publish(player)

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO && _sleepTimer.value == SleepTimer.EndOfSong) {
                controller?.pause()
                _sleepTimer.value = SleepTimer.Off
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (!isPlaying) return
            val c = controller ?: return
            val id = c.currentMediaItem?.mediaId?.toLongOrNull() ?: return
            // Count a play once per queue position, the first time it actually starts playing.
            val key = "${c.currentMediaItemIndex}:$id"
            if (key != lastRecordedIndexKey) {
                lastRecordedIndexKey = key
                userData.recordPlay(id)
            }
        }
    }

    fun connect() {
        if (future != null) return
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val f = MediaController.Builder(context, token)
            .setListener(object : MediaController.Listener {
                override fun onDisconnected(controller: MediaController) {
                    // The service stopped (for example after being swiped away while paused).
                    // Forget this controller so the next action starts a fresh session.
                    if (this@PlayerConnection.controller === controller) {
                        this@PlayerConnection.controller = null
                        future = null
                        _state.value = PlayerState()
                    }
                }
            })
            .buildAsync()
        future = f
        f.addListener({
            val c = runCatching { f.get() }.getOrNull() ?: run {
                future = null
                return@addListener
            }
            controller = c
            c.addListener(listener)
            publish(c)
            pending.forEach { it(c) }
            pending.clear()
            restoreQueueIfEmpty(c)
        }, ContextCompat.getMainExecutor(context))
    }

    private fun withController(action: (MediaController) -> Unit) {
        val c = controller?.takeIf { it.isConnected }
        if (c != null) action(c) else {
            if (controller != null) {
                controller = null
                future = null
            }
            pending += action
            connect()
        }
    }

    private fun restoreQueueIfEmpty(c: MediaController) {
        if (c.mediaItemCount > 0) return
        scope.launch {
            if (!settings.settings.first().resumeQueue) return@launch
            val saved = userData.loadQueue() ?: return@launch
            val lib = library.library.first { it.tracks.isNotEmpty() }
            if (c.mediaItemCount > 0) return@launch
            val savedCurrent = saved.trackIds.getOrNull(saved.index)
            val tracks = saved.trackIds.mapNotNull { lib.byId[it] }
            if (tracks.isEmpty()) return@launch
            val index = tracks.indexOfFirst { it.id == savedCurrent }.takeIf { it >= 0 } ?: 0
            c.repeatMode = saved.repeatMode
            c.setMediaItems(tracks.map { it.toMediaItem() }, index, if (index == saved.index) saved.positionMs else 0L)
            if (saved.shuffle) c.shuffleModeEnabled = true
            c.prepare()
        }
    }

    private fun publish(p: Player) {
        val count = p.mediaItemCount
        val current = p.currentMediaItemIndex
        val upNext = ArrayList<QueueEntry>()
        if (count > 0 && current != C.INDEX_UNSET) {
            val timeline = p.currentTimeline
            var i = timeline.getNextWindowIndex(current, Player.REPEAT_MODE_OFF, p.shuffleModeEnabled)
            while (i != C.INDEX_UNSET && upNext.size < 500) {
                p.getMediaItemAt(i).mediaId.toLongOrNull()?.let { upNext += QueueEntry(i, it) }
                i = timeline.getNextWindowIndex(i, Player.REPEAT_MODE_OFF, p.shuffleModeEnabled)
            }
        }
        _state.value = PlayerState(
            connected = true,
            currentId = p.currentMediaItem?.mediaId?.toLongOrNull(),
            currentIndex = current,
            isPlaying = p.isPlaying,
            playWhenReady = p.playWhenReady,
            buffering = p.playbackState == Player.STATE_BUFFERING,
            shuffle = p.shuffleModeEnabled,
            repeatMode = p.repeatMode,
            durationMs = p.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: 0L,
            upNext = upNext,
            queueSize = count,
        )
    }

    val positionMs: Long get() = controller?.currentPosition ?: 0L

    fun playTracks(tracks: List<Track>, startIndex: Int = 0, shuffle: Boolean = false) {
        if (tracks.isEmpty()) return
        withController { c ->
            val items = tracks.map { it.toMediaItem() }
            val start = if (shuffle) Random.nextInt(items.size) else startIndex.coerceIn(0, items.lastIndex)
            if (!shuffle) c.shuffleModeEnabled = false
            c.setMediaItems(items, start, 0L)
            if (shuffle) c.shuffleModeEnabled = true
            c.prepare()
            c.play()
        }
    }

    fun playNext(tracks: List<Track>) = withController { c ->
        if (c.mediaItemCount == 0) return@withController playTracks(tracks)
        c.addMediaItems(c.currentMediaItemIndex + 1, tracks.map { it.toMediaItem() })
    }

    fun playLast(tracks: List<Track>) = withController { c ->
        if (c.mediaItemCount == 0) return@withController playTracks(tracks)
        c.addMediaItems(tracks.map { it.toMediaItem() })
    }

    fun togglePlay() = withController { c ->
        if (c.isPlaying) c.pause() else {
            if (c.playbackState == Player.STATE_IDLE) c.prepare()
            if (c.playbackState == Player.STATE_ENDED) c.seekToDefaultPosition(0)
            c.play()
        }
    }

    fun next() = withController { it.seekToNext() }

    /** Restarts the song when more than 3 seconds in, otherwise goes back, like Apple Music. */
    fun previous() = withController { c ->
        if (c.currentPosition > 3000 || !c.hasPreviousMediaItem()) c.seekTo(0) else c.seekToPreviousMediaItem()
    }

    fun seekTo(ms: Long) = withController { it.seekTo(ms) }

    /** Always goes to the previous song (swipe gestures), unlike [previous] which restarts first. */
    fun previousTrack() = withController { c ->
        if (c.hasPreviousMediaItem()) c.seekToPreviousMediaItem() else c.seekTo(0)
    }

    fun skipToIndex(index: Int) = withController { c ->
        c.seekToDefaultPosition(index)
        c.play()
    }

    fun removeAt(index: Int) = withController { it.removeMediaItem(index) }

    fun moveToNext(index: Int) = withController { c ->
        val target = c.currentMediaItemIndex + if (index > c.currentMediaItemIndex) 1 else 0
        c.moveMediaItem(index, target)
    }

    fun clearUpNext() = withController { c ->
        val current = c.currentMediaItemIndex
        if (current + 1 < c.mediaItemCount) c.removeMediaItems(current + 1, c.mediaItemCount)
    }

    fun toggleShuffle() = withController { it.shuffleModeEnabled = !it.shuffleModeEnabled }

    fun cycleRepeat() = withController { c ->
        c.repeatMode = when (c.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    fun setSleepTimer(minutes: Int?) {
        sleepJob?.cancel()
        when (minutes) {
            null -> _sleepTimer.value = SleepTimer.Off
            -1 -> _sleepTimer.value = SleepTimer.EndOfSong
            else -> {
                val end = System.currentTimeMillis() + minutes * 60_000L
                _sleepTimer.value = SleepTimer.At(end)
                sleepJob = scope.launch {
                    delay(minutes * 60_000L)
                    controller?.pause()
                    _sleepTimer.value = SleepTimer.Off
                }
            }
        }
    }
}
