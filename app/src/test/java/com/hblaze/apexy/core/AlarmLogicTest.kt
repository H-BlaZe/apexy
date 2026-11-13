package com.hblaze.apexy.core

import com.hblaze.apexy.data.Alarm
import com.hblaze.apexy.data.AppSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class AlarmLogicTest {
    private val zone = ZoneId.systemDefault()
    private fun at(y: Int, mo: Int, d: Int, h: Int, mi: Int) = LocalDateTime.of(y, mo, d, h, mi).atZone(zone).toInstant().toEpochMilli()

    // 2026-11-12 is a Thursday.
    private val thu0800 = at(2026, 11, 12, 8, 0)

    @Test fun oneOffRingsTodayOrTomorrow() {
        assertEquals(at(2026, 11, 12, 9, 30), AlarmScheduler.nextTrigger(Alarm(hour = 9, minute = 30), thu0800))
        assertEquals(at(2026, 11, 13, 7, 0), AlarmScheduler.nextTrigger(Alarm(hour = 7, minute = 0), thu0800))
    }

    @Test fun repeatingPicksNextSelectedDay() {
        val mondays = Alarm(hour = 7, minute = 0, days = 0b0000001)
        assertEquals(at(2026, 11, 16, 7, 0), AlarmScheduler.nextTrigger(mondays, thu0800))
    }

    @Test fun skipNextJumpsOneOccurrence() {
        val weekdays = Alarm(hour = 9, minute = 0, days = 0b0011111)
        val first = AlarmScheduler.nextTrigger(weekdays, thu0800)!!
        val skipped = weekdays.copy(skipUntil = first + 1)
        assertEquals(at(2026, 11, 13, 9, 0), AlarmScheduler.nextTrigger(skipped, thu0800))
    }

    @Test fun snoozeOverridesSchedule() {
        val snoozed = Alarm(hour = 7, minute = 0, snoozedUntil = thu0800 + 300_000, snoozeCount = 1)
        assertEquals(thu0800 + 300_000, AlarmScheduler.nextTrigger(snoozed, thu0800))
    }

    @Test fun disabledNeverRings() = assertNull(AlarmScheduler.nextTrigger(Alarm(enabled = false), thu0800))

    private val strict = AppSettings(preventChanges = true, lockWindowMinutes = 60)

    @Test fun lockOnlyInsideWindow() {
        val soon = Alarm(id = 1, hour = 8, minute = 45)
        val later = Alarm(id = 2, hour = 10, minute = 0)
        assertTrue(AlarmGuard.lock(soon, strict, null, thu0800) is AlarmGuard.Lock.Soon)
        assertNull(AlarmGuard.lock(later, strict, null, thu0800))
        assertNull("off by default", AlarmGuard.lock(soon, AppSettings(), null, thu0800))
        assertNull("disabled alarms can be edited", AlarmGuard.lock(soon.copy(enabled = false), strict, null, thu0800))
    }

    @Test fun snoozedAndRingingAreLocked() {
        val snoozed = Alarm(id = 3, hour = 6, minute = 0, snoozeCount = 1, snoozedUntil = thu0800 + 4 * 3_600_000)
        assertEquals(AlarmGuard.Lock.Snoozed, AlarmGuard.lock(snoozed, strict, null, thu0800))
        assertEquals("ringing is locked even with the setting off", AlarmGuard.Lock.Ringing, AlarmGuard.lock(Alarm(id = 4), AppSettings(), 4L, thu0800))
    }

    @Test fun settingsCanOnlyLoosenWhenNothingIsLocked() {
        val alarms = listOf(Alarm(id = 1, hour = 8, minute = 30), Alarm(id = 2, hour = 20, minute = 0))
        assertEquals(false, AlarmGuard.canLoosen(alarms, strict, null, thu0800))
        assertEquals(true, AlarmGuard.canLoosen(alarms.drop(1), strict, null, thu0800))
    }
}
