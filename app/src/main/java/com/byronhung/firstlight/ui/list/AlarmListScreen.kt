package com.byronhung.firstlight.ui.list

import androidx.activity.compose.BackHandler
import com.byronhung.firstlight.ui.plus.PlusNudgeCard
import com.byronhung.firstlight.ui.components.PlusSheet
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.byronhung.firstlight.ui.components.AmberButton
import com.byronhung.firstlight.ui.theme.AppShapes
import com.byronhung.firstlight.ui.theme.Sun
import kotlinx.coroutines.delay
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.byronhung.firstlight.alarm.NextAlarm
import com.byronhung.firstlight.alarm.Weekdays
import com.byronhung.firstlight.puzzle.Preset
import com.byronhung.firstlight.puzzle.WakeMethod
import com.byronhung.firstlight.ui.components.Chip
import com.byronhung.firstlight.ui.components.GlassCard
import com.byronhung.firstlight.ui.components.IconSquareButton
import com.byronhung.firstlight.ui.components.LogoMark
import com.byronhung.firstlight.ui.components.SectionLabel
import com.byronhung.firstlight.ui.components.SkyBackground
import com.byronhung.firstlight.ui.components.SpringToggle
import com.byronhung.firstlight.ui.components.SunFab
import com.byronhung.firstlight.ui.components.SunIcons
import com.byronhung.firstlight.ui.components.springClick
import com.byronhung.firstlight.ui.theme.FirstLightTheme
import com.byronhung.firstlight.ui.theme.AppText
import com.byronhung.firstlight.ui.theme.LocalSky
import com.byronhung.firstlight.ui.theme.Motion
import com.byronhung.firstlight.ui.theme.LocalSkyTheme
import com.byronhung.firstlight.ui.theme.skyAt
import com.byronhung.firstlight.ui.theme.Space
import java.time.DayOfWeek
import java.time.LocalTime

@Composable
fun AlarmListScreen(
    viewModel: AlarmListViewModel,
    onAdd: () -> Unit,
    onEdit: (Long) -> Unit,
    onHistory: () -> Unit,
    onSettings: () -> Unit,
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val deleted by viewModel.deleted.collectAsStateWithLifecycle()
    val nudge by viewModel.nudge.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.checkNudge() }
    var plusReason by remember { mutableStateOf<String?>(null) }
    // Switching an alarm on needs notifications first; switching one off never does.
    val whenNotificationsAllowed = rememberNotificationGate("switch the alarm on again")
    AlarmListContent(
        ui, onAdd, onEdit,
        { id, on -> if (on) whenNotificationsAllowed { viewModel.setEnabled(id, true) } else viewModel.setEnabled(id, false) },
        onHistory, onSettings,
        deletedCount = deleted.size,
        onDelete = viewModel::delete,
        onUndo = viewModel::undoDelete,
        onUndoGone = viewModel::dismissUndo,
        overlay = {
            PlusNudgeCard(
                visible = nudge,
                onSeePlus = {
                    viewModel.nudgeSeePlus()
                    plusReason = "Three more puzzles and four new skies."
                },
                onNotNow = viewModel::nudgeNotNow,
            )
            PlusSheet(reason = plusReason, onDismiss = { plusReason = null })
        },
    )
}

@Composable
private fun AlarmListContent(
    ui: AlarmListUi?,
    onAdd: () -> Unit,
    onEdit: (Long) -> Unit,
    onToggle: (Long, Boolean) -> Unit,
    onHistory: () -> Unit,
    onSettings: () -> Unit,
    deletedCount: Int = 0,
    onDelete: (Set<Long>) -> Unit = {},
    onUndo: () -> Unit = {},
    onUndoGone: () -> Unit = {},
    overlay: @Composable BoxScope.() -> Unit = {},
) {
    // Long-press a card to start picking; tap more to add them; delete them in one go.
    var picked by remember { mutableStateOf(emptySet<Long>()) }
    val picking = picked.isNotEmpty()
    // An alarm deleted elsewhere (its own edit screen) drops out of the selection.
    val rowIds = ui?.rows.orEmpty().map { it.id }.toSet()
    if (ui != null && picked.any { it !in rowIds }) picked = picked intersect rowIds
    BackHandler(enabled = picking) { picked = emptySet() }
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(deletedCount) {
        if (deletedCount > 0) {
            delay(5_000)
            onUndoGone()
        }
    }

    SkyBackground(skyAt(ui?.hour ?: LocalTime.now().hour)) {
        val sky = LocalSky.current
        LazyColumn(
            modifier = Modifier.safeDrawingPadding(),
            contentPadding = PaddingValues(start = Space.screen, end = Space.screen, top = 28.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(
                    Modifier.padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (picking) {
                        IconSquareButton(SunIcons.Back, "Cancel", { picked = emptySet() })
                        Text("${picked.size} selected", style = AppText.header, color = sky.ink, modifier = Modifier.weight(1f))
                        Text(
                            "All",
                            style = AppText.bodyStrong,
                            color = sky.sunInk,
                            modifier = Modifier
                                .springClick(0.92f) { picked = rowIds }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                        )
                    } else {
                        LogoMark(sky.ink)
                        Text("First Light", style = AppText.header, color = sky.ink, modifier = Modifier.weight(1f))
                        IconSquareButton(SunIcons.History, "History", onHistory)
                        IconSquareButton(SunIcons.Sliders, "Settings", onSettings)
                    }
                }
            }
            item { Hero(ui) }
            item { SetupCard() }
            itemsIndexed(ui?.rows.orEmpty(), key = { _, row -> row.id }) { i, row ->
                val flip = { picked = if (row.id in picked) picked - row.id else picked + row.id }
                AlarmCard(
                    row,
                    index = i,
                    picking = picking,
                    isPicked = row.id in picked,
                    onClick = { if (picking) flip() else onEdit(row.id) },
                    onLongClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        flip()
                    },
                    onToggle = { onToggle(row.id, it) },
                )
            }
            if (ui != null && ui.rows.isEmpty()) {
                item {
                    Text(
                        "No alarms yet. Tap + to add one.",
                        style = AppText.body,
                        color = sky.dim,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
            }
        }
        if (picking) {
            AmberButton(
                "Delete ${picked.size}",
                onClick = {
                    onDelete(picked)
                    picked = emptySet()
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .safeDrawingPadding()
                    .padding(start = 24.dp, end = 24.dp, bottom = 30.dp)
                    .fillMaxWidth(),
                height = 58.dp,
            )
        } else {
            if (deletedCount > 0) {
                UndoBar(
                    if (deletedCount == 1) "Alarm deleted" else "$deletedCount alarms deleted",
                    onUndo,
                    Modifier
                        .align(Alignment.BottomStart)
                        .safeDrawingPadding()
                        .padding(start = 24.dp, end = 112.dp, bottom = 36.dp),
                )
            }
            SunFab(
                onClick = onAdd,
                label = "Add alarm",
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .safeDrawingPadding()
                    .padding(end = 24.dp, bottom = 30.dp),
            )
        }
        overlay()
    }
}

/** "3 alarms deleted · Undo", for five seconds after a delete. */
@Composable
private fun UndoBar(text: String, onUndo: () -> Unit, modifier: Modifier = Modifier) {
    val sky = LocalSky.current
    GlassCard(modifier, shape = AppShapes.button) {
        Row(
            Modifier.padding(start = 18.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text, style = AppText.body, color = sky.ink, modifier = Modifier.weight(1f))
            Text(
                "Undo",
                style = AppText.bodyStrong,
                color = sky.sunInk,
                modifier = Modifier
                    .springClick(0.92f, onClick = onUndo)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            )
        }
    }
}

@Composable
private fun Hero(ui: AlarmListUi?) {
    val sky = LocalSky.current
    Column(
        Modifier.padding(start = 12.dp, end = 12.dp, top = 28.dp, bottom = 18.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        SectionLabel("Next alarm")
        when {
            ui == null -> Spacer(Modifier.padding(bottom = 104.dp))
            ui.next != null -> NextClock(ui.next)
            else -> {
                Text("All quiet", style = AppText.display.copy(fontSize = 56.sp), color = sky.ink)
                Text("No alarms on. Sleep in.", style = AppText.body, color = sky.dim)
            }
        }
    }
}

@Composable
private fun NextClock(next: NextAlarm) {
    val sky = LocalSky.current
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(next.digits, style = AppText.heroClock, color = sky.ink)
        if (next.period != null) {
            Text(next.period, style = AppText.period.copy(fontSize = 22.sp), color = sky.dim, modifier = Modifier.padding(bottom = 12.dp))
        }
    }
    Text(next.line, style = AppText.body, color = sky.dim)
}

private val DAY_LETTERS = listOf("M", "T", "W", "T", "F", "S", "S")

/** Glass card: rises in on first show, staggered; fades when switched off. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AlarmCard(
    row: AlarmRowUi,
    index: Int,
    picking: Boolean,
    isPicked: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onToggle: (Boolean) -> Unit,
) {
    val sky = LocalSky.current
    val rise = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(index * 90L)
        rise.animateTo(1f, Motion.settle())
    }
    val dim by animateFloatAsState(if (row.enabled) 1f else 0.55f, tween(350), label = "off")

    GlassCard(
        Modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = rise.value.coerceIn(0f, 1f) * (if (picking) 1f else dim)
                translationY = (1f - rise.value) * 16.dp.toPx()
            }
            .border(2.dp, if (isPicked) Sun.ToggleLight else Color.Transparent, AppShapes.card),
    ) {
        Row(
            Modifier.padding(start = 20.dp, end = 18.dp, top = 18.dp, bottom = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(
                Modifier
                    .weight(1f)
                    .springClick(0.985f, onLongClick = onLongClick, onClick = onClick),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(row.time, style = AppText.alarmTime, color = sky.ink)
                    if (row.period != null) {
                        Text(row.period, style = AppText.period, color = sky.dim, modifier = Modifier.padding(start = 6.dp, bottom = 3.dp))
                    }
                    if (row.label.isNotBlank()) {
                        Text(
                            row.label,
                            style = AppText.caption,
                            color = sky.dim,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(start = 12.dp, bottom = 3.dp),
                        )
                    }
                }
                // Days and tags flow: when an alarm has several tags they drop to a second line
                // instead of squeezing a tag until its text breaks.
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        buildAnnotatedString {
                            if (row.repeatDays == Weekdays.NONE) {
                                append("ONCE")
                            } else {
                                DayOfWeek.entries.forEachIndexed { i, day ->
                                    withStyle(SpanStyle(color = if (Weekdays.has(row.repeatDays, day)) sky.sunInk else sky.mute)) {
                                        append(DAY_LETTERS[i])
                                    }
                                    if (i < 6) append("  ")
                                }
                            }
                        },
                        style = AppText.day,
                        color = sky.mute,
                        modifier = Modifier.align(Alignment.CenterVertically),
                    )
                    if (row.skipping != null) Chip(row.skipping)
                    if (row.wakeChecks > 0) Chip(if (row.wakeChecks == 1) "1 check" else "${row.wakeChecks} checks", SunIcons.Check)
                    when (WakeMethod.of(row.wakeMethod)) {
                        WakeMethod.SCAN -> Chip("Scan only", SunIcons.Scan)
                        WakeMethod.PUZZLES_AND_SCAN -> Chip("Scan", SunIcons.Scan)
                        WakeMethod.PUZZLES -> Unit
                    }
                    // Only an alarm that overrides the default difficulty gets a tag.
                    if (row.difficulty >= 0 && WakeMethod.of(row.wakeMethod) != WakeMethod.SCAN) when (Preset.of(row.difficulty)) {
                        Preset.GENTLE -> Chip("Gentle")
                        Preset.HARD -> Chip("Hard")
                        Preset.NORMAL -> Unit
                    }
                }
            }
            if (picking) PickMark(isPicked, onClick)
            else SpringToggle(row.enabled, onToggle, label = "${row.time} ${row.period.orEmpty()} on")
        }
    }
}

/** Where the toggle was, while picking: an amber tick when picked, an empty ring when not. */
@Composable
private fun PickMark(picked: Boolean, onClick: () -> Unit) {
    val sky = LocalSky.current
    Box(
        Modifier
            .springClick(0.88f, role = Role.Checkbox, onClick = onClick)
            .size(30.dp)
            .clip(CircleShape)
            .then(if (picked) Modifier.background(Sun.amberBrush) else Modifier.border(2.dp, sky.track, CircleShape))
            .semantics { contentDescription = if (picked) "Selected" else "Not selected" },
        contentAlignment = Alignment.Center,
    ) {
        if (picked) Icon(SunIcons.Check, contentDescription = null, tint = Sun.OnAmber, modifier = Modifier.size(16.dp))
    }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun AlarmListPreview() = FirstLightTheme {
    AlarmListContent(
        ui = AlarmListUi(
            rows = listOf(
                AlarmRowUi(1, "6:30", "AM", Weekdays.WEEKDAYS, "Weekdays", true, 1, 2),
                AlarmRowUi(2, "8:15", "AM", Weekdays.WEEKEND, "Weekend", true, 0, 1),
                AlarmRowUi(3, "5:45", "AM", 0b1010, "Gym", false, 1, 0),
            ),
            next = NextAlarm("6:30", "AM", "tomorrow · in 8 h 30 m"),
            hour = 22,
        ),
        onAdd = {}, onEdit = {}, onToggle = { _, _ -> }, onHistory = {}, onSettings = {},
    )
}
