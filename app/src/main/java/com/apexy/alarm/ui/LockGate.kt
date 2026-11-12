package com.apexy.alarm.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.apexy.alarm.ApexyApp
import com.apexy.alarm.core.AlarmGuard
import com.apexy.alarm.core.AlarmService
import com.apexy.alarm.data.Alarm
import com.apexy.alarm.data.AppSettings
import com.apexy.alarm.ui.neu.Neu
import com.apexy.alarm.ui.neu.NeuButton
import com.apexy.alarm.ui.neu.NeuCard
import com.apexy.alarm.ui.neu.neu

/** Checks an alarm against the anti-cheat rules and explains in a dialog when a change is blocked. */
class LockGate(private val settings: AppSettings, private val onLocked: (AlarmGuard.Lock) -> Unit) {
    /** True when [alarm] may be changed; otherwise shows why not. */
    fun allows(alarm: Alarm): Boolean {
        val lock = AlarmGuard.lock(alarm, settings, AlarmService.state.value?.alarm?.id) ?: return true
        onLocked(lock)
        return false
    }
}

@Composable
fun rememberLockGate(): LockGate {
    val settings by ApexyApp.instance.settings.settings.collectAsStateWithLifecycle(AppSettings())
    var shown by remember { mutableStateOf<AlarmGuard.Lock?>(null) }
    shown?.let { LockedDialog(it) { shown = null } }
    return remember(settings) { LockGate(settings) { shown = it } }
}

fun lockMessage(lock: AlarmGuard.Lock): String = when (lock) {
    AlarmGuard.Lock.Ringing -> "This alarm is ringing right now."
    AlarmGuard.Lock.Snoozed -> "Snoozed. Editable once dismissed."
    is AlarmGuard.Lock.Soon ->
        "Rings in ${durationUntil(lock.ringsAt)}. Locked ${windowLabel(lock.windowMinutes)} before."
}

fun windowLabel(minutes: Int) = if (minutes < 60) "${minutes}m" else "${minutes / 60}h"

@Composable
private fun LockedDialog(lock: AlarmGuard.Lock, onDismiss: () -> Unit) {
    val c = Neu.colors
    Dialog(onDismissRequest = onDismiss) {
        NeuCard(Modifier.fillMaxWidth(), cornerRadius = 30.dp, padding = PaddingValues(26.dp)) {
            Box(Modifier.size(64.dp).neu(32.dp, elevation = 0.dp, inset = 5.dp).align(Alignment.CenterHorizontally), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Lock, null, tint = c.accent)
            }
            Spacer(Modifier.height(16.dp))
            Text("Alarm locked", style = MaterialTheme.typography.titleLarge, color = c.text, modifier = Modifier.align(Alignment.CenterHorizontally))
            Spacer(Modifier.height(8.dp))
            Text(lockMessage(lock), style = MaterialTheme.typography.bodyLarge, color = c.textMuted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(22.dp))
            NeuButton(onDismiss, Modifier.fillMaxWidth(), primary = true) { Text("OK") }
        }
    }
}
