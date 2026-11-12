package com.apexy.alarm.sleep

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.apexy.alarm.ApexyApp
import com.apexy.alarm.R
import com.apexy.alarm.data.SleepSession
import com.apexy.alarm.data.SnoreClip
import com.apexy.alarm.ui.MainActivity
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
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sqrt

data class SleepState(
    val tracking: Boolean = false,
    val sessionId: Long = 0,
    val startedAt: Long = 0,
    val playing: Boolean = false,
    val mix: Map<String, Float> = emptyMap(),
    /** When the sound timer stops the sounds; 0 = no timer. */
    val timerEndsAt: Long = 0,
    val micOn: Boolean = false,
    val snoreModel: Boolean = false,
    val snoreClips: Int = 0,
) {
    val active get() = tracking || playing
}

/** Owns the night: sleep sounds with their timer, motion and sound tracking, and snore recording. */
class SleepService : Service(), SensorEventListener {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val app get() = application as ApexyApp
    private val player = Ambient.Player()
    @Volatile private var soundGen = 0
    private var timerJob: Job? = null
    private var minuteJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    // Motion: mean deviation of acceleration magnitude from its slow average, per minute.
    private var gravity = SensorManager.GRAVITY_EARTH
    @Volatile private var motionSum = 0.0
    @Volatile private var motionCount = 0

    // Sound
    private var recorder: AudioRecord? = null
    private var micThread: Thread? = null
    @Volatile private var micRunning = false
    @Volatile private var energySum = 0.0
    @Volatile private var energyCount = 0
    @Volatile private var snoredThisMinute = false
    private var detector: SnoreDetector? = null

    private val samples = mutableListOf<MinuteSample>()
    private var snoreMinutes = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val mix = Ambient.parseMix(intent.getStringExtra(EXTRA_MIX).orEmpty())
                val timer = intent.getIntExtra(EXTRA_TIMER, 0)
                val track = intent.getBooleanExtra(EXTRA_TRACK, true)
                val snore = intent.getBooleanExtra(EXTRA_SNORE, true)
                start(mix, timer, track, snore)
            }
            ACTION_SET_MIX -> setMix(Ambient.parseMix(intent.getStringExtra(EXTRA_MIX).orEmpty()))
            ACTION_STOP_SOUNDS -> stopSounds(fade = true)
            ACTION_ALARM_RINGING -> stopSounds(fade = false)
            ACTION_STOP, ACTION_ALARM_DISMISSED -> scope.launch { finish() }
            else -> if (!state.value.active) stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun start(mix: Map<String, Float>, timerMinutes: Int, track: Boolean, snore: Boolean) {
        val micOk = track && ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        var type = ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
        if (micOk && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification(), type)

        if (mix.isNotEmpty()) {
            soundGen++
            player.start(mix)
        }
        val timerEnds = if (timerMinutes > 0 && mix.isNotEmpty()) System.currentTimeMillis() + timerMinutes * 60_000L else 0L
        _state.value = _state.value.copy(playing = mix.isNotEmpty(), mix = mix, timerEndsAt = timerEnds)
        if (timerEnds > 0) startTimer(timerEnds)

        if (track && !_state.value.tracking) scope.launch {
            val now = System.currentTimeMillis()
            val id = app.db.sleep().insert(SleepSession(startedAt = now, goalMinutes = sleepGoal(), sounds = mix.keys.joinToString(",")))
            acquireWakeLock()
            startMotion()
            if (micOk) startMic(snore)
            _state.value = _state.value.copy(tracking = true, sessionId = id, startedAt = now, micOn = micOk, snoreModel = detector?.available == true)
            minuteJob = scope.launch { minuteLoop(id) }
            refreshNotification()
        }
        refreshNotification()
    }

    private suspend fun sleepGoal(): Int = runCatching {
        app.settings.settings.first().sleepGoalMinutes
    }.getOrDefault(480)

    private fun setMix(mix: Map<String, Float>) {
        if (mix.isEmpty()) return stopSounds(fade = true)
        // Cancels a stop that is still fading out, so a tap right after "Stop" isn't swallowed.
        soundGen++
        player.start(mix)
        val before = _state.value
        _state.value = before.copy(playing = true, mix = mix)
        // Slider drags arrive many times a second; only re-post the notification when its text changes.
        if (!before.playing || before.mix.keys != mix.keys) refreshNotification()
    }

    private fun startTimer(endsAt: Long) {
        timerJob?.cancel()
        timerJob = scope.launch {
            delay((endsAt - System.currentTimeMillis() - FADE_MS).coerceAtLeast(0))
            stopSounds(fade = true, fadeMs = FADE_MS)
        }
    }

    /**
     * The screen shows "off" straight away; the audio then fades out ([fadeMs] for the sleep timer,
     * a short tail when the user taps Stop) unless new sounds were picked meanwhile.
     */
    private fun stopSounds(fade: Boolean, fadeMs: Long = STOP_FADE_MS) {
        timerJob?.cancel()
        val gen = ++soundGen
        _state.value = _state.value.copy(playing = false, timerEndsAt = 0)
        refreshNotification()
        scope.launch {
            if (fade && player.isPlaying) {
                player.fadeTo(0f, fadeMs.toInt())
                delay(fadeMs)
            }
            if (gen != soundGen) return@launch
            player.stop()
            if (!_state.value.tracking) shutDown()
        }
    }

    // --- Tracking ----------------------------------------------------------------------------

    private fun startMotion() {
        val sm = getSystemService(SensorManager::class.java)
        sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let { sm.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
    }

    override fun onSensorChanged(event: SensorEvent) {
        val v = event.values
        val mag = sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2])
        gravity = 0.98f * gravity + 0.02f * mag
        motionSum += abs(mag - gravity)
        motionCount++
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    @SuppressLint("MissingPermission") // checked by the caller
    private fun startMic(detectSnoring: Boolean) {
        val rate = SnoreDetector.SAMPLE_RATE
        val minBuf = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val rec = runCatching {
            AudioRecord(MediaRecorder.AudioSource.MIC, rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(minBuf, rate))
        }.getOrNull()?.takeIf { it.state == AudioRecord.STATE_INITIALIZED } ?: return
        recorder = rec
        if (detectSnoring) detector = SnoreDetector(this)
        micRunning = true
        micThread = Thread({ micLoop(rec) }, "apexy-sleep-mic").apply { start() }
    }

    /** Reads 100 ms chunks; once a second asks YAMNet about the last second if it was loud enough. */
    private fun micLoop(rec: AudioRecord) {
        val rate = SnoreDetector.SAMPLE_RATE
        val chunk = ShortArray(rate / 10)
        val ring = ShortArray(rate * 3) // last 3 s, for the clip pre-roll and the classifier window
        var ringPos = 0
        var chunksSinceCheck = 0
        var clip: MutableList<Short>? = null
        var clipStart = 0L
        var lastSnoreAt = 0L
        var clipPeak = 0f
        val maxClipSamples = rate * 30
        rec.startRecording()
        try {
            while (micRunning) {
                val n = rec.read(chunk, 0, chunk.size)
                if (n <= 0) continue
                var sq = 0.0
                for (i in 0 until n) {
                    val s = chunk[i]
                    sq += s.toDouble() * s
                    ring[ringPos] = s; ringPos = (ringPos + 1) % ring.size
                }
                val rms = sqrt(sq / n) / Short.MAX_VALUE
                energySum += rms * rms; energyCount++
                clip?.let { for (i in 0 until n) it.add(chunk[i]) }

                if (++chunksSinceCheck >= 10) {
                    chunksSinceCheck = 0
                    val det = detector
                    // Quiet seconds can't be snoring: skip the model to save battery.
                    if (det != null && 20 * log10(rms.coerceAtLeast(1e-9)) > -52) {
                        val window = FloatArray(15_600) { k -> ring[(ringPos - 15_600 + k + ring.size) % ring.size] / 32768f }
                        val score = det.snoreScore(window)
                        val now = System.currentTimeMillis()
                        if (score >= SNORE_THRESHOLD) {
                            snoredThisMinute = true
                            lastSnoreAt = now
                            clipPeak = maxOf(clipPeak, score)
                            if (clip == null && _state.value.snoreClips < MAX_CLIPS) {
                                // Start the clip two seconds back so the first snore is audible.
                                clip = MutableList(rate * 2) { k -> ring[(ringPos - rate * 2 + k + ring.size) % ring.size] }
                                clipStart = now - 2_000
                            }
                        }
                    }
                    val open = clip
                    if (open != null && (System.currentTimeMillis() - lastSnoreAt > 4_000 || open.size >= maxClipSamples)) {
                        saveClip(open.toShortArray(), clipStart, clipPeak)
                        clip = null
                        clipPeak = 0f
                    }
                }
            }
        } finally {
            runCatching { rec.stop() }
            rec.release()
        }
    }

    private fun saveClip(pcm: ShortArray, startedAt: Long, score: Float) {
        val sessionId = _state.value.sessionId
        val file = File(File(filesDir, "snores/$sessionId"), "$startedAt.wav")
        SnoreDetector.writeWav(file, pcm)
        _state.value = _state.value.copy(snoreClips = _state.value.snoreClips + 1)
        scope.launch {
            app.db.sleep().insertClip(
                SnoreClip(sessionId = sessionId, startedAt = startedAt, durationMs = pcm.size * 1000L / SnoreDetector.SAMPLE_RATE, path = file.absolutePath, score = score),
            )
        }
    }

    private suspend fun minuteLoop(sessionId: Long) {
        while (scope.isActive) {
            delay(60_000)
            val activity = if (motionCount > 0) (motionSum / motionCount).toFloat() else 0f
            motionSum = 0.0; motionCount = 0
            // Rough dB: microphones aren't calibrated, so +90 just maps full scale to a familiar range.
            val db = if (energyCount > 0) (10 * log10((energySum / energyCount).coerceAtLeast(1e-12)) + 90).toFloat().coerceAtLeast(0f) else 0f
            energySum = 0.0; energyCount = 0
            if (snoredThisMinute) snoreMinutes++
            snoredThisMinute = false
            samples += MinuteSample(activity, db)
            // Save as we go so a crash or reboot keeps most of the night.
            if (samples.size % 5 == 0) persist(sessionId, ended = false)
            // Unplugged and nearly empty: stop tracking so there is still power to ring the alarm.
            val battery = BatteryStatus.read(this)
            if (!battery.charging && battery.percent <= BatteryStatus.STOP_AT) {
                notifyBatteryStop(battery.percent)
                scope.launch { finish() }
                return
            }
        }
    }

    private fun notifyBatteryStop(percent: Int) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL_BATTERY, "Sleep battery warnings", NotificationManager.IMPORTANCE_DEFAULT).apply {
            setSound(null, null) // never wake the sleeper over this
        })
        nm.notify(
            BATTERY_NOTIFICATION_ID,
            NotificationCompat.Builder(this, CHANNEL_BATTERY)
                .setSmallIcon(R.drawable.ic_stat_apexy)
                .setContentTitle("Sleep tracking stopped at $percent%")
                .setContentText("Saving battery so your alarm can still ring. Charge your phone while tracking.")
                .setAutoCancel(true)
                .build(),
        )
    }

    private suspend fun persist(sessionId: Long, ended: Boolean) {
        val dao = app.db.sleep()
        val session = dao.get(sessionId) ?: return
        dao.update(
            session.copy(
                samples = SleepAnalysis.encode(samples),
                snoreMinutes = snoreMinutes,
                endedAt = if (ended) System.currentTimeMillis() else 0,
            ),
        )
    }

    private suspend fun finish() {
        val s = _state.value
        player.stop()
        timerJob?.cancel()
        minuteJob?.cancel()
        stopTracking()
        if (s.tracking) {
            if (samples.size < MIN_MINUTES) {
                // Too short to say anything about: drop it rather than clutter the history.
                app.db.sleep().get(s.sessionId)?.let { app.db.sleep().delete(it) }
                app.db.sleep().deleteClips(s.sessionId)
                File(filesDir, "snores/${s.sessionId}").deleteRecursively()
                lastFinishedSession.value = -1
            } else {
                persist(s.sessionId, ended = true)
                lastFinishedSession.value = s.sessionId
            }
        }
        shutDown()
    }

    private fun stopTracking() {
        getSystemService(SensorManager::class.java).unregisterListener(this)
        micRunning = false
        micThread?.join(1_500)
        micThread = null
        recorder = null
        detector?.close()
        detector = null
        wakeLock?.takeIf { it.isHeld }?.release()
    }

    private fun shutDown() {
        _state.value = SleepState()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun acquireWakeLock() {
        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "apexy:sleep")
            .apply { acquire(14 * 3_600_000L) }
    }

    // --- Notification -------------------------------------------------------------------------

    private fun refreshNotification() {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification())
    }

    private fun notification(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Sleep", NotificationManager.IMPORTANCE_LOW))
        val s = _state.value
        val open = PendingIntent.getActivity(
            this, 10, Intent(this, MainActivity::class.java).putExtra(MainActivity.EXTRA_TAB, "sleep"),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val b = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_apexy)
            .setContentTitle(if (s.tracking) "Tracking your sleep" else "Sleep sounds")
            .setContentText(
                listOfNotNull(
                    s.mix.keys.joinToString(", ") { id -> Ambient.sounds.first { it.id == id }.name }.takeIf { s.playing },
                    "snoring detection on".takeIf { s.snoreModel },
                ).joinToString(" · ").ifBlank { "Sweet dreams" },
            )
            .setOngoing(true)
            .setContentIntent(open)
        if (s.playing) b.addAction(0, "Stop sounds", action(ACTION_STOP_SOUNDS, 11))
        b.addAction(0, if (s.tracking) "I'm awake" else "Stop", action(ACTION_STOP, 12))
        return b.build()
    }

    private fun action(name: String, code: Int) = PendingIntent.getService(
        this, code, Intent(this, SleepService::class.java).setAction(name),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    override fun onDestroy() {
        player.stop()
        stopTracking()
        scope.cancel()
        _state.value = SleepState()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.apexy.alarm.sleep.START"
        const val ACTION_SET_MIX = "com.apexy.alarm.sleep.SET_MIX"
        const val ACTION_STOP_SOUNDS = "com.apexy.alarm.sleep.STOP_SOUNDS"
        const val ACTION_STOP = "com.apexy.alarm.sleep.STOP"
        const val ACTION_ALARM_RINGING = "com.apexy.alarm.sleep.ALARM_RINGING"
        const val ACTION_ALARM_DISMISSED = "com.apexy.alarm.sleep.ALARM_DISMISSED"
        const val EXTRA_MIX = "mix"
        const val EXTRA_TIMER = "timer"
        const val EXTRA_TRACK = "track"
        const val EXTRA_SNORE = "snore"
        private const val CHANNEL = "sleep"
        private const val CHANNEL_BATTERY = "sleep_battery"
        private const val BATTERY_NOTIFICATION_ID = 2002
        private const val NOTIFICATION_ID = 2001
        private const val FADE_MS = 8_000L
        private const val STOP_FADE_MS = 400L
        private const val SNORE_THRESHOLD = 0.3f
        private const val MAX_CLIPS = 40
        private const val MIN_MINUTES = 5

        private val _state = MutableStateFlow(SleepState())
        val state: StateFlow<SleepState> = _state

        /** Set when a night ends, so the Sleep tab can open its report. */
        val lastFinishedSession = MutableStateFlow(0L)

        fun start(context: Context, mix: String, timerMinutes: Int, track: Boolean, snore: Boolean) =
            ContextCompat.startForegroundService(
                context,
                Intent(context, SleepService::class.java).setAction(ACTION_START)
                    .putExtra(EXTRA_MIX, mix).putExtra(EXTRA_TIMER, timerMinutes)
                    .putExtra(EXTRA_TRACK, track).putExtra(EXTRA_SNORE, snore),
            )

        fun send(context: Context, action: String, mix: String? = null) {
            if (!_state.value.active && action != ACTION_START) return
            context.startService(Intent(context, SleepService::class.java).setAction(action).apply { mix?.let { putExtra(EXTRA_MIX, it) } })
        }
    }
}
