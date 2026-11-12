package com.byronhung.firstlight.ui.plus

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.byronhung.firstlight.ui.components.AmberButton
import com.byronhung.firstlight.ui.components.LogoMark
import com.byronhung.firstlight.ui.components.QuietButton
import com.byronhung.firstlight.ui.components.SkyBackground
import com.byronhung.firstlight.ui.theme.AppText
import com.byronhung.firstlight.ui.theme.SkyTheme
import com.byronhung.firstlight.ui.theme.Sun

/**
 * The Plus popup, from the approved mockup. Shown by the alarm list only when [PlusNudge] says so.
 * The note thanks people without a name on it (Byron's call, 9 Oct). Tapping outside counts as
 * "Not now".
 */
@Composable
fun BoxScope.PlusNudgeCard(visible: Boolean, onSeePlus: () -> Unit, onNotNow: () -> Unit) {
    BackHandler(enabled = visible, onBack = onNotNow)
    AnimatedVisibility(visible, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.matchParentSize()) {
        androidx.compose.foundation.layout.Box(
            Modifier
                .fillMaxSize()
                .background(Color(0xFF06061A).copy(alpha = 0.45f))
                .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onNotNow),
        )
    }
    AnimatedVisibility(
        visible,
        enter = slideInVertically(tween(450)) { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut(),
        modifier = Modifier.align(Alignment.BottomCenter),
    ) {
        Column(
            Modifier
                .navigationBarsPadding()
                .padding(14.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(30.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFF2A1F55), Color(0xFF1C1640))))
                .border(1.dp, Sun.ToggleLight.copy(alpha = 0.35f), RoundedCornerShape(30.dp))
                .clickable(remember { MutableInteractionSource() }, indication = null) {}
                .padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            LogoMark(Sun.Glow, size = 44.dp)
            Text("Three good mornings in a row.", style = AppText.title.copy(fontSize = 25.sp, lineHeight = 29.sp), color = Sun.OnGlass)
            Text(
                "Plus adds three more puzzles and four skies you can touch. One payment, no subscription.",
                style = AppText.body,
                color = Sun.OnGlass.copy(alpha = 0.78f),
            )
            Text(
                "First Light is made by one person. Thank you for using it, it means a lot. " +
                    "If it's helping you get up, Plus is how you can help it keep going.",
                style = AppText.caption.copy(fontSize = 13.5.sp, lineHeight = 19.sp),
                color = Sun.OnGlass.copy(alpha = 0.72f),
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White.copy(alpha = 0.06f))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QuietButton("Not now", onNotNow)
                AmberButton("See Plus", onSeePlus, Modifier.weight(1f), height = 52.dp)
            }
        }
    }
}

/**
 * Right after someone gets Plus: thanks, what just unlocked, and a way to the skies. Unsigned,
 * like the popup. Reached on a new Google Play purchase (PlusBilling.newPurchase), never on a
 * restore; debug builds also reach it from the Settings switch.
 */
@Composable
fun ThankYouScreen(onPickSky: () -> Unit) {
    SkyBackground(SkyTheme.SUNRISE.dawn, drift = true, scene = false) {
        Column(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(start = 28.dp, end = 28.dp, top = 24.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterVertically),
        ) {
            LogoMark(Sun.Glow, size = 96.dp)
            Text("Thank you.", style = AppText.display.copy(fontSize = 52.sp), color = Sun.OnGlass)
            Text(
                "You just made First Light possible for a little longer. Your support pays for new puzzles " +
                    "and new skies, and means the alarm can stay free for anyone who needs it.",
                style = AppText.body.copy(fontSize = 17.sp, lineHeight = 25.sp),
                color = Sun.OnGlass.copy(alpha = 0.9f),
            )
            Text(
                "Plus is on: Pairs, Path and Slide out can join your mix, and all five skies are yours.",
                style = AppText.body,
                color = Sun.Glow,
            )
            AmberButton("Pick a sky", onPickSky, Modifier.fillMaxWidth().padding(top = 12.dp), height = 56.dp)
        }
    }
}
