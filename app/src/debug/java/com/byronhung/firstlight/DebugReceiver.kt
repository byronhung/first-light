package com.byronhung.firstlight

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.byronhung.firstlight.alarm.WakeCheck
import com.byronhung.firstlight.alarm.Weekdays
import com.byronhung.firstlight.data.Alarm
import com.byronhung.firstlight.data.FirstLightDatabase
import com.byronhung.firstlight.data.WakeLog
import com.byronhung.firstlight.data.WakeOutcome
import com.byronhung.firstlight.puzzle.parseMix
import com.byronhung.firstlight.ui.theme.SkyTheme
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

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
 *   ... -a com.byronhung.firstlight.debug.DEMO                          (store screenshots: 3 alarms + 4 weeks of mornings;
 *                                                                        force-stop and reopen the app afterwards)
 *   ... -a com.byronhung.firstlight.debug.CODE --es code "First Light"   (register a wake-up code without the camera)
 *   ... -a com.byronhung.firstlight.debug.SCAN_PAUSE --ez on true       (camera stays live but reads nothing; until restart)
 *
 * ADD also takes --es label L, --ei checks N (wake checks, default 0), --ei checkDelaySec S (seconds between
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
                                label = intent.getStringExtra("label") ?: "Test",
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
                    ACTION_SCAN_PAUSE -> {
                        com.byronhung.firstlight.puzzle.ScanPause.paused = intent.getBooleanExtra("on", true)
                        Log.i(TAG, "Scan pause set")
                    }
                    ACTION_CODE -> {
                        app.repository.setWakeCode(intent.getStringExtra("code") ?: "First Light")
                        Log.i(TAG, "Wake code set")
                    }
                    ACTION_DEMO -> {
                        seedDemo(app)
                        Log.i(TAG, "Demo data added")
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

    /**
     * Believable data for the Play Store screenshots. Writes the wake history through a second
     * database handle (the repository keeps its own private), so reopen the app to see it.
     */
    private suspend fun seedDemo(app: FirstLightApp) {
        val work = app.repository.save(Alarm(hour = 6, minute = 30, repeatDays = Weekdays.WEEKDAYS, label = "Work"))
        app.repository.save(Alarm(hour = 8, minute = 15, repeatDays = Weekdays.WEEKEND, label = "Weekend", wakeMethod = 2))
        app.repository.save(Alarm(hour = 5, minute = 45, repeatDays = 0b0001010, label = "Gym", enabled = false, wakeChecks = 1))
        val db = FirstLightDatabase.build(app)
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now()
        // A good run with a couple of realistic slips: one give-up, one morning without the scan.
        for (back in 1..27) {
            val day = today.minusDays(back.toLong())
            val weekend = day.dayOfWeek.value >= 6
            if (back % 9 == 4) continue
            val fired = day.atTime(if (weekend) 8 else 6, if (weekend) 15 else 30).atZone(zone).toInstant().toEpochMilli()
            val outcome = when (back) { 11 -> WakeOutcome.GAVE_UP; 17 -> WakeOutcome.NO_SCAN; else -> WakeOutcome.SOLVED }
            val took = (70 + (back * 37) % 110) * 1000L
            db.wakeDao().insertWake(WakeLog(alarmId = work, firedAt = fired, endedAt = fired + took, outcome = outcome))
        }
        db.close()
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
        const val ACTION_DEMO = "com.byronhung.firstlight.debug.DEMO"
        const val ACTION_CODE = "com.byronhung.firstlight.debug.CODE"
        const val ACTION_SCAN_PAUSE = "com.byronhung.firstlight.debug.SCAN_PAUSE"
    }
}
