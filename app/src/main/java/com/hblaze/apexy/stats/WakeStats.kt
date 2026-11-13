package com.hblaze.apexy.stats

import com.hblaze.apexy.data.AlarmEvent
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.sqrt

/**
 * One morning's wake-up for one alarm: every ring from the first until the final dismissal,
 * including snoozes and rings brought back by a failed wake-up check.
 */
data class WakeSession(
    val alarmId: Long,
    val label: String,
    val firstRingAt: Long,
    val endedAt: Long,
    /** "dismissed", "missed", or "" while it's still ringing. */
    val outcome: String,
    val rings: Int,
    val snoozes: Int,
    val missionCount: Int,
    val missionMs: Long,
    val escapes: Int,
    val checksPassed: Int,
    val checksFailed: Int,
) {
    val finished get() = outcome == "dismissed" || outcome == "missed"
    val wokeUp get() = outcome == "dismissed"
    /** Woke up and never fell back asleep: what a streak is made of. */
    val clean get() = wokeUp && checksFailed == 0
    val secondsToGetUp get() = if (wokeUp) (endedAt - firstRingAt) / 1000 else null
}

data class Summary(
    val sessions: Int,
    val wokeRate: Float?,
    val avgSnoozes: Float?,
    val medianSecondsToGetUp: Long?,
    /** Standard deviation of dismissal time of day, in minutes. */
    val consistencyMinutes: Int?,
    val missionSessions: Int,
    val avgMissionSeconds: Long?,
    val escapes: Int,
    val checksPassed: Int,
    val checksTotal: Int,
)

data class DayPoint(val date: LocalDate, val avgMinutesToGetUp: Float?, val missed: Int, val sessions: Int)

data class AlarmBreakdown(val alarmId: Long, val label: String, val sessions: Int, val wokeRate: Float?, val avgSnoozes: Float)

object WakeStats {
    /** How long after a snooze or failed check a ring still belongs to the same wake-up. */
    private const val CONTINUE_WINDOW_MS = 3 * 3_600_000L

    fun sessions(events: List<AlarmEvent>): List<WakeSession> {
        val out = mutableListOf<WakeSession>()
        // Index into [out] of each alarm's latest session, and whether the next ring continues it.
        val open = mutableMapOf<Long, Int>()
        val continues = mutableMapOf<Long, Boolean>()

        for (e in events.sortedBy { it.ringAt }) {
            val idx = open[e.alarmId]
            if (e.kind == AlarmEvent.KIND_CHECK) {
                if (idx == null) continue
                val s = out[idx]
                out[idx] = if (e.outcome == "asleep") s.copy(checksFailed = s.checksFailed + 1) else s.copy(checksPassed = s.checksPassed + 1)
                continues[e.alarmId] = e.outcome == "asleep"
                continue
            }
            val joins = idx != null && continues[e.alarmId] == true &&
                e.ringAt - maxOf(out[idx].endedAt, out[idx].firstRingAt) < CONTINUE_WINDOW_MS
            val snoozed = e.outcome == "snoozed"
            val finalOutcome = if (snoozed) "" else e.outcome
            if (joins) {
                val s = out[idx!!]
                out[idx] = s.copy(
                    endedAt = maxOf(e.endedAt, s.endedAt),
                    outcome = finalOutcome,
                    rings = s.rings + 1,
                    snoozes = s.snoozes + if (snoozed) 1 else 0,
                    missionCount = s.missionCount + e.missionCount,
                    missionMs = s.missionMs + e.missionMs,
                    escapes = s.escapes + e.escapes,
                )
            } else {
                out += WakeSession(
                    alarmId = e.alarmId, label = e.label, firstRingAt = e.ringAt, endedAt = e.endedAt,
                    outcome = finalOutcome, rings = 1, snoozes = if (snoozed) 1 else 0,
                    missionCount = e.missionCount, missionMs = e.missionMs, escapes = e.escapes,
                    checksPassed = 0, checksFailed = 0,
                )
                open[e.alarmId] = out.lastIndex
            }
            continues[e.alarmId] = snoozed
        }
        return out
    }

    fun summary(sessions: List<WakeSession>, zone: ZoneId = ZoneId.systemDefault()): Summary {
        val finished = sessions.filter { it.finished }
        val woke = finished.filter { it.wokeUp }
        val times = woke.mapNotNull { it.secondsToGetUp }.sorted()
        // Consistency compares each wake-up with that same alarm's usual time, so a 6:45 alarm and a
        // 10:00 nap alarm don't count as inconsistency.
        val deviations = woke.groupBy { it.alarmId }.values.filter { it.size >= 2 }.flatMap { group ->
            val minutes = group.map { Instant.ofEpochMilli(it.endedAt).atZone(zone).let { t -> t.hour * 60 + t.minute }.toDouble() }
            val mean = minutes.average()
            minutes.map { it - mean }
        }
        val withMissions = woke.filter { it.missionCount > 0 }
        return Summary(
            sessions = finished.size,
            wokeRate = if (finished.isEmpty()) null else woke.size.toFloat() / finished.size,
            avgSnoozes = if (finished.isEmpty()) null else finished.sumOf { it.snoozes }.toFloat() / finished.size,
            medianSecondsToGetUp = times.getOrNull(times.size / 2),
            consistencyMinutes = if (deviations.isEmpty()) null else sqrt(deviations.sumOf { it * it } / deviations.size).toInt(),
            missionSessions = withMissions.size,
            avgMissionSeconds = if (withMissions.isEmpty()) null else withMissions.sumOf { it.missionMs } / withMissions.size / 1000,
            escapes = sessions.sumOf { it.escapes },
            checksPassed = sessions.sumOf { it.checksPassed },
            checksTotal = sessions.sumOf { it.checksPassed + it.checksFailed },
        )
    }

    /**
     * Consecutive days with alarms where every alarm was dismissed with no failed wake-up check.
     * Days without alarms neither count nor break a streak; today only counts once its alarms are done.
     */
    fun streaks(sessions: List<WakeSession>, today: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Pair<Int, Int> {
        val byDay = sessions.filter { it.finished }.groupBy { day(it.firstRingAt, zone) }.toSortedMap()
        var best = 0
        var run = 0
        for ((_, list) in byDay) {
            run = if (list.all { it.clean }) run + 1 else 0
            best = maxOf(best, run)
        }
        // The current streak is the run that reaches the latest alarm day (today or earlier).
        var current = 0
        for ((date, list) in byDay.entries.reversed()) {
            if (date.isAfter(today)) continue
            if (list.all { it.clean }) current++ else break
        }
        return current to best
    }

    fun daily(sessions: List<WakeSession>, days: Int, today: LocalDate, zone: ZoneId = ZoneId.systemDefault()): List<DayPoint> {
        val byDay = sessions.filter { it.finished }.groupBy { day(it.firstRingAt, zone) }
        return (days - 1 downTo 0).map { back ->
            val date = today.minusDays(back.toLong())
            val list = byDay[date].orEmpty()
            val times = list.mapNotNull { it.secondsToGetUp }
            DayPoint(
                date = date,
                avgMinutesToGetUp = if (times.isEmpty()) null else times.average().toFloat() / 60f,
                missed = list.count { !it.wokeUp },
                sessions = list.size,
            )
        }
    }

    fun byAlarm(sessions: List<WakeSession>): List<AlarmBreakdown> =
        sessions.filter { it.finished }.groupBy { it.alarmId }.map { (id, list) ->
            AlarmBreakdown(
                alarmId = id,
                label = list.last().label,
                sessions = list.size,
                wokeRate = list.count { it.wokeUp }.toFloat() / list.size,
                avgSnoozes = list.sumOf { it.snoozes }.toFloat() / list.size,
            )
        }.sortedByDescending { it.sessions }

    fun day(ms: Long, zone: ZoneId = ZoneId.systemDefault()): LocalDate = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()
}

fun formatSeconds(seconds: Long): String = when {
    seconds < 60 -> "${seconds}s"
    seconds < 3600 -> "${seconds / 60}m ${seconds % 60}s"
    else -> "${seconds / 3600}h ${(seconds % 3600) / 60}m"
}
