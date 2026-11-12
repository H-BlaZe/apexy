package com.apexy.alarm.missions

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.nfc.NfcAdapter
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Nfc
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apexy.alarm.ui.neu.MissionCounter
import com.apexy.alarm.ui.neu.Neu
import com.apexy.alarm.ui.neu.NeuProgressRing
import com.apexy.alarm.ui.neu.neu
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.random.Random

/** Whether this phone has what the mission needs right now; if not, the emergency typing challenge stands in. */
fun MissionType.availableOn(context: Context): Boolean = when (this) {
    MissionType.NFC -> NfcAdapter.getDefaultAdapter(context)?.isEnabled == true
    MissionType.LIGHT -> context.getSystemService(SensorManager::class.java)?.getDefaultSensor(Sensor.TYPE_LIGHT) != null
    MissionType.STEADY -> context.getSystemService(SensorManager::class.java)?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null
    else -> true
}

private tailrec fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}

/** Reads NFC tag ids while on screen, using reader mode so no other app grabs the tag. */
@Composable
fun NfcReader(onTag: (String) -> Unit) {
    val context = LocalContext.current
    val view = LocalView.current
    val latest by rememberUpdatedState(onTag)
    DisposableEffect(Unit) {
        val activity = context.activity()
        val adapter = NfcAdapter.getDefaultAdapter(context)
        val flags = NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B or NfcAdapter.FLAG_READER_NFC_F or
            NfcAdapter.FLAG_READER_NFC_V or NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS
        if (activity != null && adapter != null) {
            runCatching {
                adapter.enableReaderMode(activity, { tag ->
                    val id = tag.id.joinToString("") { "%02X".format(it) }
                    view.post { latest(id) }
                }, flags, null)
            }
        }
        onDispose { if (activity != null) runCatching { adapter?.disableReaderMode(activity) } }
    }
}

// --- NFC ----------------------------------------------------------------------------------------

@Composable
fun NfcMission(mission: Mission, cb: MissionCallbacks) {
    val c = Neu.colors
    val view = LocalView.current
    var wrong by remember { mutableStateOf(false) }
    NfcReader { id ->
        cb.onInteraction()
        if (id in mission.codes) {
            view.performHapticFeedback(android.view.HapticFeedbackConstants.CONFIRM)
            cb.onComplete()
        } else wrong = true
    }
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        NfcPulse()
        Spacer(Modifier.height(28.dp))
        Text(
            if (wrong) "That's a different tag" else "Hold the back of your phone to your tag",
            style = MaterialTheme.typography.titleMedium, color = if (wrong) c.danger else c.textMuted, textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun NfcPulse() {
    val c = Neu.colors
    val scale = remember { Animatable(1f) }
    LaunchedEffect(Unit) {
        while (true) { scale.animateTo(1.1f, androidx.compose.animation.core.tween(700)); scale.animateTo(1f, androidx.compose.animation.core.tween(700)) }
    }
    Box(Modifier.size(200.dp).neu(100.dp, elevation = 12.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.size((110 * scale.value).dp).neu(55.dp, elevation = 0.dp, inset = 7.dp), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Nfc, null, tint = c.accent, modifier = Modifier.size(52.dp))
        }
    }
}

// --- Light --------------------------------------------------------------------------------------

/** Lux needed per difficulty: a lamp, a bright room, a window, daylight. */
val LIGHT_LUX = intArrayOf(40, 120, 300, 700, 1500)

@Composable
fun LightMission(mission: Mission, cb: MissionCallbacks) {
    val c = Neu.colors
    val context = LocalContext.current
    val need = LIGHT_LUX[mission.difficulty - 1]
    var lux by remember { mutableFloatStateOf(0f) }
    var litMs by remember { mutableIntStateOf(0) }
    val total = mission.count * 1000
    val latestCb by rememberUpdatedState(cb)

    DisposableEffect(Unit) {
        val sm = context.getSystemService(SensorManager::class.java)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) { lux = e.values[0] }
            override fun onAccuracyChanged(s: Sensor?, a: Int) {}
        }
        sm.getDefaultSensor(Sensor.TYPE_LIGHT)?.let { sm.registerListener(listener, it, SensorManager.SENSOR_DELAY_UI) }
        onDispose { sm.unregisterListener(listener) }
    }
    LaunchedEffect(Unit) {
        while (litMs < total) {
            delay(100)
            if (lux >= need) { litMs += 100; latestCb.onInteraction() }
        }
        latestCb.onComplete()
    }

    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            if (lux >= need) "Stay in the light" else "Go to a lamp or window",
            style = MaterialTheme.typography.titleLarge, color = if (lux >= need) c.success else c.text,
        )
        Spacer(Modifier.height(28.dp))
        NeuProgressRing(litMs.toFloat() / total) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${((total - litMs + 999) / 1000).coerceAtLeast(0)}", fontSize = 64.sp, fontWeight = FontWeight.Light, color = c.text)
                Text("s left", style = MaterialTheme.typography.labelLarge, color = c.textMuted)
            }
        }
        Spacer(Modifier.height(24.dp))
        // How close the reading is to the goal.
        val ratio = (lux / need).coerceIn(0f, 1f)
        Box(Modifier.fillMaxWidth().height(12.dp).neu(6.dp, elevation = 0.dp, inset = 3.dp)) {
            Box(Modifier.fillMaxWidth(ratio).height(12.dp).neu(6.dp, elevation = 0.dp, fill = if (ratio >= 1f) c.success else c.accent))
        }
        Text("${lux.roundToInt()} / $need lux", style = MaterialTheme.typography.labelLarge, color = c.textMuted, modifier = Modifier.padding(top = 8.dp))
    }
}

// --- Odd one out --------------------------------------------------------------------------------

private val ODD_PAIRS = listOf(
    "😀" to "😃", "😐" to "😑", "🙂" to "🙃", "😺" to "😸", "🕐" to "🕜",
    "🌑" to "🌚", "😊" to "😌", "🐶" to "🐕", "🍏" to "🍎", "🔷" to "🔹", "⬜" to "◻️", "🌕" to "🌝",
)

@Composable
fun OddMission(mission: Mission, cb: MissionCallbacks) {
    val c = Neu.colors
    val grid = when (mission.difficulty) { 1 -> 3; 2 -> 4; 3 -> 5; 4 -> 6; else -> 7 }
    var round by remember { mutableIntStateOf(0) }
    var attempt by remember { mutableIntStateOf(0) }
    val (same, odd) = remember(round, attempt) { ODD_PAIRS.random().let { if (Random.nextBoolean()) it else it.second to it.first } }
    val oddAt = remember(round, attempt) { Random.nextInt(grid * grid) }
    var wrong by remember(round, attempt) { mutableIntStateOf(-1) }
    LaunchedEffect(wrong) { if (wrong >= 0) { delay(700); attempt++ } }

    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Round ${round + 1} of ${mission.count}", style = MaterialTheme.typography.labelLarge, color = c.textMuted)
        Spacer(Modifier.height(8.dp))
        Text(if (wrong >= 0) "Not that one" else "Tap the one that's different", style = MaterialTheme.typography.titleLarge, color = if (wrong >= 0) c.danger else c.text)
        Spacer(Modifier.height(22.dp))
        Column(Modifier.fillMaxWidth().aspectRatio(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (r in 0 until grid) Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (col in 0 until grid) {
                    val i = r * grid + col
                    Box(
                        Modifier.weight(1f).fillMaxSize()
                            .neu(12.dp, elevation = 3.dp, fill = if (i == wrong) c.danger.copy(alpha = 0.3f) else null)
                            .pointerInput(round, attempt) {
                                detectTapGestures {
                                    cb.onInteraction()
                                    if (wrong >= 0) return@detectTapGestures
                                    if (i == oddAt) { if (round + 1 >= mission.count) cb.onComplete() else round++ } else wrong = i
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) { Text(if (i == oddAt) odd else same, fontSize = (150 / grid).sp) }
                }
            }
        }
    }
}

// --- Numbers in order ---------------------------------------------------------------------------

@Composable
fun OrderMission(mission: Mission, cb: MissionCallbacks) {
    val c = Neu.colors
    val scope = rememberCoroutineScope()
    val count = when (mission.difficulty) { 1 -> 6; 2 -> 9; 3 -> 12; 4 -> 16; else -> 20 }
    val cols = if (count <= 9) 3 else 4
    // The hardest level shuffles the board after every correct tap.
    val reshuffle = mission.difficulty >= 5
    var round by remember { mutableIntStateOf(0) }
    var next by remember(round) { mutableIntStateOf(1) }
    var layout by remember(round) { mutableStateOf((1..count).shuffled()) }
    val shake = remember { Animatable(0f) }

    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Round ${round + 1} of ${mission.count}", style = MaterialTheme.typography.labelLarge, color = c.textMuted)
        Spacer(Modifier.height(8.dp))
        Text("Tap $next", style = MaterialTheme.typography.titleLarge, color = c.text)
        Spacer(Modifier.height(22.dp))
        Column(Modifier.fillMaxWidth().offset { IntOffset(shake.value.roundToInt(), 0) }, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            layout.chunked(cols).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { n ->
                        val done = n < next
                        Box(
                            Modifier.weight(1f).aspectRatio(1.2f)
                                .neu(16.dp, elevation = if (done) 0.dp else 5.dp, inset = if (done) 4.dp else 0.dp)
                                .pointerInput(n, next) {
                                    detectTapGestures {
                                        cb.onInteraction()
                                        if (done) return@detectTapGestures
                                        if (n == next) {
                                            next++
                                            if (next > count) { if (round + 1 >= mission.count) cb.onComplete() else round++ }
                                            else if (reshuffle) layout = layout.shuffled()
                                        } else scope.launch { for (x in listOf(16f, -12f, 8f, -4f, 0f)) shake.animateTo(x) }
                                    }
                                },
                            contentAlignment = Alignment.Center,
                        ) { Text("$n", fontSize = 24.sp, fontWeight = FontWeight.Medium, color = if (done) c.textMuted.copy(alpha = 0.4f) else c.text) }
                    }
                    repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

// --- Hold steady --------------------------------------------------------------------------------

/** Allowed wobble in m/s² per difficulty. */
private val STEADY_TOLERANCE = floatArrayOf(1.2f, 0.8f, 0.5f, 0.3f, 0.18f)

@Composable
fun SteadyMission(mission: Mission, cb: MissionCallbacks) {
    val c = Neu.colors
    val context = LocalContext.current
    val tolerance = STEADY_TOLERANCE[mission.difficulty - 1]
    val total = mission.count * 1000
    var stillMs by remember { mutableIntStateOf(0) }
    var wobble by remember { mutableFloatStateOf(0f) }
    val latestCb by rememberUpdatedState(cb)

    DisposableEffect(Unit) {
        val sm = context.getSystemService(SensorManager::class.java)
        var smooth = SensorManager.GRAVITY_EARTH
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                val v = e.values
                val mag = sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2])
                smooth = 0.8f * smooth + 0.2f * mag
                wobble = 0.7f * wobble + 0.3f * kotlin.math.abs(mag - smooth)
            }
            override fun onAccuracyChanged(s: Sensor?, a: Int) {}
        }
        sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let { sm.registerListener(listener, it, SensorManager.SENSOR_DELAY_GAME) }
        onDispose { sm.unregisterListener(listener) }
    }
    LaunchedEffect(Unit) {
        while (stillMs < total) {
            delay(100)
            stillMs = if (wobble < tolerance) stillMs + 100 else 0
            latestCb.onInteraction()
        }
        latestCb.onComplete()
    }

    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        val steady = wobble < tolerance
        Text(if (steady) "Hold it…" else "Too shaky, start again", style = MaterialTheme.typography.titleLarge, color = if (steady) c.text else c.danger)
        Spacer(Modifier.height(28.dp))
        MissionCounter((stillMs / 1000), mission.count, "s")
        Spacer(Modifier.height(18.dp))
        Text("Hold your phone up in front of you", style = MaterialTheme.typography.bodyMedium, color = c.textMuted)
    }
}
