package com.cadence.music.ui.nowplaying

import android.graphics.Bitmap
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.cadence.music.ui.theme.Cadence
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Horizontal swipe to change song. Content follows the finger; past a quarter of the width it
 * slides out, the song changes and the new one slides in from the other side.
 */
@Stable
class SwipeToSkipState internal constructor(private val scope: CoroutineScope) {
    internal val offset = Animatable(0f)
    val value: Float get() = offset.value

    internal fun drag(dx: Float) {
        scope.launch { offset.snapTo(offset.value + dx) }
    }

    internal fun release(width: Float, onNext: () -> Unit, onPrevious: () -> Unit) {
        scope.launch {
            val x = offset.value
            when {
                width > 0f && x < -width * 0.25f -> {
                    offset.animateTo(-width, tween(140))
                    onNext()
                    offset.snapTo(width * 0.6f)
                    offset.animateTo(0f, spring(dampingRatio = 0.85f, stiffness = 500f))
                }
                width > 0f && x > width * 0.25f -> {
                    offset.animateTo(width, tween(140))
                    onPrevious()
                    offset.snapTo(-width * 0.6f)
                    offset.animateTo(0f, spring(dampingRatio = 0.85f, stiffness = 500f))
                }
                else -> offset.animateTo(0f, spring(dampingRatio = 0.8f, stiffness = 600f))
            }
        }
    }
}

@Composable
fun rememberSwipeToSkipState(): SwipeToSkipState {
    val scope = rememberCoroutineScope()
    return remember { SwipeToSkipState(scope) }
}

/** Detects the horizontal swipe; apply [swipeOffset] to whatever should move with the finger. */
fun Modifier.swipeToSkip(state: SwipeToSkipState, onNext: () -> Unit, onPrevious: () -> Unit): Modifier =
    this.pointerInput(state) {
        detectHorizontalDragGestures(
            onDragEnd = { state.release(size.width.toFloat(), onNext, onPrevious) },
            onDragCancel = { state.release(size.width.toFloat(), onNext, onPrevious) },
        ) { change, dx ->
            change.consume()
            state.drag(dx)
        }
    }

/** Moves and fades content with the swipe (draw-phase only, no recomposition). */
fun Modifier.swipeOffset(state: SwipeToSkipState): Modifier = this.graphicsLayer {
    val x = state.value
    translationX = x
    alpha = if (size.width > 0f) (1f - abs(x) / size.width * 1.2f).coerceIn(0f, 1f) else 1f
}

/** [swipeToSkip] with callbacks that stay current across recompositions. */
@Composable
fun Modifier.swipeToSkipUpdated(state: SwipeToSkipState, onNext: () -> Unit, onPrevious: () -> Unit): Modifier {
    val next by rememberUpdatedState(onNext)
    val prev by rememberUpdatedState(onPrevious)
    return swipeToSkip(state, { next() }, { prev() })
}

/**
 * The artwork that flies from the mini player to its place in Now Playing (and back) while the
 * sheet opens, closes or is dragged. Everything happens in the graphics layer, so it runs at
 * full frame rate without recomposing.
 */
@Composable
fun ArtworkMorph(sheet: PlayerSheetState, bitmap: Bitmap?) {
    val image = remember(bitmap) { bitmap?.asImageBitmap() }
    val placeholder = Cadence.colors.placeholder
    Box(
        Modifier
            .layout { measurable, constraints ->
                val full = sheet.fullArt
                if (full == null || full.width < 1f) {
                    layout(0, 0) {}
                } else {
                    val p = measurable.measure(Constraints.fixed(full.width.roundToInt(), full.height.roundToInt()))
                    layout(constraints.maxWidth, constraints.maxHeight) {
                        p.placeWithLayer(full.left.roundToInt(), full.top.roundToInt()) {
                            val f = sheet.fraction
                            val mini = sheet.miniArt
                            if (mini == null || !sheet.morphing(f)) {
                                alpha = 0f
                                return@placeWithLayer
                            }
                            alpha = 1f
                            // The sheet's own spring gives the easing; the art just tracks it.
                            val t = f
                            val k = sheet.fullArtScale
                            val targetW = full.width * k
                            val targetH = full.height * k
                            val targetL = full.left + (full.width - targetW) / 2f
                            val targetT = full.top + (full.height - targetH) / 2f
                            val w = lerp(targetW, mini.width, t)
                            val h = lerp(targetH, mini.height, t)
                            val l = lerp(targetL, mini.left, t)
                            val top = lerp(targetT, mini.top, t)
                            val sx = w / full.width
                            val sy = h / full.height
                            transformOrigin = TransformOrigin(0f, 0f)
                            scaleX = sx
                            scaleY = sy
                            translationX = l - full.left
                            translationY = top - full.top
                            // Corner radius in unscaled space, so it lands at 6 dp on the mini player.
                            val r = lerp(10.dp.toPx(), 6.dp.toPx(), t) / sx.coerceAtLeast(0.01f)
                            shape = RoundedCornerShape(r)
                            clip = true
                            shadowElevation = lerp(18.dp.toPx(), 0f, t)
                        }
                    }
                }
            }
            .background(placeholder),
    ) {
        if (image != null) {
            Image(image, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
    }
}

private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t
