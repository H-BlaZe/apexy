package com.hblaze.apexy.core

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.hblaze.apexy.data.Alarm
import com.hblaze.apexy.data.AlarmDao
import com.hblaze.apexy.ui.MainActivity
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class AlarmScheduler(private val context: Context, private val dao: AlarmDao) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    fun schedule(alarm: Alarm) {
        cancel(alarm.id)
        val at = nextTrigger(alarm) ?: return
        val operation = ringIntent(alarm.id)
        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
        if (canExact) {
            val show = PendingIntent.getActivity(
                context, 0, Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            alarmManager.setAlarmClock(AlarmManager.AlarmClockInfo(at, show), operation)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, operation)
        }
    }

    fun cancel(id: Long) = alarmManager.cancel(ringIntent(id))

    /** Wake-up checks use exact alarm-clock timing too: a sleeping phone must still deliver them. */
    fun scheduleWakeCheck(id: Long, at: Long) {
        val show = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
        if (canExact) alarmManager.setAlarmClock(AlarmManager.AlarmClockInfo(at, show), wakeCheckIntent(id))
        else alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, wakeCheckIntent(id))
    }

    fun cancelWakeCheck(id: Long) = alarmManager.cancel(wakeCheckIntent(id))

    suspend fun rescheduleAll() {
        val now = System.currentTimeMillis()
        dao.getAll().forEach {
            // A quick (nap) alarm whose moment passed while the phone was off is stale, not "same time tomorrow".
            if (it.isQuick && it.enabled && it.snoozedUntil in 1 until now) {
                cancel(it.id)
                dao.delete(it)
                return@forEach
            }
            if (it.enabled) schedule(it) else cancel(it.id)
            // A check that came due while the phone was off fires right away.
            if (it.wakeCheckAt > 0) scheduleWakeCheck(it.id, maxOf(it.wakeCheckAt, now + 5_000))
        }
        com.hblaze.apexy.core.AlarmsChanged.notify(context)
    }

    private fun wakeCheckIntent(id: Long): PendingIntent = PendingIntent.getBroadcast(
        context, (id + WAKE_CHECK_CODE_OFFSET).toInt(),
        Intent(context, AlarmReceiver::class.java)
            .putExtra(AlarmService.EXTRA_ID, id)
            .putExtra(AlarmService.EXTRA_WAKE_CHECK, true),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun ringIntent(id: Long): PendingIntent = PendingIntent.getBroadcast(
        context, id.toInt(),
        Intent(context, AlarmReceiver::class.java).putExtra(AlarmService.EXTRA_ID, id),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    companion object {
        private const val WAKE_CHECK_CODE_OFFSET = 500_000L

        /** Next instant this alarm should ring, or null if it never will. */
        fun nextTrigger(alarm: Alarm, now: Long = System.currentTimeMillis()): Long? {
            if (!alarm.enabled) return null
            if (alarm.snoozedUntil > now) return alarm.snoozedUntil
            val zone = ZoneId.systemDefault()
            val today = ZonedDateTime.ofInstant(Instant.ofEpochMilli(now), zone).toLocalDate()
            for (offset in 0L..14L) {
                val candidate = ZonedDateTime.of(today.plusDays(offset), LocalTime.of(alarm.hour, alarm.minute), zone)
                val ms = candidate.toInstant().toEpochMilli()
                if (ms <= now || ms < alarm.skipUntil) continue
                if (!alarm.isRepeating || alarm.hasDay(candidate.dayOfWeek.value - 1)) return ms
            }
            return null
        }
    }
}
