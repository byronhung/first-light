package com.byronhung.firstlight.data

import com.byronhung.firstlight.alarm.AlarmScheduler
import com.byronhung.firstlight.alarm.PendingCheckStore
import com.byronhung.firstlight.alarm.WakeCheck
import com.byronhung.firstlight.alarm.Weekdays
import com.byronhung.firstlight.puzzle.Difficulty
import com.byronhung.firstlight.puzzle.Levels
import com.byronhung.firstlight.puzzle.PastRound
import com.byronhung.firstlight.puzzle.Preset
import com.byronhung.firstlight.puzzle.effectiveMix
import com.byronhung.firstlight.puzzle.effectivePreset
import com.byronhung.firstlight.puzzle.encodeMix
import com.byronhung.firstlight.puzzle.parseMix
import com.byronhung.firstlight.puzzle.WakeMethod
import com.byronhung.firstlight.puzzle.roundsFor
import com.byronhung.firstlight.ui.theme.SkyTheme
import com.byronhung.firstlight.ui.theme.Appearance
import com.byronhung.firstlight.puzzle.planMorning
import com.byronhung.firstlight.ring.RingSession
import com.byronhung.firstlight.ring.RoundResultDraft
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDateTime
import kotlin.random.Random

/**
 * The one door to saved alarms and wake history. Screens never touch the DAOs, and every change
 * to an alarm schedules or cancels the real Android alarm in the same step.
 *
 * A mutex serialises the writes, because two paths can land at once: the app starting up
 * (rescheduleAll) and an alarm firing (beginWake) happen together whenever an alarm wakes the app.
 */
class AlarmRepository(
    private val database: FirstLightDatabase,
    private val scheduler: AlarmScheduler,
    private val pendingCheck: PendingCheckStore,
) {
    private val alarmDao = database.alarmDao()
    private val wakeDao = database.wakeDao()
    private val mutex = Mutex()

    val alarms: Flow<List<Alarm>> = alarmDao.observeAll()
    val recentWakes: Flow<List<WakeLog>> = wakeDao.observeRecent(60)

    fun wakesBetween(range: LongRange): Flow<List<WakeLog>> = wakeDao.observeBetween(range.first, range.last + 1)
    val settings: Flow<AppSettings?> = wakeDao.observeSettings()

    suspend fun setWakeCode(code: String?) = editSettings { it.copy(wakeCode = code?.trim()?.ifEmpty { null }) }

    suspend fun setPuzzleMix(mix: List<RoundType>) = editSettings { it.copy(puzzleMix = encodeMix(mix)) }

    suspend fun setDefaultDifficulty(preset: Preset) = editSettings { it.copy(defaultDifficulty = preset.code) }

    /** Until Google Play Billing exists (chunk P11), only the debug switch in Settings calls this. */
    suspend fun setPlus(on: Boolean) = editSettings { it.copy(isPlus = on) }

    suspend fun setTheme(theme: SkyTheme) = editSettings { it.copy(theme = theme.code) }

    suspend fun setWelcomeDone() = editSettings { it.copy(welcomeDone = true) }

    suspend fun setAppearance(appearance: Appearance) = editSettings { it.copy(appearance = appearance.code) }

    /** The Plus popup just showed: start its 10-day wait. */
    suspend fun plusNudgeShown(at: Long = System.currentTimeMillis()) = editSettings { it.copy(plusNudgeAt = at) }

    /** "Not now" on the Plus popup. */
    suspend fun plusNudgeDismissed() = editSettings { it.copy(plusNudgeDismissals = it.plusNudgeDismissals + 1) }

    /** The settings as they are now, read once. */
    suspend fun currentSettings(): AppSettings = wakeDao.settings() ?: AppSettings()

    private suspend fun editSettings(change: (AppSettings) -> AppSettings) = mutex.withLock {
        wakeDao.saveSettings(change(wakeDao.settings() ?: AppSettings()))
    }

    suspend fun get(id: Long): Alarm? = alarmDao.get(id)

    /** Inserts a new alarm (id 0) or updates an existing one, then schedules it. Returns its id. */
    suspend fun save(alarm: Alarm): Long = mutex.withLock {
        val id = if (alarm.id == 0L) alarmDao.insert(alarm) else alarm.id.also { alarmDao.update(alarm) }
        applySchedule(alarm.copy(id = id))
        id
    }

    suspend fun setEnabled(id: Long, enabled: Boolean) = mutex.withLock {
        alarmDao.setEnabled(id, enabled)
        alarmDao.get(id)?.let { applySchedule(it) }
    }

    suspend fun delete(id: Long) = mutex.withLock {
        scheduler.cancel(id)
        alarmDao.delete(id)
    }

    /** Deletes several at once. Returns what was deleted, so the list can offer Undo. */
    suspend fun deleteAll(ids: Collection<Long>): List<Alarm> = mutex.withLock {
        ids.mapNotNull { id ->
            alarmDao.get(id)?.also {
                scheduler.cancel(id)
                alarmDao.delete(id)
            }
        }
    }

    /** Undo: puts alarms back with their old ids, so History still names them, and schedules them. */
    suspend fun restore(alarms: List<Alarm>) = mutex.withLock {
        alarms.forEach {
            alarmDao.insert(it)
            applySchedule(it)
        }
    }

    suspend fun hasAlarms(): Boolean = alarmDao.getAll().isNotEmpty()

    /** A backup from before a reinstall: settings and alarms back in place, alarms scheduled. Keeps Plus as it is. */
    suspend fun restoreBackup(alarms: List<Alarm>, settings: AppSettings) = mutex.withLock {
        val plus = wakeDao.settings()?.isPlus ?: false
        wakeDao.saveSettings(settings.copy(isPlus = plus))
        alarms.forEach {
            alarmDao.insert(it)
            applySchedule(it)
        }
    }

    /** After a reboot, a clock change, a force-stop, or any app start: make Android match the table. */
    suspend fun rescheduleAll() = mutex.withLock {
        alarmDao.getAll().forEach { applySchedule(it) }
    }

    private fun applySchedule(alarm: Alarm) {
        if (alarm.enabled) scheduler.schedule(alarm) else scheduler.cancel(alarm.id)
    }

    /**
     * An alarm just fired. Sets up its next ring, plans the morning, and opens a wake record.
     * Returns null if it shouldn't ring after all (deleted or switched off since it was scheduled).
     *
     * [isRering]: a missed wake check is ringing it again. The alarm may have switched itself off
     * already (one-offs do, at fire time) and its next ring is already set, so neither is touched.
     */
    suspend fun beginWake(
        alarmId: Long,
        now: LocalDateTime = LocalDateTime.now(),
        isRering: Boolean = false,
    ): RingSession? = mutex.withLock {
        val alarm = alarmDao.get(alarmId)
        if (alarm == null || (!alarm.enabled && !isRering)) {
            if (!isRering) scheduler.cancel(alarmId)
            return@withLock null
        }
        if (!isRering) scheduleNextAfterFiring(alarm, now)
        // A ring takes over from any waiting wake check: this morning's solve sets a fresh one.
        cancelPendingCheck()

        val planned = plan(alarm).copy(gentleStart = alarm.gentleStart && !isRering)
        val wakeId = wakeDao.insertWake(
            WakeLog(alarmId = alarmId, firedAt = System.currentTimeMillis(), opener = planned.rounds.first()),
        )
        planned.copy(wakeId = wakeId)
    }

    /**
     * "Test this alarm": the same morning [alarm] would get (unsaved edits included), flagged as a
     * test. It writes nothing, schedules nothing, and never sets a wake check.
     */
    suspend fun testSession(alarm: Alarm): RingSession = mutex.withLock {
        val planned = plan(alarm)
        planned.copy(isTest = true, wakeChecks = 0, gentleStart = alarm.gentleStart, label = if (alarm.label.isBlank()) "Test" else "Test · ${alarm.label}")
    }

    /** Plans a morning for [alarm]: puzzles, levels, the scan. No side effects; wakeId is unset. */
    private suspend fun plan(alarm: Alarm): RingSession {
        val settings = wakeDao.settings() ?: AppSettings()
        val wakeCode = settings.wakeCode
        // The mix is app-wide now (Settings); the alarm's own roundTypes column is no longer read.
        val puzzles = effectiveMix(parseMix(settings.puzzleMix), settings.isPlus)
        val lastOpener = wakeDao.lastOpener()?.let { runCatching { RoundType.valueOf(it) }.getOrNull() }
        val preset = effectivePreset(override = alarm.difficulty, default = settings.defaultDifficulty)
        val method = WakeMethod.of(alarm.wakeMethod)
        val rounds = roundsFor(
            method,
            planMorning(puzzles, lastOpener, Random.Default, rounds = preset.rounds),
            hasCode = wakeCode != null,
        )
        val levels = puzzles.associateWith { preset.level(adaptive = levelFor(it)) }

        return RingSession(
            alarmId = alarm.id,
            wakeId = -1,
            hour = alarm.hour,
            minute = alarm.minute,
            label = alarm.label,
            rounds = rounds,
            levels = levels,
            mix = puzzles,
            qrCode = if (RoundType.QR_SCAN in rounds) wakeCode else null,
            wakeChecks = alarm.wakeChecks.coerceIn(0, WakeCheck.MAX),
            preset = preset,
            soundUri = alarm.soundUri,
        )
    }

    /** A second alarm fired while one is already ringing: don't ring twice, but keep its schedule right. */
    suspend fun skipWake(alarmId: Long, now: LocalDateTime = LocalDateTime.now()) = mutex.withLock {
        alarmDao.get(alarmId)?.takeIf { it.enabled }?.let { scheduleNextAfterFiring(it, now) }
    }

    /**
     * Done at fire time, before the ring starts, so a crash mid-ring can't lose tomorrow's alarm.
     * A one-off alarm switches itself off; a repeating one is set for its next day.
     */
    private suspend fun scheduleNextAfterFiring(alarm: Alarm, now: LocalDateTime) {
        if (alarm.repeatDays == Weekdays.NONE) {
            alarmDao.setEnabled(alarm.id, false)
            scheduler.cancel(alarm.id)
        } else {
            scheduler.schedule(alarm, now)
        }
    }

    /**
     * Only a solve earns wake checks: a give-up or a time-out has already ended the morning. A no-scan
     * solve counts, so "Can't scan now?" tapped in bed still has a check waiting.
     */
    suspend fun finishWake(session: RingSession, outcome: WakeOutcome, results: List<RoundResultDraft>) {
        if (session.isTest) return
        val wakeId = session.wakeId
        wakeDao.finish(wakeId, System.currentTimeMillis(), outcome.name)
        // Only Normal mornings teach the automatic difficulty: a Gentle or Hard one says nothing
        // about how fast you are at your own level.
        if (results.isNotEmpty() && session.preset.feedsAdaptive) {
            wakeDao.insertResults(
                results.map { RoundResult(wakeId = wakeId, type = it.type, level = it.level, solveMs = it.solveMs, misses = it.misses) },
            )
        }
        if (outcome == WakeOutcome.SOLVED || outcome == WakeOutcome.NO_SCAN) {
            WakeCheck.first(session.alarmId, wakeId, session.wakeChecks)?.let { scheduleCheck(it) }
        }
    }

    /** Answered in time, or passed silently because the phone was in use. Sets up the next one. */
    suspend fun checkPassed(check: WakeCheck) {
        // An old check answered late must not overwrite the one that's actually waiting.
        if (!isPendingCheck(check)) return
        scheduler.cancelCheckDeadline(check)
        wakeDao.checkPassed(check.wakeId)
        val next = check.next()
        if (next != null) scheduleCheck(next) else pendingCheck.clear()
    }

    /** Missed: the caller rings the alarm again, and that morning gets its own checks if solved. */
    /** Whether [check] is the one waiting check; anything else is stale and must do nothing. */
    fun isPendingCheck(check: WakeCheck): Boolean = pendingCheck.get() == check

    /**
     * One wake check at a time: setting one cancels whatever was waiting. Two alarms at 12:40 and
     * 12:42 give one check, after the 12:42 solve, not one after each.
     */
    private fun scheduleCheck(check: WakeCheck) {
        cancelPendingCheck()
        pendingCheck.set(check)
        scheduler.scheduleWakeCheck(check)
    }

    private fun cancelPendingCheck() {
        pendingCheck.get()?.let { scheduler.cancelWakeCheck(it) }
        pendingCheck.clear()
    }

    suspend fun checkMissed(check: WakeCheck) {
        pendingCheck.clear()
        wakeDao.checkMissed(check.wakeId)
    }

    /** This morning's level for a puzzle type, moved by the last five rounds at the current level. */
    private suspend fun levelFor(type: RoundType): Int {
        val recent = wakeDao.recentResults(type.name, limit = 20)
        val current = recent.firstOrNull()?.level ?: Levels.DEFAULT
        val atLevel = recent.filter { it.level == current }.map { PastRound(it.solveMs, it.misses) }
        return Difficulty.nextLevel(type, current, atLevel)
    }
}
