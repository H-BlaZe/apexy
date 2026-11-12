package com.apexy.alarm.ui

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Bundle
import android.widget.RemoteViews
import androidx.compose.ui.graphics.toArgb
import com.apexy.alarm.ApexyApp
import com.apexy.alarm.R
import com.apexy.alarm.core.AlarmScheduler
import com.apexy.alarm.data.AppSettings
import com.apexy.alarm.data.ThemeMode
import com.apexy.alarm.ui.neu.darkNeu
import com.apexy.alarm.ui.neu.lightNeu
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

/** Home-screen widget showing the next alarm on a raised neumorphic card. */
class NextAlarmWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = updateAsync(context)

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) = updateAsync(context)

    private fun updateAsync(context: Context) {
        val pending = goAsync()
        (context.applicationContext as ApexyApp).appScope.launch {
            try { refresh(context) } finally { pending.finish() }
        }
    }

    companion object {
        suspend fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, NextAlarmWidget::class.java))
            if (ids.isEmpty()) return
            val app = context.applicationContext as ApexyApp
            val settings = app.settings.settings.first()
            val next = app.db.alarms().getAll()
                .mapNotNull { a -> AlarmScheduler.nextTrigger(a)?.let { a to it } }
                .minByOrNull { it.second }
            ids.forEach { id -> manager.updateAppWidget(id, views(context, manager, id, settings, next)) }
        }

        private fun views(
            context: Context,
            manager: AppWidgetManager,
            id: Int,
            settings: AppSettings,
            next: Pair<com.apexy.alarm.data.Alarm, Long>?,
        ): RemoteViews {
            val dark = when (settings.themeMode) {
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
                ThemeMode.SYSTEM -> (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
            }
            val primary = androidx.compose.ui.graphics.Color(settings.primaryColor)
            val c = if (dark) darkNeu(primary) else lightNeu(primary)
            val v = RemoteViews(context.packageName, R.layout.widget_next_alarm)

            val opts = manager.getAppWidgetOptions(id)
            val density = context.resources.displayMetrics.density
            val wDp = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 250).coerceAtLeast(120)
            val hDp = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 100).coerceIn(70, 220)
            v.setImageViewBitmap(R.id.widget_bg, neuCard((wDp * density).toInt(), (hDp * density).toInt(), density, c))

            v.setInt(R.id.widget_icon, "setColorFilter", c.primary.toArgb())
            v.setTextColor(R.id.widget_label, c.textMuted.toArgb())
            v.setTextColor(R.id.widget_time, c.text.toArgb())
            v.setTextColor(R.id.widget_detail, c.textMuted.toArgb())
            if (next == null) {
                v.setTextViewText(R.id.widget_time, "Off")
                v.setTextViewText(R.id.widget_detail, "Tap to set an alarm")
            } else {
                val (alarm, at) = next
                val t = Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault())
                val today = LocalDate.now()
                val day = when (t.toLocalDate()) {
                    today -> "Today"
                    today.plusDays(1) -> "Tomorrow"
                    else -> t.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
                }
                v.setTextViewText(R.id.widget_time, formatTime(context, t.hour, t.minute))
                v.setTextViewText(R.id.widget_detail, listOf(day, alarm.label).filter { it.isNotBlank() }.joinToString(" · "))
            }
            val open = PendingIntent.getActivity(
                context, 30, Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            v.setOnClickPendingIntent(R.id.widget_root, open)
            return v
        }

        /** Same shadow recipe as Modifier.neu, drawn once into a bitmap for the launcher. */
        private fun neuCard(w: Int, h: Int, density: Float, c: com.apexy.alarm.ui.neu.NeuColors): Bitmap {
            val bmp = Bitmap.createBitmap(w.coerceAtLeast(1), h.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            val margin = 10 * density
            val e = 6 * density
            val r = 26 * density
            val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply { maskFilter = BlurMaskFilter(e * 1.5f, BlurMaskFilter.Blur.NORMAL) }
            val d = e * 0.65f
            shadow.color = c.shadowDark.toArgb()
            canvas.drawRoundRect(margin + d, margin + d, w - margin + d, h - margin + d, r, r, shadow)
            shadow.color = c.shadowLight.toArgb()
            canvas.drawRoundRect(margin - d, margin - d, w - margin - d, h - margin - d, r, r, shadow)
            canvas.drawRoundRect(margin, margin, w - margin, h - margin, r, r, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = c.surface.toArgb() })
            return bmp
        }
    }
}
