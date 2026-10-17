package com.byronhung.firstlight

import android.content.Intent
import android.os.Bundle
import android.text.format.DateFormat
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.flow.map
import com.byronhung.firstlight.ui.plus.ThankYouScreen
import com.byronhung.firstlight.ui.theme.SkyTheme
import com.byronhung.firstlight.ui.plus.SkyPreviewScreen
import com.byronhung.firstlight.ui.welcome.WelcomeScreen
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.byronhung.firstlight.ring.RingActivity
import com.byronhung.firstlight.ring.RingState
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.byronhung.firstlight.ui.edit.AlarmEditScreen
import com.byronhung.firstlight.ui.edit.AlarmEditViewModel
import com.byronhung.firstlight.ui.history.HistoryScreen
import com.byronhung.firstlight.ui.history.HistoryViewModel
import com.byronhung.firstlight.ui.list.AlarmListScreen
import com.byronhung.firstlight.ui.list.AlarmListViewModel
import com.byronhung.firstlight.ui.scan.ScanScreen
import com.byronhung.firstlight.ui.settings.SettingsScreen
import com.byronhung.firstlight.ui.settings.SettingsViewModel
import com.byronhung.firstlight.ui.theme.FirstLightTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onResume() {
        super.onResume()
        // A permission granted from the setup card may have made scheduling possible.
        val app = application as FirstLightApp
        app.appScope.launch { app.repository.rescheduleAll() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val repository = (application as FirstLightApp).repository
        val is24Hour = DateFormat.is24HourFormat(this)

        // While an alarm rings, opening the app always lands on the ringing screen. If notifications are
        // off, Android never shows that screen by itself, and this is the only way to reach the puzzles
        // (a tester's alarm rang with no way to stop it but Force stop).
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                (application as FirstLightApp).ringController.state.collect { state ->
                    if (state is RingState.Ringing) {
                        startActivity(Intent(this@MainActivity, RingActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                }
            }
        }

        setContent {
            val skyTheme by (application as FirstLightApp).skyTheme.collectAsState()
            val appearance by (application as FirstLightApp).appearance.collectAsState()
            FirstLightTheme(skyTheme, appearance) {
                val nav = rememberNavController()
                // Plus switching on while the app is open (the debug switch now, Play Billing later)
                // ends on the thank-you screen. The first value is just the app starting up.
                val isPlus by remember { repository.settings.map { it?.isPlus == true } }.collectAsState(initial = null)
                var hadPlus by remember { mutableStateOf<Boolean?>(null) }
                LaunchedEffect(isPlus) {
                    val now = isPlus ?: return@LaunchedEffect
                    if (hadPlus == false && now) nav.navigate("thanks")
                    hadPlus = now
                }
                NavHost(
                    navController = nav,
                    startDestination = "list",
                    // Screens slide a little and fade, instead of the stock hard cut.
                    enterTransition = { slideInHorizontally(tween(320)) { it / 8 } + fadeIn(tween(320)) },
                    exitTransition = { fadeOut(tween(180)) },
                    popEnterTransition = { fadeIn(tween(320)) },
                    popExitTransition = { slideOutHorizontally(tween(260)) { it / 8 } + fadeOut(tween(220)) },
                ) {
                    composable("list") {
                        // First launch: the welcome, once. Finishing or skipping it marks it done.
                        LaunchedEffect(Unit) {
                            if (!repository.currentSettings().welcomeDone) nav.navigate("welcome?replay=false")
                        }
                        val vm: AlarmListViewModel = viewModel(
                            factory = viewModelFactory { initializer { AlarmListViewModel(repository, is24Hour) } },
                        )
                        AlarmListScreen(
                            viewModel = vm,
                            onAdd = { nav.navigate("edit") },
                            onEdit = { id -> nav.navigate("edit?id=$id") },
                            onHistory = { nav.navigate("history") },
                            onSettings = { nav.navigate("settings") },
                        )
                    }
                    composable("history") {
                        val vm: HistoryViewModel = viewModel(
                            factory = viewModelFactory { initializer { HistoryViewModel(repository, is24Hour) } },
                        )
                        HistoryScreen(viewModel = vm, onBack = { nav.popBackStack() })
                    }
                    composable("settings") { entry ->
                        val vm: SettingsViewModel = viewModel(
                            factory = viewModelFactory { initializer { SettingsViewModel(repository) } },
                        )
                        val scanned by entry.savedStateHandle.getStateFlow<String?>(SCANNED_CODE, null).collectAsState()
                        LaunchedEffect(scanned) {
                            scanned?.let {
                                vm.setCode(it)
                                entry.savedStateHandle[SCANNED_CODE] = null
                            }
                        }
                        SettingsScreen(
                            viewModel = vm,
                            onScan = { nav.navigate("scan") },
                            onHelp = { nav.navigate("welcome?replay=true") },
                            onPreviewSky = { nav.navigate("sky/${it.code}") },
                            onBack = { nav.popBackStack() },
                        )
                    }
                    composable("thanks") {
                        ThankYouScreen(onPickSky = {
                            nav.navigate("settings") { popUpTo("list") }
                        })
                    }
                    composable(
                        route = "sky/{code}",
                        arguments = listOf(navArgument("code") { type = NavType.IntType }),
                    ) { entry ->
                        SkyPreviewScreen(SkyTheme.of(entry.arguments?.getInt("code") ?: 0), onBack = { nav.popBackStack() })
                    }
                    composable(
                        route = "welcome?replay={replay}",
                        arguments = listOf(navArgument("replay") { type = NavType.BoolType; defaultValue = false }),
                    ) { entry ->
                        val replay = entry.arguments?.getBoolean("replay") == true
                        val settings by repository.settings.collectAsState(initial = null)
                        val scope = rememberCoroutineScope()
                        val scanned by entry.savedStateHandle.getStateFlow<String?>(SCANNED_CODE, null).collectAsState()
                        LaunchedEffect(scanned) {
                            scanned?.let {
                                repository.setWakeCode(it)
                                entry.savedStateHandle[SCANNED_CODE] = null
                            }
                        }
                        WelcomeScreen(
                            wakeCode = settings?.wakeCode,
                            replay = replay,
                            onScan = { nav.navigate("scan") },
                            onDone = {
                                scope.launch {
                                    repository.setWelcomeDone()
                                    if (replay) {
                                        nav.popBackStack()
                                    } else {
                                        // Straight into setting the first alarm, with the welcome gone from Back.
                                        nav.navigate("edit") { popUpTo("welcome?replay={replay}") { inclusive = true } }
                                    }
                                }
                            },
                        )
                    }
                    composable("scan") {
                        ScanScreen(
                            // Hand the code back to whichever screen opened the scanner (Settings or an alarm).
                            onCode = { code ->
                                nav.previousBackStackEntry?.savedStateHandle?.set(SCANNED_CODE, code)
                                if (nav.currentDestination?.route == "scan") nav.popBackStack()
                            },
                            onBack = { nav.popBackStack() },
                        )
                    }
                    composable(
                        route = "edit?id={id}",
                        arguments = listOf(navArgument("id") { type = NavType.LongType; defaultValue = -1L }),
                    ) { entry ->
                        val id = entry.arguments?.getLong("id")?.takeIf { it >= 0 }
                        val vm: AlarmEditViewModel = viewModel(
                            factory = viewModelFactory { initializer { AlarmEditViewModel(repository, id) } },
                        )
                        val scanned by entry.savedStateHandle.getStateFlow<String?>(SCANNED_CODE, null).collectAsState()
                        LaunchedEffect(scanned) {
                            scanned?.let {
                                vm.codeScanned(it)
                                entry.savedStateHandle[SCANNED_CODE] = null
                            }
                        }
                        AlarmEditScreen(
                            viewModel = vm,
                            onScanForCode = { nav.navigate("scan") },
                            // Only pop if we're still on the edit screen, so a double tap can't pop the list too.
                            onDone = {
                                if (nav.currentDestination?.route?.startsWith("edit") == true) nav.popBackStack()
                            },
                        )
                    }
                }
            }
        }
    }

    private companion object {
        const val SCANNED_CODE = "scannedCode"
    }
}
