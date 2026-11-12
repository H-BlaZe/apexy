package com.apexy.alarm.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.AlarmOn
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MonitorHeart
import androidx.compose.material.icons.rounded.MoneyOff
import androidx.compose.material.icons.rounded.NoAccounts
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.SaveAlt
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Snooze
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apexy.alarm.R
import com.apexy.alarm.missions.MissionType
import com.apexy.alarm.missions.icon
import com.apexy.alarm.ui.neu.Neu
import com.apexy.alarm.ui.neu.NeuCard
import com.apexy.alarm.ui.neu.NeuIconButton
import com.apexy.alarm.ui.neu.SectionLabel
import com.apexy.alarm.ui.neu.neu

private data class Point(val icon: ImageVector, val title: String, val detail: String)

private val WHY = listOf(
    Point(Icons.Rounded.AlarmOn, "You actually get up", "Missions keep ringing until you're awake"),
    Point(Icons.Rounded.MoneyOff, "Everything included", "No ads, no premium tier, no paywall"),
    Point(Icons.Rounded.CloudOff, "Works offline", "Rings the same with no signal at all"),
    Point(Icons.Rounded.PhoneAndroid, "Built for strict phones", "Guides for Xiaomi, Samsung and more"),
)

private val SAFE = listOf(
    Point(Icons.Rounded.CloudOff, "No internet permission", "The app can't send anything anywhere"),
    Point(Icons.Rounded.NoAccounts, "No account", "No sign-up, email or phone number"),
    Point(Icons.Rounded.VisibilityOff, "No tracking", "No analytics, ads or trackers"),
    Point(Icons.Rounded.GraphicEq, "Mic stays on the phone", "Snoring is detected on-device; only short clips are kept"),
    Point(Icons.Rounded.CameraAlt, "Camera only in missions", "Photos and codes never leave the phone"),
    Point(Icons.Rounded.SaveAlt, "Backups are yours", "A file you choose where to save"),
    Point(Icons.Rounded.DeleteForever, "Delete anytime", "Clear history, nights or recordings in one tap"),
    Point(Icons.Rounded.RestartAlt, "Survives restarts", "Alarms come back after reboot or time changes"),
    Point(Icons.Rounded.BatteryChargingFull, "Battery guard", "Tracking stops at low battery so the alarm still rings"),
)

private val FEATURES = listOf(
    "Alarms" to listOf(
        Point(Icons.Rounded.Alarm, "Repeat, label, colour", "Per-alarm days, name and accent"),
        Point(Icons.Rounded.Snooze, "Smart snooze", "Limits, shrinking snooze, skip next"),
        Point(Icons.Rounded.VolumeUp, "Sounds", "7 tones, system sounds or your music"),
        Point(Icons.Rounded.TrendingUp, "Gentle to loud", "Fade in, curves, louder if ignored"),
        Point(Icons.Rounded.Vibration, "Vibration", "Patterns or tap your own"),
        Point(Icons.Rounded.FlashlightOn, "Flash", "Torch and screen flash"),
        Point(Icons.Rounded.RecordVoiceOver, "Spoken alarm", "Time, date and label read aloud"),
        Point(Icons.Rounded.Widgets, "Widget and morning card", "Next alarm at a glance, your day after"),
    ),
    "Anti-cheat" to listOf(
        Point(Icons.Rounded.MonitorHeart, "Wake-up check", "Confirm you're up or it rings again"),
        Point(Icons.Rounded.Lock, "Locks", "No edits before it rings, volume locked"),
        Point(Icons.Rounded.Shield, "Bring back", "Reopens if you leave the alarm"),
    ),
    "Sleep" to listOf(
        Point(Icons.Rounded.Bedtime, "Sleep sounds", "8 live sounds, mix and timer"),
        Point(Icons.Rounded.MonitorHeart, "Sleep tracking", "Stages, snoring and nightly reports"),
        Point(Icons.Rounded.Alarm, "Bedtime", "Sleep goal and reminders"),
    ),
    "More" to listOf(
        Point(Icons.Rounded.BarChart, "Reports", "Streaks, wake times and history"),
        Point(Icons.Rounded.Shield, "Reliability", "Phone checklist and test alarm"),
        Point(Icons.Rounded.Palette, "Your look", "Light, dark, any colour, sounds"),
        Point(Icons.Rounded.SaveAlt, "Backup", "Save and restore everything"),
    ),
)

/** What Apexy does, why it's worth using, and why it's safe. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val c = Neu.colors
    val context = LocalContext.current
    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: ""
    }
    BackHandler(onBack = onBack)

    Column(Modifier.fillMaxSize().background(c.surface).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            NeuIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Back", onBack, size = 46.dp)
            Text("About", style = MaterialTheme.typography.titleLarge, color = c.text, modifier = Modifier.padding(start = 16.dp))
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Column(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(96.dp).neu(28.dp, elevation = 10.dp).padding(8.dp).clip(RoundedCornerShape(22.dp))) {
                    Image(painterResource(R.drawable.ic_launcher_bg), null, Modifier.fillMaxSize())
                    Image(painterResource(R.drawable.ic_launcher_fg), "Apexy", Modifier.fillMaxSize().scale(1.25f))
                }
                Spacer(Modifier.height(16.dp))
                Text("Apexy", fontSize = 32.sp, fontWeight = FontWeight.Light, color = c.text)
                Text("v$version · Wake up for real", style = MaterialTheme.typography.bodyMedium, color = c.textMuted)
            }

            PointsCard("Why Apexy", WHY)
            PointsCard("Safe & private", SAFE, tint = c.success)

            NeuCard(Modifier.fillMaxWidth()) {
                SectionLabel("${MissionType.entries.size} wake-up missions")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    MissionType.entries.forEach { m ->
                        Row(
                            Modifier.neu(14.dp, elevation = 0.dp, inset = 3.dp).padding(horizontal = 12.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(m.icon, null, Modifier.size(16.dp), tint = c.accent)
                            Spacer(Modifier.width(6.dp))
                            Text(m.title, style = MaterialTheme.typography.labelLarge, color = c.text)
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text("Random pick, difficulty, try-before-use, emergency escape", style = MaterialTheme.typography.bodyMedium, color = c.textMuted)
            }

            FEATURES.forEach { (title, points) -> PointsCard(title, points) }

            Text(
                "Made with care. Your mornings, your data.",
                style = MaterialTheme.typography.bodyMedium, color = c.textMuted,
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}

@Composable
private fun PointsCard(title: String, points: List<Point>, tint: androidx.compose.ui.graphics.Color = Neu.colors.accent) {
    val c = Neu.colors
    NeuCard(Modifier.fillMaxWidth(), padding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 12.dp)) {
        SectionLabel(title)
        points.forEach { p ->
            Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp).neu(14.dp, elevation = 0.dp, inset = 3.dp), contentAlignment = Alignment.Center) {
                    Icon(p.icon, null, Modifier.size(20.dp), tint = tint)
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(p.title, style = MaterialTheme.typography.titleMedium, color = c.text)
                    Text(p.detail, style = MaterialTheme.typography.bodyMedium, color = c.textMuted)
                }
            }
        }
    }
}
