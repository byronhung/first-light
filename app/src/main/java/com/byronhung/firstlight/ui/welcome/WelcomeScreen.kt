package com.byronhung.firstlight.ui.welcome

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import com.byronhung.firstlight.ui.theme.Motion
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import com.byronhung.firstlight.ui.theme.SkyTheme
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.byronhung.firstlight.puzzle.Levels
import com.byronhung.firstlight.puzzle.StroopPuzzle
import com.byronhung.firstlight.ui.components.AmberButton
import com.byronhung.firstlight.ui.components.LogoMark
import com.byronhung.firstlight.ui.components.QuietButton
import com.byronhung.firstlight.ui.components.SectionLabel
import com.byronhung.firstlight.ui.components.SkyBackground
import com.byronhung.firstlight.ui.components.springClick
import com.byronhung.firstlight.ui.list.SetupRow
import com.byronhung.firstlight.ui.list.rememberSetupItems
import com.byronhung.firstlight.ui.theme.AppShapes
import com.byronhung.firstlight.ui.theme.AppText
import com.byronhung.firstlight.ui.theme.LocalSky
import com.byronhung.firstlight.ui.theme.LocalSkyTheme
import com.byronhung.firstlight.ui.theme.Sun
import kotlinx.coroutines.delay

private const val STEPS = 7
private const val PERMISSIONS_STEP = 6

/**
 * The first-run welcome, from the approved mockup (claude.ai/artifact/3BdabayPCjA5BD57hGj6LC):
 * what the app is, a real puzzle to play, tap-to-quiet, a wake check, registering a code, where
 * your mornings are kept, and the permissions alarms need. Skip jumps to the permissions, since those are the one step that really matters.
 * Settings › "How First Light works" replays it ([replay]: no "set my first alarm" at the end).
 *
 * [wakeCode] is the registered code, if any; [onScan] opens the real scanner, which saves the code
 * through Settings' usual path and comes back here.
 */
@Composable
fun WelcomeScreen(wakeCode: String?, replay: Boolean, onScan: () -> Unit, onDone: () -> Unit) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    BackHandler(enabled = step > 0) { step-- }

    SkyBackground(LocalSkyTheme.current.dawn) {
        Column(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(start = 24.dp, end = 24.dp, top = 18.dp, bottom = 22.dp),
        ) {
            Row(Modifier.fillMaxWidth().height(44.dp), verticalAlignment = Alignment.CenterVertically) {
                Pips(step, Modifier.weight(1f))
                if (step < PERMISSIONS_STEP) {
                    Text(
                        "Skip",
                        style = AppText.bodyStrong,
                        color = LocalSky.current.dim,
                        modifier = Modifier.springClick(0.9f) { step = PERMISSIONS_STEP }.padding(horizontal = 6.dp, vertical = 10.dp),
                    )
                }
            }
            AnimatedContent(
                targetState = step,
                // A page turn: forward slides in from the right and out to the left, Back the reverse.
                // The old page moves too, so a step whose layout changes a lot doesn't read as a snap.
                transitionSpec = {
                    val dir = if (targetState > initialState) 1 else -1
                    (slideInHorizontally(tween(420, easing = Motion.softOut)) { dir * it / 3 } + fadeIn(tween(320, delayMillis = 60)))
                        .togetherWith(slideOutHorizontally(tween(420, easing = Motion.softOut)) { -dir * it / 3 } + fadeOut(tween(220)))
                },
                modifier = Modifier.weight(1f),
                label = "welcome",
            ) { s ->
                Column(Modifier.fillMaxSize()) {
                    when (s) {
                        0 -> Hello(next = { step = 1 })
                        1 -> TryOne(next = { step = 2 })
                        2 -> Quiet(next = { step = 3 })
                        3 -> WakeCheckDemo(next = { step = 4 })
                        4 -> Scan(wakeCode, onScan, next = { step = 5 })
                        5 -> YourMonth(next = { step = 6 })
                        else -> Permissions(replay, onDone)
                    }
                }
            }
        }
    }
}

@Composable
private fun Pips(step: Int, modifier: Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.Center) {
        // Balance the Skip button on the right so the pips sit in the middle.
        Spacer(Modifier.width(40.dp))
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)) {
            repeat(STEPS) { i ->
                Box(
                    Modifier
                        .size(width = if (i == step) 30.dp else 22.dp, height = 5.dp)
                        .clip(AppShapes.small)
                        .background(if (i == step) Sun.ToggleLight else Sun.OnGlass.copy(alpha = 0.22f)),
                )
            }
        }
    }
}

/** The shared layout of a step: label, title, body, whatever the step shows, and its buttons. */
@Composable
private fun ColumnScope.Step(
    label: String,
    title: String,
    body: String?,
    buttons: @Composable ColumnScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    val sky = LocalSky.current
    Column(
        Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
    ) {
        SectionLabel(label, color = sky.sunInk)
        Text(title, style = AppText.display.copy(fontSize = 32.sp, lineHeight = 36.sp), color = sky.ink)
        if (body != null) Text(body, style = AppText.body, color = sky.dim)
        content()
    }
    Column(Modifier.fillMaxWidth().padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { buttons() }
}

@Composable
private fun ColumnScope.Hello(next: () -> Unit) {
    Step(
        label = "Welcome to First Light",
        title = "An alarm that makes sure you're up.",
        body = "No snooze. To stop it, you solve a few quick puzzles, or walk to a code and scan it. " +
            "The only other way out is holding a button for 30 seconds, and that gets written down.",
        buttons = { AmberButton("Show me", next, Modifier.fillMaxWidth(), height = 56.dp) },
    ) {
        LogoMark(Sun.Glow, size = 84.dp)
    }
}

/** A real Stroop round at the easiest level: the fastest way to get what a morning feels like. */
@Composable
private fun ColumnScope.TryOne(next: () -> Unit) {
    var solved by remember { mutableStateOf(false) }
    var misses by remember { mutableIntStateOf(0) }
    Step(
        label = "Try one",
        title = "Each morning is a few rounds like this.",
        body = null,
        buttons = { AmberButton(if (solved) "Next" else "Skip the puzzle", next, Modifier.fillMaxWidth(), height = 56.dp) },
    ) {
        PuzzleGlass {
            AnimatedContent(solved, transitionSpec = { fadeIn(tween(300)).togetherWith(fadeOut(tween(200))) }, label = "solved") { done ->
                if (!done) StroopPuzzle(Levels.MIN, onMiss = { misses++ }, onSolved = { solved = true })
                else Text("Nice. That's one round.", style = AppText.bodyStrong, color = Sun.Glow, textAlign = TextAlign.Center)
            }
        }
        if (!solved && misses > 0) {
            Text("Read what it asks: the ink colour, or the word.", style = AppText.caption, color = LocalSky.current.dim)
        }
        // The one mention of Plus in the welcome: a line and a glimpse of the skies, no sell.
        AnimatedVisibility(solved, enter = fadeIn(tween(400, delayMillis = 250)) + expandVertically(tween(400, delayMillis = 150))) { PlusHint() }
    }
}

@Composable
private fun PlusHint() {
    val sky = LocalSky.current
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            "Plus adds three more puzzles and four skies. Try them any time in Settings.",
            style = AppText.body,
            color = sky.dim,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            SkyTheme.entries.filter { it.plus }.forEach { theme ->
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(
                        Modifier
                            .size(56.dp)
                            .clip(AppShapes.small)
                            .background(Brush.linearGradient(theme.swatch)),
                    )
                    Text(theme.label.substringBefore(' '), style = AppText.chip.copy(fontSize = 11.sp), color = sky.dim)
                }
            }
        }
    }
}

/** Tap-to-quiet, shown on a pretend ringing alarm. Same 10 s rule as a real ring. */
@Composable
private fun ColumnScope.Quiet(next: () -> Unit) {
    var lastTap by remember { mutableLongStateOf(0L) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(250)
            now = System.currentTimeMillis()
        }
    }
    val quiet = lastTap != 0L && now - lastTap < 10_000
    Step(
        label = "Quiet while you solve",
        title = "Somewhere quiet? Tap to hush it.",
        body = "Tapping the puzzle silences the alarm while you work. Stop for 10 seconds and it comes back " +
            "at full volume, so you can't drift off.",
        buttons = { AmberButton("Next", next, Modifier.fillMaxWidth(), height = 56.dp) },
    ) {
        PuzzleGlass(
            Modifier
                .springClick(0.97f) {
                    lastTap = System.currentTimeMillis()
                    now = lastTap
                }
                .semantics { contentDescription = if (quiet) "Quiet. Tap again to keep it quiet." else "Ringing alarm. Tap to quiet it." },
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("6:30", style = AppText.heroClock.copy(fontSize = 64.sp), color = Sun.OnGlass)
                SoundBars(ringing = !quiet)
                Text(
                    when {
                        quiet -> "Quiet while you keep tapping"
                        lastTap != 0L -> "Ringing again"
                        else -> "Ringing · tap me"
                    },
                    style = AppText.bodyStrong,
                    color = if (quiet) Sun.Glow else Sun.Rose,
                )
            }
        }
    }
}

@Composable
private fun SoundBars(ringing: Boolean) {
    val t = rememberInfiniteTransition(label = "bars")
    val phase by t.animateFloat(0f, 1f, infiniteRepeatable(tween(500), RepeatMode.Reverse), label = "bar")
    Canvas(Modifier.size(width = 90.dp, height = 50.dp)) {
        val n = 7
        val gap = size.width / n
        for (i in 0 until n) {
            val wave = ((phase + i * 0.17f) % 1f)
            val h = if (ringing) size.height * (0.2f + 0.8f * wave) else size.height * 0.16f
            drawLine(
                Sun.ToggleLight, Offset(gap * (i + 0.5f), size.height / 2 - h / 2), Offset(gap * (i + 0.5f), size.height / 2 + h / 2),
                strokeWidth = gap * 0.55f, cap = StrokeCap.Round,
            )
        }
    }
}

/**
 * A wake check, tried once: the "Still awake?" ring that comes a few minutes after you solve an
 * alarm. The real one gives a minute; the demo gives 15 seconds so nobody waits around.
 */
@Composable
private fun ColumnScope.WakeCheckDemo(next: () -> Unit) {
    var started by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var now by remember { mutableLongStateOf(started) }
    var passed by remember { mutableStateOf(false) }
    LaunchedEffect(started, passed) {
        while (!passed) {
            delay(100)
            now = System.currentTimeMillis()
        }
    }
    val left = (DEMO_CHECK_MS - (now - started)).coerceAtLeast(0)
    val missed = !passed && left == 0L
    Step(
        label = "Wake checks",
        title = "Five minutes later: still awake?",
        body = "Turn them on for an alarm and, after you solve it, it checks in. Tap I'm up within a minute, " +
            "or it rings again from round 1. If you're already using your phone, it passes on its own. Off by default, up to 3 per alarm.",
        buttons = { AmberButton("Next", next, Modifier.fillMaxWidth(), height = 56.dp) },
    ) {
        PuzzleGlass {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when {
                    passed -> Text("That's a wake check passed.", style = AppText.bodyStrong, color = Sun.Glow, textAlign = TextAlign.Center)
                    missed -> {
                        Text("Missed it. A real one would ring again now.", style = AppText.bodyStrong, color = Sun.Rose, textAlign = TextAlign.Center)
                        QuietButton("Try again", {
                            started = System.currentTimeMillis()
                            now = started
                        })
                    }
                    else -> {
                        Text("Still awake?", style = AppText.title, color = Sun.OnGlass)
                        Box(contentAlignment = Alignment.Center) {
                            Canvas(Modifier.size(150.dp)) {
                                val stroke = 6.dp.toPx()
                                val inset = stroke / 2
                                val arc = Size(size.width - stroke, size.height - stroke)
                                drawArc(Sun.OnGlass.copy(alpha = 0.15f), 0f, 360f, false, Offset(inset, inset), arc, style = androidx.compose.ui.graphics.drawscope.Stroke(stroke))
                                drawArc(Sun.ToggleLight, -90f, 360f * left / DEMO_CHECK_MS, false, Offset(inset, inset), arc, style = androidx.compose.ui.graphics.drawscope.Stroke(stroke, cap = StrokeCap.Round))
                            }
                            Box(
                                Modifier
                                    .size(112.dp)
                                    .clip(CircleShape)
                                    .background(Sun.amberBrush)
                                    .springClick(0.9f) { passed = true }
                                    .semantics { contentDescription = "I'm up" },
                                contentAlignment = Alignment.Center,
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("I'm up", style = AppText.button, color = Sun.OnAmber)
                                    Text("${(left + 999) / 1000} s", style = AppText.caption, color = Sun.OnAmber.copy(alpha = 0.7f))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private const val DEMO_CHECK_MS = 15_000L

/** Registering the wake-up code: the real scanner, or later. */
@Composable
private fun ColumnScope.Scan(wakeCode: String?, onScan: () -> Unit, next: () -> Unit) {
    Step(
        label = "Or scan to stop it",
        title = "Pick something away from your bed.",
        body = "Toothpaste, a cereal box, the kettle: any barcode or QR code. Out when an alarm goes off? " +
            "“Can't scan now?” swaps the scan for two hard puzzles.",
        buttons = {
            if (wakeCode == null) {
                AmberButton("Scan a code", onScan, Modifier.fillMaxWidth(), height = 56.dp)
                QuietButton("Later", next, Modifier.fillMaxWidth())
            } else {
                AmberButton("Next", next, Modifier.fillMaxWidth(), height = 56.dp)
            }
        },
    ) {
        Viewfinder()
        if (wakeCode != null) {
            Text("Saved · $wakeCode", style = AppText.bodyStrong, color = Sun.Glow)
        }
    }
}

/** A drawn viewfinder: corners, a barcode, and a scan line sweeping over it. */
@Composable
private fun Viewfinder() {
    val t = rememberInfiniteTransition(label = "scan")
    val sweep by t.animateFloat(0f, 1f, infiniteRepeatable(tween(2000), RepeatMode.Reverse), label = "line")
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(180.dp)
            .clip(AppShapes.puzzle)
            .background(Color(0xFF221E2B)),
    ) {
        val c = Sun.Glow
        val inset = 22.dp.toPx()
        val arm = 26.dp.toPx()
        val sw = 3.dp.toPx()
        for ((x, y, dx, dy) in listOf(
            listOf(inset, inset, 1f, 1f), listOf(size.width - inset, inset, -1f, 1f),
            listOf(inset, size.height - inset, 1f, -1f), listOf(size.width - inset, size.height - inset, -1f, -1f),
        )) {
            drawLine(c, Offset(x, y), Offset(x + dx * arm, y), strokeWidth = sw, cap = StrokeCap.Round)
            drawLine(c, Offset(x, y), Offset(x, y + dy * arm), strokeWidth = sw, cap = StrokeCap.Round)
        }
        val bw = 150.dp.toPx()
        val bh = 64.dp.toPx()
        val left = (size.width - bw) / 2
        val top = (size.height - bh) / 2
        drawRoundRect(Color(0xFFF3EFE6), Offset(left - 10.dp.toPx(), top - 8.dp.toPx()), Size(bw + 20.dp.toPx(), bh + 16.dp.toPx()), CornerRadius(6.dp.toPx()))
        var x = left
        for ((i, w) in BARS.withIndex()) {
            val px = w * 1.6f.dp.toPx()
            if (i % 2 == 0) drawRect(Color(0xFF1B1820), Offset(x, top), Size(px, bh))
            x += px
        }
        val y = inset + (size.height - inset * 2) * sweep
        drawLine(Sun.ToggleLight, Offset(inset + 8.dp.toPx(), y), Offset(size.width - inset - 8.dp.toPx(), y), strokeWidth = 2.dp.toPx())
    }
}

private val BARS = listOf(3, 1, 2, 1, 4, 1, 1, 3, 2, 1, 1, 2, 3, 1, 2, 1, 1, 4, 1, 2, 2, 1, 3, 1, 2, 2, 1, 3, 2, 1, 3)

/** History, shown with an example week: the calendar's dots and the time you were really up. */
@Composable
private fun ColumnScope.YourMonth(next: () -> Unit) {
    Step(
        label = "Your mornings",
        title = "Every morning lands here.",
        body = "History keeps a calendar of your month and the time you were actually up each day. " +
            "It's under the clock icon on the home screen.",
        buttons = { AmberButton("Next", next, Modifier.fillMaxWidth(), height = 56.dp) },
    ) {
        PuzzleGlass(contentAlignment = Alignment.TopStart) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                SectionLabel("Example week", color = Sun.OnGlass.copy(alpha = 0.55f))
                Row(Modifier.fillMaxWidth()) {
                    EXAMPLE_WEEK.forEachIndexed { i, mark ->
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(DAYS[i], style = AppText.chip.copy(fontSize = 11.sp), color = Sun.OnGlass.copy(alpha = 0.5f))
                            DayDot(i + 5, mark)
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Legend(1, "Up")
                    Legend(2, "Rang again")
                    Legend(3, "Gave up")
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(Sun.OnGlass.copy(alpha = 0.12f)))
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    SectionLabel("Thu 8 Oct · 6:30 AM", color = Sun.OnGlass.copy(alpha = 0.55f))
                    Text("Up at 6:34 AM", style = AppText.bodyStrong, color = Sun.Glow)
                    Text("1 wake check passed", style = AppText.caption, color = Sun.OnGlass.copy(alpha = 0.7f))
                }
            }
        }
    }
}

private val DAYS = listOf("M", "T", "W", "T", "F", "S", "S")

/** 1 up, 2 rang again, 3 gave up, 0 no alarm. */
private val EXAMPLE_WEEK = listOf(1, 1, 2, 1, 1, 0, 3)

@Composable
private fun DayDot(day: Int, mark: Int) {
    Box(
        Modifier
            .size(32.dp)
            .clip(CircleShape)
            .drawBehind {
                when (mark) {
                    1 -> drawCircle(Sun.ToggleLight)
                    2 -> drawArc(Sun.ToggleLight, 90f, 180f, useCenter = true)
                }
            }
            .border(1.dp, if (mark == 2 || mark == 3) Sun.OnGlass.copy(alpha = 0.45f) else Color.Transparent, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "$day",
            style = AppText.chip.copy(fontSize = 11.sp),
            color = if (mark == 1) Sun.OnAmber else Sun.OnGlass.copy(alpha = if (mark == 0) 0.5f else 1f),
        )
    }
}

@Composable
private fun Legend(mark: Int, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            Modifier
                .size(11.dp)
                .clip(CircleShape)
                .drawBehind {
                    when (mark) {
                        1 -> drawCircle(Sun.ToggleLight)
                        2 -> drawArc(Sun.ToggleLight, 90f, 180f, useCenter = true)
                    }
                }
                .border(1.dp, if (mark == 1) Color.Transparent else Sun.OnGlass.copy(alpha = 0.45f), CircleShape),
        )
        Text(text, style = AppText.caption.copy(fontSize = 12.sp), color = Sun.OnGlass.copy(alpha = 0.75f))
    }
}

/** The one step that matters: what the phone must allow. Never blocks, since some phones refuse. */
@Composable
private fun ColumnScope.Permissions(replay: Boolean, onDone: () -> Unit) {
    val items = rememberSetupItems()
    val all = items.all { it.allowed }
    val sky = LocalSky.current
    Step(
        label = if (replay) "Before alarms can ring" else "Last step",
        title = "What alarms need to ring.",
        body = null,
        buttons = {
            if (!all) {
                Text(
                    "Alarms may not ring until these are allowed. They'll stay on your home screen until they are.",
                    style = AppText.caption,
                    color = sky.dim,
                )
            }
            AmberButton(if (replay) "Done" else "Set my first alarm", onDone, Modifier.fillMaxWidth(), height = 56.dp)
        },
    ) {
        PuzzleGlass(contentAlignment = Alignment.TopStart) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) { items.forEach { SetupRow(it) } }
        }
    }
}

/** The dark glass the ringing puzzles sit on, so this looks like the real thing. */
@Composable
private fun PuzzleGlass(
    modifier: Modifier = Modifier,
    contentAlignment: Alignment = Alignment.Center,
    content: @Composable () -> Unit,
) {
    Box(
        modifier
            .fillMaxWidth()
            .clip(AppShapes.puzzle)
            .background(Sun.PuzzleGlass)
            .padding(horizontal = 18.dp, vertical = 22.dp),
        contentAlignment = contentAlignment,
    ) { content() }
}
