package com.byronhung.firstlight.ui.list

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.NotificationManagerCompat

/**
 * Runs an action that turns an alarm on, but only once notifications are allowed. Without them
 * Android never shows the ringing screen, and a tester's alarm rang with no way to stop it but
 * Force stop. Android won't let us switch them on, so: ask, and if the answer is no (or was
 * "don't ask again"), open the app's notification settings and say why. The action doesn't run;
 * the user tries again once they're on.
 */
@Composable
fun rememberNotificationGate(retryHint: String): (then: () -> Unit) -> Unit {
    val context = LocalContext.current
    val pending = remember { arrayOfNulls<() -> Unit>(1) }
    fun explainAndOpenSettings() {
        Toast.makeText(context, "Turn on notifications so the alarm can show its screen, then $retryHint.", Toast.LENGTH_LONG).show()
        context.startActivity(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
        )
    }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val then = pending[0]
        pending[0] = null
        if (granted && NotificationManagerCompat.from(context).areNotificationsEnabled()) then?.invoke()
        else explainAndOpenSettings()
    }
    return { then ->
        when {
            NotificationManagerCompat.from(context).areNotificationsEnabled() -> then()
            Build.VERSION.SDK_INT >= 33 -> {
                pending[0] = then
                ask.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            else -> explainAndOpenSettings()
        }
    }
}
