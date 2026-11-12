package com.ethran.notable.activity

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.Month
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/** The stretch of time the Writing activity card shows. */
sealed interface ActivityPeriod {
    /** What is saved in the preferences. */
    val key: String

    /** The last [count] months, ending today. */
    data class Months(val count: Int) : ActivityPeriod {
        override val key: String get() = "m$count"
    }

    /** One calendar year: January 1 to December 31, or to today for the current year. */
    data class Year(val year: Int) : ActivityPeriod {
        override val key: String get() = "y$year"
    }

    companion object {
        val DEFAULT: ActivityPeriod = Months(12)
        val MONTH_CHOICES: List<ActivityPeriod> = listOf(Months(3), Months(6), Months(12))

        fun parse(key: String?): ActivityPeriod? = when {
            key == null -> null
            key.startsWith("m") -> key.drop(1).toIntOrNull()?.let { n -> MONTH_CHOICES.firstOrNull { it == Months(n) } }
            key.startsWith("y") -> key.drop(1).toIntOrNull()?.takeIf { it in 1970..9999 }?.let(::Year)
            else -> null
        }
    }
}

/** First day of a month that gets a label above the heatmap, and the column it sits in. */
data class MonthMark(val column: Int, val month: Month)

/**
 * The heatmap: weeks across (Monday first), seven rows. [levels] is column-major (`column * 7 + row`, row 0 is
 * Monday): 0..4 for days in the period, [WritingActivityStats.OUTSIDE] for the cells before the first or after the last day.
 */
data class WeekGrid(
    val columns: Int,
    val levels: List<Int>,
    /** Index into [levels] of today, or -1 when today is not in the period. */
    val todayCell: Int,
    val months: List<MonthMark>
)

/** Everything the card shows for one period. */
data class ActivityView(
    val range: WritingActivityStats.DateRange,
    val grid: WeekGrid,
    val daysWithInk: Int,
    /** Null when the period does not reach today (a past year): a "current" streak has no meaning there. */
    val currentStreak: Int?,
    val longestStreak: Int,
    val daysPerWeek: Double
) {
    val isEmpty: Boolean get() = daysWithInk == 0
}

/**
 * The maths of the writing activity card. Pure: no Android, no clock, no database. Counts come in as strokes per day.
 * Days are [LocalDate]s, so DST and leap years need no special care; the database only groups strokes by local day.
 */
object WritingActivityStats {
    /** A day counts as written from this many strokes on (a stray mark does not make a streak). */
    const val MIN_STROKES = 5
    const val MAX_YEAR_CHIPS = 3
    const val OUTSIDE = -1
    const val LEVELS = 4

    data class DateRange(val start: LocalDate, val end: LocalDate) {
        val days: Int get() = ChronoUnit.DAYS.between(start, end).toInt() + 1
        operator fun contains(day: LocalDate) = !day.isBefore(start) && !day.isAfter(end)
    }

    fun isWritten(strokes: Int): Boolean = strokes >= MIN_STROKES

    fun rangeOf(period: ActivityPeriod, today: LocalDate): DateRange = when (period) {
        is ActivityPeriod.Months -> DateRange(today.minusMonths(period.count.toLong()).plusDays(1), today)
        is ActivityPeriod.Year -> {
            val last = LocalDate.of(period.year, 12, 31)
            DateRange(LocalDate.of(period.year, 1, 1), if (last.isAfter(today)) today else last)
        }
    }

    /** Parses the "yyyy-MM-dd" keys; keys that are not dates and non-positive counts are dropped. */
    fun parseDays(raw: Map<String, Int>): Map<LocalDate, Int> {
        val out = HashMap<LocalDate, Int>(raw.size * 2)
        for ((key, n) in raw) {
            if (n <= 0) continue
            val day = runCatching { LocalDate.parse(key) }.getOrNull() ?: continue
            out[day] = n
        }
        return out
    }

    /** Max per day: deleting notes or erasing never shrinks what was written. */
    fun mergeMax(saved: Map<String, Int>, fresh: Map<String, Int>): Map<String, Int> {
        val out = HashMap<String, Int>(saved)
        for ((day, n) in fresh) if (n > (out[day] ?: 0)) out[day] = n
        return out
    }

    /** Calendar years (newest first, at most [MAX_YEAR_CHIPS]) that have a written day. Years after today are ignored. */
    fun yearsWithData(counts: Map<LocalDate, Int>, today: LocalDate): List<Int> =
        counts.filter { (day, n) -> isWritten(n) && day.year <= today.year }.keys
            .map { it.year }.distinct().sortedDescending().take(MAX_YEAR_CHIPS)

    /**
     * Upper bounds of levels 1..3, the 25th, 50th and 75th percentile of the written days (lower nearest rank), or
     * null when there is nothing written. A single big import day pushes only itself to the top level.
     */
    fun thresholds(written: List<Int>): IntArray? {
        if (written.isEmpty()) return null
        val sorted = written.sorted()
        fun at(p: Double) = sorted[((sorted.size - 1) * p).toInt()]
        return intArrayOf(at(0.25), at(0.50), at(0.75))
    }

    /** 0 for an unwritten day, else 1..4 by [thresholds]. Days that are all alike get the middle-dark level 3. */
    fun level(strokes: Int, thresholds: IntArray?, allEqual: Boolean = false): Int {
        if (!isWritten(strokes) || thresholds == null) return 0
        if (allEqual) return 3
        var level = 1
        for (t in thresholds) if (strokes > t) level++
        return level
    }

    /** Written days ending at the anchor day: today, or yesterday while today has no ink yet. Null for a period that does not reach today. */
    fun currentStreak(counts: Map<LocalDate, Int>, range: DateRange, today: LocalDate): Int? {
        if (range.end != today) return null
        var day = if (isWritten(counts[today] ?: 0)) today else today.minusDays(1)
        var n = 0
        while (!day.isBefore(range.start) && isWritten(counts[day] ?: 0)) {
            n++
            day = day.minusDays(1)
        }
        return n
    }

    fun longestStreak(counts: Map<LocalDate, Int>, range: DateRange): Int {
        var best = 0
        var run = 0
        var day = range.start
        while (!day.isAfter(range.end)) {
            if (isWritten(counts[day] ?: 0)) { run++; if (run > best) best = run } else run = 0
            day = day.plusDays(1)
        }
        return best
    }

    /** Written days per seven days of the period, one decimal. */
    fun daysPerWeek(writtenDays: Int, range: DateRange): Double =
        Math.round(writtenDays * 7.0 / range.days * 10) / 10.0

    fun mondayOf(day: LocalDate): LocalDate = day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    /** Labels need about three columns of room; when two neighbours would touch, the earlier one is dropped. */
    private const val MIN_LABEL_GAP = 3

    fun grid(counts: Map<LocalDate, Int>, range: DateRange, today: LocalDate): WeekGrid {
        val firstMonday = mondayOf(range.start)
        val columns = (ChronoUnit.DAYS.between(firstMonday, mondayOf(range.end)) / 7).toInt() + 1
        val written = ArrayList<Int>()
        var day = range.start
        while (!day.isAfter(range.end)) {
            counts[day]?.takeIf(::isWritten)?.let(written::add)
            day = day.plusDays(1)
        }
        val thresholds = thresholds(written)
        val allEqual = written.isNotEmpty() && written.min() == written.max()

        val levels = MutableList(columns * 7) { OUTSIDE }
        val marks = ArrayList<MonthMark>()
        var todayCell = -1
        day = range.start
        while (!day.isAfter(range.end)) {
            val column = (ChronoUnit.DAYS.between(firstMonday, day) / 7).toInt()
            val cell = column * 7 + (day.dayOfWeek.value - 1)
            levels[cell] = level(counts[day] ?: 0, thresholds, allEqual)
            if (day == today) todayCell = cell
            // A month is named above the column that holds its first day; a period that starts mid-month names that month at column 0.
            if (day.dayOfMonth == 1 || day == range.start) marks += MonthMark(column, day.month)
            day = day.plusDays(1)
        }
        val kept = marks.filterIndexed { i, m -> i == marks.lastIndex || marks[i + 1].column - m.column >= MIN_LABEL_GAP }
        return WeekGrid(columns, levels, todayCell, kept)
    }

    fun build(counts: Map<LocalDate, Int>, period: ActivityPeriod, today: LocalDate): ActivityView {
        val range = rangeOf(period, today)
        var written = 0
        var day = range.start
        while (!day.isAfter(range.end)) {
            if (isWritten(counts[day] ?: 0)) written++
            day = day.plusDays(1)
        }
        return ActivityView(
            range = range,
            grid = grid(counts, range, today),
            daysWithInk = written,
            currentStreak = currentStreak(counts, range, today),
            longestStreak = longestStreak(counts, range),
            daysPerWeek = daysPerWeek(written, range)
        )
    }
}
