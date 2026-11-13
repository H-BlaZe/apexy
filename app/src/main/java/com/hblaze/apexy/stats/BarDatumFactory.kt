package com.hblaze.apexy.stats

import com.hblaze.apexy.ui.neu.BarDatum
import java.time.format.TextStyle
import java.util.Locale

/** Turns daily points into chart columns with the tooltip text for each day. */
object BarDatumFactory {
    fun from(days: List<DayPoint>): List<BarDatum> = days.map { d ->
        val short = d.date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())
        val label = if (days.size > 7) "${d.date.dayOfMonth}" else short
        val detail = buildString {
            append("$short ${d.date.dayOfMonth} · ")
            when {
                d.sessions == 0 -> append("no alarms")
                d.avgMinutesToGetUp != null -> append("up in ${formatSeconds((d.avgMinutesToGetUp * 60).toLong())}")
                else -> append("not up")
            }
            if (d.missed > 0) append(" · ${d.missed} missed")
        }
        BarDatum(label, d.avgMinutesToGetUp, flagged = d.missed > 0, detail = detail)
    }
}
