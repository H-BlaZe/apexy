package com.hblaze.apexy.ui.screens

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AlarmOn
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DoNotDisturbOn
import androidx.compose.material.icons.rounded.FlipToFront
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.hblaze.apexy.core.AlarmActions
import com.hblaze.apexy.core.Notifications
import com.hblaze.apexy.ui.neu.Neu
import com.hblaze.apexy.ui.neu.NeuButton
import com.hblaze.apexy.ui.neu.NeuCard
import com.hblaze.apexy.ui.neu.NeuChip
import com.hblaze.apexy.ui.neu.NeuIconButton
import com.hblaze.apexy.ui.neu.NeuProgressRing
import com.hblaze.apexy.ui.neu.SectionLabel
import com.hblaze.apexy.ui.neu.SettingRow
import com.hblaze.apexy.ui.neu.neu
import kotlinx.coroutines.launch

// --- Checks Android lets us read ----------------------------------------------------------------

internal class ReliabilityItem(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    /** null when Android gives no way to read the state. */
    val ok: Boolean?,
    val open: (Context) -> Unit,
)

private fun Intent.newTask() = addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

private fun appDetails(context: Context) =
    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")).newTask())

@SuppressLint("BatteryLife")
internal fun reliabilityItems(context: Context): List<ReliabilityItem> {
    val pkg = context.packageName
    val pkgUri = Uri.parse("package:$pkg")
    val nm = context.getSystemService(NotificationManager::class.java)
    val items = mutableListOf<ReliabilityItem>()

    val channelOn = nm.getNotificationChannel(Notifications.CHANNEL_RINGING)?.importance != NotificationManager.IMPORTANCE_NONE
    items += ReliabilityItem(
        "Notifications", "Needed to show the ringing screen", Icons.Rounded.Notifications,
        NotificationManagerCompat.from(context).areNotificationsEnabled() && channelOn,
    ) { it.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, pkg).newTask()) }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        items += ReliabilityItem(
            "Exact alarms", "On the dot", Icons.Rounded.AlarmOn,
            context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms(),
        ) { it.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, pkgUri).newTask()) }
    }

    if (Build.VERSION.SDK_INT >= 34) {
        items += ReliabilityItem(
            "Full-screen alarms", "Show the alarm over the lock screen", Icons.Rounded.Fullscreen,
            nm.canUseFullScreenIntent(),
        ) { it.startActivity(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, pkgUri).newTask()) }
    }

    items += ReliabilityItem(
        "Display over other apps", "Lets Apexy bring the alarm back if you leave it", Icons.Rounded.FlipToFront,
        Settings.canDrawOverlays(context),
    ) { it.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, pkgUri).newTask()) }

    items += ReliabilityItem(
        "Unrestricted battery", "Stop Android from putting Apexy to sleep", Icons.Rounded.BatteryChargingFull,
        context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(pkg),
    ) { ctx ->
        runCatching { ctx.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, pkgUri).newTask()) }
            .onFailure { appDetails(ctx) }
    }

    val audio = context.getSystemService(AudioManager::class.java)
    items += ReliabilityItem(
        "Alarm volume", "Your phone's alarm volume isn't muted", Icons.Rounded.VolumeUp,
        audio.getStreamVolume(AudioManager.STREAM_ALARM) > 0,
    ) { it.startActivity(Intent(Settings.ACTION_SOUND_SETTINGS).newTask()) }

    // "Total silence" Do Not Disturb blocks alarms too; the usual modes let them through.
    items += ReliabilityItem(
        "Do Not Disturb", "Not set to total silence", Icons.Rounded.DoNotDisturbOn,
        nm.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_NONE,
    ) { ctx -> runCatching { ctx.startActivity(Intent(Settings.ACTION_ZEN_MODE_PRIORITY_SETTINGS).newTask()) }.onFailure { ctx.startActivity(Intent(Settings.ACTION_SOUND_SETTINGS).newTask()) } }

    // Phone makers' extra switches can't be read, so each one is a "Check" step.
    PhoneGuides.forThisPhone()?.actions?.forEach { action ->
        items += ReliabilityItem(action.label, action.hint, Icons.Rounded.PhoneAndroid, null) { action.open(it) }
    }
    return items
}

// --- Phone-maker guides -------------------------------------------------------------------------

/** One button in a guide: tries each known settings screen in turn, then falls back to app info. */
internal class GuideAction(val label: String, val hint: String, private val intents: (String) -> List<Intent>) {
    fun open(context: Context) {
        for (intent in intents(context.packageName)) {
            if (runCatching { context.startActivity(intent.newTask()) }.isSuccess) return
        }
        appDetails(context)
    }
}

internal class PhoneGuide(
    val name: String,
    /** Lower-case manufacturer or brand names that use this guide. */
    val makers: Set<String>,
    val steps: List<String>,
    val actions: List<GuideAction>,
)

private fun component(pkg: String, cls: String) = Intent().setComponent(ComponentName(pkg, cls))

internal object PhoneGuides {
    private val batterySettings = GuideAction("Battery optimisation list", "Set Apexy to \"Don't optimise\"") {
        listOf(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
    }

    val all = listOf(
        PhoneGuide(
            "Xiaomi, Redmi & POCO", setOf("xiaomi", "redmi", "poco"),
            listOf(
                "Security app → Permissions → Autostart: turn on Apexy.",
                "App info → Other permissions: allow \"Show on lock screen\" and \"Display pop-up windows while running in the background\".",
                "App info → Battery saver: choose \"No restrictions\".",
                "Open Recents, pull Apexy down and tap the lock so cleanup never closes it.",
            ),
            listOf(
                GuideAction("Autostart", "Let Apexy restart after reboot or cleanup") { listOf(
                    component("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
                    component("com.miui.securitycenter", "com.miui.permcenter.permissions.PermissionsEditorActivity"),
                ) },
                GuideAction("Lock screen & pop-ups", "Allow \"Show on lock screen\" and background pop-ups") { pkg -> listOf(
                    Intent("miui.intent.action.APP_PERM_EDITOR")
                        .setClassName("com.miui.securitycenter", "com.miui.permcenter.permissions.PermissionsEditorActivity")
                        .putExtra("extra_pkgname", pkg),
                    Intent("miui.intent.action.APP_PERM_EDITOR")
                        .setClassName("com.miui.securitycenter", "com.miui.permcenter.permissions.AppPermissionsEditorActivity")
                        .putExtra("extra_pkgname", pkg),
                ) },
                GuideAction("Battery saver", "Choose \"No restrictions\"") { pkg -> listOf(
                    component("com.miui.powerkeeper", "com.miui.powerkeeper.ui.HiddenAppsConfigActivity")
                        .putExtra("package_name", pkg).putExtra("package_label", "Apexy"),
                ) },
            ),
        ),
        PhoneGuide(
            "Samsung", setOf("samsung"),
            listOf(
                "Settings → Battery → Background usage limits: add Apexy to \"Never sleeping apps\".",
                "Make sure Apexy isn't in \"Sleeping apps\" or \"Deep sleeping apps\".",
                "App info → Battery: choose \"Unrestricted\".",
            ),
            listOf(
                GuideAction("Never sleeping apps", "Add Apexy so One UI never puts it to sleep") { listOf(
                    component("com.samsung.android.lool", "com.samsung.android.sm.battery.ui.usage.CheckableAppListActivity"),
                    component("com.samsung.android.lool", "com.samsung.android.sm.battery.ui.BatteryActivity"),
                    component("com.samsung.android.sm", "com.samsung.android.sm.ui.battery.BatteryActivity"),
                ) },
            ),
        ),
        PhoneGuide(
            "Huawei & Honor", setOf("huawei", "honor"),
            listOf(
                "Phone Manager → App launch: turn off \"Manage automatically\" for Apexy, then allow auto-launch, secondary launch and run in background.",
                "Settings → Battery: turn off power-saving modes while you rely on alarms.",
            ),
            listOf(
                GuideAction("App launch", "Manage manually and allow all three switches") { listOf(
                    component("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
                    component("com.huawei.systemmanager", "com.huawei.systemmanager.appcontrol.activity.StartupAppControlActivity"),
                    component("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity"),
                    component("com.hihonor.systemmanager", "com.hihonor.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
                ) },
            ),
        ),
        PhoneGuide(
            "OPPO, Realme & OnePlus", setOf("oppo", "realme", "oneplus"),
            listOf(
                "Settings → Apps → Auto launch (or Startup manager): allow Apexy.",
                "App info → Battery usage: allow background activity and turn off \"Optimise battery use\".",
                "Lock Apexy in Recents so it isn't cleared.",
            ),
            listOf(
                GuideAction("Auto launch", "Allow Apexy to start in the background") { listOf(
                    component("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
                    component("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity"),
                    component("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity"),
                    component("com.oneplus.security", "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity"),
                ) },
                GuideAction("Power manager", "Allow background activity") { listOf(
                    component("com.coloros.oppoguardelf", "com.coloros.powermanager.fuelgaue.PowerUsageModelActivity"),
                    component("com.coloros.oppoguardelf", "com.coloros.powermanager.fuelgaue.PowerConsumptionActivity"),
                ) },
            ),
        ),
        PhoneGuide(
            "vivo & iQOO", setOf("vivo", "iqoo"),
            listOf(
                "i Manager → App manager → Autostart: allow Apexy.",
                "Settings → Battery → Background power consumption: allow high usage for Apexy.",
            ),
            listOf(
                GuideAction("Autostart", "Allow Apexy to start in the background") { listOf(
                    component("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
                    component("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager"),
                    component("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"),
                ) },
            ),
        ),
        PhoneGuide(
            "Tecno, Infinix & itel", setOf("tecno", "infinix", "itel"),
            listOf(
                "Phone Master → App management → Auto-start: allow Apexy.",
                "Phone Master → Power saving: exclude Apexy from background cleanup.",
            ),
            listOf(
                GuideAction("Auto-start", "Allow Apexy in Phone Master") { listOf(
                    component("com.transsion.phonemaster", "com.cyin.himgr.autostart.AutoStartActivity"),
                ) },
            ),
        ),
        PhoneGuide(
            "ASUS", setOf("asus"),
            listOf("Mobile Manager → Auto-start manager: allow Apexy.", "Turn off \"Auto-deny\" for new apps if it's on."),
            listOf(GuideAction("Auto-start manager", "Allow Apexy") { listOf(
                component("com.asus.mobilemanager", "com.asus.mobilemanager.autostart.AutoStartActivity"),
                component("com.asus.mobilemanager", "com.asus.mobilemanager.entry.FunctionActivity"),
            ) }),
        ),
        PhoneGuide(
            "Nokia", setOf("nokia", "hmd global"),
            listOf("Older Nokia phones have a \"Battery protection\" app: exclude Apexy there.", "App info → Battery: Unrestricted."),
            listOf(GuideAction("Battery protection", "Exclude Apexy") { listOf(
                component("com.evenwell.powersaving.g3", "com.evenwell.powersaving.g3.exception.PowerSaverExceptionActivity"),
            ) }),
        ),
        PhoneGuide(
            "Meizu", setOf("meizu"),
            listOf("Security → Permissions: allow background running for Apexy."),
            listOf(GuideAction("App permissions", "Allow background running") { pkg -> listOf(
                Intent("com.meizu.safe.security.SHOW_APPSEC").addCategory(Intent.CATEGORY_DEFAULT).putExtra("packageName", pkg),
            ) }),
        ),
        PhoneGuide(
            "Lenovo & Motorola", setOf("lenovo", "motorola"),
            listOf("App info → Battery: Unrestricted.", "Lenovo: Security → Background app management: allow Apexy."),
            listOf(GuideAction("Background management", "Allow Apexy") { listOf(
                component("com.lenovo.security", "com.lenovo.security.purebackground.PureBackgroundActivity"),
            ) }),
        ),
        PhoneGuide(
            "Sony", setOf("sony"),
            listOf("Settings → Battery → STAMINA mode: add Apexy to the exceptions, or turn STAMINA off overnight."),
            listOf(batterySettings),
        ),
        PhoneGuide(
            "Google Pixel, Nothing & others", setOf("google", "nothing", "fairphone"),
            listOf(
                "Mostly stock Android: the checks above cover it.",
                "App info → Battery: Unrestricted, so Adaptive Battery leaves Apexy alone.",
            ),
            listOf(batterySettings),
        ),
    )

    fun forThisPhone(): PhoneGuide? {
        val names = setOf(Build.MANUFACTURER.lowercase(), Build.BRAND.lowercase())
        return all.firstOrNull { g -> names.any { it in g.makers } }
    }
}

// --- Screen -------------------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReliabilityScreen(onBack: () -> Unit) {
    val c = Neu.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var checks by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) { checks++; onPauseOrDispose {} }
    val items = remember(checks) { reliabilityItems(context) }
    val known = items.filter { it.ok != null }
    val ok = known.count { it.ok == true }
    val mine = remember { PhoneGuides.forThisPhone() }
    var shown by remember { mutableStateOf(mine) }
    var testSet by remember { mutableStateOf(false) }
    BackHandler(onBack = onBack)

    Column(Modifier.fillMaxSize().background(c.surface).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            NeuIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Back", onBack, size = 46.dp)
            Text("Reliability", style = MaterialTheme.typography.titleLarge, color = c.text, modifier = Modifier.padding(start = 16.dp))
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Box(Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.Center) {
                NeuProgressRing(if (known.isEmpty()) 1f else ok.toFloat() / known.size, size = 190.dp) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("$ok/${known.size}", fontSize = 44.sp, fontWeight = FontWeight.Light, color = if (ok == known.size) c.success else c.text)
                        Text("checks passed", style = MaterialTheme.typography.labelLarge, color = c.textMuted)
                    }
                }
            }

            NeuCard(Modifier.fillMaxWidth()) {
                SettingRow("Test alarm", subtitle = if (testSet) "Rings in 1m. Lock the phone." else "Rings in 1m, screen off", icon = Icons.Rounded.Timer) {
                    NeuButton({
                        scope.launch { AlarmActions.quick(1, label = "Reliability test") }
                        testSet = true
                    }, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp), primary = !testSet) { Text(if (testSet) "Again" else "Start") }
                }
                Text(
                    "If it doesn't ring on time, go through your phone's steps below. Some phones kill alarms after you swipe the app away.",
                    style = MaterialTheme.typography.bodyMedium, color = c.textMuted,
                )
            }

            NeuCard(Modifier.fillMaxWidth()) {
                SectionLabel("Android")
                items.filter { it.ok != null }.forEach { CheckRow(it) }
            }

            NeuCard(Modifier.fillMaxWidth()) {
                SectionLabel("Phone maker")
                Text(
                    if (mine != null) "Detected: ${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}"
                    else "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}: pick the closest maker.",
                    style = MaterialTheme.typography.bodyMedium, color = c.textMuted,
                )
                Spacer(Modifier.height(12.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PhoneGuides.all.forEach { g -> NeuChip(g.name.substringBefore(",").substringBefore(" &"), shown == g, { shown = g }) }
                }
                shown?.let { g ->
                    Spacer(Modifier.height(16.dp))
                    Text(g.name, style = MaterialTheme.typography.titleMedium, color = c.text)
                    Spacer(Modifier.height(8.dp))
                    g.steps.forEachIndexed { i, step ->
                        Row(Modifier.padding(vertical = 4.dp)) {
                            Box(Modifier.size(24.dp).neu(12.dp, elevation = 0.dp, inset = 2.dp), contentAlignment = Alignment.Center) {
                                Text("${i + 1}", style = MaterialTheme.typography.labelMedium, color = c.accent)
                            }
                            Spacer(Modifier.width(10.dp))
                            Text(step, style = MaterialTheme.typography.bodyMedium, color = c.text)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    g.actions.forEach { a ->
                        NeuButton({ a.open(context) }, Modifier.fillMaxWidth().padding(vertical = 5.dp)) { Text("Open ${a.label}") }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
internal fun CheckRow(item: ReliabilityItem) {
    val c = Neu.colors
    val context = LocalContext.current
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
