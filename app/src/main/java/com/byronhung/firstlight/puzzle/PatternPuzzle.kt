package com.byronhung.firstlight.puzzle

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.byronhung.firstlight.ui.components.springClick
import com.byronhung.firstlight.ui.theme.AppShapes
import com.byronhung.firstlight.ui.theme.AppText
import com.byronhung.firstlight.ui.theme.Sun
import kotlinx.coroutines.delay
import kotlin.random.Random

/**
 * Cells glow amber for two seconds, then go dark: tap them back. A wrong tap clears your picks and
 * shows the same pattern again, so tapping every cell can't brute-force it.
 */
@Composable
fun PatternPuzzle(level: Int, onMiss: () -> Unit, onSolved: () -> Unit) {
    val rng = remember { Random(System.nanoTime()) }
    val spec = remember(level) { patternFor(level, rng) }
    val picked = remember { mutableStateListOf<Int>() }
    var showing by remember { mutableStateOf(true) }
    var shows by remember { mutableIntStateOf(0) }
    var misses by remember { mutableIntStateOf(0) }
    var finished by remember { mutableStateOf(false) }

    // A beat of empty grid first, so a half-asleep eye is on the screen before the cells light (tester feedback).
    var ready by remember { mutableStateOf(true) }

    LaunchedEffect(shows) {
        ready = true
        showing = true
        delay(LEAD_MS)
        ready = false
        delay(SHOW_MS)
        showing = false
    }

    fun showAgain() {
        if (showing || finished) return
        picked.clear()
        shows++
    }

    fun tap(index: Int) {
        if (showing || finished) return
        if (index in spec.lit) {
            if (index !in picked) picked += index
            if (picked.size == spec.lit.size) {
                finished = true
                onSolved()
            }
        } else {
            misses++
            onMiss()
            picked.clear()
            shows++
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        PuzzlePrompt(
            when {
                ready -> "Get ready"
                showing -> "Remember these"
                else -> "Tap the ones that lit up"
            },
        )
        SquareGrid(
            size = spec.gridSize,
            gap = 10,
            modifier = Modifier
                .padding(horizontal = 12.dp)
                .shakeOn(misses),
        ) { i ->
            val on = (showing && !ready && i in spec.lit) || i in picked
            val glow by animateFloatAsState(if (on) 1f else 0f, tween(250), label = "cell")
            Box(
                Modifier
                    .fillMaxSize()
                    .springClick(0.9f, enabled = !showing) { tap(i) }
                    .shadow((14 * glow).dp, AppShapes.small, ambientColor = Sun.AmberLight, spotColor = Sun.AmberLight)
                    .clip(AppShapes.small)
                    .background(Color.White.copy(alpha = 0.08f))
                    .drawBehind { drawRect(Sun.amberBrush, alpha = glow) }
                    .semantics { contentDescription = "Cell ${i + 1}" },
            )
        }
        Text(
            if (showing) "Watch…" else "${picked.size} of ${spec.lit.size}",
            style = AppText.caption.copy(fontSize = AppText.label.fontSize * 1.1f),
            color = Sun.OnGlass.copy(alpha = 0.6f),
        )
        // Missed it? Show it again (clears your picks; not a miss).
        Text(
            "Show again",
            style = AppText.label,
            color = Sun.OnGlass.copy(alpha = if (showing || finished) 0f else 0.85f),
            modifier = Modifier
                .clip(AppShapes.small)
                .springClick(0.95f, enabled = !showing && !finished) { showAgain() }
                .padding(horizontal = 16.dp, vertical = 10.dp),
        )
        MissNote(misses, text = "Not that one. Watch again.")
    }
}

private const val LEAD_MS = 1_000L
private const val SHOW_MS = 2_000L
