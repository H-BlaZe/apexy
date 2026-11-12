package com.apexy.alarm.missions

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.apexy.alarm.ui.neu.MissionCounter
import com.apexy.alarm.ui.neu.Neu
import kotlin.math.sqrt

/** Turns raw sensor streams into discrete reps. Pure logic so it can be reasoned about apart from Android. */
object MotionDetectors {
    /** Counts shakes: spikes of acceleration (gravity removed) above [threshold], at most one per 180 ms. */
    class Shake(private val threshold: Float) {
        private val gravity = FloatArray(3)
        private var primed = false
        private var last = 0L

        fun onAccel(v: FloatArray, t: Long): Boolean {
            if (!primed) { v.copyInto(gravity); primed = true; return false }
            for (i in 0..2) gravity[i] = 0.9f * gravity[i] + 0.1f * v[i]
            val x = v[0] - gravity[0]; val y = v[1] - gravity[1]; val z = v[2] - gravity[2]
            val mag = sqrt(x * x + y * y + z * z)
            if (mag > threshold && t - last > 180) { last = t; return true }
            return false
        }
    }

    /** Fallback pedometer for phones without a step sensor (or without the permission). */
    class Steps {
        private var smooth = SensorManager.GRAVITY_EARTH
        private var armed = true
        private var last = 0L

        fun onAccel(v: FloatArray, t: Long): Boolean {
            val mag = sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2])
            smooth = 0.75f * smooth + 0.25f * mag
            val delta = smooth - SensorManager.GRAVITY_EARTH
            if (armed && delta > 1.6f && t - last > 280) { armed = false; last = t; return true }
            if (delta < 0.4f) armed = true
            return false
        }
    }

    /**
     * Counts squats from vertical acceleration (positive = up). A squat shows as
     * down (negative) → bottom (positive) → top (negative) within 0.6–5 s.
     */
    class Squat(private val threshold: Float) {
        private var smooth = 0f
        private var state = 0
        private var start = 0L

        /** [vertical] is linear acceleration along gravity, positive = upwards, in m/s². */
        fun onVertical(vertical: Float, t: Long): Boolean {
            smooth = 0.7f * smooth + 0.3f * vertical
            if (state != 0 && t - start > 5000) state = 0
            when (state) {
                0 -> if (smooth < -threshold) { state = 1; start = t }
                1 -> if (smooth > threshold) state = 2
                2 -> if (smooth < -threshold * 0.7f) {
                    // Braking at the top blends into the next descent, so it also starts the next rep.
                    val valid = t - start in 600..5000
                    state = 1
                    start = t
                    return valid
                }
            }
            return false
        }
    }

    /** Splits raw accelerometer readings into gravity and motion when no fused sensors exist. */
    class GravitySplitter {
        val gravity = FloatArray(3)
        private var primed = false

        fun vertical(v: FloatArray): Float {
            if (!primed) { v.copyInto(gravity); primed = true; return 0f }
            for (i in 0..2) gravity[i] = 0.985f * gravity[i] + 0.015f * v[i]
            return project(floatArrayOf(v[0] - gravity[0], v[1] - gravity[1], v[2] - gravity[2]), gravity)
        }
    }

    fun project(linear: FloatArray, gravity: FloatArray): Float {
        val g = sqrt(gravity[0] * gravity[0] + gravity[1] * gravity[1] + gravity[2] * gravity[2]).coerceAtLeast(0.1f)
        return (linear[0] * gravity[0] + linear[1] * gravity[1] + linear[2] * gravity[2]) / g
    }
}

private val SHAKE_THRESHOLDS = floatArrayOf(8f, 10.5f, 13f, 15.5f, 18f)
private val SQUAT_THRESHOLDS = floatArrayOf(1.4f, 1.8f, 2.2f, 2.7f, 3.2f)

@Composable
private fun SensorEffect(sensorType: Int, onEvent: (SensorEvent) -> Unit): Boolean {
    val context = LocalContext.current
    val manager = remember { context.getSystemService(Context.SENSOR_SERVICE) as SensorManager }
    val sensor = remember { manager.getDefaultSensor(sensorType) }
    val latest by rememberUpdatedState(onEvent)
    DisposableEffect(sensor) {
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) = latest(event)
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        sensor?.let { manager.registerListener(listener, it, SensorManager.SENSOR_DELAY_GAME) }
        onDispose { manager.unregisterListener(listener) }
    }
    return sensor != null
}

@Composable
private fun RepMission(mission: Mission, cb: MissionCallbacks, unit: String, hint: String, available: Boolean, count: Int) {
    val c = Neu.colors
    LaunchedEffect(count) { if (count >= mission.count) cb.onComplete() }
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(24.dp))
        MissionCounter(count, mission.count, unit)
        Spacer(Modifier.height(32.dp))
        Text(
            if (available) hint else "No motion sensor. Use the escape below.",
            style = MaterialTheme.typography.titleMedium, color = if (available) c.textMuted else c.danger,
            textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 12.dp),
        )
    }
}

@Composable
fun ShakeMission(mission: Mission, cb: MissionCallbacks) {
    val haptics = LocalHapticFeedback.current
    val detector = remember { MotionDetectors.Shake(SHAKE_THRESHOLDS[mission.difficulty - 1]) }
    var count by remember { mutableIntStateOf(0) }
    val available = SensorEffect(Sensor.TYPE_ACCELEROMETER) { e ->
        if (detector.onAccel(e.values, e.timestamp / 1_000_000)) {
            count++
            cb.onInteraction()
            if (count % 5 == 0) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }
    RepMission(mission, cb, "shakes", "Shake your phone hard!", available, count)
}

@Composable
fun WalkMission(mission: Mission, cb: MissionCallbacks) {
    val context = LocalContext.current
    val permitted = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED
    val hasStepSensor = remember {
        (context.getSystemService(Context.SENSOR_SERVICE) as SensorManager).getDefaultSensor(Sensor.TYPE_STEP_DETECTOR) != null
    }
    var count by remember { mutableIntStateOf(0) }
    val available = if (permitted && hasStepSensor) {
        SensorEffect(Sensor.TYPE_STEP_DETECTOR) { count++; cb.onInteraction() }
    } else {
        val detector = remember { MotionDetectors.Steps() }
        SensorEffect(Sensor.TYPE_ACCELEROMETER) { e ->
            if (detector.onAccel(e.values, e.timestamp / 1_000_000)) { count++; cb.onInteraction() }
        }
    }
    RepMission(mission, cb, "steps", "Get up and walk — carry the phone with you.", available, count)
}

@Composable
fun SquatMission(mission: Mission, cb: MissionCallbacks) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val detector = remember { MotionDetectors.Squat(SQUAT_THRESHOLDS[mission.difficulty - 1]) }
    var count by remember { mutableIntStateOf(0) }
    val fused = remember {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        sm.getDefaultSensor(Sensor.TYPE_GRAVITY) != null && sm.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION) != null
    }
    fun onVertical(vertical: Float, t: Long) {
        if (detector.onVertical(vertical, t)) {
            count++
            cb.onInteraction()
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }
    val available = if (fused) {
        // Sensor fusion gives a far steadier gravity vector than filtering by hand.
        val gravity = remember { floatArrayOf(0f, 0f, SensorManager.GRAVITY_EARTH) }
        SensorEffect(Sensor.TYPE_GRAVITY) { e -> e.values.copyInto(gravity, endIndex = 3) }
        SensorEffect(Sensor.TYPE_LINEAR_ACCELERATION) { e ->
            onVertical(MotionDetectors.project(e.values, gravity), e.timestamp / 1_000_000)
        }
    } else {
        val splitter = remember { MotionDetectors.GravitySplitter() }
        SensorEffect(Sensor.TYPE_ACCELEROMETER) { e -> onVertical(splitter.vertical(e.values), e.timestamp / 1_000_000) }
    }
    RepMission(mission, cb, "squats", "Phone to chest, squat down and up", available, count)
}
