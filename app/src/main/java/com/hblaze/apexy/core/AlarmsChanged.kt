package com.hblaze.apexy.core

import android.content.Context
import com.hblaze.apexy.sleep.Bedtime
import com.hblaze.apexy.ui.NextAlarmWidget

/** Everything that depends on "when is the next alarm" refreshes from here. */
object AlarmsChanged {
    suspend fun notify(context: Context) {
        Bedtime.reschedule(context)
        runCatching { NextAlarmWidget.refresh(context) }
    }
}
