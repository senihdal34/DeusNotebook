package com.ethran.notable.planner

import java.time.LocalDate
import java.time.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlannerPagesKeysTest {
    @Test fun `background keys have the agreed format`() {
        assertEquals("planner-month:2026-11", PlannerWeeks.monthBackground(YearMonth.of(2026, 11)))
        assertEquals("planner-month:2026-01", PlannerWeeks.monthBackground(YearMonth.of(2026, 1)))
        assertEquals("planner-quarter:2026-Q4", PlannerWeeks.quarterBackground(2026, 4))
        assertEquals("planner-year:2026", PlannerWeeks.yearBackground(2026))
        assertEquals("planner-week:2026-10-26", PlannerWeeks.weekBackground(LocalDate.of(2026, 10, 26)))
        assertEquals("planner-day:2026-11-12", PlannerWeeks.dayBackground(LocalDate.of(2026, 11, 12)))
    }

    @Test fun `keys round trip`() {
        for (m in 1..12) {
            val ym = YearMonth.of(2026, m)
            assertEquals(ym, PlannerWeeks.parseMonth(PlannerWeeks.monthBackground(ym)))
        }
        for (q in 1..4) assertEquals(PlannerWeeks.YearQuarter(2027, q), PlannerWeeks.parseQuarter(PlannerWeeks.quarterBackground(2027, q)))
        assertEquals(2026, PlannerWeeks.parseYear(PlannerWeeks.yearBackground(2026)))
        assertEquals(PlannerWeeks.YearQuarter(2026, 4), PlannerWeeks.parseQuarter("planner-quarter:2026-Q4"))
    }

    @Test fun `unknown or damaged keys are ignored`() {
        for (bad in listOf("planner-month:", "planner-month:2026", "planner-month:2026-13", "planner-month:2026-00", "planner-month:26-11",
            "planner-month:2026-11-01", "planner-month:abc", "planner-quarter:2026-Q5", "planner-quarter:2026-Q0", "planner-quarter:2026-4",
            "planner-year:", "planner-year:26", "planner-year:2026-01", "planner-year:abcd", "planner-decade:2020", "blank", "")) {
            assertNull(PlannerWeeks.parseMonth(bad), bad)
            assertNull(PlannerWeeks.parseQuarter(bad), bad)
            assertNull(PlannerWeeks.parseYear(bad), bad)
        }
        // keys of one level never parse as another
        assertNull(PlannerWeeks.parseMonth("planner-year:2026"))
        assertNull(PlannerWeeks.parseYear("planner-month:2026-11"))
        assertNull(PlannerWeeks.parseWeek("planner-month:2026-11"))
        assertNull(PlannerWeeks.parseDay("planner-quarter:2026-Q4"))
    }

    @Test fun `every planner key is a planner key and only fixed pages do not scroll`() {
        val keys = listOf(PlannerWeeks.monthBackground(YearMonth.of(2026, 11)), PlannerWeeks.quarterBackground(2026, 4), PlannerWeeks.yearBackground(2026),
            "planner-week:2026-10-26", "planner-day:2026-11-12", "planner-todo:2026-10-26", "planner-future:1")
        assertTrue(keys.all(PlannerWeeks::isPlanner))
        assertTrue(keys.take(5).all(PlannerWeeks::isFixedPage))
        assertFalse(PlannerWeeks.isFixedPage("planner-todo:2026-10-26"))
        assertFalse(PlannerWeeks.isFixedPage("planner-future:1"))
        assertFalse(PlannerWeeks.isFixedPage("blank"))
        assertFalse(PlannerWeeks.isPlanner("blank"))
    }

    @Test fun `level of a background`() {
        assertEquals(PlannerLevel.Month, PlannerLevel.of("planner-month:2026-11"))
        assertEquals(PlannerLevel.Quarter, PlannerLevel.of("planner-quarter:2026-Q4"))
        assertEquals(PlannerLevel.Year, PlannerLevel.of("planner-year:2026"))
        assertNull(PlannerLevel.of("planner-week:2026-10-26"))
        assertNull(PlannerLevel.of("lined"))
    }

    @Test fun `quarter arithmetic`() {
        val q = PlannerWeeks.YearQuarter.of(LocalDate.of(2026, 11, 12))
        assertEquals(PlannerWeeks.YearQuarter(2026, 4), q)
        assertEquals(LocalDate.of(2026, 10, 1), q.start)
        assertEquals(LocalDate.of(2026, 12, 31), q.end)
        assertEquals(PlannerWeeks.YearQuarter(2027, 1), q.plusQuarters(1))
        assertEquals(PlannerWeeks.YearQuarter(2026, 3), q.plusQuarters(-1))
        assertEquals(PlannerWeeks.YearQuarter(2026, 1), PlannerWeeks.YearQuarter.of(LocalDate.of(2026, 3, 31)))
        assertEquals(PlannerWeeks.YearQuarter(2026, 2), PlannerWeeks.YearQuarter.of(LocalDate.of(2026, 4, 1)))
        assertEquals(LocalDate.of(2028, 3, 31), PlannerWeeks.YearQuarter(2028, 1).end)
    }

    @Test fun `each notebook keeps its pages in date order`() {
        val months = listOf("planner-month:2027-01", "planner-month:2026-11", "planner-month:2026-12", "planner-month:2026-02")
        assertEquals(listOf("planner-month:2026-02", "planner-month:2026-11", "planner-month:2026-12", "planner-month:2027-01"),
            months.sortedBy { PlannerWeeks.sortKey(it) })
        val quarters = listOf("planner-quarter:2027-Q1", "planner-quarter:2026-Q4", "planner-quarter:2026-Q2", "planner-quarter:2026-Q3")
        assertEquals(listOf("planner-quarter:2026-Q2", "planner-quarter:2026-Q3", "planner-quarter:2026-Q4", "planner-quarter:2027-Q1"),
            quarters.sortedBy { PlannerWeeks.sortKey(it) })
        val years = listOf("planner-year:2028", "planner-year:2026", "planner-year:2027")
        assertEquals(listOf("planner-year:2026", "planner-year:2027", "planner-year:2028"), years.sortedBy { PlannerWeeks.sortKey(it) })
    }

    @Test fun `the week notebook sort order is unchanged`() {
        val pages = listOf("planner-day:2026-10-28", "planner-week:2026-11-02", "planner-week:2026-10-26", "planner-day:2026-10-26", "planner-day:2026-11-01")
        assertEquals(listOf("planner-week:2026-10-26", "planner-day:2026-10-26", "planner-day:2026-10-28", "planner-day:2026-11-01", "planner-week:2026-11-02"),
            pages.sortedBy { PlannerWeeks.sortKey(it) })
        assertNull(PlannerWeeks.sortKey("blank"))
        assertNull(PlannerWeeks.sortKey("planner-future:1"))
    }
}
