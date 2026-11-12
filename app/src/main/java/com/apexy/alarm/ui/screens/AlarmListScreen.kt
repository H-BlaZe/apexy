package com.apexy.alarm.ui.screens

import com.apexy.alarm.ui.neu.Reveal
import com.apexy.alarm.ui.neu.CollapsedBar
import com.apexy.alarm.ui.neu.collapsingTitle
import com.apexy.alarm.ui.neu.rememberScrollSignal
import com.apexy.alarm.ui.neu.scrollReveal
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.rounded.AlarmAdd
import androidx.compose.material.icons.rounded.AlarmOn
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Snooze
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.apexy.alarm.ApexyApp
import com.apexy.alarm.core.AlarmActions
import com.apexy.alarm.core.AlarmScheduler
import com.apexy.alarm.data.Alarm
import com.apexy.alarm.ui.DAY_LETTERS
import com.apexy.alarm.ui.daysSummary
import com.apexy.alarm.ui.durationUntil
import com.apexy.alarm.ui.formatTime
import com.apexy.alarm.ui.neu.Neu
import com.apexy.alarm.ui.neu.NeuButton
import com.apexy.alarm.ui.neu.NeuCard
import com.apexy.alarm.ui.neu.NeuChip
import com.apexy.alarm.ui.neu.NeuIconButton
import com.apexy.alarm.ui.neu.NeuToggle
import com.apexy.alarm.ui.neu.neu
import com.apexy.alarm.ui.timeParts
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun AlarmListScreen(onEdit: (Long) -> Unit, onOpenReliability: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val alarms by ApexyApp.instance.db.alarms().observeAll().collectAsStateWithLifecycle(emptyList())
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) { delay(15_000); now = System.currentTimeMillis() }
    }
    val gate = com.apexy.alarm.ui.rememberLockGate()
    var showQuick by remember { mutableStateOf(false) }
    var menuFor by remember { mutableStateOf<Alarm?>(null) }

    val next = alarms.mapNotNull { a -> AlarmScheduler.nextTrigger(a, now)?.let { a to it } }.minByOrNull { it.second }

    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val scrollSignal = rememberScrollSignal(listState)
    // Re-checked each time the screen comes back, e.g. after fixing a permission.
    var checks by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    androidx.lifecycle.compose.LifecycleResumeEffect(Unit) { checks++; onPauseOrDispose {} }
    val broken = remember(checks) { reliabilityItems(context).filter { it.ok == false } }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 110.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            item {
                Text("Alarms", style = MaterialTheme.typography.headlineMedium, color = Neu.colors.text, modifier = Modifier.collapsingTitle(scrollSignal).padding(start = 4.dp, bottom = 4.dp))
            }
            if (broken.isNotEmpty() && alarms.any { it.enabled }) item { Reveal(scrollSignal) { ReliabilityBanner(broken.size, onOpenReliability) } }
            item { Reveal(scrollSignal) { NextAlarmCard(next, now) } }
            items(alarms, key = { it.id }) { alarm ->
                com.apexy.alarm.ui.neu.WithAccent(alarm.accentColor) { AlarmCard(
                    modifier = Modifier.scrollReveal(scrollSignal),
                    alarm = alarm,
                    now = now,
                    onClick = { if (gate.allows(alarm)) onEdit(alarm.id) },
                    onLongClick = { menuFor = alarm },
                    onToggle = { on ->
                        if (!on && !gate.allows(alarm)) return@AlarmCard
                        scope.launch {
                            AlarmActions.setEnabled(alarm, on)
                            if (on) AlarmScheduler.nextTrigger(alarm.copy(enabled = true, snoozedUntil = 0, skipUntil = 0))?.let {
                                Toast.makeText(context, "Alarm set for ${durationUntil(it)} from now", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                ) }
            }
            if (alarms.isEmpty()) item { com.apexy.alarm.ui.neu.EmptyHint(Icons.Rounded.AlarmAdd, "No alarms") }
        }
        CollapsedBar("Alarms", scrollSignal)
        Row(
            Modifier.align(Alignment.BottomEnd).padding(20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NeuIconButton(Icons.Rounded.Bolt, "Quick alarm", { showQuick = true }, size = 54.dp, tint = Neu.colors.primary)
            NeuIconButton(Icons.Rounded.Add, "New alarm", { onEdit(0) }, size = 64.dp, primary = true)
        }
    }

    if (showQuick) QuickAlarmDialog(onDismiss = { showQuick = false }) { minutes ->
        showQuick = false
        scope.launch {
            AlarmActions.quick(minutes)
            Toast.makeText(context, "Quick alarm in ${minutes}m", Toast.LENGTH_SHORT).show()
        }
    }
    menuFor?.let { alarm ->
        AlarmMenuDialog(
            alarm = alarm,
            skipped = alarm.skipUntil > now,
            onDismiss = { menuFor = null },
            onAction = { action ->
                menuFor = null
                val changesIt = action == MenuAction.SKIP || action == MenuAction.DELETE
                if (changesIt && !gate.allows(alarm)) return@AlarmMenuDialog
                scope.launch {
                    when (action) {
                        MenuAction.SKIP -> AlarmActions.skipNext(alarm)
                        MenuAction.UNSKIP -> AlarmActions.unskip(alarm)
                        MenuAction.DUPLICATE -> AlarmActions.duplicate(alarm)
                        MenuAction.DELETE -> AlarmActions.delete(alarm)
                    }
                }
            },
        )
    }
}

@Composable
private fun NextAlarmCard(next: Pair<Alarm, Long>?, now: Long) {
    val c = Neu.colors
    val context = LocalContext.current
    NeuCard(Modifier.fillMaxWidth(), cornerRadius = 30.dp, padding = PaddingValues(22.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(74.dp).neu(37.dp, elevation = 0.dp, inset = 6.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.size(50.dp).neu(25.dp, elevation = 5.dp, fill = c.primary), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.AlarmOn, null, tint = c.onPrimary, modifier = Modifier.size(26.dp))
                }
            }
            Spacer(Modifier.width(18.dp))
            Column {
                Text("NEXT ALARM", style = MaterialTheme.typography.labelMedium, color = c.textMuted)
                if (next == null) {
                    Text("Nothing scheduled", style = MaterialTheme.typography.titleLarge, color = c.text)
                } else {
                    val (alarm, at) = next
                    val day = Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()).dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())
                    Text("in ${durationUntil(at, now)}", fontSize = 30.sp, fontWeight = FontWeight.Light, color = c.text)
                    Text(
                        listOf(day, formatInstant(context, at), alarm.label).filter { it.isNotBlank() }.joinToString("  ·  "),
                        style = MaterialTheme.typography.bodyMedium, color = c.textMuted,
                    )
                }
            }
        }
    }
}

private fun formatInstant(context: android.content.Context, ms: Long): String {
    val t = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault())
    return formatTime(context, t.hour, t.minute)
}

@Composable
private fun AlarmCard(alarm: Alarm, now: Long, onClick: () -> Unit, onLongClick: () -> Unit, onToggle: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val c = Neu.colors
    val context = LocalContext.current
    val (clock, suffix) = timeParts(context, alarm.hour, alarm.minute)
    val tone = if (alarm.enabled) c.text else c.textMuted.copy(alpha = 0.6f)
    NeuCard(modifier.fillMaxWidth(), onClick = onClick, onLongClick = onLongClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(clock, fontSize = 42.sp, fontWeight = FontWeight.Light, color = tone)
                    if (suffix != null) Text(" $suffix", style = MaterialTheme.typography.titleMedium, color = tone, modifier = Modifier.padding(bottom = 8.dp))
                }
                Text(
                    alarm.label.ifBlank { daysSummary(alarm) },
                    style = MaterialTheme.typography.bodyMedium, color = c.textMuted,
                )
            }
            NeuToggle(alarm.enabled, onToggle)
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            DAY_LETTERS.forEachIndexed { i, letter ->
                val on = alarm.hasDay(i)
                Text(
                    letter,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (on && alarm.enabled) c.accent else c.textMuted.copy(alpha = if (on) 0.85f else 0.45f),
                    modifier = Modifier.width(20.dp),
                )
            }
            Spacer(Modifier.weight(1f))
            val status = when {
                !alarm.enabled -> null
                alarm.snoozeCount > 0 && alarm.snoozedUntil > now -> "Snoozed until ${formatInstant(context, alarm.snoozedUntil)}"
                alarm.skipUntil > now -> "Next skipped"
                else -> null
            }
            if (status != null) Text(status, style = MaterialTheme.typography.labelMedium, color = c.accent, modifier = Modifier.padding(end = 10.dp))
            if (alarm.snoozeEnabled) Icon(Icons.Rounded.Snooze, "Snooze on", Modifier.size(16.dp), tint = c.textMuted)
            if (alarm.extraLoud) Icon(Icons.Rounded.VolumeUp, "Extra loud", Modifier.padding(start = 6.dp).size(16.dp), tint = c.textMuted)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuickAlarmDialog(onDismiss: () -> Unit, onStart: (Int) -> Unit) {
    val c = Neu.colors
    var minutes by remember { mutableIntStateOf(10) }
    Dialog(onDismissRequest = onDismiss) {
        NeuCard(Modifier.fillMaxWidth(), cornerRadius = 30.dp, padding = PaddingValues(24.dp)) {
            Text("Quick alarm", style = MaterialTheme.typography.titleLarge, color = c.text)
            Spacer(Modifier.height(20.dp))
            Box(Modifier.fillMaxWidth().height(96.dp).neu(24.dp, elevation = 0.dp, inset = 6.dp), contentAlignment = Alignment.Center) {
                Text(
                    if (minutes >= 60) "%dh %02dm".format(minutes / 60, minutes % 60) else "${minutes}m",
                    fontSize = 40.sp, fontWeight = FontWeight.Light, color = c.text,
                )
            }
            Spacer(Modifier.height(20.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf(1, 5, 10, 15, 30, 60).forEach { add ->
                    NeuChip("+$add", selected = false, onClick = { minutes = (minutes + add).coerceAtMost(24 * 60) })
                }
                NeuChip("Reset", selected = true, onClick = { minutes = 0 })
            }
            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                NeuButton(onDismiss, Modifier.weight(1f)) { Text("Cancel") }
                com.apexy.alarm.ui.neu.NeuDoneButton(
                    "Start", onConfirm = { minutes > 0 }, modifier = Modifier.weight(1f), enabled = minutes > 0,
                    contentPadding = PaddingValues(horizontal = 22.dp, vertical = 15.dp),
                    onFinished = { onStart(minutes) },
                )
            }
        }
    }
}

private enum class MenuAction { SKIP, UNSKIP, DUPLICATE, DELETE }

@Composable
private fun AlarmMenuDialog(alarm: Alarm, skipped: Boolean, onDismiss: () -> Unit, onAction: (MenuAction) -> Unit) {
    val c = Neu.colors
    val context = LocalContext.current
    Dialog(onDismissRequest = onDismiss) {
        NeuCard(Modifier.fillMaxWidth(), cornerRadius = 30.dp, padding = PaddingValues(24.dp)) {
            Text(formatTime(context, alarm.hour, alarm.minute), style = MaterialTheme.typography.titleLarge, color = c.text)
            Text(alarm.label.ifBlank { daysSummary(alarm) }, color = c.textMuted)
            Spacer(Modifier.height(20.dp))
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                if (alarm.isRepeating && alarm.enabled) {
                    NeuButton({ onAction(if (skipped) MenuAction.UNSKIP else MenuAction.SKIP) }, Modifier.fillMaxWidth()) {
                        Text(if (skipped) "Un-skip next ring" else "Skip next ring")
                    }
                }
                NeuButton({ onAction(MenuAction.DUPLICATE) }, Modifier.fillMaxWidth()) { Text("Duplicate") }
                NeuButton({ onAction(MenuAction.DELETE) }, Modifier.fillMaxWidth()) { Text("Delete", color = c.danger) }
            }
        }
    }
}

/** Shown on the alarm list when something would stop an alarm from ringing. */
@Composable
private fun ReliabilityBanner(problems: Int, onOpen: () -> Unit) {
    val c = Neu.colors
    com.apexy.alarm.ui.neu.NeuCard(Modifier.fillMaxWidth(), onClick = onOpen, padding = PaddingValues(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).neu(14.dp, elevation = 0.dp, inset = 3.dp), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Warning, null, tint = c.danger, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("Alarms may not ring", style = MaterialTheme.typography.titleMedium, color = c.text)
                Text("$problems setting${if (problems > 1) "s" else ""} to fix", style = MaterialTheme.typography.bodyMedium, color = c.textMuted)
            }
            Text("Fix", style = MaterialTheme.typography.labelLarge, color = c.accent)
        }
    }
}
