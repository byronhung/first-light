package com.byronhung.firstlight.ui.list

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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

/** Where we remember the phone maker's own screen, which Android can't check for us. */
private const val PREFS = "setup"
private const val LAUNCH_VISITED = "launch_visited"
private const val LAUNCH_DONE = "launch_done"

/** What the phone must allow before an alarm can reliably ring. Re-read every time the app resumes. */
data class SetupChecks(
    val notifications: Boolean,
    val fullScreen: Boolean,
    val exactAlarms: Boolean,
    val battery: Boolean,
    /** The phone maker's own start-up screen has been opened at least once. */
    val launchVisited: Boolean,
    /** The user said they finished on that screen (tapped Done). */
    val launchDone: Boolean,
) {

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
                launchVisited = prefs(context).getBoolean(LAUNCH_VISITED, false),
                launchDone = prefs(context).getBoolean(LAUNCH_DONE, false),
            )
        }
    }
}

private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

/** Opens [intent]; false if this phone has no such screen or won't let us open it. */
private fun Context.tryStart(intent: Intent): Boolean = try {
    startActivity(intent)
    true
} catch (e: ActivityNotFoundException) {
    false
} catch (e: SecurityException) {
    false
}

/**
 * One thing the phone must allow for alarms to ring, and how to ask for it. [confirm] is for
 * settings Android can't read back: once set, the row offers Done (and Open again) instead of Allow.
 */
class SetupItem(
    val title: String,
    val why: String,
    val allowed: Boolean,
    val confirm: (() -> Unit)? = null,
    val ask: () -> Unit,
)

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
    val maker = remember { PhoneMaker.from(Build.MANUFACTURER) }
    val appInfo = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg)
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
        // Opens a settings screen rather than asking directly: Play restricts the direct-ask permission to a
        // short list of app types, and alarm clocks aren't clearly on it.
        if (maker == null) {
            add(
                // The extras ask Settings to scroll to the Battery row and flash it: Samsung does (checked on
                // One UI), stock Android ignores them, and either way the page opens.
                SetupItem("Remove battery limits", "Tap Battery, then Unrestricted. Stops the phone putting First Light to sleep overnight.", checks.battery) {
                    context.startActivity(
                        Intent(appInfo)
                            .putExtra(EXTRA_FRAGMENT_ARG_KEY, BATTERY_ROW)
                            .putExtra(EXTRA_SHOW_FRAGMENT_ARGS, Bundle().apply { putString(EXTRA_FRAGMENT_ARG_KEY, BATTERY_ROW) }),
                    )
                },
            )
        } else {
            // These brands have no Battery row in App info (Honor, checked by a tester), so go to the
            // standard battery optimization list instead.
            add(
                SetupItem("Remove battery limits", maker.batteryHow, checks.battery) {
                    context.tryStart(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) ||
                        context.tryStart(appInfo)
                },
            )
            // Their own start-up manager kills alarms even with the above, and nothing tells us its state,
            // so the user confirms it themselves.
            val markVisited = {
                prefs(context).edit().putBoolean(LAUNCH_VISITED, true).apply()
                checks = SetupChecks.read(context)
            }
            add(
                SetupItem(
                    title = "Let it start on its own",
                    why = maker.launchHow,
                    allowed = checks.launchDone,
                    ask = {
                        maker.launchScreens.any { (app, screen) -> context.tryStart(Intent().setClassName(app, screen)) } ||
                            context.tryStart(appInfo)
                        markVisited()
                    },
                    confirm = if (!checks.launchVisited) null else {
                        {
                            prefs(context).edit().putBoolean(LAUNCH_DONE, true).apply()
                            checks = SetupChecks.read(context)
                        }
                    },
                ),
            )
        }
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
        when {
            item.allowed -> Text("Allowed", style = AppText.bodyStrong, color = sky.sunInk, modifier = Modifier.padding(horizontal = 12.dp))
            item.confirm != null -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                AmberButton("Done", item.confirm, height = 44.dp)
                Text(
                    "Open again",
                    style = AppText.caption,
                    color = sky.dim,
                    modifier = Modifier.clickable(onClick = item.ask).padding(top = 6.dp),
                )
            }
            else -> AmberButton("Allow", item.ask, height = 44.dp)
        }
    }
}

