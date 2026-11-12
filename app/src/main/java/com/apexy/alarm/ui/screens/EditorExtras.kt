package com.apexy.alarm.ui.screens

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.apexy.alarm.core.AlarmService
import com.apexy.alarm.data.Alarm
import com.apexy.alarm.ui.neu.Neu
import com.apexy.alarm.ui.neu.NeuButton
import com.apexy.alarm.ui.neu.NeuCard
import com.apexy.alarm.ui.neu.neu

private fun vibrator(context: Context): Vibrator =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) context.getSystemService(VibratorManager::class.java).defaultVibrator
    else @Suppress("DEPRECATION") (context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator)

/** Plays one cycle of a pattern so the user can feel it while choosing. */
fun previewVibration(context: Context, pattern: String) {
    runCatching { vibrator(context).vibrate(VibrationEffect.createWaveform(AlarmService.vibrationTimings(pattern), -1)) }
}

fun shrinkHint(a: Alarm): String {
    val lengths = (0 until minOf(4, if (a.maxSnoozes < 0) 4 else a.maxSnoozes)).map { (a.snoozeMinutes - it).coerceAtLeast(1) }
    return lengths.joinToString(" → ") { "${it}m" }
}

/** Little curve showing how the volume rises from its start level. */
@Composable
fun RampPreview(alarm: Alarm) {
    val c = Neu.colors
    Box(Modifier.fillMaxWidth().height(64.dp).neu(16.dp, elevation = 0.dp, inset = 3.dp)) {
        Canvas(Modifier.fillMaxWidth().height(64.dp)) {
            val pad = 12.dp.toPx()
            val w = size.width - pad * 2
            val h = size.height - pad * 2
            val path = Path()
            for (i in 0..40) {
                val t = i / 40f
                val x = pad + w * t
                val y = pad + h * (1f - AlarmService.rampAt(alarm, t))
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, c.primary, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
        }
    }
}

/**
 * Press and hold to buzz, let go to pause. The rhythm tapped here becomes the alarm's vibration,
 * stored as "custom:on,off,on,off…" in milliseconds.
 */
@Composable
fun VibrationRecorder(onSave: (String) -> Unit, onCancel: () -> Unit) {
    val c = Neu.colors
    val context = LocalContext.current
    val segments = remember { mutableStateListOf<Long>() }
    var holding by remember { mutableStateOf(false) }
    var lastUp by remember { mutableStateOf(0L) }
    val total = segments.sum()
    val full = total >= MAX_MS

    Dialog(onDismissRequest = onCancel) {
        NeuCard(Modifier.fillMaxWidth(), cornerRadius = 30.dp, padding = PaddingValues(24.dp)) {
            Text("Tap your rhythm", style = MaterialTheme.typography.titleLarge, color = c.text)
            Text("Hold to buzz, release to pause.", style = MaterialTheme.typography.bodyMedium, color = c.textMuted)
            Spacer(Modifier.height(18.dp))
            // Timeline of what's been recorded: filled bars are buzzes.
            Canvas(Modifier.fillMaxWidth().height(28.dp)) {
                var x = 0f
                segments.forEachIndexed { i, ms ->
                    val w = size.width * ms / MAX_MS
                    if (i % 2 == 0) drawRoundRect(c.primary, Offset(x, 4f), Size(w.coerceAtLeast(3f), size.height - 8f), CornerRadius(6f))
                    x += w
                }
                drawLine(c.textMuted.copy(alpha = 0.3f), Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 1.5f)
            }
            Spacer(Modifier.height(18.dp))
            Box(
                Modifier.align(Alignment.CenterHorizontally).size(150.dp)
                    .neu(75.dp, elevation = if (holding) 0.dp else 10.dp, inset = if (holding) 8.dp else 0.dp)
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown()
                            if (segments.sum() >= MAX_MS) return@awaitEachGesture
                            val down = System.currentTimeMillis()
                            // The pause since the last release, unless this is the first buzz.
                            if (segments.isNotEmpty()) segments += (down - lastUp).coerceIn(40, 3_000)
                            holding = true
                            runCatching { vibrator(context).vibrate(VibrationEffect.createOneShot(5_000, VibrationEffect.DEFAULT_AMPLITUDE)) }
                            waitForUpOrCancellation()
                            vibrator(context).cancel()
                            holding = false
                            lastUp = System.currentTimeMillis()
                            segments += (lastUp - down).coerceIn(30, 3_000)
                        }
                    },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.TouchApp, null, tint = if (holding) c.accent else c.textMuted, modifier = Modifier.size(48.dp)) }
            if (full) Text("8s max", style = MaterialTheme.typography.bodyMedium, color = c.textMuted, modifier = Modifier.align(Alignment.CenterHorizontally))
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                NeuButton({ segments.clear() }, Modifier.weight(1f)) { Text("Clear") }
                NeuButton({
                    if (segments.size >= 1) {
                        // Every loop needs a closing pause so buzzes don't run together.
                        val withGap = if (segments.size % 2 == 1) segments + 600L else segments.toList()
                        previewVibration(context, AlarmService.CUSTOM_VIBRATION + withGap.joinToString(","))
                    }
                }, Modifier.weight(1f), enabled = segments.isNotEmpty()) { Text("Play") }
                NeuButton({
                    val withGap = if (segments.size % 2 == 1) segments + 600L else segments.toList()
                    onSave(AlarmService.CUSTOM_VIBRATION + withGap.joinToString(","))
                }, Modifier.weight(1f), primary = true, enabled = segments.isNotEmpty()) { Text("Save") }
            }
        }
    }
}

private const val MAX_MS = 8_000L
