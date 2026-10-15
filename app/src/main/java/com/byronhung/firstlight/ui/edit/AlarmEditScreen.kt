package com.byronhung.firstlight.ui.edit

import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.text.style.TextOverflow
import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.byronhung.firstlight.alarm.Weekdays
import com.byronhung.firstlight.alarm.formatTime
import com.byronhung.firstlight.puzzle.Preset
import com.byronhung.firstlight.data.FOLLOW_DEFAULT
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.graphicsLayer
import com.byronhung.firstlight.puzzle.WakeMethod
import com.byronhung.firstlight.ui.components.AmberButton
import com.byronhung.firstlight.ui.components.DayPill
import com.byronhung.firstlight.ui.components.GlassCard
import com.byronhung.firstlight.ui.components.SectionLabel
import com.byronhung.firstlight.ui.components.Segmented
import com.byronhung.firstlight.ui.components.SkyBackground
import com.byronhung.firstlight.ui.components.QuietButton
import com.byronhung.firstlight.alarm.nextTrigger
import com.byronhung.firstlight.ui.components.SpringToggle
import com.byronhung.firstlight.ui.components.WheelColumn
import com.byronhung.firstlight.ui.components.springClick
import com.byronhung.firstlight.ui.theme.AppShapes
import com.byronhung.firstlight.ui.theme.AppText
import com.byronhung.firstlight.ui.theme.LocalSky
import com.byronhung.firstlight.ui.theme.LocalSkyTheme
import com.byronhung.firstlight.ui.theme.skyAt
import com.byronhung.firstlight.ui.theme.Space
import java.time.DayOfWeek
import java.time.LocalTime

@Composable
fun AlarmEditScreen(viewModel: AlarmEditViewModel, onScanForCode: () -> Unit, onDone: () -> Unit) {
    val wakeCode by viewModel.wakeCode.collectAsStateWithLifecycle()
    val defaultDifficulty by viewModel.defaultDifficulty.collectAsStateWithLifecycle()
    val context = LocalContext.current
    SkyBackground(skyAt(LocalTime.now().hour), calm = true) {
        val draft = viewModel.draft ?: return@SkyBackground
        AlarmEditContent(
            draft = draft,
            hasWakeCode = wakeCode != null,
            defaultDifficulty = defaultDifficulty,
            onMethod = { if (viewModel.pickMethod(it)) onScanForCode() },
            onCancel = onDone,
            onSave = { viewModel.save(onDone) },
            onTime = viewModel::setTime,
            onToggleDay = viewModel::toggleDay,
            onChecks = viewModel::setWakeChecks,
            onDifficulty = viewModel::setDifficulty,
            onSound = viewModel::setSound,
            onGentle = viewModel::setGentle,
            onSkipNext = viewModel::setSkipNext,
            onLabel = viewModel::setLabel,
            onDelete = { viewModel.delete(onDone) },
            onTest = { viewModel.test(context) },
        )
    }
}

private val DAY_LETTERS = listOf("M", "T", "W", "T", "F", "S", "S")
private val DAY_NAMES = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")

@Composable
private fun AlarmEditContent(
    draft: EditDraft,
    hasWakeCode: Boolean,
    defaultDifficulty: Preset,
    onMethod: (WakeMethod) -> Unit,
    onCancel: () -> Unit,
    onSave: () -> Unit,
    onTime: (Int, Int) -> Unit,
    onToggleDay: (Int) -> Unit,
    onChecks: (Int) -> Unit,
    onDifficulty: (Int) -> Unit,
    onSound: (String?) -> Unit,
    onGentle: (Boolean) -> Unit,
    onSkipNext: (Boolean) -> Unit,
    onLabel: (String) -> Unit,
    onDelete: () -> Unit,
    onTest: () -> Unit,
) {
    val sky = LocalSky.current
    val is24Hour = DateFormat.is24HourFormat(LocalContext.current)

    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .imePadding(),
    ) {
        Row(
            Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Cancel",
                style = AppText.prompt,
                color = sky.dim,
                modifier = Modifier
                    .springClick(0.92f, onClick = onCancel)
                    .padding(vertical = 10.dp, horizontal = 4.dp),
            )
            Text(
                if (draft.isNew) "New alarm" else "Edit alarm",
                style = AppText.header,
                color = sky.ink,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            AmberButton("Save", onSave, height = 40.dp)
        }

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.screen, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            TimeWheels(draft.hour, draft.minute, is24Hour, onTime)
            Text(
                "Rings at ${formatTime(draft.hour, draft.minute, is24Hour)} ${repeatPhrase(draft.repeatDays)}",
                style = AppText.caption,
                color = sky.dim,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .offset(y = (-4).dp),
            )

            Section {
                SectionLabel("Repeat")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    DayOfWeek.entries.forEachIndexed { i, day ->
                        DayPill(DAY_LETTERS[i], DAY_NAMES[i], Weekdays.has(draft.repeatDays, day)) { onToggleDay(i) }
                    }
                }
            }

            // Everything below already follows your defaults. Each row shows its value and only
            // opens if this one alarm should be different: setting an alarm is time, days, save.
            val method = WakeMethod.of(draft.wakeMethod)
            var open by remember { mutableStateOf<String?>(null) }
            fun toggle(row: String) { open = if (open == row) null else row }
            GlassCard(Modifier.fillMaxWidth()) {
                ExpandableRow("How to wake up", methodName(method), open == "method", { toggle("method") }, first = true) {
                    Segmented(listOf("Puzzles", "Scan", "Both"), WakeMethod.entries.indexOf(method), onSelect = { onMethod(WakeMethod.entries[it]) })
                    Text(methodLine(method, hasWakeCode), style = AppText.caption, color = sky.dim)
                }
                // Difficulty only means something when there are puzzles.
                if (method != WakeMethod.SCAN) {
                    val override = draft.difficulty
                    val shown = if (override < 0) "${defaultDifficulty.label} · your default" else "${Preset.of(override).label} · this alarm only"
                    ExpandableRow("Difficulty", shown, open == "difficulty", { toggle("difficulty") }) {
                        // Index 0 = follow the default; 1–3 = Gentle, Normal, Hard for this alarm.
                        val selected = if (override < 0) 0 else Preset.entries.indexOf(Preset.of(override)) + 1
                        Segmented(listOf("Default", "Gentle", "Normal", "Hard"), selected, onSelect = {
                            onDifficulty(if (it == 0) FOLLOW_DEFAULT else Preset.entries[it - 1].code)
                        })
                        Text(
                            if (override < 0) "Follows Settings, currently ${defaultDifficulty.label}. " + difficultyLine(defaultDifficulty)
                            else difficultyLine(Preset.of(override)),
                            style = AppText.caption,
                            color = sky.dim,
                        )
                    }
                }
                ExpandableRow(
                    "Wake checks",
                    if (draft.wakeChecks == 0) "Off" else draft.wakeChecks.toString(),
                    open == "checks",
                    { toggle("checks") },
                ) {
                    Segmented(listOf("Off", "1", "2", "3"), draft.wakeChecks, onChecks)
                    Text(checksLine(draft.wakeChecks), style = AppText.caption, color = sky.dim)
                }
            }

            SoundRow(draft.soundUri, onSound)
            GentleRow(draft.gentleStart, onGentle)
            if (draft.repeatDays != Weekdays.NONE) SkipNextRow(draft, onSkipNext)

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel("Label", Modifier.padding(start = 4.dp))
                BasicTextField(
                    value = draft.label,
                    onValueChange = onLabel,
                    singleLine = true,
                    textStyle = AppText.body.copy(color = sky.ink),
                    cursorBrush = SolidColor(sky.sunInk),
                    decorationBox = { inner ->
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .clip(AppShapes.button)
                                .background(sky.card)
                                .border(1.dp, sky.cardEdge, AppShapes.button)
                                .padding(horizontal = 16.dp),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            if (draft.label.isEmpty()) Text("Weekdays, Gym…", style = AppText.body, color = sky.mute)
                            inner()
                        }
                    },
                )
            }

            // Hear it and try its puzzles before trusting it overnight. Nothing is saved or logged.
            QuietButton("Test this alarm", onTest, Modifier.fillMaxWidth().padding(top = 6.dp))

            if (!draft.isNew) {
                Text(
                    "Delete alarm",
                    style = AppText.prompt,
                    color = sky.mute,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .springClick(0.92f, onClick = onDelete)
                        .padding(14.dp),
                )
            }
        }
    }
}

@Composable
private fun Section(content: @Composable () -> Unit) {
    GlassCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { content() }
    }
}

/** Hour, minute and AM/PM wheels over one highlighted band. 24-hour phones get two wheels. */
@Composable
private fun TimeWheels(hour: Int, minute: Int, is24Hour: Boolean, onTime: (Int, Int) -> Unit) {
    val sky = LocalSky.current
    var h by remember { mutableIntStateOf(if (is24Hour) hour else (hour + 11) % 12) }
    var m by remember { mutableIntStateOf(minute) }
    var pm by remember { mutableIntStateOf(if (hour >= 12) 1 else 0) }
    fun report() {
        val h24 = if (is24Hour) h else ((h + 1) % 12) + 12 * pm
        onTime(h24, m)
    }

    Box(Modifier.padding(horizontal = 24.dp)) {
        Box(
            Modifier
                .padding(top = 88.dp)
                .fillMaxWidth()
                .height(44.dp)
                .clip(AppShapes.button)
                .background(sky.card)
                .border(1.dp, sky.cardEdge, AppShapes.button),
        )
        Row(Modifier.fillMaxWidth()) {
            val hours = if (is24Hour) (0..23).map { "%02d".format(it) } else (1..12).map { it.toString() }
            WheelColumn(hours, h, { h = it; report() }, "Hour", Modifier.weight(1f), wrap = true)
            WheelColumn((0..59).map { "%02d".format(it) }, m, { m = it; report() }, "Minute", Modifier.weight(1f), wrap = true)
            if (!is24Hour) WheelColumn(listOf("AM", "PM"), pm, { pm = it; report() }, "AM or PM", Modifier.weight(1f))
        }
    }
}

private fun repeatPhrase(days: Int): String = when (days) {
    Weekdays.NONE -> "once"
    Weekdays.WEEKDAYS -> "on weekdays"
    Weekdays.WEEKEND -> "on weekends"
    0b1111111 -> "every day"
    else -> "on " + DayOfWeek.entries.filter { Weekdays.has(days, it) }
        .joinToString(", ") { DAY_NAMES[it.value - 1].take(3) }
}

private fun methodName(method: WakeMethod): String = when (method) {
    WakeMethod.PUZZLES -> "Puzzles"
    WakeMethod.SCAN -> "Scan only"
    WakeMethod.PUZZLES_AND_SCAN -> "Puzzles + scan"
}

private val Preset.label: String get() = name.lowercase().replaceFirstChar { it.uppercase() }

/** A row that shows its current value and opens in place to change it. */
@Composable
private fun ExpandableRow(
    label: String,
    value: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    first: Boolean = false,
    content: @Composable () -> Unit,
) {
    val sky = LocalSky.current
    val turn by animateFloatAsState(if (expanded) 90f else 0f, label = "chevron")
    Column {
        if (!first) Box(Modifier.fillMaxWidth().height(1.dp).background(sky.cardEdge.copy(alpha = 0.5f)))
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(label, style = AppText.bodyStrong, color = sky.ink, modifier = Modifier.weight(1f))
            Text(value, style = AppText.caption, color = sky.dim)
            Text("›", style = AppText.header, color = sky.mute, modifier = Modifier.graphicsLayer { rotationZ = turn })
        }
        AnimatedVisibility(expanded) {
            Column(
                Modifier.padding(start = 18.dp, end = 18.dp, bottom = 18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) { content() }
        }
    }
}

private fun methodLine(method: WakeMethod, hasCode: Boolean): String = when (method) {
    WakeMethod.PUZZLES -> "Solve the puzzles to stop it."
    WakeMethod.SCAN ->
        if (hasCode) "No puzzles. Walk to your wake-up code and scan it." else "No puzzles: scan your wake-up code. You'll register it next."
    WakeMethod.PUZZLES_AND_SCAN ->
        if (hasCode) "The puzzles, then a last round: walk to your code and scan it." else "Puzzles, then a scan. You'll register your code next."
}

private fun difficultyLine(preset: Preset): String = when (preset) {
    Preset.GENTLE -> "3 rounds of the easiest puzzles. For mornings you wake up fine."
    Preset.NORMAL -> "5 rounds. Starts hard and eases off if you've been slow lately."
    Preset.HARD -> "4 rounds at the hardest level, every time."
}

private fun checksLine(n: Int): String = when (n) {
    0 -> "Off. Once you solve the puzzles, the alarm is done for the morning."
    1 -> "5 min after you solve, one “Still awake?” check. If your phone's in use it passes by itself. Miss it and the alarm rings again, from round 1."
    else -> "Starting 5 min after you solve: $n “Still awake?” checks, 5 min apart. Any you miss rings the alarm again, from round 1."
}

/**
 * The alarm's sound. Opens Android's own picker: every built-in tone, plus files on the phone.
 * Picking "Default" stores null, so the alarm follows the phone's default alarm sound.
 */
/**
 * Skip next: for a holiday, skip just the coming ring; the alarm stays on and is back the day
 * after. Shows which ring that is, from the time and days as currently set.
 */
@Composable
private fun SkipNextRow(draft: EditDraft, onChange: (Boolean) -> Unit) {
    val sky = LocalSky.current
    val is24Hour = DateFormat.is24HourFormat(LocalContext.current)
    val next = nextTrigger(draft.hour, draft.minute, draft.repeatDays, java.time.LocalDateTime.now())
    val day = next.format(java.time.format.DateTimeFormatter.ofPattern("EEE d MMM", java.util.Locale.ENGLISH))
    GlassCard(Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                SectionLabel("Skip the next one")
                Text("$day, ${formatTime(next.hour, next.minute, is24Hour)}", style = AppText.bodyStrong, color = sky.ink)
                Text(
                    if (draft.skipNext) "It stays on and rings again after that." else "For a holiday: skip just this one.",
                    style = AppText.caption,
                    color = sky.dim,
                )
            }
            SpringToggle(draft.skipNext, onChange, label = "Skip the next one")
        }
    }
}

/** Gentle start: fade in over 30 seconds, or start at full volume. */
@Composable
private fun GentleRow(on: Boolean, onChange: (Boolean) -> Unit) {
    val sky = LocalSky.current
    GlassCard(Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                SectionLabel("Gentle start")
                Text(
                    if (on) "Fades in over the first 30 seconds" else "Starts at full volume",
                    style = AppText.bodyStrong,
                    color = sky.ink,
                )
            }
            SpringToggle(on, onChange, label = "Gentle start")
        }
    }
}

@Composable
private fun SoundRow(soundUri: String?, onSound: (String?) -> Unit) {
    val sky = LocalSky.current
    val context = LocalContext.current
    val title = remember(soundUri) {
        if (soundUri == null) {
            "Phone's default alarm sound"
        } else {
            runCatching { RingtoneManager.getRingtone(context, Uri.parse(soundUri))?.getTitle(context) }
                .getOrNull() ?: "Custom sound"
        }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val picked: Uri? = if (Build.VERSION.SDK_INT >= 33) {
            result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
        }
        val isDefault = picked == null || picked == Settings.System.DEFAULT_ALARM_ALERT_URI
        onSound(if (isDefault) null else picked.toString())
    }

    GlassCard(
        Modifier
            .fillMaxWidth()
            .springClick(0.97f) {
                picker.launch(
                    Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
                        .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                        .putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Alarm sound")
                        .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                        .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                        .putExtra(RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI, Settings.System.DEFAULT_ALARM_ALERT_URI)
                        .putExtra(
                            RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,
                            soundUri?.let { Uri.parse(it) } ?: Settings.System.DEFAULT_ALARM_ALERT_URI,
                        ),
                )
            },
    ) {
        Row(
            Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                SectionLabel("Sound")
                Text(title, style = AppText.bodyStrong, color = sky.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text("Change", style = AppText.caption, color = sky.sunInk)
        }
    }
}
