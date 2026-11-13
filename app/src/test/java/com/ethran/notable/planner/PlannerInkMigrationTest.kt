package com.ethran.notable.planner

import com.ethran.notable.data.db.Stroke
import com.ethran.notable.data.db.StrokePoint
import com.ethran.notable.editor.utils.Pen
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class PlannerInkMigrationTest {
    private val heights = listOf(2480f, 1860f, 1600f)   // portrait tablet, landscape, a smaller device
    private val monday: LocalDate = LocalDate.of(2026, 11, 2)
    private val wednesday: LocalDate = monday.plusDays(2)

    private fun stroke(id: String, cy: Float, page: String = "week", x: Float = 300f, half: Float = 5f) = Stroke(
        id = id, size = 3f, pen = Pen.BALLPEN, top = cy - half, bottom = cy + half, left = x, right = x + 100f,
        points = listOf(StrokePoint(x, cy - half), StrokePoint(x + 100f, cy + half)), pageId = page
    )

    private fun cy(s: Stroke) = (s.top + s.bottom) / 2f
    private fun moved(s: Stroke, kind: PlannerInkMigration.Kind, h: Float) = PlannerInkMigration.migrate(listOf(s), kind, h).firstOrNull() ?: s
    private fun run(block: suspend () -> Unit) = runBlocking { block() }

    // ------------------------------------------------------------------------------------------------ the old layout
    @Test fun `the old layout is the one before the zoom strip`() {
        assertEquals(PlannerLayout.ROWS_TOP - PlannerLayout.ZOOM_H, PlannerInkMigration.ROWS_TOP_OLD, 0.001f)
        val old = PlannerInkMigration.oldWeek(2480f)
        assertEquals(61.06f, old.step, 0.01f)
        assertEquals(58.31f, PlannerLayout.week(2480f).step, 0.01f)
        assertEquals(2480f - PlannerLayout.BOTTOM_MARGIN, old.bottom, 0.01f)
    }

    // ------------------------------------------------------------------------------------------------ week mapping
    @Test fun `a week stroke keeps its place inside its row`() {
        for (h in heights) {
            val old = PlannerInkMigration.oldWeek(h)
            val new = PlannerLayout.week(h)
            for (i in 0..6) for (f in listOf(0.0f, 0.13f, 0.5f, 0.97f)) {
                val centre = old.rowTop(i) + f * old.rowH
                val s = moved(stroke("s", centre), PlannerInkMigration.Kind.Week, h)
                assertEquals("h=$h row=$i f=$f", new.rowTop(i) + f * new.rowH, cy(s), 0.01f)
            }
        }
    }

    @Test fun `a stroke is translated, not scaled`() {
        val h = 2480f
        val old = PlannerInkMigration.oldWeek(h)
        val s = stroke("s", old.rowTop(4) + 100f, x = 420f, half = 30f)
        val m = moved(s, PlannerInkMigration.Kind.Week, h)
        val dy = cy(m) - cy(s)
        assertTrue(dy != 0f)
        assertEquals(s.bottom - s.top, m.bottom - m.top, 0.001f)
        assertEquals(s.left, m.left, 0f)
        assertEquals(s.right, m.right, 0f)
        assertEquals(s.points[0].y + dy, m.points[0].y, 0.001f)
        assertEquals(s.points[1].y + dy, m.points[1].y, 0.001f)
        assertEquals(s.points[1].x, m.points[1].x, 0f)
        assertEquals(s.id, m.id)
    }

    @Test fun `the centre decides when a stroke straddles a row border`() {
        val h = 2480f
        val old = PlannerInkMigration.oldWeek(h)
        val new = PlannerLayout.week(h)
        val border = old.rowTop(3)
        val above = stroke("above", border - 4f, half = 60f)     // centre in row 2, reaches into row 3
        val below = stroke("below", border + 4f, half = 60f)     // centre in row 3, reaches into row 2
        val a = moved(above, PlannerInkMigration.Kind.Week, h)
        val b = moved(below, PlannerInkMigration.Kind.Week, h)
        assertEquals(new.rowTop(2) + (border - 4f - old.rowTop(2)) * new.rowH / old.rowH, cy(a), 0.01f)
        assertEquals(new.rowTop(3) + 4f * new.rowH / old.rowH, cy(b), 0.01f)
        assertTrue(cy(a) < new.rowTop(3) && cy(b) >= new.rowTop(3))
    }

    @Test fun `header ink and ink under the last row stay where they are`() {
        for (h in heights) {
            val old = PlannerInkMigration.oldWeek(h)
            val stay = listOf(stroke("logo", 60f), stroke("tabs", 250f), stroke("edge", PlannerInkMigration.ROWS_TOP_OLD - 6f, half = 5f),
                stroke("margin", old.bottom + 10f, half = 4f), stroke("bottom", h - 3f, half = 2f))
            assertTrue("h=$h", PlannerInkMigration.migrate(stay, PlannerInkMigration.Kind.Week, h).isEmpty())
            // The first spot of row 0 does move (it is now under the zoom strip otherwise).
            val first = moved(stroke("r0", PlannerInkMigration.ROWS_TOP_OLD + 1f), PlannerInkMigration.Kind.Week, h)
            assertTrue(cy(first) >= PlannerLayout.ROWS_TOP)
        }
    }

    @Test fun `row borders map to row borders and rows keep their order`() {
        for (h in heights) {
            val old = PlannerInkMigration.oldWeek(h)
            val new = PlannerLayout.week(h)
            var last = -1f
            for (i in 0..6) {
                assertEquals(new.rowTop(i), PlannerInkMigration.newY(PlannerInkMigration.Kind.Week, old.rowTop(i), h), 0.01f)
                val inside = PlannerInkMigration.newY(PlannerInkMigration.Kind.Week, old.rowTop(i) + old.rowH - 0.01f, h)
                assertTrue(inside < new.rowTop(i + 1) && inside > last)
                last = inside
            }
        }
    }

    // ------------------------------------------------------------------------------------------------- day mapping
    @Test fun `old lines of a day page land on the new lines`() {
        for (h in heights) {
            val old = PlannerInkMigration.oldWeek(h)
            val new = PlannerLayout.week(h)
            for (k in 0..PlannerLayout.dayLines(h)) {
                val y = PlannerInkMigration.newY(PlannerInkMigration.Kind.Day, PlannerInkMigration.ROWS_TOP_OLD + k * old.step, h)
                assertEquals("h=$h line=$k", PlannerLayout.ROWS_TOP + k * new.step, y, 0.01f)
            }
        }
    }

    @Test fun `header ink of a day page stays`() {
        val s = listOf(stroke("title", 90f, "day"), stroke("tabs", 280f, "day"))
        assertTrue(PlannerInkMigration.migrate(s, PlannerInkMigration.Kind.Day, 2480f).isEmpty())
    }

    @Test fun `day ink far down the page follows the line step`() {
        val h = 2480f
        val m = moved(stroke("far", 2000f, "day"), PlannerInkMigration.Kind.Day, h)
        assertEquals(PlannerLayout.ROWS_TOP + (2000f - PlannerInkMigration.ROWS_TOP_OLD) * PlannerLayout.week(h).step / PlannerInkMigration.oldWeek(h).step, cy(m), 0.01f)
        assertTrue(cy(m) < 2480f - PlannerLayout.BOTTOM_MARGIN)
    }

    // ------------------------------------------------------------------------------------------- once, safely
    @Test fun `an empty page just gets the marker, with no backup`() = run {
        val store = FakePlannerStore().apply { strict = true }
        assertFalse(PlannerInkMigration.ensure(store, "week", PlannerInkMigration.Kind.Week, 2480f))
        assertTrue(store.layoutMarked("week"))
        assertEquals(0, store.backups)
    }

    @Test fun `a page with only unmoved ink gets the marker and no backup`() = run {
        val store = FakePlannerStore(stroke("logo", 60f)).apply { strict = true }
        assertFalse(PlannerInkMigration.ensure(store, "week", PlannerInkMigration.Kind.Week, 2480f))
        assertTrue(store.layoutMarked("week"))
        assertEquals(0, store.backups)
    }

    @Test fun `migrating twice changes nothing the second time`() = run {
        val h = 2480f
        val old = PlannerInkMigration.oldWeek(h)
        val store = FakePlannerStore(*(0..6).map { stroke("w$it", old.rowTop(it) + 90f) }.toTypedArray()).apply { strict = true }
        assertTrue(PlannerInkMigration.ensure(store, "week", PlannerInkMigration.Kind.Week, h))
        val after = store.all.toMap()
        assertFalse(PlannerInkMigration.ensure(store, "week", PlannerInkMigration.Kind.Week, h))
        assertEquals(after, store.all.toMap())
        assertEquals(1, store.commits.size)
        assertEquals(1, store.backups)
    }

    @Test fun `a page created with the current layout is never migrated`() = run {
        val h = 2480f
        val store = FakePlannerStore(stroke("w0", PlannerLayout.week(h).rowTop(0) + 50f)).apply { strict = true }
        store.marked += "week"
        val before = store.all.toMap()
        assertFalse(PlannerInkMigration.ensure(store, "week", PlannerInkMigration.Kind.Week, h))
        assertEquals(before, store.all.toMap())
        assertEquals(0, store.backups)
    }

    @Test fun `the backup is asked for before the first change and a failing backup does not block`() = run {
        val h = 2480f
        val old = PlannerInkMigration.oldWeek(h)
        val store = FakePlannerStore(stroke("w1", old.rowTop(1) + 50f)).apply { strict = true; backupFails = true }
        assertTrue(PlannerInkMigration.ensure(store, "week", PlannerInkMigration.Kind.Week, h))
        assertEquals(1, store.backups)
        assertTrue(cy(store.all.getValue("w1")) != old.rowTop(1) + 50f)
    }

    @Test fun `many strokes are written in chunks of one commit and the sync records of the page go with it`() = run {
        val h = 2480f
        val old = PlannerInkMigration.oldWeek(h)
        val many = (0 until 1000).map { stroke("s$it", old.rowTop(it % 7) + 20f + (it % 50)) }
        val store = FakePlannerStore(*many.toTypedArray()).apply { strict = true }
        store.kv["${PlannerSync.KEY_PREFIX}week:day"] = "{}"
        store.kv["${PlannerSync.KEY_PREFIX}v2:week:day2"] = "{}"
        store.kv["${PlannerSync.KEY_PREFIX}v2:zzz1:zzz2"] = "{}"
        store.kv["unrelated"] = "x"
        assertTrue(PlannerInkMigration.ensure(store, "week", PlannerInkMigration.Kind.Week, h))
        assertEquals(listOf(listOf(400, 400, 200)), store.commits)
        assertEquals(setOf("${PlannerSync.KEY_PREFIX}v2:zzz1:zzz2", "unrelated"), store.kv.keys)
    }

    @Test fun `ink erased before the migration is not brought back`() = run {
        val h = 2480f
        val old = PlannerInkMigration.oldWeek(h)
        val store = FakePlannerStore(stroke("keep", old.rowTop(1) + 50f), stroke("gone", old.rowTop(2) + 50f)).apply { strict = true }
        store.erase("gone")
        PlannerInkMigration.ensure(store, "week", PlannerInkMigration.Kind.Week, h)
        assertEquals(setOf("keep"), store.ids("week"))
    }

    @Test fun `only week and day pages have a migration`() {
        assertEquals(PlannerInkMigration.Kind.Week, PlannerInkMigration.kindOf(PlannerWeeks.weekBackground(monday)))
        assertEquals(PlannerInkMigration.Kind.Day, PlannerInkMigration.kindOf(PlannerWeeks.dayBackground(wednesday)))
        assertEquals(null, PlannerInkMigration.kindOf(PlannerWeeks.monthBackground(java.time.YearMonth.of(2026, 11))))
        assertEquals(null, PlannerInkMigration.kindOf("blank"))
    }

    // ------------------------------------------------------------------------------- migrated pairs and the sync
    private fun layoutOf(h: Float) = PlannerLayout.week(h)

    /** Old week with ink in every row, and the old day page of Wednesday with copies of row 2 made before the zoom strip. */
    private fun oldPair(h: Float, withCopies: Boolean): FakePlannerStore {
        val old = PlannerInkMigration.oldWeek(h)
        val weekInk = (0..6).flatMap { i ->
            listOf(stroke("w$i-a", old.rowTop(i) + 0.20f * old.rowH), stroke("w$i-b", old.rowTop(i) + 0.62f * old.rowH, x = 500f))
        }
        val dayInk = if (!withCopies) emptyList() else listOf("w2-a", "w2-b").map { id ->
            val w = weekInk.first { it.id == id }
            stroke(PlannerSync.copyId(id, "day"), PlannerInkMigration.ROWS_TOP_OLD + (cy(w) - old.rowTop(2)), "day", w.left)
        }
        return FakePlannerStore(*(weekInk + dayInk).toTypedArray()).apply {
            strict = true
            if (withCopies) kv["${PlannerSync.KEY_PREFIX}week:day"] = "{}"          // the first layout's record
        }
    }

    private fun assertCoincide(store: FakePlannerStore, h: Float) {
        val row = layoutOf(h).rowTop(2)
        val week = store.all.values.filter { it.pageId == "week" && cy(it) >= row && cy(it) < row + layoutOf(h).rowH }
        val day = store.all.values.filter { it.pageId == "day" && cy(it) >= PlannerLayout.ROWS_TOP && cy(it) < PlannerLayout.ROWS_TOP + layoutOf(h).rowH }
        val w = week.map { cy(it) - row }.sorted()
        val d = day.map { cy(it) - PlannerLayout.ROWS_TOP }.sorted()
        assertEquals(w.size, d.size)
        w.zip(d).forEach { (a, b) -> assertEquals(a, b, 0.05f) }
    }

    @Test fun `a week with ink in every row, migrated and synced with its day, loses and duplicates nothing`() {
        for (h in heights) {
            val store = oldPair(h, withCopies = true)
            val weekBefore = store.ids("week")
            val dayBefore = store.ids("day")
            runBlocking { PlannerSync.weekToDays(store, "week", monday, h, mapOf(wednesday to "day"), emptySet()) }
            assertEquals("h=$h week", weekBefore, store.ids("week"))
            assertEquals("h=$h day", dayBefore, store.ids("day"))
            assertEquals(14, store.ids("week").size)
            assertEquals(2, store.ids("day").size)
            assertCoincide(store, h)
            // The record of the pair was rewritten for the new layout and the old one is gone.
            assertTrue(store.kv.containsKey(PlannerSync.key("week", "day")))
            assertFalse(store.kv.containsKey("${PlannerSync.KEY_PREFIX}week:day"))
            // The next sync in either direction changes nothing.
            val snapshot = store.all.toMap()
            runBlocking {
                PlannerSync.dayToWeek(store, "day", wednesday, "week", h, emptySet())
                PlannerSync.weekToDays(store, "week", monday, h, mapOf(wednesday to "day"), emptySet())
            }
            assertEquals(snapshot, store.all.toMap())
        }
    }

    @Test fun `the migrated week ink sits on the same lines as before, relative to its check boxes`() {
        val h = 2480f
        val store = oldPair(h, withCopies = false)
        val old = PlannerInkMigration.oldWeek(h)
        val before = store.all.getValue("w5-a")
        runBlocking { PlannerInkMigration.ensure(store, "week", PlannerInkMigration.Kind.Week, h) }
        val after = store.all.getValue("w5-a")
        val lineBefore = (cy(before) - old.rowTop(5)) / old.step
        val lineAfter = (cy(after) - layoutOf(h).rowTop(5)) / layoutOf(h).step
        assertEquals(lineBefore, lineAfter, 0.001f)
    }

    @Test fun `week ink with an empty day page is copied once, on the day's lines`() {
        for (h in heights) {
            val store = oldPair(h, withCopies = false)
            runBlocking { PlannerSync.weekToDays(store, "week", monday, h, mapOf(wednesday to "day"), emptySet()) }
            assertEquals(setOf(PlannerSync.copyId("w2-a", "day"), PlannerSync.copyId("w2-b", "day")), store.ids("day"))
            assertEquals(14, store.ids("week").size)
            assertCoincide(store, h)
            val n = store.all.size
            runBlocking { PlannerSync.weekToDays(store, "week", monday, h, mapOf(wednesday to "day"), emptySet()) }
            assertEquals(n, store.all.size)
        }
    }

    @Test fun `day ink written in the old layout reaches the week row at the right place`() {
        val h = 2480f
        val old = PlannerInkMigration.oldWeek(h)
        val store = FakePlannerStore(stroke("d1", PlannerInkMigration.ROWS_TOP_OLD + 2.5f * old.step, "day"), stroke("d-far", 2200f, "day")).apply { strict = true }
        runBlocking { PlannerSync.dayToWeek(store, "day", wednesday, "week", h, emptySet()) }
        assertEquals(setOf("d1", "d-far"), store.ids("day"))
        assertEquals(setOf(PlannerSync.copyId("d1", "week")), store.ids("week"))
        val onWeek = store.all.getValue(PlannerSync.copyId("d1", "week"))
        assertEquals(layoutOf(h).rowTop(2) + 2.5f * layoutOf(h).step, cy(onWeek), 0.01f)
        assertEquals(PlannerLayout.ROWS_TOP + 2.5f * layoutOf(h).step, cy(store.all.getValue("d1")), 0.01f)
    }

    @Test fun `a week of an older notebook and a pair synced with the new layout but old ink are brought in line`() {
        val h = 2480f
        val store = oldPair(h, withCopies = false)
        // After the zoom strip a sync made copies from ink that was still at the old place; its record names both pages.
        runBlocking { PlannerSync.syncPair(store, "week", layoutOf(h).rowTop(2), "day", h, "week", setOf("never")) }
        assertTrue(store.kv.keys.any { it.startsWith(PlannerSync.KEY_PREFIX) })
        // Both pages were unmarked, so they were migrated before anything was mirrored: the record is already for new positions.
        assertEquals(setOf("week", "day"), store.marked)
        assertCoincide(store, h)
        assertEquals(setOf(PlannerSync.copyId("w2-a", "day"), PlannerSync.copyId("w2-b", "day")), store.ids("day"))
    }

    @Test fun `a page that is open and unmarked is not mirrored, the other one is migrated for later`() {
        val h = 2480f
        val store = oldPair(h, withCopies = false)
        val day0 = stroke("d0", PlannerInkMigration.ROWS_TOP_OLD + 40f, "day")
        store.add(day0)
        val changed = runBlocking { PlannerSync.weekToDays(store, "week", monday, h, mapOf(wednesday to "day"), setOf("week")) }
        assertEquals(emptySet<String>(), changed)               // the open week page is unmarked: nothing is touched
        assertEquals(setOf("d0"), store.ids("day"))
        assertTrue(store.marked.isEmpty())
    }

    @Test fun `the sync migrates the unopened page and reports it so its caches are dropped`() {
        val h = 2480f
        val store = oldPair(h, withCopies = false)
        store.marked += "week"                                   // the open page was migrated when it was opened
        store.add(stroke("d0", PlannerInkMigration.ROWS_TOP_OLD + 40f, "day"))
        val changed = runBlocking { PlannerSync.weekToDays(store, "week", monday, h, mapOf(wednesday to "day"), setOf("week")) }
        assertTrue("day" in changed)
        assertTrue("day" in store.marked)
        assertEquals(PlannerInkMigration.newY(PlannerInkMigration.Kind.Day, PlannerInkMigration.ROWS_TOP_OLD + 40f, h), cy(store.all.getValue("d0")), 0.01f)
    }

    @Test fun `new pages that carry the marker are mirrored without any migration`() {
        val h = 2480f
        val row = layoutOf(h).rowTop(2)
        val store = FakePlannerStore(stroke("w1", row + 30f)).apply { strict = true; marked += setOf("week", "day") }
        runBlocking { PlannerSync.weekToDays(store, "week", monday, h, mapOf(wednesday to "day"), emptySet()) }
        assertEquals(row + 30f, cy(store.all.getValue("w1")), 0f)
        assertEquals(PlannerLayout.ROWS_TOP + 30f, cy(store.all.getValue(PlannerSync.copyId("w1", "day"))), 0.01f)
        assertTrue(store.commits.isEmpty())
        assertEquals(0, store.backups)
    }
}
