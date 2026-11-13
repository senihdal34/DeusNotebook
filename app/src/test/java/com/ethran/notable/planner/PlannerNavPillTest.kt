package com.ethran.notable.planner

import java.time.LocalDate
import java.time.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The `‹ PREVIOUS | THIS … | NEXT ›` pill in the header of the month, quarter and year pages. */
class PlannerNavPillTest {
    private val sizes = listOf(1860f to 2480f, 2480f to 1860f)   // portrait, landscape
    private val today = LocalDate.of(2026, 11, 12)

    private fun PageRect.inside(x: Float, y: Float) = x in left..right && y in top..bottom

    private fun tapAt(bg: String, w: Float, h: Float, x: Float, y: Float, now: LocalDate = today): PageTap? =
        pageTapZones(bg, w, h, today = now).firstOrNull { it.rect.inside(x, y) }?.tap

    // ---- the pill itself -------------------------------------------------------------------------------------------

    @Test fun `the three parts sit side by side in the header band`() {
        val p = PlannerPagesLayout.navPill()
        assertEquals(p.previous.right, p.current.left)
        assertEquals(p.current.right, p.next.left)
        assertTrue(p.outline.top >= 0f && p.outline.bottom < PlannerLayout.HEADER_H)
        assertTrue(p.outline.left >= 740f, "starts after the longest title (\"Quarterly Planner\" ends near x 720)")
        for ((w, _) in sizes) {
            assertTrue(PlannerPagesLayout.navFits(w))
            assertTrue(p.outline.right <= PlannerPagesLayout.right(w) - PlannerPagesLayout.DATE_BLOCK_W, "ends before the date block")
        }
    }

    @Test fun `no zone starts in the system's top-edge band`() {
        for ((w, h) in sizes) for (bg in listOf("planner-month:2026-11", "planner-quarter:2026-Q4", "planner-year:2026")) {
            for (z in pageTapZones(bg, w, h)) assertTrue(z.rect.top >= PlannerPagesLayout.SYSTEM_TOP_EDGE, "$bg ${z.tap}")
        }
    }

    @Test fun `a screen too narrow for the pill has neither pill nor zones`() {
        val w = 1404f   // a 7.8" e-ink screen in portrait
        assertFalse(PlannerPagesLayout.navFits(w))
        for (bg in listOf("planner-month:2026-11", "planner-quarter:2026-Q4", "planner-year:2026")) {
            assertTrue(pageTapZones(bg, w, 1872f).none { it.rect.bottom < PlannerLayout.HEADER_H }, bg)
        }
    }

    @Test fun `the zones are taller than the pill but never leave the header`() {
        val p = PlannerPagesLayout.navPill()
        for ((w, h) in sizes) for (bg in listOf("planner-month:2026-11", "planner-quarter:2026-Q4", "planner-year:2026")) {
            val zones = pageTapZones(bg, w, h).filter { it.rect.centerY < PlannerLayout.HEADER_H }
            assertEquals(3, zones.size, bg)
            for (z in zones) {
                assertTrue(z.rect.height > p.outline.height)
                assertTrue(z.rect.top >= 0f && z.rect.bottom < PlannerLayout.HEADER_H)
            }
            assertTrue(zones.zipWithNext().all { (a, b) -> a.rect.right <= b.rect.left })
        }
    }

    // ---- month page ------------------------------------------------------------------------------------------------

    @Test fun `month page pill goes to the previous this and next month in both orientations`() {
        val p = PlannerPagesLayout.navPill()
        for ((w, h) in sizes) {
            val bg = "planner-month:2026-05"
            assertEquals(PageTap.OpenMonth(YearMonth.of(2026, 4)), tapAt(bg, w, h, p.previous.centerX, p.previous.centerY))
            assertEquals(PageTap.OpenMonth(YearMonth.of(2026, 11)), tapAt(bg, w, h, p.current.centerX, p.current.centerY))
            assertEquals(PageTap.OpenMonth(YearMonth.of(2026, 6)), tapAt(bg, w, h, p.next.centerX, p.next.centerY))
        }
    }

    @Test fun `month pill crosses the year boundary`() {
        val p = PlannerPagesLayout.navPill()
        assertEquals(PageTap.OpenMonth(YearMonth.of(2025, 12)), tapAt("planner-month:2026-01", 1860f, 2480f, p.previous.centerX, p.previous.centerY))
        assertEquals(PageTap.OpenMonth(YearMonth.of(2027, 1)), tapAt("planner-month:2026-12", 1860f, 2480f, p.next.centerX, p.next.centerY))
    }

    @Test fun `this month follows today not the open page`() {
        val p = PlannerPagesLayout.navPill()
        assertEquals(PageTap.OpenMonth(YearMonth.of(2028, 2)), tapAt("planner-month:2026-11", 1860f, 2480f, p.current.centerX, p.current.centerY, LocalDate.of(2028, 2, 29)))
        // the leap day belongs to February, the day after it to March
        assertEquals(PageTap.OpenMonth(YearMonth.of(2028, 3)), tapAt("planner-month:2026-11", 1860f, 2480f, p.current.centerX, p.current.centerY, LocalDate.of(2028, 3, 1)))
    }

    // ---- quarter page ----------------------------------------------------------------------------------------------

    @Test fun `quarter page pill goes to the previous this and next quarter in both orientations`() {
        val p = PlannerPagesLayout.navPill()
        for ((w, h) in sizes) {
            val bg = "planner-quarter:2026-Q2"
            assertEquals(PageTap.OpenQuarter(2026, 1), tapAt(bg, w, h, p.previous.centerX, p.previous.centerY))
            assertEquals(PageTap.OpenQuarter(2026, 4), tapAt(bg, w, h, p.current.centerX, p.current.centerY))
            assertEquals(PageTap.OpenQuarter(2026, 3), tapAt(bg, w, h, p.next.centerX, p.next.centerY))
        }
    }

    @Test fun `quarter pill crosses the year boundary`() {
        val p = PlannerPagesLayout.navPill()
        assertEquals(PageTap.OpenQuarter(2025, 4), tapAt("planner-quarter:2026-Q1", 1860f, 2480f, p.previous.centerX, p.previous.centerY))
        assertEquals(PageTap.OpenQuarter(2027, 1), tapAt("planner-quarter:2026-Q4", 2480f, 1860f, p.next.centerX, p.next.centerY))
    }

    // ---- year page -------------------------------------------------------------------------------------------------

    @Test fun `year page pill goes to the previous this and next year in both orientations`() {
        val p = PlannerPagesLayout.navPill()
        for ((w, h) in sizes) {
            val bg = "planner-year:2028"   // a leap year
            assertEquals(PageTap.OpenYear(2027), tapAt(bg, w, h, p.previous.centerX, p.previous.centerY))
            assertEquals(PageTap.OpenYear(2026), tapAt(bg, w, h, p.current.centerX, p.current.centerY))
            assertEquals(PageTap.OpenYear(2029), tapAt(bg, w, h, p.next.centerX, p.next.centerY))
        }
    }

    @Test fun `this year on new year's eve and the day after`() {
        val p = PlannerPagesLayout.navPill()
        assertEquals(PageTap.OpenYear(2028), tapAt("planner-year:2026", 1860f, 2480f, p.current.centerX, p.current.centerY, LocalDate.of(2028, 12, 31)))
        assertEquals(PageTap.OpenYear(2029), tapAt("planner-year:2026", 1860f, 2480f, p.current.centerX, p.current.centerY, LocalDate.of(2029, 1, 1)))
    }

    // ---- the pill covers nothing else ------------------------------------------------------------------------------

    @Test fun `no pill zone overlaps another zone and the rest of the page is unchanged`() {
        for ((w, h) in sizes) for (bg in listOf("planner-month:2026-11", "planner-quarter:2026-Q4", "planner-year:2026")) {
            val zones = pageTapZones(bg, w, h)
            for (i in zones.indices) for (j in i + 1 until zones.size) {
                val a = zones[i].rect
                val b = zones[j].rect
                val overlap = a.left < b.right && b.left < a.right && a.top < b.bottom && b.top < a.bottom
                assertFalse(overlap, "$bg: ${zones[i].tap} / ${zones[j].tap}")
            }
        }
        // a tap left of the pill, on the title, does nothing
        assertNull(tapAt("planner-month:2026-11", 1860f, 2480f, 300f, 78f))
    }

    // ---- what a tap does -------------------------------------------------------------------------------------------

    @Test fun `a tap on the page that is already open opens nothing`() {
        assertTrue(PageTap.OpenMonth(YearMonth.of(2026, 11)).opens("planner-month:2026-11"))
        assertFalse(PageTap.OpenMonth(YearMonth.of(2026, 10)).opens("planner-month:2026-11"))
        assertTrue(PageTap.OpenQuarter(2026, 4).opens("planner-quarter:2026-Q4"))
        assertFalse(PageTap.OpenQuarter(2026, 3).opens("planner-quarter:2026-Q4"))
        assertTrue(PageTap.OpenYear(2026).opens("planner-year:2026"))
        assertFalse(PageTap.OpenYear(2027).opens("planner-year:2026"))
        assertFalse(PageTap.OpenYear(2026).opens("planner-month:2026-11"))
        assertFalse(PageTap.OpenDay(today).opens("planner-month:2026-11"))
    }

    @Test fun `the pill marks the current period only on its own page`() {
        assertTrue(PlannerPagesLayout.isCurrent(YearMonth.of(2026, 11), today))
        assertFalse(PlannerPagesLayout.isCurrent(YearMonth.of(2025, 11), today))
        assertTrue(PlannerPagesLayout.isCurrent(PlannerWeeks.YearQuarter(2026, 4), today))
        assertFalse(PlannerPagesLayout.isCurrent(PlannerWeeks.YearQuarter(2026, 3), today))
        assertTrue(PlannerPagesLayout.isCurrent(2026, today))
        assertNotEquals(true, PlannerPagesLayout.isCurrent(2027, today))
    }
}
