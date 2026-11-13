package com.hblaze.apexy.ui.neu

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp

/**
 * How far a screen has scrolled, as a lambda so readers subscribe in the draw phase only:
 * scrolling then repaints layers without recomposing anything.
 */
class ScrollSignal(val offsetPx: () -> Float)

@Composable
fun rememberScrollSignal(state: LazyListState) = remember(state) {
    // Past the first item the header is fully collapsed, so a large constant is enough.
    ScrollSignal { if (state.firstVisibleItemIndex > 0) 100_000f else state.firstVisibleItemScrollOffset.toFloat() }
}

@Composable
fun rememberScrollSignal(state: ScrollState) = remember(state) { ScrollSignal { state.value.toFloat() } }

/**
 * Cards rise, grow and fade in as they come up from the bottom of the screen, and settle back a
 * little as they leave at the top.
 */
fun Modifier.scrollReveal(signal: ScrollSignal): Modifier = composed {
    val view = LocalView.current
    val holder = remember { arrayOfNulls<LayoutCoordinates>(1) }
    onPlaced { holder[0] = it }.graphicsLayer {
        signal.offsetPx()
        val coords = holder[0]?.takeIf { it.isAttached } ?: return@graphicsLayer
        val top = coords.positionInWindow().y
        val height = coords.size.height.toFloat()
        val screen = view.height.toFloat()
        val rise = 180.dp.toPx()
        // 0 when the card's top is at the bottom edge, 1 once it is [rise] above it.
        val enter = ((screen - top) / rise).coerceIn(0f, 1f)
        val e = 1f - (1f - enter) * (1f - enter) * (1f - enter)
        // Leaving at the top: shrink slightly as it passes under the header.
        val leave = if (top < 0f && height > 0f) (-top / height).coerceIn(0f, 1f) else 0f
        alpha = (0.15f + 0.85f * e) * (1f - 0.5f * leave)
        val s = (0.9f + 0.1f * e) * (1f - 0.06f * leave)
        scaleX = s
        scaleY = s
        translationY = (1f - e) * 56.dp.toPx()
        transformOrigin = TransformOrigin(0.5f, if (leave > 0f) 1f else 0f)
    }
}

/** Wraps one lazy-list item so it gets [scrollReveal]. */
@Composable
fun Reveal(signal: ScrollSignal, content: @Composable BoxScope.() -> Unit) {
    Box(Modifier.scrollReveal(signal), content = content)
}

/** The big page title: drifts up slower than the list and fades as the compact bar takes over. */
fun Modifier.collapsingTitle(signal: ScrollSignal): Modifier = graphicsLayer {
    val y = signal.offsetPx()
    val p = (y / 64.dp.toPx()).coerceIn(0f, 1f)
    alpha = 1f - p
    scaleX = 1f - 0.12f * p
    scaleY = 1f - 0.12f * p
    translationY = y * 0.4f
    transformOrigin = TransformOrigin(0f, 0.5f)
}

/** Compact title bar that fades in over the content once the big title has scrolled away. */
@Composable
fun BoxScope.CollapsedBar(title: String, signal: ScrollSignal) {
    val c = Neu.colors
    Box(
        Modifier
            .align(Alignment.TopCenter)
            .fillMaxWidth()
            .graphicsLayer { alpha = ((signal.offsetPx() - 40.dp.toPx()) / 40.dp.toPx()).coerceIn(0f, 1f) },
    ) {
        Box(Modifier.fillMaxWidth().height(52.dp).background(c.surface)) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = c.text, modifier = Modifier.align(Alignment.CenterStart).padding(start = 24.dp))
        }
        // Soft fade instead of a hard edge, in keeping with the neumorphic look.
        Box(
            Modifier.padding(top = 52.dp).fillMaxWidth().height(18.dp)
                .background(Brush.verticalGradient(listOf(c.surface, c.surface.copy(alpha = 0f)))),
        )
    }
}
