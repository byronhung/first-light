package com.byronhung.firstlight.ring

import android.os.SystemClock
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.byronhung.firstlight.alarm.WakeCheck
import com.byronhung.firstlight.alarm.clockDigits
import com.byronhung.firstlight.alarm.formatDuration
import com.byronhung.firstlight.alarm.formatTime
import com.byronhung.firstlight.alarm.period
import com.byronhung.firstlight.data.RoundType
import com.byronhung.firstlight.data.WakeOutcome
import com.byronhung.firstlight.puzzle.Levels
import com.byronhung.firstlight.puzzle.OddOneOutPuzzle
import com.byronhung.firstlight.puzzle.PatternPuzzle
import com.byronhung.firstlight.puzzle.PairsPuzzle
import com.byronhung.firstlight.puzzle.PathPuzzle
import com.byronhung.firstlight.puzzle.QrPuzzle
import com.byronhung.firstlight.puzzle.SlidePuzzle
import com.byronhung.firstlight.puzzle.StroopPuzzle
import com.byronhung.firstlight.puzzle.cantScanRounds
import com.byronhung.firstlight.ui.components.HoldToGiveUp
import com.byronhung.firstlight.ui.components.Pips
import com.byronhung.firstlight.ui.components.SkyBackground
import com.byronhung.firstlight.ui.components.springClick
import com.byronhung.firstlight.ui.theme.AppShapes
import com.byronhung.firstlight.ui.theme.AppText
import com.byronhung.firstlight.ui.theme.Motion
import com.byronhung.firstlight.ui.theme.LocalSkyTheme
import com.byronhung.firstlight.ui.theme.Sun
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalTime
import kotlin.random.Random

const val GIVE_UP_HOLD_MS = 30_000L

private sealed interface Ending {
    data class Solved(val totalMs: Long, val checks: Int, val noScan: Boolean, val test: Boolean = false) : Ending
    data class GaveUp(val test: Boolean = false) : Ending
}

@Composable
fun RingRoute(controller: RingController, is24Hour: Boolean, onClose: () -> Unit) {
    val state by controller.state.collectAsStateWithLifecycle()
    val lastTap by controller.lastTap.collectAsStateWithLifecycle()
    // The same rule the service rings by, re-checked often enough that the hint flips with the sound.
    val quiet by produceState(false, lastTap) {
        while (true) {
            value = QuietWhileSolving.isQuiet(lastTap, SystemClock.elapsedRealtime())
            delay(250)
        }
    }
    // Set the moment the ring ends here, so the closing screen shows instead of an instant exit.
    var ending by remember { mutableStateOf<Ending?>(null) }

    SkyBackground(LocalSkyTheme.current.dawn, drift = true, scene = false) {
        val finished = ending
        when {
            finished != null -> EndScreen(finished, is24Hour, onClose)
            state is RingState.Ringing -> {
                val session = (state as RingState.Ringing).session
                RingScreen(
                    session = session,
                    is24Hour = is24Hour,
                    quiet = quiet,
                    onPuzzleTap = { controller.puzzleTapped() },
                    onSolved = { results, totalMs, noScan ->
                        ending = Ending.Solved(totalMs, session.wakeChecks, noScan, session.isTest)
                        controller.finish(if (noScan) WakeOutcome.NO_SCAN else WakeOutcome.SOLVED, results)
                    },
                    onGiveUp = { results ->
                        ending = Ending.GaveUp(session.isTest)
                        controller.finish(WakeOutcome.GAVE_UP, results)
                    },
                )
            }
            state == RingState.Starting -> Unit
            else -> LaunchedEffect(Unit) {
                // Idle. The alarm receiver can open this screen a moment before the service reports
                // Starting, so wait briefly before deciding nothing is ringing.
                delay(3_000)
                onClose()
            }
        }
    }
}

/**
 * The round harness: the same frame for every puzzle. Clock and round markers at the top, the
 * puzzle card in the middle (each new round slides in), the give-up ring at the bottom. Puzzles
 * only report onMiss / onSolved; timing and results are kept here.
 */
@Composable
private fun RingScreen(
    session: RingSession,
    is24Hour: Boolean,
    quiet: Boolean,
    onPuzzleTap: () -> Unit,
    onSolved: (List<RoundResultDraft>, Long, Boolean) -> Unit,
    onGiveUp: (List<RoundResultDraft>) -> Unit,
) {
    var rounds by remember { mutableStateOf(session.rounds) }
    // Where "Can't scan now?" puzzles start, once tapped. They're all top level and never logged as
    // results: a forced Hard round would drag the automatic difficulty back to the top.
    var cantScanFrom by remember { mutableStateOf<Int?>(null) }
    var index by remember { mutableIntStateOf(0) }
    val results = remember { mutableStateListOf<RoundResultDraft>() }
    val firstStart = remember { SystemClock.elapsedRealtime() }
    var roundStart by remember { mutableLongStateOf(firstStart) }
    var misses by remember { mutableIntStateOf(0) }

    val now by produceState(LocalTime.now()) {
        while (true) {
            delay(1_000 - System.currentTimeMillis() % 1_000)
            value = LocalTime.now()
        }
    }

    fun levelAt(i: Int): Int = cantScanFrom?.takeIf { i >= it }?.let { Levels.MAX } ?: levelOf(session, rounds[i])

    fun roundSolved() {
        val type = rounds[index]
        val t = SystemClock.elapsedRealtime()
        if (cantScanFrom == null) results += RoundResultDraft(type, levelAt(index), t - roundStart, misses)
        misses = 0
        roundStart = t
        if (index == rounds.lastIndex) onSolved(results.toList(), t - firstStart, cantScanFrom != null) else index++
    }

    fun cantScan() {
        cantScanFrom = index
        rounds = cantScanRounds(rounds, session.mix, Random.Default)
        misses = 0
        roundStart = SystemClock.elapsedRealtime()
    }

    // Short screens (a flip phone's cover screen, a phone in landscape) get a one-line header and a
    // smaller give-up control, so the puzzle keeps as much room as it can.
    BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
    val compact = maxHeight < COMPACT_HEIGHT
    // A scan-only alarm is one step, not "round 1 of 1".
    val scanOnly = rounds == listOf(RoundType.QR_SCAN)
    val roundLabel = if (scanOnly) "SCAN YOUR CODE TO STOP IT" else "ROUND ${index + 1} OF ${rounds.size}"
    // Without this, nobody finds out that tapping silences it: on the scan round there's
    // nothing to tap.
    val quietHint = if (quiet) "Quiet while you keep tapping" else "Tap the screen to quiet it"
    Column(
        Modifier
            .fillMaxSize()
            .padding(start = if (compact) 18.dp else 24.dp, end = if (compact) 18.dp else 24.dp, top = if (compact) 8.dp else 30.dp, bottom = if (compact) 8.dp else 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (compact) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(clockDigits(now.hour, now.minute, is24Hour), style = AppText.ringClock.copy(fontSize = 36.sp, lineHeight = 36.sp), color = Sun.OnGlass)
                    if (!is24Hour) {
                        Text(period(now.hour), style = AppText.period.copy(fontSize = 13.sp), color = Sun.OnGlass.copy(alpha = 0.75f), modifier = Modifier.padding(bottom = 4.dp))
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(roundLabel, style = AppText.label.copy(letterSpacing = 0.14.em), color = Sun.OnGlass.copy(alpha = 0.7f))
                    Text(quietHint, style = AppText.caption.copy(fontSize = 12.sp), color = Sun.OnGlass.copy(alpha = if (quiet) 0.55f else 0.85f))
                }
            }
        } else {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(clockDigits(now.hour, now.minute, is24Hour), style = AppText.ringClock, color = Sun.OnGlass)
                if (!is24Hour) {
                    Text(period(now.hour), style = AppText.period.copy(fontSize = 20.sp), color = Sun.OnGlass.copy(alpha = 0.75f), modifier = Modifier.padding(bottom = 10.dp))
                }
            }
            Text(session.label.ifBlank { "Alarm" }, style = AppText.caption.copy(fontSize = 15.sp), color = Sun.OnGlass.copy(alpha = 0.75f))
            Spacer(Modifier.height(20.dp))
            if (!scanOnly) {
                Pips(done = index, total = rounds.size)
                Spacer(Modifier.height(10.dp))
            }
            Text(roundLabel, style = AppText.label.copy(letterSpacing = 0.18.em), color = Sun.OnGlass.copy(alpha = 0.7f))
            Text(quietHint, style = AppText.caption, color = Sun.OnGlass.copy(alpha = if (quiet) 0.55f else 0.85f), modifier = Modifier.padding(top = 6.dp))
        }

        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(vertical = if (compact) 8.dp else 14.dp)
                // Watches touches on the way down without taking them: the puzzle still gets every tap.
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            if (awaitPointerEvent(PointerEventPass.Initial).type == PointerEventType.Press) onPuzzleTap()
                        }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            FitToHeight(PUZZLE_DESIGN_HEIGHT) {
                AnimatedContent(
                    // Keyed on the round's type too, so the "Can't scan now?" swap slides in like a new round.
                    targetState = index to rounds[index],
                    transitionSpec = {
                        (slideInHorizontally(Motion.settle()) { it / 6 } + fadeIn(tween(300)) + scaleIn(Motion.settle(), initialScale = 0.96f))
                            .togetherWith(fadeOut(tween(150)))
                    },
                    label = "round",
                ) { (i, type) ->
                    PuzzleCard {
                        val level = levelAt(i)
                        val onMiss: () -> Unit = { misses++ }
                        when (type) {
                            RoundType.STROOP -> StroopPuzzle(level, onMiss, ::roundSolved)
                            RoundType.PATTERN_FLASH -> PatternPuzzle(level, onMiss, ::roundSolved)
                            RoundType.ODD_ONE_OUT -> OddOneOutPuzzle(level, onMiss, ::roundSolved)
                            RoundType.QR_SCAN -> QrPuzzle(session.qrCode.orEmpty(), onMiss, ::roundSolved, onCantScan = ::cantScan.takeIf { session.mix.isNotEmpty() })
                            RoundType.PAIRS -> PairsPuzzle(level, ::roundSolved)
                            RoundType.PATH -> PathPuzzle(level, ::roundSolved)
                            RoundType.SLIDE -> SlidePuzzle(level, ::roundSolved)
                        }
                    }
                }
            }
        }

        HoldToGiveUp(GIVE_UP_HOLD_MS, onGiveUp = { onGiveUp(results.toList()) }, modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp), compact = compact)
    }
    }
}

/** Below this height the ringing screen goes compact. A Flip's cover screen is about 400 dp. */
private val COMPACT_HEIGHT = 600.dp

/** The height every puzzle is laid out for. Less room than this and the puzzle is zoomed out to fit. */
private val PUZZLE_DESIGN_HEIGHT = 400.dp

/**
 * Lays [content] out at [design] height and shrinks it to fit when there's less room, like zooming
 * out, instead of squashing it. Taps still land: Compose maps touches through the scale.
 */
@Composable
private fun FitToHeight(design: Dp, content: @Composable () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (maxHeight >= design) {
            content()
        } else {
            val scale = maxHeight / design
            Box(
                Modifier
                    .requiredSize(maxWidth / scale, design)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    },
                contentAlignment = Alignment.Center,
            ) { content() }
        }
    }
}

/** Dark glass, so the puzzle colours read on a bright dawn. */
@Composable
private fun PuzzleCard(content: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .graphicsLayer {
                shape = AppShapes.puzzle
                clip = true
            }
            .background(Sun.PuzzleGlass)
            .drawBehind {
                drawRoundRect(Color.White.copy(alpha = 0.14f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(30.dp.toPx()), style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
            }
            .padding(start = 18.dp, end = 18.dp, top = 22.dp, bottom = 18.dp),
        contentAlignment = Alignment.Center,
    ) { content() }
}

private fun levelOf(session: RingSession, type: RoundType): Int =
    if (type == RoundType.QR_SCAN) 0 else session.levels[type] ?: Levels.DEFAULT

/** "Good morning" in a sunburst, or the give-up note. Tap anywhere, or it closes itself. */
@Composable
private fun EndScreen(ending: Ending, is24Hour: Boolean, onClose: () -> Unit) {
    LaunchedEffect(Unit) {
        delay(8_000)
        onClose()
    }
    val halo = remember { Animatable(0f) }
    val rays = remember { Animatable(0f) }
    val text = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        launch { halo.animateTo(1f, Motion.bouncy()) }
        launch { rays.animateTo(1f, tween(1_200, easing = Motion.softOut)) }
        delay(350)
        text.animateTo(1f, tween(700, easing = Motion.softOut))
    }

    Box(
        Modifier
            .fillMaxSize()
            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onClose),
    ) {
        if (ending is Ending.Solved) {
            Box(
                Modifier
                    .fillMaxSize()
                    .drawBehind {
                        val c = Offset(size.width / 2, size.height * 0.4f)
                        // Twelve rays growing out from the sun.
                        for (k in 0 until 12) {
                            val a = Math.toRadians(k * 30.0 - 90.0)
                            val inner = 50.dp.toPx()
                            val outer = inner + 120.dp.toPx() * rays.value
                            drawLine(
                                Brush.linearGradient(
                                    listOf(Color.Transparent, Sun.Glow),
                                    start = Offset(c.x + (inner * Math.cos(a)).toFloat(), c.y + (inner * Math.sin(a)).toFloat()),
                                    end = Offset(c.x + (outer * Math.cos(a)).toFloat(), c.y + (outer * Math.sin(a)).toFloat()),
                                ),
                                start = Offset(c.x + (inner * Math.cos(a)).toFloat(), c.y + (inner * Math.sin(a)).toFloat()),
                                end = Offset(c.x + (outer * Math.cos(a)).toFloat(), c.y + (outer * Math.sin(a)).toFloat()),
                                strokeWidth = 4.dp.toPx(),
                                cap = StrokeCap.Round,
                                alpha = rays.value,
                            )
                        }
                        drawCircle(
                            Brush.radialGradient(
                                0f to Color(0xFFFFE6BC), 0.5f to Color(0x80FFAA6E), 0.7f to Color.Transparent,
                                center = c, radius = 110.dp.toPx() * halo.value.coerceAtLeast(0.01f),
                            ),
                            radius = 110.dp.toPx() * halo.value.coerceAtLeast(0f),
                            center = c,
                        )
                    },
            )
        }
        Column(
            Modifier
                .align(if (ending is Ending.Solved) Alignment.BottomCenter else Alignment.Center)
                .safeDrawingPadding()
                .padding(start = 28.dp, end = 28.dp, bottom = 70.dp)
                .graphicsLayer {
                    alpha = text.value
                    translationY = (1f - text.value) * 24.dp.toPx()
                },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            when (ending) {
                is Ending.Solved -> {
                    Text("Good morning", style = AppText.display.copy(fontSize = 54.sp), color = Sun.OnGlass)
                    Text("Solved in ${formatDuration(ending.totalMs)}", style = AppText.bodyStrong.copy(fontSize = 18.sp), color = Sun.Glow)
                    if (ending.test) {
                        Text("That was a test. Nothing went in History.", style = AppText.body.copy(fontSize = 15.sp), color = Sun.OnGlass.copy(alpha = 0.82f))
                    } else if (ending.noScan) {
                        Text("Logged as no scan in History.", style = AppText.body.copy(fontSize = 15.sp), color = Sun.OnGlass.copy(alpha = 0.82f))
                    }
                    if (ending.checks > 0) {
                        val at = LocalTime.now().plusSeconds(WakeCheck.delayMs / 1000)
                        Text(
                            if (ending.checks == 1) "Wake check at ${formatTime(at.hour, at.minute, is24Hour)}."
                            else "First wake check at ${formatTime(at.hour, at.minute, is24Hour)}. ${ending.checks} checks, five minutes apart.",
                            style = AppText.body.copy(fontSize = 15.sp),
                            color = Sun.OnGlass.copy(alpha = 0.82f),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.widthIn(max = 290.dp),
                        )
                    }
                }
                is Ending.GaveUp -> {
                    Text("Alarm stopped", style = AppText.display.copy(fontSize = 48.sp), color = Sun.OnGlass)
                    Text(
                        if (ending.test) "That was a test. Nothing went in History." else "Logged as a give-up in History.",
                        style = AppText.body,
                        color = Sun.OnGlass.copy(alpha = 0.82f),
                    )
                }
            }
            Box(
                Modifier
                    .padding(top = 18.dp)
                    .springClick(0.92f, onClick = onClose)
                    .height(56.dp)
                    .graphicsLayer {
                        shape = AppShapes.button
                        clip = true
                    }
                    .background(Color.White.copy(alpha = 0.92f))
                    .padding(horizontal = 34.dp),
                contentAlignment = Alignment.Center,
            ) { Text("Close", style = AppText.button, color = Sun.OnAmber) }
        }
    }
}
