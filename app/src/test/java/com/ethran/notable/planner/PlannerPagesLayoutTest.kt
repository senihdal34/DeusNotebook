package com.ethran.notable.planner

import java.time.LocalDate
import java.time.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlannerPagesLayoutTest {
    private val pw = 1860f
    private val ph = 2480f   // portrait page
    private val lw = 2480f
    private val lh = 1860f   // landscape page

    private fun d(y: Int, m: Int, day: Int) = LocalDate.of(y, m, day)

    // ---- month grid --------------------------------------------------------------------------------------------------

    @Test fun `february of a leap year has 29 days in six fixed rows`() {
        val weeks = PlannerPagesLayout.monthWeeks(YearMonth.of(2028, 2))   // 1 Feb 2028 is a Tuesday
        assertEquals(6, weeks.size)
        assertTrue(weeks.all { it.size == 7 })
        assertEquals(d(2028, 1, 31), weeks[0][0])
        assertEquals(d(2028, 2, 1), weeks[0][1])
        val inMonth = weeks.flatten().filter { YearMonth.from(it) == YearMonth.of(2028, 2) }
        assertEquals(29, inMonth.size)
        assertTrue(inMonth.contains(d(2028, 2, 29)))
        assertEquals(d(2028, 3, 12), weeks.last().last())
    }

    @Test fun `a month that starts on a sunday begins six days earlier`() {
        val weeks = PlannerPagesLayout.monthWeeks(YearMonth.of(2026, 3))   // 1 Mar 2026 is a Sunday
        assertEquals(d(2026, 2, 23), weeks[0][0])
        assertEquals(d(2026, 3, 1), weeks[0][6])
        assertEquals(6, PlannerPagesLayout.weeksNeeded(YearMonth.of(2026, 3)))
        assertEquals(d(2026, 4, 5), weeks[5][6])
    }

    @Test fun `a 28 day month that fits four rows still shows six`() {
        val feb = YearMonth.of(2027, 2)   // Monday 1 Feb to Sunday 28 Feb
        assertEquals(4, PlannerPagesLayout.weeksNeeded(feb))
        assertEquals(4, PlannerPagesLayout.miniWeeks(feb).size)
        val weeks = PlannerPagesLayout.monthWeeks(feb)
        assertEquals(6, weeks.size)
        assertEquals(d(2027, 3, 1), weeks[4][0])
        assertEquals(d(2027, 3, 14), weeks[5][6])
    }

    @Test fun `mini calendars use four to six rows`() {
        assertEquals(5, PlannerPagesLayout.weeksNeeded(YearMonth.of(2026, 10)))
        assertEquals(6, PlannerPagesLayout.weeksNeeded(YearMonth.of(2026, 11)))   // starts on a Sunday
        assertEquals(6, PlannerPagesLayout.weeksNeeded(YearMonth.of(2026, 8)))   // starts on a Saturday, 31 days
        for (m in 1..12) assertTrue(PlannerPagesLayout.weeksNeeded(YearMonth.of(2026, m)) in 4..6)
    }

    @Test fun `iso week 53 and the new year week`() {
        val rows = PlannerPagesLayout.month(YearMonth.of(2027, 1), pw, ph).rows
        assertEquals(d(2026, 12, 28), rows[0].monday)
        assertEquals(53, rows[0].isoWeek)   // 2026 has 53 ISO weeks
        assertEquals(1, rows[1].isoWeek)
        assertEquals(d(2027, 1, 4), rows[1].monday)
    }

    @Test fun `december 30 belongs to week 1 of the next year`() {
        val dec = PlannerPagesLayout.month(YearMonth.of(2024, 12), pw, ph).rows
        assertEquals(d(2024, 12, 30), dec[5].monday)
        assertEquals(1, dec[5].isoWeek)
        assertTrue(dec[5].cells.map { it.date }.contains(d(2025, 1, 1)))
        val jan = PlannerPagesLayout.month(YearMonth.of(2025, 1), pw, ph).rows
        assertEquals(d(2024, 12, 30), jan[0].monday)
        assertEquals(1, jan[0].isoWeek)
        assertEquals(listOf(false, false, true, true, true, true, true), jan[0].cells.map { it.inMonth })
    }

    @Test fun `month rows fill the page down to the bottom margin`() {
        for ((w, h) in listOf(pw to ph, lw to lh)) {
            val lay = PlannerPagesLayout.month(YearMonth.of(2026, 11), w, h)
            assertEquals(6, lay.rows.size)
            assertEquals(h - PlannerPagesLayout.BOTTOM, lay.rows.last().rect.bottom, 0.5f)
            assertEquals(PlannerLayout.ROWS_TOP, lay.focus.top, 0.01f)   // content starts at ROWS_TOP, never a hard-coded value
            assertEquals(lay.focus.bottom, lay.weekdayRow.top, 0.01f)
            assertTrue(lay.rows.all { it.rect.height > 150f })
        }
    }

    // ---- focus block -------------------------------------------------------------------------------------------------

    @Test fun `focus line count is clamped to one to five`() {
        assertEquals(1, PlannerFocusLines.clamp(0))
        assertEquals(1, PlannerFocusLines.clamp(-4))
        assertEquals(3, PlannerFocusLines.clamp(3))
        assertEquals(5, PlannerFocusLines.clamp(9))
        assertEquals(PlannerFocusLines.DEFAULT, 2)
        assertEquals(5, PlannerPagesLayout.month(YearMonth.of(2026, 11), pw, ph, 12).focus.lines)
        assertEquals(1, PlannerPagesLayout.month(YearMonth.of(2026, 11), pw, ph, 0).focus.lines)
    }

    @Test fun `an extra focus line makes the grid shorter`() {
        val two = PlannerPagesLayout.month(YearMonth.of(2026, 11), pw, ph, 2)
        val three = PlannerPagesLayout.month(YearMonth.of(2026, 11), pw, ph, 3)
        assertEquals(PlannerPagesLayout.FOCUS_STEP, three.focus.bottom - two.focus.bottom, 0.01f)
        assertEquals(two.gridTop + PlannerPagesLayout.FOCUS_STEP, three.gridTop, 0.01f)
        assertTrue(three.rows[0].rect.height < two.rows[0].rect.height)
        assertEquals(two.gridBottom, three.gridBottom, 0.01f)
    }

    @Test fun `the plus button exists until five lines`() {
        for (n in 1..4) assertNotNull(PlannerPagesLayout.month(YearMonth.of(2026, 11), pw, ph, n).focus.plusZone, "n=$n")
        assertNull(PlannerPagesLayout.month(YearMonth.of(2026, 11), pw, ph, 5).focus.plusZone)
        assertEquals(PageTap.AddFocusLine(PlannerLevel.Month), PlannerPagesLayout.month(YearMonth.of(2026, 11), pw, ph, 5).let { l ->
            PlannerPagesLayout.month(YearMonth.of(2026, 11), pw, ph, 2).tapAt(l.focus.plusCenterX, l.focus.plusCenterY)
        })
    }

    @Test fun `the plus zone stays clear of the writing lines`() {
        for (n in 1..4) {
            val f = PlannerPagesLayout.month(YearMonth.of(2026, 11), pw, ph, n).focus
            assertTrue(f.plusZone!!.right < f.linesLeft)
        }
    }

    @Test fun `focus block is taller with more lines`() {
        assertEquals(84f, PlannerPagesLayout.focusHeight(1), 0.01f)
        assertEquals(144f, PlannerPagesLayout.focusHeight(2), 0.01f)
        assertEquals(324f, PlannerPagesLayout.focusHeight(5), 0.01f)
    }

    // ---- tap zones ---------------------------------------------------------------------------------------------------

    @Test fun `a tap on a date number opens that day, also a dimmed neighbour day`() {
        val lay = PlannerPagesLayout.month(YearMonth.of(2026, 11), pw, ph)
        val twelfth = lay.rows[2].cells.first { it.date == d(2026, 11, 12) }
        assertEquals(PageTap.OpenDay(d(2026, 11, 12)), lay.tapAt(twelfth.number.centerX, twelfth.number.centerY))
        val dim = lay.rows[0].cells[0]   // 26 Oct, dimmed
        assertFalse(dim.inMonth)
        assertEquals(PageTap.OpenDay(d(2026, 10, 26)), lay.tapAt(dim.number.centerX, dim.number.centerY))
    }

    @Test fun `a tap on a week number opens that week`() {
        val lay = PlannerPagesLayout.month(YearMonth.of(2026, 11), pw, ph)
        val row = lay.rows[2]
        assertEquals(PageTap.OpenWeek(d(2026, 11, 9)), lay.tapAt(row.weekZone.centerX, row.weekZone.centerY))
    }

    @Test fun `the writing area of a day cell is not a tap zone`() {
        val lay = PlannerPagesLayout.month(YearMonth.of(2026, 11), pw, ph)
        for (row in lay.rows) for (c in row.cells) {
            assertNull(lay.tapAt(c.rect.centerX, c.rect.centerY))
            assertNull(lay.tapAt(c.rect.right - 5f, c.rect.bottom - 5f))
            assertTrue(c.number.bottom < c.rect.top + c.rect.height / 2f)
        }
        assertNull(lay.tapAt(10f, 10f))
        assertNull(lay.tapAt(lay.weekdayRow.centerX, lay.weekdayRow.centerY))
    }

    @Test fun `month zones never overlap each other`() {
        for ((w, h) in listOf(pw to ph, lw to lh)) assertNoOverlap(PlannerPagesLayout.month(YearMonth.of(2026, 11), w, h).taps)
    }

    private fun assertNoOverlap(zones: List<TapZone>) {
        for (i in zones.indices) for (j in i + 1 until zones.size) {
            val a = zones[i].rect
            val b = zones[j].rect
            val overlap = a.left < b.right && b.left < a.right && a.top < b.bottom && b.top < a.bottom
            assertFalse(overlap, "${zones[i].tap} overlaps ${zones[j].tap}")
        }
    }

    // ---- quarter ----------------------------------------------------------------------------------------------------

    @Test fun `a quarter has three months in date order with a goals area each`() {
        for ((w, h) in listOf(pw to ph, lw to lh)) {
            val lay = PlannerPagesLayout.quarter(PlannerWeeks.YearQuarter(2026, 4), w, h)
            assertEquals(listOf(YearMonth.of(2026, 10), YearMonth.of(2026, 11), YearMonth.of(2026, 12)), lay.months.map { it.month })
            assertEquals(PlannerLayout.ROWS_TOP, lay.focus.top, 0.01f)
            assertEquals(lay.focus.bottom, lay.areaTop, 0.01f)
            for (m in lay.months) {
                assertTrue(m.goalRules.size >= 5, "goal lines ${m.goalRules.size}")
                assertTrue(m.goalRules.all { it <= lay.areaBottom }, "goal lines stay on the page")
                assertTrue(m.calendar.rows.size in 4..6)
                assertTrue(m.calendar.rows.last().cy < lay.areaBottom)
            }
        }
    }

    @Test fun `portrait quarter stacks the months and landscape puts them side by side`() {
        val p = PlannerPagesLayout.quarter(PlannerWeeks.YearQuarter(2026, 4), pw, ph)
        assertTrue(p.portrait)
        assertTrue(p.months[0].block.top < p.months[1].block.top && p.months[1].block.top < p.months[2].block.top)
        assertEquals(p.months[0].block.left, p.months[2].block.left, 0.01f)
        val l = PlannerPagesLayout.quarter(PlannerWeeks.YearQuarter(2026, 4), lw, lh)
        assertFalse(l.portrait)
        assertTrue(l.months[0].block.left < l.months[1].block.left && l.months[1].block.left < l.months[2].block.left)
        assertEquals(l.months[0].block.top, l.months[2].block.top, 0.01f)
    }

    @Test fun `a month title on the quarter page opens that month and the goals stay free`() {
        for ((w, h) in listOf(pw to ph, lw to lh)) {
            val lay = PlannerPagesLayout.quarter(PlannerWeeks.YearQuarter(2026, 4), w, h)
            for (m in lay.months) {
                val z = m.calendar.titleZone
                assertEquals(PageTap.OpenMonth(m.month), lay.tapAt(z.centerX, z.centerY))
                assertNull(lay.tapAt(m.goalsLeft + 10f, m.goalRules.first() - 10f))
                assertTrue(z.bottom <= m.calendar.rows.first().cy)   // never reaches the day numbers
            }
            assertNoOverlap(lay.taps)
        }
    }

    @Test fun `iso week numbers on the quarter calendar`() {
        val nov = PlannerPagesLayout.quarter(PlannerWeeks.YearQuarter(2026, 4), pw, ph).months[1].calendar
        assertEquals(listOf(44, 45, 46, 47, 48, 49), nov.rows.map { it.isoWeek })
        val dec = PlannerPagesLayout.quarter(PlannerWeeks.YearQuarter(2026, 4), pw, ph).months[2].calendar
        assertEquals(listOf(49, 50, 51, 52, 53), dec.rows.map { it.isoWeek })
    }

    // ---- year -------------------------------------------------------------------------------------------------------

    @Test fun `year is three by four in portrait and four by three in landscape`() {
        val p = PlannerPagesLayout.year(2026, pw, ph)
        assertEquals(3, p.columns); assertEquals(4, p.rowsCount)
        assertEquals(12, p.cells.size)
        val l = PlannerPagesLayout.year(2026, lw, lh)
        assertEquals(4, l.columns); assertEquals(3, l.rowsCount)
        // January is first, December last, in reading order
        for (lay in listOf(p, l)) {
            assertEquals((1..12).toList(), lay.cells.map { it.month.monthValue })
            assertTrue(lay.cells.zipWithNext().all { (a, b) -> a.rect.top < b.rect.top || (a.rect.top == b.rect.top && a.rect.left < b.rect.left) })
            assertEquals(PlannerLayout.ROWS_TOP, lay.gridTop, 0.01f)
            assertTrue(lay.gridBottom <= lay.focus.top)
        }
    }

    @Test fun `year calendars fit their cells`() {
        for ((w, h) in listOf(pw to ph, lw to lh)) {
            val lay = PlannerPagesLayout.year(2026, w, h)
            for (c in lay.cells) {
                assertTrue(c.calendar.rows.last().cy + 20f <= c.rect.bottom, "${c.month} rows end inside the cell")
                assertTrue(c.calendar.columnX.last() + 20f <= c.rect.right)
                assertTrue(c.calendar.columnX.first() - 20f >= c.rect.left)
            }
        }
    }

    @Test fun `year focus sits at the bottom and grows upwards`() {
        val one = PlannerPagesLayout.year(2026, pw, ph, 1)
        val three = PlannerPagesLayout.year(2026, pw, ph, 3)
        assertEquals(ph - PlannerPagesLayout.BOTTOM, one.focus.bottom, 0.01f)
        assertEquals(ph - PlannerPagesLayout.BOTTOM, three.focus.bottom, 0.01f)
        assertEquals(2 * PlannerPagesLayout.FOCUS_STEP, one.focus.top - three.focus.top, 0.01f)
        assertTrue(three.cells[0].rect.height < one.cells[0].rect.height)
    }

    @Test fun `quarter labels and month titles open their page on the year page`() {
        val p = PlannerPagesLayout.year(2026, pw, ph)
        val q3 = p.quarterStripes[2]
        assertEquals(PageTap.OpenQuarter(2026, 3), p.tapAt(q3.centerX, q3.centerY))
        val nov = p.cells[10].calendar.titleZone
        assertEquals(PageTap.OpenMonth(YearMonth.of(2026, 11)), p.tapAt(nov.centerX, nov.centerY))
        assertNoOverlap(p.taps)
        // landscape: a small Q tag on the first month of each quarter
        val l = PlannerPagesLayout.year(2026, lw, lh)
        val tagged = l.cells.filter { it.quarterTag != null }.map { it.month.monthValue }
        assertEquals(listOf(1, 4, 7, 10), tagged)
        val oct = l.cells[9].quarterTag!!
        assertEquals(PageTap.OpenQuarter(2026, 4), l.tapAt(oct.centerX, oct.centerY))
        assertNoOverlap(l.taps)
    }

    @Test fun `the day numbers of a year calendar are not tap zones`() {
        for ((w, h) in listOf(pw to ph, lw to lh)) {
            val lay = PlannerPagesLayout.year(2026, w, h)
            for (c in lay.cells) for (row in c.calendar.rows) for (cell in row.cells) assertNull(lay.tapAt(cell.cx, cell.cy))
        }
    }

    // ---- period progress --------------------------------------------------------------------------------------------

    @Test fun `period percent runs from zero to a hundred`() {
        val s = d(2026, 11, 1)
        val e = d(2026, 11, 30)
        assertEquals(0, PlannerPagesLayout.periodPercent(s, e, d(2026, 10, 31)))
        assertEquals(100, PlannerPagesLayout.periodPercent(s, e, d(2026, 11, 30)))
        assertEquals(100, PlannerPagesLayout.periodPercent(s, e, d(2027, 1, 1)))
        assertEquals(40, PlannerPagesLayout.periodPercent(s, e, d(2026, 11, 12)))   // 12 of 30 days
    }

    @Test fun `the default writing snapshot has no ink`() {
        assertFalse(NoWritingDays.hasInk(d(2026, 11, 12)))
        assertTrue(PlannerWritingDays.current === NoWritingDays)
    }
}
