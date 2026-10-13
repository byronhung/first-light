package com.byronhung.firstlight.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import com.byronhung.firstlight.ui.theme.Phase
import com.byronhung.firstlight.ui.theme.Scene
import com.byronhung.firstlight.ui.theme.Sky
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.sin

/** One touch on open sky, in px and scene seconds. */
class Touch(val x: Float, val y: Float, val t: Float)

/**
 * What a scene knows about the screen it sits behind: how far the content has scrolled, and where
 * the sky was touched. Plain fields, written by [SkyBackground]'s input handlers and read while
 * drawing: the frame clock redraws every frame anyway, so none of this needs to be snapshot state.
 */
class SceneInput {
    /** Total scroll of whatever list sits on top, in px. Layers move by a fraction of it. */
    var scroll = 0f
    var down = false
    var x = 0f
    var y = 0f
    /** Scene seconds at the last frame, so touches are stamped on the same clock as the drawing. */
    var now = 0f
    val taps = ArrayList<Touch>()
    val wipes = ArrayList<Touch>()

    /** Aurora's pull toward the finger, eased in while held and out after release. */
    var pull = 0f
    var lastFrame = 0f

    /** Where a press on a card started, until it moves far enough to count as a drag. */
    private var cardPress: Offset? = null
    private var slop = 0f

    /** A press on open sky: a tap (ripples, lights) and the start of a drag. */
    fun press(at: Offset) {
        down = true
        x = at.x
        y = at.y
        taps += Touch(at.x, at.y, now)
        wipes += Touch(at.x, at.y, now)
    }

    /**
     * A press a card or button took. It never taps the sky (the card is opening or toggling), but
     * once the finger moves it drags like any other: scrolling the list wipes the glass.
     */
    fun pressOnContent(at: Offset, touchSlop: Float) {
        cardPress = at
        slop = touchSlop
    }

    fun release() {
        down = false
        cardPress = null
    }

    val tracking: Boolean get() = down || cardPress != null

    fun move(at: Offset) {
        cardPress?.let { start ->
            if (kotlin.math.hypot(at.x - start.x, at.y - start.y) < slop) return
            cardPress = null
            down = true
        }
        x = at.x
        y = at.y
        val last = wipes.lastOrNull()
        if (last == null || kotlin.math.hypot(last.x - at.x, last.y - at.y) > 8f) wipes += Touch(at.x, at.y, now)
    }
}

/**
 * The Plus skies' moving layer, drawn between the gradient and the screen's content.
 *
 * Every scene is drawn from the round-2 prototype (claude.ai/artifact/3kQdAWeSjG49xjk6rHy92P) in
 * its own units: a 300-wide phone, scaled up to the real screen, so every number in the port
 * matches the prototype's.
 *
 * Everything runs off one clock in seconds, so each effect is a function of time and nothing piles
 * up across recompositions. Sunrise has no layer: its sun glow lives in [SkyBackground].
 */
@Composable
fun SceneLayer(sky: Sky, input: SceneInput, modifier: Modifier = Modifier) {
    if (sky.scene == Scene.SUN) return
    val seconds by produceState(0f) {
        val start = withFrameMillis { it }
        while (true) withFrameMillis { value = (it - start) / 1000f }
    }
    Canvas(modifier) {
        val t = seconds
        input.now = t
        // Touches only matter for a few seconds; don't let them pile up.
        input.taps.removeAll { t - it.t > 10f }
        input.wipes.removeAll { t - it.t > 10f }
        proto(input) { w, h, scroll, k ->
            when (sky.scene) {
                Scene.AURORA -> aurora(w, h, t, sky.phase, input, scroll, k)
                Scene.MONSOON -> monsoon(w, h, t, sky.phase, input, scroll, k)
                Scene.NEON -> neon(w, h, t, sky.phase, input, scroll, k)
                Scene.COAST -> coast(w, h, t, sky.phase, sky.bottom, input, scroll, k)
                Scene.SUN -> Unit
            }
        }
    }
}

/** The prototype's phone was 300 px wide. */
private const val PROTO_W = 300f

/**
 * Parallax stops after this much scroll (prototype units, about the prototype list's full scroll),
 * so a long alarm list can't drag mountains or buildings off their ground.
 */
private const val MAX_PARALLAX_SCROLL = 360f

/**
 * Draws in prototype units: [block] gets the width (300), the height in those units, the scroll
 * in those units, and k (prototype px → screen px) for converting touches.
 */
private inline fun DrawScope.proto(input: SceneInput, block: DrawScope.(w: Float, h: Float, scroll: Float, k: Float) -> Unit) {
    val k = size.width / PROTO_W
    withTransform({ scale(k, k, Offset.Zero) }) {
        block(PROTO_W, size.height / k, (input.scroll / k).coerceAtMost(MAX_PARALLAX_SCROLL), k)
    }
}

// ---------- shared by the ported scenes ----------

/** The prototype's hash: the same pseudo-random numbers, so stars and ridges land where they did there. */
internal fun hash(n: Double): Float {
    val s = sin(n * 12.9898 + 78.233) * 43758.5453
    return (s - floor(s)).toFloat()
}

internal fun hash(n: Int): Float = hash(n.toDouble())

/** Night effects at full strength, half at dawn, faint by day (the prototype's PH table). */
internal val Phase.fx: Float get() = when (this) { Phase.NIGHT -> 1f; Phase.DAWN -> 0.5f; Phase.DAY -> 0.14f }
internal val Phase.starAlpha: Float get() = when (this) { Phase.NIGHT -> 1f; Phase.DAWN -> 0.3f; Phase.DAY -> 0f }

private class Star(val x: Float, val y: Float, val r: Float, val phase: Float, val speed: Float)

private val STARS = List(160) { i ->
    Star(hash(i), hash(i + 300), 0.5f + hash(i + 600) * 1.3f, hash(i + 900) * 2 * PI.toFloat(), 0.6f + hash(i + 1200) * 2.4f)
}

/** Twinkling stars in the top [maxY] of the sky, nudged by [dy] for parallax. */
internal fun DrawScope.stars(w: Float, maxY: Float, t: Float, alpha: Float, dy: Float, count: Int = STARS.size) {
    if (alpha <= 0.01f) return
    for (i in 0 until count) {
        val s = STARS[i]
        val a = alpha * (0.45f + 0.55f * (0.5f + 0.5f * sin(t * s.speed + s.phase))) * (1 - s.y * 0.6f)
        drawCircle(Color(0xFFFFFAF0).copy(alpha = a.coerceIn(0f, 1f)), s.r * 0.75f, Offset(s.x * w, s.y * maxY + dy))
    }
}
