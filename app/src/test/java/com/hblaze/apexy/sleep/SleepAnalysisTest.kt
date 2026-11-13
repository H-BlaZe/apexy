package com.hblaze.apexy.sleep

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class SleepAnalysisTest {
    private val rnd = Random(3)
    private fun minutes(n: Int, activity: Float) = List(n) { MinuteSample(activity * (0.8f + rnd.nextFloat() * 0.4f), 32f) }

    /** 15 min restless, then light/deep cycles with a 10-min wake in the middle, restless at the end. */
    private val night = minutes(15, 0.3f) +
        minutes(60, 0.03f) + minutes(90, 0.006f) + minutes(60, 0.03f) +
        minutes(10, 0.4f) +
        minutes(60, 0.03f) + minutes(90, 0.006f) + minutes(60, 0.03f) +
        minutes(5, 0.5f)

    @Test fun findsOnsetStagesAndWakeups() {
        val r = SleepAnalysis.analyze(night, goalMinutes = 420)
        assertEquals(night.size, r.minutesInBed)
        assertTrue("onset ${r.sleepOnsetMinutes}", r.sleepOnsetMinutes in 13..20)
        assertTrue("deep ${r.deepMinutes}", r.deepMinutes in 150..200)
        assertTrue("awake ${r.awakeMinutes}", r.awakeMinutes in 25..45)
        assertEquals(r.minutesInBed, r.awakeMinutes + r.lightMinutes + r.deepMinutes)
        assertTrue("score ${r.score}", r.score in 75..100)
    }

    @Test fun restlessNightScoresLow() {
        val r = SleepAnalysis.analyze(minutes(120, 0.4f), goalMinutes = 480)
        assertTrue(r.minutesAsleep < 20)
        assertTrue("score ${r.score}", r.score < 30)
    }

    @Test fun samplesRoundTrip() {
        val s = listOf(MinuteSample(0.0123f, 41.5f), MinuteSample(0.5f, 30f))
        val back = SleepAnalysis.decode(SleepAnalysis.encode(s))
        assertEquals(2, back.size)
        assertEquals(0.0123f, back[0].activity, 0.0001f)
        assertEquals(30f, back[1].noiseDb, 0.01f)
    }

    @Test fun mixParsing() {
        assertEquals(mapOf("rain" to 0.6f, "fan" to 0.25f), Ambient.parseMix("rain:0.6,fan:0.25,bogus:1,ocean:0"))
        assertEquals("rain:0.60", Ambient.formatMix(mapOf("rain" to 0.6f, "fan" to 0f)))
    }
}
