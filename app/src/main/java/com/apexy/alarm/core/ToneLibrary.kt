package com.apexy.alarm.core

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/** Alarm tones synthesized on-device, so the app ships no third-party audio. */
object ToneLibrary {
    data class Tone(val id: String, val name: String)

    val tones = listOf(
        Tone("radiant", "Radiant"),
        Tone("pulse", "Pulse"),
        Tone("rising", "Rising"),
        Tone("chime", "Chime"),
        Tone("siren", "Siren"),
        Tone("marimba", "Marimba"),
        Tone("klaxon", "Klaxon"),
    )

    private const val RATE = 44100

    fun file(context: Context, id: String) = File(File(context.filesDir, "tones"), "$id.wav")

    fun ensureGenerated(context: Context) {
        tones.forEach { tone ->
            val f = file(context, tone.id)
            if (!f.exists()) {
                f.parentFile?.mkdirs()
                writeWav(f, synth(tone.id))
            }
        }
    }

    private fun synth(id: String): FloatArray = when (id) {
        "radiant" -> sequence(listOf(523.25, 659.25, 783.99, 1046.5, 783.99, 659.25), 0.18, gap = 0.6, tail = 0.5) { f, t -> bell(f, t, 6.0) }
        "pulse" -> sequence(List(4) { 880.0 }, 0.12, gap = 0.7, spacing = 0.08) { f, t -> square(f, t) * 0.5 }
        "rising" -> sweep(400.0, 1400.0, 1.6, gap = 0.3)
        "chime" -> sequence(listOf(1318.5, 987.77, 1174.66, 783.99), 0.45, gap = 0.8, tail = 0.9) { f, t -> bell(f, t, 3.0) }
        "siren" -> sequence(List(6) { if (it % 2 == 0) 960.0 else 770.0 }, 0.35, gap = 0.0) { f, t -> sine(f, t) * 0.8 }
        "marimba" -> sequence(listOf(587.33, 739.99, 880.0, 739.99, 587.33, 880.0, 1174.66), 0.16, gap = 0.7, tail = 0.2) { f, t -> bell(f, t, 14.0) }
        "klaxon" -> sequence(List(3) { 440.0 }, 0.4, gap = 0.5, spacing = 0.15) { f, t -> (square(f, t) * 0.4 + square(f * 1.5, t) * 0.3) }
        else -> FloatArray(RATE)
    }

    private fun sine(f: Double, t: Double) = sin(2 * PI * f * t)
    private fun square(f: Double, t: Double) = if (sine(f, t) >= 0) 1.0 else -1.0
    private fun bell(f: Double, t: Double, decay: Double) =
        exp(-decay * t) * (sine(f, t) + 0.5 * sine(f * 2.0, t) * exp(-decay * t) + 0.25 * sine(f * 3.01, t) * exp(-2 * decay * t))

    /** Plays [freqs] one after another. [tail] lets decaying voices ring past the note length. */
    private fun sequence(
        freqs: List<Double>, noteLen: Double, gap: Double, spacing: Double = 0.0, tail: Double = 0.0,
        voice: (Double, Double) -> Double,
    ): FloatArray {
        val step = noteLen + spacing
        val total = ((freqs.size * step + gap + tail) * RATE).toInt()
        val out = FloatArray(total)
        val len = ((noteLen + tail) * RATE).toInt()
        freqs.forEachIndexed { i, f ->
            val start = (i * step * RATE).toInt()
            for (n in 0 until len) {
                val idx = start + n
                if (idx >= out.size) break
                val edge = minOf(1.0, n / 200.0, (len - n) / 400.0)
                out[idx] += (voice(f, n.toDouble() / RATE) * edge * 0.45).toFloat()
            }
        }
        return out
    }

    private fun sweep(from: Double, to: Double, seconds: Double, gap: Double): FloatArray {
        val out = FloatArray(((seconds + gap) * RATE).toInt())
        var phase = 0.0
        val n = (seconds * RATE).toInt()
        for (i in 0 until n) {
            val f = from + (to - from) * i / n
            phase += 2 * PI * f / RATE
            val edge = minOf(1.0, i / 300.0, (n - i) / 300.0)
            out[i] = (sin(phase) * 0.5 * edge).toFloat()
        }
        return out
    }

    private fun writeWav(file: File, samples: FloatArray) {
        val peak = samples.maxOf { kotlin.math.abs(it) }.coerceAtLeast(1e-6f)
        val gain = 0.9f / peak
        val data = ByteBuffer.allocate(samples.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        samples.forEach { data.putShort((it * gain * Short.MAX_VALUE).toInt().toShort()) }
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(36 + data.capacity()); put("WAVE".toByteArray())
            put("fmt ".toByteArray()); putInt(16); putShort(1); putShort(1)
            putInt(RATE); putInt(RATE * 2); putShort(2); putShort(16)
            put("data".toByteArray()); putInt(data.capacity())
        }
        FileOutputStream(file).use { it.write(header.array()); it.write(data.array()) }
    }
}
