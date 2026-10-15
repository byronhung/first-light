package com.byronhung.firstlight.ring

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.text.format.DateFormat
import androidx.core.app.NotificationCompat
import com.byronhung.firstlight.R
import com.byronhung.firstlight.alarm.WakeCheck
import com.byronhung.firstlight.alarm.formatTime

object Notifications {
    private const val CHANNEL_RINGING = "ringing"
    /** The same ring once its screen is showing: no banner over the screen it opens. */
    private const val CHANNEL_RINGING_ON_SCREEN = "ringing_on_screen"
    private const val CHANNEL_CHECK = "wake_check"
    const val ID_RINGING = 1
    const val ID_CHECK = 2

    fun ensureChannel(context: Context) {
        val channel = NotificationChannel(CHANNEL_RINGING, "Ringing alarm", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Shown while an alarm is ringing."
            // The ring service plays the sound and vibration itself, on the alarm stream.
            setSound(null, null)
            enableVibration(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        // Wake checks: a soft chime and a buzz, the phone's normal notification sound. Not the alarm.
        val check = NotificationChannel(CHANNEL_CHECK, "Wake checks", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "“Still awake?” after you solve an alarm."
            enableVibration(true)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        val onScreen = NotificationChannel(CHANNEL_RINGING_ON_SCREEN, "Ringing alarm, screen open", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Shown while the ringing screen is already open, so no banner covers it."
            setSound(null, null)
            enableVibration(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        context.getSystemService(NotificationManager::class.java)
            .createNotificationChannels(listOf(channel, onScreen, check))
    }

    fun wakeCheck(context: Context, check: WakeCheck, deadline: Long): Notification {
        val open = PendingIntent.getActivity(
            context,
            1,
            WakeCheckActivity.intent(context, check, deadline)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(context, CHANNEL_CHECK)
            .setSmallIcon(R.drawable.ic_alarm)
            .setContentTitle("Still awake?")
            .setContentText("Tap within a minute, or the alarm rings again")
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setTimeoutAfter(WakeCheck.WINDOW_MS + 5_000)
            .setFullScreenIntent(open, true)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
    }

    fun cancelWakeCheck(context: Context) {
        context.getSystemService(NotificationManager::class.java).cancel(ID_CHECK)
    }

    /**
     * The full-screen intent is what puts the ringing screen over the lock screen. If the phone is
     * unlocked and in use, Android shows it as a banner instead; tapping it opens the same screen.
     * Once that screen is showing ([onScreen]), the banner would only cover its clock, so the same
     * notification moves to a quiet channel with no full-screen intent.
     */
    fun ringing(context: Context, session: RingSession?, onScreen: Boolean = false): Notification {
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, RingActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val title = session?.let {
            val time = formatTime(it.hour, it.minute, DateFormat.is24HourFormat(context))
            if (it.label.isBlank()) time else "$time · ${it.label}"
        } ?: "Alarm"
        return NotificationCompat.Builder(context, if (onScreen) CHANNEL_RINGING_ON_SCREEN else CHANNEL_RINGING)
            .setSmallIcon(R.drawable.ic_alarm)
            .setContentTitle(title)
            .setContentText("Solve the puzzles to stop it")
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            // Off screen it may alert again, so leaving the ring screen brings the banner back.
            .setOnlyAlertOnce(onScreen)
            // Android 12+ holds back a foreground service's notification for up to 10 s unless
            // asked not to. For an alarm that delay is the bug: show it now.
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .apply { if (!onScreen) setFullScreenIntent(open, true) }
            .setContentIntent(open)
            .build()
    }
}
