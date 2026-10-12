@file:OptIn(ExperimentalFoundationApi::class)

package com.cadence.music.ui.nowplaying

import android.content.Context
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.rounded.FormatQuote
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.toSize
import com.cadence.music.data.Lyrics
import com.cadence.music.data.LyricsRepository
import android.content.Intent
import android.media.AudioManager
import android.media.audiofx.AudioEffect
import android.os.Build
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VolumeDown
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.ColorUtils
import androidx.media3.common.Player
import androidx.palette.graphics.Palette
import com.cadence.music.data.AppSettings
import com.cadence.music.data.ArtworkLoader
import com.cadence.music.data.AudioQuality
import com.cadence.music.data.AudioQualityReader
import com.cadence.music.data.Library
import com.cadence.music.data.Track
import com.cadence.music.data.formatDuration
import com.cadence.music.data.prettyPath
import com.cadence.music.playback.PlaybackService
import com.cadence.music.playback.PlayerConnection
import com.cadence.music.playback.PlayerState
import com.cadence.music.playback.SleepTimer
import com.cadence.music.ui.components.ActionMenu
import com.cadence.music.ui.components.Artwork
import com.cadence.music.ui.components.ArtworkImage
import com.cadence.music.ui.components.LocalTrackActions
import com.cadence.music.ui.components.MenuAction
import com.cadence.music.ui.components.rememberArtwork
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private val Fallback = Color(0xFF3A3A3C) to Color(0xFF121214)

private enum class Panel { ARTWORK, LYRICS, QUEUE }

@Composable
fun NowPlayingScreen(
    track: Track,
    player: PlayerState,
    library: Library,
    settings: AppSettings,
    sleepTimer: SleepTimer,
    connection: PlayerConnection,
    sheet: PlayerSheetState,
    onOpenAlbum: (Long) -> Unit,
    onOpenArtist: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Stays composed while hidden so opening is instant; polling only runs while it's on screen.
    val active = sheet.isVisible
    val context = LocalContext.current
    val actions = LocalTrackActions.current
    val bitmap = rememberArtwork(track, ArtworkLoader.LARGE)

    val palette by produceState(Fallback, bitmap, settings.dynamicBackground) {
        value = if (bitmap == null || !settings.dynamicBackground) Fallback else withContext(Dispatchers.Default) {
            val p = Palette.from(bitmap).maximumColorCount(16).generate()
            val top = p.getVibrantColor(p.getDominantColor(0xFF3A3A3C.toInt()))
            val bottom = p.getDarkMutedColor(p.getDarkVibrantColor(0xFF121214.toInt()))
            Color(ColorUtils.blendARGB(top, 0xFF000000.toInt(), 0.35f)) to
                Color(ColorUtils.blendARGB(bottom, 0xFF000000.toInt(), 0.55f))
        }
    }
    val topColor by animateColorAsState(palette.first, tween(600), label = "top")
    val bottomColor by animateColorAsState(palette.second, tween(600), label = "bottom")

    var panel by remember { mutableStateOf(Panel.ARTWORK) }
    SideEffect { sheet.morphEnabled = panel == Panel.ARTWORK }
    var showInfo by remember { mutableStateOf(false) }
    var showSleep by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }

    val quality by produceState(AudioQualityReader.cached(track.id), track.id) {
        value = AudioQualityReader.read(context, track)
    }

    // Lets the artwork report where it sits relative to the sheet (unaffected by the sheet's slide).
    val sheetCoords = remember { arrayOfNulls<LayoutCoordinates>(1) }
    val artSwipe = rememberSwipeToSkipState()

    Box(
        modifier
            .fillMaxSize()
            .onGloballyPositioned { sheetCoords[0] = it }
            .background(Brush.verticalGradient(listOf(topColor, bottomColor)))
    ) {
        if (bitmap != null && settings.dynamicBackground && Build.VERSION.SDK_INT >= 31) {
            val image = remember(bitmap) { bitmap.asImageBitmap() }
            Image(
                image, null,
                modifier = Modifier.fillMaxSize().blur(90.dp).graphicsLayer { alpha = 0.55f },
                contentScale = ContentScale.Crop,
            )
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.25f)))
        }

        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 28.dp)
        ) {
            // Grabber: drag down anywhere on the top area to close.
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(28.dp)
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onDragEnd = { sheet.settle(fromOpen = true) },
                            onDragCancel = { sheet.settle(fromOpen = true) },
                        ) { _, dy -> sheet.dragBy(dy) }
                    }
                    .clickable { sheet.close() },
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(width = 38.dp, height = 5.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.45f)))
            }

            AnimatedContent(
                targetState = panel,
                transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(180)) },
                modifier = Modifier.weight(1f),
                label = "panel",
            ) { shown ->
                when (shown) {
                    Panel.QUEUE -> QueuePanel(track, player, library, connection, bitmap)
                    Panel.LYRICS -> LyricsPanel(track, connection, settings.onlineLyrics, active, bitmap)
                    Panel.ARTWORK -> Column(
                        Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectVerticalDragGestures(
                                    onDragEnd = { sheet.settle(fromOpen = true) },
                                    onDragCancel = { sheet.settle(fromOpen = true) },
                                ) { _, dy -> sheet.dragBy(dy) }
                            }
                    ) {
                        Spacer(Modifier.weight(1f))
                        val playingScale by animateFloatAsState(
                            if (player.isPlaying || !settings.shrinkArtworkWhenPaused) 1f else 0.78f,
                            spring(dampingRatio = 0.62f, stiffness = 260f),
                            label = "artScale",
                        )
                        SideEffect { sheet.fullArtScale = playingScale }
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .onGloballyPositioned { art ->
                                    val root = sheetCoords[0] ?: return@onGloballyPositioned
                                    if (!root.isAttached || !art.isAttached) return@onGloballyPositioned
                                    sheet.fullArt = Rect(root.localPositionOf(art, Offset.Zero), art.size.toSize())
                                }
                                // Tap the artwork to play / pause; swipe it to change song.
                                .pointerInput(Unit) { detectTapGestures(onTap = { connection.togglePlay() }) }
                                .swipeToSkipUpdated(artSwipe, onNext = { connection.next() }, onPrevious = { connection.previousTrack() }),
                            contentAlignment = Alignment.Center,
                        ) {
                            ArtworkImage(
                                bitmap,
                                Modifier
                                    .fillMaxSize()
                                    .swipeOffset(artSwipe)
                                    .graphicsLayer {
                                        scaleX = playingScale
                                        scaleY = playingScale
                                        // Hidden while the flying copy is on screen during open / close.
                                        if (sheet.morphing()) alpha = 0f
                                    }
                                    .shadow(if (player.isPlaying) 28.dp else 12.dp, RoundedCornerShape(10.dp)),
                                RoundedCornerShape(10.dp),
                            )
                        }
                        Spacer(Modifier.weight(1f))
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // Title, artist and the round Favorite / More buttons.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        track.title, color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Bold,
                        maxLines = 1, modifier = Modifier.basicMarquee(),
                    )
                    Text(
                        track.artist, color = Color.White.copy(alpha = 0.65f), fontSize = 19.sp, maxLines = 1,
                        modifier = Modifier
                            .basicMarquee()
                            .clickable { onOpenArtist(track.primaryArtist) },
                    )
                }
                Spacer(Modifier.width(10.dp))
                val favorite = track.id in actions.favorites
                RoundGlassButton(if (favorite) Icons.Rounded.Star else Icons.Rounded.StarBorder, "Favorite") {
                    actions.toggleFavorite(track)
                }
                Spacer(Modifier.width(10.dp))
                Box {
                    RoundGlassButton(Icons.Rounded.MoreHoriz, "More") { menu = true }
                    ActionMenu(
                        menu, { menu = false },
                        listOf(
                            MenuAction("Add to Playlist…", Icons.Rounded.PlaylistAdd) { actions.addToPlaylist(listOf(track)) },
                            MenuAction("Go to Album", Icons.Rounded.Album) { onOpenAlbum(track.albumId) },
                            MenuAction("Go to Artist", Icons.Rounded.Mic) { onOpenArtist(track.primaryArtist) },
                            MenuAction("Sleep Timer", Icons.Rounded.Timer) { showSleep = true },
                            MenuAction("Song Info", Icons.Rounded.Info) { showInfo = true },
                        ),
                    )
                }
            }

            Spacer(Modifier.height(18.dp))
            Scrubber(track, player, connection, quality, settings.showQualityBadges, active) { showInfo = true }
            Spacer(Modifier.height(12.dp))

            // Transport controls.
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TransportButton(Icons.Rounded.FastRewind, "Previous", 46.dp) { connection.previous() }
                TransportButton(
                    if (player.playWhenReady) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    if (player.playWhenReady) "Pause" else "Play",
                    64.dp,
                ) { connection.togglePlay() }
                TransportButton(Icons.Rounded.FastForward, "Next", 46.dp) { connection.next() }
            }

            if (settings.showVolumeBar) {
                Spacer(Modifier.height(16.dp))
                VolumeRow(active)
            }
            Spacer(Modifier.height(14.dp))

            // Bottom row: sleep timer, equalizer, queue.
            Row(
                Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BottomToggle(Icons.Rounded.FormatQuote, "Lyrics", active = panel == Panel.LYRICS) {
                    panel = if (panel == Panel.LYRICS) Panel.ARTWORK else Panel.LYRICS
                }
                BottomToggle(Icons.Rounded.Timer, "Sleep timer", active = sleepTimer != SleepTimer.Off) { showSleep = true }
                BottomToggle(Icons.Rounded.Tune, "Equalizer", active = false) { openEqualizer(context) }
                BottomToggle(Icons.Rounded.QueueMusic, "Playing Next", active = panel == Panel.QUEUE) {
                    panel = if (panel == Panel.QUEUE) Panel.ARTWORK else Panel.QUEUE
                }
            }
        }
    }

    if (showInfo) SongInfoDialog(track, quality) { showInfo = false }
    if (showSleep) {
        SleepTimerDialog(sleepTimer, onPick = { minutes ->
            connection.setSleepTimer(minutes)
            showSleep = false
            val msg = when (minutes) {
                null -> "Sleep timer off"
                -1 -> "Music will stop at the end of this song"
                else -> "Music will stop in $minutes minutes"
            }
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }, onDismiss = { showSleep = false })
    }

}

@Composable
private fun Scrubber(
    track: Track,
    player: PlayerState,
    connection: PlayerConnection,
    quality: AudioQuality?,
    showBadge: Boolean,
    active: Boolean,
    onBadgeClick: () -> Unit,
) {
    var position by remember { mutableLongStateOf(connection.positionMs) }
    var scrubbing by remember { mutableStateOf(false) }
    var scrubFraction by remember { mutableFloatStateOf(0f) }
    val duration = player.durationMs.takeIf { it > 0 } ?: track.durationMs

    LaunchedEffect(track.id, active) {
        while (active) {
            if (!scrubbing) position = connection.positionMs
            delay(200)
        }
    }

    val shown = if (scrubbing) (scrubFraction * duration).toLong() else position
    ThinSlider(
        value = if (duration > 0) (position.toFloat() / duration) else 0f,
        onValueChange = { scrubFraction = it },
        onDragStateChange = { scrubbing = it },
        onValueChangeFinished = { f ->
            val ms = (f * duration).toLong()
            position = ms
            connection.seekTo(ms)
        },
    )
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(formatDuration(shown), color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp, modifier = Modifier.width(64.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            if (showBadge && quality != null) {
                Row(
                    Modifier
                        .clip(RoundedCornerShape(5.dp))
                        .background(Color.White.copy(alpha = 0.14f))
                        .clickable(onClick = onBadgeClick)
                        .padding(horizontal = 7.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (quality.lossless) {
                        Icon(Icons.Rounded.GraphicEq, null, tint = Color.White.copy(alpha = 0.75f), modifier = Modifier.size(12.dp))
                        Spacer(Modifier.width(3.dp))
                    }
                    Text(quality.badge, color = Color.White.copy(alpha = 0.75f), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        Text(
            "-" + formatDuration((duration - shown).coerceAtLeast(0)),
            color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp,
            modifier = Modifier.width(64.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
        )
    }
}

/** Apple-style thin bar that thickens while dragged. Used for the scrubber and volume. */
@Composable
fun ThinSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    onDragStateChange: (Boolean) -> Unit = {},
    onValueChangeFinished: (Float) -> Unit = {},
    activeColor: Color = Color.White,
    inactiveColor: Color = Color.White.copy(alpha = 0.22f),
    thickness: Dp = 6.dp,
) {
    var dragging by remember { mutableStateOf(false) }
    var local by remember { mutableFloatStateOf(value) }
    val change by rememberUpdatedState(onValueChange)
    val finish by rememberUpdatedState(onValueChangeFinished)
    val dragState by rememberUpdatedState(onDragStateChange)
    val height by animateDpAsState(if (dragging) thickness * 2 else thickness, label = "sliderHeight")
    val fill by animateColorAsState(if (dragging) activeColor else activeColor.copy(alpha = 0.7f), label = "sliderColor")
    val shown = (if (dragging) local else value).coerceIn(0f, 1f)

    Box(
        modifier
            .fillMaxWidth()
            .height(30.dp)
            .pointerInput(Unit) {
                detectTapGestures { o ->
                    val f = (o.x / size.width).coerceIn(0f, 1f)
                    change(f)
                    finish(f)
                }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { o ->
                        dragging = true
                        dragState(true)
                        local = (o.x / size.width).coerceIn(0f, 1f)
                        change(local)
                    },
                    onDragEnd = {
                        dragging = false
                        dragState(false)
                        finish(local)
                    },
                    onDragCancel = {
                        dragging = false
                        dragState(false)
                    },
                ) { pointer, _ ->
                    pointer.consume()
                    local = (pointer.position.x / size.width).coerceIn(0f, 1f)
                    change(local)
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(Modifier.fillMaxWidth().height(height).clip(CircleShape).background(inactiveColor)) {
            Box(Modifier.fillMaxWidth(shown).fillMaxHeight().background(fill))
        }
    }
}

@Composable
private fun VolumeRow(active: Boolean) {
    val context = LocalContext.current
    val audio = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val max = remember { audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }
    var volume by remember { mutableFloatStateOf(audio.getStreamVolume(AudioManager.STREAM_MUSIC) / max.toFloat()) }
    var dragging by remember { mutableStateOf(false) }

    LaunchedEffect(active) {
        while (active) {
            if (!dragging) volume = audio.getStreamVolume(AudioManager.STREAM_MUSIC) / max.toFloat()
            delay(500)
        }
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.VolumeDown, null, tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        ThinSlider(
            value = volume,
            onValueChange = {
                volume = it
                audio.setStreamVolume(AudioManager.STREAM_MUSIC, (it * max).roundToInt(), 0)
            },
            onDragStateChange = { dragging = it },
            modifier = Modifier.weight(1f),
            thickness = 3.6.dp, // 40% thinner than the scrubber
        )
        Spacer(Modifier.width(8.dp))
        Icon(Icons.Rounded.VolumeUp, null, tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun TransportButton(icon: ImageVector, description: String, size: Dp, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.84f else 1f, spring(dampingRatio = 0.5f), label = "press")
    val highlight by animateColorAsState(if (pressed) Color.White.copy(alpha = 0.16f) else Color.Transparent, label = "hl")
    Box(
        Modifier
            .size(size + 28.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .background(highlight)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = Color.White, modifier = Modifier.size(size))
    }
}

@Composable
private fun RoundGlassButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    Box(
        Modifier.size(32.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.16f)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = Color.White, modifier = Modifier.size(19.dp))
    }
}

@Composable
private fun BottomToggle(icon: ImageVector, description: String, active: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (active) Color.White.copy(alpha = 0.9f) else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon, contentDescription = description,
            tint = if (active) Color.Black.copy(alpha = 0.75f) else Color.White.copy(alpha = 0.7f),
            modifier = Modifier.size(24.dp),
        )
    }
}

@Composable
private fun QueuePanel(track: Track, player: PlayerState, library: Library, connection: PlayerConnection, bitmap: android.graphics.Bitmap?) {
    Column(Modifier.fillMaxSize()) {
        PanelHeader(track, bitmap)
        Row(Modifier.padding(top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Playing Next", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text(
                    if (player.upNext.isEmpty()) "Nothing queued" else "${player.upNext.size} songs",
                    color = Color.White.copy(alpha = 0.55f), fontSize = 13.sp,
                )
            }
            QueueToggle(Icons.Rounded.Shuffle, "Shuffle", player.shuffle) { connection.toggleShuffle() }
            Spacer(Modifier.width(8.dp))
            QueueToggle(
                if (player.repeatMode == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                "Repeat", player.repeatMode != Player.REPEAT_MODE_OFF,
            ) { connection.cycleRepeat() }
        }
        if (player.upNext.isNotEmpty()) {
            Text(
                "Clear", color = Color.White.copy(alpha = 0.8f), fontSize = 14.sp,
                modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { connection.clearUpNext() }.padding(vertical = 4.dp, horizontal = 2.dp),
            )
        }
        LazyColumn(Modifier.weight(1f)) {
            items(player.upNext, key = { it.index }) { entry ->
                val t = library.byId[entry.trackId]
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { connection.skipToIndex(entry.index) }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Artwork(t, Modifier.size(44.dp), corner = 5.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(t?.title ?: "Unavailable", color = Color.White, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(t?.artist.orEmpty(), color = Color.White.copy(alpha = 0.55f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    IconButton(onClick = { connection.removeAt(entry.index) }) {
                        Icon(Icons.Rounded.Close, contentDescription = "Remove", tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(20.dp))
                    }
                }
                HorizontalDivider(Modifier.padding(start = 56.dp), thickness = 0.5.dp, color = Color.White.copy(alpha = 0.12f))
            }
        }
    }
}

@Composable
private fun PanelHeader(track: Track, bitmap: android.graphics.Bitmap?) {
    Row(Modifier.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        ArtworkImage(bitmap, Modifier.size(64.dp), RoundedCornerShape(6.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(track.title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(track.artist, color = Color.White.copy(alpha = 0.6f), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun QueueToggle(icon: ImageVector, description: String, active: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(width = 46.dp, height = 32.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (active) Color.White.copy(alpha = 0.9f) else Color.White.copy(alpha = 0.14f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, description, tint = if (active) Color.Black.copy(alpha = 0.8f) else Color.White, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun SongInfoDialog(track: Track, quality: AudioQuality?, onDismiss: () -> Unit) {
    val rows = buildList {
        add("Format" to (quality?.codec ?: "…"))
        quality?.bitDepth?.let { add("Bit depth" to "$it-bit") }
        quality?.sampleRate?.let { add("Sample rate" to AudioQuality.formatRate(it)) }
        quality?.bitrate?.takeIf { it > 0 }?.let { add("Bitrate" to "${it / 1000} kbps") }
        quality?.channels?.let { add("Channels" to if (it == 2) "Stereo" else if (it == 1) "Mono" else "$it") }
        if (quality?.lossless == true) add("Quality" to quality.badge)
        add("Duration" to formatDuration(track.durationMs))
        add("Size" to "%.1f MB".format(track.sizeBytes / 1_048_576.0))
        add("Location" to prettyPath(track.path))
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
        title = { Text(track.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            Column {
                rows.forEach { (k, v) ->
                    Row(Modifier.padding(vertical = 4.dp)) {
                        Text(k, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(96.dp), fontSize = 14.sp)
                        Text(v, fontSize = 14.sp)
                    }
                }
            }
        },
    )
}

@Composable
private fun SleepTimerDialog(current: SleepTimer, onPick: (Int?) -> Unit, onDismiss: () -> Unit) {
    val options = listOf<Pair<String, Int?>>(
        "Off" to null, "15 minutes" to 15, "30 minutes" to 30, "45 minutes" to 45,
        "1 hour" to 60, "2 hours" to 120, "End of current song" to -1,
    )
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        title = { Text("Sleep Timer") },
        text = {
            Column {
                if (current is SleepTimer.At) {
                    Text(
                        "Stops in " + formatDuration((current.endsAtMillis - now).coerceAtLeast(0)),
                        color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 8.dp),
                    )
                } else if (current == SleepTimer.EndOfSong) {
                    Text("Stops at the end of this song", color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 8.dp))
                }
                options.forEach { (label, minutes) ->
                    Text(
                        label,
                        fontSize = 16.sp,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { onPick(minutes) }.padding(vertical = 12.dp, horizontal = 4.dp),
                    )
                }
            }
        },
    )
}

fun openEqualizer(context: Context) {
    val intent = Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL).apply {
        putExtra(AudioEffect.EXTRA_AUDIO_SESSION, PlaybackService.audioSession)
        putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
        putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
    }
    try {
        context.startActivity(intent)
    } catch (_: Exception) {
        Toast.makeText(context, "No equalizer app found on this phone", Toast.LENGTH_SHORT).show()
    }
}

// ---------------------------------------------------------------------------------------------
// Lyrics: synced lines that follow the song (tap a line to jump there), or plain text.
// ---------------------------------------------------------------------------------------------

@Composable
private fun LyricsPanel(
    track: Track,
    connection: PlayerConnection,
    allowOnline: Boolean,
    active: Boolean,
    bitmap: android.graphics.Bitmap?,
) {
    val context = LocalContext.current
    val result by produceState(LyricsRepository.cached(track.id), track.id, allowOnline) {
        value = LyricsRepository.cached(track.id)
        value = LyricsRepository.load(context, track, allowOnline)
    }
    Column(Modifier.fillMaxSize()) {
        PanelHeader(track, bitmap)
        when (val r = result) {
            null -> LyricsMessage("Looking for lyrics…", null)
            LyricsRepository.Result.None -> LyricsMessage(
                "No Lyrics",
                if (allowOnline) "None in this song's tags, and LRCLIB doesn't have this song."
                else "None in this song's tags. Turn on Find Lyrics Online in Settings to search LRCLIB.",
            )
            is LyricsRepository.Result.Found ->
                if (r.lyrics.isSynced) SyncedLyrics(track, r.lyrics, connection, active)
                else PlainLyrics(r.lyrics)
        }
    }
}

@Composable
private fun LyricsMessage(title: String, subtitle: String?) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, color = Color.White.copy(alpha = 0.85f), fontSize = 20.sp, fontWeight = FontWeight.Bold)
        if (subtitle != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                subtitle, color = Color.White.copy(alpha = 0.55f), fontSize = 14.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}

@Composable
private fun SyncedLyrics(track: Track, lyrics: Lyrics, connection: PlayerConnection, active: Boolean) {
    val lines = lyrics.lines
    var position by remember { mutableLongStateOf(connection.positionMs) }
    LaunchedEffect(track.id, active) {
        while (active) {
            position = connection.positionMs
            delay(120)
        }
    }
    // Highlight a line slightly before it starts, as Apple Music does.
    val current by remember(lines) {
        derivedStateOf {
            val t = position + 200
            var lo = 0
            var hi = lines.lastIndex
            var found = -1
            while (lo <= hi) {
                val mid = (lo + hi) ushr 1
                if (lines[mid].timeMs <= t) {
                    found = mid
                    lo = mid + 1
                } else hi = mid - 1
            }
            found
        }
    }
    val listState = rememberLazyListState()
    var lastTouch by remember { mutableLongStateOf(0L) }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        // The current line sits about a third of the way down.
        val topPad = maxHeight / 3
        LaunchedEffect(current) {
            if (current < 0) return@LaunchedEffect
            if (android.os.SystemClock.uptimeMillis() - lastTouch < 3500) return@LaunchedEffect
            listState.animateScrollToItem(current)
        }
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(top = topPad, bottom = maxHeight / 2),
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        lastTouch = android.os.SystemClock.uptimeMillis()
                    }
                },
        ) {
            itemsIndexed(lines, key = { i, _ -> i }) { i, line ->
                val isCurrent = i == current
                val alpha by animateFloatAsState(if (isCurrent) 1f else if (i < current) 0.32f else 0.42f, tween(300), label = "lyricAlpha")
                val scale by animateFloatAsState(if (isCurrent) 1f else 0.94f, tween(300), label = "lyricScale")
                Text(
                    line.text.ifBlank { "♪" },
                    color = Color.White,
                    fontSize = 27.sp,
                    lineHeight = 33.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer {
                            this.alpha = alpha
                            scaleX = scale
                            scaleY = scale
                            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f)
                        }
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            lastTouch = 0L
                            connection.seekTo(line.timeMs)
                        }
                        .padding(vertical = 9.dp, horizontal = 2.dp),
                )
            }
            item { LyricsSource(lyrics.source) }
        }
    }
}

@Composable
private fun PlainLyrics(lyrics: Lyrics) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 12.dp, bottom = 24.dp)) {
        Text(
            lyrics.plain.orEmpty(),
            color = Color.White.copy(alpha = 0.9f),
            fontSize = 22.sp,
            lineHeight = 31.sp,
            fontWeight = FontWeight.SemiBold,
        )
        LyricsSource(lyrics.source)
    }
}

@Composable
private fun LyricsSource(source: String) {
    val label = when (source) {
        "LRCLIB" -> "Lyrics from LRCLIB"
        "File" -> "Lyrics from .lrc file"
        else -> "Lyrics from song tags"
    }
    Text(label, color = Color.White.copy(alpha = 0.45f), fontSize = 12.sp, modifier = Modifier.padding(top = 24.dp))
}
