package com.ethran.notable.activity

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The in-memory days with ink that the planner pages draw their dots from. */
class WritingDaysSnapshotTest {
    private fun d(s: String) = LocalDate.parse(s)
    private val today = d("2026-11-12")
    private fun snapshot() = WritingDaysSnapshot { today }

    @Test fun `an empty snapshot has no dots`() {
        val s = snapshot()
        assertFalse(s.hasInk(d("2026-11-10")))
        assertEquals(0, s.version.value)
    }

    @Test fun `a day counts from the same number of strokes as the activity card`() {
        val s = snapshot()
        s.update(mapOf(d("2026-11-09") to WritingActivityStats.MIN_STROKES - 1, d("2026-11-10") to WritingActivityStats.MIN_STROKES, d("2026-11-11") to 40))
        assertFalse(s.hasInk(d("2026-11-09")), "four strokes is a stray mark")
        assertTrue(s.hasInk(d("2026-11-10")))
        assertTrue(s.hasInk(d("2026-11-11")))
        assertFalse(s.hasInk(d("2026-11-08")), "no strokes")
    }

    @Test fun `an update replaces the old days`() {
        val s = snapshot()
        s.update(mapOf(d("2026-11-10") to 9))
        s.update(mapOf(d("2026-11-11") to 9))
        assertFalse(s.hasInk(d("2026-11-10")))
        assertTrue(s.hasInk(d("2026-11-11")))
    }

    @Test fun `only a real change reports itself and bumps the version`() {
        val s = snapshot()
        assertFalse(s.update(emptyMap()), "nothing written, nothing changed")
        assertTrue(s.update(mapOf(d("2026-11-10") to 9)))
        assertEquals(1, s.version.value)
        assertFalse(s.update(mapOf(d("2026-11-10") to 9)), "same days")
        assertFalse(s.update(mapOf(d("2026-11-10") to 30)), "more strokes on a day that already had a dot")
        assertFalse(s.update(mapOf(d("2026-11-10") to 9, d("2026-11-11") to 2)), "a day below the threshold")
        assertEquals(1, s.version.value)
        assertTrue(s.update(mapOf(d("2026-11-10") to 9, d("2026-11-11") to 5)))
        assertEquals(2, s.version.value)
    }

    @Test fun `no dot for a day that has not come`() {
        val s = snapshot()
        s.update(mapOf(today to 9, d("2026-11-13") to 50, d("2027-01-01") to 50))
        assertTrue(s.hasInk(today))
        assertFalse(s.hasInk(d("2026-11-13")))
        assertFalse(s.hasInk(d("2027-01-01")))
    }

    @Test fun `the repository publishes every answer to the snapshot`() = kotlinx.coroutines.test.runTest {
        val published = mutableListOf<Map<LocalDate, Int>>()
        val source = FakeSource(mapOf("2026-11-12" to 7))
        val repo = WritingActivityRepository(source, FakeStore(), kotlinx.coroutines.test.StandardTestDispatcher(testScheduler)) { published += it }
        repo.dayCounts()
        repo.dayCounts()   // answered from memory, still published
        assertEquals(listOf(mapOf(d("2026-11-12") to 7), mapOf(d("2026-11-12") to 7)), published)
    }
}
