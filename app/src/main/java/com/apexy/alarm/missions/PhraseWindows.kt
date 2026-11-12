package com.apexy.alarm.missions

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.EditNote
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.platform.LocalView
import androidx.compose.runtime.SideEffect
import android.view.WindowManager
import com.apexy.alarm.ApexyApp
import com.apexy.alarm.ui.neu.EmptyHint
import com.apexy.alarm.ui.neu.Neu
import com.apexy.alarm.ui.neu.NeuButton
import com.apexy.alarm.ui.neu.NeuChip
import com.apexy.alarm.ui.neu.NeuIconButton
import com.apexy.alarm.ui.neu.NeuTextField
import com.apexy.alarm.ui.neu.neu
import kotlinx.coroutines.launch

/** A full-screen window of its own, so long lists never stretch the mission settings. */
@Composable
private fun FullWindow(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    val c = Neu.colors
    Dialog(onDismiss, DialogProperties(usePlatformDefaultWidth = false)) {
        // No dimming, so the strip under the status bar shows the matching screen behind; resize for the keyboard.
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect {
            window?.setDimAmount(0f)
            @Suppress("DEPRECATION")
            window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
        Box(Modifier.fillMaxSize().background(c.surface)) { content() }
    }
}

@Composable
private fun WindowHeader(title: String, onBack: () -> Unit, trailing: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        NeuIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Back", onBack, size = 46.dp)
        Text(title, style = MaterialTheme.typography.titleLarge, color = Neu.colors.text, modifier = Modifier.padding(start = 16.dp).weight(1f))
        trailing()
    }
}

/** Every built-in phrase, one list at a time, plus the user's own (removable) phrases. */
@Composable
fun PhraseLibraryWindow(initial: String, custom: List<String>, onAdd: () -> Unit, onDismiss: () -> Unit) {
    val c = Neu.colors
    val scope = rememberCoroutineScope()
    val repo = ApexyApp.instance.settings
    var list by remember { mutableStateOf(initial) }
    FullWindow(onDismiss) {
        Column(Modifier.fillMaxSize()) {
            WindowHeader("Phrases", onDismiss) { NeuIconButton(Icons.Rounded.Add, "Add phrase", onAdd, size = 46.dp, primary = true) }
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Phrases.ALL.forEach { id -> NeuChip(Phrases.name(id), id == list, { list = id }) }
            }
            val lines = if (list == Phrases.CUSTOM) custom else Phrases.builtIn(list)
            if (lines.isEmpty()) {
                EmptyHint(Icons.Rounded.EditNote, "No phrases yet", Modifier.padding(top = 40.dp))
            } else {
                LazyColumn(
                    Modifier.weight(1f),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(lines, key = { it }) { p ->
                        Row(
                            Modifier.fillMaxWidth().neu(16.dp, elevation = 0.dp, inset = 3.dp)
                                .padding(start = 16.dp, top = if (list == Phrases.CUSTOM) 4.dp else 14.dp, bottom = if (list == Phrases.CUSTOM) 4.dp else 14.dp, end = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(p, style = MaterialTheme.typography.bodyLarge, color = c.text, modifier = Modifier.weight(1f).padding(end = 8.dp))
                            if (list == Phrases.CUSTOM) {
                                NeuIconButton(Icons.Rounded.Close, "Remove", { scope.launch { repo.setCustomPhrases(custom - p) } }, size = 40.dp)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Write one phrase for the "Custom" list. */
@Composable
fun AddPhraseWindow(onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val repo = ApexyApp.instance.settings
    var draft by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    fun save() {
        val p = draft.trim()
        if (p.isEmpty()) return
        scope.launch {
            val current = repo.currentCustomPhrases()
            repo.setCustomPhrases((current + p).distinct())
            onDismiss()
        }
    }
    FullWindow(onDismiss) {
        Column(Modifier.fillMaxSize()) {
            WindowHeader("New phrase", onDismiss) {
                NeuButton(::save, primary = true, enabled = draft.isNotBlank()) { Text("Save") }
            }
            Column(Modifier.weight(1f).padding(horizontal = 20.dp, vertical = 8.dp)) {
                NeuTextField(draft, { draft = it.take(160) }, "Type a phrase", singleLine = false, focusRequester = focus)
                Spacer(Modifier.height(8.dp))
                Text("${draft.trim().length}/160", style = MaterialTheme.typography.labelMedium, color = Neu.colors.textMuted, modifier = Modifier.align(Alignment.End).padding(end = 6.dp))
            }
        }
    }
}
