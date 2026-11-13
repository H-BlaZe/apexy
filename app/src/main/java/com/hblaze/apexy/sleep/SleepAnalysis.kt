package com.hblaze.apexy.sleep

import kotlin.math.roundToInt

enum class Stage { AWAKE, LIGHT, DEEP }

data class MinuteSample(val activity: Float, val noiseDb: Float)

data class NightReport(
    val minutesInBed: Int,
    val minutesAsleep: Int,
    /** Minutes from starting tracking to falling asleep. */
    val sleepOnsetMinutes: Int,
    val awakeMinutes: Int,
    val lightMinutes: Int,
    val deepMinutes: Int,
    val avgNoiseDb: Float,
    val stages: List<Stage>,
    /** 0–100: duration against the goal, efficiency, and deep share. */
    val score: Int,
)

/**
 * Estimates sleep from phone-on-the-mattress motion (actigraphy). It's an estimate, like every
 * phone-based tracker: thresholds adapt to each night's own baseline so mattress and phone placement
 * matter less than absolute values.
 */
object SleepAnalysis {
    /** Minutes of stillness that count as having fallen asleep. */
    private const val ONSET_RUN = 10
    /** Minutes of deeper stillness needed to call a stretch deep sleep. */
    private const val DEEP_RUN = 20
    /** Absolute floor so a dead-still night isn't all "awake" from tiny noise. */
    private const val MIN_AWAKE_ACTIVITY = 0.02f
    /** Absolute ceiling: movement this strong is wakefulness however restless the whole night was. */
    private const val MAX_AWAKE_ACTIVITY = 0.15f

    fun encode(samples: List<MinuteSample>) = samples.joinToString(";") { "%.4f,%.1f".format(java.util.Locale.US, it.activity, it.noiseDb) }

    fun decode(raw: String): List<MinuteSample> = if (raw.isBlank()) emptyList() else raw.split(';').mapNotNull { p ->
        val (a, n) = p.split(',').takeIf { it.size == 2 } ?: return@mapNotNull null
        MinuteSample(a.toFloatOrNull() ?: return@mapNotNull null, n.toFloatOrNull() ?: 0f)
    }

    fun analyze(samples: List<MinuteSample>, goalMinutes: Int): NightReport {
        if (samples.isEmpty()) return NightReport(0, 0, 0, 0, 0, 0, 0f, emptyList(), 0)
        // Weighted moving window, in the spirit of Cole–Kripke: a minute is judged with its neighbours.
        val w = floatArrayOf(0.15f, 0.2f, 0.3f, 0.2f, 0.15f)
        val smooth = samples.indices.map { i ->
            var s = 0f; var tw = 0f
            for (k in -2..2) samples.getOrNull(i + k)?.let { s += it.activity * w[k + 2]; tw += w[k + 2] }
            s / tw
        }
        val median = smooth.sorted()[smooth.size / 2]
        val awakeAbove = (median * 4f).coerceIn(MIN_AWAKE_ACTIVITY, MAX_AWAKE_ACTIVITY)
        val deepBelow = median * 0.6f
        val still = smooth.map { it < awakeAbove }

        val onset = (0..samples.size - ONSET_RUN).firstOrNull { i -> (i until i + ONSET_RUN).all { still[it] } } ?: samples.size
        val stages = MutableList(samples.size) { i ->
            when {
                i < onset || !still[i] -> Stage.AWAKE
                else -> Stage.LIGHT
            }
        }
        // Promote long very-still stretches to deep sleep.
        var runStart = -1
        for (i in 0..samples.size) {
            val deepish = i < samples.size && stages[i] == Stage.LIGHT && smooth[i] <= deepBelow
            if (deepish && runStart < 0) runStart = i
            if (!deepish && runStart >= 0) {
                if (i - runStart >= DEEP_RUN) for (j in runStart until i) stages[j] = Stage.DEEP
                runStart = -1
            }
        }

        val awake = stages.count { it == Stage.AWAKE }
        val deep = stages.count { it == Stage.DEEP }
        val asleep = samples.size - awake
        val duration = (asleep.toFloat() / goalMinutes).coerceAtMost(1f)
        val efficiency = asleep.toFloat() / samples.size
        val deepShare = if (asleep == 0) 0f else (deep.toFloat() / asleep / 0.25f).coerceAtMost(1f)
        val score = ((duration * 0.5f + efficiency * 0.3f + deepShare * 0.2f) * 100).roundToInt()

        return NightReport(
            minutesInBed = samples.size,
            minutesAsleep = asleep,
            sleepOnsetMinutes = onset.coerceAtMost(samples.size),
            awakeMinutes = awake,
            lightMinutes = asleep - deep,
            deepMinutes = deep,
            avgNoiseDb = samples.map { it.noiseDb }.average().toFloat(),
            stages = stages,
            score = score,
        )
    }
}

fun formatMinutes(min: Int): String = if (min < 60) "${min}m" else "${min / 60}h ${"%02d".format(min % 60)}m"
