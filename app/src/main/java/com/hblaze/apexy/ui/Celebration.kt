package com.hblaze.apexy.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hblaze.apexy.ApexyApp
import com.hblaze.apexy.core.MissionStats
import com.hblaze.apexy.stats.WakeStats
import com.hblaze.apexy.stats.formatSeconds
import com.hblaze.apexy.ui.neu.Neu
import com.hblaze.apexy.ui.neu.NeuButton
import com.hblaze.apexy.ui.neu.neu
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** Shown once every mission is done: the alarm is already silent, this is the reward. */
@Composable
fun CelebrationScreen(stats: MissionStats, onClose: () -> Unit) {
    val c = Neu.colors
    val haptics = rememberHaptics()
    val pop = remember { Animatable(0f) }
    val tick = remember { Animatable(0f) }
    val rings = remember { Animatable(0f) }
    var showText by remember { mutableStateOf(false) }
    var streak by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(Unit) {
        haptics.success()
        launch { pop.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)) }
        launch { delay(180); tick.animateTo(1f, tween(420, easing = FastOutSlowInEasing)) }
        launch { rings.animateTo(1f, tween(1400, easing = LinearEasing)) }
        delay(350)
        showText = true
        // The dismissal is being written right now; give it a moment, then count the streak.
        delay(700)
        streak = runCatching {
            val events = ApexyApp.instance.db.events().observeAll().first()
            WakeStats.streaks(WakeStats.sessions(events), LocalDate.now()).first
        }.getOrNull()
        delay(14_000)
        onClose()
    }

    Box(Modifier.fillMaxSize().background(c.surface)) {
        Confetti(Modifier.fillMaxSize())
        Column(
            Modifier.fillMaxSize().systemBarsPadding().padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(1f))
            Box(Modifier.size(260.dp), contentAlignment = Alignment.Center) {
                // Two soft rings ripple out behind the badge.
                Canvas(Modifier.fillMaxSize()) {
                    for (k in 0..1) {
                        val p = ((rings.value - k * 0.25f) / 0.75f).coerceIn(0f, 1f)
                        if (p in 0.001f..0.999f) drawCircle(
                            c.primary.copy(alpha = (1f - p) * 0.35f),
                            radius = size.minDimension / 2 * (0.55f + 0.45f * p),
                            style = Stroke(width = 6.dp.toPx() * (1f - p)),
                        )
                    }
                }
                Box(Modifier.size(190.dp).scale(pop.value).neu(95.dp, elevation = 14.dp), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(140.dp).neu(70.dp, elevation = 0.dp, inset = 8.dp), contentAlignment = Alignment.Center) {
                        Box(Modifier.size(96.dp).neu(48.dp, elevation = 6.dp, fill = c.primary), contentAlignment = Alignment.Center) {
                            CheckMark(tick.value, c.onPrimary, Modifier.size(52.dp))
                        }
                    }
                }
            }
            Spacer(Modifier.height(28.dp))
            AnimatedVisibility(showText, enter = fadeIn(tween(400)) + slideInVertically { it / 3 }) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(greeting(), style = MaterialTheme.typography.titleMedium, color = c.textMuted)
                    Text("You're up!", fontSize = 44.sp, fontWeight = FontWeight.Light, color = c.text)
                    Spacer(Modifier.height(22.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        StatPill(Icons.Rounded.Extension, "${stats.count}")
                        if (stats.ms > 0) StatPill(Icons.Rounded.Timer, formatSeconds(stats.ms / 1000))
                        streak?.takeIf { it > 0 }?.let { StatPill(Icons.Rounded.LocalFireDepartment, "$it") }
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            NeuButton(onClose, Modifier.fillMaxWidth().height(68.dp), primary = true, cornerRadius = 24.dp, contentPadding = PaddingValues(16.dp)) {
                Text("Start my day", fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun StatPill(icon: ImageVector, value: String) {
    val c = Neu.colors
    Row(
        Modifier.neu(18.dp, elevation = 5.dp).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = c.accent, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(value, style = MaterialTheme.typography.titleMedium, color = c.text)
    }
}

/** A check mark that draws itself as [progress] goes 0 → 1. */
@Composable
private fun CheckMark(progress: Float, color: Color, modifier: Modifier) {
    Canvas(modifier) {
        val w = size.width
        val full = Path().apply {
            moveTo(w * 0.18f, w * 0.52f)
            lineTo(w * 0.42f, w * 0.75f)
            lineTo(w * 0.84f, w * 0.28f)
        }
        val measure = PathMeasure().apply { setPath(full, false) }
        val partial = Path()
        measure.getSegment(0f, measure.length * progress, partial, true)
        drawPath(partial, color, style = Stroke(width = w * 0.12f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

private class Piece(
    var x: Float, var y: Float, var vx: Float, var vy: Float,
    var angle: Float, val spin: Float, val color: Color, val w: Float, val h: Float, val round: Boolean,
)

/** One burst of confetti from the badge, in the app's own colour family. */
@Composable
private fun Confetti(modifier: Modifier) {
    val c = Neu.colors
    var frame by remember { mutableLongStateOf(0L) }
    val pieces = remember { mutableListOf<Piece>() }
    // Plain holder, not state: it's set during the first draw, once the canvas size is known.
    val started = remember { booleanArrayOf(false) }
    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        var last = start
        while (true) {
            val now = withFrameNanos { it }
            val dt = ((now - last) / 1e9f).coerceAtMost(0.05f)
            last = now
            pieces.forEach { p ->
                p.vy += 900f * dt
                p.vx *= 0.99f
                p.x += p.vx * dt
                p.y += p.vy * dt
                p.angle += p.spin * dt
            }
            frame = now
            if (now - start > 4_500_000_000L) break
        }
    }
    val palette = listOf(c.primary, lerp(c.primary, Color.White, 0.45f), lerp(c.primary, Color.Black, 0.25f), Color(0xFFF5A524), c.success)
    Canvas(modifier) {
        if (!started[0]) {
            started[0] = true
            val rnd = Random(System.nanoTime())
            val origin = Offset(size.width / 2, size.height * 0.42f)
            repeat(110) {
                val a = -PI / 2 + (rnd.nextDouble() - 0.5) * PI * 1.3
                val speed = 700f + rnd.nextFloat() * 1100f
                pieces += Piece(
                    origin.x, origin.y,
                    (cos(a) * speed).toFloat(), (sin(a) * speed).toFloat(),
                    rnd.nextFloat() * 360f, (rnd.nextFloat() - 0.5f) * 720f,
                    palette[rnd.nextInt(palette.size)],
                    (5 + rnd.nextInt(5)).dp.toPx(), (9 + rnd.nextInt(8)).dp.toPx(), rnd.nextInt(4) == 0,
                )
            }
        }
        frame // read so every frame redraws
        pieces.forEach { p ->
            if (p.y > size.height + 40f) return@forEach
            rotate(p.angle, Offset(p.x, p.y)) {
                if (p.round) drawCircle(p.color, radius = p.w / 2, center = Offset(p.x, p.y))
                else drawRoundRect(p.color, Offset(p.x - p.w / 2, p.y - p.h / 2), Size(p.w, p.h), CornerRadius(2.dp.toPx()))
            }
        }
    }
}

private fun greeting(): String = when (LocalTime.now().hour) {
    in 4..11 -> "Good morning"
    in 12..16 -> "Good afternoon"
    in 17..21 -> "Good evening"
    else -> "Rise and shine"
}
