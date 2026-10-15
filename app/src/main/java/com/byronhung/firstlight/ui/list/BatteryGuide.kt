package com.byronhung.firstlight.ui.list

import android.os.Build
import android.view.Gravity
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.platform.LocalView
import com.byronhung.firstlight.ui.components.AmberButton
import com.byronhung.firstlight.ui.components.SectionLabel
import com.byronhung.firstlight.ui.theme.AppText
import com.byronhung.firstlight.ui.theme.Sun

/**
 * Battery limits can't be lifted with one tap (Play restricts the direct ask), and Android won't
 * point at a row in another app's settings. So before opening App info, this shows the two taps on
 * a small copy of that screen: a pulsing ring on the battery row, then on Unrestricted, looping.
 * The row is named the way this phone names it.
 */
@Composable
fun BatteryGuide(onOpenSettings: () -> Unit, onDismiss: () -> Unit) {
    val samsung = Build.MANUFACTURER.equals("samsung", ignoreCase = true)
    val batteryRow = if (samsung) "Battery" else "App battery usage"
    // One loop: 0–0.5 the battery row, 0.5–1 Unrestricted.
    val loop by rememberInfiniteTransition(label = "guide").animateFloat(
        0f, 1f, infiniteRepeatable(tween(4_400, easing = LinearEasing)), label = "step",
    )
    val second = loop >= 0.5f
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        0f, 1f, infiniteRepeatable(tween(1_100, easing = FastOutSlowInEasing), RepeatMode.Restart), label = "ring",
    )

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        // A sheet, not a full-screen layer: the dialog window wraps it and sits on the bottom edge,
        // above the gesture bar. Its own dim is the scrim, and a tap outside closes it.
        (LocalView.current.parent as? DialogWindowProvider)?.window?.setGravity(Gravity.BOTTOM)
        run {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp))
                    .background(Brush.verticalGradient(listOf(Color(0xFF221B45), Color(0xFF171334))))
                    .clickable(remember { MutableInteractionSource() }, indication = null) {}
                    .padding(start = 22.dp, end = 22.dp, top = 12.dp, bottom = 18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    Modifier
                        .align(Alignment.CenterHorizontally)
                        .width(40.dp)
                        .height(5.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color.White.copy(alpha = 0.3f)),
                )
                SectionLabel("Two taps in Settings", color = Sun.ToggleLight)
                Text(
                    "Next, Settings opens on First Light. Tap these two.",
                    style = AppText.body,
                    color = Sun.OnGlass.copy(alpha = 0.8f),
                )
                // The small copy of the screen: the list, then the battery choices.
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .background(Color.Black.copy(alpha = 0.25f))
                        .padding(12.dp),
                ) {
                    if (!second) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            MockRow("Notifications", highlighted = false, pulse = pulse)
                            MockRow("Permissions", highlighted = false, pulse = pulse)
                            MockRow(batteryRow, highlighted = true, pulse = pulse)
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            MockRow("Unrestricted", highlighted = true, pulse = pulse, radio = true)
                            MockRow("Optimized", highlighted = false, pulse = pulse, radio = true)
                            MockRow("Restricted", highlighted = false, pulse = pulse, radio = true)
                        }
                    }
                }
                Step(1, "Tap $batteryRow", active = !second)
                Step(2, "Choose Unrestricted", active = second)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Not now",
                        style = AppText.button.copy(fontWeight = FontWeight.SemiBold),
                        color = Sun.OnGlass.copy(alpha = 0.7f),
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(onClick = onDismiss)
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                    )
                    AmberButton("Open Settings", onOpenSettings, Modifier.weight(1f))
                }
            }
        }
    }
}

/** A row of the pretend settings screen. The highlighted one wears a ring that pulses outward. */
@Composable
private fun MockRow(text: String, highlighted: Boolean, pulse: Float, radio: Boolean = false) {
    Row(
        Modifier
            .fillMaxWidth()
            .drawBehind {
                if (!highlighted) return@drawBehind
                val grow = 6.dp.toPx() * pulse
                drawRoundRect(
                    color = Sun.AmberLight.copy(alpha = 0.9f * (1f - pulse)),
                    topLeft = androidx.compose.ui.geometry.Offset(-grow, -grow),
                    size = androidx.compose.ui.geometry.Size(size.width + grow * 2, size.height + grow * 2),
                    cornerRadius = CornerRadius(14.dp.toPx() + grow),
                    style = Stroke(2.dp.toPx()),
                )
            }
            .clip(RoundedCornerShape(14.dp))
            .background(if (highlighted) Sun.AmberLight.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.06f))
            .border(1.dp, if (highlighted) Sun.AmberLight else Color.White.copy(alpha = 0.1f), RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (radio) {
            Box(
                Modifier
                    .size(16.dp)
                    .border(2.dp, if (highlighted) Sun.AmberLight else Color.White.copy(alpha = 0.4f), CircleShape)
                    .padding(4.dp)
                    .clip(CircleShape)
                    .background(if (highlighted) Sun.AmberLight else Color.Transparent),
            )
        }
        Text(text, style = AppText.bodyStrong.copy(fontSize = 15.sp), color = Sun.OnGlass.copy(alpha = if (highlighted) 1f else 0.55f))
    }
}

@Composable
private fun Step(n: Int, text: String, active: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(if (active) Sun.amberBrush else Brush.linearGradient(listOf(Color.White.copy(alpha = 0.1f), Color.White.copy(alpha = 0.1f)))),
            contentAlignment = Alignment.Center,
        ) { Text("$n", style = AppText.caption.copy(fontWeight = FontWeight.SemiBold), color = if (active) Sun.OnAmber else Sun.OnGlass) }
        Text(text, style = AppText.bodyStrong, color = Sun.OnGlass.copy(alpha = if (active) 1f else 0.5f))
    }
}
