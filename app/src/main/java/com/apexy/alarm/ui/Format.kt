package com.apexy.alarm.ui

import android.content.Context
import android.text.format.DateFormat
import com.apexy.alarm.data.Alarm

val DAY_LETTERS = listOf("M", "T", "W", "T", "F", "S", "S")
val DAY_NAMES = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
const val WEEKDAYS = 0b0011111
const val WEEKENDS = 0b1100000
const val EVERY_DAY = 0b1111111

fun formatTime(context: Context, hour: Int, minute: Int): String =
    if (DateFormat.is24HourFormat(context)) "%02d:%02d".format(hour, minute)
    else "%d:%02d %s".format(if (hour % 12 == 0) 12 else hour % 12, minute, if (hour < 12) "AM" else "PM")

/** Splits the clock face from the AM/PM suffix so they can be styled separately. */
fun timeParts(context: Context, hour: Int, minute: Int): Pair<String, String?> =
    if (DateFormat.is24HourFormat(context)) "%02d:%02d".format(hour, minute) to null
    else "%d:%02d".format(if (hour % 12 == 0) 12 else hour % 12, minute) to if (hour < 12) "AM" else "PM"

fun daysSummary(alarm: Alarm): String = when (alarm.days) {
    0 -> if (alarm.isQuick) "Quick alarm" else "Once"
    EVERY_DAY -> "Every day"
    WEEKDAYS -> "Weekdays"
    WEEKENDS -> "Weekends"
    else -> DAY_NAMES.filterIndexed { i, _ -> alarm.hasDay(i) }.joinToString(", ")
}

fun durationUntil(target: Long, now: Long = System.currentTimeMillis()): String {
    val totalMin = ((target - now + 59_999) / 60_000).coerceAtLeast(0)
    val d = totalMin / 1440
    val h = (totalMin % 1440) / 60
    val m = totalMin % 60
    return buildList {
        if (d > 0) add("${d}d")
        if (h > 0) add("${h}h")
        if (m > 0 || (d == 0L && h == 0L)) add("${m}m")
    }.joinToString(" ")
}
