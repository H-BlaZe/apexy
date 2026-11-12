package com.apexy.alarm.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.apexy.alarm.ApexyApp
import com.apexy.alarm.core.AlarmScheduler
import com.apexy.alarm.stats.WakeStats
import com.apexy.alarm.sleep.formatMinutes
import com.apexy.alarm.ui.neu.Neu
import com.apexy.alarm.ui.neu.NeuButton
import com.apexy.alarm.ui.neu.NeuCard
import com.apexy.alarm.ui.neu.neu
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private data class DayEvent(val title: String, val begin: Long, val allDay: Boolean)

/** Today's calendar events, read straight from the phone's own calendar database. */
private fun todaysEvents(context: Context): List<DayEvent>? {
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) return null
    val zone = ZoneId.systemDefault()
    val start = LocalDate.now().atStartOfDay(zone).toInstant().toEpochMilli()
    val end = start + 24 * 3_600_000L
    val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
        android.content.ContentUris.appendId(it, start)
        android.content.ContentUris.appendId(it, end)
    }.build()
    return runCatching {
        context.contentResolver.query(
            uri, arrayOf(CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN, CalendarContract.Instances.ALL_DAY),
            null, null, "${CalendarContract.Instances.BEGIN} ASC",
        )?.use { cur ->
            buildList {
                while (cur.moveToNext() && size < 6) add(DayEvent(cur.getString(0) ?: "Event", cur.getLong(1), cur.getInt(2) == 1))
            }
        } ?: emptyList()
    }.getOrNull()
}

/** The day ahead, shown once the alarm is dismissed. Everything comes from the phone; nothing goes online. */
@Composable
fun MorningCardScreen(onClose: () -> Unit) {
    val c = Neu.colors
    val context = LocalContext.current
    val app = ApexyApp.instance
    val now = remember { LocalDateTime.now() }
    val events by produceState<List<DayEvent>?>(null) { value = withContext(Dispatchers.IO) { todaysEvents(context) } }
    val nights by app.db.sleep().observeNights().collectAsStateWithLifecycle(emptyList())
    val alarms by app.db.alarms().observeAll().collectAsStateWithLifecycle(emptyList())
    val history by app.db.events().observeAll().collectAsStateWithLifecycle(emptyList())
    val lastNight = nights.firstOrNull { System.currentTimeMillis() - it.endedAt < 12 * 3_600_000L }
    val nextAlarm = alarms.mapNotNull { a -> AlarmScheduler.nextTrigger(a)?.let { a to it } }.minByOrNull { it.second }
    val streak = remember(history) { WakeStats.streaks(WakeStats.sessions(history), LocalDate.now()).first }

    // Cards rise in one after another.
    val appear = remember { List(5) { Animatable(0f) } }
    LaunchedEffect(Unit) {
        appear.forEachIndexed { i, a -> launch { kotlinx.coroutines.delay(i * 70L); a.animateTo(1f, tween(380)) } }
    }
    fun Modifier.rise(i: Int) = graphicsLayer {
        val v = appear[i].value
        alpha = v
        translationY = (1f - v) * 60f
    }

    Column(
        Modifier.fillMaxSize().background(c.surface).systemBarsPadding().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Column(Modifier.rise(0)) {
            Text(greeting(now.hour), style = MaterialTheme.typography.titleMedium, color = c.textMuted)
            Text(now.format(DateTimeFormatter.ofPattern("EEEE")), fontSize = 44.sp, fontWeight = FontWeight.Light, color = c.text)
            Text(now.format(DateTimeFormatter.ofPattern("d MMMM yyyy")), style = MaterialTheme.typography.titleMedium, color = c.accent)
        }

        CardRow(Modifier.rise(1), Icons.Rounded.Event, "Today") {
            when {
                events == null -> Text("Allow calendar access to see your day", style = MaterialTheme.typography.bodyMedium, color = c.textMuted)
                events!!.isEmpty() -> Text("Nothing on the calendar", style = MaterialTheme.typography.bodyLarge, color = c.text)
                else -> events!!.forEach { e ->
                    Row(Modifier.padding(vertical = 3.dp)) {
                        Text(
                            if (e.allDay) "All day" else Instant.ofEpochMilli(e.begin).atZone(ZoneId.systemDefault()).let { formatTime(context, it.hour, it.minute) },
                            style = MaterialTheme.typography.labelLarge, color = c.accent, modifier = Modifier.width(78.dp),
                        )
                        Text(e.title, style = MaterialTheme.typography.bodyLarge, color = c.text, maxLines = 1)
                    }
                }
            }
        }

        Row(Modifier.rise(2), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            StatTile(Modifier.weight(1f), Icons.Rounded.Bedtime, "Slept",
                lastNight?.let { formatMinutes(((it.endedAt - it.startedAt) / 60_000).toInt()) } ?: "—")
            StatTile(Modifier.weight(1f), Icons.Rounded.LocalFireDepartment, "Streak", if (streak > 0) "${streak}d" else "—")
        }

        CardRow(Modifier.rise(3), Icons.Rounded.Alarm, "Next alarm") {
            Text(
                nextAlarm?.let { (a, at) ->
                    val t = Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault())
                    formatTime(context, t.hour, t.minute) + " · " + t.format(DateTimeFormatter.ofPattern("EEE")) + (a.label.takeIf { it.isNotBlank() }?.let { " · $it" } ?: "")
                } ?: "None set",
                style = MaterialTheme.typography.bodyLarge, color = c.text,
            )
        }

        NeuButton(onClose, Modifier.fillMaxWidth().height(64.dp).rise(4), primary = true, cornerRadius = 22.dp) {
            Text("Start the day", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun CardRow(modifier: Modifier, icon: ImageVector, title: String, content: @Composable () -> Unit) {
    val c = Neu.colors
    NeuCard(modifier.fillMaxWidth(), padding = PaddingValues(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(36.dp).neu(12.dp, elevation = 0.dp, inset = 3.dp), contentAlignment = Alignment.Center) {
                Icon(icon, null, Modifier.size(18.dp), tint = c.accent)
            }
            Spacer(Modifier.width(12.dp))
            Text(title.uppercase(), style = MaterialTheme.typography.labelMedium, color = c.textMuted)
        }
        Spacer(Modifier.height(10.dp))
        content()
    }
}

@Composable
private fun StatTile(modifier: Modifier, icon: ImageVector, label: String, value: String) {
    val c = Neu.colors
    NeuCard(modifier, padding = PaddingValues(18.dp)) {
        Icon(icon, null, tint = c.accent)
        Spacer(Modifier.height(8.dp))
        Text(value, fontSize = 24.sp, fontWeight = FontWeight.Medium, color = c.text)
        Text(label, style = MaterialTheme.typography.labelLarge, color = c.textMuted)
    }
}

private fun greeting(hour: Int) = when (hour) { in 4..11 -> "Good morning"; in 12..16 -> "Good afternoon"; else -> "Good evening" }
