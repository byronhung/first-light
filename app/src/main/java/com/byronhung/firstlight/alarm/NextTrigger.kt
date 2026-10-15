package com.byronhung.firstlight.alarm

import com.byronhung.firstlight.data.Alarm
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * Pure time logic, no Android: when does an alarm ring next, and how do we say so.
 * Chunk 03 schedules real alarms from [nextTrigger], so it's unit-tested (see NextTriggerTest).
 */

/** Repeat days as bits, Monday = bit 0 … Sunday = bit 6. */
object Weekdays {
    const val NONE = 0
    const val WEEKDAYS = 0b0011111
    const val WEEKEND = 0b1100000

    fun has(mask: Int, day: DayOfWeek): Boolean = mask and (1 shl (day.value - 1)) != 0
}

/**
 * The next moment strictly after [now] that the alarm should ring.
 * A one-off alarm (no repeat days) rings at the next occurrence of its time: today if that's
 * still ahead, otherwise tomorrow.
 */
fun nextTrigger(hour: Int, minute: Int, repeatDays: Int, now: LocalDateTime): LocalDateTime {
    val time = LocalTime.of(hour, minute)
    // 0..7, not 0..6: a Monday-only alarm checked on Monday after its time rings next Monday.
    for (offset in 0L..7L) {
        val date = now.toLocalDate().plusDays(offset)
        val candidate = date.atTime(time)
        if (!candidate.isAfter(now)) continue
        if (repeatDays == Weekdays.NONE || Weekdays.has(repeatDays, date.dayOfWeek)) return candidate
    }
    error("No trigger within 8 days for repeatDays=$repeatDays")
}

/**
 * The next ring, jumping over [skip] (skip next) when that's the one coming up. A skip that has
 * passed, or that no longer lines up with the alarm's days, simply matches nothing.
 */
fun nextTrigger(hour: Int, minute: Int, repeatDays: Int, now: LocalDateTime, skip: LocalDateTime?): LocalDateTime {
    val next = nextTrigger(hour, minute, repeatDays, now)
    return if (skip != null && next == skip && repeatDays != Weekdays.NONE) nextTrigger(hour, minute, repeatDays, next) else next
}

/** The next ring this alarm would have, skip or not: what "skip next" skips. */
fun Alarm.plainNextTrigger(now: LocalDateTime): LocalDateTime = nextTrigger(hour, minute, repeatDays, now)

fun Alarm.skipTime(): LocalDateTime? =
    if (skipAt <= 0) null else LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(skipAt), java.time.ZoneId.systemDefault())

fun Alarm.nextTrigger(now: LocalDateTime): LocalDateTime = nextTrigger(hour, minute, repeatDays, now, skipTime())

/** The upcoming ring is being skipped. */
fun Alarm.isSkippingNext(now: LocalDateTime): Boolean = skipTime()?.let { it == plainNextTrigger(now) && repeatDays != Weekdays.NONE } == true

/** "06:30" on a 24-hour phone, "6:30 AM" on a 12-hour one. */
fun formatTime(hour: Int, minute: Int, is24Hour: Boolean = true): String =
    if (is24Hour) "%02d:%02d".format(hour, minute) else "${clockDigits(hour, minute, false)} ${period(hour)}"

/** The digits alone: "06:30", or "6:30" on a 12-hour phone (where 0:00 is 12:00). */
fun clockDigits(hour: Int, minute: Int, is24Hour: Boolean): String =
    if (is24Hour) "%02d:%02d".format(hour, minute) else "%d:%02d".format((hour + 11) % 12 + 1, minute)

fun period(hour: Int): String = if (hour < 12) "AM" else "PM"

/** "24 s", "1 m 12 s", "1 h 3 m". */
fun formatDuration(ms: Long): String {
    val seconds = ms / 1000
    return when {
        seconds >= 3600 -> "${seconds / 3600} h ${(seconds % 3600) / 60} m"
        seconds >= 60 -> "${seconds / 60} m ${seconds % 60} s"
        else -> "$seconds s"
    }
}

/** "Next: tomorrow at 06:30 · in 7 h 49 m", or null if every alarm is off. */
fun nextAlarmSummary(alarms: List<Alarm>, now: LocalDateTime, is24Hour: Boolean = true): String? {
    val next = alarms.filter { it.enabled }.minOfOrNull { it.nextTrigger(now) } ?: return null
    return describeNext(next, now, is24Hour)
}

fun describeNext(next: LocalDateTime, now: LocalDateTime, is24Hour: Boolean = true): String {
    val (day, until) = dayAndCountdown(next, now)
    return "Next: $day at ${formatTime(next.hour, next.minute, is24Hour)} · in $until"
}

/** The list's hero: the next alarm as big digits, AM/PM, and "tomorrow · in 8 h 30 m" ("in 42 s" in the last minute). */
data class NextAlarm(val digits: String, val period: String?, val line: String)

fun nextAlarm(alarms: List<Alarm>, now: LocalDateTime, is24Hour: Boolean): NextAlarm? {
    val next = alarms.filter { it.enabled }.minOfOrNull { it.nextTrigger(now) } ?: return null
    val (day, until) = dayAndCountdown(next, now)
    return NextAlarm(
        digits = clockDigits(next.hour, next.minute, is24Hour),
        period = if (is24Hour) null else period(next.hour),
        line = "$day · in $until",
    )
}

private fun dayAndCountdown(next: LocalDateTime, now: LocalDateTime): Pair<String, String> {
    val day = when (ChronoUnit.DAYS.between(now.toLocalDate(), next.toLocalDate())) {
        0L -> "today"
        1L -> "tomorrow"
        else -> next.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
    }
    // Round up, so the count never reads 0 before it rings.
    val seconds = ((Duration.between(now, next).toMillis() + 999) / 1000).coerceAtLeast(1)
    val minutes = (seconds + 59) / 60
    // Hours and days count whole minutes left, so 23 h 59 m 30 s reads "23 h 59 m", not "1 d 0 h".
    val whole = seconds / 60
    val until = when {
        // The last minute counts down in seconds; any earlier, seconds are just noise.
        seconds < 60 -> "$seconds s"
        whole >= 24 * 60 -> "${whole / (24 * 60)} d ${(whole % (24 * 60)) / 60} h"
        whole >= 60 -> "${whole / 60} h ${whole % 60} m"
        else -> "$minutes m"
    }
    return day to until
}
