package com.hblaze.apexy.sleep

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.hblaze.apexy.ApexyApp
import com.hblaze.apexy.R
import com.hblaze.apexy.core.AlarmScheduler
import com.hblaze.apexy.data.Alarm
import com.hblaze.apexy.data.AppSettings
import com.hblaze.apexy.ui.MainActivity
import com.hblaze.apexy.ui.formatTime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId

/** Bedtime = the next alarm minus the sleep goal; the reminder fires a little before that. */
object Bedtime {
    data class Plan(val alarm: Alarm, val alarmAt: Long, val bedtime: Long, val remindAt: Long)

    fun plan(alarms: List<Alarm>, settings: AppSettings, now: Long = System.currentTimeMillis()): Plan? {
        // Quick alarms are naps, not mornings.
        val (alarm, at) = alarms.filter { !it.isQuick }
            .mapNotNull { a -> AlarmScheduler.nextTrigger(a, now)?.let { a to it } }
            .minByOrNull { it.second } ?: return null
        val bedtime = at - settings.sleepGoalMinutes * 60_000L
        return Plan(alarm, at, bedtime, bedtime - settings.bedtimeLeadMinutes * 60_000L)
    }

    suspend fun reschedule(context: Context) {
        val app = context.applicationContext as ApexyApp
        val settings = app.settings.settings.first()
        val am = context.getSystemService(AlarmManager::class.java)
        val pi = PendingIntent.getBroadcast(
            context, 0, Intent(context, BedtimeReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        am.cancel(pi)
        if (!settings.bedtimeReminder) return
        val plan = plan(app.db.alarms().getAll(), settings) ?: return
        // Past tonight's reminder time already: there's nothing useful to remind about.
        if (plan.remindAt <= System.currentTimeMillis()) return
        val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
        if (exact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, plan.remindAt, pi)
        else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, plan.remindAt, pi)
    }

    fun clock(context: Context, ms: Long): String {
        val t = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault())
        return formatTime(context, t.hour, t.minute)
    }
}

class BedtimeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val app = context.applicationContext as ApexyApp
        app.appScope.launch {
            try {
                val settings = app.settings.settings.first()
                Bedtime.plan(app.db.alarms().getAll(), settings, System.currentTimeMillis() - 60_000)?.let {
                    // Tracking a whole night on a low, unplugged battery could leave the alarm without power.
                    val battery = BatteryStatus.read(context)
                    notify(context, it, if (settings.trackSleep && battery.riskyForNight) battery.percent else null)
                }
            } finally {
                pending.finish()
            }
        }
    }

    private fun notify(context: Context, plan: Bedtime.Plan, lowBattery: Int?) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Bedtime reminders", NotificationManager.IMPORTANCE_DEFAULT))
        val open = PendingIntent.getActivity(
            context, 20, Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_TAB, "sleep").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        nm.notify(
            NOTIFICATION_ID,
            NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_apexy)
                .setContentTitle("Time to wind down")
                .setContentText("Be in bed by ${Bedtime.clock(context, plan.bedtime)} to make your ${Bedtime.clock(context, plan.alarmAt)} alarm.")
                .setStyle(
                    NotificationCompat.BigTextStyle().bigText(
                        "Be in bed by ${Bedtime.clock(context, plan.bedtime)} to make your ${Bedtime.clock(context, plan.alarmAt)} alarm." +
                            (lowBattery?.let { "\n\nBattery is at $it%. Keep your phone charged for sleep tracking, or the battery may run out." } ?: ""),
                    ),
                )
                .setContentIntent(open)
                .addAction(0, "Start sleep", open)
                .setAutoCancel(true)
                .build(),
        )
    }

    companion object {
        private const val CHANNEL = "bedtime"
        private const val NOTIFICATION_ID = 3001
    }
}
