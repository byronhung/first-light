package com.byronhung.firstlight.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.UserManager
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Keeps alarms and settings safe across a new phone or a reinstall.
 *
 * The database lives in device-protected storage so alarms can ring before the first unlock, but
 * Android's Auto Backup skips that storage. So every change is mirrored here as a small JSON file
 * in normal storage, which Auto Backup does copy. On start, an empty database with a backup next
 * to it means a fresh install on a restored phone: the alarms and settings come back.
 *
 * Wake history isn't backed up; it's a log, not something you set up. Plus isn't either: once
 * billing exists, Google Play restores the purchase itself.
 */
class Backup(private val context: Context, private val repository: AlarmRepository, private val scope: CoroutineScope) {
    private val file get() = File(context.filesDir, "backup.json")

    /** Restore if needed, then keep the file up to date. Waits for the first unlock if the phone is still locked. */
    fun start() {
        if (context.getSystemService(UserManager::class.java).isUserUnlocked) {
            scope.launch { restoreThenMirror() }
            return
        }
        // Normal storage can't be read before the first unlock, and an alarm can start the app then.
        ContextCompat.registerReceiver(context, object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                context.unregisterReceiver(this)
                scope.launch { restoreThenMirror() }
            }
        }, IntentFilter(Intent.ACTION_USER_UNLOCKED), ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    private suspend fun restoreThenMirror() {
        // Restore first, so the mirror below never overwrites a good backup with an empty table.
        runCatching { restoreIfEmpty() }.onFailure { Log.w(TAG, "Backup restore failed", it) }
        combine(repository.alarms, repository.settings) { alarms, settings -> encode(alarms, settings ?: AppSettings()) }
            .collect { json -> write(json) }
    }

    private suspend fun restoreIfEmpty() {
        if (repository.hasAlarms()) return
        val text = withContext(Dispatchers.IO) { file.takeIf { it.exists() }?.readText() } ?: return
        val (alarms, settings) = decode(text)
        if (alarms.isEmpty()) return
        repository.restoreBackup(alarms, settings)
        Log.i(TAG, "Restored ${alarms.size} alarms from backup")
    }

    private suspend fun write(json: String) = withContext(Dispatchers.IO) {
        runCatching {
            // Write then rename, so a crash mid-write never leaves half a file.
            val tmp = File(context.filesDir, "backup.json.tmp")
            tmp.writeText(json)
            if (!tmp.renameTo(file)) file.writeText(json)
        }.onFailure { Log.w(TAG, "Backup write failed", it) }
    }

    companion object {
        private const val TAG = "Backup"
        private const val VERSION = 1

        fun encode(alarms: List<Alarm>, s: AppSettings): String = JSONObject()
            .put("version", VERSION)
            .put("alarms", JSONArray(alarms.map { a ->
                JSONObject()
                    .put("id", a.id).put("hour", a.hour).put("minute", a.minute)
                    .put("repeatDays", a.repeatDays).put("label", a.label).put("enabled", a.enabled)
                    .put("roundTypes", a.roundTypes.joinToString(",") { it.name })
                    .put("wakeChecks", a.wakeChecks).put("difficulty", a.difficulty)
                    .put("wakeMethod", a.wakeMethod).put("soundUri", a.soundUri ?: JSONObject.NULL)
                    .put("gentleStart", a.gentleStart).put("skipAt", a.skipAt)
            }))
            .put("settings", JSONObject()
                .put("wakeCode", s.wakeCode ?: JSONObject.NULL)
                .put("puzzleMix", s.puzzleMix ?: JSONObject.NULL)
                .put("defaultDifficulty", s.defaultDifficulty)
                .put("theme", s.theme)
                .put("welcomeDone", s.welcomeDone)
                .put("plusNudgeAt", s.plusNudgeAt)
                .put("plusNudgeDismissals", s.plusNudgeDismissals)
                .put("appearance", s.appearance))
            .toString()

        /** Anything missing falls back to the defaults, so older backups still load. */
        fun decode(text: String): Pair<List<Alarm>, AppSettings> {
            val root = JSONObject(text)
            val list = root.optJSONArray("alarms") ?: JSONArray()
            val alarms = (0 until list.length()).map { i ->
                val o = list.getJSONObject(i)
                val types = o.optString("roundTypes").split(",")
                    .mapNotNull { name -> RoundType.entries.firstOrNull { it.name == name } }
                Alarm(
                    id = o.getLong("id"),
                    hour = o.getInt("hour"),
                    minute = o.getInt("minute"),
                    repeatDays = o.optInt("repeatDays", 0),
                    label = o.optString("label", ""),
                    enabled = o.optBoolean("enabled", true),
                    roundTypes = types.ifEmpty { RoundType.MORNING_DEFAULT },
                    wakeChecks = o.optInt("wakeChecks", 1),
                    difficulty = o.optInt("difficulty", FOLLOW_DEFAULT),
                    wakeMethod = o.optInt("wakeMethod", 0),
                    soundUri = if (o.isNull("soundUri")) null else o.optString("soundUri"),
                    gentleStart = o.optBoolean("gentleStart", true),
                    skipAt = o.optLong("skipAt", 0),
                )
            }
            val s = root.optJSONObject("settings") ?: JSONObject()
            val settings = AppSettings(
                wakeCode = if (s.isNull("wakeCode")) null else s.optString("wakeCode"),
                puzzleMix = if (s.isNull("puzzleMix")) null else s.optString("puzzleMix"),
                defaultDifficulty = s.optInt("defaultDifficulty", 1),
                theme = s.optInt("theme", 0),
                welcomeDone = s.optBoolean("welcomeDone", false),
                plusNudgeAt = s.optLong("plusNudgeAt", 0),
                plusNudgeDismissals = s.optInt("plusNudgeDismissals", 0),
                appearance = s.optInt("appearance", 0),
            )
            return alarms to settings
        }
    }
}
