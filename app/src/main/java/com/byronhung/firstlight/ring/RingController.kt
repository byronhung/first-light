package com.byronhung.firstlight.ring

import android.os.SystemClock
import com.byronhung.firstlight.data.AlarmRepository
import com.byronhung.firstlight.data.RoundType
import com.byronhung.firstlight.data.WakeOutcome
import com.byronhung.firstlight.puzzle.Preset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Everything the ringing screen needs, fixed when the alarm fires. */
data class RingSession(
    val alarmId: Long,
    val wakeId: Long,
    val hour: Int,
    val minute: Int,
    val label: String,
    val rounds: List<RoundType>,
    val levels: Map<RoundType, Int>,
    /** The puzzles this morning draws from: what "Can't scan now?" swaps the scan for. */
    val mix: List<RoundType> = emptyList(),
    val qrCode: String?,
    /** "Still awake?" checks after solving, 0–3. */
    val wakeChecks: Int = 0,
    val preset: Preset = Preset.NORMAL,
    /** Null = the phone's default alarm sound. */
    val soundUri: String? = null,
    /** "Test this alarm" from the editor: rings for real, but nothing is logged or scheduled. */
    val isTest: Boolean = false,
    /** Fade in over the first 30 s. Off for a re-ring after a missed wake check: you fell back asleep. */
    val gentleStart: Boolean = false,
)

/** A solved round, before it's written to the database. */
data class RoundResultDraft(val type: RoundType, val level: Int, val solveMs: Long, val misses: Int)

sealed interface RingState {
    data object Idle : RingState

    /** The service has woken up and is reading the alarm from the database. */
    data object Starting : RingState

    data class Ringing(val session: RingSession) : RingState
}

/**
 * The single source of truth for "is an alarm ringing right now". The service drives it, the
 * ringing screen reads it, and [finish] is the only way a ring ends: solved, given up or timed out.
 *
 * Grit's `00:00` ghost came from a screen trusting a stale copy of this fact. Here every reader
 * asks this one object, and a screen that finds [RingState.Idle] closes itself.
 */
class RingController(
    private val repository: AlarmRepository,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow<RingState>(RingState.Idle)
    val state: StateFlow<RingState> = _state.asStateFlow()

    val isActive: Boolean get() = _state.value != RingState.Idle

    /** The ringing screen is open and in front. The ring service uses it to drop the banner. */
    val screenVisible = MutableStateFlow(false)

    /** A test ring handed over by the editor, picked up by [RingService] when it starts. */
    @Volatile var pendingTest: RingSession? = null

    private val _lastTap = MutableStateFlow<Long?>(null)

    /** When the puzzle was last tapped this ring (elapsedRealtime). See [QuietWhileSolving]. */
    val lastTap: StateFlow<Long?> = _lastTap.asStateFlow()

    fun puzzleTapped(at: Long = SystemClock.elapsedRealtime()) {
        if (_state.value is RingState.Ringing) _lastTap.value = at
    }

    fun markStarting() {
        _lastTap.value = null
        _state.value = RingState.Starting
    }

    fun begin(session: RingSession) {
        _lastTap.value = null
        _state.value = RingState.Ringing(session)
    }

    /** The alarm turned out not to need ringing (deleted or switched off in the meantime). */
    fun abort() {
        _lastTap.value = null
        _state.value = RingState.Idle
    }

    fun finish(outcome: WakeOutcome, results: List<RoundResultDraft>) {
        val ringing = _state.value as? RingState.Ringing
        _lastTap.value = null
        _state.value = RingState.Idle
        if (ringing != null) {
            scope.launch { repository.finishWake(ringing.session, outcome, results) }
        }
    }
}
