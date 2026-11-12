package com.apexy.alarm.ui.screens

import com.apexy.alarm.ui.neu.Reveal
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import com.apexy.alarm.ui.neu.CollapsedBar
import com.apexy.alarm.ui.neu.collapsingTitle
import com.apexy.alarm.ui.neu.rememberScrollSignal
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AlarmOn
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.apexy.alarm.ApexyApp
import com.apexy.alarm.data.Alarm
import com.apexy.alarm.stats.AlarmBreakdown
import com.apexy.alarm.stats.BarDatumFactory
import com.apexy.alarm.stats.Summary
import com.apexy.alarm.stats.WakeSession
import com.apexy.alarm.stats.WakeStats
import com.apexy.alarm.stats.formatSeconds
import com.apexy.alarm.ui.formatTime
import com.apexy.alarm.ui.neu.Neu
import com.apexy.alarm.ui.neu.NeuBarChart
import com.apexy.alarm.ui.neu.NeuButton
import com.apexy.alarm.ui.neu.NeuCard
import com.apexy.alarm.ui.neu.NeuChip
import com.apexy.alarm.ui.neu.NeuSegmented
import com.apexy.alarm.ui.neu.SectionLabel
import com.apexy.alarm.ui.neu.neu
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private enum class Period(val label: String, val days: Int?) { WEEK("7 days", 7), MONTH("30 days", 30), ALL("All time", null) }

private enum class Filter(val label: String, val test: (WakeSession) -> Boolean) {
    ALL("All", { true }),
    WOKE("Woke up", { it.wokeUp }),
    MISSED("Missed", { it.outcome == "missed" }),
    SNOOZED("Snoozed", { it.snoozes > 0 }),
    ASLEEP("Fell asleep", { it.checksFailed > 0 }),
}

@Composable
fun ReportsScreen() {
    val c = Neu.colors
    val app = ApexyApp.instance
    val scope = rememberCoroutineScope()
    val events by app.db.events().observeAll().collectAsStateWithLifecycle(emptyList())
    val alarms by app.db.alarms().observeAll().collectAsStateWithLifecycle(emptyList())
    var period by rememberSaveable { mutableStateOf(Period.WEEK) }
    var filter by rememberSaveable { mutableStateOf(Filter.ALL) }
    var selectedBar by remember(period) { mutableStateOf<Int?>(null) }
    var confirmClear by remember { mutableStateOf(false) }

    val today = LocalDate.now()
    val all = remember(events) { WakeStats.sessions(events) }
    val inPeriod = remember(all, period) {
        val since = period.days?.let { today.minusDays(it - 1L).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() } ?: 0L
        all.filter { it.firstRingAt >= since }
    }
    val summary = remember(inPeriod) { WakeStats.summary(inPeriod) }
    val (streak, best) = remember(all) { WakeStats.streaks(all, today) }
    val days = remember(all, period) { WakeStats.daily(all, period.days ?: 30, today) }
    val chart = remember(days) { BarDatumFactory.from(days) }
    val breakdown = remember(inPeriod) { WakeStats.byAlarm(inPeriod) }
    val history = remember(all, filter) { all.filter(filter.test).asReversed() }
    // Default the tooltip to the latest day with data so a value is always visible.
    val shownBar = selectedBar ?: chart.indexOfLast { it.value != null || it.flagged }.takeIf { it >= 0 }

    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val scrollSignal = rememberScrollSignal(listState)
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            item { Text("Reports", style = MaterialTheme.typography.headlineMedium, color = c.text, modifier = Modifier.collapsingTitle(scrollSignal).padding(start = 4.dp)) }
            item { Reveal(scrollSignal) { NeuSegmented(Period.entries, period, { period = it }, Modifier.fillMaxWidth()) { it.label } } }
            item { Reveal(scrollSignal) { StreakCard(streak, best) } }
            item { Reveal(scrollSignal) { StatGrid(summary) } }
            item { Reveal(scrollSignal) {
                NeuCard(Modifier.fillMaxWidth()) {
                    SectionLabel("Minutes to get up")
                    NeuBarChart(chart, shownBar, { selectedBar = it }, unit = "m", labelEvery = if (chart.size > 7) 5 else 1)
                    if (chart.any { it.flagged }) {
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(8.dp).neu(4.dp, elevation = 0.dp, fill = c.danger))
                            Text("  Missed", style = MaterialTheme.typography.labelMedium, color = c.textMuted)
                        }
                    }
                }
            } }
            item { Reveal(scrollSignal) { MissionsCard(summary) } }
            if (breakdown.size > 1) item { Reveal(scrollSignal) { ByAlarmCard(breakdown, alarms) } }

            item { Reveal(scrollSignal) {
                Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    SectionLabel("History", Modifier.weight(1f))
                    if (events.isNotEmpty()) {
                        NeuButton({ confirmClear = true }, contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)) {
                            Text("Clear", color = c.danger)
                        }
                    }
                }
            } }
            item { Reveal(scrollSignal) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(vertical = 6.dp, horizontal = 4.dp)) {
                    items(Filter.entries) { f -> NeuChip(f.label, filter == f, { filter = f }) }
                }
            } }
            if (history.isEmpty()) item { Reveal(scrollSignal) {
                com.apexy.alarm.ui.neu.EmptyHint(Icons.Rounded.History, if (all.isEmpty()) "No history yet" else "Nothing here")
            } }
            var lastDay: LocalDate? = null
            history.forEach { s ->
                val day = WakeStats.day(s.firstRingAt)
                if (day != lastDay) {
                    lastDay = day
                    item(key = "h$day") {
                        Text(dayHeading(day, today), style = MaterialTheme.typography.labelLarge, color = c.textMuted, modifier = Modifier.padding(start = 6.dp, top = 4.dp))
                    }
                }
                item(key = "s${s.alarmId}-${s.firstRingAt}") { Reveal(scrollSignal) { SessionRow(s) } }
            }
        }
        CollapsedBar("Reports", scrollSignal)
    }

    if (confirmClear) ConfirmClearDialog(
        onDismiss = { confirmClear = false },
        onConfirm = {
            confirmClear = false
            scope.launch { app.db.events().clear() }
        },
    )
}

@Composable
private fun StreakCard(streak: Int, best: Int) {
    val c = Neu.colors
    NeuCard(Modifier.fillMaxWidth(), cornerRadius = 30.dp, padding = PaddingValues(22.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(74.dp).neu(37.dp, elevation = 0.dp, inset = 6.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.size(50.dp).neu(25.dp, elevation = 5.dp, fill = c.primary), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.LocalFireDepartment, null, tint = c.onPrimary, modifier = Modifier.size(28.dp))
                }
            }
            Spacer(Modifier.width(18.dp))
            Column(Modifier.weight(1f)) {
                Text("WAKE-UP STREAK", style = MaterialTheme.typography.labelMedium, color = c.textMuted)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("$streak", fontSize = 40.sp, fontWeight = FontWeight.Light, color = c.text)
                    Text(if (streak == 1) " day" else " days", style = MaterialTheme.typography.titleMedium, color = c.textMuted, modifier = Modifier.padding(bottom = 8.dp))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.EmojiEvents, "Best streak", tint = c.textMuted, modifier = Modifier.size(16.dp))
                    Text("  $best", style = MaterialTheme.typography.titleMedium, color = c.textMuted)
                }
            }
        }
    }
}

@Composable
private fun StatGrid(s: Summary) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            StatTile("Woke up", s.wokeRate?.let { "${(it * 100).toInt()}%" } ?: "–", "${s.sessions} alarms", Modifier.weight(1f))
            StatTile("Time to get up", s.medianSecondsToGetUp?.let(::formatSeconds) ?: "–", "typical", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            StatTile("Snoozes", s.avgSnoozes?.let { "%.1f".format(it) } ?: "–", "per alarm", Modifier.weight(1f))
            StatTile("Consistency", s.consistencyMinutes?.let { "±${it}m" } ?: "–", "wake-up time", Modifier.weight(1f))
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, note: String, modifier: Modifier = Modifier) {
    val c = Neu.colors
    NeuCard(modifier, cornerRadius = 24.dp, padding = PaddingValues(18.dp)) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelMedium, color = c.textMuted)
        Spacer(Modifier.height(6.dp))
        Text(value, fontSize = 26.sp, color = c.text)
        Text(note, style = MaterialTheme.typography.labelMedium, color = c.textMuted)
    }
}

@Composable
private fun MissionsCard(s: Summary) {
    val c = Neu.colors
    NeuCard(Modifier.fillMaxWidth()) {
        SectionLabel("Missions & checks")
        MetricRow("Wake-ups solved with missions", "${s.missionSessions}")
        MetricRow("Average time on missions", s.avgMissionSeconds?.let(::formatSeconds) ?: "–")
        MetricRow("Emergency escapes used", "${s.escapes}")
        MetricRow("Wake-up checks passed", if (s.checksTotal == 0) "–" else "${s.checksPassed} of ${s.checksTotal}")
    }
}

@Composable
private fun MetricRow(label: String, value: String) {
    val c = Neu.colors
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = c.textMuted, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.titleMedium, color = c.text)
    }
}

@Composable
private fun ByAlarmCard(rows: List<AlarmBreakdown>, alarms: List<Alarm>) {
    val c = Neu.colors
    val context = LocalContext.current
    NeuCard(Modifier.fillMaxWidth()) {
        SectionLabel("By alarm")
        // Deleted alarms (finished quick alarms, mostly) merge into one row per label.
        val named = rows.map { r ->
            val alarm = alarms.firstOrNull { it.id == r.alarmId }
            val name = if (alarm != null) {
                listOf(formatTime(context, alarm.hour, alarm.minute), alarm.label).filter { it.isNotBlank() }.joinToString(" · ")
            } else "${r.label.ifBlank { "Alarm" }} (deleted)"
            name to r
        }
        named.groupBy({ it.first }, { it.second }).forEach { (name, group) ->
            val total = group.sumOf { it.sessions }
            val r = group.first().copy(
                sessions = total,
                wokeRate = group.sumOf { (it.wokeRate ?: 0f).toDouble() * it.sessions }.toFloat() / total,
                avgSnoozes = group.sumOf { it.avgSnoozes.toDouble() * it.sessions }.toFloat() / total,
            )
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(name, style = MaterialTheme.typography.titleMedium, color = c.text)
                    Text("${r.sessions} wake-up${if (r.sessions == 1) "" else "s"} · ${"%.1f".format(r.avgSnoozes)} snoozes each", style = MaterialTheme.typography.bodyMedium, color = c.textMuted)
                }
                Text(r.wokeRate?.let { "${(it * 100).toInt()}%" } ?: "–", style = MaterialTheme.typography.titleMedium, color = c.text)
            }
        }
    }
}

@Composable
private fun SessionRow(s: WakeSession) {
    val c = Neu.colors
    val context = LocalContext.current
    val (icon, tint, status) = when {
        s.outcome == "missed" -> Triple(Icons.Rounded.Cancel, c.danger, "Missed")
        s.wokeUp && s.checksFailed > 0 -> Triple(Icons.Rounded.CheckCircle, c.textMuted, "Up after falling asleep")
        s.wokeUp -> Triple(Icons.Rounded.CheckCircle, c.success, "Up in ${formatSeconds(s.secondsToGetUp ?: 0)}")
        else -> Triple<ImageVector, androidx.compose.ui.graphics.Color, String>(Icons.Rounded.AlarmOn, c.primary, "Unfinished")
    }
    val start = Instant.ofEpochMilli(s.firstRingAt).atZone(ZoneId.systemDefault())
    val details = buildList {
        if (s.snoozes > 0) add("${s.snoozes} snooze${if (s.snoozes > 1) "s" else ""}")
        if (s.missionCount > 0) add("${s.missionCount} mission${if (s.missionCount > 1) "s" else ""} · ${formatSeconds(s.missionMs / 1000)}")
        if (s.escapes > 0) add("escape used")
        if (s.checksPassed + s.checksFailed > 0) add(if (s.checksFailed > 0) "check failed ${s.checksFailed}×" else "check passed")
    }
    Row(
        Modifier.fillMaxWidth().neu(20.dp, elevation = 0.dp, inset = 4.dp).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                listOf(formatTime(context, start.hour, start.minute), s.label).filter { it.isNotBlank() }.joinToString(" · "),
                style = MaterialTheme.typography.titleMedium, color = c.text,
            )
            Text(status + if (details.isEmpty()) "" else " · " + details.joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = c.textMuted)
        }
    }
}

private val dayFormat = DateTimeFormatter.ofPattern("EEEE, d MMMM")

private fun dayHeading(day: LocalDate, today: LocalDate) = when (day) {
    today -> "Today"
    today.minusDays(1) -> "Yesterday"
    else -> day.format(dayFormat)
}

@Composable
private fun ConfirmClearDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    val c = Neu.colors
    Dialog(onDismissRequest = onDismiss) {
        NeuCard(Modifier.fillMaxWidth(), cornerRadius = 30.dp, padding = PaddingValues(24.dp)) {
            Text("Delete all history?", style = MaterialTheme.typography.titleLarge, color = c.text)
            Spacer(Modifier.height(8.dp))
            Text("All history is removed for good. Alarms stay.", color = c.textMuted)
            Spacer(Modifier.height(22.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                NeuButton(onDismiss, Modifier.weight(1f)) { Text("Cancel") }
                NeuButton(onConfirm, Modifier.weight(1f)) { Text("Delete", color = c.danger) }
            }
        }
    }
}
