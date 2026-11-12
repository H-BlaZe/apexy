package com.apexy.alarm.core

import android.content.Context
import com.apexy.alarm.sleep.Bedtime
import com.apexy.alarm.ui.NextAlarmWidget

/** Everything that depends on "when is the next alarm" refreshes from here. */
object AlarmsChanged {
    suspend fun notify(context: Context) {
        Bedtime.reschedule(context)
        runCatching { NextAlarmWidget.refresh(context) }
    }
}
