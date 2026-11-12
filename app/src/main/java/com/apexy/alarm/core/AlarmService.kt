package com.apexy.alarm.core

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.media.audiofx.LoudnessEnhancer
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.apexy.alarm.ApexyApp
import com.apexy.alarm.R
import com.apexy.alarm.data.Alarm
import com.apexy.alarm.data.AlarmEvent
import com.apexy.alarm.ui.RingingActivity
import com.apexy.alarm.ui.formatTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale
import kotlin.math.roundToInt

data class Ringing(
    val alarm: Alarm,
    val eventId: Long,
    val preview: Boolean,
    val startedAt: Long,
    /** A gentle "are you awake?" prompt after dismissal rather than the alarm itself. */
    val wakeCheck: Boolean = false,
    /** Left ringing past the escalation point: louder, faster vibration, flashing screen. */
    val escalated: Boolean = false,
) {
    val canSnooze get() = !preview && !wakeCheck && alarm.snoozeEnabled && (alarm.maxSnoozes < 0 || alarm.snoozeCount < alarm.maxSnoozes)
    /** The screen flashes when the alarm asks for it or once it has escalated. */
    val flashScreen get() = !wakeCheck && (escalated || alarm.flash == Alarm.FLASH_SCREEN || alarm.flash == Alarm.FLASH_BOTH)
    val checkDeadline get() = startedAt + alarm.wakeUpCheckResponse * 1000L
}

/** How the missions went on the ring that ended in a dismissal. */
data class MissionStats(val count: Int = 0, val ms: Long = 0, val escapes: Int = 0)

/** Foreground service that owns everything audible while an alarm rings. */
class AlarmService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val app get() = application as ApexyApp
    private val audio by lazy { getSystemService(AudioManager::class.java) }

    private var player: MediaPlayer? = null
    private var enhancer: LoudnessEnhancer? = null
    private var vibrator: Vibrator? = null
    private var tts: TextToSpeech? = null
    private var focus: AudioFocusRequest? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var savedVolume = -1
    private var rampLevel = 1f
    private var duckLevel = 1f
    private var missionLevel = 1f
    private var vibrationPattern: String? = null
    private var targetVolume = -1
    /** Stream volume the alarm started at, and the ceiling escalation / idle boosts may reach. */
    private var baseVolume = -1
    private var capVolume = -1
    private var idleBoosts = 0
    private var torch: Torch? = null
    private var screenShown = false
    private val ringJobs = mutableListOf<Job>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_RING -> {
                startForegroundCompat(buildNotification(null))
                val id = intent.getLongExtra(EXTRA_ID, -1)
                val preview = intent.getBooleanExtra(EXTRA_PREVIEW, false)
                scope.launch { ring(id, preview) }
            }
            ACTION_WAKE_CHECK -> {
                startForegroundCompat(buildNotification(null))
                val id = intent.getLongExtra(EXTRA_ID, -1)
                scope.launch { startWakeCheck(id) }
            }
            ACTION_CHECK_PASS -> scope.launch { passWakeCheck() }
            ACTION_SNOOZE -> scope.launch { snooze() }
            ACTION_DISMISS -> {
                val missions = MissionStats(
                    count = intent.getIntExtra(EXTRA_MISSION_COUNT, 0),
                    ms = intent.getLongExtra(EXTRA_MISSION_MS, 0),
                    escapes = intent.getIntExtra(EXTRA_ESCAPES, 0),
                )
                scope.launch { dismiss(missions = missions) }
            }
            ACTION_SCREEN_SHOWN -> setScreenShown(true)
            ACTION_SCREEN_HIDDEN -> setScreenShown(false)
            ACTION_MISSION_START -> setMissionMode(true)
            ACTION_MISSION_IDLE -> setMissionMode(false)
            else -> if (state.value == null) stopSelf()
        }
        return START_NOT_STICKY
    }

    private suspend fun ring(id: Long, preview: Boolean) {
        // A second alarm firing mid-ring takes over; the first counts as missed.
        state.value?.let { finishEvent(it, "missed") ; stopOutputs() }

        val alarm = app.db.alarms().get(id) ?: return stopIfIdle()
        val eventId = if (preview) -1 else app.db.events().insert(
            AlarmEvent(alarmId = alarm.id, label = alarm.label, ringAt = System.currentTimeMillis(), snoozes = alarm.snoozeCount),
        )
        if (!preview && alarm.isRepeating) {
            // Book the next occurrence now so a crash mid-ring never loses it.
            val cleared = alarm.copy(snoozedUntil = 0)
            app.db.alarms().update(cleared)
            app.scheduler.schedule(cleared)
        }

        val ringing = Ringing(alarm, eventId, preview, System.currentTimeMillis())
        _state.value = ringing
        if (!preview) com.apexy.alarm.sleep.SleepService.send(this, com.apexy.alarm.sleep.SleepService.ACTION_ALARM_RINGING)
        acquireWakeLock()
        startForegroundCompat(buildNotification(ringing))
        launchRingingScreen()
        val settings = app.settings.settings.first()
        startAudio(alarm)
        if (settings.lockVolume && !preview) guardVolume()
        if (settings.bringBack && !preview) guardScreen()
        if (alarm.vibrate) startVibration(alarm.vibrationPattern)
        if (alarm.speakLabel) startSpeaking(alarm)
        if (alarm.flash == Alarm.FLASH_TORCH || alarm.flash == Alarm.FLASH_BOTH) startTorch()
        if (alarm.escalate) ringJobs += scope.launch {
            delay(if (preview) 20_000L else alarm.escalateAfter * 60_000L)
            escalate()
        }
        ringJobs += scope.launch {
            delay(if (preview) PREVIEW_MS else TIMEOUT_MS)
            if (ringing.canSnooze) snooze(outcome = "missed") else dismiss(outcome = "missed")
        }
    }

    private suspend fun snooze(outcome: String = "snoozed") {
        val ringing = state.value ?: return stopIfIdle()
        if (!ringing.canSnooze) return
        val fresh = app.db.alarms().get(ringing.alarm.id) ?: ringing.alarm
        val until = System.currentTimeMillis() + fresh.nextSnoozeMinutes * 60_000L
        val snoozed = fresh.copy(snoozeCount = fresh.snoozeCount + 1, snoozedUntil = until)
        app.db.alarms().update(snoozed)
        app.scheduler.schedule(snoozed)
        finishEvent(ringing, outcome)
        endRinging()
    }

    private suspend fun dismiss(outcome: String = "dismissed", missions: MissionStats = MissionStats()) {
        val ringing = state.value ?: return stopIfIdle()
        if (ringing.wakeCheck) return
        if (!ringing.preview) {
            val fresh = app.db.alarms().get(ringing.alarm.id)
            if (fresh != null) {
                val checkAt = if (outcome == "dismissed" && fresh.wakeUpCheck) System.currentTimeMillis() + fresh.wakeUpCheckMinutes * 60_000L else 0L
                val reset = fresh.copy(snoozeCount = 0, snoozedUntil = 0, wakeCheckAt = checkAt)
                when {
                    reset.isRepeating -> { app.db.alarms().update(reset); app.scheduler.schedule(reset) }
                    // A quick alarm waits for its wake-up check before deleting itself.
                    reset.isQuick && checkAt == 0L -> { app.db.alarms().delete(reset); app.scheduler.cancel(reset.id) }
                    else -> { app.db.alarms().update(reset.copy(enabled = false)); app.scheduler.cancel(reset.id) }
                }
                if (checkAt > 0) app.scheduler.scheduleWakeCheck(reset.id, checkAt)
            }
            finishEvent(ringing, outcome, missions)
            if (outcome == "dismissed") com.apexy.alarm.sleep.SleepService.send(this, com.apexy.alarm.sleep.SleepService.ACTION_ALARM_DISMISSED)
            com.apexy.alarm.core.AlarmsChanged.notify(this)
        }
        endRinging()
    }

    // --- Wake-up check -----------------------------------------------------------------------

    private suspend fun startWakeCheck(id: Long) {
        val alarm = app.db.alarms().get(id) ?: return stopIfIdle()
        app.db.alarms().update(alarm.copy(wakeCheckAt = 0))
        // A real alarm is ringing: that already proves nothing, so try again after it.
        if (state.value != null) {
            val retry = System.currentTimeMillis() + alarm.wakeUpCheckMinutes * 60_000L
            app.db.alarms().update(alarm.copy(wakeCheckAt = retry))
            app.scheduler.scheduleWakeCheck(id, retry)
            return
        }
        val check = Ringing(alarm, eventId = -1, preview = false, startedAt = System.currentTimeMillis(), wakeCheck = true)
        _state.value = check
        acquireWakeLock()
        startForegroundCompat(buildNotification(check))
        launchRingingScreen()
        startAudio(alarm.copy(sound = "builtin:chime", volume = 40, rampSeconds = 0, extraLoud = false))
        startVibration("pulse")
        ringJobs += scope.launch {
            delay(alarm.wakeUpCheckResponse * 1000L)
            failWakeCheck()
        }
    }

    private suspend fun passWakeCheck() {
        val check = state.value?.takeIf { it.wakeCheck } ?: return
        logCheck(check.alarm, "awake")
        val fresh = app.db.alarms().get(check.alarm.id)
        if (fresh != null && fresh.isQuick && !fresh.enabled) app.db.alarms().delete(fresh)
        endRinging()
    }

    /** No answer: back to the full alarm, missions and all. */
    private suspend fun failWakeCheck() {
        val check = state.value?.takeIf { it.wakeCheck } ?: return
        logCheck(check.alarm, "asleep")
        // stopOutputs() cancels the job we're running in, so ring from a fresh coroutine.
        stopOutputs()
        _state.value = null
        scope.launch { ring(check.alarm.id, preview = false) }
    }

    private suspend fun logCheck(alarm: Alarm, outcome: String) {
        val now = System.currentTimeMillis()
        app.db.events().insert(
            AlarmEvent(alarmId = alarm.id, label = alarm.label, ringAt = now, endedAt = now, outcome = outcome, kind = AlarmEvent.KIND_CHECK),
        )
    }

    private suspend fun finishEvent(ringing: Ringing, outcome: String, missions: MissionStats = MissionStats()) {
        if (ringing.preview) return
        val event = app.db.events().get(ringing.eventId) ?: return
        app.db.events().update(
            event.copy(
                endedAt = System.currentTimeMillis(), outcome = outcome,
                missionCount = missions.count, missionMs = missions.ms, escapes = missions.escapes,
            ),
        )
    }

    private fun endRinging() {
        stopOutputs()
        _state.value = null
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        getSystemService(android.app.NotificationManager::class.java).cancel(Notifications.ID_RINGING_QUIET)
        stopSelf()
    }

    private fun stopIfIdle() {
        if (state.value == null) endRinging()
    }

    // --- Audio -------------------------------------------------------------------------------

    private fun startAudio(alarm: Alarm) {
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_ALARM)
        savedVolume = audio.getStreamVolume(AudioManager.STREAM_ALARM)
        val target = if (alarm.extraLoud) max else (max * alarm.volume / 100f).roundToInt().coerceIn(1, max)
        targetVolume = target
        baseVolume = target
        capVolume = maxOf(target, (max * alarm.volumeCap / 100f).roundToInt().coerceIn(1, max))
        idleBoosts = 0
        runCatching { audio.setStreamVolume(AudioManager.STREAM_ALARM, target, 0) }

        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT).setAudioAttributes(attrs).build()
            .also { audio.requestAudioFocus(it) }

        val mp = createPlayer(attrs, resolveSound(alarm.sound))
            ?: createPlayer(attrs, Uri.fromFile(ToneLibrary.file(this, "radiant")))
            ?: return
        player = mp
        if (alarm.extraLoud) {
            enhancer = runCatching {
                LoudnessEnhancer(mp.audioSessionId).apply { setTargetGain(1200); enabled = true }
            }.getOrNull()
        }
        val ramping = alarm.rampSeconds > 0 && !alarm.extraLoud
        rampLevel = if (ramping) rampAt(alarm, 0f) else 1f
        applyVolume()
        mp.start()

        if (ramping) ringJobs += scope.launch {
            val steps = alarm.rampSeconds * 4
            for (i in 1..steps) {
                delay(250)
                // Never lower it: escalation may already have pushed it to full.
                rampLevel = maxOf(rampLevel, rampAt(alarm, i.toFloat() / steps))
                applyVolume()
            }
        }
    }

    private fun createPlayer(attrs: AudioAttributes, uri: Uri): MediaPlayer? = runCatching {
        MediaPlayer().apply {
            setAudioAttributes(attrs)
            setDataSource(this@AlarmService, uri)
            isLooping = true
            prepare()
        }
    }.getOrNull()

    private fun resolveSound(sound: String): Uri = when {
        sound.startsWith("builtin:") -> {
            ToneLibrary.ensureGenerated(this)
            Uri.fromFile(ToneLibrary.file(this, sound.removePrefix("builtin:")))
        }
        sound == "system" -> RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        else -> Uri.parse(sound)
    }

    private fun applyVolume() {
        val v = rampLevel * duckLevel * missionLevel
        player?.setVolume(v, v)
    }

    /**
     * While the ringing screen is in front, swap the alerting notification for a silent one so
     * Android's heads-up banner doesn't sit on top of the screen; swap back when it's left.
     */
    private fun setScreenShown(shown: Boolean) {
        screenShown = shown
        val ringing = state.value ?: return
        val nm = getSystemService(android.app.NotificationManager::class.java)
        if (shown) {
            startForegroundCompat(buildNotification(ringing, quiet = true), Notifications.ID_RINGING_QUIET)
            nm.cancel(Notifications.ID_RINGING)
        } else {
            startForegroundCompat(buildNotification(ringing), Notifications.ID_RINGING)
            nm.cancel(Notifications.ID_RINGING_QUIET)
        }
    }

    /** Anti-cheat: turning the alarm volume down while it rings doesn't stick. */
    private fun guardVolume() {
        ringJobs += scope.launch {
            while (isActive) {
                delay(1_000)
                if (targetVolume > 0 && audio.getStreamVolume(AudioManager.STREAM_ALARM) < targetVolume) {
                    runCatching { audio.setStreamVolume(AudioManager.STREAM_ALARM, targetVolume, 0) }
                }
            }
        }
    }

    /**
     * Anti-cheat: if the user leaves the ringing screen, bring it back. Android only allows this when
     * Apexy may draw over other apps (on Xiaomi: "Display pop-up windows while running in background").
     */
    private fun guardScreen() {
        ringJobs += scope.launch {
            while (isActive) {
                delay(2_000)
                if (!screenShown) launchRingingScreen()
            }
        }
    }

    /** Quiet the alarm and stop vibrating while a mission runs (vibration would also fool the motion sensors). */
    private fun setMissionMode(active: Boolean) {
        val ringing = state.value ?: return
        missionLevel = if (!active) 1f else when (ringing.alarm.duringMission) {
            Alarm.DURING_NORMAL -> 1f
            Alarm.DURING_OFF -> 0f
            else -> 0.25f
        }
        applyVolume()
        if (active) {
            vibrator?.cancel()
            // The camera missions need the flash unit, and a strobing torch is no help while solving.
            torch?.stop()
        } else {
            vibrationPattern?.let { startVibration(it) }
            if (ringing.alarm.flash == Alarm.FLASH_TORCH || ringing.alarm.flash == Alarm.FLASH_BOTH || ringing.escalated) startTorch()
            boostAfterIdle()
        }
    }

    /**
     * Walked away from a mission: each time, the alarm comes back one notch louder, but never past
     * the alarm's speaker limit.
     */
    private fun boostAfterIdle() {
        if (baseVolume < 0 || capVolume <= baseVolume) return
        idleBoosts++
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_ALARM)
        val step = maxOf(1, (max * 0.1f).roundToInt())
        setStreamTarget(minOf(capVolume, baseVolume + step * idleBoosts))
    }

    private fun setStreamTarget(volume: Int) {
        targetVolume = maxOf(targetVolume, volume)
        runCatching { audio.setStreamVolume(AudioManager.STREAM_ALARM, targetVolume, 0) }
    }

    /** Still ringing after [Alarm.escalateAfter]: climb to the speaker limit, buzz harder, flash. */
    private suspend fun escalate() {
        val ringing = state.value ?: return
        if (ringing.escalated || ringing.wakeCheck) return
        _state.value = ringing.copy(escalated = true)
        rampLevel = 1f
        applyVolume()
        if (ringing.alarm.vibrate && missionLevel == 1f) startVibration("rapid")
        if (missionLevel == 1f) startTorch()
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_ALARM)
        val step = maxOf(1, (max * 0.06f).roundToInt())
        while (targetVolume in 1 until capVolume) {
            setStreamTarget(minOf(capVolume, targetVolume + step))
            delay(4_000)
        }
    }

    private fun startTorch() {
        if (torch == null) torch = Torch(this)
        torch?.start(scope)
    }

    // --- Vibration & speech ------------------------------------------------------------------

    private fun startVibration(pattern: String) {
        vibrationPattern = pattern
        val v = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION") getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        vibrator = v
        val effect = VibrationEffect.createWaveform(vibrationTimings(pattern), 0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            v.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM))
        } else {
            @Suppress("DEPRECATION")
            v.vibrate(effect, AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build())
        }
    }

    private fun startSpeaking(alarm: Alarm) {
        tts = TextToSpeech(this) { status ->
            if (status != TextToSpeech.SUCCESS) return@TextToSpeech
            val engine = tts ?: return@TextToSpeech
            engine.language = Locale.getDefault()
            // Apexy is offline: only use a voice that is installed on the phone.
            runCatching {
                engine.voices
                    ?.filter { !it.isNetworkConnectionRequired && it.locale.language == Locale.getDefault().language }
                    ?.maxByOrNull { it.quality }
                    ?.let { engine.voice = it }
            }
            engine.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build())
            ringJobs += scope.launch {
                delay(5_000)
                while (isActive) {
                    val now = java.time.LocalDateTime.now()
                    val text = buildString {
                        append(greeting(now.hour)).append(". ")
                        append("It's ${formatTime(this@AlarmService, now.hour, now.minute)}, ")
                        append(now.format(java.time.format.DateTimeFormatter.ofPattern("EEEE, d MMMM"))).append(".")
                        if (alarm.label.isNotBlank()) append(" ${alarm.label}.")
                        if (alarm.speakMessage.isNotBlank()) append(" ${alarm.speakMessage}")
                    }
                    duckLevel = 0.2f; applyVolume()
                    engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "label")
                    delay(SPEAK_MS + text.length * 60L)
                    duckLevel = 1f; applyVolume()
                    delay(alarm.speakEvery.coerceAtLeast(15) * 1000L)
                }
            }
        }
    }

    private fun stopOutputs() {
        ringJobs.forEach { it.cancel() }
        ringJobs.clear()
        player?.runCatching { stop(); release() }
        player = null
        enhancer?.release()
        enhancer = null
        vibrator?.cancel()
        torch?.stop()
        torch = null
        tts?.shutdown()
        tts = null
        focus?.let { audio.abandonAudioFocusRequest(it) }
        if (savedVolume >= 0) runCatching { audio.setStreamVolume(AudioManager.STREAM_ALARM, savedVolume, 0) }
        savedVolume = -1
        targetVolume = -1
        baseVolume = -1
        capVolume = -1
        idleBoosts = 0
        duckLevel = 1f
        missionLevel = 1f
        vibrationPattern = null
        wakeLock?.takeIf { it.isHeld }?.release()
    }

    // --- Notification & screen ---------------------------------------------------------------

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "apexy:ringing")
            .apply { acquire(TIMEOUT_MS + 60_000) }
    }

    private fun ringingScreenIntent(): PendingIntent = PendingIntent.getActivity(
        this, 1, Intent(this, RingingActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun launchRingingScreen() {
        // Works when the app is in front; otherwise the full-screen intent takes over.
        runCatching { startActivity(Intent(this, RingingActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    private fun buildNotification(ringing: Ringing?, quiet: Boolean = false): Notification {
        val alarm = ringing?.alarm
        val builder = NotificationCompat.Builder(this, if (quiet) Notifications.CHANNEL_RINGING_QUIET else Notifications.CHANNEL_RINGING)
            .setSmallIcon(R.drawable.ic_stat_apexy)
            .setContentTitle(
                when {
                    ringing?.wakeCheck == true -> "Are you awake?"
                    alarm != null -> formatTime(this, alarm.hour, alarm.minute)
                    else -> "Alarm"
                },
            )
            .setContentText(
                when {
                    ringing?.wakeCheck == true -> "Tap to confirm, or the alarm rings again"
                    else -> alarm?.label?.ifBlank { "Time to wake up" } ?: "Starting…"
                },
            )
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setContentIntent(ringingScreenIntent())
        if (!quiet) builder.setFullScreenIntent(ringingScreenIntent(), true)
        if (ringing?.canSnooze == true && !ringing.alarm.snoozeMission) {
            builder.addAction(0, "Snooze", serviceAction(ACTION_SNOOZE, 2))
        }
        if (ringing?.wakeCheck == true) {
            builder.addAction(0, "I'm awake", serviceAction(ACTION_CHECK_PASS, 4))
        } else if (ringing != null && ringing.alarm.missions.isBlank()) {
            builder.addAction(0, "Dismiss", serviceAction(ACTION_DISMISS, 3))
        }
        return builder.build()
    }

    private fun serviceAction(action: String, code: Int) = PendingIntent.getService(
        this, code, Intent(this, AlarmService::class.java).setAction(action),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun startForegroundCompat(notification: Notification, id: Int = Notifications.ID_RINGING) {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0
        ServiceCompat.startForeground(this, id, notification, type)
    }

    override fun onDestroy() {
        stopOutputs()
        _state.value = null
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_RING = "com.apexy.alarm.RING"
        const val ACTION_SNOOZE = "com.apexy.alarm.SNOOZE"
        const val ACTION_DISMISS = "com.apexy.alarm.DISMISS"
        const val ACTION_SCREEN_SHOWN = "com.apexy.alarm.SCREEN_SHOWN"
        const val ACTION_SCREEN_HIDDEN = "com.apexy.alarm.SCREEN_HIDDEN"
        const val ACTION_MISSION_START = "com.apexy.alarm.MISSION_START"
        const val ACTION_MISSION_IDLE = "com.apexy.alarm.MISSION_IDLE"
        const val EXTRA_ID = "alarm_id"
        const val EXTRA_PREVIEW = "preview"
        const val EXTRA_WAKE_CHECK = "wake_check"
        const val EXTRA_MISSION_COUNT = "mission_count"
        const val EXTRA_MISSION_MS = "mission_ms"
        const val EXTRA_ESCAPES = "escapes"
        const val ACTION_WAKE_CHECK = "com.apexy.alarm.WAKE_CHECK"
        const val ACTION_CHECK_PASS = "com.apexy.alarm.CHECK_PASS"
        private const val TIMEOUT_MS = 30 * 60_000L
        private const val PREVIEW_MS = 3 * 60_000L
        private const val SPEAK_MS = 2_000L

        val VIBRATION_PATTERNS = mapOf(
            "heartbeat" to longArrayOf(0, 150, 120, 150, 900),
            "pulse" to longArrayOf(0, 500, 500),
            "rapid" to longArrayOf(0, 120, 120),
            "long" to longArrayOf(0, 1400, 600),
            "sos" to longArrayOf(0, 150, 150, 150, 150, 150, 400, 450, 150, 450, 150, 450, 400, 150, 150, 150, 150, 150, 1200),
            "waltz" to longArrayOf(0, 400, 200, 150, 200, 150, 700),
            "staccato" to longArrayOf(0, 60, 80, 60, 80, 60, 80, 60, 600),
            "build" to longArrayOf(0, 80, 500, 160, 400, 260, 300, 400, 200, 600, 600),
        )

        /** Built-in pattern name, or "custom:" followed by comma-separated on/off milliseconds. */
        fun vibrationTimings(pattern: String): LongArray {
            if (pattern.startsWith(CUSTOM_VIBRATION)) {
                val parts = pattern.removePrefix(CUSTOM_VIBRATION).split(',').mapNotNull { it.trim().toLongOrNull()?.coerceIn(0, 5_000) }
                if (parts.size >= 2 && parts.any { it > 0 }) return (listOf(0L) + parts).toLongArray()
            }
            return VIBRATION_PATTERNS[pattern] ?: VIBRATION_PATTERNS.getValue("heartbeat")
        }
        const val CUSTOM_VIBRATION = "custom:"

        /** Volume multiplier [t] of the way (0..1) through the ramp. */
        fun rampAt(alarm: Alarm, t: Float): Float {
            val start = alarm.rampStart.coerceIn(0, 90) / 100f
            val x = t.coerceIn(0f, 1f)
            val shaped = when (alarm.rampCurve) {
                Alarm.RAMP_GENTLE -> x * x * x
                Alarm.RAMP_QUICK -> 1f - (1f - x) * (1f - x) * (1f - x)
                else -> x
            }
            return (start + (1f - start) * shaped).coerceIn(0.01f, 1f)
        }

        private fun greeting(hour: Int) = when (hour) { in 4..11 -> "Good morning"; in 12..16 -> "Good afternoon"; else -> "Good evening" }

        private val _state = MutableStateFlow<Ringing?>(null)
        val state: StateFlow<Ringing?> = _state

        fun ringIntent(context: Context, id: Long, preview: Boolean = false) =
            Intent(context, AlarmService::class.java).setAction(ACTION_RING)
                .putExtra(EXTRA_ID, id).putExtra(EXTRA_PREVIEW, preview)

        fun wakeCheckIntent(context: Context, id: Long) =
            Intent(context, AlarmService::class.java).setAction(ACTION_WAKE_CHECK).putExtra(EXTRA_ID, id)

        fun send(context: Context, action: String) =
            context.startService(Intent(context, AlarmService::class.java).setAction(action))

        fun dismissAfterMissions(context: Context, stats: MissionStats) = context.startService(
            Intent(context, AlarmService::class.java).setAction(ACTION_DISMISS)
                .putExtra(EXTRA_MISSION_COUNT, stats.count)
                .putExtra(EXTRA_MISSION_MS, stats.ms)
                .putExtra(EXTRA_ESCAPES, stats.escapes),
        )
    }
}
