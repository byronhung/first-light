package com.byronhung.firstlight.puzzle

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.byronhung.firstlight.ui.components.springClick
import com.byronhung.firstlight.ui.theme.AppText
import com.byronhung.firstlight.ui.theme.Sun
import kotlin.random.Random

private val TileShape = RoundedCornerShape(14.dp)

/**
 * Connect the path: tap a tile to turn it a quarter. Tiles connected to the left light glow, so
 * you can see how far your path reaches. Join the left light to the right one.
 */
@Composable
fun PathPuzzle(level: Int, onSolved: () -> Unit) {
    val rng = remember { Random(System.nanoTime()) }
    var board by remember(level) { mutableStateOf(pathFor(level, rng)) }
    var finished by remember { mutableStateOf(false) }
    val lit = board.lit()
    val n = board.size

    fun turn(i: Int) {
        if (finished) return
        board = board.turn(i)
        if (board.isSolved) {
            finished = true
            onSolved()
        }
    }

    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        PuzzlePrompt("Tap tiles to turn them. Join the two lights.")
        // Tiles and the side lights share one measured size, so each light sits exactly on its row.
        BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            val gap = 8.dp
            val light = 12.dp
            // Capped so a 5×5 board still leaves room for the caption below it on a normal phone.
            val tile = minOf((maxWidth - light * 2 - 12.dp - gap * (n - 1)) / n, MAX_TILE)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Lights(n, board.startRow, tile, gap, light)
                Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                    for (y in 0 until n) {
                        Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                            for (x in 0 until n) {
                                val i = y * n + x
                                Tile(board.solved[i], board.turns[i], i in lit, Modifier.size(tile)) { turn(i) }
                            }
                        }
                    }
                }
                Lights(n, board.endRow, tile, gap, light)
            }
        }
        Text(
            if (finished) "Connected" else "Lit tiles reach the start",
            style = AppText.caption,
            color = Sun.OnGlass.copy(alpha = 0.6f),
        )
    }
}

private val MAX_TILE = 50.dp

/** The glowing marker on the row where the path enters or leaves. */
@Composable
private fun Lights(n: Int, row: Int, tile: Dp, gap: Dp, light: Dp) {
    Column(Modifier.width(light), verticalArrangement = Arrangement.spacedBy(gap)) {
        for (y in 0 until n) {
            Box(Modifier.height(tile).width(light), contentAlignment = Alignment.Center) {
                if (y == row) {
                    Box(
                        Modifier
                            .size(light)
                            .shadow(10.dp, CircleShape, ambientColor = Sun.AmberLight, spotColor = Sun.AmberLight)
                            .background(Sun.Glow, CircleShape),
                    )
                }
            }
        }
    }
}

/**
 * The pipe is drawn in its solving orientation and the whole tile turned by [turns] quarters, so
 * each tap animates as a turn with a little overshoot.
 */
@Composable
private fun Tile(solved: Int, turns: Int, lit: Boolean, modifier: Modifier, onTap: () -> Unit) {
    val angle by animateFloatAsState(turns * 90f, spring(dampingRatio = 0.55f, stiffness = 500f), label = "turn")
    val ink by animateColorAsState(if (lit) Sun.ToggleLight else Sun.OnGlass.copy(alpha = 0.45f), label = "ink")
    val bg by animateColorAsState(if (lit) Color(0x29FFBE78) else Color.White.copy(alpha = 0.07f), label = "bg")
    Box(
        modifier
            .springClick(0.9f, onClick = onTap)
            .clip(TileShape)
            .background(bg)
            .semantics { contentDescription = if (lit) "Tile, connected" else "Tile" },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(
            Modifier
                .fillMaxSize()
                .graphicsLayer { rotationZ = angle },
        ) {
            val c = Offset(size.width / 2, size.height / 2)
            val stroke = size.width * 0.18f
            drawCircle(ink, radius = stroke * 0.55f, center = c)
            if (solved and Pipe.N != 0) drawLine(ink, c, Offset(c.x, 0f), stroke, StrokeCap.Butt)
            if (solved and Pipe.E != 0) drawLine(ink, c, Offset(size.width, c.y), stroke, StrokeCap.Butt)
            if (solved and Pipe.S != 0) drawLine(ink, c, Offset(c.x, size.height), stroke, StrokeCap.Butt)
            if (solved and Pipe.W != 0) drawLine(ink, c, Offset(0f, c.y), stroke, StrokeCap.Butt)
        }
    }
}
