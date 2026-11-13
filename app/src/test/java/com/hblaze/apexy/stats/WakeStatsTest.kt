package com.hblaze.apexy.stats

import com.hblaze.apexy.data.AlarmEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class WakeStatsTest {
    private val utc = ZoneOffset.UTC
    private fun t(day: Int, h: Int, m: Int, s: Int = 0) = LocalDate.of(2026, 11, day).atTime(h, m, s).toInstant(utc).toEpochMilli()
    private fun ring(id: Long, at: Long, end: Long, outcome: String, missions: Int = 0) =
        AlarmEvent(alarmId = id, label = "Gym", ringAt = at, endedAt = end, outcome = outcome, missionCount = missions, missionMs = if (missions > 0) 40_000 else 0)
    private fun check(id: Long, at: Long, outcome: String) =
        AlarmEvent(alarmId = id, label = "Gym", ringAt = at, endedAt = at, outcome = outcome, kind = AlarmEvent.KIND_CHECK)

    @Test fun snoozesJoinOneSession() {
        val events = listOf(
            ring(1, t(10, 7, 0), t(10, 7, 0, 20), "snoozed"),
            ring(1, t(10, 7, 5), t(10, 7, 5, 30), "snoozed"),
            ring(1, t(10, 7, 10), t(10, 7, 12), "dismissed", missions = 2),
        )
        val s = WakeStats.sessions(events).single()
        assertEquals(2, s.snoozes)
        assertEquals(3, s.rings)
        assertEquals(12 * 60L, s.secondsToGetUp)
        assertEquals(2, s.missionCount)
    }

    @Test fun failedCheckReopensSessionAndBreaksStreak() {
        val events = listOf(
            ring(1, t(10, 7, 0), t(10, 7, 1), "dismissed"),
            check(1, t(10, 7, 4), "asleep"),
            ring(1, t(10, 7, 4, 1), t(10, 7, 6), "dismissed"),
            check(1, t(10, 7, 9), "awake"),
            ring(1, t(11, 7, 0), t(11, 7, 1), "dismissed"),
        )
        val sessions = WakeStats.sessions(events)
        assertEquals(2, sessions.size)
        assertEquals(1, sessions[0].checksFailed)
        assertEquals(1, sessions[0].checksPassed)
        assertEquals(6 * 60L, sessions[0].secondsToGetUp)
        assertEquals(1 to 1, WakeStats.streaks(sessions, LocalDate.of(2026, 11, 11), utc))
    }

    @Test fun streakSkipsDaysWithoutAlarmsAndStopsAtMisses() {
        val events = listOf(
            ring(1, t(5, 7, 0), t(5, 7, 30), "missed"),
            ring(1, t(6, 7, 0), t(6, 7, 1), "dismissed"),
            ring(1, t(7, 7, 0), t(7, 7, 1), "dismissed"),
            // 8th: no alarm (weekend)
            ring(1, t(9, 7, 0), t(9, 7, 1), "dismissed"),
        )
        val sessions = WakeStats.sessions(events)
        assertEquals(3 to 3, WakeStats.streaks(sessions, LocalDate.of(2026, 11, 10), utc))
    }

    @Test fun summaryAndDaily() {
        val events = listOf(
            ring(1, t(9, 7, 0), t(9, 7, 2), "dismissed"),
            ring(1, t(10, 7, 0), t(10, 7, 30), "missed"),
            ring(2, t(10, 8, 0), t(10, 8, 4), "dismissed"),
        )
        val sessions = WakeStats.sessions(events)
        val sum = WakeStats.summary(sessions, utc)
        assertEquals(3, sum.sessions)
        assertEquals(2f / 3, sum.wokeRate!!, 0.001f)
        assertEquals(240L, sum.medianSecondsToGetUp)
        val days = WakeStats.daily(sessions, 3, LocalDate.of(2026, 11, 10), utc)
        assertEquals(listOf(8, 9, 10), days.map { it.date.dayOfMonth })
        assertNull(days[0].avgMinutesToGetUp)
        assertEquals(2f, days[1].avgMinutesToGetUp!!, 0.01f)
        assertEquals(1, days[2].missed)
    }

    @Test fun formatsDurations() {
        assertEquals("45s", formatSeconds(45))
        assertEquals("3m 10s", formatSeconds(190))
        assertEquals("1h 5m", formatSeconds(3900))
    }
}
