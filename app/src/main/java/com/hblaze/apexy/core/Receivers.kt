package com.hblaze.apexy.core

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.hblaze.apexy.ApexyApp
import kotlinx.coroutines.launch

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(AlarmService.EXTRA_ID, -1)
        if (id < 0) return
        val intent = if (intent.getBooleanExtra(AlarmService.EXTRA_WAKE_CHECK, false)) AlarmService.wakeCheckIntent(context, id)
        else AlarmService.ringIntent(context, id)
        ContextCompat.startForegroundService(context, intent)
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val app = context.applicationContext as ApexyApp
        app.appScope.launch {
            try { app.scheduler.rescheduleAll() } finally { pending.finish() }
        }
    }
}
