package com.apexy.alarm.ui

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Crisp haptics straight from the vibrator. Compose's HapticFeedback falls back to "do nothing" on
 * many phones (HyperOS included), so wheel ticks and confirmations felt dead.
 */
class Haptics(context: Context) {
    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION") context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }
    private var lastTick = 0L

    /** One detent of a wheel. Rate-limited so a fast fling buzzes, not drones. */
    fun tick() {
        val now = SystemClock.uptimeMillis()
        if (now - lastTick < 28) return
        lastTick = now
        play(
            primitive = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) VibrationEffect.Composition.PRIMITIVE_TICK else null,
            scale = 0.9f,
            predefined = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) VibrationEffect.EFFECT_TICK else null,
            fallbackMs = 12,
        )
    }

    /** A firm single click for confirming. */
    fun confirm() = play(
        primitive = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) VibrationEffect.Composition.PRIMITIVE_CLICK else null,
        scale = 1f,
        predefined = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) VibrationEffect.EFFECT_CLICK else null,
        fallbackMs = 25,
    )

    /** A rising "ta-da" for finishing something that mattered. */
    fun success() {
        val v = vibrator ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && v.areAllPrimitivesSupported(
                VibrationEffect.Composition.PRIMITIVE_CLICK, VibrationEffect.Composition.PRIMITIVE_THUD,
            )
        ) {
            vibrate(
                VibrationEffect.startComposition()
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.5f)
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.75f, 70)
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, 1f, 90)
                    .compose(),
            )
        } else {
            val amp = v.hasAmplitudeControl()
            vibrate(VibrationEffect.createWaveform(longArrayOf(0, 25, 60, 30, 70, 60), if (amp) intArrayOf(0, 90, 0, 160, 0, 255) else intArrayOf(0, 255, 0, 255, 0, 255), -1))
        }
    }

    private fun play(primitive: Int?, scale: Float, predefined: Int?, fallbackMs: Long) {
        val v = vibrator ?: return
        val effect = when {
            primitive != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && v.areAllPrimitivesSupported(primitive) ->
                VibrationEffect.startComposition().addPrimitive(primitive, scale).compose()
            predefined != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> VibrationEffect.createPredefined(predefined)
            else -> VibrationEffect.createOneShot(fallbackMs, if (v.hasAmplitudeControl()) 120 else VibrationEffect.DEFAULT_AMPLITUDE)
        }
        vibrate(effect)
    }

    private fun vibrate(effect: VibrationEffect) {
        val v = vibrator ?: return
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                v.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_TOUCH))
            } else {
                v.vibrate(effect)
            }
        }
    }
}

@Composable
fun rememberHaptics(): Haptics {
    val context = LocalContext.current
    return remember { Haptics(context.applicationContext) }
}
