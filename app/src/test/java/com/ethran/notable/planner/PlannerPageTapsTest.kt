package com.ethran.notable.planner

import java.time.LocalDate
import java.time.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PlannerPageTapsTest {
    @Test fun `month page zones come from the month layout`() {
        val zones = pageTapZones("planner-month:2026-11", 1860f, 2480f)
        assertEquals(PlannerPagesLayout.month(YearMonth.of(2026, 11), 1860f, 2480f).taps, zones)
        assertTrue(zones.any { it.tap == PageTap.OpenDay(LocalDate.of(2026, 11, 12)) })
        assertTrue(zones.any { it.tap == PageTap.OpenWeek(LocalDate.of(2026, 11, 9)) })
        assertTrue(zones.any { it.tap == PageTap.AddFocusLine(PlannerLevel.Month) })
    }

    @Test fun `the plus button follows the line count of its own page type`() {
        assertTrue(pageTapZones("planner-month:2026-11", 1860f, 2480f, month = 5).none { it.tap is PageTap.AddFocusLine })
        assertTrue(pageTapZones("planner-quarter:2026-Q4", 1860f, 2480f, month = 5).any { it.tap is PageTap.AddFocusLine })
        assertTrue(pageTapZones("planner-year:2026", 2480f, 1860f, year = 5).none { it.tap is PageTap.AddFocusLine })
    }

    @Test fun `other pages have no zones`() {
        assertTrue(pageTapZones(null, 1860f, 2480f).isEmpty())
        assertTrue(pageTapZones("blank", 1860f, 2480f).isEmpty())
        assertTrue(pageTapZones("planner-week:2026-10-26", 1860f, 2480f).isEmpty())
        assertTrue(pageTapZones("planner-month:broken", 1860f, 2480f).isEmpty())
        assertTrue(pageTapZones("planner-future:1", 1860f, 2480f).isEmpty())
    }

    @Test fun `quarter and year pages offer month and quarter taps`() {
        val q = pageTapZones("planner-quarter:2026-Q4", 2480f, 1860f)
        assertEquals(listOf(10, 11, 12), q.mapNotNull { (it.tap as? PageTap.OpenMonth)?.month?.monthValue })
        val y = pageTapZones("planner-year:2026", 1860f, 2480f)
        assertEquals((1..12).toList(), y.mapNotNull { (it.tap as? PageTap.OpenMonth)?.month?.monthValue })
        assertEquals(listOf(1, 2, 3, 4), y.mapNotNull { (it.tap as? PageTap.OpenQuarter)?.quarter })
    }

    @Test fun `every zone stays on the page`() {
        for ((w, h) in listOf(1860f to 2480f, 2480f to 1860f)) {
            for (bg in listOf("planner-month:2026-11", "planner-quarter:2026-Q4", "planner-year:2026")) {
                for (z in pageTapZones(bg, w, h).filter { zone -> PlannerPagesLayout.navPill().let { zone.rect.top < PlannerLayout.ROWS_TOP && zone.rect.top >= it.outline.top - PlannerPagesLayout.NAV_TAP_PAD } }) {
                    assertTrue(z.rect.left >= 0f && z.rect.top >= 0f && z.rect.bottom < PlannerLayout.HEADER_H && z.rect.right <= w, "$bg ${z.tap} nav zone is in the header")
                }
                for (z in pageTapZones(bg, w, h).filter { it.rect.top >= PlannerLayout.ROWS_TOP - 1f }) {
                    assertTrue(z.rect.left >= 0f && z.rect.top >= PlannerLayout.ROWS_TOP - 1f && z.rect.right <= w && z.rect.bottom <= h, "$bg ${z.tap}")
                }
            }
        }
    }
}
