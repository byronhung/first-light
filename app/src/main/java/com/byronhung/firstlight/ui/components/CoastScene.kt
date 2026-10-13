package com.byronhung.firstlight.ui.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import com.byronhung.firstlight.ui.theme.Phase
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin

/**
 * Coast, ported from the round-2 prototype in its units (300 wide). A perspective sea: wave
 * highlights bunch up toward the horizon, and the moon (or the sun) lays a road of tiny glints on
 * the water that sparkle on and off. A lighthouse on a headland sweeps the bay at night. A tap on
 * the water spreads ripples, flattened for distance; a tap on the night sky sends a shooting star.
 *
 * One fix over the prototype: waves drifting left used JavaScript's negative `%` and slowly slid
 * off screen for good. Here they wrap.
 */
internal fun DrawScope.coast(w: Float, h: Float, t: Float, phase: Phase, horizonColor: Color, input: SceneInput, scroll: Float, k: Float) {
    val day = phase == Phase.DAY
    val dawn = phase == Phase.DAWN
    val night = phase == Phase.NIGHT
    val fx = phase.fx
    val horizon = h * 0.6f

    stars(w, horizon * 0.9f, t, phase.starAlpha, -scroll * 0.04f)

    // The orb: moon by night, sun on the horizon at dawn, high sun by day.
    val ox = if (dawn) w * 0.42f else w * 0.7f
    val oy = (if (dawn) horizon - 6 else if (day) h * 0.12f else h * 0.19f) - if (dawn) 0f else scroll * 0.05f
    val r = if (dawn) 26f else 17f
    val orbCentre = Offset(ox, oy)
    val haloEnd = r * if (dawn) 9f else 7f
    val haloColor = when (phase) { Phase.DAWN -> Color(0xFFFFC27A); Phase.DAY -> Color.White; Phase.NIGHT -> Color(0xFFFFF2D6) }
    val haloAlpha = when (phase) { Phase.DAWN -> 0.55f; Phase.DAY -> 0.6f; Phase.NIGHT -> 0.22f }
    val haloFade = if (dawn) Color(0xFFFF8A5C) else Color(0xFFFFF2D6)
    drawCircle(
        Brush.radialGradient(
            0f to haloColor.copy(alpha = haloAlpha),
            (r * 0.8f / haloEnd) to haloColor.copy(alpha = haloAlpha),
            1f to haloFade.copy(alpha = 0f),
            center = orbCentre, radius = haloEnd,
        ),
        haloEnd, orbCentre,
    )
    // The disc is clipped at the horizon, so the dawn sun sits half sunk.
    clipRect(0f, 0f, w, horizon) {
        val disc = when (phase) {
            Phase.NIGHT -> listOf(0f to Color(0xFFFFFBF0), 0.8f to Color(0xFFF3E6CC), 1f to Color(0xFFD9C9AC))
            Phase.DAWN -> listOf(0f to Color(0xFFFFF1C9), 1f to Color(0xFFFFB25E))
            Phase.DAY -> listOf(0f to Color.White, 1f to Color(0xFFFFF4D6))
        }
        drawCircle(Brush.radialGradient(*disc.toTypedArray(), center = Offset(ox - r * 0.3f, oy - r * 0.3f), radius = r * 1.3f), r, orbCentre)
        if (night) {
            for (m in MARIA) {
                val c = Offset(ox + m[0] * r, oy + m[1] * r)
                rotate(Math.toDegrees(0.4).toFloat(), c) {
                    drawOval(Color(160, 140, 115).copy(alpha = 0.16f), Offset(c.x - m[2] * r, c.y - m[3] * r), Size(m[2] * r * 2, m[3] * r * 2))
                }
            }
        }
    }

    // Thin cloud strata, brightest in the middle.
    val tone = when (phase) { Phase.DAY -> Color.White; Phase.DAWN -> Color(0xFFFFB48A); Phase.NIGHT -> Color(0xFF9FB6D6) }
    val toneA = when (phase) { Phase.DAY -> 0.55f; Phase.DAWN -> 0.5f; Phase.NIGHT -> 0.16f }
    for (c in 0 until 5) {
        val cy = horizon * (0.18f + c * 0.13f) - scroll * 0.05f
        val cx = (hash(c + 40) * w * 1.6f + t * (3 + c * 1.5f)) % (w * 1.8f) - w * 0.4f
        val cw = w * (0.5f + hash(c + 41) * 0.5f)
        val ch = 3 + hash(c + 42) * 5
        drawOval(
            Brush.horizontalGradient(0f to tone.copy(alpha = 0f), 0.5f to tone.copy(alpha = toneA), 1f to tone.copy(alpha = 0f), startX = cx, endX = cx + cw),
            Offset(cx, cy - ch), Size(cw, ch * 2),
        )
    }

    // The sea: lighter at the horizon, deep in front.
    val sea = when (phase) {
        Phase.DAY -> listOf(0xFF9FD0E6, 0xFF4F93B8, 0xFF2D6E95)
        Phase.DAWN -> listOf(0xFFE7A27E, 0xFF7A5A86, 0xFF2A2550)
        Phase.NIGHT -> listOf(0xFF183A63, 0xFF0A2040, 0xFF040E1F)
    }.map { Color(it) }
    drawRect(Brush.verticalGradient(0f to sea[0], 0.35f to sea[1], 1f to sea[2], startY = horizon, endY = h), Offset(0f, horizon), Size(w, h - horizon))

    // Wave highlights, bunched toward the horizon for depth.
    val crest = Path()
    for (q in WAVES) {
        val f = q[0].pow(1.7f)
        val y = horizon + 2 + f * (h - horizon)
        val len = 3 + f * 40
        val span = w + 60
        val travel = q[1] * w * 1.3f + t * (4 + f * 14) * if (q[2] > 0.5f) 1f else -0.6f
        val x = ((travel % span) + span) % span - 30
        val a = (0.05f + 0.22f * f) * (0.6f + 0.4f * sin(t * (0.8f + q[2]) + q[1] * 20))
        val color = when (phase) {
            Phase.DAY -> Color.White.copy(alpha = (a * 1.2f).coerceAtMost(1f))
            Phase.DAWN -> Color(255, 214, 170).copy(alpha = a)
            Phase.NIGHT -> Color(170, 200, 240).copy(alpha = a * 0.8f)
        }
        crest.reset()
        crest.moveTo(x, y)
        crest.quadraticTo(x + len / 2, y - 0.8f - f * 1.5f, x + len, y)
        drawPath(crest, color, style = Stroke(0.6f + f * 1.8f, cap = StrokeCap.Round))
    }

    // The road of light under the orb: tiny glints that sparkle on and off.
    val glint = when (phase) { Phase.DAWN -> Color(0xFFFFC27A); Phase.DAY -> Color.White; Phase.NIGHT -> Color(0xFFFFF2D6) }
    val glintGain = when (phase) { Phase.DAY -> 0.7f; Phase.DAWN -> 0.95f; Phase.NIGHT -> 0.85f }
    for (q in GLINTS) {
        val f = q[0].pow(1.4f)
        val y = horizon + 1 + f * (h - horizon)
        val spread = w * (0.015f + 0.2f * f) * if (dawn) 1.3f else 1f
        val x = ox + (q[1] - 0.5f) * 2 * spread * (0.4f + 0.6f * q[2]) + sin(t * 0.9f + q[3] * 30) * 3 * f
        val tw = max(0f, sin(t * (1.6f + q[2] * 3) + q[3] * 40)).pow(3)
        val near = 1 - abs(q[1] - 0.5f) * 2
        val a = tw * (0.35f + 0.65f * near) * glintGain
        if (a < 0.03f) continue
        val len = 2 + f * 18
        drawLine(glint.copy(alpha = a.coerceAtMost(1f)), Offset(x - len / 2, y), Offset(x + len / 2, y), strokeWidth = 0.7f + f * 1.6f, blendMode = BlendMode.Plus)
    }

    // Haze along the horizon line.
    drawRect(
        Brush.verticalGradient(0f to horizonColor.copy(alpha = 0f), 0.45f to horizonColor.copy(alpha = 0.55f), 1f to horizonColor.copy(alpha = 0f), startY = horizon - 10, endY = horizon + 14),
        Offset(0f, horizon - 10), Size(w, 24f),
    )

    // Headland and lighthouse.
    val land = Color(when (phase) { Phase.DAY -> 0xFF3E6A7E; Phase.DAWN -> 0xFF2A1E3E; Phase.NIGHT -> 0xFF030913 })
    val headland = Path().apply {
        moveTo(0f, horizon + 6)
        var x = 0f
        while (x <= w * 0.42f) {
            val n = x / (w * 0.42f)
            lineTo(x, horizon + 6 - (1 - n).pow(1.6f) * h * 0.07f - sin(n * 13) * 2 * (1 - n))
            x += 3
        }
        lineTo(w * 0.42f, horizon + 6)
        close()
    }
    drawPath(headland, land)
    val lx = w * 0.14f
    // Stand the tower on the headland's surface at lx, sunk a little so no sky shows under it.
    val ln = 0.14f / 0.42f
    val ly = horizon + 6 - (1 - ln).pow(1.6f) * h * 0.07f - sin(ln * 13) * 2 * (1 - ln) + 2
    val tower = Path().apply {
        moveTo(lx - 5, ly)
        lineTo(lx + 5, ly)
        lineTo(lx + 3, ly - 30)
        lineTo(lx - 3, ly - 30)
        close()
    }
    drawPath(tower, Color(when (phase) { Phase.DAY -> 0xFFE9EEF1; Phase.DAWN -> 0xFF3A2E52; Phase.NIGHT -> 0xFF0A1424 }))
    if (day) drawRect(Color(0xFFC8483C), Offset(lx - 4.3f, ly - 14), Size(8.6f, 5f))
    drawRect(if (day) Color(0xFF5C6A72) else Color(0xFF0A1424), Offset(lx - 4, ly - 36), Size(8f, 6f))
    if (!day) {
        val th = t * 0.85f
        val face = cos(th)
        val len = w * 1.05f * abs(face) + 20
        val dir = if (face > 0) 1f else -1f
        val beamA = (if (dawn) 0.25f else 0.55f) * abs(face).pow(0.6f)
        val lamp = Offset(lx, ly - 33)
        val beam = Path().apply {
            moveTo(lamp.x, lamp.y)
            lineTo(lamp.x + dir * len, lamp.y - len * 0.07f)
            lineTo(lamp.x + dir * len, lamp.y + len * 0.05f)
            close()
        }
        drawPath(
            beam,
            Brush.linearGradient(listOf(Color(255, 240, 200).copy(alpha = beamA), Color(255, 240, 200).copy(alpha = 0f)), start = lamp, end = Offset(lamp.x + dir * len, lamp.y)),
            blendMode = BlendMode.Plus,
        )
        val glow = 0.5f + 0.5f * (1 - abs(sin(th))).pow(4)
        drawCircle(
            Brush.radialGradient(listOf(Color(255, 240, 200).copy(alpha = glow), Color(255, 240, 200).copy(alpha = 0f)), lamp, 16f),
            16f, lamp, blendMode = BlendMode.Plus,
        )
    }

    // Touch: ripples on the water, a shooting star in the night sky.
    val now = input.now
    for (q in input.taps) {
        val age = now - q.t
        if (age >= 3.2f) continue
        val at = Offset(q.x / k, q.y / k)
        if (at.y > horizon) {
            val depth = (at.y - horizon) / (h - horizon)
            val squash = 0.12f + depth * 0.22f
            for (ring in 0 until 3) {
                val rad = (age - ring * 0.28f) * (40 + depth * 70)
                if (rad <= 0f) continue
                val a = 0.5f * (1 - age / 3.2f) * (1 - ring * 0.25f)
                drawOval(
                    if (day) Color.White.copy(alpha = a) else glint.copy(alpha = a * 0.9f),
                    Offset(at.x - rad, at.y - rad * squash), Size(rad * 2, rad * 2 * squash),
                    style = Stroke(0.8f + depth * 1.4f),
                )
            }
        } else if (!day) {
            val p = age / 1.1f
            if (p > 1f) continue
            val head = Offset(at.x - p * 140, at.y + 0.42f * p * 140)
            val tail = Offset(head.x + 90, head.y - 0.42f * 90)
            drawLine(
                Brush.linearGradient(listOf(Color.White.copy(alpha = 1 - p), Color.White.copy(alpha = 0f)), start = head, end = tail),
                head, tail, strokeWidth = 1.6f,
            )
        }
    }
}

/** The moon's darker patches: x, y, half-width, half-height, as fractions of its radius. */
private val MARIA = listOf(
    floatArrayOf(-0.3f, -0.15f, 0.32f, 0.22f),
    floatArrayOf(0.25f, 0.1f, 0.25f, 0.18f),
    floatArrayOf(-0.05f, 0.35f, 0.2f, 0.13f),
    floatArrayOf(0.3f, -0.35f, 0.14f, 0.1f),
)

private val WAVES = List(420) { i -> floatArrayOf(hash(i + 6000), hash(i + 6100), hash(i + 6200)) }
private val GLINTS = List(520) { i -> floatArrayOf(hash(i + 5000), hash(i + 5100), hash(i + 5200), hash(i + 5300)) }
