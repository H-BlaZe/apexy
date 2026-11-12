package com.apexy.alarm.ui

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AlarmOff
import androidx.compose.material.icons.rounded.Snooze
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.apexy.alarm.ApexyApp
import com.apexy.alarm.core.AlarmService
import com.apexy.alarm.core.Ringing
import com.apexy.alarm.data.AppSettings
import com.apexy.alarm.missions.MissionCodec
import com.apexy.alarm.missions.MissionHost
import com.apexy.alarm.missions.icon
import com.apexy.alarm.ui.neu.ApexyTheme
import com.apexy.alarm.ui.neu.Neu
import com.apexy.alarm.ui.neu.NeuButton
import com.apexy.alarm.ui.neu.neu
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Calendar

/** Full-screen alarm shown over the lock screen while [AlarmService] rings. */
class RingingActivity : ComponentActivity() {
    @Volatile private var lockVolume = true
    /** Set when the missions are done: the alarm stops and this screen turns into the reward. */
    private val celebration = androidx.compose.runtime.mutableStateOf<com.apexy.alarm.core.MissionStats?>(null)
    /** After dismissing (and any celebration), the day-ahead card, when the alarm asks for it. */
    private val morning = androidx.compose.runtime.mutableStateOf(false)
    private var wantsMorning = false
    private val done get() = celebration.value != null || morning.value

    override fun onKeyDown(keyCode: Int, event: android.view.KeyEvent?): Boolean {
        val volumeKey = keyCode == android.view.KeyEvent.KEYCODE_VOLUME_DOWN ||
            keyCode == android.view.KeyEvent.KEYCODE_VOLUME_UP || keyCode == android.view.KeyEvent.KEYCODE_VOLUME_MUTE
        val ringing = AlarmService.state.value
        if (volumeKey && lockVolume && ringing != null && !ringing.preview) return true
        return super.onKeyDown(keyCode, event)
    }

    override fun onStart() {
        super.onStart()
        if (AlarmService.state.value != null) AlarmService.send(this, AlarmService.ACTION_SCREEN_SHOWN)
    }

    override fun onStop() {
        super.onStop()
        if (done && !isChangingConfigurations) { finish(); return }
        if (AlarmService.state.value != null && !isFinishing) AlarmService.send(this, AlarmService.ACTION_SCREEN_HIDDEN)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O_MR1) {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        enableEdgeToEdge()
        // Back must not silence an alarm.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { if (done) finish() }
        })

        val app = application as ApexyApp
        setContent {
            val settings by app.settings.settings.collectAsStateWithLifecycle(AppSettings())
            val ringing by AlarmService.state.collectAsStateWithLifecycle()
            LaunchedEffect(settings.lockVolume) { lockVolume = settings.lockVolume }
            ApexyTheme(settings) {
                val current = ringing
                val party = celebration.value
                when {
                    morning.value -> MorningCardScreen { finish() }
                    party != null -> CelebrationScreen(party) {
                        if (wantsMorning) { celebration.value = null; morning.value = true } else finish()
                    }
                    current == null -> LaunchedEffect(Unit) { delay(400); if (AlarmService.state.value == null) finish() }
                    current.wakeCheck -> com.apexy.alarm.ui.neu.WithAccent(current.alarm.accentColor) { WakeCheckScreen(current) }
                    else -> com.apexy.alarm.ui.neu.WithAccent(current.alarm.accentColor) {
                        RingingScreen(
                            current,
                            onMissionsDone = { stats ->
                                wantsMorning = current.alarm.morningCard
                                celebration.value = stats
                                AlarmService.dismissAfterMissions(this@RingingActivity, stats)
                            },
                            onDismissed = {
                                if (current.alarm.morningCard) morning.value = true
                                AlarmService.send(this@RingingActivity, AlarmService.ACTION_DISMISS)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WakeCheckScreen(check: Ringing) {
    val c = Neu.colors
    val context = androidx.compose.ui.platform.LocalContext.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(200); now = System.currentTimeMillis() } }
    val total = check.alarm.wakeUpCheckResponse * 1000f
    val left = (check.checkDeadline - now).coerceAtLeast(0)
    Column(
        Modifier.fillMaxSize().background(c.surface).systemBarsPadding().padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(40.dp))
        Text("Still awake?", fontSize = 40.sp, fontWeight = FontWeight.Light, color = c.text)
        Text(
            "Confirm or it rings again",
            style = MaterialTheme.typography.bodyLarge, color = c.textMuted,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp, start = 12.dp, end = 12.dp),
        )
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            com.apexy.alarm.ui.neu.NeuProgressRing(left / total, size = 250.dp) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${(left + 999) / 1000}", fontSize = 64.sp, fontWeight = FontWeight.Light, color = if (left < 10_000) c.danger else c.text)
                    Text("s", style = MaterialTheme.typography.labelLarge, color = c.textMuted)
                }
            }
        }
        com.apexy.alarm.ui.neu.NeuDoneButton(
            "I'm awake",
            onConfirm = { true },
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 24.dp,
            contentPadding = PaddingValues(22.dp),
            textStyle = MaterialTheme.typography.titleLarge,
            onFinished = { AlarmService.send(context, AlarmService.ACTION_CHECK_PASS) },
        )
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun RingingScreen(ringing: Ringing, onMissionsDone: (com.apexy.alarm.core.MissionStats) -> Unit, onDismissed: () -> Unit) {
    val c = Neu.colors
    val context = androidx.compose.ui.platform.LocalContext.current
    // A random draw is made once per ring, so leaving and re-entering the missions doesn't reroll it.
    val missions = remember(ringing.eventId, ringing.startedAt) {
        val all = MissionCodec.decode(ringing.alarm.missions)
        if (ringing.alarm.missionOrder == com.apexy.alarm.data.Alarm.ORDER_RANDOM && all.size > 1) {
            all.shuffled().take(ringing.alarm.randomCount.coerceIn(1, all.size))
        } else all
    }
    var inMission by remember { mutableStateOf(false) }
    var snoozeCheck by remember { mutableStateOf(false) }
    if (inMission) {
        MissionHost(
            missions = missions,
            onAllDone = onMissionsDone,
            idleTimeoutMs = ringing.alarm.idleSeconds.coerceIn(10, 120) * 1000L,
            onTimeout = {
                inMission = false
                AlarmService.send(context, AlarmService.ACTION_MISSION_IDLE)
            },
            modifier = Modifier.background(c.surface).systemBarsPadding().padding(horizontal = 24.dp, vertical = 16.dp),
        )
        return
    }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(1000); now = System.currentTimeMillis() } }
    val cal = Calendar.getInstance().apply { timeInMillis = now }
    val (clock, suffix) = timeParts(context, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))

    if (snoozeCheck) {
        SnoozeCheck(onPassed = { snoozeCheck = false; AlarmService.send(context, AlarmService.ACTION_SNOOZE) }, onCancel = { snoozeCheck = false })
        return
    }

    val pulse = rememberInfiniteTransition(label = "pulse")
    val glow by pulse.animateFloat(0.9f, 1.08f, infiniteRepeatable(tween(1100, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "glow")
    val ringAlpha by pulse.animateFloat(0.25f, 0.7f, infiniteRepeatable(tween(1100), RepeatMode.Reverse), label = "alpha")

    // Screen flash: the background pulses to the accent colour.
    val flash by pulse.animateFloat(0f, 1f, infiniteRepeatable(tween(450), RepeatMode.Reverse), label = "flash")
    val background = if (ringing.flashScreen) androidx.compose.ui.graphics.lerp(c.surface, c.primary, flash * 0.85f) else c.surface
    Column(
        Modifier.fillMaxSize().background(background).systemBarsPadding().padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(32.dp))
        Text(LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d MMMM")), style = MaterialTheme.typography.titleMedium, color = c.textMuted)
        Text(clock + (suffix?.let { " $it" } ?: ""), fontSize = 76.sp, fontWeight = FontWeight.ExtraLight, color = c.text)
        if (ringing.alarm.label.isNotBlank()) {
            Text(ringing.alarm.label, style = MaterialTheme.typography.titleLarge, color = c.accent)
        }
        if (ringing.preview) Text("Preview", style = MaterialTheme.typography.bodyMedium, color = c.textMuted)

        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Box(Modifier.size(250.dp).scale(glow).alpha(ringAlpha).neu(125.dp, elevation = 0.dp, fill = c.primary.copy(alpha = 0.25f)))
            Box(Modifier.size(220.dp).neu(110.dp, elevation = 14.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.size(170.dp).neu(85.dp, elevation = 0.dp, inset = 9.dp), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(96.dp).scale(glow).neu(48.dp, elevation = 8.dp, fill = c.primary), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.AlarmOff, null, tint = c.onPrimary, modifier = Modifier.size(40.dp))
                    }
                }
            }
        }

        val snoozesLeft = if (ringing.alarm.maxSnoozes < 0) null else ringing.alarm.maxSnoozes - ringing.alarm.snoozeCount
        if (ringing.canSnooze) {
            NeuButton({
                if (ringing.alarm.snoozeMission && !ringing.preview) snoozeCheck = true
                else AlarmService.send(context, AlarmService.ACTION_SNOOZE)
            }, Modifier.fillMaxWidth().height(64.dp)) {
                Icon(Icons.Rounded.Snooze, null, Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text("Snooze ${ringing.alarm.nextSnoozeMinutes}m" + (snoozesLeft?.let { "  ·  $it left" } ?: ""))
            }
        } else if (!ringing.preview && ringing.alarm.snoozeEnabled) {
            Text("No snoozes left — time to get up!", color = c.danger, style = MaterialTheme.typography.labelLarge)
        }
        Spacer(Modifier.height(18.dp))
        if (missions.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                missions.forEach { Icon(it.type.icon, it.type.title, Modifier.size(22.dp), tint = c.textMuted) }
            }
            Spacer(Modifier.height(14.dp))
        }
        NeuButton(
            {
                if (missions.isEmpty()) onDismissed()
                else {
                    inMission = true
                    AlarmService.send(context, AlarmService.ACTION_MISSION_START)
                }
            },
            Modifier.fillMaxWidth().height(72.dp), primary = true, cornerRadius = 24.dp,
            contentPadding = PaddingValues(16.dp),
        ) {
            if (missions.isNotEmpty()) { Icon(Icons.Rounded.Extension, null); Spacer(Modifier.width(10.dp)) }
            Text(if (missions.isEmpty()) "Dismiss" else "Start mission${if (missions.size > 1) "s" else ""}", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(12.dp))
    }
}

/** One sum to earn a snooze: enough to wake the brain, not a full mission. */
@Composable
private fun SnoozeCheck(onPassed: () -> Unit, onCancel: () -> Unit) {
    val c = Neu.colors
    var problem by remember { mutableStateOf(com.apexy.alarm.missions.MathProblems.make(2)) }
    var input by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().background(c.surface).systemBarsPadding().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Solve to snooze", style = MaterialTheme.typography.titleLarge, color = c.text)
        Spacer(Modifier.height(18.dp))
        Text("${problem.text} =", fontSize = 40.sp, fontWeight = FontWeight.Light, color = c.text)
        Spacer(Modifier.height(14.dp))
        com.apexy.alarm.ui.neu.NeuWell(Modifier.fillMaxWidth().height(76.dp), cornerRadius = 24.dp) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(input.ifEmpty { if (wrong) "Try again" else "?" }, fontSize = 34.sp, color = if (wrong && input.isEmpty()) c.danger else c.text)
            }
        }
        Spacer(Modifier.weight(1f))
        com.apexy.alarm.ui.neu.NumberPad(
            onDigit = { if (input.length < 6) { input += it; wrong = false } },
            onBackspace = { input = input.dropLast(1) },
            onSubmit = {
                if (input.toLongOrNull() == problem.answer) onPassed()
                else { wrong = true; input = ""; problem = com.apexy.alarm.missions.MathProblems.make(2) }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        androidx.compose.material3.TextButton(onClick = onCancel) { Text("Back", color = c.textMuted) }
    }
}
