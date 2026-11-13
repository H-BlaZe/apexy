package com.hblaze.apexy.ui.screens

import com.hblaze.apexy.ui.neu.Reveal
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import com.hblaze.apexy.ui.neu.CollapsedBar
import com.hblaze.apexy.ui.neu.collapsingTitle
import com.hblaze.apexy.ui.neu.rememberScrollSignal
import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaPlayer
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.BatteryAlert
import androidx.compose.material.icons.rounded.BatteryStd
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MonitorHeart
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hblaze.apexy.ApexyApp
import com.hblaze.apexy.data.AppSettings
import com.hblaze.apexy.data.SleepSession
import com.hblaze.apexy.data.SnoreClip
import com.hblaze.apexy.sleep.Ambient
import com.hblaze.apexy.sleep.Bedtime
import com.hblaze.apexy.sleep.SleepAnalysis
import com.hblaze.apexy.sleep.SleepService
import com.hblaze.apexy.sleep.SleepState
import com.hblaze.apexy.sleep.SnoreDetector
import com.hblaze.apexy.sleep.formatMinutes
import com.hblaze.apexy.ui.neu.Hypnogram
import com.hblaze.apexy.ui.neu.Neu
import com.hblaze.apexy.ui.neu.NeuButton
import com.hblaze.apexy.ui.neu.NeuCard
import com.hblaze.apexy.ui.neu.NeuChip
import com.hblaze.apexy.ui.neu.NeuIconButton
import com.hblaze.apexy.ui.neu.NeuProgressRing
import com.hblaze.apexy.ui.neu.NeuSegmented
import com.hblaze.apexy.ui.neu.NeuSlider
import com.hblaze.apexy.ui.neu.NeuStepper
import com.hblaze.apexy.ui.neu.NeuToggle
import com.hblaze.apexy.ui.neu.SectionLabel
import com.hblaze.apexy.ui.neu.SettingRow
import com.hblaze.apexy.ui.neu.neu
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val TIMER_OPTIONS = listOf(0, 15, 30, 45, 60, 90)

@Composable
fun SleepScreen() {
    val app = ApexyApp.instance
    val settings by app.settings.settings.collectAsStateWithLifecycle(AppSettings())
    val state by SleepService.state.collectAsStateWithLifecycle()
    var openNight by rememberSaveable { mutableStateOf<Long?>(null) }

    // When a tracked night ends, jump straight to its report.
    val finished by SleepService.lastFinishedSession.collectAsStateWithLifecycle()
    LaunchedEffect(finished) {
        if (finished > 0) { openNight = finished; SleepService.lastFinishedSession.value = 0 }
    }

    when {
        openNight != null -> {
            BackHandler { openNight = null }
            NightReportScreen(openNight!!, onBack = { openNight = null })
        }
        state.active -> NightModeScreen(state, settings)
        else -> SleepHome(settings, onOpenNight = { openNight = it })
    }
}

// --- Home ---------------------------------------------------------------------------------------

@Composable
private fun SleepHome(settings: AppSettings, onOpenNight: (Long) -> Unit) {
    val c = Neu.colors
    val app = ApexyApp.instance
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = app.settings
    val alarms by app.db.alarms().observeAll().collectAsStateWithLifecycle(emptyList())
    val nights by app.db.sleep().observeNights().collectAsStateWithLifecycle(emptyList())
    val plan = Bedtime.plan(alarms, settings)

    fun startNight(track: Boolean) = SleepService.start(
        context, mix = settings.soundMix, timerMinutes = settings.soundTimerMinutes,
        track = track, snore = settings.detectSnoring,
    )
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { startNight(track = true) }
    var lowBattery by remember { mutableStateOf<com.hblaze.apexy.sleep.BatteryStatus?>(null) }
    /** Low-battery notice with nothing to start afterwards (shown when tracking is switched on). */
    var warnOnly by remember { mutableStateOf<com.hblaze.apexy.sleep.BatteryStatus?>(null) }
    // Re-read every half minute so plugging in clears the warning.
    var batteryTick by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) { while (true) { kotlinx.coroutines.delay(30_000); batteryTick++ } }
    val battery = remember(batteryTick) { com.hblaze.apexy.sleep.BatteryStatus.read(context) }
    fun beginNight() {
        val needsMic = settings.trackSleep &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED
        if (needsMic) micPermission.launch(Manifest.permission.RECORD_AUDIO) else startNight(settings.trackSleep)
    }

    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val scrollSignal = rememberScrollSignal(listState)
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item { Text("Sleep", style = MaterialTheme.typography.headlineMedium, color = c.text, modifier = Modifier.collapsingTitle(scrollSignal).padding(start = 4.dp)) }

            item { Reveal(scrollSignal) {
                NeuCard(Modifier.fillMaxWidth(), cornerRadius = 30.dp, padding = PaddingValues(22.dp)) {
                    Text("TONIGHT", style = MaterialTheme.typography.labelMedium, color = c.textMuted)
                    if (plan != null) {
                        Text("Bed by ${Bedtime.clock(context, plan.bedtime)}", fontSize = 30.sp, fontWeight = FontWeight.Light, color = c.text)
                        Text(
                            "${formatMinutes(settings.sleepGoalMinutes)} sleep · alarm ${Bedtime.clock(context, plan.alarmAt)}",
                            style = MaterialTheme.typography.bodyMedium, color = c.textMuted,
                        )
                    } else {
                        Text("No alarm set", fontSize = 26.sp, fontWeight = FontWeight.Light, color = c.text)
                    }
                    Spacer(Modifier.height(18.dp))
                    NeuButton({
                        // Tracking all night unplugged on a low battery risks a dead phone and a silent alarm.
                        val battery = com.hblaze.apexy.sleep.BatteryStatus.read(context)
                        if (settings.trackSleep && battery.riskyForNight) lowBattery = battery else beginNight()
                    }, Modifier.fillMaxWidth().height(64.dp), primary = true, cornerRadius = 22.dp) {
                        Icon(Icons.Rounded.Bedtime, null); Spacer(Modifier.width(10.dp))
                        Text(if (settings.trackSleep) "Start sleep" else "Play sleep sounds", fontSize = 18.sp)
                    }
                    Spacer(Modifier.height(10.dp))
                    SettingRow("Track my sleep", icon = Icons.Rounded.MonitorHeart) {
                        NeuToggle(settings.trackSleep, { on ->
                            scope.launch { repo.setTrackSleep(on) }
                            // Turning tracking on with a low, unplugged battery: say so straight away.
                            val battery = com.hblaze.apexy.sleep.BatteryStatus.read(context)
                            if (on && battery.riskyForNight) warnOnly = battery
                        })
                    }
                    if (settings.trackSleep && battery.riskyForNight) {
                        Spacer(Modifier.height(8.dp))
                        Row(
                            Modifier.fillMaxWidth().neu(16.dp, elevation = 0.dp, inset = 3.dp).padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Rounded.BatteryAlert, null, tint = c.danger, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(10.dp))
                            Text(
                                "Battery ${battery.percent}% · plug in to track",
                                style = MaterialTheme.typography.bodyMedium, color = c.text,
                            )
                        }
                    }
                    if (settings.trackSleep && SnoreDetector.SUPPORTED) {
                        SettingRow("Record snoring", icon = Icons.Rounded.GraphicEq) {
                            NeuToggle(settings.detectSnoring, { scope.launch { repo.setDetectSnoring(it) } })
                        }
                    }
                }
            } }

            item { Reveal(scrollSignal) { SoundMixer(Ambient.parseMix(settings.soundMix), onCommit = { scope.launch { repo.setSoundMix(Ambient.formatMix(it)) } }) } }

            item { Reveal(scrollSignal) {
                NeuCard(Modifier.fillMaxWidth()) {
                    SectionLabel("Sound timer")
                    NeuSegmented(TIMER_OPTIONS, settings.soundTimerMinutes, { m -> scope.launch { repo.setSoundTimer(m) } }, Modifier.fillMaxWidth()) {
                        if (it == 0) "Off" else "${it}m"
                    }
                }
            } }

            item { Reveal(scrollSignal) { BedtimeCard(settings) } }

            item { Reveal(scrollSignal) { SectionLabel("Past nights", Modifier.padding(top = 4.dp)) } }
            if (nights.isEmpty()) item { Reveal(scrollSignal) { com.hblaze.apexy.ui.neu.EmptyHint(Icons.Rounded.Bedtime, "No nights yet") } }
            items(nights, key = { it.id }) { Reveal(scrollSignal) { NightRow(it, onClick = { onOpenNight(it.id) }) } }
        }
        CollapsedBar("Sleep", scrollSignal)
    }

    warnOnly?.let { b ->
        Dialog(onDismissRequest = { warnOnly = null }) {
            NeuCard(Modifier.fillMaxWidth(), cornerRadius = 30.dp, padding = PaddingValues(24.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.foundation.layout.Box(
                        Modifier.size(56.dp).neu(28.dp, elevation = 0.dp, inset = 5.dp),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Rounded.BatteryAlert, null, tint = c.danger) }
                    Spacer(Modifier.width(16.dp))
                    Text("Battery ${b.percent}%", style = MaterialTheme.typography.titleLarge, color = c.text)
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    "Tracking runs all night. Charge your phone so the alarm still rings.",
                    style = MaterialTheme.typography.bodyLarge, color = c.textMuted,
                )
                Spacer(Modifier.height(22.dp))
                NeuButton({ warnOnly = null }, Modifier.fillMaxWidth(), primary = true) { Text("Got it") }
            }
        }
    }

    lowBattery?.let { b ->
        Dialog(onDismissRequest = { lowBattery = null }) {
            NeuCard(Modifier.fillMaxWidth(), cornerRadius = 30.dp, padding = PaddingValues(24.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.foundation.layout.Box(
                        Modifier.size(56.dp).neu(28.dp, elevation = 0.dp, inset = 5.dp),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Rounded.BatteryAlert, null, tint = c.danger) }
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text("Battery ${b.percent}%", style = MaterialTheme.typography.titleLarge, color = c.text)
                        Text("Not charging", style = MaterialTheme.typography.bodyMedium, color = c.textMuted)
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    "Charge your phone so the alarm still rings.",
                    style = MaterialTheme.typography.bodyLarge, color = c.textMuted,
                )
                Spacer(Modifier.height(22.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    NeuButton({ lowBattery = null }, Modifier.weight(1f)) { Text("Cancel") }
                    NeuButton({ lowBattery = null; beginNight() }, Modifier.weight(1f), primary = true) { Text("Start anyway") }
                }
            }
        }
    }
}

/**
 * Sound chips and volume sliders. Taps change a local copy at once, so the screen never waits on
 * storage or the service: [onChange] fires on every change, [onCommit] once a tap or drag is done.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SoundMixer(mix: Map<String, Float>, onChange: (Map<String, Float>) -> Unit = {}, onCommit: (Map<String, Float>) -> Unit) {
    val c = Neu.colors
    var local by remember { mutableStateOf(mix) }
    var dragging by remember { mutableStateOf(false) }
    // Follow outside changes (another screen, the notification's Stop) unless the user is mid-drag.
    LaunchedEffect(mix) { if (!dragging) local = mix }
    fun set(next: Map<String, Float>, commit: Boolean) {
        local = next
        onChange(next)
        if (commit) onCommit(next)
    }
    NeuCard(Modifier.fillMaxWidth()) {
        SectionLabel("Sleep sounds")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Ambient.sounds.forEach { s ->
                val on = s.id in local
                NeuChip(s.name, on, {
                    com.hblaze.apexy.ui.UiSounds.play(com.hblaze.apexy.ui.UiSounds.Kind.TAP)
                    set(if (on) local - s.id else local + (s.id to 0.6f), commit = true)
                })
            }
        }
        local.forEach { (id, vol) ->
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(Ambient.sounds.first { it.id == id }.name, style = MaterialTheme.typography.labelLarge, color = c.textMuted, modifier = Modifier.width(96.dp))
                NeuSlider(
                    vol,
                    { v -> dragging = true; set(local + (id to v.coerceAtLeast(0.05f)), commit = false) },
                    Modifier.weight(1f),
                    onValueChangeFinished = { dragging = false; onCommit(local) },
                )
            }
        }
    }
}

@Composable
private fun BedtimeCard(settings: AppSettings) {
    val c = Neu.colors
    val app = ApexyApp.instance
    val scope = rememberCoroutineScope()
    fun update(block: suspend () -> Unit) = scope.launch { block(); Bedtime.reschedule(app) }
    NeuCard(Modifier.fillMaxWidth()) {
        SettingRow("Bedtime reminder", icon = Icons.Rounded.NotificationsActive) {
            NeuToggle(settings.bedtimeReminder, { on -> update { app.settings.setBedtimeReminder(on) } })
        }
        Spacer(Modifier.height(10.dp))
        Text("Sleep goal", style = MaterialTheme.typography.labelLarge, color = c.textMuted)
        Spacer(Modifier.height(8.dp))
        GoalStepper(settings.sleepGoalMinutes) { m -> update { app.settings.setSleepGoal(m) } }
        if (settings.bedtimeReminder) {
            Spacer(Modifier.height(14.dp))
            Text("Remind me", style = MaterialTheme.typography.labelLarge, color = c.textMuted)
            Spacer(Modifier.height(8.dp))
            NeuSegmented(listOf(0, 15, 30, 60), settings.bedtimeLeadMinutes, { m -> update { app.settings.setBedtimeLead(m) } }, Modifier.fillMaxWidth()) {
                if (it == 0) "At bedtime" else "-${it}m"
            }
        }
    }
}

/** Half-hour steps between 5 and 10 hours, shown as hours. */
@Composable
private fun GoalStepper(minutes: Int, onChange: (Int) -> Unit) {
    val halfHours = minutes / 30
    NeuStepper(halfHours, { onChange(it * 30) }, range = 10..20, step = 1, display = { formatMinutes(it * 30) })
}

@Composable
private fun NightRow(night: SleepSession, onClick: () -> Unit) {
    val c = Neu.colors
    val report = remember(night.id, night.samples) { SleepAnalysis.analyze(SleepAnalysis.decode(night.samples), night.goalMinutes) }
    val date = Instant.ofEpochMilli(night.startedAt).atZone(ZoneId.systemDefault())
    NeuCard(Modifier.fillMaxWidth(), cornerRadius = 22.dp, padding = PaddingValues(18.dp), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(date.format(DateTimeFormatter.ofPattern("EEEE, d MMM")), style = MaterialTheme.typography.titleMedium, color = c.text)
                Text(
                    "${formatMinutes(report.minutesAsleep)} asleep" + if (night.snoreMinutes > 0) " · snored ${night.snoreMinutes}m" else "",
                    style = MaterialTheme.typography.bodyMedium, color = c.textMuted,
                )
            }
            Text("${report.score}", fontSize = 26.sp, color = c.text)
        }
    }
}

// --- Night mode ---------------------------------------------------------------------------------

@Composable
private fun NightModeScreen(state: SleepState, settings: AppSettings) {
    val c = Neu.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(1_000); now = System.currentTimeMillis() } }
    val clock = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault())

    LazyColumn(
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(com.hblaze.apexy.ui.formatTime(context, clock.hour, clock.minute), fontSize = 64.sp, fontWeight = FontWeight.ExtraLight, color = c.text)
                // Re-read with the clock tick; shows only while tracking unplugged.
                val battery = remember(now / 30_000) { com.hblaze.apexy.sleep.BatteryStatus.read(context) }
                if (state.tracking && !battery.charging) {
                    Row(
                        Modifier.padding(vertical = 8.dp).neu(16.dp, elevation = 0.dp, inset = 3.dp).padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            if (battery.riskyForNight) Icons.Rounded.BatteryAlert else Icons.Rounded.BatteryStd, "Not charging",
                            tint = if (battery.riskyForNight) c.danger else c.textMuted, modifier = Modifier.size(18.dp),
                        )
                        Text(" ${battery.percent}% · plug in", style = MaterialTheme.typography.labelLarge, color = if (battery.riskyForNight) c.danger else c.textMuted)
                    }
                }
                Text(
                    if (state.tracking) "Tracking ${formatMinutes(((now - state.startedAt) / 60_000).toInt())}" else "Sleep sounds",
                    style = MaterialTheme.typography.titleMedium, color = c.textMuted,
                )
                if (state.tracking) {
                    Text(
                        listOfNotNull(
                            if (state.micOn) "sound on" else "motion only",
                            if (state.snoreModel) "snoring detection on" else null,
                            if (state.snoreClips > 0) "${state.snoreClips} snore clips" else null,
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium, color = c.textMuted,
                    )
                }
            }
        }
        item {
            val total = settings.soundTimerMinutes * 60_000f
            val left = (state.timerEndsAt - now).coerceAtLeast(0)
            NeuProgressRing(if (state.timerEndsAt > 0 && total > 0) left / total else if (state.playing) 1f else 0f, size = 200.dp) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Rounded.Bedtime, null, tint = c.accent, modifier = Modifier.size(32.dp))
                    Text(
                        when {
                            !state.playing -> "Sounds off"
                            state.timerEndsAt > 0 -> "${(left / 60_000) + 1}m"
                            else -> "No timer"
                        },
                        style = MaterialTheme.typography.titleMedium, color = c.text,
                    )
                }
            }
        }
        item {
            SoundMixer(
                state.mix.takeIf { state.playing } ?: emptyMap(),
                onChange = { m ->
                    if (m.isEmpty()) SleepService.send(context, SleepService.ACTION_STOP_SOUNDS)
                    else SleepService.send(context, SleepService.ACTION_SET_MIX, Ambient.formatMix(m))
                },
                onCommit = { m -> if (m.isNotEmpty()) scope.launch { ApexyApp.instance.settings.setSoundMix(Ambient.formatMix(m)) } },
            )
        }
        if (state.playing) item {
            NeuButton({ SleepService.send(context, SleepService.ACTION_STOP_SOUNDS) }, Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.Stop, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Stop sounds")
            }
        }
        item {
            NeuButton(
                { SleepService.send(context, SleepService.ACTION_STOP) },
                Modifier.fillMaxWidth().height(68.dp), primary = true, cornerRadius = 24.dp,
            ) { Text(if (state.tracking) "I'm awake" else "Done", fontSize = 19.sp, fontWeight = FontWeight.SemiBold) }
        }
    }
}

// --- Night report -------------------------------------------------------------------------------

@Composable
private fun NightReportScreen(id: Long, onBack: () -> Unit) {
    val c = Neu.colors
    val context = LocalContext.current
    val app = ApexyApp.instance
    val scope = rememberCoroutineScope()
    var night by remember { mutableStateOf<SleepSession?>(null) }
    LaunchedEffect(id) { night = app.db.sleep().get(id) }
    val clips by app.db.sleep().observeClips(id).collectAsStateWithLifecycle(emptyList())
    var confirmDelete by remember { mutableStateOf(false) }
    val n = night ?: return
    val report = remember(n) { SleepAnalysis.analyze(SleepAnalysis.decode(n.samples), n.goalMinutes) }
    val start = Instant.ofEpochMilli(n.startedAt).atZone(ZoneId.systemDefault())
    val snoreMinutes = clips.map { ((it.startedAt - n.startedAt) / 60_000).toInt() }

    LazyColumn(
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                NeuIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Back", onBack, size = 46.dp)
                Text(start.format(DateTimeFormatter.ofPattern("EEEE, d MMMM")), style = MaterialTheme.typography.titleLarge, color = c.text, modifier = Modifier.padding(start = 16.dp))
            }
        }
        item {
            NeuCard(Modifier.fillMaxWidth(), cornerRadius = 30.dp, padding = PaddingValues(22.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    NeuProgressRing(report.score / 100f, size = 120.dp) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("${report.score}", fontSize = 32.sp, color = c.text)
                            Text("score", style = MaterialTheme.typography.labelMedium, color = c.textMuted)
                        }
                    }
                    Spacer(Modifier.width(18.dp))
                    Column {
                        Text(formatMinutes(report.minutesAsleep), fontSize = 30.sp, fontWeight = FontWeight.Light, color = c.text)
                        Text("asleep of ${formatMinutes(n.goalMinutes)} goal", style = MaterialTheme.typography.bodyMedium, color = c.textMuted)
                        Text("${Bedtime.clock(context, n.startedAt)} → ${Bedtime.clock(context, n.endedAt)}", style = MaterialTheme.typography.bodyMedium, color = c.textMuted)
                    }
                }
            }
        }
        item {
            NeuCard(Modifier.fillMaxWidth()) {
                SectionLabel("Sleep stages")
                Hypnogram(report.stages, snoreMinutes, timeLabel = { m -> Bedtime.clock(context, n.startedAt + m * 60_000L) })
                if (snoreMinutes.isNotEmpty()) {
                    Text("Marks under the timeline are snore clips.", style = MaterialTheme.typography.labelMedium, color = c.textMuted)
                }
            }
        }
        item {
            NeuCard(Modifier.fillMaxWidth()) {
                SectionLabel("Details")
                Metric("Time in bed", formatMinutes(report.minutesInBed))
                Metric("Fell asleep after", formatMinutes(report.sleepOnsetMinutes))
                Metric("Awake", formatMinutes(report.awakeMinutes))
                Metric("Light sleep", formatMinutes(report.lightMinutes))
                Metric("Deep sleep", formatMinutes(report.deepMinutes))
                Metric("Snoring", if (n.snoreMinutes == 0) "None detected" else formatMinutes(n.snoreMinutes))
                Metric("Average noise", "${report.avgNoiseDb.toInt()} dB")
            }
        }
        if (clips.isNotEmpty()) item { SnoreClips(clips) }
        item {
            Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Info, null, tint = c.textMuted, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("Estimate, not a medical measurement", style = MaterialTheme.typography.labelMedium, color = c.textMuted)
            }
        }
        item {
            NeuButton({ confirmDelete = true }, Modifier.fillMaxWidth()) { Text("Delete this night", color = c.danger) }
        }
    }

    if (confirmDelete) Dialog(onDismissRequest = { confirmDelete = false }) {
        NeuCard(Modifier.fillMaxWidth(), cornerRadius = 30.dp, padding = PaddingValues(24.dp)) {
            Text("Delete this night?", style = MaterialTheme.typography.titleLarge, color = c.text)
            Spacer(Modifier.height(8.dp))
            Text("Report and recordings are removed for good.", color = c.textMuted)
            Spacer(Modifier.height(22.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                NeuButton({ confirmDelete = false }, Modifier.weight(1f)) { Text("Cancel") }
                NeuButton({
                    confirmDelete = false
                    scope.launch {
                        app.db.sleep().deleteClips(n.id)
                        app.db.sleep().delete(n)
                        File(context.filesDir, "snores/${n.id}").deleteRecursively()
                        onBack()
                    }
                }, Modifier.weight(1f)) { Text("Delete", color = c.danger) }
            }
        }
    }
}

@Composable
private fun Metric(label: String, value: String) {
    val c = Neu.colors
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = c.textMuted, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.titleMedium, color = c.text)
    }
}

@Composable
private fun SnoreClips(clips: List<SnoreClip>) {
    val c = Neu.colors
    val context = LocalContext.current
    var playing by remember { mutableStateOf<Long?>(null) }
    val player = remember { arrayOfNulls<MediaPlayer>(1) }
    fun stop() { player[0]?.runCatching { stop(); release() }; player[0] = null; playing = null }
    DisposableEffect(Unit) { onDispose { stop() } }
    NeuCard(Modifier.fillMaxWidth()) {
        SectionLabel("Snore recordings")
        clips.forEach { clip ->
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                NeuIconButton(if (playing == clip.id) Icons.Rounded.Stop else Icons.Rounded.PlayArrow, "Play", {
                    val was = playing == clip.id
                    stop()
                    if (!was) {
                        player[0] = runCatching {
                            MediaPlayer().apply {
                                setDataSource(clip.path); prepare(); start()
                                setOnCompletionListener { stop() }
                            }
                        }.getOrNull()
                        if (player[0] != null) playing = clip.id
                    }
                }, size = 42.dp, tint = c.accent)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(Bedtime.clock(context, clip.startedAt), style = MaterialTheme.typography.titleMedium, color = c.text)
                    Text("${clip.durationMs / 1000}s · ${(clip.score * 100).toInt()}% sure", style = MaterialTheme.typography.bodyMedium, color = c.textMuted)
                }
            }
        }
    }
}
