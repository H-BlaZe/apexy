package com.hblaze.apexy.missions

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.Calculate
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.ViewWeek
import androidx.compose.material.icons.rounded.Nfc
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material.icons.rounded.EmojiEmotions
import androidx.compose.material.icons.rounded.FormatListNumbered
import androidx.compose.material.icons.rounded.PanTool
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.hblaze.apexy.ui.neu.Neu
import com.hblaze.apexy.ui.neu.NeuIconButton
import com.hblaze.apexy.ui.neu.neu
import kotlinx.coroutines.delay

val MissionType.icon: ImageVector
    get() = when (this) {
        MissionType.MATH -> Icons.Rounded.Calculate
        MissionType.MEMORY -> Icons.Rounded.GridView
        MissionType.TYPING -> Icons.Rounded.Keyboard
        MissionType.SHAKE -> Icons.Rounded.ScreenRotation
        MissionType.WALK -> Icons.AutoMirrored.Rounded.DirectionsWalk
        MissionType.SQUAT -> Icons.Rounded.FitnessCenter
        MissionType.QR -> Icons.Rounded.QrCode2
        MissionType.BARCODE -> Icons.Rounded.ViewWeek
        MissionType.PHOTO -> Icons.Rounded.PhotoCamera
        MissionType.NFC -> Icons.Rounded.Nfc
        MissionType.LIGHT -> Icons.Rounded.WbSunny
        MissionType.ODD -> Icons.Rounded.EmojiEmotions
        MissionType.ORDER -> Icons.Rounded.FormatListNumbered
        MissionType.STEADY -> Icons.Rounded.PanTool
    }

/** The long typing challenge that replaces a mission the user physically can't finish (lost QR code, no space to walk…). */
private val EMERGENCY = Mission(MissionType.TYPING, difficulty = 5, count = 3)
private const val ESCAPE_AFTER_MS = 20_000L

/**
 * Runs [missions] one after another. Calls [onTimeout] after [idleTimeoutMs] without any
 * interaction (the alarm then gets loud again), and [onClose] when the user backs out of a preview.
 */
@Composable
fun MissionHost(
    missions: List<Mission>,
    onAllDone: (com.hblaze.apexy.core.MissionStats) -> Unit,
    onTimeout: () -> Unit,
    modifier: Modifier = Modifier,
    idleTimeoutMs: Long = 30_000,
    onClose: (() -> Unit)? = null,
) {
    val c = Neu.colors
    val context = LocalContext.current
    val cameraOk = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    // A mission that can't run (camera denied, nothing registered) falls back to the emergency challenge.
    val runnable = remember(missions) {
        missions.map { if (!it.isReady || (it.type.needsCamera && !cameraOk) || !it.type.availableOn(context)) EMERGENCY else it }
    }

    var index by remember { mutableIntStateOf(0) }
    var escaped by remember { mutableStateOf(false) }
    var escapes by remember { mutableIntStateOf(0) }
    val hostStarted = remember { System.currentTimeMillis() }
    var attemptKey by remember { mutableIntStateOf(0) }
    val current = if (escaped) EMERGENCY else runnable[index]
    var lastTouch by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var startedAt by remember(index) { mutableLongStateOf(System.currentTimeMillis()) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val latestTimeout by rememberUpdatedState(onTimeout)

    LaunchedEffect(Unit) {
        while (true) {
            delay(250)
            now = System.currentTimeMillis()
            if (now - lastTouch > idleTimeoutMs) {
                lastTouch = now
                latestTimeout()
            }
        }
    }

    val callbacks = remember(index, escaped, attemptKey) {
        MissionCallbacks(
            onInteraction = { lastTouch = System.currentTimeMillis() },
            onComplete = {
                lastTouch = System.currentTimeMillis()
                escaped = false
                if (index + 1 < runnable.size) index++
                else onAllDone(com.hblaze.apexy.core.MissionStats(runnable.size, System.currentTimeMillis() - hostStarted, escapes))
            },
        )
    }

    Column(modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(52.dp).neu(26.dp, elevation = 0.dp, inset = 4.dp), contentAlignment = Alignment.Center) {
                Icon(current.type.icon, null, tint = c.accent)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (escaped) "Emergency escape" else current.type.title,
                    style = MaterialTheme.typography.titleLarge, color = c.text,
                )
                Text(
                    if (runnable.size > 1) "Mission ${index + 1} of ${runnable.size}" else current.type.blurb,
                    style = MaterialTheme.typography.bodyMedium, color = c.textMuted,
                )
            }
            if (onClose != null) NeuIconButton(Icons.Rounded.Close, "Close", onClose, size = 44.dp)
        }
        Spacer(Modifier.height(10.dp))
        // Idle bar: drains while the user does nothing; empty means the alarm gets loud again.
        val remaining = (1f - (now - lastTouch).toFloat() / idleTimeoutMs).coerceIn(0f, 1f)
        Box(Modifier.fillMaxWidth().height(8.dp).neu(4.dp, elevation = 0.dp, inset = 2.dp)) {
            Box(Modifier.fillMaxWidth(remaining).height(8.dp).neu(4.dp, elevation = 0.dp, fill = if (remaining < 0.3f) c.danger else c.accent))
        }
        if (runnable.size > 1) {
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                runnable.indices.forEach { i ->
                    Box(Modifier.size(width = 26.dp, height = 6.dp).neu(3.dp, elevation = 0.dp, fill = if (i <= index) c.accent else c.textMuted.copy(alpha = 0.25f)))
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        Box(Modifier.weight(1f).fillMaxWidth()) {
            key(index, escaped, attemptKey) {
                when (current.type) {
                    MissionType.MATH -> MathMission(current, callbacks)
                    MissionType.MEMORY -> MemoryMission(current, callbacks)
                    MissionType.TYPING -> TypingMission(current, callbacks)
                    MissionType.SHAKE -> ShakeMission(current, callbacks)
                    MissionType.WALK -> WalkMission(current, callbacks)
                    MissionType.SQUAT -> SquatMission(current, callbacks)
                    MissionType.QR, MissionType.BARCODE -> ScanMission(current, callbacks)
                    MissionType.PHOTO -> PhotoMission(current, callbacks)
                    MissionType.NFC -> NfcMission(current, callbacks)
                    MissionType.LIGHT -> LightMission(current, callbacks)
                    MissionType.ODD -> OddMission(current, callbacks)
                    MissionType.ORDER -> OrderMission(current, callbacks)
                    MissionType.STEADY -> SteadyMission(current, callbacks)
                }
            }
        }
        if (!escaped && current.type.isPhysical && now - startedAt > ESCAPE_AFTER_MS) {
            TextButton(
                onClick = { escaped = true; escapes++; attemptKey++; lastTouch = System.currentTimeMillis() },
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) {
                Text("Can't do this one? Emergency escape", color = c.textMuted, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
