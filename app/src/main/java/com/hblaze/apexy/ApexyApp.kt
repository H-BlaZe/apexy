package com.hblaze.apexy

import android.app.Application
import com.hblaze.apexy.core.AlarmScheduler
import com.hblaze.apexy.core.Notifications
import com.hblaze.apexy.core.ToneLibrary
import com.hblaze.apexy.data.AppDatabase
import com.hblaze.apexy.data.SettingsRepository
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
        appScope.launch { com.hblaze.apexy.ui.UiSounds.init(this@ApexyApp) }
        appScope.launch {
            ToneLibrary.ensureGenerated(this@ApexyApp)
            scheduler.rescheduleAll()
        }
        // Repaint the widget whenever the look changes.
        appScope.launch {
            settings.settings.collect {
                com.hblaze.apexy.ui.UiSounds.enabled = it.uiSounds
                com.hblaze.apexy.ui.NextAlarmWidget.refresh(this@ApexyApp)
            }
        }
    }

    companion object {
        lateinit var instance: ApexyApp
            private set
    }
}
