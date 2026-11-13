package com.hblaze.apexy.missions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

class MissionLogicTest {
    private val g = 9.80665f

    @Test fun mathAnswersMatchTheirText() {
        val rnd = Random(1)
        for (level in 1..5) repeat(200) {
            val p = MathProblems.make(level, rnd)
            val parts = p.text.split(" ")
            val expected = when (parts.size) {
                3 -> parts[0].toLong() + parts[2].toLong()
                else -> parts[0].toLong() * parts[2].toLong() + parts[4].toLong()
            }
            assertEquals(p.text, expected, p.answer)
        }
    }

    @Test fun codecRoundTrips() {
        val list = listOf(
            Mission(MissionType.MATH, 4, 5),
            Mission(MissionType.QR, codes = listOf("https://example.com/a", "b\"quoted\"")),
            Mission(MissionType.PHOTO, 2, photo = "/data/x.jpg"),
        )
        assertEquals(list, MissionCodec.decode(MissionCodec.encode(list)))
        assertEquals(emptyList<Mission>(), MissionCodec.decode("not json"))
    }

    /** Simulated 50 Hz vertical acceleration: down-accelerate, brake at the bottom, rise, brake at the top. */
    private fun squatSignal(reps: Int, amplitude: Float, secondsPerRep: Double): List<Float> {
        val perRep = (secondsPerRep * 50).toInt()
        return List(reps * perRep + 50) { i ->
            if (i >= reps * perRep) 0f else {
                val phase = (i % perRep).toDouble() / perRep
                // One full cosine-shaped displacement cycle → acceleration is -cos: negative, positive, negative.
                (-amplitude * kotlin.math.cos(2 * PI * phase)).toFloat()
            }
        }
    }

    @Test fun squatsAreCounted() {
        val detector = MotionDetectors.Squat(2.2f)
        val count = squatSignal(10, 4f, 2.0).withIndex().count { (i, v) -> detector.onVertical(v, i * 20L) }
        assertEquals(10, count)
    }

    @Test fun squatsWithPausesAreCounted() {
        val detector = MotionDetectors.Squat(2.2f)
        val rep = squatSignal(1, 4f, 1.5)
        val signal = (0 until 6).flatMap { rep + List(100) { 0f } } // 2 s standing between reps
        val count = signal.withIndex().count { (i, v) -> detector.onVertical(v, i * 20L) }
        assertEquals(6, count)
    }

    @Test fun gentleSwayIsNotASquat() {
        val detector = MotionDetectors.Squat(2.2f)
        val count = squatSignal(10, 1f, 2.0).withIndex().count { (i, v) -> detector.onVertical(v, i * 20L) }
        assertEquals(0, count)
    }

    @Test fun shakesAreCountedAndStillnessIsNot() {
        val detector = MotionDetectors.Shake(13f)
        var t = 0L
        repeat(50) { detector.onAccel(floatArrayOf(0f, 0f, g), t); t += 20 }
        var shakes = 0
        repeat(20) {
            // A hard flick: 100 ms of strong sideways acceleration, then rest.
            repeat(5) { if (detector.onAccel(floatArrayOf(22f, 0f, g), t)) shakes++; t += 20 }
            repeat(20) { if (detector.onAccel(floatArrayOf(0f, 0f, g), t)) shakes++; t += 20 }
        }
        assertTrue("got $shakes", shakes in 20..40)
        var still = 0
        repeat(500) { if (detector.onAccel(floatArrayOf(0.3f, 0.2f, g), t)) still++; t += 20 }
        assertEquals(0, still)
    }

    @Test fun walkingIsCounted() {
        val detector = MotionDetectors.Steps()
        var steps = 0
        for (i in 0 until 50 * 20) { // 20 s at ~1.8 steps/s
            val bounce = (3.5 * sin(2 * PI * 1.8 * i / 50.0)).toFloat()
            if (detector.onAccel(floatArrayOf(0f, 0f, g + bounce), i * 20L)) steps++
        }
        assertTrue("got $steps", steps in 32..40)
    }
}
