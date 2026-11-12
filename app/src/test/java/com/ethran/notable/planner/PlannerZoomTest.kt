package com.ethran.notable.planner

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import com.ethran.notable.planner.ZoomLevel.DAY
import com.ethran.notable.planner.ZoomLevel.MONTH
import com.ethran.notable.planner.ZoomLevel.QUARTER
import com.ethran.notable.planner.ZoomLevel.WEEK
import com.ethran.notable.planner.ZoomLevel.YEAR

class PlannerZoomTest {
    private fun d(s: String) = LocalDate.parse(s)
    private val today = d("2026-11-12")

    // ---- levelAndDate
    @Test fun `parses all five keys`() {
        assertEquals(DAY to d("2026-11-12"), levelAndDate("planner-day:2026-11-12"))
        assertEquals(WEEK to d("2026-11-09"), levelAndDate("planner-week:2026-11-09"))
        assertEquals(MONTH to d("2026-11-01"), levelAndDate("planner-month:2026-11"))
        assertEquals(QUARTER to d("2026-10-01"), levelAndDate("planner-quarter:2026-Q4"))
        assertEquals(QUARTER to d("2026-01-01"), levelAndDate("planner-quarter:2026-Q1"))
        assertEquals(YEAR to d("2026-01-01"), levelAndDate("planner-year:2026"))
    }

    @Test fun `parsing is tolerant about case and spaces`() {
        assertEquals(QUARTER to d("2026-04-01"), levelAndDate(" Planner-Quarter: 2026-q2 "))
        assertEquals(MONTH to d("2026-02-01"), levelAndDate("planner-month:2026-2"))
    }

    @Test fun `garbage keys give null`() {
        for (bad in listOf("", "blank", "inbox", "planner-day", "planner-day:", "planner-day:2026-13-01", "planner-day:2026-02-30",
            "planner-week:abc", "planner-month:2026-13", "planner-month:2026", "planner-quarter:2026-Q5", "planner-quarter:2026-Q0",
            "planner-quarter:2026", "planner-year:26", "planner-year:abcd", "planner-todo:2026-11-09", "planner-hour:2026-11-09")) {
            assertNull("'$bad'", levelAndDate(bad))
        }
    }

    // ---- ranges
    @Test fun `ranges of every level`() {
        assertEquals(d("2026-11-09")..d("2026-11-15"), zoomRange(WEEK, d("2026-11-12")))
        assertEquals(d("2026-11-01")..d("2026-11-30"), zoomRange(MONTH, d("2026-11-12")))
        assertEquals(d("2026-10-01")..d("2026-12-31"), zoomRange(QUARTER, d("2026-11-12")))
        assertEquals(d("2026-01-01")..d("2026-12-31"), zoomRange(YEAR, d("2026-11-12")))
        assertEquals(d("2024-02-01")..d("2024-02-29"), zoomRange(MONTH, d("2024-02-29")))
    }

    // ---- targetDate
    @Test fun `from a day page the day is the focus for every level`() {
        for (to in ZoomLevel.values()) assertEquals(d("2026-11-12"), targetDate(DAY, d("2026-11-12"), to, NO_FOCUS, today))
        // a remembered focus of another page does not matter on a day page
        assertEquals(d("2026-11-12"), targetDate(DAY, d("2026-11-12"), MONTH, d("2020-01-01"), today))
    }

    @Test fun `without focus a page that holds today zooms to today`() {
        for (from in listOf(MONTH, QUARTER, YEAR)) for (to in ZoomLevel.values())
            assertEquals("$from -> $to", today, targetDate(from, d(zoomRange(from, today).start.toString()), to, NO_FOCUS, today))
        assertEquals(today, targetDate(WEEK, d("2026-11-09"), DAY, NO_FOCUS, today))
        assertEquals(today, targetDate(WEEK, d("2026-11-09"), WEEK, NO_FOCUS, today))
    }

    @Test fun `without focus and without today a page zooms to its first day`() {
        assertEquals(d("2026-03-02"), targetDate(WEEK, d("2026-03-02"), DAY, NO_FOCUS, today))
        assertEquals(d("2026-03-01"), targetDate(MONTH, d("2026-03-01"), DAY, NO_FOCUS, today))
        assertEquals(d("2025-07-01"), targetDate(QUARTER, d("2025-07-01"), MONTH, NO_FOCUS, today))
        assertEquals(d("2025-01-01"), targetDate(YEAR, d("2025-01-01"), QUARTER, NO_FOCUS, today))
        assertEquals(d("2025-01-01"), targetDate(YEAR, d("2025-01-01"), DAY, NO_FOCUS, today))
    }

    @Test fun `week to coarser levels uses the Thursday of the week`() {
        // Mon 29 Dec 2025 .. Sun 4 Jan 2026: Thursday is 1 Jan 2026 -> January 2026, Q1 2026, 2026
        val monday = d("2025-12-29")
        for (to in listOf(MONTH, QUARTER, YEAR)) assertEquals(d("2026-01-01"), targetDate(WEEK, monday, to, NO_FOCUS, today))
        // the other way round: Mon 28 Jun 2027 .. Sun 4 Jul 2027, Thursday 1 Jul -> July (Q3), not June
        assertEquals(d("2027-07-01"), targetDate(WEEK, d("2027-06-28"), MONTH, NO_FOCUS, today))
        // ISO week 53 of 2020: Mon 28 Dec 2020, Thursday 31 Dec 2020 -> December 2020
        assertEquals(d("2020-12-31"), targetDate(WEEK, d("2020-12-28"), MONTH, NO_FOCUS, today))
        assertEquals(d("2020-12-31"), targetDate(WEEK, d("2020-12-28"), YEAR, NO_FOCUS, today))
        // the week Mon 30 Dec 2019 .. Sun 5 Jan 2020 (Thursday 2 Jan 2020) is ISO week 1 of 2020
        assertEquals(d("2020-01-02"), targetDate(WEEK, d("2019-12-30"), YEAR, NO_FOCUS, today))
    }

    @Test fun `week to finer levels keeps the monday or the day`() {
        assertEquals(d("2025-12-29"), targetDate(WEEK, d("2025-12-29"), DAY, NO_FOCUS, today))
    }

    @Test fun `a remembered focus inside the page is reused`() {
        val focus = d("2026-11-12")
        assertEquals(focus, targetDate(MONTH, d("2026-11-01"), DAY, focus, d("2026-01-01")))
        assertEquals(focus, targetDate(MONTH, d("2026-11-01"), WEEK, focus, d("2026-01-01")))
        assertEquals(focus, targetDate(QUARTER, d("2026-10-01"), MONTH, focus, d("2026-01-01")))
        assertEquals(focus, targetDate(YEAR, d("2026-01-01"), QUARTER, focus, d("2026-01-01")))
        // an explicit focus beats the Thursday rule
        assertEquals(d("2025-12-30"), targetDate(WEEK, d("2025-12-29"), MONTH, d("2025-12-30"), today))
    }

    @Test fun `a focus outside the page is ignored`() {
        assertEquals(d("2026-12-01"), targetDate(MONTH, d("2026-12-01"), DAY, d("2026-11-12"), today))
        assertEquals(d("2026-12-07"), targetDate(WEEK, d("2026-12-07"), DAY, d("2026-11-12"), today))
    }

    @Test fun `leap day`() {
        val leap = d("2024-02-29")
        assertEquals(leap, targetDate(DAY, leap, MONTH, NO_FOCUS, today))
        assertEquals(leap, targetDate(MONTH, d("2024-02-01"), DAY, leap, today))
        assertEquals(d("2024-02-01"), zoomRange(MONTH, leap).start)
        assertEquals(leap, zoomRange(MONTH, leap).endInclusive)
        // the week of the leap day (Mon 26 Feb .. Sun 3 Mar 2024) has Thursday 29 Feb -> February
        assertEquals(leap, targetDate(WEEK, d("2024-02-26"), MONTH, NO_FOCUS, today))
        assertEquals(d("2024-01-01"), targetDate(YEAR, d("2024-01-01"), MONTH, NO_FOCUS, today))
    }

    // ---- focus round trip through the navigator
    private class Fake {
        var focus: LocalDate? = null
        val asked = mutableListOf<Pair<ZoomLevel, LocalDate>>()
        val nav = PlannerZoomNavigator(
            ensure = { level, date -> asked += level to date; PlannerService.Target("book", "$level-$date") },
            today = { LocalDate.parse("2026-11-12") }, onFocus = { focus = it }, focus = { focus })
    }

    @Test fun `day to month to day returns to the same day`() = runBlocking {
        val f = Fake()
        f.focus = d("2026-11-03")   // an old focus of another day
        val day = d("2026-11-20")
        // opening a day page makes the day the focus (the host does this); then zoom out and in
        f.focus = day
        val toMonth = f.nav.go(DAY, day, MONTH)
        assertEquals("MONTH-2026-11-20", toMonth.pageId)
        val back = f.nav.go(MONTH, d("2026-11-01"), DAY)
        assertEquals("DAY-2026-11-20", back.pageId)
        // and through the year
        f.nav.go(DAY, day, YEAR)
        assertEquals("DAY-2026-11-20", f.nav.go(YEAR, d("2026-01-01"), DAY).pageId)
    }

    @Test fun `navigator remembers the focus only after the page exists`() = runBlocking {
        var focus: LocalDate? = null
        val nav = PlannerZoomNavigator(ensure = { _, _ -> error("boom") }, today = { today }, onFocus = { focus = it }, focus = { focus })
        runCatching { nav.go(MONTH, d("2026-11-01"), DAY) }
        assertNull(focus)
    }

    @Test fun `navigator without focus starts from today or the first day`() = runBlocking {
        val f = Fake()
        assertEquals("DAY-2026-11-12", f.nav.go(MONTH, d("2026-11-01"), DAY).pageId)
        val g = Fake()
        assertEquals("DAY-2026-12-01", g.nav.go(MONTH, d("2026-12-01"), DAY).pageId)
    }

    // ---- layout
    @Test fun `zoom strip sits between the tab bar and the page content`() {
        assertEquals(PlannerLayout.TABS_H + PlannerLayout.ZOOM_H, PlannerLayout.NAV_H, 0f)
        assertEquals(153f, PlannerLayout.TABS_H, 0f)
        assertEquals(PlannerLayout.HEADER_H + PlannerLayout.NAV_H, PlannerLayout.ROWS_TOP, 0f)
    }

    @Test fun `week step stays usable on portrait and landscape`() {
        for ((h, floor) in listOf(2480f to 55f, 1860f to 40f)) {
            val w = PlannerLayout.week(h)
            assertTrue(w.lines >= PlannerLayout.MIN_LINES)
            assertTrue("step ${w.step} at $h", w.step >= floor)
            assertEquals(h - PlannerLayout.BOTTOM_MARGIN, w.rowTop(7), 0.5f)
            // the day page keeps the week's line step, so its first lines coincide with the week row
            assertEquals(w.step, PlannerLayout.dayStep(h), 0f)
            assertTrue(PlannerLayout.dayLines(h) >= 8)
        }
    }
}
