package com.hblaze.apexy.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val primaryColor: Long = 0xFF5B6CF0,
    /** Block editing, disabling, skipping or deleting an alarm shortly before it rings or while snoozed. */
    val preventChanges: Boolean = false,
    val lockWindowMinutes: Int = 60,
    /** Snap the alarm volume back up if it's turned down while ringing; swallow volume keys. */
    val lockVolume: Boolean = true,
    /** Reopen the ringing screen if the user leaves it. */
    val bringBack: Boolean = true,
    // --- Sleep ---
    val bedtimeReminder: Boolean = false,
    val sleepGoalMinutes: Int = 480,
    /** How long before bedtime the reminder fires. */
    val bedtimeLeadMinutes: Int = 15,
    val trackSleep: Boolean = true,
    val detectSnoring: Boolean = true,
    /** Sleep-sound timer; 0 = play until the alarm. */
    val soundTimerMinutes: Int = 30,
    /** Sound mix as "id:volume" pairs, comma-separated. */
    val soundMix: String = "rain:0.6",
    val onboarded: Boolean = false,
    /** Clicks and chimes on buttons, toggles and the time wheels. */
    val uiSounds: Boolean = true,
    /** The user's own phrase list for typing missions. */
    val customPhrases: List<String> = emptyList(),
)

private val Context.dataStore by preferencesDataStore("settings")

class SettingsRepository(private val context: Context) {
    private val themeKey = intPreferencesKey("theme_mode")
    private val primaryKey = longPreferencesKey("primary_color")
    private val preventKey = booleanPreferencesKey("prevent_changes")
    private val windowKey = intPreferencesKey("lock_window_minutes")
    private val lockVolumeKey = booleanPreferencesKey("lock_volume")
    private val bringBackKey = booleanPreferencesKey("bring_back")
    private val bedtimeKey = booleanPreferencesKey("bedtime_reminder")
    private val goalKey = intPreferencesKey("sleep_goal_minutes")
    private val leadKey = intPreferencesKey("bedtime_lead_minutes")
    private val trackKey = booleanPreferencesKey("track_sleep")
    private val snoreKey = booleanPreferencesKey("detect_snoring")
    private val timerKey = intPreferencesKey("sound_timer_minutes")
    private val onboardedKey = booleanPreferencesKey("onboarded")
    private val uiSoundsKey = booleanPreferencesKey("ui_sounds")
    private val phrasesKey = androidx.datastore.preferences.core.stringPreferencesKey("custom_phrases")
    private val mixKey = androidx.datastore.preferences.core.stringPreferencesKey("sound_mix")

    val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            themeMode = ThemeMode.entries.getOrElse(p[themeKey] ?: 0) { ThemeMode.SYSTEM },
            primaryColor = p[primaryKey] ?: AppSettings().primaryColor,
            preventChanges = p[preventKey] ?: false,
            lockWindowMinutes = p[windowKey] ?: 60,
            lockVolume = p[lockVolumeKey] ?: true,
            bringBack = p[bringBackKey] ?: true,
            bedtimeReminder = p[bedtimeKey] ?: false,
            sleepGoalMinutes = p[goalKey] ?: 480,
            bedtimeLeadMinutes = p[leadKey] ?: 15,
            trackSleep = p[trackKey] ?: true,
            detectSnoring = p[snoreKey] ?: true,
            soundTimerMinutes = p[timerKey] ?: 30,
            soundMix = p[mixKey] ?: "rain:0.6",
            onboarded = p[onboardedKey] ?: false,
            uiSounds = p[uiSoundsKey] ?: true,
            customPhrases = p[phrasesKey]?.let { raw ->
                runCatching { org.json.JSONArray(raw).let { a -> (0 until a.length()).map { a.getString(it) } } }.getOrNull()
            } ?: emptyList(),
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) = context.dataStore.edit { it[themeKey] = mode.ordinal }
    suspend fun setPrimaryColor(argb: Long) = context.dataStore.edit { it[primaryKey] = argb }
    suspend fun setPreventChanges(on: Boolean) = context.dataStore.edit { it[preventKey] = on }
    suspend fun setLockWindow(minutes: Int) = context.dataStore.edit { it[windowKey] = minutes }
    suspend fun setLockVolume(on: Boolean) = context.dataStore.edit { it[lockVolumeKey] = on }
    suspend fun setBringBack(on: Boolean) = context.dataStore.edit { it[bringBackKey] = on }
    suspend fun setBedtimeReminder(on: Boolean) = context.dataStore.edit { it[bedtimeKey] = on }
    suspend fun setSleepGoal(minutes: Int) = context.dataStore.edit { it[goalKey] = minutes }
    suspend fun setBedtimeLead(minutes: Int) = context.dataStore.edit { it[leadKey] = minutes }
    suspend fun setTrackSleep(on: Boolean) = context.dataStore.edit { it[trackKey] = on }
    suspend fun setDetectSnoring(on: Boolean) = context.dataStore.edit { it[snoreKey] = on }
    suspend fun setSoundTimer(minutes: Int) = context.dataStore.edit { it[timerKey] = minutes }
    suspend fun setSoundMix(mix: String) = context.dataStore.edit { it[mixKey] = mix }
    suspend fun currentCustomPhrases(): List<String> = settings.first().customPhrases
    suspend fun setCustomPhrases(list: List<String>) = context.dataStore.edit { it[phrasesKey] = org.json.JSONArray(list).toString() }
    suspend fun setUiSounds(on: Boolean) = context.dataStore.edit { it[uiSoundsKey] = on }
    suspend fun setOnboarded(done: Boolean) = context.dataStore.edit { it[onboardedKey] = done }
}
