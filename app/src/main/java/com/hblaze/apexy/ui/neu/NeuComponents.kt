@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.hblaze.apexy.ui.neu

import android.graphics.BlurMaskFilter
import android.graphics.Paint
import android.graphics.Path
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlin.math.roundToInt

// --- Shadow engine ------------------------------------------------------------------------------

/**
 * Draws a neumorphic body: a fill plus an outer light/dark shadow pair ([elevation]) and/or an
 * inner shadow pair ([inset]). Animate the two against each other for a "pressed in" effect.
 */
fun Modifier.neu(
    cornerRadius: Dp = 22.dp,
    elevation: Dp = 7.dp,
    inset: Dp = 0.dp,
    fill: Color? = null,
): Modifier = composed {
    val c = Neu.colors
    val body = fill ?: c.surface
    drawWithCache {
        val r = minOf(cornerRadius.toPx(), size.minDimension / 2f)
        val e = elevation.toPx()
        val i = inset.toPx()
        val w = size.width
        val h = size.height
        val outer = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            if (e > 0.5f) maskFilter = BlurMaskFilter(e * 1.5f, BlurMaskFilter.Blur.NORMAL)
        }
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = body.toArgb() }
        val inner = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = i * 2f
            if (i > 0.5f) maskFilter = BlurMaskFilter(i, BlurMaskFilter.Blur.NORMAL)
        }
        val clip = Path().apply { addRoundRect(0f, 0f, w, h, r, r, Path.Direction.CW) }
        onDrawBehind {
            drawIntoCanvas { canvas ->
                val nc = canvas.nativeCanvas
                if (e > 0.5f) {
                    val d = e * 0.65f
                    outer.color = c.shadowDark.toArgb()
                    nc.drawRoundRect(d, d, w + d, h + d, r, r, outer)
                    outer.color = c.shadowLight.toArgb()
                    nc.drawRoundRect(-d, -d, w - d, h - d, r, r, outer)
                }
                nc.drawRoundRect(0f, 0f, w, h, r, r, fillPaint)
                if (i > 0.5f) {
                    val d = i * 0.6f
                    nc.save()
                    nc.clipPath(clip)
                    inner.color = c.shadowDark.toArgb()
                    nc.drawRoundRect(d, d, w + d, h + d, r, r, inner)
                    inner.color = c.shadowLight.toArgb()
                    nc.drawRoundRect(-d, -d, w - d, h - d, r, r, inner)
                    nc.restore()
                }
            }
        }
    }
}

// --- Surfaces -----------------------------------------------------------------------------------


@Composable
fun NeuCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 26.dp,
    padding: PaddingValues = PaddingValues(20.dp),
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val elevation by animateDpAsState(if (pressed) 3.dp else 8.dp, label = "card")
    val clickable = if (onClick != null || onLongClick != null) {
        Modifier.combinedClickable(source, null, onClick = { onClick?.invoke() }, onLongClick = onLongClick)
    } else Modifier
    Column(
        modifier
            .neu(cornerRadius, elevation)
            .then(clickable)
            .padding(padding),
        content = content,
    )
}

/** A sunken well, used for inputs, tracks and grouped content. */
@Composable
fun NeuWell(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 18.dp,
    content: @Composable () -> Unit,
) {
    Box(modifier.neu(cornerRadius, elevation = 0.dp, inset = 5.dp)) { content() }
}

// --- Buttons ------------------------------------------------------------------------------------

@Composable
fun NeuButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    enabled: Boolean = true,
    cornerRadius: Dp = 18.dp,
    contentPadding: PaddingValues = PaddingValues(horizontal = 22.dp, vertical = 15.dp),
    content: @Composable RowScope.() -> Unit,
) {
    val c = Neu.colors
    val haptics = com.hblaze.apexy.ui.rememberHaptics()
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val elevation by animateDpAsState(if (pressed || !enabled) 0.dp else 6.dp, spring(stiffness = 900f), label = "elev")
    val inset by animateDpAsState(if (pressed) 4.dp else 0.dp, spring(stiffness = 900f), label = "inset")
    val fill = if (primary) c.primary else c.surface
    val contentColor = when {
        !enabled -> c.textMuted.copy(alpha = 0.5f)
        primary -> c.onPrimary
        else -> c.text
    }
    Row(
        modifier
            .neu(cornerRadius, elevation, inset, fill)
            .combinedClickable(source, null, enabled = enabled, onClick = {
                haptics.tick()
                com.hblaze.apexy.ui.UiSounds.play(com.hblaze.apexy.ui.UiSounds.Kind.TAP)
                onClick()
            })
            .padding(contentPadding),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            androidx.compose.material3.ProvideTextStyle(MaterialTheme.typography.labelLarge) { content() }
        }
    }
}

@Composable
fun NeuIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 50.dp,
    primary: Boolean = false,
    tint: Color? = null,
) {
    val c = Neu.colors
    NeuButton(
        onClick = onClick,
        modifier = modifier.size(size),
        primary = primary,
        cornerRadius = size,
        contentPadding = PaddingValues(0.dp),
    ) {
        Icon(icon, contentDescription, Modifier.size(size * 0.44f), tint = tint ?: if (primary) c.onPrimary else c.text)
    }
}

// --- Selection controls -------------------------------------------------------------------------

@Composable
fun NeuToggle(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val c = Neu.colors
    val haptics = com.hblaze.apexy.ui.rememberHaptics()
    val fraction by animateFloatAsState(if (checked) 1f else 0f, spring(dampingRatio = 0.7f, stiffness = 500f), label = "toggle")
    val track by animateColorAsState(if (checked) c.primary.copy(alpha = if (c.isDark) 0.55f else 0.35f) else c.surface, label = "track")
    val density = LocalDensity.current
    Box(
        modifier
            .size(width = 58.dp, height = 32.dp)
            .neu(16.dp, elevation = 0.dp, inset = 4.dp, fill = track)
            .pointerInput(enabled, checked) {
                if (enabled) detectTapGestures {
                    haptics.confirm()
                    com.hblaze.apexy.ui.UiSounds.play(if (checked) com.hblaze.apexy.ui.UiSounds.Kind.OFF else com.hblaze.apexy.ui.UiSounds.Kind.ON)
                    onCheckedChange(!checked)
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        val travel = with(density) { (58.dp - 26.dp - 6.dp).toPx() }
        Box(
            Modifier
                .offset { IntOffset((with(density) { 3.dp.toPx() } + travel * fraction).roundToInt(), 0) }
                .size(26.dp)
                .neu(13.dp, elevation = 3.dp, fill = if (checked) c.primary else c.surface),
        )
    }
}

@Composable
fun NeuChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    round: Boolean = false,
) {
    val c = Neu.colors
    val source = remember { MutableInteractionSource() }
    val elevation by animateDpAsState(if (selected) 0.dp else 4.dp, label = "chipE")
    val inset by animateDpAsState(if (selected) 3.5.dp else 0.dp, label = "chipI")
    Box(
        modifier
            .then(if (round) Modifier.size(40.dp) else Modifier.height(40.dp))
            .neu(if (round) 20.dp else 14.dp, elevation, inset)
            .combinedClickable(source, null, onClick = onClick)
            .padding(horizontal = if (round) 0.dp else 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) c.accent else c.textMuted,
        )
    }
}

@Composable
fun <T> NeuSegmented(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    label: (T) -> String,
) {
    val c = Neu.colors
    val index = options.indexOf(selected).coerceAtLeast(0)
    // One raised pill slides between the options instead of each option popping up in place.
    val position = remember { androidx.compose.animation.core.Animatable(index.toFloat()) }
    LaunchedEffect(index) { position.animateTo(index.toFloat(), spring(dampingRatio = 0.8f, stiffness = 420f)) }
    BoxWithConstraints(
        modifier
            .neu(18.dp, elevation = 0.dp, inset = 4.dp)
            .padding(5.dp),
    ) {
        val gap = 4.dp
        val slot = (maxWidth - gap * (options.size - 1)) / options.size
        val stepPx = with(LocalDensity.current) { (slot + gap).toPx() }
        Box(
            Modifier
                .width(slot)
                .height(38.dp)
                .graphicsLayer {
                    translationX = position.value * stepPx
                    scaleX = 1f + (kotlin.math.abs(position.velocity) * 0.05f).coerceAtMost(0.3f)
                }
                .neu(14.dp, 4.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
            options.forEachIndexed { i, option ->
                val isSel = i == index
                val tint by animateColorAsState(if (isSel) c.accent else c.textMuted, label = "segTint")
                Box(
                    Modifier
                        .weight(1f)
                        .height(38.dp)
                        .pointerInput(option) { detectTapGestures { onSelect(option) } },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label(option), style = MaterialTheme.typography.labelLarge, color = tint, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

@Composable
fun NeuSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    trackColor: Color? = null,
    onValueChangeFinished: () -> Unit = {},
) {
    val c = Neu.colors
    val latest by rememberUpdatedState(onValueChange)
    BoxWithConstraints(modifier.fillMaxWidth().height(36.dp), contentAlignment = Alignment.CenterStart) {
        val thumb = 28.dp
        val widthPx = constraints.maxWidth.toFloat()
        val thumbPx = with(LocalDensity.current) { thumb.toPx() }
        fun valueAt(x: Float) = ((x - thumbPx / 2) / (widthPx - thumbPx)).coerceIn(0f, 1f)
        Box(
            Modifier
                .fillMaxWidth()
                .height(14.dp)
                .neu(7.dp, elevation = 0.dp, inset = 3.dp),
        )
        Box(
            Modifier
                .padding(horizontal = 3.dp)
                .width((maxWidth - 6.dp) * value.coerceIn(0.02f, 1f))
                .height(8.dp)
                .neu(4.dp, elevation = 0.dp, fill = trackColor ?: c.primary),
        )
        Box(
            Modifier
                .offset { IntOffset(((widthPx - thumbPx) * value).roundToInt(), 0) }
                .size(thumb)
                .neu(14.dp, elevation = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(10.dp).neu(5.dp, elevation = 0.dp, fill = trackColor ?: c.primary))
        }
        Box(
            Modifier
                .matchParentSize()
                .pointerInput(widthPx) {
                    detectTapGestures { latest(valueAt(it.x)); onValueChangeFinished() }
                }
                .pointerInput(widthPx) {
                    detectHorizontalDragGestures(onDragEnd = onValueChangeFinished) { change, _ ->
                        latest(valueAt(change.position.x))
                    }
                },
        )
    }
}

// --- Text input ---------------------------------------------------------------------------------

@Composable
fun NeuTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    focusRequester: androidx.compose.ui.focus.FocusRequester? = null,
) {
    val c = Neu.colors
    NeuWell(modifier.fillMaxWidth()) {
        Box(Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
            if (value.isEmpty()) Text(placeholder, color = c.textMuted, style = MaterialTheme.typography.bodyLarge)
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = singleLine,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = c.text),
                cursorBrush = SolidColor(c.primary),
                modifier = Modifier.fillMaxWidth().then(focusRequester?.let { Modifier.focusRequester(it) } ?: Modifier),
            )
        }
    }
}

// --- Wheel picker -------------------------------------------------------------------------------

/** Endless, snapping wheel used for hours and minutes. */
@Composable
fun WheelPicker(
    count: Int,
    selected: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    itemHeight: Dp = 60.dp,
    textStyle: TextStyle = MaterialTheme.typography.displayMedium,
    cyclic: Boolean = true,
    label: (Int) -> String,
) {
    val c = Neu.colors
    val total = if (cyclic) count * 400 else count
    val base = if (cyclic) total / 2 - (total / 2) % count else 0
    val state = rememberLazyListState(initialFirstVisibleItemIndex = base + selected)
    val fling = rememberSnapFlingBehavior(lazyListState = state)
    val itemPx = with(LocalDensity.current) { itemHeight.toPx() }
    val center by remember {
        derivedStateOf { state.firstVisibleItemIndex + if (state.firstVisibleItemScrollOffset > itemPx / 2) 1 else 0 }
    }
    val haptics = com.hblaze.apexy.ui.rememberHaptics()
    val latest by rememberUpdatedState(onSelected)

    // A tick for every number that crosses the selection band, like a real dial.
    LaunchedEffect(state) {
        snapshotFlow { center }.distinctUntilChanged().drop(1).collect { haptics.tick(); com.hblaze.apexy.ui.UiSounds.play(com.hblaze.apexy.ui.UiSounds.Kind.TICK) }
    }
    LaunchedEffect(state) {
        snapshotFlow { state.isScrollInProgress }.filter { !it }.collect { latest(center % count) }
    }
    LaunchedEffect(selected) {
        if (center % count != selected) state.scrollToItem(center - center % count + selected)
    }

    LazyColumn(
        state = state,
        flingBehavior = fling,
        modifier = modifier.height(itemHeight * 3),
        contentPadding = PaddingValues(vertical = itemHeight),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        items(total) { index ->
            Box(
                Modifier
                    .height(itemHeight)
                    .fillMaxWidth()
                    // Drum effect: each number tilts, shrinks and fades by its distance from the
                    // selection band. Read in the layer block so scrolling never recomposes.
                    .graphicsLayer {
                        val info = state.layoutInfo
                        val item = info.visibleItemsInfo.firstOrNull { it.index == index } ?: return@graphicsLayer
                        val mid = (info.viewportStartOffset + info.viewportEndOffset) / 2f
                        val d = ((item.offset + item.size / 2f - mid) / item.size).coerceIn(-1.6f, 1.6f)
                        val ad = kotlin.math.abs(d)
                        rotationX = -d * 32f
                        scaleX = 1f - 0.16f * ad
                        scaleY = 1f - 0.16f * ad
                        translationY = d * item.size * 0.06f
                        alpha = (1f - 0.55f * ad).coerceAtLeast(0.15f)
                        cameraDistance = 10f * density
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(label(index % count), style = textStyle, color = c.text)
            }
        }
    }
}

// --- Headings -----------------------------------------------------------------------------------

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = Neu.colors.textMuted,
        modifier = modifier.padding(start = 6.dp, bottom = 10.dp),
    )
}

@Composable
fun SettingRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    trailing: @Composable () -> Unit = {},
) {
    val c = Neu.colors
    Row(modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) {
            Box(Modifier.size(40.dp).neu(14.dp, elevation = 0.dp, inset = 3.dp), contentAlignment = Alignment.Center) {
                Icon(icon, null, Modifier.size(20.dp), tint = c.accent)
            }
            Box(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = c.text)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = c.textMuted)
        }
        trailing()
    }
}

/** Icon-first empty state: a sunken circle with an icon and a few words. */
@Composable
fun EmptyHint(icon: ImageVector, text: String, modifier: Modifier = Modifier) {
    val c = Neu.colors
    Column(modifier.fillMaxWidth().padding(vertical = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(72.dp).neu(36.dp, elevation = 0.dp, inset = 6.dp), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = c.textMuted, modifier = Modifier.size(30.dp))
        }
        Spacer(Modifier.height(10.dp))
        Text(text, style = MaterialTheme.typography.titleMedium, color = c.textMuted)
    }
}
