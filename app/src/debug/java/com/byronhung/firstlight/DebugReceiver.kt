package com.byronhung.firstlight

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.byronhung.firstlight.alarm.WakeCheck
import com.byronhung.firstlight.data.Alarm
import com.byronhung.firstlight.data.WakeOutcome
import com.byronhung.firstlight.puzzle.parseMix
import com.byronhung.firstlight.ui.theme.SkyTheme
import kotlinx.coroutines.launch
import java.time.LocalDateTime

/**
 * Debug builds only (src/debug). Lets a test drive the real alarm path from adb:
 *
 *   adb shell am broadcast -n com.byronhung.firstlight/.DebugReceiver -a com.byronhung.firstlight.debug.ADD --ei minutes 1
 *   adb shell am broadcast -n com.byronhung.firstlight/.DebugReceiver -a com.byronhung.firstlight.debug.STOP
 *   adb shell am broadcast -n com.byronhung.firstlight/.DebugReceiver -a com.byronhung.firstlight.debug.SOLVE   (as if all rounds were solved: starts wake checks)
 *
 *   ... -a com.byronhung.firstlight.debug.PLUS --ez on true          (Plus on/off, like the Settings switch)
 *   ... -a com.byronhung.firstlight.debug.MIX --es mix PAIRS,PATH,SLIDE
 *   ... -a com.byronhung.firstlight.debug.WELCOME_DONE                  (skip the first-run welcome)
 *   ... -a com.byronhung.firstlight.debug.THEME --ei code 3            (sky theme by code; Plus ones need PLUS)
 *
 * ADD also takes --ei checks N (wake checks, default 0), --ei checkDelaySec S (seconds between
 * checks, instead of 5 minutes; lasts until the app process dies) and --ei wakeMethod M (0 puzzles, 1 scan, 2 both).
 *
 * ADD saves a one-off alarm through the repository, so it's scheduled exactly like a real one.
 */
class DebugReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as FirstLightApp
        val pending = goAsync()
        app.appScope.launch {
            try {
                when (intent.action) {
                    ACTION_ADD -> {
                        val at = LocalDateTime.now().plusMinutes(intent.getIntExtra("minutes", 1).toLong())
                        if (intent.hasExtra("checkDelaySec")) {
                            WakeCheck.delayOverrideMs = intent.getIntExtra("checkDelaySec", 300) * 1000L
                        }
                        val id = app.repository.save(
                            Alarm(
                                hour = at.hour,
                                minute = at.minute,
                                label = "Test",
                                wakeChecks = intent.getIntExtra("checks", 0),
                                wakeMethod = intent.getIntExtra("wakeMethod", 0),
                            ),
                        )
                        Log.i(TAG, "Added test alarm $id at ${at.hour}:${at.minute}")
                    }
                    ACTION_PLUS -> {
                        app.repository.setPlus(intent.getBooleanExtra("on", true))
                        Log.i(TAG, "Plus set")
                    }
                    ACTION_MIX -> {
                        val mix = parseMix(intent.getStringExtra("mix"))
                        app.repository.setPuzzleMix(mix)
                        Log.i(TAG, "Mix set to $mix")
                    }
                    ACTION_WELCOME_DONE -> {
                        app.repository.setWelcomeDone()
                        Log.i(TAG, "Welcome marked done")
                    }
                    ACTION_THEME -> {
                        app.repository.setTheme(SkyTheme.of(intent.getIntExtra("code", 0)))
                        Log.i(TAG, "Theme set")
                    }
                    ACTION_SOLVE -> {
                        app.ringController.finish(WakeOutcome.SOLVED, emptyList())
                        Log.i(TAG, "Solved ring")
                    }
                    ACTION_STOP -> {
                        app.ringController.finish(WakeOutcome.GAVE_UP, emptyList())
                        Log.i(TAG, "Stopped ring")
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        const val TAG = "FirstLightDebug"
        const val ACTION_ADD = "com.byronhung.firstlight.debug.ADD"
        const val ACTION_STOP = "com.byronhung.firstlight.debug.STOP"
        const val ACTION_SOLVE = "com.byronhung.firstlight.debug.SOLVE"
        const val ACTION_WELCOME_DONE = "com.byronhung.firstlight.debug.WELCOME_DONE"
        const val ACTION_THEME = "com.byronhung.firstlight.debug.THEME"
        const val ACTION_PLUS = "com.byronhung.firstlight.debug.PLUS"
        const val ACTION_MIX = "com.byronhung.firstlight.debug.MIX"
    }
}
