package com.hblaze.apexy.core

import com.hblaze.apexy.ApexyApp
import com.hblaze.apexy.data.Alarm
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId

/** User-facing alarm operations; each keeps the database and AlarmManager in step. */
object AlarmActions {
    private val app get() = ApexyApp.instance
    private val dao get() = app.db.alarms()

    /** Bedtime and the widget follow along in the background so saving never waits on them. */
    private fun refreshDependents() {
        app.appScope.launch { AlarmsChanged.notify(app) }
    }

    suspend fun save(alarm: Alarm): Alarm {
        val clean = alarm.copy(enabled = true, snoozeCount = 0, snoozedUntil = if (alarm.isQuick) alarm.snoozedUntil else 0)
        val rowId = dao.upsert(clean)
        val saved = if (clean.id == 0L) clean.copy(id = rowId) else clean
        app.scheduler.schedule(saved)
        refreshDependents()
        return saved
    }

    suspend fun setEnabled(alarm: Alarm, enabled: Boolean) {
        val updated = alarm.copy(enabled = enabled, snoozeCount = 0, snoozedUntil = 0, skipUntil = 0)
        dao.update(updated)
        if (enabled) app.scheduler.schedule(updated) else app.scheduler.cancel(updated.id)
        refreshDependents()
    }

    suspend fun delete(alarm: Alarm) {
        app.scheduler.cancel(alarm.id)
        dao.delete(alarm)
        refreshDependents()
    }

    suspend fun duplicate(alarm: Alarm) = save(alarm.copy(id = 0, createdAt = System.currentTimeMillis(), skipUntil = 0))

    suspend fun skipNext(alarm: Alarm) {
        val next = AlarmScheduler.nextTrigger(alarm.copy(snoozedUntil = 0)) ?: return
        val updated = alarm.copy(skipUntil = next + 1, snoozeCount = 0, snoozedUntil = 0)
        dao.update(updated)
        app.scheduler.schedule(updated)
    }

    suspend fun unskip(alarm: Alarm) {
        val updated = alarm.copy(skipUntil = 0)
        dao.update(updated)
        app.scheduler.schedule(updated)
    }

    /** One-off alarm [minutes] from now, exact to the second. */
    suspend fun quick(minutes: Int, label: String = "Quick alarm"): Alarm {
        val at = System.currentTimeMillis() + minutes * 60_000L
        val local = Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault())
        return save(
            Alarm(
                hour = local.hour, minute = local.minute, label = label,
                isQuick = true, snoozedUntil = at, rampSeconds = 10,
            ),
        )
    }
}
