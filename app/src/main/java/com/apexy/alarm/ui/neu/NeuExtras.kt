package com.apexy.alarm.ui.neu

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Circular progress: an inset track with a primary arc and [content] in the middle. */
@Composable
fun NeuProgressRing(progress: Float, modifier: Modifier = Modifier, size: Dp = 240.dp, content: @Composable () -> Unit) {
    val c = Neu.colors
    val animated by animateFloatAsState(progress.coerceIn(0f, 1f), label = "ring")
    Box(modifier.size(size).neu(size / 2, elevation = 12.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.size(size - 28.dp).neu(size / 2, elevation = 0.dp, inset = 8.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(size - 52.dp)) {
                drawArc(
                    color = c.accent,
                    startAngle = -90f,
                    sweepAngle = 360f * animated,
                    useCenter = false,
                    style = Stroke(width = 12.dp.toPx(), cap = StrokeCap.Round),
                )
            }
            content()
        }
    }
}

/** On-screen digits so math answers never depend on the system keyboard. */
@Composable
fun NumberPad(onDigit: (Char) -> Unit, onBackspace: () -> Unit, onSubmit: () -> Unit, modifier: Modifier = Modifier) {
    val rows = listOf("123", "456", "789")
    Column(modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                row.forEach { d -> PadKey(Modifier.weight(1f), onClick = { onDigit(d) }) { Text("$d", fontSize = 26.sp) } }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            PadKey(Modifier.weight(1f), onClick = onBackspace) { Icon(Icons.AutoMirrored.Rounded.Backspace, "Delete") }
            PadKey(Modifier.weight(1f), onClick = { onDigit('0') }) { Text("0", fontSize = 26.sp) }
            PadKey(Modifier.weight(1f), primary = true, onClick = onSubmit) { Icon(Icons.Rounded.Check, "Submit") }
        }
    }
}

@Composable
private fun PadKey(modifier: Modifier, primary: Boolean = false, onClick: () -> Unit, content: @Composable () -> Unit) {
    NeuButton(onClick, modifier.height(62.dp), primary = primary, cornerRadius = 20.dp, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
        content()
    }
}

/** − value + control for whole-number settings. */
@Composable
fun NeuStepper(
    value: Int,
    onChange: (Int) -> Unit,
    range: IntRange,
    step: Int = 1,
    modifier: Modifier = Modifier,
    suffix: String = "",
    display: (Int) -> String = { "$it$suffix" },
) {
    val c = Neu.colors
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        NeuIconButton(Icons.Rounded.Remove, "Less", { onChange((value - step).coerceIn(range)) }, size = 48.dp)
        Box(
            Modifier.weight(1f).padding(horizontal = 14.dp).height(56.dp).neu(18.dp, elevation = 0.dp, inset = 5.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(display(value), fontSize = 24.sp, fontWeight = FontWeight.Medium, color = c.text)
        }
        NeuIconButton(Icons.Rounded.Add, "More", { onChange((value + step).coerceIn(range)) }, size = 48.dp)
    }
}

@Composable
fun MissionCounter(done: Int, total: Int, unit: String) {
    val c = Neu.colors
    NeuProgressRing(done.toFloat() / total) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("${(total - done).coerceAtLeast(0)}", fontSize = 64.sp, fontWeight = FontWeight.Light, color = c.text)
            Text("$unit left", style = MaterialTheme.typography.labelLarge, color = c.textMuted)
        }
    }
}
