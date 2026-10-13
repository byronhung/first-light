package com.byronhung.firstlight.ui.settings

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.material3.Icon
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.byronhung.firstlight.BuildConfig
import com.byronhung.firstlight.data.AppSettings
import com.byronhung.firstlight.data.RoundType
import com.byronhung.firstlight.puzzle.CatalogPuzzle
import com.byronhung.firstlight.puzzle.MIN_MIX
import com.byronhung.firstlight.puzzle.PUZZLE_CATALOG
import com.byronhung.firstlight.puzzle.Preset
import com.byronhung.firstlight.puzzle.effectiveMix
import com.byronhung.firstlight.puzzle.parseMix
import com.byronhung.firstlight.ui.components.PlusSheet
import com.byronhung.firstlight.ui.plus.PlusNudge
import com.byronhung.firstlight.ui.plus.PuzzlePreviewSheet
import com.byronhung.firstlight.ui.components.Segmented
import com.byronhung.firstlight.ui.components.SpringToggle
import com.byronhung.firstlight.ui.components.springClick
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.byronhung.firstlight.data.AlarmRepository
import com.byronhung.firstlight.ui.components.AmberButton
import com.byronhung.firstlight.ui.components.GlassCard
import com.byronhung.firstlight.ui.components.IconSquareButton
import com.byronhung.firstlight.ui.components.QuietButton
import com.byronhung.firstlight.ui.components.SectionLabel
import com.byronhung.firstlight.ui.components.SkyBackground
import com.byronhung.firstlight.ui.components.SunIcons
import com.byronhung.firstlight.ui.theme.AppShapes
import com.byronhung.firstlight.ui.theme.AppText
import com.byronhung.firstlight.ui.theme.LocalSky
import com.byronhung.firstlight.ui.theme.LocalSkyTheme
import com.byronhung.firstlight.ui.theme.Motion
import com.byronhung.firstlight.ui.theme.SkyTheme
import com.byronhung.firstlight.ui.theme.calmSky
import com.byronhung.firstlight.ui.theme.Appearance
import com.byronhung.firstlight.ui.theme.Space
import com.byronhung.firstlight.ui.theme.Sun
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUi(
    val wakeCode: String?,
    val alarmsUsingIt: Int,
    /** The mix as saved (what the picker shows as on). */
    val mix: List<RoundType>,
    val defaultDifficulty: Preset,
    val isPlus: Boolean,
    val theme: SkyTheme,
    val appearance: Appearance = Appearance.BY_HOUR,
)

class SettingsViewModel(private val repository: AlarmRepository) : ViewModel() {
    val ui: StateFlow<SettingsUi?> = combine(repository.settings, repository.alarms) { stored, alarms ->
        val settings = stored ?: AppSettings()
        SettingsUi(
            wakeCode = settings.wakeCode,
            alarmsUsingIt = alarms.count { it.wakeMethod != 0 },
            mix = effectiveMix(parseMix(settings.puzzleMix), settings.isPlus),
            defaultDifficulty = Preset.of(settings.defaultDifficulty),
            isPlus = settings.isPlus,
            theme = SkyTheme.effective(settings.theme, settings.isPlus),
            appearance = Appearance.of(settings.appearance),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setCode(code: String?) {
        viewModelScope.launch { repository.setWakeCode(code) }
    }

    /** Turns a puzzle on or off. Returns false (and changes nothing) if that would leave fewer than [MIN_MIX]. */
    fun toggle(type: RoundType): Boolean {
        val now = ui.value?.mix ?: return false
        val next = if (type in now) now - type else now + type
        if (next.size < MIN_MIX) return false
        viewModelScope.launch { repository.setPuzzleMix(next) }
        return true
    }

    fun setDefaultDifficulty(preset: Preset) {
        viewModelScope.launch { repository.setDefaultDifficulty(preset) }
    }

    fun setAppearance(appearance: Appearance) {
        viewModelScope.launch { repository.setAppearance(appearance) }
    }

    fun setTheme(theme: SkyTheme) {
        viewModelScope.launch { repository.setTheme(theme) }
    }

    fun setPlus(on: Boolean) {
        viewModelScope.launch { repository.setPlus(on) }
    }
}

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onScan: () -> Unit,
    onHelp: () -> Unit,
    onPreviewSky: (SkyTheme) -> Unit,
    onBack: () -> Unit,
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    var plusReason by remember { mutableStateOf<String?>(null) }
    // A locked Plus puzzle being tried.
    var trying by remember { mutableStateOf<CatalogPuzzle?>(null) }
    var mixNote by remember { mutableStateOf<String?>(null) }
    SkyBackground(calmSky(), calm = true) {
        val sky = LocalSky.current
        Column(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.screen, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                Modifier.padding(start = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                IconSquareButton(SunIcons.Back, "Back", onBack)
                Text("Settings", style = AppText.title, color = sky.ink)
            }

            val current = ui
            if (current != null) {
                if (!current.isPlus) PlusTeaser { plusReason = "Three more puzzles and four new skies." }

                GlassCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.Bottom) {
                            SectionLabel("Puzzle mix", Modifier.weight(1f))
                            Text("${current.mix.size} of ${PUZZLE_CATALOG.size} on", style = AppText.caption.copy(fontSize = 12.5.sp), color = sky.mute)
                        }
                        PUZZLE_CATALOG.chunked(3).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                row.forEach { entry ->
                                    MixTile(
                                        entry = entry,
                                        on = entry.type != null && entry.type in current.mix,
                                        isPlus = current.isPlus,
                                        modifier = Modifier.weight(1f),
                                        onTap = {
                                            when {
                                                entry.plus && !current.isPlus -> if (entry.type != null) trying = entry else plusReason = "${entry.name} is part of First Light Plus."
                                                entry.type == null -> mixNote = "${entry.name} is coming soon."
                                                !viewModel.toggle(entry.type) -> mixNote = "At least $MIN_MIX stay on, so mornings never just alternate."
                                                else -> mixNote = null
                                            }
                                        },
                                    )
                                }
                            }
                        }
                        Text(
                            mixNote ?: if (current.isPlus) "Every alarm draws from these. At least $MIN_MIX stay on."
                            else "Every alarm draws from these three. Tap a locked one to try a round.",
                            style = AppText.caption.copy(fontSize = 12.5.sp),
                            color = sky.dim,
                        )
                    }
                }

                GlassCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SectionLabel("Default difficulty")
                        Segmented(
                            listOf("Gentle", "Normal", "Hard"),
                            Preset.entries.indexOf(current.defaultDifficulty),
                            onSelect = { viewModel.setDefaultDifficulty(Preset.entries[it]) },
                        )
                        Text(
                            "Every alarm uses this unless you change that one alarm.",
                            style = AppText.caption.copy(fontSize = 12.5.sp),
                            color = sky.dim,
                        )
                    }
                }

                GlassCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SectionLabel("Theme")
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            SkyTheme.entries.forEach { theme ->
                                val locked = theme.plus && !current.isPlus
                                ThemeSwatch(
                                    theme = theme,
                                    selected = theme == current.theme,
                                    locked = locked,
                                    onTap = {
                                        if (locked) onPreviewSky(theme) else viewModel.setTheme(theme)
                                    },
                                )
                            }
                        }
                    }
                }

                GlassCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SectionLabel("Appearance")
                        Segmented(
                            listOf("By hour", "Light", "Dark", "Phone"),
                            Appearance.entries.indexOf(current.appearance),
                            onSelect = { viewModel.setAppearance(Appearance.entries[it]) },
                        )
                        Text(
                            when (current.appearance) {
                                Appearance.BY_HOUR -> "The sky follows the clock, night to day and back."
                                Appearance.LIGHT -> "Always light. The sky still shifts through the day."
                                Appearance.DARK -> "Always dark. The sky still shifts, from night to dawn to dusk."
                                Appearance.PHONE -> "Light or dark, following your phone's dark mode."
                            } + " The ringing screen is always dawn.",
                            style = AppText.caption.copy(fontSize = 12.5.sp),
                            color = sky.dim,
                        )
                    }
                }
            }

            GlassCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    SectionLabel("Your wake-up code")
                    val code = ui?.wakeCode
                    if (code != null) {
                        BarcodeArt(code)
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(code, style = AppText.header.copy(fontSize = 22.sp, letterSpacing = 0.04.em), color = sky.ink)
                            val n = ui?.alarmsUsingIt ?: 0
                            Text(
                                if (n == 0) "Not used yet. Pick Scan or Both under “How to wake up” on an alarm."
                                else "Used by $n alarm" + if (n == 1) "" else "s",
                                style = AppText.caption,
                                color = sky.dim,
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            AmberButton("Scan a code", onScan, Modifier.weight(1f))
                            QuietButton("Remove", { viewModel.setCode(null) }, Modifier.weight(1f))
                        }
                    } else {
                        Text(
                            "No code yet. Register one and any alarm can end with walking to it.",
                            style = AppText.body,
                            color = sky.dim,
                        )
                        AmberButton("Register a code", onScan, Modifier.fillMaxWidth())
                    }
                }
            }

            GlassCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    SectionLabel("How it works")
                    Step(1, "Scan any barcode or QR code that lives away from your bed. Toothpaste, a cereal box, the kettle. Nothing to print.")
                    Step(2, "First Light saves the number under the bars. It never looks the product up.")
                    Step(3, "Alarms set to Scan or Both stop only when you walk there and scan it.")
                }
            }

            Text(
                "Every tube of the same toothpaste has the same barcode, so a new one still works. Don't keep a spare by the bed.",
                style = AppText.caption.copy(fontSize = 13.sp),
                color = sky.mute,
                modifier = Modifier.padding(horizontal = 8.dp),
            )

            QuietButton("How First Light works", onHelp, Modifier.fillMaxWidth())

            // Debug builds only: flip Plus on and off until Google Play Billing replaces this (P11).
            if (BuildConfig.DEBUG && ui != null) {
                GlassCard(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Debug: Plus", style = AppText.bodyStrong, color = sky.ink)
                            Text("Not in release builds.", style = AppText.caption, color = sky.mute)
                        }
                        SpringToggle(ui?.isPlus == true, { viewModel.setPlus(it) }, "Debug Plus")
                    }
                }
                QuietButton("Debug: show the Plus popup", {
                    PlusNudge.forceNextForDebug = true
                    onBack()
                }, Modifier.fillMaxWidth())
            }
        }

        PuzzlePreviewSheet(
            type = trying?.type,
            name = trying?.name.orEmpty(),
            onGetPlus = {
                plusReason = "${trying?.name} is part of First Light Plus."
                trying = null
            },
            onDismiss = { trying = null },
        )
        PlusSheet(reason = plusReason, onDismiss = { plusReason = null })
    }
}

@Composable
private fun Step(n: Int, text: String) {
    val sky = LocalSky.current
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(Sun.ToggleLight.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) { Text(n.toString(), style = AppText.chip.copy(fontSize = 13.sp), color = Sun.ToggleLight) }
        Text(text, style = AppText.body.copy(fontSize = 15.sp), color = sky.ink.copy(alpha = 0.85f))
    }
}

/** A barcode drawn from your actual code's digits, with the prototype's moving scan line. */
@Composable
private fun BarcodeArt(code: String) {
    val t = rememberInfiniteTransition(label = "scan")
    val y by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1_200, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "line")
    val ink = Color(0xFF2A1D3A)
    Box(
        Modifier
            .fillMaxWidth()
            .height(96.dp)
            .clip(AppShapes.button)
            .background(Color(0xFFFFF6EC)),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(62.dp)
                .padding(horizontal = 24.dp),
        ) {
            // Each digit becomes a bar and a gap of its own widths: the same code always looks the same.
            val units = code.flatMap { c -> val d = c.code % 10; listOf(1 + d % 3, 1 + (d / 3) % 3) }
            val unit = size.width / units.sum().coerceAtLeast(1)
            var x = 0f
            units.forEachIndexed { i, u ->
                if (i % 2 == 0) drawRect(ink, Offset(x, 0f), Size(u * unit, size.height))
                x += u * unit
            }
        }
        Canvas(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 16.dp),
        ) {
            val ly = size.height * y
            drawLine(Sun.Rose.copy(alpha = 0.35f), Offset(0f, ly), Offset(size.width, ly), strokeWidth = 10.dp.toPx())
            drawLine(Sun.Rose, Offset(0f, ly), Offset(size.width, ly), strokeWidth = 2.dp.toPx())
        }
    }
}

/** One puzzle in the mix picker: amber when on, a lock when it's Plus and not owned. */
@Composable
private fun MixTile(entry: CatalogPuzzle, on: Boolean, isPlus: Boolean, modifier: Modifier, onTap: () -> Unit) {
    val sky = LocalSky.current
    val locked = entry.plus && !isPlus
    val soon = entry.type == null && !locked
    Box(
        modifier
            .springClick(0.92f, onClick = onTap)
            .height(74.dp)
            .clip(AppShapes.small)
            .background(if (on) Sun.ToggleLight.copy(alpha = 0.16f) else sky.card)
            .border(1.dp, if (on) Sun.ToggleLight.copy(alpha = 0.6f) else sky.cardEdge, AppShapes.small)
            .semantics { contentDescription = entry.name + when { locked -> ", Plus"; soon -> ", coming soon"; on -> ", on"; else -> ", off" } },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(entry.name, style = AppText.chip.copy(fontSize = 12.sp), color = sky.ink.copy(alpha = if (locked || soon) 0.55f else 1f))
            if (soon) Text("Coming soon", style = AppText.chip.copy(fontSize = 10.sp), color = sky.mute)
            if (locked) Text("Try it", style = AppText.chip.copy(fontSize = 10.5.sp), color = Sun.ToggleLight)
        }
        if (locked) {
            Icon(SunIcons.Lock, contentDescription = null, tint = Sun.ToggleLight, modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(12.dp))
        }
    }
}

/** One sky in the Theme row: its night-to-dawn colours, amber-ringed when chosen, a lock when it's Plus. */
@Composable
private fun ThemeSwatch(theme: SkyTheme, selected: Boolean, locked: Boolean, onTap: () -> Unit) {
    val sky = LocalSky.current
    val scale by animateFloatAsState(if (selected) 1.06f else 1f, Motion.bouncy(), label = "swatch")
    Column(
        Modifier
            .springClick(0.9f, onClick = onTap)
            .semantics(mergeDescendants = true) {
                contentDescription = theme.label + when { locked -> ", Plus"; selected -> ", chosen"; else -> "" }
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            Modifier
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    alpha = if (locked) 0.55f else 1f
                }
                .size(52.dp)
                .clip(AppShapes.small)
                .background(Brush.linearGradient(theme.swatch))
                .border(2.dp, if (selected) Sun.ToggleLight else Color.Transparent, AppShapes.small),
        ) {
            if (locked) {
                Icon(SunIcons.Lock, contentDescription = null, tint = Sun.ToggleLight, modifier = Modifier.align(Alignment.TopEnd).padding(5.dp).size(12.dp))
            }
        }
        // "Neon city" is too long for a 52 dp swatch; the first word is the name people use.
        Text(theme.label.substringBefore(' '), style = AppText.chip.copy(fontSize = 11.sp), color = sky.ink.copy(alpha = if (locked) 0.6f else 1f))
    }
}

/** The Settings banner for free users: one line, opens the Plus sheet. */
@Composable
private fun PlusTeaser(onClick: () -> Unit) {
    val sky = LocalSky.current
    Row(
        Modifier
            .fillMaxWidth()
            .springClick(0.97f, onClick = onClick)
            .clip(AppShapes.button)
            .background(Brush.linearGradient(listOf(Sun.AmberLight.copy(alpha = 0.22f), Sun.Amber.copy(alpha = 0.22f))))
            .border(1.dp, Sun.ToggleLight.copy(alpha = 0.45f), AppShapes.button)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("First Light Plus", style = AppText.bodyStrong, color = sky.ink)
            Text("3 puzzles and 4 skies. Pay once.", style = AppText.caption, color = sky.dim)
        }
        Text("See", style = AppText.bodyStrong, color = Sun.ToggleLight)
    }
}
