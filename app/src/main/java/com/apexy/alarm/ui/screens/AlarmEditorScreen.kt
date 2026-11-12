package com.apexy.alarm.ui.screens

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.text.format.DateFormat
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Snooze
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material.icons.rounded.Speaker
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material.icons.rounded.WbTwilight
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.apexy.alarm.ApexyApp
import com.apexy.alarm.core.AlarmActions
import com.apexy.alarm.core.AlarmScheduler
import com.apexy.alarm.core.AlarmService
import com.apexy.alarm.core.ToneLibrary
import com.apexy.alarm.data.Alarm
import com.apexy.alarm.missions.Mission
import com.apexy.alarm.missions.MissionCodec
import com.apexy.alarm.missions.MissionConfigScreen
import com.apexy.alarm.missions.MissionPicker
import com.apexy.alarm.missions.icon
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import com.apexy.alarm.ui.DAY_LETTERS
import com.apexy.alarm.ui.EVERY_DAY
import com.apexy.alarm.ui.WEEKDAYS
import com.apexy.alarm.ui.WEEKENDS
import com.apexy.alarm.ui.durationUntil
import com.apexy.alarm.ui.neu.Neu
import com.apexy.alarm.ui.neu.NeuButton
import com.apexy.alarm.ui.neu.NeuCard
import com.apexy.alarm.ui.neu.NeuDoneButton
import com.apexy.alarm.ui.neu.NeuChip
import com.apexy.alarm.ui.neu.NeuIconButton
import com.apexy.alarm.ui.neu.NeuSegmented
import com.apexy.alarm.ui.neu.NeuSlider
import com.apexy.alarm.ui.neu.NeuTextField
import com.apexy.alarm.ui.neu.NeuToggle
import com.apexy.alarm.ui.neu.SectionLabel
import com.apexy.alarm.ui.neu.SettingRow
import com.apexy.alarm.ui.neu.WheelPicker
import com.apexy.alarm.ui.neu.neu
import kotlinx.coroutines.launch

private val RAMP_OPTIONS = listOf(0, 15, 30, 60, 120, 300)
private val SNOOZE_LENGTHS = listOf(1, 3, 5, 10, 15, 20)
private val SNOOZE_LIMITS = listOf(1, 2, 3, 5, -1)
private val RAMP_STARTS = listOf(0, 5, 20, 40)
private val ESCALATE_AFTER = listOf(1, 2, 3, 5, 10)
private val SPEAK_EVERY = listOf(30, 45, 60, 120)
private val IDLE_OPTIONS = listOf(15, 30, 45, 60)

@Composable
fun AlarmEditorScreen(alarmId: Long, onClose: () -> Unit) {
    var draft by remember { mutableStateOf<Alarm?>(null) }
    LaunchedEffect(alarmId) {
        draft = if (alarmId == 0L) Alarm() else ApexyApp.instance.db.alarms().get(alarmId) ?: Alarm()
    }
    // Sub-screens: picking a mission type, then configuring one (index -1 = new).
    var picking by remember { mutableStateOf(false) }
    val scroll = rememberScrollState()
    var editing by remember { mutableStateOf<Pair<Int, Mission>?>(null) }
    val loaded = draft ?: return
    val missions = MissionCodec.decode(loaded.missions)
    fun setMissions(list: List<Mission>) { draft = loaded.copy(missions = MissionCodec.encode(list)) }

    val edit = editing
    when {
        edit != null -> MissionConfigScreen(
            initial = edit.second,
            isNew = edit.first < 0,
            onSave = { m ->
                setMissions(if (edit.first < 0) missions + m else missions.toMutableList().also { it[edit.first] = m })
                editing = null
            },
            onRemove = { setMissions(missions.filterIndexed { i, _ -> i != edit.first }); editing = null },
            onBack = { editing = null },
        )
        picking -> MissionPicker(onPick = { picking = false; editing = -1 to Mission(it) }, onBack = { picking = false })
        // The editor previews the alarm's own accent colour while you pick it.
        else -> com.apexy.alarm.ui.neu.WithAccent(loaded.accentColor) {
            EditorContent(
                loaded, isNew = alarmId == 0L, scroll = scroll, onChange = { draft = it }, onClose = onClose,
                onAddMission = { picking = true },
                onEditMission = { i -> editing = i to missions[i] },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EditorContent(
    draft: Alarm,
    isNew: Boolean,
    scroll: androidx.compose.foundation.ScrollState,
    onChange: (Alarm) -> Unit,
    onClose: () -> Unit,
    onAddMission: () -> Unit,
    onEditMission: (Int) -> Unit,
) {
    val c = Neu.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val preview = remember { TonePreview(context) }
    val gate = com.apexy.alarm.ui.rememberLockGate()
    /** The saved version decides the lock: the draft may already have a different time. */
    suspend fun stored() = if (isNew) null else ApexyApp.instance.db.alarms().get(draft.id)
    DisposableEffect(Unit) { onDispose { preview.stop() } }

    val ringtonePicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            @Suppress("DEPRECATION")
            val uri = result.data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            if (uri != null) onChange(draft.copy(sound = uri.toString()))
        }
    }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            onChange(draft.copy(sound = uri.toString()))
        }
    }

    /** Saves unless the alarm is locked; returns the saved alarm, or null when blocked. */
    suspend fun persist(): Alarm? {
        stored()?.let { if (!gate.allows(it)) return null }
        preview.stop()
        return AlarmActions.save(draft.copy(skipUntil = 0))
    }
    var savedAt by remember { mutableStateOf<Long?>(null) }
    var recording by remember { mutableStateOf(false) }
    val calendarPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}

    fun save(thenPreview: Boolean = false) = scope.launch {
        val saved = persist() ?: return@launch
        if (thenPreview) {
            ContextCompat.startForegroundService(context, AlarmService.ringIntent(context, saved.id, preview = true))
            onChange(saved)
        }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            NeuIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Back", onClose, size = 46.dp)
            Text(
                if (isNew) "New alarm" else "Edit alarm",
                style = MaterialTheme.typography.titleLarge, color = c.text,
                modifier = Modifier.padding(start = 16.dp).weight(1f),
            )
            NeuDoneButton(
                "Save",
                onConfirm = { persist()?.let { savedAt = AlarmScheduler.nextTrigger(it); true } ?: false },
                onFinished = {
                    savedAt?.let { Toast.makeText(context, "Rings in ${durationUntil(it)}", Toast.LENGTH_SHORT).show() }
                    onClose()
                },
            )
        }

        Column(
            Modifier.weight(1f).verticalScroll(scroll).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            Spacer(Modifier.height(4.dp))
            TimeCard(draft, onChange)

            NeuCard(Modifier.fillMaxWidth()) {
                SectionLabel("Repeat")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    DAY_LETTERS.forEachIndexed { i, letter ->
                        NeuChip(letter, selected = draft.hasDay(i), round = true, onClick = {
                            onChange(draft.copy(days = draft.days xor (1 shl i), isQuick = false))
                        })
                    }
                }
                Spacer(Modifier.height(14.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf("Once" to 0, "Weekdays" to WEEKDAYS, "Weekends" to WEEKENDS, "Every day" to EVERY_DAY).forEach { (name, mask) ->
                        NeuChip(name, selected = draft.days == mask, onClick = { onChange(draft.copy(days = mask)) })
                    }
                }
            }

            NeuCard(Modifier.fillMaxWidth()) {
                SectionLabel("Label")
                NeuTextField(draft.label, { onChange(draft.copy(label = it.take(60))) }, "e.g. Gym, Morning meds")
                Spacer(Modifier.height(18.dp))
                Text("Accent colour", style = MaterialTheme.typography.labelLarge, color = c.textMuted)
                Spacer(Modifier.height(10.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    NeuChip("App colour", draft.accentColor == 0L, { onChange(draft.copy(accentColor = 0)) })
                    com.apexy.alarm.ui.neu.PrimaryPresets.forEach { (argb, name) ->
                        ColorSwatch(androidx.compose.ui.graphics.Color(argb), draft.accentColor == argb, name) { onChange(draft.copy(accentColor = argb)) }
                    }
                }
            }

            MissionsCard(
                draft = draft,
                missions = MissionCodec.decode(draft.missions),
                onChange = onChange,
                onAdd = onAddMission,
                onEdit = onEditMission,
                onRemove = { i -> onChange(draft.copy(missions = MissionCodec.encode(MissionCodec.decode(draft.missions).filterIndexed { j, _ -> j != i }))) },
            )

            NeuCard(Modifier.fillMaxWidth()) {
                SectionLabel("Sound")
                ToneLibrary.tones.forEach { tone ->
                    val id = "builtin:${tone.id}"
                    SoundRow(tone.name, selected = draft.sound == id, playing = preview.playing == id,
                        onSelect = { onChange(draft.copy(sound = id)) },
                        onPreview = { preview.toggle(id, Uri.fromFile(ToneLibrary.file(context, tone.id))) })
                }
                val custom = !draft.sound.startsWith("builtin:")
                if (custom) {
                    SoundRow(soundName(context, draft.sound), selected = true, playing = preview.playing == draft.sound,
                        onSelect = {}, onPreview = { preview.toggle(draft.sound, resolveUri(draft.sound)) })
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    NeuButton({
                        ringtonePicker.launch(
                            Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
                                .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM or RingtoneManager.TYPE_RINGTONE)
                                .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false),
                        )
                    }, Modifier.weight(1f), contentPadding = PaddingValues(12.dp)) {
                        Icon(Icons.Rounded.NotificationsActive, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("System")
                    }
                    NeuButton({ filePicker.launch(arrayOf("audio/*")) }, Modifier.weight(1f), contentPadding = PaddingValues(12.dp)) {
                        Icon(Icons.Rounded.LibraryMusic, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("My music")
                    }
                }
            }

            NeuCard(Modifier.fillMaxWidth()) {
                SectionLabel("Volume")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.VolumeUp, null, tint = c.accent)
                    Spacer(Modifier.width(14.dp))
                    NeuSlider(draft.volume / 100f, { onChange(draft.copy(volume = (it * 100).toInt().coerceAtLeast(5))) }, Modifier.weight(1f))
                    Text("${draft.volume}%", Modifier.width(52.dp).padding(start = 10.dp), color = c.textMuted)
                }
                Spacer(Modifier.height(16.dp))
                Text("Gradually increase", style = MaterialTheme.typography.titleMedium, color = c.text)
                Spacer(Modifier.height(10.dp))
                NeuSegmented(RAMP_OPTIONS, draft.rampSeconds, { onChange(draft.copy(rampSeconds = it)) }, Modifier.fillMaxWidth()) {
                    when { it == 0 -> "Off"; it < 60 -> "${it}s"; else -> "${it / 60}m" }
                }
                if (draft.rampSeconds > 0 && !draft.extraLoud) {
                    Spacer(Modifier.height(14.dp))
                    Text("Start at", style = MaterialTheme.typography.labelLarge, color = c.textMuted)
                    Spacer(Modifier.height(8.dp))
                    NeuSegmented(RAMP_STARTS, draft.rampStart, { onChange(draft.copy(rampStart = it)) }, Modifier.fillMaxWidth()) { if (it == 0) "Silent" else "$it%" }
                    Spacer(Modifier.height(14.dp))
                    Text("Curve", style = MaterialTheme.typography.labelLarge, color = c.textMuted)
                    Spacer(Modifier.height(8.dp))
                    NeuSegmented(listOf(Alarm.RAMP_GENTLE, Alarm.RAMP_LINEAR, Alarm.RAMP_QUICK), draft.rampCurve, { onChange(draft.copy(rampCurve = it)) }, Modifier.fillMaxWidth()) {
                        when (it) { Alarm.RAMP_GENTLE -> "Gentle"; Alarm.RAMP_QUICK -> "Quick"; else -> "Even" }
                    }
                    Spacer(Modifier.height(8.dp))
                    RampPreview(draft)
                }
                Spacer(Modifier.height(8.dp))
                SettingRow("Extra loud", icon = Icons.Rounded.GraphicEq) {
                    NeuToggle(draft.extraLoud, { onChange(draft.copy(extraLoud = it)) })
                }
                SettingRow("Get louder if ignored", icon = Icons.AutoMirrored.Rounded.TrendingUp) {
                    NeuToggle(draft.escalate, { onChange(draft.copy(escalate = it)) })
                }
                if (draft.escalate) {
                    Text("After", style = MaterialTheme.typography.labelLarge, color = c.textMuted)
                    Spacer(Modifier.height(8.dp))
                    NeuSegmented(ESCALATE_AFTER, draft.escalateAfter, { onChange(draft.copy(escalateAfter = it)) }, Modifier.fillMaxWidth()) { "${it}m" }
                    Spacer(Modifier.height(6.dp))
                }
                // Escalation and the mission idle boost both climb, so they share one ceiling.
                if (draft.escalate || draft.missions.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Speaker, null, tint = c.accent)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Speaker limit", style = MaterialTheme.typography.titleMedium, color = c.text)
                            NeuSlider((draft.volumeCap - 40) / 60f, { onChange(draft.copy(volumeCap = (40 + it * 60).toInt())) })
                        }
                        Text("${draft.volumeCap}%", Modifier.width(52.dp).padding(start = 10.dp), color = c.textMuted)
                    }
                    Text("Boosts never go above this, to protect your speaker.", style = MaterialTheme.typography.bodySmall, color = c.textMuted)
                }
            }

            NeuCard(Modifier.fillMaxWidth()) {
                SettingRow("Speak aloud", icon = Icons.Rounded.RecordVoiceOver) {
                    NeuToggle(draft.speakLabel, { onChange(draft.copy(speakLabel = it)) })
                }
                if (draft.speakLabel) {
                    Text("Time, date and label, read aloud", style = MaterialTheme.typography.bodyMedium, color = c.textMuted)
                    Spacer(Modifier.height(12.dp))
                    NeuTextField(draft.speakMessage, { onChange(draft.copy(speakMessage = it.take(200))) }, "Your message, e.g. Take your vitamins")
                    Spacer(Modifier.height(14.dp))
                    Text("Repeat every", style = MaterialTheme.typography.labelLarge, color = c.textMuted)
                    Spacer(Modifier.height(8.dp))
                    NeuSegmented(SPEAK_EVERY, draft.speakEvery, { onChange(draft.copy(speakEvery = it)) }, Modifier.fillMaxWidth()) { if (it < 60) "${it}s" else "${it / 60}m" }
                }
            }

            NeuCard(Modifier.fillMaxWidth()) {
                SettingRow("Vibration", icon = Icons.Rounded.Vibration) {
                    NeuToggle(draft.vibrate, { onChange(draft.copy(vibrate = it)) })
                }
                if (draft.vibrate) {
                    Spacer(Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        AlarmService.VIBRATION_PATTERNS.keys.forEach { key ->
                            NeuChip(key.uppercase().takeIf { key == "sos" } ?: key.replaceFirstChar { it.uppercase() }, draft.vibrationPattern == key, {
                                onChange(draft.copy(vibrationPattern = key))
                                previewVibration(context, key)
                            })
                        }
                        NeuChip("Custom…", draft.vibrationPattern.startsWith(AlarmService.CUSTOM_VIBRATION), { recording = true })
                    }
                }
                Spacer(Modifier.height(12.dp))
                SettingRow("Flash", icon = Icons.Rounded.FlashOn)
                NeuSegmented(listOf(Alarm.FLASH_OFF, Alarm.FLASH_TORCH, Alarm.FLASH_SCREEN, Alarm.FLASH_BOTH), draft.flash, { onChange(draft.copy(flash = it)) }, Modifier.fillMaxWidth()) {
                    when (it) { Alarm.FLASH_TORCH -> "Torch"; Alarm.FLASH_SCREEN -> "Screen"; Alarm.FLASH_BOTH -> "Both"; else -> "Off" }
                }
            }
            if (recording) VibrationRecorder(
                onSave = { pattern -> onChange(draft.copy(vibrate = true, vibrationPattern = pattern)); recording = false },
                onCancel = { recording = false },
            )

            NeuCard(Modifier.fillMaxWidth()) {
                SettingRow("Wake-up check", icon = Icons.Rounded.Verified) {
                    NeuToggle(draft.wakeUpCheck, { onChange(draft.copy(wakeUpCheck = it)) })
                }
                if (draft.wakeUpCheck) {
                    Spacer(Modifier.height(8.dp))
                    Text("Check after", style = MaterialTheme.typography.labelLarge, color = c.textMuted)
                    Spacer(Modifier.height(8.dp))
                    NeuSegmented(listOf(1, 3, 5, 10, 15), draft.wakeUpCheckMinutes, { onChange(draft.copy(wakeUpCheckMinutes = it)) }, Modifier.fillMaxWidth()) { "${it}m" }
                    Spacer(Modifier.height(14.dp))
                    Text("Time to answer", style = MaterialTheme.typography.labelLarge, color = c.textMuted)
                    Spacer(Modifier.height(8.dp))
                    NeuSegmented(listOf(30, 60, 90, 120), draft.wakeUpCheckResponse, { onChange(draft.copy(wakeUpCheckResponse = it)) }, Modifier.fillMaxWidth()) {
                        if (it < 60) "${it}s" else if (it % 60 == 0) "${it / 60}m" else "${it}s"
                    }
                }
            }

            NeuCard(Modifier.fillMaxWidth()) {
                SettingRow("Snooze", icon = Icons.Rounded.Snooze) {
                    NeuToggle(draft.snoozeEnabled, { onChange(draft.copy(snoozeEnabled = it)) })
                }
                if (draft.snoozeEnabled) {
                    Spacer(Modifier.height(8.dp))
                    Text("Length", style = MaterialTheme.typography.labelLarge, color = c.textMuted)
                    Spacer(Modifier.height(8.dp))
                    NeuSegmented(SNOOZE_LENGTHS, draft.snoozeMinutes, { onChange(draft.copy(snoozeMinutes = it)) }, Modifier.fillMaxWidth()) { "${it}m" }
                    Spacer(Modifier.height(14.dp))
                    Text("Maximum snoozes", style = MaterialTheme.typography.labelLarge, color = c.textMuted)
                    Spacer(Modifier.height(8.dp))
                    NeuSegmented(SNOOZE_LIMITS, draft.maxSnoozes, { onChange(draft.copy(maxSnoozes = it)) }, Modifier.fillMaxWidth()) {
                        if (it < 0) "∞" else "$it×"
                    }
                    Spacer(Modifier.height(6.dp))
                    SettingRow("Shorter each time", subtitle = shrinkHint(draft)) {
                        NeuToggle(draft.snoozeShrink, { onChange(draft.copy(snoozeShrink = it)) })
                    }
                    SettingRow("Solve a sum to snooze") {
                        NeuToggle(draft.snoozeMission, { onChange(draft.copy(snoozeMission = it)) })
                    }
                }
            }

            NeuCard(Modifier.fillMaxWidth()) {
                SettingRow("Morning card", icon = Icons.Rounded.WbTwilight, subtitle = "Your day after dismissing") {
                    NeuToggle(draft.morningCard, {
                        onChange(draft.copy(morningCard = it))
                        if (it && ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_CALENDAR) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                            calendarPermission.launch(android.Manifest.permission.READ_CALENDAR)
                        }
                    })
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                NeuButton({ save(thenPreview = true) }, Modifier.weight(1f)) {
                    Icon(Icons.Rounded.PlayArrow, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Preview")
                }
                if (!isNew) {
                    NeuButton({
                        scope.launch {
                            stored()?.let { if (!gate.allows(it)) return@launch }
                            AlarmActions.delete(draft); onClose()
                        }
                    }, Modifier.weight(1f)) { Text("Delete", color = c.danger) }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

private const val MAX_MISSIONS = 8

@Composable
private fun MissionsCard(
    draft: Alarm,
    missions: List<Mission>,
    onChange: (Alarm) -> Unit,
    onAdd: () -> Unit,
    onEdit: (Int) -> Unit,
    onRemove: (Int) -> Unit,
) {
    val c = Neu.colors
    NeuCard(Modifier.fillMaxWidth()) {
        SectionLabel("Wake-up missions")
        if (missions.size > 1) {
            NeuSegmented(listOf(Alarm.ORDER_ALL, Alarm.ORDER_RANDOM), draft.missionOrder, { onChange(draft.copy(missionOrder = it)) }, Modifier.fillMaxWidth()) {
                if (it == Alarm.ORDER_ALL) "Do all" else "Random pick"
            }
            if (draft.missionOrder == Alarm.ORDER_RANDOM) {
                Spacer(Modifier.height(10.dp))
                Text("Each morning, pick this many from the list below:", style = MaterialTheme.typography.bodyMedium, color = c.textMuted)
                Spacer(Modifier.height(8.dp))
                com.apexy.alarm.ui.neu.NeuStepper(draft.randomCount.coerceIn(1, missions.size - 1), { onChange(draft.copy(randomCount = it)) }, 1..(missions.size - 1))
            }
            Spacer(Modifier.height(12.dp))
        }
        missions.forEachIndexed { i, m ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NeuCard(Modifier.weight(1f), cornerRadius = 18.dp, padding = PaddingValues(horizontal = 14.dp, vertical = 12.dp), onClick = { onEdit(i) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(40.dp).neu(20.dp, elevation = 0.dp, inset = 3.dp), contentAlignment = Alignment.Center) {
                            Icon(m.type.icon, null, Modifier.size(20.dp), tint = c.accent)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("${i + 1}. ${m.type.title}", style = MaterialTheme.typography.titleMedium, color = c.text)
                            Text(m.summary(), style = MaterialTheme.typography.bodyMedium, color = c.textMuted)
                        }
                    }
                }
                Spacer(Modifier.width(12.dp))
                NeuIconButton(Icons.Rounded.Close, "Remove ${m.type.title}", { onRemove(i) }, size = 40.dp)
            }
        }
        if (missions.size < MAX_MISSIONS) {
            if (missions.isNotEmpty()) Spacer(Modifier.height(12.dp))
            NeuButton(onAdd, Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(if (missions.isEmpty()) "Add mission" else "Add another")
            }
        }
        if (missions.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Text("Alarm sound during missions", style = MaterialTheme.typography.labelLarge, color = c.textMuted)
            Spacer(Modifier.height(8.dp))
            NeuSegmented(listOf(Alarm.DURING_NORMAL, Alarm.DURING_QUIET, Alarm.DURING_OFF), draft.duringMission, { onChange(draft.copy(duringMission = it)) }, Modifier.fillMaxWidth()) {
                when (it) { Alarm.DURING_NORMAL -> "Yes"; Alarm.DURING_OFF -> "No"; else -> "Quiet" }
            }
            Spacer(Modifier.height(14.dp))
            Text("Rings again if idle for", style = MaterialTheme.typography.labelLarge, color = c.textMuted)
            Spacer(Modifier.height(8.dp))
            NeuSegmented(IDLE_OPTIONS, draft.idleSeconds, { onChange(draft.copy(idleSeconds = it)) }, Modifier.fillMaxWidth()) { "${it}s" }
        }
    }
}

@Composable
private fun TimeCard(draft: Alarm, onChange: (Alarm) -> Unit) {
    val c = Neu.colors
    val context = LocalContext.current
    val is24 = DateFormat.is24HourFormat(context)
    NeuCard(Modifier.fillMaxWidth(), cornerRadius = 32.dp, padding = PaddingValues(vertical = 18.dp, horizontal = 12.dp)) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            // Inset band marking the selected row.
            Box(Modifier.fillMaxWidth().padding(horizontal = 8.dp).height(64.dp).neu(20.dp, elevation = 0.dp, inset = 5.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                if (is24) {
                    WheelPicker(24, draft.hour, { onChange(draft.copy(hour = it)) }, Modifier.width(96.dp)) { "%02d".format(it) }
                } else {
                    WheelPicker(12, draft.hour % 12, { onChange(draft.copy(hour = it + if (draft.hour >= 12) 12 else 0)) }, Modifier.width(96.dp)) {
                        if (it == 0) "12" else "$it"
                    }
                }
                Text(":", style = MaterialTheme.typography.displayMedium, color = c.text)
                WheelPicker(60, draft.minute, { onChange(draft.copy(minute = it)) }, Modifier.width(96.dp)) { "%02d".format(it) }
                if (!is24) {
                    WheelPicker(
                        2, if (draft.hour >= 12) 1 else 0,
                        { pm -> onChange(draft.copy(hour = draft.hour % 12 + if (pm == 1) 12 else 0)) },
                        Modifier.width(72.dp), textStyle = MaterialTheme.typography.titleLarge, cyclic = false,
                    ) { if (it == 0) "AM" else "PM" }
                }
            }
        }
        val next = AlarmScheduler.nextTrigger(draft.copy(enabled = true, snoozedUntil = 0, skipUntil = 0))
        Text(
            next?.let { "Rings in ${durationUntil(it)}" } ?: "",
            style = MaterialTheme.typography.bodyMedium, color = c.textMuted,
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp),
        )
    }
}

@Composable
private fun SoundRow(name: String, selected: Boolean, playing: Boolean, onSelect: () -> Unit, onPreview: () -> Unit) {
    val c = Neu.colors
    Row(
        Modifier.fillMaxWidth().padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NeuChip(name, selected, onSelect, Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        NeuIconButton(if (playing) Icons.Rounded.Stop else Icons.Rounded.PlayArrow, "Preview $name", onPreview, size = 40.dp, tint = c.accent)
    }
}

private fun resolveUri(sound: String): Uri =
    if (sound == "system") RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM) else Uri.parse(sound)

private fun soundName(context: Context, sound: String): String = runCatching {
    RingtoneManager.getRingtone(context, resolveUri(sound))?.getTitle(context)
}.getOrNull() ?: "Custom sound"

/** Plays one tone at a time inside the editor. */
private class TonePreview(private val context: Context) {
    private var player: MediaPlayer? = null
    var playing by mutableStateOf<String?>(null)
        private set

    fun toggle(key: String, uri: Uri) {
        val wasPlaying = playing == key
        stop()
        if (wasPlaying) return
        player = runCatching {
            MediaPlayer().apply {
                setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).build())
                setDataSource(context, uri)
                isLooping = true
                prepare()
                start()
            }
        }.getOrNull()
        if (player != null) playing = key
    }

    fun stop() {
        player?.runCatching { stop(); release() }
        player = null
        playing = null
    }
}
