package com.ethran.notable.activity

import com.ethran.notable.activity.WritingActivityStats.OUTSIDE
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.Month
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class WritingActivityStatsTest {
    private val today = LocalDate.of(2026, 11, 12)   // a Thursday
    private fun d(s: String) = LocalDate.parse(s)
    private fun counts(vararg p: Pair<String, Int>): Map<LocalDate, Int> = p.associate { d(it.first) to it.second }
    private fun ActivityView.levelOn(day: LocalDate): Int {
        val monday = WritingActivityStats.mondayOf(range.start)
        val column = (java.time.temporal.ChronoUnit.DAYS.between(monday, day) / 7).toInt()
        return grid.levels[column * 7 + day.dayOfWeek.value - 1]
    }

    // ---- empty ----

    @Test fun `no strokes gives an empty view`() {
        val v = WritingActivityStats.build(emptyMap(), ActivityPeriod.Months(12), today)
        assertTrue(v.isEmpty)
        assertEquals(0, v.currentStreak)
        assertEquals(0, v.longestStreak)
        assertEquals(0.0, v.daysPerWeek)
        assertTrue(v.grid.levels.all { it == 0 || it == OUTSIDE })
        assertEquals(emptyList(), WritingActivityStats.yearsWithData(emptyMap(), today))
    }

    @Test fun `fewer than five strokes is not a written day`() {
        val c = counts("2026-11-12" to 4, "2026-11-11" to 5)
        val v = WritingActivityStats.build(c, ActivityPeriod.Months(3), today)
        assertEquals(1, v.daysWithInk)
        assertEquals(0, v.levelOn(today))
        assertTrue(v.levelOn(d("2026-11-11")) > 0)
    }

    // ---- intensity levels ----

    @Test fun `levels follow percentiles of the written days`() {
        val days = (1..8).associate { LocalDate.of(2026, 10, it) to it * 10 }   // 10..80
        val v = WritingActivityStats.build(days, ActivityPeriod.Months(3), today)
        val levels = (1..8).map { v.levelOn(LocalDate.of(2026, 10, it)) }
        assertEquals(levels.sorted(), levels)           // more strokes never means a lighter cell
        assertEquals(setOf(1, 2, 3, 4), levels.toSet())  // all four tones are used
        assertEquals(1, levels.first())
        assertEquals(4, levels.last())
    }

    @Test fun `one huge import day does not flatten the other days`() {
        val c = HashMap<LocalDate, Int>()
        for (i in 1..20) c[LocalDate.of(2026, 10, i)] = 5 + i * 3      // 8..65
        c[d("2026-10-25")] = 9000                                       // an import
        val v = WritingActivityStats.build(c, ActivityPeriod.Months(3), today)
        val normal = (1..20).map { v.levelOn(LocalDate.of(2026, 10, it)) }.toSet()
        assertTrue(normal.size >= 3, "other days must still spread over tones, got $normal")
        assertEquals(4, v.levelOn(d("2026-10-25")))
        // the import must not be what decides the scale: the same days without it use the same tones
        c.remove(d("2026-10-25"))
        val without = WritingActivityStats.build(c, ActivityPeriod.Months(3), today)
        assertTrue((1..20).map { without.levelOn(LocalDate.of(2026, 10, it)) }.toSet().size >= 3)
    }

    @Test fun `days that are all alike get one middle tone`() {
        val c = counts("2026-11-10" to 12, "2026-11-11" to 12)
        val v = WritingActivityStats.build(c, ActivityPeriod.Months(3), today)
        assertEquals(3, v.levelOn(d("2026-11-10")))
        assertEquals(3, v.levelOn(d("2026-11-11")))
    }

    @Test fun `thresholds are null without written days`() {
        assertNull(WritingActivityStats.thresholds(emptyList()))
        assertEquals(0, WritingActivityStats.level(100, null))
    }

    // ---- streaks ----

    @Test fun `streak ending today`() {
        val c = counts("2026-11-12" to 9, "2026-11-11" to 9, "2026-11-10" to 9, "2026-11-08" to 9)
        val v = WritingActivityStats.build(c, ActivityPeriod.Months(3), today)
        assertEquals(3, v.currentStreak)
        assertEquals(3, v.longestStreak)
    }

    @Test fun `streak survives a day with no ink yet today`() {
        val c = counts("2026-11-11" to 9, "2026-11-10" to 9, "2026-11-09" to 9)
        val v = WritingActivityStats.build(c, ActivityPeriod.Months(3), today)
        assertEquals(3, v.currentStreak)
    }

    @Test fun `streak is broken after a missed yesterday`() {
        val c = counts("2026-11-10" to 9, "2026-11-09" to 9)
        assertEquals(0, WritingActivityStats.build(c, ActivityPeriod.Months(3), today).currentStreak)
    }

    @Test fun `a thin day today does not extend nor break the streak`() {
        val c = counts("2026-11-12" to 2, "2026-11-11" to 9, "2026-11-10" to 9)
        assertEquals(2, WritingActivityStats.build(c, ActivityPeriod.Months(3), today).currentStreak)
    }

    @Test fun `longest streak is the best run inside the period`() {
        val run = (1..6).associate { LocalDate.of(2026, 3, it) to 9 }
        val c = run + counts("2026-11-12" to 9)
        assertEquals(6, WritingActivityStats.build(c, ActivityPeriod.Year(2026), today).longestStreak)
        assertEquals(1, WritingActivityStats.build(c, ActivityPeriod.Months(3), today).longestStreak)   // March is outside 3 months
    }

    @Test fun `a past year has no current streak`() {
        val c = counts("2025-12-31" to 9, "2025-12-30" to 9)
        val v = WritingActivityStats.build(c, ActivityPeriod.Year(2025), today)
        assertNull(v.currentStreak)
        assertEquals(2, v.longestStreak)
    }

    @Test fun `a streak is cut at the start of the period`() {
        val c = (0..40).associate { today.minusDays(it.toLong()) to 9 }
        assertEquals(41, WritingActivityStats.build(c, ActivityPeriod.Months(3), today).currentStreak)
        val long = (0..400).associate { today.minusDays(it.toLong()) to 9 }
        assertEquals(today.dayOfYear, WritingActivityStats.build(long, ActivityPeriod.Year(2026), today).currentStreak)
        assertEquals(92, WritingActivityStats.build(long, ActivityPeriod.Months(3), today).currentStreak)
    }

    // ---- days a week ----

    @Test fun `days a week is written days over weeks of the period`() {
        val range = WritingActivityStats.rangeOf(ActivityPeriod.Months(3), today)
        assertEquals(92, range.days)
        assertEquals(1.1, WritingActivityStats.daysPerWeek(15, range))
        assertEquals(7.0, WritingActivityStats.daysPerWeek(92, range))
    }

    // ---- periods ----

    @Test fun `month periods end today and are counted back by calendar months`() {
        assertEquals(d("2026-08-13"), WritingActivityStats.rangeOf(ActivityPeriod.Months(3), today).start)
        assertEquals(d("2026-05-13"), WritingActivityStats.rangeOf(ActivityPeriod.Months(6), today).start)
        assertEquals(d("2025-11-13"), WritingActivityStats.rangeOf(ActivityPeriod.Months(12), today).start)
        assertEquals(today, WritingActivityStats.rangeOf(ActivityPeriod.Months(12), today).end)
        assertEquals(365, WritingActivityStats.rangeOf(ActivityPeriod.Months(12), today).days)
    }

    @Test fun `a leap day today does not break 12 months`() {
        val leap = d("2028-02-29")
        val r = WritingActivityStats.rangeOf(ActivityPeriod.Months(12), leap)
        assertEquals(d("2027-03-01"), r.start)
        assertEquals(366, r.days)
        assertEquals(366, WritingActivityStats.rangeOf(ActivityPeriod.Year(2028), d("2028-12-31")).days)
        assertEquals(365, WritingActivityStats.rangeOf(ActivityPeriod.Year(2027), leap).days)
    }

    @Test fun `3 months from the end of a long month`() {
        val r = WritingActivityStats.rangeOf(ActivityPeriod.Months(3), d("2026-05-31"))
        assertEquals(d("2026-03-01"), r.start)   // Feb has no 31st: the calendar clamps
    }

    @Test fun `current year ends today and past years end in December`() {
        assertEquals(DateRangeOf("2026-01-01", "2026-11-12"), WritingActivityStats.rangeOf(ActivityPeriod.Year(2026), today))
        assertEquals(DateRangeOf("2025-01-01", "2025-12-31"), WritingActivityStats.rangeOf(ActivityPeriod.Year(2025), today))
    }

    private fun DateRangeOf(a: String, b: String) = WritingActivityStats.DateRange(d(a), d(b))

    @Test fun `period keys round trip and bad keys are rejected`() {
        for (p in listOf(ActivityPeriod.Months(3), ActivityPeriod.Months(6), ActivityPeriod.Months(12), ActivityPeriod.Year(2025))) {
            assertEquals(p, ActivityPeriod.parse(p.key))
        }
        assertNull(ActivityPeriod.parse(null))
        assertNull(ActivityPeriod.parse("m5"))
        assertNull(ActivityPeriod.parse("y12"))
        assertNull(ActivityPeriod.parse("yabc"))
        assertNull(ActivityPeriod.parse("x3"))
    }

    // ---- year chips ----

    @Test fun `year chips only for years with written days, newest first, at most three`() {
        val c = counts("2026-02-01" to 9, "2025-05-05" to 2, "2024-01-01" to 9, "2023-06-06" to 9, "2022-06-06" to 9, "2021-06-06" to 5)
        assertEquals(listOf(2026, 2024, 2023), WritingActivityStats.yearsWithData(c, today))   // 2025 has only a thin day
        assertEquals(listOf(2026), WritingActivityStats.yearsWithData(counts("2026-02-01" to 9, "2027-01-01" to 50), today))
    }

    // ---- grid ----

    @Test fun `grid starts on Monday and rows follow the weekday`() {
        val v = WritingActivityStats.build(counts("2026-11-09" to 9, "2026-11-15" to 9), ActivityPeriod.Months(3), d("2026-11-15"))
        val grid = v.grid
        assertEquals(DayOfWeek.MONDAY, WritingActivityStats.mondayOf(v.range.start).dayOfWeek)
        // today is a Sunday: the last cell of the last column
        assertEquals((grid.columns - 1) * 7 + 6, grid.todayCell)
        val lastColumn = grid.levels.takeLast(7)
        assertTrue(lastColumn.none { it == OUTSIDE })
        assertTrue(lastColumn[0] > 0 && lastColumn[6] > 0)
    }

    @Test fun `cells after today and before the first day are outside`() {
        val v = WritingActivityStats.build(emptyMap(), ActivityPeriod.Months(3), today)   // Thursday
        val last = v.grid.levels.takeLast(7)
        assertEquals(listOf(0, 0, 0, 0, OUTSIDE, OUTSIDE, OUTSIDE), last)   // Mon..Thu are drawn, Fri..Sun are not
        assertEquals(v.grid.columns * 7 - 4, v.grid.todayCell)
        // the period starts on Thursday 13 August: Mon..Wed of that first week are outside
        assertEquals(listOf(OUTSIDE, OUTSIDE, OUTSIDE, 0, 0, 0, 0), v.grid.levels.take(7))
    }

    @Test fun `a past year has no today cell`() {
        assertEquals(-1, WritingActivityStats.build(emptyMap(), ActivityPeriod.Year(2025), today).grid.todayCell)
    }

    @Test fun `week count of a year`() {
        // 2025: Jan 1 is a Wednesday, Dec 31 a Wednesday: Monday 30 Dec 2024 .. Monday 29 Dec 2025 = 53 columns
        assertEquals(53, WritingActivityStats.build(emptyMap(), ActivityPeriod.Year(2025), today).grid.columns)
        // 2023 starts on a Sunday and ends on a Sunday: 53 columns as well
        assertEquals(53, WritingActivityStats.build(emptyMap(), ActivityPeriod.Year(2023), today).grid.columns)
        // 2026 up to a Thursday in November
        assertEquals(46, WritingActivityStats.build(emptyMap(), ActivityPeriod.Year(2026), today).grid.columns)
    }

    @Test fun `grid across a daylight saving change keeps every day in its own cell`() {
        // 29 March 2026 is the day Europe moves its clocks; java.time dates do not care, and neither does the grid.
        val range = WritingActivityStats.DateRange(d("2026-03-23"), d("2026-04-05"))
        val c = (0..13).associate { range.start.plusDays(it.toLong()) to 10 }
        val g = WritingActivityStats.grid(c, range, d("2026-04-05"))
        assertEquals(2, g.columns)
        assertTrue(g.levels.all { it == 3 })   // 14 days, 14 cells, no gap and no double day
        assertEquals(DayOfWeek.SUNDAY, d("2026-03-29").dayOfWeek)   // the clock change day sits in the last cell of column 0
        assertEquals(DayOfWeek.MONDAY, WritingActivityStats.mondayOf(d("2026-03-29")).dayOfWeek)
    }

    @Test fun `month labels sit above the column of the first day and do not collide`() {
        val v = WritingActivityStats.build(emptyMap(), ActivityPeriod.Months(12), today)
        val marks = v.grid.months
        assertEquals(Month.NOVEMBER, marks.first().month)  // 13 Nov 2025, the period start, is a mid-month start
        assertEquals(Month.NOVEMBER, marks.last().month)
        assertEquals(Month.DECEMBER, marks[1].month)
        assertEquals(13, marks.size)
        for (i in 1 until marks.size) assertTrue(marks[i].column - marks[i - 1].column >= 3)
        assertTrue(marks.all { it.column in 0 until v.grid.columns })
    }

    @Test fun `a period that starts on the first of a month gets one label there`() {
        val marks = WritingActivityStats.build(emptyMap(), ActivityPeriod.Year(2025), today).grid.months
        assertEquals(Month.JANUARY, marks.first().month)
        assertEquals(12, marks.size)
        assertEquals(marks.map { it.month }.distinct().size, marks.size)
    }

    @Test fun `a first partial month too close to the next label is dropped`() {
        // the range starts on Sunday 29 Nov: column 0 holds only that day and December 1st is already in column 1
        val range = WritingActivityStats.DateRange(d("2026-11-29"), d("2026-12-20"))
        val marks = WritingActivityStats.grid(emptyMap(), range, d("2026-12-20")).months
        assertEquals(listOf(Month.DECEMBER), marks.map { it.month })
    }

    // ---- parsing and merging ----

    @Test fun `bad keys and non positive counts are ignored`() {
        val parsed = WritingActivityStats.parseDays(mapOf("2026-11-12" to 7, "nonsense" to 4, "2026-13-40" to 3, "2026-11-11" to 0, "2026-11-10" to -2))
        assertEquals(mapOf(d("2026-11-12") to 7), parsed)
    }

    @Test fun `merge keeps the larger count per day`() {
        val merged = WritingActivityStats.mergeMax(mapOf("2026-11-01" to 50, "2026-11-02" to 10), mapOf("2026-11-01" to 3, "2026-11-02" to 12, "2026-11-03" to 1))
        assertEquals(mapOf("2026-11-01" to 50, "2026-11-02" to 12, "2026-11-03" to 1), merged)
    }

    @Test fun `written threshold is five`() {
        assertFalse(WritingActivityStats.isWritten(4))
        assertTrue(WritingActivityStats.isWritten(5))
        assertNotNull(WritingActivityStats.thresholds(listOf(5)))
    }
}
