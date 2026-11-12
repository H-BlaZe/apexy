package com.apexy.alarm

import android.app.Application
import com.apexy.alarm.core.AlarmScheduler
import com.apexy.alarm.core.Notifications
import com.apexy.alarm.core.ToneLibrary
import com.apexy.alarm.data.AppDatabase
import com.apexy.alarm.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ApexyApp : Application() {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val db by lazy { AppDatabase.create(this) }
    val settings by lazy { SettingsRepository(this) }
    val scheduler by lazy { AlarmScheduler(this, db.alarms()) }

    override fun onCreate() {
        super.onCreate()
        instance = this
        Notifications.createChannels(this)
        appScope.launch { com.apexy.alarm.ui.UiSounds.init(this@ApexyApp) }
        appScope.launch {
            ToneLibrary.ensureGenerated(this@ApexyApp)
            scheduler.rescheduleAll()
        }
        // Repaint the widget whenever the look changes.
        appScope.launch {
            settings.settings.collect {
                com.apexy.alarm.ui.UiSounds.enabled = it.uiSounds
                com.apexy.alarm.ui.NextAlarmWidget.refresh(this@ApexyApp)
            }
        }
    }

    companion object {
        lateinit var instance: ApexyApp
            private set
    }
}
