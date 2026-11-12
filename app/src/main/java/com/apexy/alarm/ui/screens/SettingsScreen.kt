package com.apexy.alarm.ui.screens

import com.apexy.alarm.ui.neu.CollapsedBar
import com.apexy.alarm.ui.neu.collapsingTitle
import com.apexy.alarm.ui.neu.rememberScrollSignal
import com.apexy.alarm.ui.neu.scrollReveal
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AlarmOn
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.SettingsBackupRestore
import androidx.compose.material.icons.rounded.Colorize
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FlipToFront
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.PriorityHigh
import androidx.compose.material.icons.rounded.VolumeOff
import android.widget.Toast
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.apexy.alarm.core.AlarmGuard
import com.apexy.alarm.core.AlarmService
import com.apexy.alarm.ui.neu.NeuToggle
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.PhonelinkLock
import androidx.compose.material.icons.rounded.RocketLaunch
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.window.Dialog
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.apexy.alarm.ApexyApp
import com.apexy.alarm.data.AppSettings
import com.apexy.alarm.data.ThemeMode
import com.apexy.alarm.ui.neu.Neu
import com.apexy.alarm.ui.neu.NeuButton
import com.apexy.alarm.ui.neu.NeuCard
import com.apexy.alarm.ui.neu.NeuSegmented
import com.apexy.alarm.ui.neu.NeuSlider
import com.apexy.alarm.ui.neu.PrimaryPresets
import com.apexy.alarm.ui.neu.SectionLabel
import com.apexy.alarm.ui.neu.SettingRow
import com.apexy.alarm.ui.neu.neu
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(settings: AppSettings, onOpenReliability: () -> Unit, onOpenAbout: () -> Unit) {
    val c = Neu.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = ApexyApp.instance.settings
    var showPicker by remember { mutableStateOf(false) }
    val alarms by ApexyApp.instance.db.alarms().observeAll().collectAsStateWithLifecycle(emptyList())
    // Loosening anti-cheat while an alarm is locked would defeat the point.
    fun canLoosen() = AlarmGuard.canLoosen(alarms, settings, AlarmService.state.value?.alarm?.id).also {
        if (!it) Toast.makeText(context, "An alarm is locked. Try after it rings.", Toast.LENGTH_LONG).show()
    }
    var checks by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) { checks++; onPauseOrDispose {} }

    val scroll = rememberScrollState()
    val scrollSignal = rememberScrollSignal(scroll)
    val reveal = Modifier.scrollReveal(scrollSignal)
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().verticalScroll(scroll).padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            Text("Settings", style = MaterialTheme.typography.headlineMedium, color = c.text, modifier = Modifier.collapsingTitle(scrollSignal).padding(start = 4.dp))

            NeuCard(reveal.fillMaxWidth()) {
                SectionLabel("Appearance")
                NeuSegmented(ThemeMode.entries, settings.themeMode, { scope.launch { repo.setThemeMode(it) } }, Modifier.fillMaxWidth()) {
                    when (it) { ThemeMode.SYSTEM -> "System"; ThemeMode.LIGHT -> "Light"; ThemeMode.DARK -> "Dark" }
                }
                Spacer(Modifier.height(22.dp))
                Text("Primary colour", style = MaterialTheme.typography.titleMedium, color = c.text)
                Spacer(Modifier.height(14.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    PrimaryPresets.forEach { (argb, name) ->
                        ColorSwatch(Color(argb), selected = settings.primaryColor == argb, name) { scope.launch { repo.setPrimaryColor(argb) } }
                    }
                    val custom = PrimaryPresets.none { it.first == settings.primaryColor }
                    NeuButton({ showPicker = true }, Modifier.size(48.dp), cornerRadius = 24.dp, contentPadding = PaddingValues(0.dp)) {
                        Icon(Icons.Rounded.Colorize, "Custom colour", tint = if (custom) c.accent else c.textMuted)
                    }
                }
                Spacer(Modifier.height(12.dp))
                SettingRow("Sounds", icon = Icons.Rounded.MusicNote) {
                    NeuToggle(settings.uiSounds, { on -> scope.launch { repo.setUiSounds(on) } })
                }
            }

            NeuCard(reveal.fillMaxWidth()) {
                SectionLabel("Anti-cheat")
                SettingRow("Prevent last-minute changes", icon = Icons.Rounded.Lock) {
                    NeuToggle(settings.preventChanges, { on ->
                        if (on || canLoosen()) scope.launch { repo.setPreventChanges(on) }
                    })
                }
                if (settings.preventChanges) {
                    Spacer(Modifier.height(6.dp))
                    Text("Lock window", style = MaterialTheme.typography.labelLarge, color = c.textMuted)
                    Spacer(Modifier.height(8.dp))
                    NeuSegmented(listOf(15, 30, 60, 120, 240), settings.lockWindowMinutes, { minutes ->
                        if (minutes >= settings.lockWindowMinutes || canLoosen()) scope.launch { repo.setLockWindow(minutes) }
                    }, Modifier.fillMaxWidth()) { if (it < 60) "${it}m" else "${it / 60}h" }
                    Spacer(Modifier.height(8.dp))
                }
                SettingRow("Lock alarm volume", icon = Icons.Rounded.VolumeOff) {
                    NeuToggle(settings.lockVolume, { on -> if (on || canLoosen()) scope.launch { repo.setLockVolume(on) } })
                }
                SettingRow("Bring the alarm back", icon = Icons.Rounded.FlipToFront) {
                    NeuToggle(settings.bringBack, { on -> if (on || canLoosen()) scope.launch { repo.setBringBack(on) } })
                }
            }

            key(checks) {
                val items = reliabilityItems(context)
                val ok = items.count { it.ok == true }
                val known = items.count { it.ok != null }
                NeuCard(reveal.fillMaxWidth(), onClick = onOpenReliability) {
                    SectionLabel("Reliability")
                    SettingRow(
                        if (ok == known) "All set" else "${known - ok} thing${if (known - ok > 1) "s" else ""} to fix",
                        subtitle = "Checklist and test alarm for ${PhoneGuides.forThisPhone()?.name ?: "your phone"}",
                        icon = Icons.Rounded.Shield,
                    ) {
                        Box(Modifier.size(36.dp).neu(18.dp, elevation = 0.dp, inset = 3.dp), contentAlignment = Alignment.Center) {
                            Icon(
                                if (ok == known) Icons.Rounded.Check else Icons.Rounded.PriorityHigh, null,
                                tint = if (ok == known) c.success else c.danger, modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            }

            BackupCard(reveal)

            NeuCard(reveal.fillMaxWidth(), onClick = onOpenAbout) {
                SectionLabel("About")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(48.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(14.dp))) {
                        androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(com.apexy.alarm.R.drawable.ic_launcher_bg), null, Modifier.fillMaxSize())
                        androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(com.apexy.alarm.R.drawable.ic_launcher_fg), "Apexy", Modifier.fillMaxSize().scale(1.25f))
                    }
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text("Apexy", style = MaterialTheme.typography.titleMedium, color = c.text)
                        Text("Features, privacy and safety", style = MaterialTheme.typography.bodyMedium, color = c.textMuted)
                    }
                    Spacer(Modifier.weight(1f))
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = c.textMuted)
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        CollapsedBar("Settings", scrollSignal)
    }

    if (showPicker) ColorPickerDialog(Color(settings.primaryColor), onDismiss = { showPicker = false }) { color ->
        showPicker = false
        scope.launch { repo.setPrimaryColor(color.toArgb().toLong() and 0xFFFFFFFFL) }
    }
}

@Composable
private fun key(value: Int, content: @Composable () -> Unit) = androidx.compose.runtime.key(value) { content() }

@Composable
internal fun ColorSwatch(color: Color, selected: Boolean, name: String, onClick: () -> Unit) {
    val label = if (selected) "$name, selected" else name
    NeuButton(
        onClick,
        Modifier.size(48.dp).semantics { contentDescription = label },
        cornerRadius = 24.dp, contentPadding = PaddingValues(0.dp),
    ) {
        Box(Modifier.size(if (selected) 30.dp else 24.dp).neu(15.dp, elevation = 0.dp, fill = color), contentAlignment = Alignment.Center) {
            if (selected) Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun ColorPickerDialog(initial: Color, onDismiss: () -> Unit, onPick: (Color) -> Unit) {
    val c = Neu.colors
    val hsv = remember {
        FloatArray(3).also { android.graphics.Color.colorToHSV(initial.toArgb(), it) }
    }
    var hue by remember { mutableFloatStateOf(hsv[0] / 360f) }
    var sat by remember { mutableFloatStateOf(hsv[1]) }
    var value by remember { mutableFloatStateOf(hsv[2]) }
    val color = Color.hsv(hue * 360f, sat, value)
    Dialog(onDismissRequest = onDismiss) {
        NeuCard(Modifier.fillMaxWidth(), cornerRadius = 30.dp, padding = PaddingValues(24.dp)) {
            Text("Custom colour", style = MaterialTheme.typography.titleLarge, color = c.text)
            Spacer(Modifier.height(18.dp))
            Box(Modifier.fillMaxWidth().height(72.dp).neu(22.dp, elevation = 0.dp, inset = 5.dp).padding(12.dp)) {
                Box(Modifier.fillMaxSize().neu(14.dp, elevation = 0.dp, fill = color))
            }
            Spacer(Modifier.height(18.dp))
            Text("Hue", color = c.textMuted, style = MaterialTheme.typography.labelLarge)
            NeuSlider(hue, { hue = it }, trackColor = Color.hsv(hue * 360f, 0.8f, 0.9f))
            Spacer(Modifier.height(8.dp))
            Text("Saturation", color = c.textMuted, style = MaterialTheme.typography.labelLarge)
            NeuSlider(sat, { sat = it.coerceAtLeast(0.15f) }, trackColor = color)
            Spacer(Modifier.height(8.dp))
            Text("Brightness", color = c.textMuted, style = MaterialTheme.typography.labelLarge)
            NeuSlider(value, { value = it.coerceAtLeast(0.3f) }, trackColor = color)
            Spacer(Modifier.height(22.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                NeuButton(onDismiss, Modifier.weight(1f)) { Text("Cancel") }
                com.apexy.alarm.ui.neu.NeuDoneButton(
                    "Apply", onConfirm = { true }, modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 22.dp, vertical = 15.dp),
                    onFinished = { onPick(color) },
                )
            }
        }
    }
}

@Composable
private fun BackupCard(modifier: Modifier = Modifier) {
    val c = Neu.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pendingRestore by remember { mutableStateOf<android.net.Uri?>(null) }
    var busy by remember { mutableStateOf(false) }
    fun toast(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_LONG).show()

    val exporter = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) scope.launch {
            busy = true
            runCatching { com.apexy.alarm.core.Backup.export(context, uri) }
                .onSuccess { toast("Backed up ${it.alarms} alarms, ${it.events} rings and ${it.nights} nights") }
                .onFailure { toast("Backup failed: ${it.message}") }
            busy = false
        }
    }
    val importer = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
    ) { uri -> pendingRestore = uri }

    NeuCard(modifier.fillMaxWidth()) {
        SectionLabel("Backup")
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            NeuButton({
                val day = java.time.LocalDate.now().toString()
                exporter.launch("apexy-backup-$day.json")
            }, Modifier.weight(1f), enabled = !busy) {
                Icon(Icons.Rounded.CloudUpload, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Back up")
            }
            NeuButton({ importer.launch(arrayOf("application/json", "application/octet-stream", "*/*")) }, Modifier.weight(1f), enabled = !busy) {
                Icon(Icons.Rounded.SettingsBackupRestore, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Restore")
            }
        }
    }

    pendingRestore?.let { uri ->
        androidx.compose.ui.window.Dialog(onDismissRequest = { pendingRestore = null }) {
            NeuCard(Modifier.fillMaxWidth(), cornerRadius = 30.dp, padding = PaddingValues(24.dp)) {
                Text("Restore this backup?", style = MaterialTheme.typography.titleLarge, color = c.text)
                Spacer(Modifier.height(8.dp))
                Text("Everything here is replaced by the backup.", color = c.textMuted)
                Spacer(Modifier.height(22.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    NeuButton({ pendingRestore = null }, Modifier.weight(1f)) { Text("Cancel") }
                    NeuButton({
                        pendingRestore = null
                        scope.launch {
                            busy = true
                            runCatching { com.apexy.alarm.core.Backup.restore(context, uri) }
                                .onSuccess { toast("Restored ${it.alarms} alarms, ${it.events} rings and ${it.nights} nights") }
                                .onFailure { toast("Restore failed: ${it.message}") }
                            busy = false
                        }
                    }, Modifier.weight(1f), primary = true) { Text("Restore") }
                }
            }
        }
    }
}
