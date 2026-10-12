package com.cadence.music.ui.nowplaying

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Position of the Now Playing sheet, kept outside the sheet so the sheet can stay composed while
 * hidden. Opening then only moves an already-built layer instead of building the whole screen
 * during the animation, which is what made it stutter on slower phones.
 */
@Stable
class PlayerSheetState internal constructor(private val scope: CoroutineScope, startOpen: Boolean) {
    /** 0 = fully open, 1 = fully hidden below the screen. */
    private val progress = Animatable(if (startOpen) 0f else 1f)
    private var heightPx = 0f

    var isOpen by mutableStateOf(startOpen)
        private set

    /** True while any part of the sheet is on screen (open, opening, closing or being dragged). */
    val isVisible by derivedStateOf { isOpen || progress.value < 1f }

    /** 0 = open, 1 = hidden. Read it only in draw / graphicsLayer blocks so frames don't recompose. */
    val fraction: Float get() = progress.value

    // Where the artwork sits in the mini player and in the open sheet (window coordinates), so the
    // artwork can fly and grow between the two while the sheet moves, as in Apple Music.
    var miniArt by mutableStateOf<Rect?>(null)
    var fullArt by mutableStateOf<Rect?>(null)
    /** The open sheet's artwork scale (it shrinks while paused). */
    var fullArtScale by mutableFloatStateOf(1f)
    /** Off while the sheet shows lyrics or the queue instead of the big artwork. */
    var morphEnabled by mutableStateOf(true)

    /** True when the flying artwork is on screen instead of the two real ones. */
    fun morphing(f: Float = fraction): Boolean =
        morphEnabled && miniArt != null && fullArt != null && f > 0f && f < 1f

    fun open() {
        isOpen = true
        scope.launch { progress.animateTo(0f, spring(dampingRatio = 1f, stiffness = 520f)) }
    }

    fun close() {
        isOpen = false
        scope.launch { progress.animateTo(1f, spring(dampingRatio = 1f, stiffness = Spring.StiffnessMediumLow)) }
    }

    /** Follows a finger: positive [dy] pulls the sheet down, negative pushes it up. */
    fun dragBy(dy: Float) {
        if (heightPx <= 0f) return
        scope.launch { progress.snapTo((progress.value + dy / heightPx).coerceIn(0f, 1f)) }
    }

    /** Settles after a drag: close when pulled down far enough (or opened only a little), else open. */
    fun settle(fromOpen: Boolean) {
        val p = progress.value
        if (fromOpen) {
            if (p > 0.18f) close() else open()
        } else {
            if (p < 0.93f) open() else close()
        }
    }

    internal fun Modifier.sheetLayer(): Modifier = this
        .onSizeChanged { heightPx = it.height.toFloat() }
        // Measured all the time, placed (and so drawn and touchable) only while visible.
        .layout { measurable, constraints ->
            val p = measurable.measure(constraints)
            layout(p.width, p.height) { if (isVisible) p.place(0, 0) }
        }
        .graphicsLayer {
            val v = progress.value
            translationY = v * size.height
            shape = if (v > 0f) RoundedCornerShape(24.dp) else RectangleShape
            clip = true
        }
}

@Composable
fun rememberPlayerSheetState(): PlayerSheetState {
    val scope = rememberCoroutineScope()
    var savedOpen by rememberSaveable { mutableStateOf(false) }
    val state = remember { PlayerSheetState(scope, savedOpen) }
    SideEffect { savedOpen = state.isOpen }
    return state
}

fun Modifier.playerSheet(state: PlayerSheetState): Modifier = with(state) { sheetLayer() }
