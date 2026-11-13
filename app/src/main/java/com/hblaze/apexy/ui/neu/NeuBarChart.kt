package com.hblaze.apexy.ui.neu

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/** One column. [value] null means no data that day; [flagged] marks a status (e.g. a missed alarm). */
data class BarDatum(val label: String, val value: Float?, val flagged: Boolean = false, val detail: String = "")

/**
 * Single-series column chart: thin columns with 4dp rounded caps grown from one baseline,
 * hairline grid, recessive axis text, a tap tooltip and flagged days marked at the baseline.
 */
@Composable
fun NeuBarChart(
    data: List<BarDatum>,
    selected: Int?,
    onSelect: (Int?) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 190.dp,
    unit: String = "",
    labelEvery: Int = 1,
) {
    val c = Neu.colors
    val measurer = rememberTextMeasurer()
    val axisStyle = MaterialTheme.typography.labelMedium.copy(color = c.textMuted)
    val tipStyle = MaterialTheme.typography.labelLarge.copy(color = c.text)
    val max = data.mapNotNull { it.value }.maxOrNull() ?: 0f
    val top = niceCeil(max)

    Canvas(
        modifier.fillMaxWidth().height(height).pointerInput(data) {
            detectTapGestures { pos ->
                val left = 34.dp.toPx()
                val slot = (size.width - left) / data.size
                val i = ((pos.x - left) / slot).toInt()
                onSelect(if (i in data.indices && i != selected) i else null)
            }
        },
    ) {
        val left = 34.dp.toPx()
        val bottom = size.height - 22.dp.toPx()
        val topPad = 30.dp.toPx()
        val plotH = bottom - topPad
        val slot = (size.width - left) / data.size
        val barW = min(24.dp.toPx(), slot - 2.dp.toPx()).coerceAtLeast(2.dp.toPx())
        val grid = c.textMuted.copy(alpha = 0.18f)

        // Hairline grid at 0, ½ and top, with clean tick labels.
        for (frac in listOf(0f, 0.5f, 1f)) {
            val y = bottom - plotH * frac
            drawLine(grid, Offset(left, y), Offset(size.width, y), strokeWidth = 1f)
            val tick = formatTick(top * frac)
            val layout = measurer.measure(tick, axisStyle)
            drawText(layout, topLeft = Offset(left - layout.size.width - 6.dp.toPx(), y - layout.size.height / 2f))
        }

        data.forEachIndexed { i, d ->
            val cx = left + slot * i + slot / 2
            val isSel = i == selected
            d.value?.takeIf { it > 0f && top > 0f }?.let { v ->
                val h = max(plotH * (v / top), 3.dp.toPx())
                val r = min(4.dp.toPx(), barW / 2)
                val path = Path().apply {
                    addRoundRect(
                        RoundRect(
                            left = cx - barW / 2, top = bottom - h, right = cx + barW / 2, bottom = bottom,
                            topLeftCornerRadius = CornerRadius(r), topRightCornerRadius = CornerRadius(r),
                        ),
                    )
                }
                drawPath(path, c.primary.copy(alpha = if (selected == null || isSel) 1f else 0.45f))
            }
            if (d.flagged) {
                // Status marker sits on the baseline; the tooltip and caption carry the meaning.
                drawCircle(c.surface, radius = 6.dp.toPx(), center = Offset(cx, bottom))
                drawCircle(c.danger, radius = 4.dp.toPx(), center = Offset(cx, bottom))
            }
            if (i % labelEvery == 0 || i == data.lastIndex) {
                val layout = measurer.measure(d.label, axisStyle.copy(color = if (isSel) c.text else c.textMuted))
                drawText(layout, topLeft = Offset(cx - layout.size.width / 2f, bottom + 6.dp.toPx()))
            }
        }

        selected?.let { i ->
            val d = data.getOrNull(i) ?: return@let
            val text = d.detail.ifBlank { d.value?.let { "${formatTick(it)}$unit" } ?: "No alarm" }
            val layout = measurer.measure(text, tipStyle)
            val pad = 8.dp.toPx()
            val w = layout.size.width + pad * 2
            val h = layout.size.height + pad
            val cx = left + slot * i + slot / 2
            val x = (cx - w / 2).coerceIn(0f, size.width - w)
            drawRoundRect(c.shadowDark.copy(alpha = 0.35f), Offset(x + 2.dp.toPx(), 2.dp.toPx()), Size(w, h), CornerRadius(10.dp.toPx()))
            drawRoundRect(c.surface, Offset(x, 0f), Size(w, h), CornerRadius(10.dp.toPx()))
            drawText(layout, topLeft = Offset(x + pad, pad / 2))
        }
    }
}

private fun niceCeil(v: Float): Float = when {
    v <= 0f -> 1f
    v <= 1f -> 1f
    v <= 2f -> 2f
    v <= 5f -> 5f
    v <= 10f -> 10f
    else -> (ceil(v / 10f) * 10f)
}

private fun formatTick(v: Float): String = if (v % 1f == 0f) "${v.toInt()}" else "%.1f".format(v)
