package com.byronhung.firstlight.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.input.pointer.changedToDownIgnoreConsumed
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.byronhung.firstlight.ui.theme.AppShapes
import com.byronhung.firstlight.ui.theme.AppText
import com.byronhung.firstlight.ui.theme.LocalSky
import com.byronhung.firstlight.ui.theme.Motion
import com.byronhung.firstlight.ui.theme.Scene
import com.byronhung.firstlight.ui.theme.Sky
import com.byronhung.firstlight.ui.theme.Sun
import kotlin.math.abs
import kotlin.math.ceil

// ---------- the sky ----------

/**
 * Every Sunrise screen sits on this: the sky gradient, a sun glowing at the bottom that slowly
 * breathes, and [LocalSky] set so everything inside picks matching ink and glass.
 * [drift]: the ringing screen's sky slowly moves, like the prototype's.
 * [scene]: draw the theme's moving scene (Plus skies). The ringing screen turns it off, so nothing
 * competes with the puzzle.
 * [calm]: draw the scene at half strength, for screens that are mostly reading (History, Settings,
 * the editor). The alarm list keeps it at full.
 *
 * The scene follows the content: scrolling anything inside moves its layers (read through nested
 * scroll, so no screen has to report it), and touches on open sky reach it. A touch that a card,
 * toggle or button has already taken is left alone.
 */
/** The widest the content gets: about a large phone. Beyond this, it centres and the sky shows around it. */
val CONTENT_MAX_WIDTH = 560.dp

@Composable
fun SkyBackground(
    sky: Sky,
    modifier: Modifier = Modifier,
    drift: Boolean = false,
    scene: Boolean = true,
    calm: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    val input = remember { SceneInput() }
    val scrolls = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                input.scroll = (input.scroll - consumed.y).coerceAtLeast(0f)
                return Offset.Zero
            }
        }
    }
    val sceneOn = scene && sky.scene != Scene.SUN
    CompositionLocalProvider(LocalSky provides sky) {
        val t = rememberInfiniteTransition(label = "sky")
        val breathe by t.animateFloat(1f, 1.08f, infiniteRepeatable(tween(4_500, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "sun")
        val shift by t.animateFloat(0f, if (drift) 1f else 0f, infiniteRepeatable(tween(14_000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "drift")
        Box(
            modifier
                .fillMaxSize()
                .drawBehind {
                    val h = size.height * if (drift) 1.4f else 1f
                    val y0 = -(h - size.height) * shift
                    drawRect(Brush.verticalGradient(0f to sky.top, 0.58f to sky.mid, 1f to sky.bottom, startY = y0, endY = y0 + h))
                }
                .then(
                    if (!sceneOn) Modifier
                    else Modifier
                        .nestedScroll(scrolls)
                        .pointerInput(Unit) {
                            // Final pass: children have had their go, so a consumed press was a card's.
                            // Those never tap the sky, but they still drag once they move.
                            awaitPointerEventScope {
                                while (true) {
                                    val change = awaitPointerEvent(PointerEventPass.Final).changes.firstOrNull() ?: continue
                                    when {
                                        change.changedToDownIgnoreConsumed() ->
                                            if (change.isConsumed) input.pressOnContent(change.position, viewConfiguration.touchSlop) else input.press(change.position)
                                        change.pressed && input.tracking -> input.move(change.position)
                                        !change.pressed -> input.release()
                                    }
                                }
                            }
                        },
                ),
        ) {
            if (sky.scene == Scene.SUN) Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .offset(y = 270.dp)
                    .requiredSize(580.dp)
                    .graphicsLayer {
                        scaleX = breathe
                        scaleY = breathe
                        alpha = sky.sunAlpha
                    }
                    .background(
                        Brush.radialGradient(0f to Color(0x8CFFC480), 0.45f to Color(0x2EFF8C6E), 0.68f to Color.Transparent),
                        CircleShape,
                    ),
            )
            if (sceneOn) SceneLayer(sky, input, Modifier.fillMaxSize().graphicsLayer { alpha = if (calm) 0.5f else 1f })
            // On wide screens (an unfolded Fold, a tablet) the content stays a phone-width column in the
            // middle while the sky fills the whole screen. Phones are narrower than this, so unchanged.
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                Box(Modifier.fillMaxHeight().widthIn(max = CONTENT_MAX_WIDTH).fillMaxWidth(), content = content)
            }
        }
    }
}

// ---------- touch ----------

/** Press shrinks with a spring, release bounces back. The prototype's `:active` scale. */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.springClick(
    pressedScale: Float = 0.9f,
    enabled: Boolean = true,
    role: Role = Role.Button,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) pressedScale else 1f, Motion.bouncy(), label = "press")
    graphicsLayer {
        scaleX = scale
        scaleY = scale
    }.combinedClickable(source, indication = null, enabled = enabled, role = role, onLongClick = onLongClick, onClick = onClick)
}

// ---------- surfaces ----------

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = AppShapes.card,
    content: @Composable ColumnScope.() -> Unit,
) {
    val sky = LocalSky.current
    Column(
        modifier
            .clip(shape)
            .background(sky.card)
            .border(1.dp, sky.cardEdge, shape),
        content = content,
    )
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, color: Color = LocalSky.current.mute) {
    Text(text.uppercase(), style = AppText.label, color = color, modifier = modifier)
}

// ---------- controls ----------

/** The amber spring toggle: the knob overshoots, and squishes while pressed. */
@Composable
fun SpringToggle(checked: Boolean, onCheckedChange: (Boolean) -> Unit, label: String, modifier: Modifier = Modifier) {
    val sky = LocalSky.current
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val travel by animateDpAsState(if (checked) 22.dp else 0.dp, Motion.bouncy(), label = "knob")
    val knobWidth by animateDpAsState(if (pressed) 32.dp else 26.dp, Motion.bouncy(), label = "squish")
    val on by animateFloatAsState(if (checked) 1f else 0f, tween(350), label = "track")
    Box(
        modifier
            .size(54.dp, 32.dp)
            .shadow(if (checked) 8.dp else 0.dp, AppShapes.pill, ambientColor = Sun.Amber, spotColor = Sun.Amber)
            .clip(AppShapes.pill)
            .background(sky.track)
            .drawBehind { drawRect(Sun.toggleBrush, alpha = on) }
            .toggleable(checked, source, indication = null, role = Role.Switch, onValueChange = onCheckedChange)
            .semantics { contentDescription = label },
    ) {
        val squishBack = if (checked) knobWidth - 26.dp else 0.dp
        Box(
            Modifier
                .offset(x = 3.dp + travel - squishBack, y = 3.dp)
                .size(knobWidth, 26.dp)
                .shadow(3.dp, AppShapes.pill)
                .background(Color.White, AppShapes.pill),
        )
    }
}

@Composable
fun AmberButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, height: Dp = 52.dp) {
    Box(
        modifier
            .springClick(0.92f, onClick = onClick)
            .height(height)
            .shadow(14.dp, AppShapes.button, ambientColor = Sun.Amber, spotColor = Sun.Amber)
            .clip(AppShapes.button)
            .background(Sun.amberBrush)
            .padding(horizontal = 22.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = AppText.button, color = Sun.OnAmber) }
}

@Composable
fun QuietButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, height: Dp = 52.dp) {
    val sky = LocalSky.current
    Box(
        modifier
            .springClick(0.92f, onClick = onClick)
            .height(height)
            .clip(AppShapes.button)
            .border(1.dp, sky.cardEdge, AppShapes.button)
            .padding(horizontal = 22.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = AppText.button.copy(fontWeight = FontWeight.SemiBold), color = sky.ink) }
}

@Composable
fun IconSquareButton(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val sky = LocalSky.current
    Box(
        modifier
            .springClick(0.9f, onClick = onClick)
            .size(44.dp)
            .clip(AppShapes.small)
            .background(sky.card)
            .border(1.dp, sky.cardEdge, AppShapes.small)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription = null, tint = sky.ink, modifier = Modifier.size(20.dp)) }
}

/** The add button: shrinks and tilts when pressed. */
@Composable
fun SunFab(onClick: () -> Unit, label: String, modifier: Modifier = Modifier) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.88f else 1f, Motion.bouncy(), label = "fab")
    val tilt by animateFloatAsState(if (pressed) -8f else 0f, Motion.bouncy(), label = "tilt")
    Box(
        modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                rotationZ = tilt
            }
            .size(66.dp)
            .shadow(18.dp, AppShapes.fab, ambientColor = Sun.Amber, spotColor = Sun.Amber)
            .clip(AppShapes.fab)
            .background(Brush.linearGradient(0f to Sun.AmberLight, 0.7f to Sun.Amber))
            .clickable(source, indication = null, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { Icon(SunIcons.Plus, contentDescription = null, tint = Sun.OnAmber, modifier = Modifier.size(26.dp)) }
}

@Composable
fun Chip(text: String, icon: ImageVector? = null) {
    val sky = LocalSky.current
    Row(
        Modifier
            .clip(AppShapes.pill)
            .background(sky.chip)
            .padding(horizontal = 9.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = sky.ink, modifier = Modifier.size(11.dp))
        // A tag never breaks inside itself; the row around it wraps instead.
        Text(text, style = AppText.chip, color = sky.ink, maxLines = 1, softWrap = false)
    }
}

/** Round progress: each finished round fills in with a soft sweep. */
@Composable
fun Pips(done: Int, total: Int, ink: Color = Sun.OnGlass, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        repeat(total) { i ->
            val fill by animateFloatAsState(if (i < done) 1f else 0f, tween(600, easing = Motion.softOut), label = "pip")
            val track = ink.copy(alpha = if (i == done) 0.45f else 0.22f)
            Box(
                Modifier
                    .size(44.dp, 6.dp)
                    .clip(AppShapes.pill)
                    .background(track)
                    .drawBehind { drawRect(Sun.Glow, size = size.copy(width = size.width * fill)) },
            )
        }
    }
}

private val PillShape15 = androidx.compose.foundation.shape.RoundedCornerShape(15.dp)

/** Day pills M T W T F S S: amber when on, springy when tapped. */
@Composable
fun DayPill(letter: String, name: String, on: Boolean, onToggle: () -> Unit) {
    val sky = LocalSky.current
    val bg by animateColorAsState(if (on) Color.Transparent else sky.card, tween(250), label = "pill")
    val ink by animateColorAsState(if (on) Sun.OnAmber else sky.ink, tween(250), label = "pillInk")
    Box(
        Modifier
            .springClick(0.86f, role = Role.Checkbox, onClick = onToggle)
            .size(42.dp)
            .clip(PillShape15)
            .background(bg)
            .then(if (on) Modifier.background(Sun.amberBrush) else Modifier.border(1.dp, sky.cardEdge, PillShape15))
            .semantics { contentDescription = if (on) "$name, on" else "$name, off" },
        contentAlignment = Alignment.Center,
    ) { Text(letter, style = AppText.bodyStrong.copy(fontSize = 14.sp), color = ink) }
}

/** Off / 1 / 2 / 3: an amber block slides under the chosen option with a spring. */
@Composable
fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val sky = LocalSky.current
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .clip(AppShapes.button)
            .background(sky.card)
            .border(1.dp, sky.cardEdge, AppShapes.button)
            .padding(4.dp),
    ) {
        val segment = maxWidth / options.size
        val x by animateDpAsState(segment * selected, Motion.bouncy(), label = "segment")
        Box(
            Modifier
                .offset(x = x)
                .width(segment)
                .height(44.dp)
                .shadow(8.dp, AppShapes.small, ambientColor = Sun.Amber, spotColor = Sun.Amber)
                .clip(AppShapes.small)
                .background(Sun.amberBrush),
        )
        Row(Modifier.fillMaxWidth()) {
            options.forEachIndexed { i, label ->
                val ink by animateColorAsState(if (i == selected) Sun.OnAmber else sky.ink, tween(250), label = "segInk")
                Box(
                    Modifier
                        .weight(1f)
                        .height(44.dp)
                        .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.RadioButton) { onSelect(i) },
                    contentAlignment = Alignment.Center,
                ) { Text(label, style = AppText.bodyStrong.copy(fontSize = 15.sp), color = ink) }
            }
        }
    }
}

/**
 * The scroll-wheel time picker column: snaps row by row, the centre row is big and solid, the
 * rows above and below shrink and fade, and the ends fade out.
 */
@Composable
fun WheelColumn(
    items: List<String>,
    initialIndex: Int,
    onSelected: (Int) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
) {
    val sky = LocalSky.current
    val rowHeight = 44.dp
    val rowPx = with(LocalDensity.current) { rowHeight.toPx() }
    val state = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex.coerceIn(items.indices))
    val fling = rememberSnapFlingBehavior(lazyListState = state)
    val selected by remember {
        derivedStateOf {
            val i = state.firstVisibleItemIndex + if (state.firstVisibleItemScrollOffset > rowPx / 2) 1 else 0
            i.coerceIn(items.indices)
        }
    }
    val latest by rememberUpdatedState(onSelected)
    LaunchedEffect(selected) { latest(selected) }

    LazyColumn(
        state = state,
        flingBehavior = fling,
        contentPadding = PaddingValues(vertical = rowHeight * 2),
        modifier = modifier
            .height(rowHeight * 5)
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                drawRect(
                    Brush.verticalGradient(0f to Color.Transparent, 0.3f to Color.Black, 0.7f to Color.Black, 1f to Color.Transparent),
                    blendMode = BlendMode.DstIn,
                )
            }
            .semantics { contentDescription = "$label: ${items.getOrNull(selected)}" },
    ) {
        itemsIndexed(items) { i, value ->
            val d = abs(i - selected)
            val alpha by animateFloatAsState(when (d) { 0 -> 1f; 1 -> 0.5f; else -> 0.22f }, tween(200), label = "wheelAlpha")
            val scale by animateFloatAsState(when (d) { 0 -> 1f; 1 -> 0.78f; else -> 0.66f }, tween(200), label = "wheelScale")
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(rowHeight),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    value,
                    style = AppText.wheel.copy(fontWeight = if (d == 0) FontWeight.Normal else FontWeight.Light),
                    color = sky.ink,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.graphicsLayer {
                        this.alpha = alpha
                        scaleX = scale
                        scaleY = scale
                    },
                )
            }
        }
    }
}

/**
 * Hold to give up: a ring that fills over [holdMs] while pressed and springs back empty on release.
 * Rose is used here and nowhere else, because this is the one place the app costs you something.
 */
@Composable
fun HoldToGiveUp(holdMs: Long, onGiveUp: () -> Unit, modifier: Modifier = Modifier, compact: Boolean = false) {
    var pressed by remember { mutableStateOf(false) }
    val progress = remember { Animatable(0f) }
    val latest by rememberUpdatedState(onGiveUp)
    LaunchedEffect(pressed) {
        if (!pressed) {
            progress.animateTo(0f, tween(500, easing = Motion.softOut))
            return@LaunchedEffect
        }
        val start = withFrameMillis { it } - (progress.value * holdMs).toLong()
        while (true) {
            val p = ((withFrameMillis { it } - start).toFloat() / holdMs).coerceAtMost(1f)
            progress.snapTo(p)
            if (p >= 1f) {
                latest()
                break
            }
        }
    }
    val scale by animateFloatAsState(if (pressed) 1.08f else 1f, Motion.bouncy(), label = "hold")
    val totalSeconds = (holdMs / 1000).toInt()
    val left = ceil(totalSeconds * (1 - progress.value)).toInt()

    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Box(
            Modifier
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .size(if (compact) 52.dp else 84.dp)
                .clip(CircleShape)
                .background(Color(0xFF160C2A).copy(alpha = 0.45f))
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown()
                        pressed = true
                        waitForUpOrCancellation()
                        pressed = false
                    }
                }
                .drawBehind {
                    val stroke = (if (compact) 3.5f else 5f).dp.toPx()
                    val inset = (if (compact) 4 else 6).dp.toPx() + stroke / 2
                    val arcSize = size.copy(width = size.width - inset * 2, height = size.height - inset * 2)
                    val topLeft = Offset(inset, inset)
                    drawArc(Color(0x33FFF6EC), 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
                    drawArc(Sun.Rose, -90f, 360f * progress.value, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
                }
                .semantics { contentDescription = "Hold for $totalSeconds seconds to give up" },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                (if (pressed) left else totalSeconds).toString(),
                style = AppText.header.copy(fontSize = if (compact) 16.sp else 22.sp, fontWeight = FontWeight.Normal),
                color = Sun.OnGlass,
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(if (pressed) "Keep holding…" else "Hold to give up", style = AppText.bodyStrong, color = Sun.OnGlass)
            Text(
                if (pressed) "Let go to keep going" else "$totalSeconds seconds · logged in History",
                style = AppText.caption.copy(fontSize = 13.5.sp),
                color = Sun.OnGlass.copy(alpha = 0.7f),
            )
        }
    }
}

/**
 * The logo, "Horizon": the sun half risen over the horizon with five rays, as on the app icon.
 * Drawn from the icon's 108-unit geometry, cropped to the mark, so it takes any colour.
 */
@Composable
fun LogoMark(color: Color, size: Dp = 26.dp) {
    Canvas(Modifier.size(size)) {
        // The mark spans x 18..90 and y 34..74 of the icon; fit that 72-wide box and centre it.
        val s = this.size.width / 72f
        fun p(x: Float, y: Float) = Offset((x - 18f) * s, (y - 18f) * s)
        val r = 20f * s
        drawArc(color, 180f, 180f, useCenter = true, topLeft = p(34f, 52f), size = androidx.compose.ui.geometry.Size(r * 2, r * 2))
        val rays = listOf(31.5f to 59f to (24.6f to 55f), 41f to 49.5f to (37f to 42.6f), 54f to 46f to (54f to 38f), 67f to 49.5f to (71f to 42.6f), 76.5f to 59f to (83.4f to 55f))
        for ((from, to) in rays) drawLine(color, p(from.first, from.second), p(to.first, to.second), strokeWidth = 4f * s, cap = StrokeCap.Round)
        drawLine(color, p(20f, 72f), p(88f, 72f), strokeWidth = 3.5f * s, cap = StrokeCap.Round)
    }
}

/** Stroke icons from the prototype, drawn from its SVG paths. */
object SunIcons {
    val History = stroke("history", "M3 12a9 9 0 1 0 3-6.7", "M3 4v4h4", "M12 7v5l3 2")
    val Sliders = stroke(
        "sliders",
        "M4 7h10M18 7h2M4 17h4M12 17h8",
        "M18 7a2 2 0 1 1 -4 0a2 2 0 1 1 4 0",
        "M12 17a2 2 0 1 1 -4 0a2 2 0 1 1 4 0",
    )
    val Back = stroke("back", "M15 5l-7 7 7 7", width = 2f)
    val Plus = stroke("plus", "M12 5v14M5 12h14", width = 2.4f)
    val Check = stroke("check", "M5 12.5l4.5 4.5L19 7.5", width = 2.4f)
    val Lock = stroke("lock", "M5 11h14v10H5z", "M8 11V8a4 4 0 0 1 8 0v3", width = 2.4f)
    val Scan = stroke(
        "scan",
        "M4 7V5a1 1 0 0 1 1-1h2M17 4h2a1 1 0 0 1 1 1v2M20 17v2a1 1 0 0 1-1 1h-2M7 20H5a1 1 0 0 1-1-1v-2M8 9v6M11 9v6M14 9v6M17 9v6",
        width = 2f,
    )

    private fun stroke(name: String, vararg paths: String, width: Float = 1.8f): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            paths.forEach { d ->
                addPath(
                    pathData = PathParser().parsePathString(d).toNodes(),
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = width,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
        }.build()
}
