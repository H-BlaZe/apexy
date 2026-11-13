package com.hblaze.apexy.ui.neu

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.hblaze.apexy.sleep.Stage

/**
 * Sleep stages over the night: one row per stage, labelled on the axis, so position (not colour)
 * carries the stage. Snore clips show as ticks under the timeline. Tap for the time and stage.
 */
@Composable
fun Hypnogram(
    stages: List<Stage>,
    snoreMinutes: List<Int>,
    timeLabel: (Int) -> String,
    modifier: Modifier = Modifier,
) {
    val c = Neu.colors
    val measurer = rememberTextMeasurer()
    val axis = MaterialTheme.typography.labelMedium.copy(color = c.textMuted)
    val tip = MaterialTheme.typography.labelLarge.copy(color = c.text)
    var selected by remember(stages) { mutableStateOf<Int?>(null) }
    val rows = listOf(Stage.AWAKE to "Awake", Stage.LIGHT to "Light", Stage.DEEP to "Deep")

    Canvas(
        modifier.fillMaxWidth().height(200.dp).pointerInput(stages) {
            detectTapGestures { p ->
                val left = 52.dp.toPx()
                val i = ((p.x - left) / (size.width - left) * stages.size).toInt()
                selected = if (i in stages.indices && i != selected) i else null
            }
        },
    ) {
        if (stages.isEmpty()) return@Canvas
        val left = 52.dp.toPx()
        val top = 28.dp.toPx()
        val rowH = 36.dp.toPx()
        val gap = 6.dp.toPx()
        val w = size.width - left
        val perMin = w / stages.size

        rows.forEachIndexed { r, (_, name) ->
            val y = top + r * (rowH + gap)
            drawRoundRect(c.textMuted.copy(alpha = 0.08f), Offset(left, y), Size(w, rowH), CornerRadius(8.dp.toPx()))
            val layout = measurer.measure(name, axis)
            drawText(layout, topLeft = Offset(0f, y + (rowH - layout.size.height) / 2))
        }
        // Merge runs of the same stage into rounded blocks.
        var start = 0
        for (i in 1..stages.size) {
            if (i == stages.size || stages[i] != stages[start]) {
                val r = rows.indexOfFirst { it.first == stages[start] }
                val y = top + r * (rowH + gap)
                val color = when (stages[start]) {
                    Stage.DEEP -> c.primary
                    Stage.LIGHT -> c.primary.copy(alpha = 0.55f)
                    Stage.AWAKE -> c.textMuted.copy(alpha = 0.7f)
                }
                drawRoundRect(color, Offset(left + start * perMin, y + 3.dp.toPx()), Size(((i - start) * perMin).coerceAtLeast(2f), rowH - 6.dp.toPx()), CornerRadius(4.dp.toPx()))
                start = i
            }
        }
        val snoreY = top + 3 * (rowH + gap)
        snoreMinutes.forEach { m ->
            if (m in stages.indices) drawRoundRect(c.primary, Offset(left + m * perMin - 1.5f, snoreY), Size(3.dp.toPx(), 8.dp.toPx()), CornerRadius(1.5f))
        }
        val labelY = snoreY + 12.dp.toPx()
        listOf(0, stages.size / 2, stages.size - 1).distinct().forEach { m ->
            val layout = measurer.measure(timeLabel(m), axis)
            val x = (left + m * perMin - layout.size.width / 2f).coerceIn(left, size.width - layout.size.width)
            drawText(layout, topLeft = Offset(x, labelY))
        }
        selected?.let { m ->
            val text = "${timeLabel(m)} · ${stages[m].name.lowercase().replaceFirstChar { it.uppercase() }}"
            val layout = measurer.measure(text, tip)
            val pad = 8.dp.toPx()
            val bw = layout.size.width + pad * 2
            val x = (left + m * perMin - bw / 2).coerceIn(0f, size.width - bw)
            drawLine(c.text.copy(alpha = 0.5f), Offset(left + m * perMin, top - 2.dp.toPx()), Offset(left + m * perMin, snoreY), strokeWidth = 1.dp.toPx())
            drawRoundRect(c.surface, Offset(x, 0f), Size(bw, layout.size.height + pad), CornerRadius(10.dp.toPx()))
            drawText(layout, topLeft = Offset(x + pad, pad / 2))
        }
    }
}
