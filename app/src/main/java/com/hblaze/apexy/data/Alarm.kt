package com.hblaze.apexy.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/** Days are a bitmask: Monday = bit 0 … Sunday = bit 6. Zero means a one-off alarm. */
@Entity(tableName = "alarms")
data class Alarm(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val hour: Int = 7,
    val minute: Int = 0,
    val days: Int = 0,
    val enabled: Boolean = true,
    val label: String = "",
    val sound: String = "builtin:radiant",
    val volume: Int = 80,
    val rampSeconds: Int = 30,
    val vibrate: Boolean = true,
    val vibrationPattern: String = "heartbeat",
    val extraLoud: Boolean = false,
    val speakLabel: Boolean = false,
    val snoozeEnabled: Boolean = true,
    val snoozeMinutes: Int = 5,
    /** -1 means unlimited. */
    val maxSnoozes: Int = 3,
    /** Serialized mission list; filled in by the missions module. */
    val missions: String = "",
    val wakeUpCheck: Boolean = false,
    val wakeUpCheckMinutes: Int = 3,
    /** Seconds the user has to answer a wake-up check before the alarm rings again. */
    @ColumnInfo(defaultValue = "60") val wakeUpCheckResponse: Int = 60,
    /** When a pending wake-up check fires; 0 when none. Stored so reboots don't lose it. */
    @ColumnInfo(defaultValue = "0") val wakeCheckAt: Long = 0,
    /** Quick alarms delete themselves once dismissed. */
    val isQuick: Boolean = false,
    /** Occurrences before this instant are skipped ("skip next"). */
    val skipUntil: Long = 0,
    val snoozedUntil: Long = 0,
    val snoozeCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    /** ARGB accent for this alarm's card and ringing screen; 0 follows the app colour. */
    @ColumnInfo(defaultValue = "0") val accentColor: Long = 0,
    // --- v1.3 ---
    /** Volume the ramp starts from, as a percent of [volume]. */
    @ColumnInfo(defaultValue = "5") val rampStart: Int = 5,
    /** "linear", "gentle" (slow start, fast finish) or "quick" (fast start, slow finish). */
    @ColumnInfo(defaultValue = "linear") val rampCurve: String = RAMP_LINEAR,
    /** Not dismissed after [escalateAfter] minutes: louder, faster vibration, flashing. */
    @ColumnInfo(defaultValue = "0") val escalate: Boolean = false,
    @ColumnInfo(defaultValue = "2") val escalateAfter: Int = 2,
    /** Speaker safety limit, in percent of the phone's max: escalation and idle boosts never go above it. */
    @ColumnInfo(defaultValue = "85") val volumeCap: Int = 85,
    /** Extra words read aloud after the time. */
    @ColumnInfo(defaultValue = "") val speakMessage: String = "",
    @ColumnInfo(defaultValue = "45") val speakEvery: Int = 45,
    /** "off", "torch", "screen" or "both". */
    @ColumnInfo(defaultValue = "off") val flash: String = FLASH_OFF,
    /** "all" runs every mission in order; "random" draws [randomCount] of them each time. */
    @ColumnInfo(defaultValue = "all") val missionOrder: String = ORDER_ALL,
    @ColumnInfo(defaultValue = "1") val randomCount: Int = 1,
    /** Alarm sound during missions: "normal", "quiet" or "off". */
    @ColumnInfo(defaultValue = "quiet") val duringMission: String = DURING_QUIET,
    /** Seconds without touching a mission before the alarm gets loud again. */
    @ColumnInfo(defaultValue = "30") val idleSeconds: Int = 30,
    /** Each snooze is a minute shorter than the last (never under one minute). */
    @ColumnInfo(defaultValue = "0") val snoozeShrink: Boolean = false,
    /** Snoozing needs a quick sum first. */
    @ColumnInfo(defaultValue = "0") val snoozeMission: Boolean = false,
    /** After dismissing, show the day ahead: calendar, sleep, next alarm. */
    @ColumnInfo(defaultValue = "1") val morningCard: Boolean = true,
) {
    val isRepeating get() = days != 0
    fun hasDay(dayIndex: Int) = days and (1 shl dayIndex) != 0

    /** How long the next snooze lasts, after any shrinking. */
    val nextSnoozeMinutes get() = if (snoozeShrink) (snoozeMinutes - snoozeCount).coerceAtLeast(1) else snoozeMinutes

    companion object {
        const val RAMP_LINEAR = "linear"
        const val RAMP_GENTLE = "gentle"
        const val RAMP_QUICK = "quick"
        const val FLASH_OFF = "off"
        const val FLASH_TORCH = "torch"
        const val FLASH_SCREEN = "screen"
        const val FLASH_BOTH = "both"
        const val ORDER_ALL = "all"
        const val ORDER_RANDOM = "random"
        const val DURING_NORMAL = "normal"
        const val DURING_QUIET = "quiet"
        const val DURING_OFF = "off"
    }
}

/** One ring of an alarm, used for history and statistics. */
@Entity(tableName = "alarm_events")
data class AlarmEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val alarmId: Long,
    val label: String,
    val ringAt: Long,
    val endedAt: Long = 0,
    val snoozes: Int = 0,
    /** "dismissed", "snoozed" or "missed" for rings; "awake" or "asleep" for wake-up checks. */
    val outcome: String = "",
    /** "ring" or "check". */
    @ColumnInfo(defaultValue = "ring") val kind: String = KIND_RING,
    @ColumnInfo(defaultValue = "0") val missionCount: Int = 0,
    /** Time from starting the missions to finishing them. */
    @ColumnInfo(defaultValue = "0") val missionMs: Long = 0,
    @ColumnInfo(defaultValue = "0") val escapes: Int = 0,
) {
    companion object {
        const val KIND_RING = "ring"
        const val KIND_CHECK = "check"
    }
}
