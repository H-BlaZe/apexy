package com.hblaze.apexy.missions

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Add
import androidx.compose.runtime.collectAsState
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.hblaze.apexy.ui.neu.Neu
import com.hblaze.apexy.ui.neu.NeuButton
import com.hblaze.apexy.ui.neu.NeuCard
import com.hblaze.apexy.ui.neu.NeuIconButton
import com.hblaze.apexy.ui.neu.NeuSegmented
import com.hblaze.apexy.ui.neu.NeuStepper
import com.hblaze.apexy.ui.neu.SectionLabel
import com.hblaze.apexy.ui.neu.neu
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Grid of mission types to add to an alarm. */
@Composable
fun MissionPicker(onPick: (MissionType) -> Unit, onBack: () -> Unit) {
    val c = Neu.colors
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize().background(c.surface).statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            NeuIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Back", onBack, size = 46.dp)
            Text("Add a mission", style = MaterialTheme.typography.titleLarge, color = c.text, modifier = Modifier.padding(start = 16.dp))
        }
        Spacer(Modifier.height(24.dp))
        MissionType.entries.filter { it.inThisBuild }.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(bottom = 16.dp)) {
                row.forEach { type ->
                    NeuCard(Modifier.weight(1f).aspectRatio(0.9f), cornerRadius = 24.dp, padding = PaddingValues(12.dp), onClick = { onPick(type) }) {
                        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                            Box(Modifier.size(54.dp).neu(27.dp, elevation = 0.dp, inset = 4.dp), contentAlignment = Alignment.Center) {
                                Icon(type.icon, null, tint = c.accent, modifier = Modifier.size(28.dp))
                            }
                        }
                        Text(type.title, style = MaterialTheme.typography.titleMedium, color = c.text, modifier = Modifier.align(Alignment.CenterHorizontally))
                        Text(type.blurb, style = MaterialTheme.typography.labelMedium, color = c.textMuted, textAlign = TextAlign.Center, minLines = 2, maxLines = 2, modifier = Modifier.align(Alignment.CenterHorizontally))
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** Difficulty, amount, registration and a "try it" run for one mission. */
@Composable
fun MissionConfigScreen(initial: Mission, isNew: Boolean, onSave: (Mission) -> Unit, onRemove: () -> Unit, onBack: () -> Unit) {
    val c = Neu.colors
    val context = LocalContext.current
    var mission by remember { mutableStateOf(initial) }
    var trying by remember { mutableStateOf(false) }
    var registering by remember { mutableStateOf(false) }

    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) registering = true
    }
    val activityPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(mission.type) {
        if (mission.type == MissionType.WALK && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED
        ) activityPermission.launch(Manifest.permission.ACTIVITY_RECOGNITION)
    }
    fun startRegistering() {
        if (mission.type == MissionType.NFC) { registering = true; return }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) registering = true
        else cameraPermission.launch(Manifest.permission.CAMERA)
    }

    if (trying) {
        BackHandler { trying = false }
        MissionHost(
            missions = listOf(mission),
            onAllDone = { trying = false },
            onTimeout = {},
            idleTimeoutMs = 120_000,
            onClose = { trying = false },
            modifier = Modifier.background(c.surface).statusBarsPadding().navigationBarsPadding().padding(20.dp),
        )
        return
    }
    if (registering) {
        BackHandler { registering = false }
        RegisterScreen(mission, onDone = { mission = it; registering = false }, onBack = { registering = false })
        return
    }

    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize().background(c.surface).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            NeuIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Back", onBack, size = 46.dp)
            Text(mission.type.title, style = MaterialTheme.typography.titleLarge, color = c.text, modifier = Modifier.padding(start = 16.dp).weight(1f))
            com.hblaze.apexy.ui.neu.NeuDoneButton(
                if (isNew) "Add" else "Done",
                onConfirm = { true },
                enabled = mission.isReady,
                onFinished = { onSave(mission) },
            )
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            Spacer(Modifier.height(4.dp))
            NeuCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(64.dp).neu(32.dp, elevation = 0.dp, inset = 5.dp), contentAlignment = Alignment.Center) {
                        Box(Modifier.size(44.dp).neu(22.dp, elevation = 4.dp, fill = c.primary), contentAlignment = Alignment.Center) {
                            Icon(mission.type.icon, null, tint = c.onPrimary)
                        }
                    }
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(mission.type.blurb, style = MaterialTheme.typography.titleMedium, color = c.text)
                    }
                }
            }

            if (mission.type.needsRegistration) RegistrationCard(mission, onRegister = ::startRegistering, onChange = { mission = it })

            if (mission.type == MissionType.TYPING) PhraseListCard(mission) { mission = it }

            if (mission.type.hasDifficulty) {
                NeuCard(Modifier.fillMaxWidth()) {
                    SectionLabel(if (mission.type == MissionType.PHOTO) "Match strictness" else "Difficulty")
                    NeuSegmented((1..5).toList(), mission.difficulty, { mission = mission.copy(difficulty = it) }, Modifier.fillMaxWidth()) { "$it" }
                    Spacer(Modifier.height(12.dp))
                    Text(DIFFICULTY_NAMES[mission.difficulty - 1], style = MaterialTheme.typography.titleMedium, color = c.accent)
                    Text(sample(mission), style = MaterialTheme.typography.bodyMedium, color = c.textMuted)
                }
            }

            if (mission.type.countRange.first != mission.type.countRange.last) {
                NeuCard(Modifier.fillMaxWidth()) {
                    SectionLabel(mission.type.countLabel)
                    NeuStepper(mission.count, { mission = mission.copy(count = it) }, mission.type.countRange, mission.type.countStep)
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                NeuButton({ trying = true }, Modifier.weight(1f), enabled = mission.isReady) {
                    Icon(Icons.Rounded.PlayArrow, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Try it")
                }
                if (!isNew) NeuButton(onRemove, Modifier.weight(1f)) { Text("Remove", color = c.danger) }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

private fun sample(m: Mission) = when (m.type) {
    MissionType.MATH -> "e.g. ${MathProblems.make(m.difficulty, kotlin.random.Random(7)).text}"
    MissionType.MEMORY -> {
        val grid = when (m.difficulty) { 1, 2 -> 3; 3, 4 -> 4; else -> 5 }
        "${2 + m.difficulty} tiles on a ${grid}×$grid grid"
    }
    MissionType.TYPING -> when (m.difficulty) { 1, 2 -> "Short lines"; 3 -> "Any line"; else -> "Long lines, exact case" }
    MissionType.SHAKE -> listOf("Gentle", "Light", "Firm", "Hard", "Very hard").get(m.difficulty - 1) + " shakes"
    MissionType.SQUAT -> listOf("Half squats", "Shallow", "Normal depth", "Deep", "Full depth").get(m.difficulty - 1)
    MissionType.PHOTO -> "Needs ${(PhotoMatcher.THRESHOLDS[m.difficulty - 1] * 100).toInt()}% similarity"
    MissionType.LIGHT -> listOf("A lamp", "A bright room", "Near a window", "By an open window", "Daylight").get(m.difficulty - 1) +
        " · ${LIGHT_LUX[m.difficulty - 1]} lux"
    MissionType.ODD -> "${m.difficulty + 2}×${m.difficulty + 2} grid of look-alikes"
    MissionType.ORDER -> listOf("1 to 6", "1 to 9", "1 to 12", "1 to 16", "1 to 20, reshuffled after each tap").get(m.difficulty - 1)
    MissionType.STEADY -> listOf("Relaxed", "Calm", "Still", "Very still", "Statue").get(m.difficulty - 1)
    else -> ""
}

@Composable
private fun RegistrationCard(mission: Mission, onRegister: () -> Unit, onChange: (Mission) -> Unit) {
    val c = Neu.colors
    val context = LocalContext.current
    NeuCard(Modifier.fillMaxWidth()) {
        SectionLabel("Registered")
        when (mission.type) {
            MissionType.NFC -> {
                val adapter = remember { android.nfc.NfcAdapter.getDefaultAdapter(context) }
                mission.codes.forEach { code ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp).neu(16.dp, elevation = 0.dp, inset = 3.dp).padding(start = 16.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text("Tag $code".take(40), color = c.text, modifier = Modifier.weight(1f), maxLines = 1)
                        NeuIconButton(Icons.Rounded.Close, "Remove", { onChange(mission.copy(codes = mission.codes - code)) }, size = 40.dp)
                    }
                }
                when {
                    adapter == null -> Text("This phone has no NFC.", color = c.danger, style = MaterialTheme.typography.bodyMedium)
                    !adapter.isEnabled -> {
                        Text("NFC is off.", color = c.danger, style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(10.dp))
                        NeuButton({ context.startActivity(android.content.Intent(android.provider.Settings.ACTION_NFC_SETTINGS)) }, Modifier.fillMaxWidth()) { Text("Turn on NFC") }
                    }
                    mission.codes.size < 3 -> {
                        Spacer(Modifier.height(12.dp))
                        NeuButton(onRegister, Modifier.fillMaxWidth(), primary = mission.codes.isEmpty()) {
                            Icon(mission.type.icon, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Register a tag")
                        }
                    }
                }
            }
            MissionType.PHOTO -> {
                val bmp = remember(mission.photo) { mission.photo.takeIf { it.isNotBlank() }?.let { BitmapFactory.decodeFile(it)?.asImageBitmap() } }
                if (bmp != null) {
                    Image(bmp, "Registered photo", contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(20.dp)))
                    Spacer(Modifier.height(14.dp))
                }
                NeuButton(onRegister, Modifier.fillMaxWidth(), primary = bmp == null) {
                    Icon(Icons.Rounded.PhotoCamera, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
                    Text(if (bmp == null) "Take reference photo" else "Retake photo")
                }
            }
            else -> {
                mission.codes.forEach { code ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp).neu(16.dp, elevation = 0.dp, inset = 3.dp).padding(start = 16.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text(code.take(40), color = c.text, modifier = Modifier.weight(1f), maxLines = 1)
                        NeuIconButton(Icons.Rounded.Close, "Remove", { onChange(mission.copy(codes = mission.codes - code)) }, size = 40.dp)
                    }
                }
                if (mission.codes.size < 3) {
                    Spacer(Modifier.height(12.dp))
                    NeuButton(onRegister, Modifier.fillMaxWidth(), primary = mission.codes.isEmpty()) {
                        Icon(mission.type.icon, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
                        Text("Scan a ${if (mission.type == MissionType.QR) "QR code" else "barcode"}")
                    }
                }
            }
        }
    }
}

@Composable
private fun RegisterScreen(mission: Mission, onDone: (Mission) -> Unit, onBack: () -> Unit) {
    val c = Neu.colors
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    var preview by remember { mutableStateOf<PreviewView?>(null) }
    var saving by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(c.surface).statusBarsPadding().navigationBarsPadding().padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            NeuIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Back", onBack, size = 46.dp)
            Text(
                when (mission.type) { MissionType.PHOTO -> "Reference photo"; MissionType.NFC -> "Register a tag"; else -> "Scan to register" },
                style = MaterialTheme.typography.titleLarge, color = c.text, modifier = Modifier.padding(start = 16.dp),
            )
        }
        Spacer(Modifier.height(18.dp))
        if (mission.type == MissionType.NFC) {
            NfcReader { id -> view.performHapticFeedback(android.view.HapticFeedbackConstants.CONFIRM); onDone(mission.copy(codes = (mission.codes + id).distinct())) }
            Column(Modifier.fillMaxWidth().weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                NfcPulse()
                Spacer(Modifier.height(24.dp))
                Text("Tap a tag to the phone's back. Keep it away from your bed.",
                    fontSize = 16.sp, color = c.textMuted, textAlign = TextAlign.Center)
            }
        } else if (mission.type == MissionType.PHOTO) {
            CameraFrame(Modifier.fillMaxWidth().weight(1f)) { CameraPreview(Modifier.fillMaxSize(), onReady = { preview = it }) }
            Spacer(Modifier.height(16.dp))
            NeuIconButton(Icons.Rounded.PhotoCamera, "Capture", {
                val bmp = preview?.bitmap ?: return@NeuIconButton
                if (saving) return@NeuIconButton
                saving = true
                scope.launch {
                    val path = withContext(Dispatchers.IO) { PhotoMatcher.saveReference(context, bmp) }
                    onDone(mission.copy(photo = path))
                }
            }, size = 76.dp, primary = true, modifier = Modifier.align(Alignment.CenterHorizontally))
        } else {
            val kind = if (mission.type == MissionType.QR) CodeKind.QR else CodeKind.BARCODE
            var captured by remember { mutableStateOf(false) }
            val analyzer = remember {
                CodeAnalyzer(kind) { value ->
                    view.post {
                        if (captured) return@post
                        captured = true
                        view.performHapticFeedback(android.view.HapticFeedbackConstants.CONFIRM)
                        onDone(mission.copy(codes = (mission.codes + value).distinct()))
                    }
                }
            }
            CameraFrame(Modifier.fillMaxWidth().weight(1f)) { CameraPreview(Modifier.fillMaxSize(), analyzer) }
            Spacer(Modifier.height(16.dp))
            Text("Hold the code in the frame", fontSize = 16.sp, color = c.textMuted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
    }
}

/** Which phrase lists a typing mission draws from. Browsing and adding phrases open their own windows. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun PhraseListCard(mission: Mission, onChange: (Mission) -> Unit) {
    val repo = com.hblaze.apexy.ApexyApp.instance.settings
    val settings by repo.settings.collectAsState(initial = com.hblaze.apexy.data.AppSettings())
    var browsing by remember { mutableStateOf(false) }
    var adding by remember { mutableStateOf(false) }
    NeuCard(Modifier.fillMaxWidth()) {
        SectionLabel("Phrase lists")
        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Phrases.ALL.forEach { id ->
                val on = id in mission.phrases
                com.hblaze.apexy.ui.neu.NeuChip(Phrases.name(id) + if (id == Phrases.CUSTOM) " · ${settings.customPhrases.size}" else "", on, {
                    val next = if (on) mission.phrases - id else mission.phrases + id
                    if (next.isNotEmpty()) onChange(mission.copy(phrases = next))
                })
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            NeuButton({ browsing = true }, Modifier.weight(1f)) {
                Icon(Icons.AutoMirrored.Rounded.List, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Phrases")
            }
            NeuButton({ adding = true }, Modifier.weight(1f)) {
                Icon(Icons.Rounded.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Add")
            }
        }
    }
    if (browsing) PhraseLibraryWindow(
        initial = mission.phrases.firstOrNull() ?: Phrases.SHORT,
        custom = settings.customPhrases,
        onAdd = { adding = true },
        onDismiss = { browsing = false },
    )
    if (adding) AddPhraseWindow(onDismiss = { adding = false })
}
