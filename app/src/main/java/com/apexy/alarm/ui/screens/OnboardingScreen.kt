package com.apexy.alarm.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AlarmOn
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.apexy.alarm.ApexyApp
import com.apexy.alarm.data.AppSettings
import com.apexy.alarm.data.ThemeMode
import com.apexy.alarm.ui.neu.Neu
import com.apexy.alarm.ui.neu.NeuButton
import com.apexy.alarm.ui.neu.NeuCard
import com.apexy.alarm.ui.neu.NeuSegmented
import com.apexy.alarm.ui.neu.PrimaryPresets
import com.apexy.alarm.ui.neu.SettingRow
import com.apexy.alarm.ui.neu.neu
import kotlinx.coroutines.launch

private const val PAGES = 4

/** First launch: what Apexy does, how it should look, and the permissions an alarm needs to be trusted. */
@Composable
fun OnboardingScreen(settings: AppSettings, onFinished: (createAlarm: Boolean) -> Unit) {
    val c = Neu.colors
    val scope = rememberCoroutineScope()
    var page by rememberSaveable { mutableIntStateOf(0) }
    fun finish(createAlarm: Boolean) = scope.launch {
        ApexyApp.instance.settings.setOnboarded(true)
        onFinished(createAlarm)
    }
    BackHandler(enabled = page > 0) { page-- }

    Column(Modifier.fillMaxSize().background(c.surface).statusBarsPadding().navigationBarsPadding().padding(24.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            repeat(PAGES) { i ->
                Box(
                    Modifier.padding(horizontal = 4.dp).size(width = if (i == page) 28.dp else 10.dp, height = 10.dp)
                        .neu(5.dp, elevation = 0.dp, inset = if (i == page) 0.dp else 2.dp, fill = if (i == page) c.primary else null),
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        AnimatedContent(page, Modifier.weight(1f), transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "onboarding") { p ->
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                when (p) {
                    0 -> WelcomePage()
                    1 -> LookPage(settings)
                    2 -> PermissionsPage()
                    else -> ReadyPage()
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        if (page < PAGES - 1) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                if (page > 0) NeuButton({ page-- }, Modifier.weight(1f)) { Text("Back") }
                NeuButton({ page++ }, Modifier.weight(2f), primary = true) { Text(if (page == 2) "Continue" else "Next") }
            }
        } else {
            NeuButton({ finish(createAlarm = true) }, Modifier.fillMaxWidth().height(60.dp), primary = true) { Text("Create my first alarm", fontSize = 17.sp) }
            Spacer(Modifier.height(12.dp))
            NeuButton({ finish(createAlarm = false) }, Modifier.fillMaxWidth()) { Text("Maybe later") }
        }
    }
}

@Composable
private fun WelcomePage() {
    val c = Neu.colors
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(24.dp))
        // The app icon itself, raised off the page.
        Box(Modifier.size(132.dp).neu(36.dp, elevation = 14.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.size(132.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(36.dp))) {
                androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(com.apexy.alarm.R.drawable.ic_launcher_bg), null, Modifier.fillMaxSize())
                androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(com.apexy.alarm.R.drawable.ic_launcher_fg), "Apexy", Modifier.fillMaxSize().scale(1.25f))
            }
        }
        Spacer(Modifier.height(28.dp))
        Text("Apexy", fontSize = 44.sp, fontWeight = FontWeight.Light, color = c.text)
        Text("The alarm you can't sleep through.", style = MaterialTheme.typography.titleMedium, color = c.textMuted, textAlign = TextAlign.Center)
        Spacer(Modifier.height(28.dp))
        Feature(Icons.Rounded.Extension, "Wake-up missions", "Math, memory, typing, shaking, steps, squats, QR, barcode or a photo before it stops.")
        Feature(Icons.Rounded.Lock, "No cheating", "Snooze limits, wake-up checks, locked volume and locked alarms.")
        Feature(Icons.Rounded.Bedtime, "Better sleep", "Sleep sounds, sleep tracking, snore recordings and bedtime reminders.")
        Spacer(Modifier.height(8.dp))
        Text("Everything included. No ads, no account, works offline.", style = MaterialTheme.typography.bodyMedium, color = c.textMuted, textAlign = TextAlign.Center)
    }
}

@Composable
private fun Feature(icon: ImageVector, title: String, text: String) {
    SettingRow(title, subtitle = text, icon = icon, modifier = Modifier.padding(vertical = 4.dp))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LookPage(settings: AppSettings) {
    val c = Neu.colors
    val scope = rememberCoroutineScope()
    val repo = ApexyApp.instance.settings
    Text("Make it yours", style = MaterialTheme.typography.headlineMedium, color = c.text)
    Text("You can change this any time in Settings.", style = MaterialTheme.typography.bodyMedium, color = c.textMuted)
    Spacer(Modifier.height(24.dp))
    NeuCard(Modifier.fillMaxWidth()) {
        Text("Theme", style = MaterialTheme.typography.titleMedium, color = c.text)
        Spacer(Modifier.height(12.dp))
        NeuSegmented(ThemeMode.entries, settings.themeMode, { scope.launch { repo.setThemeMode(it) } }, Modifier.fillMaxWidth()) {
            when (it) { ThemeMode.SYSTEM -> "System"; ThemeMode.LIGHT -> "Light"; ThemeMode.DARK -> "Dark" }
        }
        Spacer(Modifier.height(22.dp))
        Text("Colour", style = MaterialTheme.typography.titleMedium, color = c.text)
        Spacer(Modifier.height(12.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            PrimaryPresets.forEach { (argb, name) ->
                ColorSwatch(Color(argb), settings.primaryColor == argb, name) { scope.launch { repo.setPrimaryColor(argb) } }
            }
        }
    }
    Spacer(Modifier.height(20.dp))
    NeuCard(Modifier.fillMaxWidth()) {
        Text("Preview", style = MaterialTheme.typography.labelMedium, color = c.textMuted)
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("7:00", fontSize = 42.sp, fontWeight = FontWeight.Light, color = c.text, modifier = Modifier.weight(1f))
            com.apexy.alarm.ui.neu.NeuToggle(true, {})
        }
        Spacer(Modifier.height(12.dp))
        NeuButton({}, Modifier.fillMaxWidth(), primary = true) { Text("Start missions") }
    }
}

@Composable
private fun PermissionsPage() {
    val c = Neu.colors
    val context = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) { refresh++; onPauseOrDispose {} }
    Text("Let it ring", style = MaterialTheme.typography.headlineMedium, color = c.text)
    Text(
        "Android and phone makers put apps to sleep to save battery. These settings make sure your alarm still rings, on time, over the lock screen.",
        style = MaterialTheme.typography.bodyMedium, color = c.textMuted,
    )
    Spacer(Modifier.height(20.dp))
    NeuCard(Modifier.fillMaxWidth()) {
        androidx.compose.runtime.key(refresh) {
            reliabilityItems(context).forEach { item ->
                SettingRow(item.title, subtitle = item.subtitle, icon = item.icon) {
                    if (item.ok == true) {
                        Box(Modifier.size(36.dp).neu(18.dp, elevation = 0.dp, inset = 3.dp), contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.Check, "Done", tint = c.success, modifier = Modifier.size(20.dp))
                        }
                    } else {
                        NeuButton({ item.open(context) }, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)) {
                            Text(if (item.ok == null) "Check" else "Fix", color = if (item.ok == null) c.text else c.accent)
                        }
                    }
                }
            }
        }
    }
    Spacer(Modifier.height(12.dp))
    Text("You'll find these again under Settings → Reliability.", style = MaterialTheme.typography.bodyMedium, color = c.textMuted)
}

@Composable
private fun ReadyPage() {
    val c = Neu.colors
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(48.dp))
        Box(Modifier.size(120.dp).neu(60.dp, elevation = 12.dp), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Check, null, tint = c.accent, modifier = Modifier.size(56.dp))
        }
        Spacer(Modifier.height(28.dp))
        Text("You're all set", fontSize = 34.sp, fontWeight = FontWeight.Light, color = c.text)
        Spacer(Modifier.height(10.dp))
        Text(
            "Tip: add a mission to your alarm and turn on a wake-up check. That's what makes it impossible to sleep through.",
            style = MaterialTheme.typography.bodyLarge, color = c.textMuted, textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        Spacer(Modifier.width(1.dp))
    }
}
