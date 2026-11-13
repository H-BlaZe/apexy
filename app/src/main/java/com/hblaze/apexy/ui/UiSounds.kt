package com.hblaze.apexy.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.SystemClock
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/**
 * Small interface sounds, synthesized once into the cache so the app ships no audio files.
 * SoundPool keeps them decoded in memory, so a wheel can click on every number.
 */
object UiSounds {
    private const val RATE = 44_100

    enum class Kind { TICK, TAP, ON, OFF, CONFIRM }

    @Volatile var enabled = true
    private var pool: SoundPool? = null
    private val ids = IntArray(Kind.entries.size)
    private val loaded = BooleanArray(Kind.entries.size)
    private var lastTick = 0L

    fun init(context: Context) {
        if (pool != null) return
        val dir = File(context.cacheDir, "ui_sounds").apply { mkdirs() }
        val p = SoundPool.Builder()
            .setMaxStreams(4)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            .build()
        p.setOnLoadCompleteListener { _, sampleId, status ->
            val i = ids.indexOf(sampleId)
            if (i >= 0 && status == 0) loaded[i] = true
        }
        Kind.entries.forEach { k ->
            val f = File(dir, "v1_${k.name.lowercase()}.wav")
            if (!f.exists()) f.writeBytes(wav(synth(k)))
            ids[k.ordinal] = p.load(f.path, 1)
        }
        pool = p
    }

    fun play(kind: Kind) {
        if (!enabled || !loaded[kind.ordinal]) return
        if (kind == Kind.TICK) {
            // A fast fling should rattle, not drone.
            val now = SystemClock.uptimeMillis()
            if (now - lastTick < 30) return
            lastTick = now
        }
        val vol = when (kind) { Kind.TICK -> 0.55f; Kind.TAP -> 0.5f; else -> 0.6f }
        pool?.play(ids[kind.ordinal], vol, vol, 1, 0, 1f)
    }

    private fun synth(kind: Kind): FloatArray = when (kind) {
        // A short ratchet click, like a detent on a dial.
        Kind.TICK -> render(0.012) { t, _ ->
            val env = exp(-t / 0.0014)
            (sin(2 * PI * 2900 * t) * 0.7 + sin(2 * PI * 5200 * t) * 0.3) * env
        }
        // A soft, rounded pop with a slight downward pitch.
        Kind.TAP -> render(0.05) { t, _ ->
            val f = 520 + 380 * exp(-t / 0.01)
            val env = (t / 0.0015).coerceAtMost(1.0) * exp(-t / 0.011)
            sin(2 * PI * f * t) * env
        }
        Kind.ON -> twoNotes(660.0, 990.0)
        Kind.OFF -> twoNotes(880.0, 587.0)
        // A bright two-note chime for finishing something.
        Kind.CONFIRM -> render(0.32) { t, _ ->
            val a = bell(784.0, t, 0.09)
            val b = if (t > 0.07) bell(1175.0, t - 0.07, 0.14) else 0.0
            (a + b) * 0.6
        }
    }

    private fun twoNotes(f1: Double, f2: Double) = render(0.11) { t, _ ->
        if (t < 0.045) bell(f1, t, 0.018) else bell(f2, t - 0.045, 0.025)
    }

    private fun bell(f: Double, t: Double, decay: Double): Double {
        val env = (t / 0.002).coerceAtMost(1.0) * exp(-t / decay)
        return (sin(2 * PI * f * t) + 0.25 * sin(2 * PI * f * 2.01 * t)) * env * 0.8
    }

    private fun render(seconds: Double, fn: (Double, Random) -> Double): FloatArray {
        val rnd = Random(7)
        val n = (seconds * RATE).toInt()
        return FloatArray(n) { i ->
            val fadeOut = ((n - i) / (RATE * 0.003)).coerceAtMost(1.0)
            (fn(i.toDouble() / RATE, rnd) * fadeOut).toFloat().coerceIn(-1f, 1f)
        }
    }

    private fun wav(samples: FloatArray): ByteArray {
        val data = samples.size * 2
        val b = ByteBuffer.allocate(44 + data).order(ByteOrder.LITTLE_ENDIAN)
        b.put("RIFF".toByteArray()).putInt(36 + data).put("WAVE".toByteArray())
        b.put("fmt ".toByteArray()).putInt(16).putShort(1).putShort(1).putInt(RATE).putInt(RATE * 2).putShort(2).putShort(16)
        b.put("data".toByteArray()).putInt(data)
        samples.forEach { b.putShort((it * 32767).toInt().toShort()) }
        return b.array()
    }
}
