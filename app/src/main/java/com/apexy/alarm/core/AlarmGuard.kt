package com.apexy.alarm.core

import com.apexy.alarm.data.Alarm
import com.apexy.alarm.data.AppSettings

/** Decides when an alarm may no longer be changed, so a sleepy user can't talk their way out of it. */
object AlarmGuard {
    sealed interface Lock {
        data object Ringing : Lock
        data object Snoozed : Lock
        data class Soon(val ringsAt: Long, val windowMinutes: Int) : Lock
    }

    /** Why [alarm] is locked right now, or null if it can be changed. Turning an alarm *on* is never blocked. */
    fun lock(alarm: Alarm, settings: AppSettings, ringingId: Long?, now: Long = System.currentTimeMillis()): Lock? {
        if (alarm.id == ringingId) return Lock.Ringing
        if (!settings.preventChanges || !alarm.enabled) return null
        if (alarm.snoozeCount > 0 && alarm.snoozedUntil > now) return Lock.Snoozed
        val next = AlarmScheduler.nextTrigger(alarm, now) ?: return null
        return if (next - now <= settings.lockWindowMinutes * 60_000L) Lock.Soon(next, settings.lockWindowMinutes) else null
    }

    /** The anti-cheat settings themselves can only be loosened when no alarm is locked. */
    fun canLoosen(alarms: List<Alarm>, settings: AppSettings, ringingId: Long?, now: Long = System.currentTimeMillis()) =
        alarms.none { lock(it, settings, ringingId, now) != null }
}
