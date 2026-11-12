package com.apexy.alarm.core

import com.apexy.alarm.data.Alarm
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SoundLogicTest {
    @Test fun rampStartsAtItsLevelAndEndsFull() {
        for (curve in listOf(Alarm.RAMP_LINEAR, Alarm.RAMP_GENTLE, Alarm.RAMP_QUICK)) {
            val a = Alarm(rampStart = 20, rampCurve = curve)
            assertEquals(0.2f, AlarmService.rampAt(a, 0f), 0.001f)
            assertEquals(1f, AlarmService.rampAt(a, 1f), 0.001f)
            // Never gets quieter along the way.
            var last = 0f
            for (i in 0..20) { val v = AlarmService.rampAt(a, i / 20f); assertTrue(v >= last); last = v }
        }
        val mid = { c: String -> AlarmService.rampAt(Alarm(rampStart = 0, rampCurve = c), 0.5f) }
        assertTrue(mid(Alarm.RAMP_GENTLE) < mid(Alarm.RAMP_LINEAR))
        assertTrue(mid(Alarm.RAMP_QUICK) > mid(Alarm.RAMP_LINEAR))
    }

    @Test fun customVibrationParsesAndBadInputFallsBack() {
        assertArrayEquals(longArrayOf(0, 200, 100, 400, 600), AlarmService.vibrationTimings("custom:200,100,400,600"))
        assertArrayEquals(AlarmService.VIBRATION_PATTERNS.getValue("heartbeat"), AlarmService.vibrationTimings("custom:nope"))
        assertArrayEquals(AlarmService.VIBRATION_PATTERNS.getValue("sos"), AlarmService.vibrationTimings("sos"))
        // Absurd values are clamped so a pattern can't buzz forever.
        assertEquals(5_000L, AlarmService.vibrationTimings("custom:99999,10")[1])
    }

    @Test fun shrinkingSnoozeNeverGoesUnderAMinute() {
        val a = Alarm(snoozeMinutes = 3, snoozeShrink = true)
        assertEquals(listOf(3, 2, 1, 1), (0..3).map { a.copy(snoozeCount = it).nextSnoozeMinutes })
        assertEquals(3, a.copy(snoozeShrink = false, snoozeCount = 2).nextSnoozeMinutes)
    }
}
