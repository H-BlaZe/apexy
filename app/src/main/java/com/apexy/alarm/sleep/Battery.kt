package com.apexy.alarm.sleep

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager

/** Battery level and whether the phone is plugged in, read from the sticky battery broadcast. */
data class BatteryStatus(val percent: Int, val charging: Boolean) {
    /** Too low to track a whole night unplugged: tracking keeps the phone partly awake. */
    val riskyForNight get() = !charging && percent < WARN_BELOW

    companion object {
        const val WARN_BELOW = 40
        /** During the night, tracking stops here so the alarm still has power to ring. */
        const val STOP_AT = 15

        fun read(context: Context): BatteryStatus {
            val i: Intent? = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val level = i?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = i?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
            val plugged = (i?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0
            val pct = if (level >= 0 && scale > 0) level * 100 / scale else 100
            return BatteryStatus(pct, plugged)
        }
    }
}
