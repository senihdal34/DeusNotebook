package com.ethran.notable.reading

import com.ethran.notable.R
import java.time.LocalDate
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToInt

enum class BuddyPose { Wait, Sleep, Ribbon, Party, Yawn, Read }

/** [phase] runs 0..1 from one new moon to the next; 0.5 is full. */
data class MoonPhase(val phase: Double, val litPercent: Int, val name: String, val daysToNext: Int, val next: String)

/** Hand angles in degrees clockwise from 12 o'clock. */
data class ClockAngles(val hour: Float, val minute: Float)

/** The decor boxes' logic: the buddy's mood, the moon and the clock hands. Pure, so it is unit tested. */
object ReadingDecor {
    private const val SYNODIC_DAYS = 29.530588853
    /** Julian day of a known new moon (2000-01-06 18:14 UTC). */
    private const val NEW_MOON_JD = 2451550.1
    private const val UNIX_EPOCH_JD = 2440587.5
    private val MILESTONES = setOf(7, 30, 50, 100, 200, 365)

    /** One mood per situation, as the board's cat shows it: nothing started, not read today, book just finished, streak milestone, late night, or reading along. */
    fun buddyPose(
        hasBookInProgress: Boolean, streak: Int, longestStreak: Int, todayMinutes: Int, finishedToday: Boolean, hour: Int
    ): BuddyPose = when {
        !hasBookInProgress && streak == 0 -> BuddyPose.Wait
        todayMinutes <= 0 -> BuddyPose.Sleep
        finishedToday -> BuddyPose.Ribbon
        streak in MILESTONES || (streak > 1 && streak >= longestStreak) -> BuddyPose.Party
        hour >= 22 || hour < 5 -> BuddyPose.Yawn
        else -> BuddyPose.Read
    }

    /** What the buddy says: a headline and a second line. */
    fun buddyLines(pose: BuddyPose, streak: Int, longestStreak: Int, todayMinutes: Int): Pair<String, String> = when (pose) {
        BuddyPose.Read -> RS.get(R.string.reading_buddy_read_title) to RS.get(R.string.reading_buddy_read_sub, ReadingText.minutes(todayMinutes))
        BuddyPose.Sleep -> RS.get(R.string.reading_buddy_sleep_title) to RS.get(R.string.reading_buddy_sleep_sub)
        BuddyPose.Party -> RS.plural(R.plurals.reading_buddy_streak, streak, streak) to
            (if (streak >= longestStreak) RS.get(R.string.reading_buddy_new_best) else RS.get(R.string.reading_buddy_your_best, longestStreak))
        BuddyPose.Ribbon -> RS.get(R.string.reading_buddy_ribbon_title) to RS.get(R.string.reading_buddy_ribbon_sub)
        BuddyPose.Yawn -> RS.get(R.string.reading_buddy_yawn_title) to RS.get(R.string.reading_buddy_yawn_sub, ReadingText.minutes(todayMinutes))
        BuddyPose.Wait -> RS.get(R.string.reading_buddy_wait_title) to RS.get(R.string.reading_buddy_wait_sub)
    }

    const val SPRITE_COLS = 14
    const val SPRITE_ROWS = 16

    // The cat sitting up, 13 rows below three free rows for a hat or a sleeping "z".
    private val CAT = listOf(
        "..##......##..",
        "..###....###..",
        "..##########..",
        ".############.",
        ".############.",
        ".############.",
        ".############.",
        "..##########..",
        "..##########..",
        ".############.",
        ".############.",
        ".####....####.",
        "..##......##.."
    )
    private const val TOP_ROWS = 3

    /** Cells (column to row) that change in each pose: [white] are holes in the cat, [black] are drawn on the empty rows. */
    private class Edit(val white: List<Pair<Int, Int>> = emptyList(), val black: List<Pair<Int, Int>> = emptyList())

    private val EYES_OPEN = listOf(4 to 7, 9 to 7)
    private val EYES_SHUT = listOf(3 to 8, 4 to 8, 9 to 8, 10 to 8)
    private val EDITS = mapOf(
        BuddyPose.Read to Edit(white = EYES_OPEN + listOf(6 to 9, 7 to 9)),
        BuddyPose.Wait to Edit(white = listOf(5 to 7, 10 to 7)),
        BuddyPose.Sleep to Edit(
            white = EYES_SHUT,
            black = listOf(11 to 0, 12 to 0, 13 to 0, 12 to 1, 11 to 2, 12 to 2, 13 to 2)
        ),
        BuddyPose.Party to Edit(
            white = EYES_OPEN + listOf(6 to 9, 7 to 9),
            black = listOf(6 to 2, 7 to 2, 5 to 3, 6 to 3, 7 to 3, 8 to 3, 5 to 4, 6 to 4, 7 to 4, 8 to 4)
        ),
        BuddyPose.Yawn to Edit(white = EYES_SHUT + listOf(6 to 9, 7 to 9, 6 to 10, 7 to 10)),
        BuddyPose.Ribbon to Edit(white = EYES_OPEN + listOf(6 to 9, 7 to 9, 4 to 12, 5 to 12, 8 to 12, 9 to 12, 5 to 13, 8 to 13))
    )

    /** The buddy as [SPRITE_ROWS] rows of [SPRITE_COLS] characters: '#' ink, 'w' white (an eye or the bow), '.' empty. */
    fun buddySprite(pose: BuddyPose): List<String> {
        val grid = List(SPRITE_ROWS) { CharArray(SPRITE_COLS) { '.' } }
        CAT.forEachIndexed { r, line -> line.forEachIndexed { c, ch -> grid[TOP_ROWS + r][c] = ch } }
        val edit = EDITS.getValue(pose)
        edit.white.forEach { (c, r) -> grid[r][c] = 'w' }
        edit.black.forEach { (c, r) -> grid[r][c] = '#' }
        return grid.map { String(it) }
    }

    /** Moon phase of [date] at noon UTC from the mean synodic month; good to about half a day. */
    fun moon(date: LocalDate): MoonPhase {
        val jd = date.toEpochDay() + UNIX_EPOCH_JD + 0.5
        val age = ((jd - NEW_MOON_JD) % SYNODIC_DAYS + SYNODIC_DAYS) % SYNODIC_DAYS
        val phase = age / SYNODIC_DAYS
        val lit = ((1 - cos(2 * PI * phase)) / 2 * 100).roundToInt()
        val name = when {
            phase < 0.03 || phase >= 0.97 -> RS.get(R.string.reading_moon_new)
            phase < 0.22 -> RS.get(R.string.reading_moon_waxing_crescent)
            phase < 0.28 -> RS.get(R.string.reading_moon_first_quarter)
            phase < 0.47 -> RS.get(R.string.reading_moon_waxing_gibbous)
            phase < 0.53 -> RS.get(R.string.reading_moon_full)
            phase < 0.72 -> RS.get(R.string.reading_moon_waning_gibbous)
            phase < 0.78 -> RS.get(R.string.reading_moon_last_quarter)
            else -> RS.get(R.string.reading_moon_waning_crescent)
        }
        // Next full moon while waxing, next new moon while waning.
        val (target, label) = if (phase < 0.5) 0.5 to RS.get(R.string.reading_moon_full) else 1.0 to RS.get(R.string.reading_moon_new)
        val days = floor((target - phase) * SYNODIC_DAYS + 0.999).toInt().coerceAtLeast(0)
        return MoonPhase(phase, lit, name, days, if (days == 0) RS.get(R.string.reading_moon_today, label) else RS.plural(R.plurals.reading_moon_in_days, days, label, days))
    }

    /** Hour hand moves with the minutes; both are measured clockwise from 12. */
    fun clockAngles(hour: Int, minute: Int): ClockAngles =
        ClockAngles((hour % 12) * 30f + minute * 0.5f, minute * 6f)

    /** True when the lit part of the moon is on the right (waxing, as seen from the northern hemisphere). */
    fun litOnRight(m: MoonPhase): Boolean = m.phase < 0.5

    /** Horizontal radius of the moon's terminator as a fraction of the moon's radius: 1 at new and full, 0 at the quarters. */
    fun terminator(m: MoonPhase): Double = abs(cos(2 * PI * m.phase))
}
