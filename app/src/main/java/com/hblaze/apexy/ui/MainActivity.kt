package com.hblaze.apexy.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color as AndroidColor
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hblaze.apexy.ApexyApp
import com.hblaze.apexy.data.AppSettings
import com.hblaze.apexy.ui.neu.ApexyTheme
import com.hblaze.apexy.ui.neu.Neu
import com.hblaze.apexy.ui.neu.isDarkFor
import com.hblaze.apexy.ui.neu.neu
import com.hblaze.apexy.ui.screens.AlarmEditorScreen
import com.hblaze.apexy.ui.screens.AlarmListScreen
import com.hblaze.apexy.ui.screens.ReportsScreen
import com.hblaze.apexy.ui.screens.SettingsScreen
import com.hblaze.apexy.ui.screens.SleepScreen

enum class Tab(val label: String, val icon: ImageVector) {
    ALARMS("Alarms", Icons.Rounded.Alarm),
    SLEEP("Sleep", Icons.Rounded.Bedtime),
    REPORTS("Reports", Icons.Rounded.BarChart),
    SETTINGS("Settings", Icons.Rounded.Tune),
}

class MainActivity : ComponentActivity() {
    private val requestedTab = androidx.compose.runtime.mutableStateOf<Tab?>(null)
    private val pendingNewAlarm = androidx.compose.runtime.mutableStateOf(false)

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        readTab(intent)
    }

    private fun readTab(intent: android.content.Intent?) {
        if (intent?.getStringExtra(EXTRA_TAB) == "sleep") requestedTab.value = Tab.SLEEP
    }

    companion object {
        const val EXTRA_TAB = "tab"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        readTab(intent)
        val app = application as ApexyApp
        setContent {
            // null until DataStore answers, so first-launch onboarding never flashes for returning users.
            val loaded by app.settings.settings.collectAsStateWithLifecycle(null as AppSettings?)
            val settings = loaded ?: AppSettings(onboarded = true)
            val dark = isDarkFor(settings)
            LaunchedEffect(dark) {
                val bars = if (dark) SystemBarStyle.dark(AndroidColor.TRANSPARENT)
                else SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
            }
            ApexyTheme(settings) {
                when {
                    loaded == null -> Box(Modifier.fillMaxSize().background(Neu.colors.surface))
                    !settings.onboarded -> com.hblaze.apexy.ui.screens.OnboardingScreen(settings) { createAlarm ->
                        pendingNewAlarm.value = createAlarm
                    }
                    else -> ApexyRoot(settings, requestedTab.value, pendingNewAlarm.value, onTabHandled = { requestedTab.value = null }, onNewAlarmHandled = { pendingNewAlarm.value = false })
                }
            }
        }
    }
}

@Composable
private fun ApexyRoot(settings: AppSettings, requested: Tab?, newAlarm: Boolean, onTabHandled: () -> Unit, onNewAlarmHandled: () -> Unit) {
    var tab by rememberSaveable { mutableStateOf(Tab.ALARMS) }
    LaunchedEffect(requested) {
        if (requested != null) { tab = requested; onTabHandled() }
    }
    // null = not editing, 0 = new alarm, otherwise the alarm id.
    var editing by rememberSaveable { mutableStateOf<Long?>(null) }
    var reliability by rememberSaveable { mutableStateOf(false) }
    var about by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(newAlarm) { if (newAlarm) { editing = 0; onNewAlarmHandled() } }

    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    val context = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    Box(Modifier.fillMaxSize().background(Neu.colors.surface)) {
        AnimatedContent(Triple(editing, reliability, about), transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "root") { (editId, showReliability, showAbout) ->
            if (showAbout) {
                com.hblaze.apexy.ui.screens.AboutScreen(onBack = { about = false })
            } else if (showReliability) {
                com.hblaze.apexy.ui.screens.ReliabilityScreen(onBack = { reliability = false })
            } else if (editId != null) {
                BackHandler { editing = null }
                AlarmEditorScreen(alarmId = editId, onClose = { editing = null })
            } else {
                Column(Modifier.fillMaxSize().statusBarsPadding()) {
                    AnimatedContent(
                        tab,
                        Modifier.weight(1f),
                        transitionSpec = {
                            val dir = if (targetState.ordinal > initialState.ordinal) 1 else -1
                            (slideInHorizontally(tween(260)) { it / 6 * dir } + fadeIn(tween(220))) togetherWith
                                (slideOutHorizontally(tween(220)) { -it / 6 * dir } + fadeOut(tween(160)))
                        },
                        label = "tabs",
                    ) { shown ->
                        when (shown) {
                            Tab.ALARMS -> AlarmListScreen(onEdit = { editing = it }, onOpenReliability = { reliability = true })
                            Tab.SLEEP -> SleepScreen()
                            Tab.REPORTS -> ReportsScreen()
                            Tab.SETTINGS -> SettingsScreen(settings, onOpenReliability = { reliability = true }, onOpenAbout = { about = true })
                        }
                    }
                    NeuTabBar(tab, onSelect = { tab = it })
                }
            }
        }
    }
}

@Composable
private fun NeuTabBar(selected: Tab, onSelect: (Tab) -> Unit) {
    val c = Neu.colors
    val haptics = com.hblaze.apexy.ui.rememberHaptics()
    val index = selected.ordinal

    BoxWithConstraints(
        Modifier
            .navigationBarsPadding()
            .padding(horizontal = 18.dp, vertical = 12.dp)
            .fillMaxWidth()
            .height(70.dp)
            .neu(28.dp, elevation = 9.dp)
            .padding(8.dp),
    ) {
        val gap = 6.dp
        val count = Tab.entries.size
        val slot = (maxWidth - gap * (count - 1)) / count
        val slotPx = with(androidx.compose.ui.platform.LocalDensity.current) { (slot + gap).toPx() }
        // The pill keeps one fixed size, so its shadow is drawn once; it moves and stretches on the
        // GPU layer only, which keeps the slide at full frame rate.
        val position = remember { androidx.compose.animation.core.Animatable(index.toFloat()) }
        LaunchedEffect(index) {
            position.animateTo(index.toFloat(), spring(dampingRatio = 0.78f, stiffness = 380f))
        }

        Box(
            Modifier
                .width(slot)
                .fillMaxHeight()
                .graphicsLayer {
                    translationX = position.value * slotPx
                    // Stretch with speed, like a drop of liquid, and settle back as it lands.
                    val stretch = (kotlin.math.abs(position.velocity) * 0.06f).coerceAtMost(0.45f)
                    scaleX = 1f + stretch
                    scaleY = 1f - stretch * 0.18f
                }
                .neu(20.dp, elevation = 0.dp, inset = 4.dp),
        )
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(gap)) {
            Tab.entries.forEach { t ->
                val active = t == selected
                val tint by animateColorAsState(if (active) c.accent else c.textMuted, tween(220), label = "tabTint")
                val scale by animateFloatAsState(
                    if (active) 1.12f else 1f,
                    spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
                    label = "tabScale",
                )
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .pointerInput(t) {
                            detectTapGestures {
                                if (t != selected) { haptics.tick(); com.hblaze.apexy.ui.UiSounds.play(com.hblaze.apexy.ui.UiSounds.Kind.TAP) }
                                onSelect(t)
                            }
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(t.icon, t.label, Modifier.size(22.dp).scale(scale), tint = tint)
                    Text(t.label, style = MaterialTheme.typography.labelMedium, color = tint)
                }
            }
        }
    }
}
