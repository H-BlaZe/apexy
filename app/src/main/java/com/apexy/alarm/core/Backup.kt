package com.apexy.alarm.core

import android.content.Context
import android.net.Uri
import android.util.Base64
import androidx.room.withTransaction
import com.apexy.alarm.ApexyApp
import com.apexy.alarm.data.Alarm
import com.apexy.alarm.data.AlarmEvent
import com.apexy.alarm.data.AppSettings
import com.apexy.alarm.data.SleepSession
import com.apexy.alarm.data.ThemeMode
import com.apexy.alarm.missions.MissionCodec
import com.apexy.alarm.missions.MissionType
import com.apexy.alarm.sleep.Bedtime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * One JSON file with alarms (including registered QR codes and reference photos), settings,
 * alarm history and sleep nights. Snore recordings stay on the phone: they're large and private.
 */
object Backup {
    private const val FORMAT = 1

    data class Counts(val alarms: Int, val events: Int, val nights: Int)

    suspend fun export(context: Context, uri: Uri): Counts = withContext(Dispatchers.IO) {
        val app = context.applicationContext as ApexyApp
        val alarms = app.db.alarms().getAll()
        val events = app.db.events().observeAll().first()
        val nights = app.db.sleep().observeNights().first()
        val root = JSONObject()
            .put("app", "apexy")
            .put("format", FORMAT)
            .put("exportedAt", System.currentTimeMillis())
            .put("settings", settingsToJson(app.settings.settings.first()))
            .put("alarms", JSONArray(alarms.map { alarmToJson(it) }))
            .put("events", JSONArray(events.map { eventToJson(it) }))
            .put("nights", JSONArray(nights.map { nightToJson(it) }))
        context.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(root.toString().toByteArray()) }
        Counts(alarms.size, events.size, nights.size)
    }

    /** Replaces alarms, settings, history and nights with the backup's. */
    suspend fun restore(context: Context, uri: Uri): Counts = withContext(Dispatchers.IO) {
        val app = context.applicationContext as ApexyApp
        val text = context.contentResolver.openInputStream(uri)!!.use { it.readBytes().decodeToString() }
        val root = JSONObject(text)
        require(root.optString("app") == "apexy") { "This isn't an Apexy backup." }
        require(root.optInt("format") <= FORMAT) { "This backup is from a newer version of Apexy." }

        val alarms = root.optJSONArray("alarms").objects().map { alarmFromJson(context, it) }
        val events = root.optJSONArray("events").objects().map { eventFromJson(it) }
        val nights = root.optJSONArray("nights").objects().map { nightFromJson(it) }

        val db = app.db
        db.alarms().getAll().forEach { app.scheduler.cancel(it.id); app.scheduler.cancelWakeCheck(it.id) }
        // Everything is parsed above, and the swap is one transaction: a bad file never leaves you half-restored.
        db.withTransaction {
            db.alarms().clear()
            db.events().clear()
            db.sleep().clearClips()
            db.sleep().clearNights()
            // Alarm ids change on insert; history follows them through this map.
            val ids = mutableMapOf<Long, Long>()
            alarms.forEach { (oldId, alarm) -> ids[oldId] = db.alarms().upsert(alarm.copy(id = 0)) }
            events.forEach { e -> db.events().insert(e.copy(id = 0, alarmId = ids[e.alarmId] ?: e.alarmId)) }
            nights.forEach { db.sleep().insert(it.copy(id = 0)) }
        }
        File(context.filesDir, "snores").deleteRecursively()
        root.optJSONObject("settings")?.let { restoreSettings(app, it) }
        app.scheduler.rescheduleAll()
        Bedtime.reschedule(context)
        Counts(alarms.size, events.size, nights.size)
    }

    // --- Alarms ------------------------------------------------------------------------------

    private fun alarmToJson(a: Alarm) = JSONObject()
        .put("id", a.id).put("hour", a.hour).put("minute", a.minute).put("days", a.days).put("enabled", a.enabled)
        .put("label", a.label).put("sound", a.sound).put("volume", a.volume).put("rampSeconds", a.rampSeconds)
        .put("vibrate", a.vibrate).put("vibrationPattern", a.vibrationPattern).put("extraLoud", a.extraLoud)
        .put("speakLabel", a.speakLabel).put("snoozeEnabled", a.snoozeEnabled).put("snoozeMinutes", a.snoozeMinutes)
        .put("maxSnoozes", a.maxSnoozes).put("missions", missionsWithPhotos(a.missions))
        .put("wakeUpCheck", a.wakeUpCheck).put("wakeUpCheckMinutes", a.wakeUpCheckMinutes)
        .put("wakeUpCheckResponse", a.wakeUpCheckResponse).put("isQuick", a.isQuick)
        .put("skipUntil", a.skipUntil).put("createdAt", a.createdAt).put("accentColor", a.accentColor)
        .put("snoozedUntil", a.snoozedUntil).put("snoozeCount", a.snoozeCount).put("wakeCheckAt", a.wakeCheckAt)
        .put("rampStart", a.rampStart).put("rampCurve", a.rampCurve).put("escalate", a.escalate)
        .put("escalateAfter", a.escalateAfter).put("volumeCap", a.volumeCap).put("speakMessage", a.speakMessage)
        .put("speakEvery", a.speakEvery).put("flash", a.flash).put("missionOrder", a.missionOrder)
        .put("randomCount", a.randomCount).put("duringMission", a.duringMission).put("idleSeconds", a.idleSeconds)
        .put("snoozeShrink", a.snoozeShrink).put("snoozeMission", a.snoozeMission).put("morningCard", a.morningCard)

    private fun alarmFromJson(context: Context, o: JSONObject): Pair<Long, Alarm> = o.getLong("id") to Alarm(
        hour = o.getInt("hour"), minute = o.getInt("minute"), days = o.optInt("days"), enabled = o.optBoolean("enabled", true),
        label = o.optString("label"), sound = o.optString("sound", "builtin:radiant"), volume = o.optInt("volume", 80),
        rampSeconds = o.optInt("rampSeconds", 30), vibrate = o.optBoolean("vibrate", true),
        vibrationPattern = o.optString("vibrationPattern", "heartbeat"), extraLoud = o.optBoolean("extraLoud"),
        speakLabel = o.optBoolean("speakLabel"), snoozeEnabled = o.optBoolean("snoozeEnabled", true),
        snoozeMinutes = o.optInt("snoozeMinutes", 5), maxSnoozes = o.optInt("maxSnoozes", 3),
        missions = missionsFromBackup(context, o.optJSONArray("missions")),
        wakeUpCheck = o.optBoolean("wakeUpCheck"), wakeUpCheckMinutes = o.optInt("wakeUpCheckMinutes", 3),
        wakeUpCheckResponse = o.optInt("wakeUpCheckResponse", 60), isQuick = o.optBoolean("isQuick"),
        skipUntil = o.optLong("skipUntil"), createdAt = o.optLong("createdAt", System.currentTimeMillis()),
        accentColor = o.optLong("accentColor"),
        snoozedUntil = o.optLong("snoozedUntil"), snoozeCount = o.optInt("snoozeCount"), wakeCheckAt = o.optLong("wakeCheckAt"),
        rampStart = o.optInt("rampStart", 5), rampCurve = o.optString("rampCurve", Alarm.RAMP_LINEAR),
        escalate = o.optBoolean("escalate"), escalateAfter = o.optInt("escalateAfter", 2),
        volumeCap = o.optInt("volumeCap", 85), speakMessage = o.optString("speakMessage"),
        speakEvery = o.optInt("speakEvery", 45), flash = o.optString("flash", Alarm.FLASH_OFF),
        missionOrder = o.optString("missionOrder", Alarm.ORDER_ALL), randomCount = o.optInt("randomCount", 1),
        duringMission = o.optString("duringMission", Alarm.DURING_QUIET), idleSeconds = o.optInt("idleSeconds", 30),
        snoozeShrink = o.optBoolean("snoozeShrink"), snoozeMission = o.optBoolean("snoozeMission"),
        morningCard = o.optBoolean("morningCard", true),
    )

    /** Missions as JSON, with each reference photo embedded so a restore on a new phone still works. */
    private fun missionsWithPhotos(raw: String): JSONArray {
        val arr = if (raw.isBlank()) JSONArray() else JSONArray(raw)
        for (i in 0 until arr.length()) {
            val m = arr.getJSONObject(i)
            val path = m.optString("photo")
            if (m.optString("type") == MissionType.PHOTO.name && path.isNotBlank()) {
                File(path).takeIf { it.exists() }?.let { m.put("photoData", Base64.encodeToString(it.readBytes(), Base64.NO_WRAP)) }
            }
        }
        return arr
    }

    private fun missionsFromBackup(context: Context, arr: JSONArray?): String {
        if (arr == null || arr.length() == 0) return ""
        for (i in 0 until arr.length()) {
            val m = arr.getJSONObject(i)
            val data = m.optString("photoData")
            if (data.isNotBlank()) {
                val file = File(File(context.filesDir, "mission_photos").apply { mkdirs() }, "${UUID.randomUUID()}.jpg")
                file.writeBytes(Base64.decode(data, Base64.NO_WRAP))
                m.put("photo", file.absolutePath)
                m.remove("photoData")
            }
        }
        // Round-trip through the codec so anything malformed is dropped rather than stored.
        return MissionCodec.encode(MissionCodec.decode(arr.toString()))
    }

    // --- History & sleep ---------------------------------------------------------------------

    private fun eventToJson(e: AlarmEvent) = JSONObject()
        .put("alarmId", e.alarmId).put("label", e.label).put("ringAt", e.ringAt).put("endedAt", e.endedAt)
        .put("snoozes", e.snoozes).put("outcome", e.outcome).put("kind", e.kind)
        .put("missionCount", e.missionCount).put("missionMs", e.missionMs).put("escapes", e.escapes)

    private fun eventFromJson(o: JSONObject) = AlarmEvent(
        alarmId = o.getLong("alarmId"), label = o.optString("label"), ringAt = o.getLong("ringAt"),
        endedAt = o.optLong("endedAt"), snoozes = o.optInt("snoozes"), outcome = o.optString("outcome"),
        kind = o.optString("kind", AlarmEvent.KIND_RING), missionCount = o.optInt("missionCount"),
        missionMs = o.optLong("missionMs"), escapes = o.optInt("escapes"),
    )

    private fun nightToJson(n: SleepSession) = JSONObject()
        .put("startedAt", n.startedAt).put("endedAt", n.endedAt).put("samples", n.samples)
        .put("snoreMinutes", n.snoreMinutes).put("goalMinutes", n.goalMinutes).put("sounds", n.sounds)

    private fun nightFromJson(o: JSONObject) = SleepSession(
        startedAt = o.getLong("startedAt"), endedAt = o.getLong("endedAt"), samples = o.optString("samples"),
        snoreMinutes = o.optInt("snoreMinutes"), goalMinutes = o.optInt("goalMinutes", 480), sounds = o.optString("sounds"),
    )

    // --- Settings ----------------------------------------------------------------------------

    private fun settingsToJson(s: AppSettings) = JSONObject()
        .put("themeMode", s.themeMode.name).put("primaryColor", s.primaryColor)
        .put("preventChanges", s.preventChanges).put("lockWindowMinutes", s.lockWindowMinutes)
        .put("lockVolume", s.lockVolume).put("bringBack", s.bringBack)
        .put("bedtimeReminder", s.bedtimeReminder).put("sleepGoalMinutes", s.sleepGoalMinutes)
        .put("bedtimeLeadMinutes", s.bedtimeLeadMinutes).put("trackSleep", s.trackSleep)
        .put("detectSnoring", s.detectSnoring).put("soundTimerMinutes", s.soundTimerMinutes).put("soundMix", s.soundMix)
        .put("customPhrases", JSONArray(s.customPhrases))

    private suspend fun restoreSettings(app: ApexyApp, o: JSONObject) {
        val r = app.settings
        runCatching { r.setThemeMode(ThemeMode.valueOf(o.getString("themeMode"))) }
        if (o.has("primaryColor")) r.setPrimaryColor(o.getLong("primaryColor"))
        if (o.has("preventChanges")) r.setPreventChanges(o.getBoolean("preventChanges"))
        if (o.has("lockWindowMinutes")) r.setLockWindow(o.getInt("lockWindowMinutes"))
        if (o.has("lockVolume")) r.setLockVolume(o.getBoolean("lockVolume"))
        if (o.has("bringBack")) r.setBringBack(o.getBoolean("bringBack"))
        if (o.has("bedtimeReminder")) r.setBedtimeReminder(o.getBoolean("bedtimeReminder"))
        if (o.has("sleepGoalMinutes")) r.setSleepGoal(o.getInt("sleepGoalMinutes"))
        if (o.has("bedtimeLeadMinutes")) r.setBedtimeLead(o.getInt("bedtimeLeadMinutes"))
        if (o.has("trackSleep")) r.setTrackSleep(o.getBoolean("trackSleep"))
        if (o.has("detectSnoring")) r.setDetectSnoring(o.getBoolean("detectSnoring"))
        if (o.has("soundTimerMinutes")) r.setSoundTimer(o.getInt("soundTimerMinutes"))
        if (o.has("soundMix")) r.setSoundMix(o.getString("soundMix"))
        o.optJSONArray("customPhrases")?.let { a -> r.setCustomPhrases((0 until a.length()).map { a.getString(it) }) }
    }

    private fun JSONArray?.objects(): List<JSONObject> = if (this == null) emptyList() else (0 until length()).map { getJSONObject(it) }
}
