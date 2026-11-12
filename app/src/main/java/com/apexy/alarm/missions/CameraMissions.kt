package com.apexy.alarm.missions

import android.graphics.BitmapFactory
import android.view.HapticFeedbackConstants
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
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
import com.apexy.alarm.ui.neu.Neu
import com.apexy.alarm.ui.neu.NeuIconButton
import com.apexy.alarm.ui.neu.neu
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Neumorphic frame around a live camera feed. */
@Composable
fun CameraFrame(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier.neu(30.dp, elevation = 0.dp, inset = 7.dp).padding(12.dp)) {
        Box(Modifier.fillMaxSize().clip(RoundedCornerShape(22.dp))) { content() }
    }
}

@Composable
fun ScanMission(mission: Mission, cb: MissionCallbacks) {
    val c = Neu.colors
    val view = LocalView.current
    val kind = if (mission.type == MissionType.QR) CodeKind.QR else CodeKind.BARCODE
    var message by remember { mutableStateOf("Point the camera at your registered ${if (kind == CodeKind.QR) "QR code" else "barcode"}") }
    var lastWrong by remember { mutableLongStateOf(0L) }
    var done by remember { mutableStateOf(false) }
    val latestCb by rememberUpdatedState(cb)
    val analyzer = remember {
        CodeAnalyzer(kind) { value ->
            view.post {
                if (done) return@post
                if (value in mission.codes) {
                    done = true
                    view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                    latestCb.onComplete()
                } else if (System.currentTimeMillis() - lastWrong > 1500) {
                    lastWrong = System.currentTimeMillis()
                    latestCb.onInteraction()
                    message = "That's a different code — find the one you registered"
                }
            }
        }
    }
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        CameraFrame(Modifier.fillMaxWidth().weight(1f)) { CameraPreview(Modifier.fillMaxSize(), analyzer) }
        Spacer(Modifier.height(18.dp))
        Text(message, style = MaterialTheme.typography.titleMedium, color = c.textMuted, textAlign = TextAlign.Center)
    }
}

@Composable
fun PhotoMission(mission: Mission, cb: MissionCallbacks) {
    val c = Neu.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var preview by remember { mutableStateOf<PreviewView?>(null) }
    var checking by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("Go to your registered spot and take a matching photo") }
    val reference = remember(mission.photo) { BitmapFactory.decodeFile(mission.photo)?.asImageBitmap() }
    val threshold = PhotoMatcher.THRESHOLDS[mission.difficulty - 1]

    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        CameraFrame(Modifier.fillMaxWidth().weight(1f)) { Box(Modifier.fillMaxSize()) {
            CameraPreview(Modifier.fillMaxSize(), onReady = { preview = it })
            if (reference != null) {
                Image(
                    reference, "Registered photo", contentScale = ContentScale.Crop,
                    modifier = Modifier.align(Alignment.TopEnd).padding(10.dp).size(96.dp).clip(RoundedCornerShape(16.dp)),
                )
            }
        } }
        Spacer(Modifier.height(14.dp))
        Text(message, style = MaterialTheme.typography.titleMedium, color = c.textMuted, textAlign = TextAlign.Center)
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            if (checking) {
                CircularProgressIndicator(color = c.accent, modifier = Modifier.size(64.dp))
            } else {
                NeuIconButton(Icons.Rounded.PhotoCamera, "Take photo", {
                    val bitmap = preview?.bitmap ?: return@NeuIconButton
                    cb.onInteraction()
                    checking = true
                    scope.launch {
                        val score = withContext(Dispatchers.Default) {
                            runCatching { PhotoMatcher.similarity(context, bitmap, mission.photo) }.getOrDefault(0.0)
                        }
                        checking = false
                        if (score >= threshold) cb.onComplete()
                        else message = "Match ${(score * 100).toInt()}% — need ${(threshold * 100).toInt()}%"
                    }
                }, size = 76.dp, primary = true)
            }
        }
        Spacer(Modifier.width(1.dp))
    }
}
