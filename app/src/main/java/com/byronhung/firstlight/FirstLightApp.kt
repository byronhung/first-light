package com.byronhung.firstlight

import android.app.Application
import com.byronhung.firstlight.alarm.AlarmScheduler
import com.byronhung.firstlight.alarm.PendingCheckStore
import com.byronhung.firstlight.data.AlarmRepository
import com.byronhung.firstlight.data.Backup
import com.byronhung.firstlight.data.FirstLightDatabase
import com.byronhung.firstlight.ring.Notifications
import com.byronhung.firstlight.ring.RingController
import com.byronhung.firstlight.ring.Ringer
import com.byronhung.firstlight.ui.theme.SkyTheme
import com.byronhung.firstlight.ui.theme.Appearance
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Holds the app's single database, repository and ring controller. Grit used Hilt for this; a
 * handful of objects doesn't need a dependency-injection framework.
 */
class FirstLightApp : Application() {
    /** Outlives any screen: for work that must finish even if the screen closes (saving a wake). */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val scheduler: AlarmScheduler by lazy { AlarmScheduler(this) }
    val repository: AlarmRepository by lazy { AlarmRepository(FirstLightDatabase.build(this), scheduler, PendingCheckStore(this)) }
    val ringController: RingController by lazy { RingController(repository, appScope) }

    /**
     * The chosen sky, kept warm for the whole process: the ringing screen opens on the right theme
     * instead of flashing Sunrise while the database answers.
     */
    val appearance: StateFlow<Appearance> by lazy {
        repository.settings
            .map { Appearance.of(it?.appearance ?: 0) }
            .stateIn(appScope, SharingStarted.Eagerly, Appearance.BY_HOUR)
    }

    val skyTheme: StateFlow<SkyTheme> by lazy {
        repository.settings
            .map { SkyTheme.effective(it?.theme ?: 0, it?.isPlus == true) }
            .stateIn(appScope, SharingStarted.Eagerly, SkyTheme.SUNRISE)
    }

    override fun onCreate() {
        super.onCreate()
        Notifications.ensureChannel(this)
        Ringer.restoreAfterCrash(this)
        skyTheme.value // Starts reading it now, before any alarm can ring.
        appearance.value
        // Covers the cases no broadcast announces, like a force-stop wiping every scheduled alarm.
        appScope.launch { repository.rescheduleAll() }
        Backup(this, repository, appScope).start()
    }
}
