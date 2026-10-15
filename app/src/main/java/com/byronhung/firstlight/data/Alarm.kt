package com.byronhung.firstlight.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter

/** [Alarm.difficulty] value meaning "use the default from Settings". */
const val FOLLOW_DEFAULT = -1

/** One saved alarm: one row in the `alarms` table. */
@Entity(tableName = "alarms")
data class Alarm(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val hour: Int,
    val minute: Int,
    /** Which weekdays it repeats on, as bits: Monday = bit 0 … Sunday = bit 6. 0 = rings once. */
    val repeatDays: Int = 0,
    val label: String = "",
    val enabled: Boolean = true,
    /** The puzzle types this alarm draws its morning from. */
    val roundTypes: List<RoundType> = RoundType.MORNING_DEFAULT,
    /** Unused since v0.2: the code is shared now, see [AppSettings.wakeCode]. Kept to avoid a migration. */
    val qrCode: String? = null,
    /**
     * "Still awake?" checks after solving, 0–3. Like snooze, but the other way round. Off by
     * default since v0.7: something you opt into, not a surprise ring after you've solved it.
     */
    @ColumnInfo(defaultValue = "0") val wakeChecks: Int = 0,
    /** Unused since v5: replaced by [wakeMethod] (the migration carried its value over). */
    @ColumnInfo(defaultValue = "0") val finishWithScan: Boolean = false,
    /**
     * Difficulty override, as [com.byronhung.firstlight.puzzle.Preset.code]: 0 Gentle, 1 Normal, 2 Hard.
     * -1 (the default for new alarms) = follow the default difficulty in Settings.
     */
    @ColumnInfo(defaultValue = "1") val difficulty: Int = FOLLOW_DEFAULT,
    /** How it stops, as [com.byronhung.firstlight.puzzle.WakeMethod.code]: 0 Puzzles, 1 Scan, 2 Puzzles + scan. */
    @ColumnInfo(defaultValue = "0") val wakeMethod: Int = 0,
    /** The sound picked for this alarm (a content URI). Null = the phone's default alarm sound. */
    val soundUri: String? = null,
    /** Fade in over the first 30 s instead of starting at full volume. */
    @ColumnInfo(defaultValue = "1") val gentleStart: Boolean = true,
    /**
     * Skip next: the one ring to skip, as epoch millis; 0 = none. Once that time has passed it
     * matches nothing, so it never needs clearing.
     */
    @ColumnInfo(defaultValue = "0") val skipAt: Long = 0,
)

enum class RoundType {
    STROOP,
    PATTERN_FLASH,
    ODD_ONE_OUT,
    QR_SCAN,

    // Puzzle pack 1 (Plus).
    PAIRS,
    PATH,
    SLIDE;

    companion object {
        val MORNING_DEFAULT = listOf(STROOP, PATTERN_FLASH, ODD_ONE_OUT)
    }
}

/** Room stores simple columns only, so the round list is saved as "STROOP,PATTERN_FLASH,…". */
class Converters {
    @TypeConverter
    fun roundTypesToString(types: List<RoundType>): String = types.joinToString(",") { it.name }

    @TypeConverter
    fun stringToRoundTypes(value: String): List<RoundType> =
        if (value.isBlank()) emptyList() else value.split(",").map { RoundType.valueOf(it) }
}
