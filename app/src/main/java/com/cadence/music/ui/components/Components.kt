@file:OptIn(ExperimentalFoundationApi::class)

package com.cadence.music.ui.components

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cadence.music.data.Album
import com.cadence.music.data.ArtworkLoader
import com.cadence.music.data.Track
import com.cadence.music.ui.theme.Cadence

// ---------------------------------------------------------------------------------------------
// Shared actions every song row can trigger, provided once at the root.
// ---------------------------------------------------------------------------------------------

class TrackActions(
    val play: (tracks: List<Track>, index: Int) -> Unit,
    val shuffle: (tracks: List<Track>) -> Unit,
    val playNext: (List<Track>) -> Unit,
    val playLast: (List<Track>) -> Unit,
    val toggleFavorite: (Track) -> Unit,
    val addToPlaylist: (List<Track>) -> Unit,
    val openAlbum: (Long) -> Unit,
    val openArtist: (String) -> Unit,
    val favorites: Set<Long>,
    val currentId: Long?,
    val isPlaying: Boolean,
)

val LocalTrackActions = compositionLocalOf<TrackActions> { error("TrackActions not provided") }

data class MenuAction(val label: String, val icon: ImageVector, val destructive: Boolean = false, val onClick: () -> Unit)

// ---------------------------------------------------------------------------------------------
// Artwork
// ---------------------------------------------------------------------------------------------

@Composable
fun rememberArtwork(track: Track?, size: Int): Bitmap? {
    val context = LocalContext.current
    val state = produceState(
        initialValue = track?.let { ArtworkLoader.cached(it, size) },
        track?.albumId, track?.folder, size,
    ) {
        value = track?.let { ArtworkLoader.load(context, it, size) }
    }
    return state.value
}

@Composable
fun Artwork(
    track: Track?,
    modifier: Modifier = Modifier,
    corner: Dp = 6.dp,
    size: Int = ArtworkLoader.SMALL,
    shape: Shape = RoundedCornerShape(corner),
) {
    val bitmap = rememberArtwork(track, size)
    ArtworkImage(bitmap, modifier, shape)
}

@Composable
fun ArtworkImage(bitmap: Bitmap?, modifier: Modifier = Modifier, shape: Shape = RoundedCornerShape(6.dp)) {
    val image = remember(bitmap) { bitmap?.asImageBitmap() }
    Box(modifier.clip(shape).background(Cadence.colors.placeholder), contentAlignment = Alignment.Center) {
        if (image != null) {
            Image(image, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Icon(
                Icons.Rounded.MusicNote, contentDescription = null,
                tint = Cadence.colors.secondaryText,
                modifier = Modifier.fillMaxSize(0.42f),
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Screen scaffolding: a list with an Apple-style large title that collapses into a small bar.
// ---------------------------------------------------------------------------------------------

@Composable
fun ScreenList(
    title: String,
    modifier: Modifier = Modifier,
    backLabel: String? = null,
    onBack: (() -> Unit)? = null,
    state: LazyListState = rememberLazyListState(),
    showLargeTitle: Boolean = true,
    background: Color = MaterialTheme.colorScheme.background,
    /** Shown inside another screen (a Library category page): no title, back bar or status-bar inset. */
    embedded: Boolean = false,
    trailing: (@Composable RowScope.() -> Unit)? = null,
    content: LazyListScope.() -> Unit,
) {
    if (embedded) {
        LazyColumn(
            state = state,
            modifier = modifier.fillMaxSize().background(background),
            contentPadding = PaddingValues(bottom = 32.dp),
        ) {
            if (trailing != null) {
                item(key = "__header") {
                    Row(Modifier.fillMaxWidth().padding(end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Spacer(Modifier.weight(1f))
                        trailing()
                    }
                }
            }
            content()
        }
        return
    }
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val collapsed by remember(state) {
        derivedStateOf { state.firstVisibleItemIndex > 0 || state.firstVisibleItemScrollOffset > 80 }
    }
    Box(modifier.fillMaxSize().background(background)) {
        LazyColumn(
            state = state,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = top, bottom = 32.dp),
        ) {
            item(key = "__header") {
                Column {
                    if (onBack != null) BackBar(backLabel ?: "Back", onBack, trailing)
                    if (showLargeTitle) LargeTitle(title, trailing = if (onBack == null) trailing else null)
                }
            }
            content()
        }
        AnimatedVisibility(visible = collapsed, enter = fadeIn(), exit = fadeOut()) {
            Column(Modifier.fillMaxWidth().background(Cadence.colors.bar).statusBarsPadding()) {
                Box(Modifier.fillMaxWidth().height(44.dp), contentAlignment = Alignment.Center) {
                    if (onBack != null) {
                        Row(
                            Modifier.align(Alignment.CenterStart).clickable(onClick = onBack).padding(horizontal = 8.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Rounded.ChevronLeft, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                        }
                    }
                    Text(
                        title, style = MaterialTheme.typography.titleMedium, maxLines = 1,
                        overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 56.dp),
                    )
                    if (trailing != null) {
                        Row(Modifier.align(Alignment.CenterEnd).padding(end = 4.dp), verticalAlignment = Alignment.CenterVertically) { trailing() }
                    }
                }
                HorizontalDivider(color = Cadence.colors.separator, thickness = 0.5.dp)
            }
        }
    }
}

@Composable
fun LargeTitle(text: String, modifier: Modifier = Modifier, trailing: (@Composable RowScope.() -> Unit)? = null) {
    Row(
        modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text, style = MaterialTheme.typography.headlineLarge,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
        )
        trailing?.invoke(this)
    }
}

@Composable
fun BackBar(label: String, onBack: () -> Unit, trailing: (@Composable RowScope.() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().height(44.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onBack).padding(start = 4.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.ChevronLeft, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(30.dp))
            Text(label, color = MaterialTheme.colorScheme.primary, fontSize = 17.sp)
        }
        Spacer(Modifier.weight(1f))
        trailing?.invoke(this)
    }
}

// ---------------------------------------------------------------------------------------------
// Rows
// ---------------------------------------------------------------------------------------------

enum class TrackLeading { Artwork, Number, None }

@Composable
fun TrackRow(
    track: Track,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leading: TrackLeading = TrackLeading.Artwork,
    number: Int? = null,
    subtitle: String? = "${track.artist} · ${track.album}",
    extraMenu: List<MenuAction> = emptyList(),
    showDivider: Boolean = true,
) {
    val actions = LocalTrackActions.current
    val haptics = LocalHapticFeedback.current
    var menu by remember { mutableStateOf(false) }
    val isCurrent = actions.currentId == track.id

    Column(modifier) {
        Row(
            Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        menu = true
                    },
                )
                .padding(start = 20.dp, end = 4.dp)
                .height(if (leading == TrackLeading.Artwork) 64.dp else 52.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when (leading) {
                TrackLeading.Artwork -> {
                    Box(contentAlignment = Alignment.Center) {
                        Artwork(track, Modifier.size(48.dp), corner = 5.dp)
                        if (isCurrent) {
                            Box(Modifier.size(48.dp).clip(RoundedCornerShape(5.dp)).background(Color.Black.copy(alpha = 0.45f)))
                            PlayingBars(actions.isPlaying, Color.White, Modifier.size(16.dp))
                        }
                    }
                    Spacer(Modifier.width(14.dp))
                }
                TrackLeading.Number -> {
                    Box(Modifier.width(30.dp), contentAlignment = Alignment.CenterStart) {
                        if (isCurrent) PlayingBars(actions.isPlaying, MaterialTheme.colorScheme.primary, Modifier.size(14.dp))
                        else Text("${number ?: track.trackNumber}", color = Cadence.colors.secondaryText, fontSize = 15.sp)
                    }
                    Spacer(Modifier.width(8.dp))
                }
                TrackLeading.None -> Unit
            }
            Column(Modifier.weight(1f)) {
                Text(
                    track.title, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                if (!subtitle.isNullOrEmpty()) {
                    Text(subtitle, fontSize = 13.sp, color = Cadence.colors.secondaryText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (track.id in actions.favorites) {
                Icon(Icons.Rounded.Star, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
            }
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(Icons.Rounded.MoreHoriz, contentDescription = "More", tint = MaterialTheme.colorScheme.onBackground)
                }
                TrackMenu(menu, { menu = false }, track, extraMenu)
            }
        }
        if (showDivider) {
            HorizontalDivider(
                Modifier.padding(start = if (leading == TrackLeading.Artwork) 82.dp else 20.dp),
                thickness = 0.5.dp, color = Cadence.colors.separator,
            )
        }
    }
}

@Composable
fun TrackMenu(expanded: Boolean, onDismiss: () -> Unit, track: Track, extra: List<MenuAction> = emptyList()) {
    val a = LocalTrackActions.current
    val favorite = track.id in a.favorites
    val items = buildList {
        add(MenuAction("Play Next", Icons.Rounded.PlaylistPlay) { a.playNext(listOf(track)) })
        add(MenuAction("Play Last", Icons.Rounded.QueueMusic) { a.playLast(listOf(track)) })
        add(MenuAction("Add to Playlist…", Icons.Rounded.PlaylistAdd) { a.addToPlaylist(listOf(track)) })
        add(MenuAction(if (favorite) "Undo Favorite" else "Favorite", if (favorite) Icons.Rounded.StarBorder else Icons.Rounded.Star) { a.toggleFavorite(track) })
        add(MenuAction("Go to Album", Icons.Rounded.Album) { a.openAlbum(track.albumId) })
        add(MenuAction("Go to Artist", Icons.Rounded.Mic) { a.openArtist(track.primaryArtist) })
        addAll(extra)
    }
    ActionMenu(expanded, onDismiss, items)
}

@Composable
fun ActionMenu(expanded: Boolean, onDismiss: () -> Unit, items: List<MenuAction>) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss, shape = RoundedCornerShape(14.dp)) {
        items.forEach { item ->
            val color = if (item.destructive) Color(0xFFFF3B30) else MaterialTheme.colorScheme.onSurface
            DropdownMenuItem(
                text = { Text(item.label, color = color) },
                trailingIcon = { Icon(item.icon, null, tint = color) },
                onClick = {
                    onDismiss()
                    item.onClick()
                },
            )
        }
    }
}

@Composable
fun PlayingBars(playing: Boolean, color: Color, modifier: Modifier = Modifier) {
    if (!playing) {
        Canvas(modifier) { drawBars(color, 0.3f, 0.3f, 0.3f) }
        return
    }
    val transition = rememberInfiniteTransition(label = "bars")
    val a = transition.animateFloat(0.25f, 1f, infiniteRepeatable(tween(380), RepeatMode.Reverse), label = "a")
    val b = transition.animateFloat(1f, 0.3f, infiniteRepeatable(tween(460), RepeatMode.Reverse), label = "b")
    val c = transition.animateFloat(0.4f, 0.9f, infiniteRepeatable(tween(320), RepeatMode.Reverse), label = "c")
    // Values are read while drawing, so the bars animate without recomposing every frame.
    Canvas(modifier) { drawBars(color, a.value, b.value, c.value) }
}

private fun DrawScope.drawBars(color: Color, a: Float, b: Float, c: Float) {
    val gap = 2.dp.toPx()
    val w = (size.width - gap * 2) / 3f
    val r = CornerRadius(1.dp.toPx())
    listOf(a, b, c).forEachIndexed { i, f ->
        val h = size.height * f
        drawRoundRect(color, topLeft = Offset(i * (w + gap), size.height - h), size = Size(w, h), cornerRadius = r)
    }
}

@Composable
fun NavRow(icon: ImageVector, label: String, onClick: () -> Unit, detail: String? = null, showDivider: Boolean = true) {
    Column {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 20.dp, end = 12.dp).height(52.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(16.dp))
            Text(label, fontSize = 19.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (detail != null) Text(detail, color = Cadence.colors.secondaryText, fontSize = 15.sp)
            Icon(Icons.Rounded.ChevronRight, null, tint = Cadence.colors.tertiaryText)
        }
        if (showDivider) HorizontalDivider(Modifier.padding(start = 60.dp), thickness = 0.5.dp, color = Cadence.colors.separator)
    }
}

@Composable
fun SectionHeader(title: String, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 22.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        if (actionLabel != null && onAction != null) {
            Text(
                actionLabel, color = MaterialTheme.colorScheme.primary, fontSize = 16.sp,
                modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable(onClick = onAction).padding(6.dp),
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Buttons, tiles and badges
// ---------------------------------------------------------------------------------------------

@Composable
fun PillButton(label: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.height(48.dp).clip(RoundedCornerShape(10.dp)).background(Cadence.colors.fill).clickable(onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, color = MaterialTheme.colorScheme.primary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun PlayShuffleRow(onPlay: () -> Unit, onShuffle: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        PillButton("Play", Icons.Rounded.PlayArrow, onPlay, Modifier.weight(1f))
        PillButton("Shuffle", Icons.Rounded.Shuffle, onShuffle, Modifier.weight(1f))
    }
}

@Composable
fun AlbumTile(album: Album, onClick: () -> Unit, modifier: Modifier = Modifier, subtitle: String = album.artist) {
    Column(modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick)) {
        Artwork(album.cover, Modifier.fillMaxWidth().aspectRatio(1f), corner = 8.dp, size = 512)
        Spacer(Modifier.height(6.dp))
        Text(album.title, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(subtitle, fontSize = 13.sp, color = Cadence.colors.secondaryText, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

fun LazyListScope.albumGrid(keyPrefix: String, albums: List<Album>, onClick: (Album) -> Unit, subtitle: (Album) -> String = { it.artist }) {
    items(albums.chunked(2), key = { row -> "$keyPrefix-${row.first().id}" }) { row ->
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            row.forEach { album -> AlbumTile(album, { onClick(album) }, Modifier.weight(1f), subtitle(album)) }
            if (row.size == 1) Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
fun AlbumShelf(albums: List<Album>, onClick: (Album) -> Unit, subtitle: (Album) -> String = { it.artist }) {
    LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        items(albums, key = { it.id }) { album ->
            AlbumTile(album, { onClick(album) }, Modifier.width(160.dp), subtitle(album))
        }
    }
}

@Composable
fun QualityBadge(text: String, color: Color, modifier: Modifier = Modifier) {
    Text(
        text,
        color = color,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.16f))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

@Composable
fun EmptyState(icon: ImageVector, title: String, message: String, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 40.dp, vertical = 56.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, tint = Cadence.colors.secondaryText, modifier = Modifier.size(56.dp))
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(message, color = Cadence.colors.secondaryText, textAlign = TextAlign.Center, fontSize = 15.sp)
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(20.dp))
            Text(
                actionLabel,
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .clickable(onClick = onAction)
                    .padding(horizontal = 22.dp, vertical = 12.dp),
            )
        }
    }
}

@Composable
fun FooterText(text: String) {
    Text(
        text,
        color = Cadence.colors.secondaryText,
        fontSize = 14.sp,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
    )
}
