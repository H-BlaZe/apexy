package com.hblaze.apexy.ui.neu

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hblaze.apexy.ui.rememberHaptics
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * A primary button that answers with a little celebration: the label pops into a check mark, a ring
 * and sparks burst outwards, and the phone gives a "ta-da" tap.
 *
 * [onConfirm] does the work and returns whether it succeeded (false = no celebration, e.g. a lock
 * dialog was shown instead). [onFinished] runs once the effect has played.
 */
@Composable
fun NeuDoneButton(
    text: String,
    onConfirm: suspend () -> Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    cornerRadius: Dp = 18.dp,
    contentPadding: PaddingValues = PaddingValues(horizontal = 24.dp, vertical = 13.dp),
    textStyle: TextStyle? = null,
    onFinished: () -> Unit = {},
) {
    val c = Neu.colors
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    var done by remember { mutableStateOf(false) }
    val burst = remember { Animatable(0f) }

    Box(
        modifier
            .scale(1f + 0.08f * sin(PI * burst.value).toFloat())
            .drawWithContent {
                drawContent()
                val p = burst.value
                if (p <= 0f || p >= 1f) return@drawWithContent
                val reach = 22.dp.toPx() * p
                val r = cornerRadius.toPx() + reach
                // A ring the shape of the button, swelling outwards and fading.
                drawRoundRect(
                    c.primary.copy(alpha = (1f - p) * 0.6f),
                    topLeft = Offset(-reach, -reach),
                    size = Size(size.width + reach * 2, size.height + reach * 2),
                    cornerRadius = CornerRadius(r, r),
                    style = Stroke(width = (3.dp.toPx() * (1f - p)).coerceAtLeast(0.5f)),
                )
                // Sparks thrown out from around the edge.
                val cx = size.width / 2
                val cy = size.height / 2
                for (k in 0 until 10) {
                    val a = 2 * PI * k / 10 + PI / 10
                    val dist = 1f + 0.55f * p
                    val o = Offset(cx + (cos(a) * (cx + 14.dp.toPx() * p) * dist).toFloat(), cy + (sin(a) * (cy + 14.dp.toPx() * p) * dist).toFloat())
                    drawCircle(c.primary.copy(alpha = 1f - p), radius = 3.5.dp.toPx() * (1f - p * 0.5f), center = o)
                }
            },
        contentAlignment = Alignment.Center,
        // Lets the button fill a weighted/fixed width while still wrapping its label otherwise.
        propagateMinConstraints = true,
    ) {
        NeuButton(
            onClick = {
                if (done) return@NeuButton
                scope.launch {
                    if (!onConfirm()) return@launch
                    done = true
                    haptics.success()
                    com.hblaze.apexy.ui.UiSounds.play(com.hblaze.apexy.ui.UiSounds.Kind.CONFIRM)
                    launch { burst.snapTo(0f); burst.animateTo(1f, tween(700, easing = FastOutSlowInEasing)) }
                    delay(720)
                    onFinished()
                }
            },
            primary = true,
            enabled = enabled,
            cornerRadius = cornerRadius,
            contentPadding = contentPadding,
        ) {
            AnimatedContent(
                done,
                transitionSpec = {
                    (scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium), initialScale = 0.3f) + fadeIn()) togetherWith fadeOut()
                },
                label = "done",
            ) { isDone ->
                if (isDone) Icon(Icons.Rounded.Check, "Done", tint = c.onPrimary, modifier = Modifier.size(22.dp))
                else if (textStyle != null) Text(text, style = textStyle) else Text(text)
            }
        }
    }
}
