package com.apexy.alarm.missions

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apexy.alarm.ui.neu.Neu
import com.apexy.alarm.ui.neu.NeuWell
import com.apexy.alarm.ui.neu.NumberPad
import com.apexy.alarm.ui.neu.neu
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlin.random.Random

// --- Math ---------------------------------------------------------------------------------------

object MathProblems {
    data class Problem(val text: String, val answer: Long)

    fun make(level: Int, rnd: Random = Random.Default): Problem {
        fun r(range: IntRange) = rnd.nextInt(range.first, range.last + 1)
        return when (level) {
            1 -> { val a = r(1..9); val b = r(1..9); Problem("$a + $b", (a + b).toLong()) }
            2 -> { val a = r(10..49); val b = r(10..49); Problem("$a + $b", (a + b).toLong()) }
            3 -> { val a = r(2..9); val b = r(11..19); val c = r(10..99); Problem("$a × $b + $c", (a * b + c).toLong()) }
            4 -> { val a = r(11..19); val b = r(3..9); val c = r(100..499); Problem("$a × $b + $c", (a * b + c).toLong()) }
            else -> { val a = r(11..29); val b = r(11..19); val c = r(100..999); Problem("$a × $b + $c", (a * b + c).toLong()) }
        }
    }
}

@Composable
fun MathMission(mission: Mission, cb: MissionCallbacks) {
    val c = Neu.colors
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    var solved by remember { mutableIntStateOf(0) }
    var problem by remember { mutableStateOf(MathProblems.make(mission.difficulty)) }
    var input by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    val shake = remember { Animatable(0f) }

    fun submit() {
        cb.onInteraction()
        if (input.toLongOrNull() == problem.answer) {
            solved++
            input = ""
            if (solved >= mission.count) cb.onComplete() else problem = MathProblems.make(mission.difficulty)
        } else {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            wrong = true
            input = ""
            scope.launch {
                for (x in listOf(18f, -14f, 10f, -6f, 0f)) shake.animateTo(x)
            }
        }
    }

    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Problem ${solved + 1} of ${mission.count}", style = MaterialTheme.typography.labelLarge, color = c.textMuted)
        Spacer(Modifier.height(18.dp))
        Text("${problem.text} =", fontSize = 40.sp, fontWeight = FontWeight.Light, color = c.text)
        Spacer(Modifier.height(18.dp))
        NeuWell(Modifier.fillMaxWidth().height(80.dp).offset { IntOffset(shake.value.roundToInt(), 0) }, cornerRadius = 24.dp) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    input.ifEmpty { if (wrong) "Try again" else "?" },
                    fontSize = 36.sp,
                    color = when { input.isNotEmpty() -> c.text; wrong -> c.danger; else -> c.textMuted },
                )
            }
        }
        Spacer(Modifier.weight(1f))
        NumberPad(
            onDigit = { if (input.length < 7) { input += it; wrong = false; cb.onInteraction() } },
            onBackspace = { input = input.dropLast(1); cb.onInteraction() },
            onSubmit = ::submit,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

// --- Memory -------------------------------------------------------------------------------------

private enum class MemoryPhase { SHOW, INPUT, SUCCESS, FAIL }

@Composable
fun MemoryMission(mission: Mission, cb: MissionCallbacks) {
    val c = Neu.colors
    val haptics = LocalHapticFeedback.current
    val grid = when (mission.difficulty) { 1, 2 -> 3; 3, 4 -> 4; else -> 5 }
    val tiles = 2 + mission.difficulty
    var round by remember { mutableIntStateOf(0) }
    var attempt by remember { mutableIntStateOf(0) }
    var pattern by remember { mutableStateOf(emptySet<Int>()) }
    var picked by remember { mutableStateOf(emptySet<Int>()) }
    var wrongTile by remember { mutableIntStateOf(-1) }
    var phase by remember { mutableStateOf(MemoryPhase.SHOW) }

    LaunchedEffect(round, attempt) {
        pattern = (0 until grid * grid).shuffled().take(tiles).toSet()
        picked = emptySet()
        wrongTile = -1
        phase = MemoryPhase.SHOW
        delay(1500L + 350L * tiles)
        phase = MemoryPhase.INPUT
    }
    LaunchedEffect(phase) {
        when (phase) {
            MemoryPhase.SUCCESS -> {
                delay(600)
                if (round + 1 >= mission.count) cb.onComplete() else round++
            }
            MemoryPhase.FAIL -> { delay(1200); attempt++ }
            else -> Unit
        }
    }

    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Round ${round + 1} of ${mission.count}", style = MaterialTheme.typography.labelLarge, color = c.textMuted)
        Spacer(Modifier.height(8.dp))
        Text(
            when (phase) {
                MemoryPhase.SHOW -> "Memorise the lit tiles"
                MemoryPhase.INPUT -> "Tap them (${picked.size}/$tiles)"
                MemoryPhase.SUCCESS -> "Correct!"
                MemoryPhase.FAIL -> "Not quite — new pattern"
            },
            style = MaterialTheme.typography.titleLarge,
            color = when (phase) { MemoryPhase.SUCCESS -> c.success; MemoryPhase.FAIL -> c.danger; else -> c.text },
        )
        Spacer(Modifier.height(28.dp))
        Column(Modifier.fillMaxWidth().aspectRatio(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            for (row in 0 until grid) {
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    for (col in 0 until grid) {
                        val i = row * grid + col
                        val lit = (phase == MemoryPhase.SHOW || phase == MemoryPhase.FAIL) && i in pattern
                        val isPicked = i in picked
                        val fill = when {
                            i == wrongTile -> c.danger
                            lit || isPicked -> c.primary
                            else -> null
                        }
                        Box(
                            Modifier
                                .weight(1f)
                                .fillMaxSize()
                                .neu(16.dp, elevation = if (isPicked) 0.dp else 5.dp, inset = if (isPicked) 4.dp else 0.dp, fill = fill)
                                .pointerInput(phase, i) {
                                    if (phase == MemoryPhase.INPUT) detectTapGestures {
                                        cb.onInteraction()
                                        if (i in picked) return@detectTapGestures
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        if (i in pattern) {
                                            picked = picked + i
                                            if (picked.size == pattern.size) phase = MemoryPhase.SUCCESS
                                        } else {
                                            wrongTile = i
                                            phase = MemoryPhase.FAIL
                                        }
                                    }
                                },
                        )
                    }
                }
            }
        }
    }
}

// --- Typing -------------------------------------------------------------------------------------

@Composable
fun TypingMission(mission: Mission, cb: MissionCallbacks) {
    val c = Neu.colors
    val custom = com.apexy.alarm.ApexyApp.instance.settings.settings
        .collectAsState(initial = null).value?.customPhrases ?: emptyList()
    var done by remember { mutableIntStateOf(0) }
    var phrase by remember(custom) { mutableStateOf(Phrases.pick(mission.phrases, mission.difficulty, custom)) }
    val target = phrase.text
    // Mixed-case "I aM aWaKe" lines only make sense if the capitals count.
    val exact = mission.difficulty >= 4 || phrase.exactCase
    var input by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    fun same(a: Char, b: Char) = if (exact) a == b else a.equals(b, ignoreCase = true)

    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Phrase ${done + 1} of ${mission.count}" + if (exact) " · case matters" else "", style = MaterialTheme.typography.labelLarge, color = c.textMuted)
        Spacer(Modifier.height(18.dp))
        Text(
            buildAnnotatedString {
                target.forEachIndexed { i, ch ->
                    val color = when {
                        i >= input.length -> c.textMuted
                        same(input[i], ch) -> c.success
                        else -> c.danger
                    }
                    withStyle(SpanStyle(color = color)) { append(ch) }
                }
            },
            fontSize = 22.sp, lineHeight = 32.sp,
            modifier = Modifier.fillMaxWidth().neu(22.dp, elevation = 6.dp).padding(22.dp),
        )
        Spacer(Modifier.height(22.dp))
        NeuWell(Modifier.fillMaxWidth(), cornerRadius = 20.dp) {
            BasicTextField(
                value = input,
                onValueChange = { v ->
                    cb.onInteraction()
                    input = v.take(target.length + 5)
                    val matches = input.length == target.length && input.indices.all { same(input[it], target[it]) }
                    if (matches) {
                        done++
                        input = ""
                        if (done >= mission.count) cb.onComplete() else phrase = Phrases.pick(mission.phrases, mission.difficulty, custom, avoid = target)
                    }
                },
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = c.text, fontSize = 18.sp),
                cursorBrush = SolidColor(c.primary),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    autoCorrectEnabled = false,
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.None,
                ),
                modifier = Modifier.fillMaxWidth().padding(18.dp).focusRequester(focus),
            )
        }
    }
}
