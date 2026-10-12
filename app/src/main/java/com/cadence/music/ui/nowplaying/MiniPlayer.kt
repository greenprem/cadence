package com.cadence.music.ui.nowplaying

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cadence.music.data.Track
import com.cadence.music.ui.components.Artwork
import com.cadence.music.ui.theme.Cadence

/**
 * The floating bar above the tabs, as in Apple Music. Tap or drag up to open Now Playing, swipe
 * left / right to change song. A thin line along the bottom shows how far into the song you are.
 */
@Composable
fun MiniPlayer(
    track: Track,
    isPlaying: Boolean,
    sheet: PlayerSheetState,
    progress: () -> Float,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(14.dp)
    val swipe = rememberSwipeToSkipState()
    val barColor = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f)
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .shadow(10.dp, shape, ambientColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.2f))
            .clip(shape)
            .background(if (Cadence.colors.isDark) Cadence.colors.groupedCell else Cadence.colors.bar)
            .drawWithContent {
                drawContent()
                val h = 2.dp.toPx()
                val inset = 12.dp.toPx()
                val w = size.width - inset * 2
                val y = size.height - h - 3.dp.toPx()
                drawRect(trackColor, Offset(inset, y), Size(w, h))
                drawRect(barColor, Offset(inset, y), Size(w * progress().coerceIn(0f, 1f), h))
            }
            .clickable { sheet.open() }
            .pointerInput(sheet) {
                // The Now Playing sheet follows the finger, then settles open or closed.
                detectVerticalDragGestures(
                    onDragEnd = { sheet.settle(fromOpen = false) },
                    onDragCancel = { sheet.settle(fromOpen = false) },
                ) { _, dy -> sheet.dragBy(dy) }
            }
            .swipeToSkipUpdated(swipe, onNext = onNext, onPrevious = onPrevious)
            .padding(start = 8.dp, end = 4.dp)
            .height(60.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.weight(1f).swipeOffset(swipe), verticalAlignment = Alignment.CenterVertically) {
            Artwork(
                track,
                Modifier
                    .size(44.dp)
                    .onGloballyPositioned { sheet.miniArt = Rect(it.positionInRoot(), it.size.toSize()) }
                    // Hidden while its flying copy is on screen during open / close.
                    .graphicsLayer { if (sheet.morphing()) alpha = 0f },
                corner = 6.dp,
            )
            Spacer(Modifier.width(12.dp))
            Text(
                track.title,
                modifier = Modifier.weight(1f),
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onTogglePlay) {
            Icon(
                if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = if (isPlaying) "Pause" else "Play",
                modifier = Modifier.size(30.dp),
            )
        }
        IconButton(onClick = onNext) {
            Icon(Icons.Rounded.FastForward, contentDescription = "Next", modifier = Modifier.size(28.dp))
        }
    }
}
