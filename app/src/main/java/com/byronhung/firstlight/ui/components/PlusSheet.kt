package com.byronhung.firstlight.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.byronhung.firstlight.FirstLightApp
import com.byronhung.firstlight.ui.theme.AppText
import com.byronhung.firstlight.ui.theme.Sun

/**
 * What tapping something locked opens: what Plus is, Play's real local price, and the buy button
 * (Google Play Billing, see PlusBilling). When Play can't sell here (no Play, signed out) it says so
 * instead of showing a button that does nothing. [reason] null = hidden.
 */
@Composable
fun BoxScope.PlusSheet(reason: String?, onDismiss: () -> Unit) {
    // Keep the last reason while the sheet animates out.
    var shown by remember { mutableStateOf("") }
    if (reason != null) shown = reason
    val context = LocalContext.current
    val billing = (context.applicationContext as FirstLightApp).billing
    val offer by billing.offer.collectAsState()
    // Opening the sheet is a good moment to (re)load the price if it isn't there yet.
    LaunchedEffect(reason != null) { if (reason != null && offer.price == null) billing.refresh() }
    // Bought: the app moves on to the thank-you screen, so this sheet shouldn't wait behind it.
    LaunchedEffect(offer.owned) { if (offer.owned && reason != null) onDismiss() }

    AnimatedVisibility(reason != null, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.matchParentSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color(0xFF06061A).copy(alpha = 0.55f))
                .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
        )
    }
    AnimatedVisibility(
        reason != null,
        enter = slideInVertically { it },
        exit = slideOutVertically { it },
        modifier = Modifier.align(Alignment.BottomCenter),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFF2C205E), Color(0xFF5B2F6B))))
                .clickable(remember { MutableInteractionSource() }, indication = null) { }
                .navigationBarsPadding()
                .padding(start = 22.dp, end = 22.dp, top = 14.dp, bottom = 26.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .size(44.dp, 5.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color.White.copy(alpha = 0.3f)),
            )
            Text("FIRST LIGHT PLUS", style = AppText.label, color = Sun.ToggleLight)
            Text(shown, style = AppText.display.copy(fontSize = 28.sp, lineHeight = 32.sp), color = Sun.OnGlass)
            Text(
                "Plus adds Memory pairs, Connect the path and Slide out, and four new skies you can " +
                    "touch. Waking up, and your month of mornings, always stay free.",
                style = AppText.body.copy(fontSize = 15.sp),
                color = Sun.OnGlass.copy(alpha = 0.82f),
            )
            val price = offer.price
            Text(
                when {
                    offer.pending -> "Your payment is pending. Plus unlocks by itself as soon as it clears."
                    price != null -> "One payment, no subscription. Comes back on any phone signed in to the same Google account."
                    offer.available == false -> "Plus can't be bought on this phone right now. Check you're signed in to Google Play."
                    else -> "Loading the price from Google Play…"
                },
                style = AppText.caption,
                color = Sun.OnGlass.copy(alpha = 0.6f),
            )
            if (price != null && !offer.pending) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    QuietButton("Not now", onDismiss)
                    AmberButton("Get Plus · $price", {
                        context.findActivity()?.let { billing.buy(it) }
                    }, Modifier.weight(1f))
                }
            } else {
                AmberButton("Got it", onDismiss, Modifier.fillMaxWidth())
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
