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
import androidx.compose.material.icons.rounded.East
import androidx.compose.material.icons.rounded.Nfc
import androidx.compose.material.icons.rounded.North
import androidx.compose.material.icons.rounded.NorthEast
import androidx.compose.material.icons.rounded.NorthWest
import androidx.compose.material.icons.rounded.South
import androidx.compose.material.icons.rounded.SouthEast
import androidx.compose.material.icons.rounded.SouthWest
import androidx.compose.material.icons.rounded.West
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
import com.apexy.alarm.ui.neu.Neu
import com.apexy.alarm.ui.neu.NeuProgressRing
import com.apexy.alarm.ui.neu.neu
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.random.Random

/** Whether this phone has what the mission needs right now; if not, the emergency typing challenge stands in. */
fun MissionType.availableOn(context: Context): Boolean = when (this) {
    MissionType.NFC -> NfcAdapter.getDefaultAdapter(context)?.isEnabled == true
    MissionType.LIGHT -> context.getSystemService(SensorManager::class.java)?.getDefaultSensor(Sensor.TYPE_LIGHT) != null
    MissionType.STEADY -> context.getSystemService(SensorManager::class.java)?.let {
        it.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null && it.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) != null
    } == true
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
/** How far off the target compass heading and tilt (degrees) still counts, per difficulty. */
private val HEADING_TOLERANCE = floatArrayOf(30f, 25f, 20f, 15f, 12f)
private val TILT_TOLERANCE = floatArrayOf(14f, 12f, 10f, 8f, 6f)
private val COMPASS = listOf("north" to 0f, "east" to 90f, "south" to 180f, "west" to 270f)
/** Degrees the top of the phone leans away from you; 0 is upright. Never flat, so it can't sit on the bed. */
private val TILTS = listOf(0f, 15f, 30f, 45f, 60f)

@Composable
fun SteadyMission(mission: Mission, cb: MissionCallbacks) {
    val c = Neu.colors
    val context = LocalContext.current
    val tolerance = STEADY_TOLERANCE[mission.difficulty - 1]
    val headingTolerance = HEADING_TOLERANCE[mission.difficulty - 1]
    val tiltTolerance = TILT_TOLERANCE[mission.difficulty - 1]
    // A fresh pose every time, so a phone propped up once doesn't pass the next morning.
    val (direction, targetHeading) = remember { COMPASS.random() }
    val targetTilt = remember { TILTS.random() }
    val total = mission.count * 1000
    var stillMs by remember { mutableIntStateOf(0) }
    var wobble by remember { mutableFloatStateOf(0f) }
    var heading by remember { mutableFloatStateOf(Float.NaN) }
    var tilt by remember { mutableFloatStateOf(Float.NaN) }
    val latestCb by rememberUpdatedState(cb)

    DisposableEffect(Unit) {
        val sm = context.getSystemService(SensorManager::class.java)
        var smooth = SensorManager.GRAVITY_EARTH
        val r = FloatArray(9)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                val v = e.values
                if (e.sensor.type == Sensor.TYPE_ROTATION_VECTOR) {
                    // Where the back camera points, in world coordinates (x east, y north, z up).
                    SensorManager.getRotationMatrixFromVector(r, v)
                    val east = -r[2]; val north = -r[5]; val up = -r[8]
                    heading = (Math.toDegrees(atan2(east, north).toDouble()).toFloat() + 360f) % 360f
                    tilt = -Math.toDegrees(asin(up.coerceIn(-1f, 1f).toDouble())).toFloat()
                    return
                }
                val mag = sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2])
                smooth = 0.8f * smooth + 0.2f * mag
                wobble = 0.7f * wobble + 0.3f * abs(mag - smooth)
            }
            override fun onAccuracyChanged(s: Sensor?, a: Int) {}
        }
        sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let { sm.registerListener(listener, it, SensorManager.SENSOR_DELAY_GAME) }
        sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)?.let { sm.registerListener(listener, it, SensorManager.SENSOR_DELAY_GAME) }
        onDispose { sm.unregisterListener(listener) }
    }
    // Positive: turn clockwise / lean the top further away.
    val turnBy = if (heading.isNaN()) Float.NaN else ((targetHeading - heading + 540f) % 360f) - 180f
    val leanBy = if (tilt.isNaN()) Float.NaN else targetTilt - tilt
    // -1, 0 or 1 on each axis; 0 means close enough. NaN until the sensor reports, which reads as off.
    val turn = if (abs(turnBy) <= headingTolerance) 0 else if (turnBy > 0) 1 else -1
    val lean = if (abs(leanBy) <= tiltTolerance) 0 else if (leanBy > 0) 1 else -1
    val aimed = !heading.isNaN() && turn == 0 && lean == 0
    val steady = wobble < tolerance
    val aimedNow by rememberUpdatedState(aimed)

    LaunchedEffect(Unit) {
        while (stillMs < total) {
            delay(100)
            // Shaking starts over; drifting off target only pauses, so a nudge back on target carries on.
            stillMs = when {
                wobble >= tolerance -> 0
                aimedNow -> stillMs + 100
                else -> stillMs
            }
            latestCb.onInteraction()
        }
        latestCb.onComplete()
    }

    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "Face ${direction.uppercase()}, ${if (targetTilt == 0f) "phone upright" else "tilted back ${targetTilt.roundToInt()}°"}",
            style = MaterialTheme.typography.titleMedium, color = c.textMuted, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        // Camera aim: leaning the top away points the camera down.
        val status = when {
            heading.isNaN() -> "Finding direction…"
            !aimed -> listOfNotNull(
                when (turn) { 1 -> "right"; -1 -> "left"; else -> null },
                when (lean) { 1 -> "down"; -1 -> "up"; else -> null },
            ).joinToString(" and ", prefix = "Aim ")
            !steady -> "Too shaky, start again"
            else -> "Hold it…"
        }
        Text(status, style = MaterialTheme.typography.titleLarge, color = if (aimed && steady) c.text else c.danger, textAlign = TextAlign.Center)
        Spacer(Modifier.height(28.dp))
        NeuProgressRing(stillMs.toFloat() / total) {
            if (aimed || heading.isNaN()) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${(mission.count - stillMs / 1000).coerceAtLeast(0)}", fontSize = 64.sp, fontWeight = FontWeight.Light, color = c.text)
                    Text("s left", style = MaterialTheme.typography.labelLarge, color = c.textMuted)
                }
            } else {
                val arrow = when (turn to lean) {
                    0 to -1 -> Icons.Rounded.North
                    1 to -1 -> Icons.Rounded.NorthEast
                    1 to 0 -> Icons.Rounded.East
                    1 to 1 -> Icons.Rounded.SouthEast
                    0 to 1 -> Icons.Rounded.South
                    -1 to 1 -> Icons.Rounded.SouthWest
                    -1 to 0 -> Icons.Rounded.West
                    else -> Icons.Rounded.NorthWest
                }
                Icon(arrow, status, Modifier.size(120.dp), tint = c.accent)
            }
        }
        Spacer(Modifier.height(18.dp))
        Text("Hold your phone up in front of you, screen facing you, and follow the arrow", style = MaterialTheme.typography.bodyMedium, color = c.textMuted, textAlign = TextAlign.Center)
    }
}
