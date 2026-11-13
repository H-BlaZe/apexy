package com.hblaze.apexy.core

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context

object Notifications {
    const val CHANNEL_RINGING = "ringing"
    const val CHANNEL_RINGING_QUIET = "ringing_quiet"
    const val CHANNEL_GENERAL = "general"
    const val ID_RINGING = 1001
    const val ID_RINGING_QUIET = 1002

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_RINGING, "Ringing alarms", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Shown while an alarm is ringing"
                setSound(null, null)
                enableVibration(false)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_RINGING_QUIET, "Ringing alarm (screen open)", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Used while the alarm screen is already showing, so no banner covers it"
                setSound(null, null)
                enableVibration(false)
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_GENERAL, "Reminders", NotificationManager.IMPORTANCE_DEFAULT),
        )
    }
}
