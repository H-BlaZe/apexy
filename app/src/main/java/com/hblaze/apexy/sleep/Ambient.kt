package com.hblaze.apexy.sleep

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/** Endless relaxing sounds, synthesized live so they never loop audibly and need no audio files. */
object Ambient {
    const val RATE = 44_100

    data class Sound(val id: String, val name: String)

    val sounds = listOf(
        Sound("rain", "Rain"),
        Sound("ocean", "Ocean"),
        Sound("stream", "Stream"),
        Sound("fire", "Campfire"),
        Sound("fan", "Fan"),
        Sound("white", "White noise"),
        Sound("pink", "Pink noise"),
        Sound("brown", "Brown noise"),
    )

    fun parseMix(mix: String): Map<String, Float> = mix.split(',').mapNotNull { part ->
        val (id, v) = part.split(':').takeIf { it.size == 2 } ?: return@mapNotNull null
        v.toFloatOrNull()?.let { id to it.coerceIn(0f, 1f) }
    }.filter { (id, v) -> v > 0f && sounds.any { it.id == id } }.toMap()

    fun formatMix(mix: Map<String, Float>) = mix.filterValues { it > 0f }.entries.joinToString(",") { "${it.key}:${"%.2f".format(java.util.Locale.US, it.value)}" }

    /** One voice: fills [out] with samples in -1..1. Not thread-safe; owned by the mixer thread. */
    abstract class Voice {
        abstract fun next(): Float
    }

    private class White(private val rnd: Random) : Voice() {
        override fun next() = rnd.nextFloat() * 2f - 1f
    }

    /** Paul Kellet's economical pink filter. */
    private class Pink(private val rnd: Random) : Voice() {
        private var b0 = 0f; private var b1 = 0f; private var b2 = 0f
        override fun next(): Float {
            val w = rnd.nextFloat() * 2f - 1f
            b0 = 0.99765f * b0 + w * 0.0990460f
            b1 = 0.96300f * b1 + w * 0.2965164f
            b2 = 0.57000f * b2 + w * 1.0526913f
            return (b0 + b1 + b2 + w * 0.1848f) * 0.22f
        }
    }

    private class Brown(private val rnd: Random) : Voice() {
        private var last = 0f
        override fun next(): Float {
            last = (last + (rnd.nextFloat() * 2f - 1f) * 0.02f) / 1.002f
            return last * 3.2f
        }
    }

    private class OnePoleLow(private val a: Float) {
        private var y = 0f
        fun run(x: Float): Float { y += a * (x - y); return y }
    }

    private class Rain(private val rnd: Random) : Voice() {
        private val hiss = Pink(rnd)
        private val hp = OnePoleLow(0.08f)
        private var drop = 0f
        override fun next(): Float {
            val p = hiss.next()
            val bright = p - hp.run(p)
            if (rnd.nextFloat() < 0.0009f) drop = 0.5f + rnd.nextFloat() * 0.5f
            drop *= 0.992f
            return bright * 1.6f + (rnd.nextFloat() * 2f - 1f) * drop * 0.35f
        }
    }

    /** Brown surf swelling with irregular wave periods. */
    private class Ocean(private val rnd: Random) : Voice() {
        private val body = Brown(rnd)
        private val foam = Pink(rnd)
        private var phase = 0.0
        private var period = 9.0
        override fun next(): Float {
            phase += 1.0 / (RATE * period)
            if (phase >= 1.0) { phase -= 1.0; period = 7.0 + rnd.nextDouble() * 6.0 }
            val swell = (0.5 - 0.5 * kotlin.math.cos(2 * PI * phase)).let { it * it }.toFloat()
            return body.next() * (0.25f + 0.75f * swell) + foam.next() * 0.5f * swell
        }
    }

    private class Stream(private val rnd: Random) : Voice() {
        private val src = Pink(rnd)
        private val lp = OnePoleLow(0.25f)
        private var bubble = 0f
        private var mod = 1f
        override fun next(): Float {
            if (rnd.nextFloat() < 0.0005f) mod = 0.6f + rnd.nextFloat() * 0.8f
            bubble += (mod - bubble) * 0.0008f
            return lp.run(src.next()) * 1.7f * bubble
        }
    }

    private class Fire(private val rnd: Random) : Voice() {
        private val rumble = Brown(rnd)
        private var crackle = 0f
        private var t = 0
        override fun next(): Float {
            t++
            if (rnd.nextFloat() < 0.0004f) crackle = 0.6f + rnd.nextFloat() * 0.4f
            crackle *= 0.96f
            val pop = (rnd.nextFloat() * 2f - 1f) * crackle
            val flicker = 0.8f + 0.2f * sin(2 * PI * t / (RATE * 3.1)).toFloat()
            return rumble.next() * 0.55f * flicker + pop * 0.6f
        }
    }

    private class Fan(private val rnd: Random) : Voice() {
        private val src = Pink(rnd)
        private val lp = OnePoleLow(0.06f)
        private var t = 0
        override fun next(): Float {
            t++
            val hum = sin(2 * PI * 58.0 * t / RATE).toFloat() * 0.04f
            return lp.run(src.next()) * 3.2f + hum
        }
    }

    fun voice(id: String, rnd: Random = Random.Default): Voice = when (id) {
        "rain" -> Rain(rnd)
        "ocean" -> Ocean(rnd)
        "stream" -> Stream(rnd)
        "fire" -> Fire(rnd)
        "fan" -> Fan(rnd)
        "white" -> object : Voice() { val w = White(rnd); override fun next() = w.next() * 0.35f }
        "pink" -> Pink(rnd)
        else -> Brown(rnd)
    }

    /** Plays a live mix on a background thread; volumes and the master fade can change at any time. */
    class Player {
        @Volatile private var mix: Map<String, Float> = emptyMap()
        @Volatile private var master = 1f
        /** How long a change of [master] takes, in samples. */
        @Volatile private var fadeSamples = RATE * 2
        /** The thread currently allowed to play; an old one still finishing its last buffer just exits. */
        @Volatile private var thread: Thread? = null

        val isPlaying get() = thread != null

        fun setMix(newMix: Map<String, Float>) { mix = newMix }

        /** Glide the overall level to [level] (0..1) over [ms]. */
        fun fadeTo(level: Float, ms: Int) {
            fadeSamples = (RATE * ms / 1000).coerceAtLeast(1)
            master = level
        }

        fun start(initial: Map<String, Float>) {
            mix = initial
            fadeTo(1f, 1500)
            if (thread != null) return
            thread = Thread({ loop() }, "apexy-ambient").apply { start() }
        }

        /** Stops at once without waiting for the audio thread, so it never stalls the caller. */
        fun stop() {
            thread = null
        }

        private fun loop() {
            val me = Thread.currentThread()
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_AUDIO)
            val minBuf = AudioTrack.getMinBufferSize(RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_FLOAT)
            val track = AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                .setAudioFormat(AudioFormat.Builder().setSampleRate(RATE).setEncoding(AudioFormat.ENCODING_PCM_FLOAT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                .setBufferSizeInBytes(maxOf(minBuf, 4096 * 4))
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
            // Plain arrays indexed like [sounds]: no map lookups or boxing in the per-sample loop.
            val n = sounds.size
            val voices = arrayOfNulls<Voice>(n)
            val gains = FloatArray(n)
            val targets = FloatArray(n)
            val buf = FloatArray(1024)
            var level = 0f
            track.play()
            try {
                while (thread === me) {
                    val target = mix
                    for (k in 0 until n) {
                        targets[k] = target[sounds[k].id] ?: 0f
                        if (targets[k] > 0f && voices[k] == null) voices[k] = voice(sounds[k].id)
                    }
                    val goal = master
                    val step = 1f / fadeSamples
                    for (i in buf.indices) {
                        var s = 0f
                        for (k in 0 until n) {
                            val v = voices[k] ?: continue
                            // Glide each voice's gain (~50 ms) so taps and slider moves never click.
                            val g = gains[k] + (targets[k] - gains[k]) * 0.0005f
                            gains[k] = g
                            if (g > 0.0005f) s += v.next() * g
                        }
                        level = if (level < goal) minOf(goal, level + step) else maxOf(goal, level - step)
                        buf[i] = softClip(s) * level
                    }
                    for (k in 0 until n) if (targets[k] == 0f && gains[k] <= 0.0005f) { voices[k] = null; gains[k] = 0f }
                    track.write(buf, 0, buf.size, AudioTrack.WRITE_BLOCKING)
                }
            } finally {
                runCatching { track.pause(); track.flush(); track.stop() }
                track.release()
            }
        }

        private fun softClip(x: Float) = (x / (1f + kotlin.math.abs(x))) * 1.6f
    }

}
