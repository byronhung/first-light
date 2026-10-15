package com.byronhung.firstlight.ui.list

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.byronhung.firstlight.ui.components.AmberButton
import com.byronhung.firstlight.ui.components.GlassCard
import com.byronhung.firstlight.ui.components.SectionLabel
import com.byronhung.firstlight.ui.theme.LocalSky
import com.byronhung.firstlight.ui.theme.AppText
import com.byronhung.firstlight.ui.theme.Space

/** Settings' "scroll to and highlight this row" extras. Not public API, but long-standing. */
private const val EXTRA_FRAGMENT_ARG_KEY = ":settings:fragment_args_key"
private const val EXTRA_SHOW_FRAGMENT_ARGS = ":settings:show_fragment_args"
/** The Battery row's key on an app's App info page. */
private const val BATTERY_ROW = "battery"

/** What the phone must allow before an alarm can reliably ring. Re-read every time the app resumes. */
data class SetupChecks(
    val notifications: Boolean,
    val fullScreen: Boolean,
    val exactAlarms: Boolean,
    val battery: Boolean,
) {
    val allGood get() = notifications && fullScreen && exactAlarms && battery

    companion object {
        fun read(context: Context): SetupChecks {
            val nm = context.getSystemService(NotificationManager::class.java)
            val am = context.getSystemService(AlarmManager::class.java)
            val pm = context.getSystemService(PowerManager::class.java)
            return SetupChecks(
                notifications = NotificationManagerCompat.from(context).areNotificationsEnabled(),
                fullScreen = Build.VERSION.SDK_INT < 34 || nm.canUseFullScreenIntent(),
                exactAlarms = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms(),
                battery = pm.isIgnoringBatteryOptimizations(context.packageName),
            )
        }
    }
}

/** One thing the phone must allow for alarms to ring, and how to ask for it. */
class SetupItem(val title: String, val why: String, val allowed: Boolean, val ask: () -> Unit)

/**
 * Everything the phone must allow, re-read whenever the app comes back to the front (the user
 * may have just flipped one in system settings). Items that don't exist on this Android version
 * are left out. Shared by the home-screen card and the welcome's last step.
 */
@Composable
fun rememberSetupItems(): List<SetupItem> {
    val context = LocalContext.current
    var checks by remember { mutableStateOf(SetupChecks.read(context)) }
    LifecycleResumeEffect(Unit) {
        checks = SetupChecks.read(context)
        onPauseOrDispose { }
    }
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        checks = SetupChecks.read(context)
    }
    val pkg = Uri.parse("package:${context.packageName}")
    return buildList {
        add(
            SetupItem("Allow notifications", "The ringing screen opens through one.", checks.notifications) {
                if (Build.VERSION.SDK_INT >= 33) {
                    askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    context.startActivity(
                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                    )
                }
            },
        )
        if (Build.VERSION.SDK_INT >= 34) {
            add(
                SetupItem("Allow full-screen alarms", "Lets the alarm take over the lock screen.", checks.fullScreen) {
                    context.startActivity(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, pkg))
                },
            )
        }
        if (Build.VERSION.SDK_INT >= 31) {
            add(
                SetupItem("Allow alarms", "So it rings on the minute, not roughly.", checks.exactAlarms) {
                    context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, pkg))
                },
            )
        }
        add(
            // Opens App info rather than asking directly: Play restricts the direct-ask permission to a
            // short list of app types, and alarm clocks aren't clearly on it. The extras ask Settings to
            // scroll to the Battery row and flash it: Samsung does (checked on One UI), stock Android
            // ignores them, and either way the page opens.
            SetupItem("Remove battery limits", "Tap Battery, then Unrestricted. Stops the phone putting First Light to sleep overnight.", checks.battery) {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg)
                        .putExtra(EXTRA_FRAGMENT_ARG_KEY, BATTERY_ROW)
                        .putExtra(EXTRA_SHOW_FRAGMENT_ARGS, Bundle().apply { putString(EXTRA_FRAGMENT_ARG_KEY, BATTERY_ROW) }),
                )
            },
        )
    }
}

/**
 * The "phone makers' battery killers" answer from the brief: not clever code, a checklist that
 * walks you to the right settings screen and disappears once everything is allowed.
 */
@Composable
fun SetupCard() {
    val missing = rememberSetupItems().filter { !it.allowed }
    if (missing.isEmpty()) return
    val sky = LocalSky.current
    GlassCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(Space.lg + 2.dp), verticalArrangement = Arrangement.spacedBy(Space.lg)) {
            SectionLabel("Before alarms can ring", color = sky.sunInk)
            missing.forEach { SetupRow(it) }
        }
    }
}

/** One permission: what and why, then Allow, or a quiet "Allowed" once it's done. */
@Composable
fun SetupRow(item: SetupItem) {
    val sky = LocalSky.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.md)) {
        Column(Modifier.weight(1f)) {
            Text(item.title, style = AppText.bodyStrong, color = sky.ink)
            Text(item.why, style = AppText.caption, color = sky.dim)
        }
        if (item.allowed) Text("Allowed", style = AppText.bodyStrong, color = sky.sunInk, modifier = Modifier.padding(horizontal = 12.dp))
        else AmberButton("Allow", item.ask, height = 44.dp)
    }
}

