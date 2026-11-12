package com.ethran.notable.planner

import com.ethran.notable.data.db.Stroke
import com.ethran.notable.data.db.StrokePoint
import com.ethran.notable.editor.utils.Pen
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class PlannerSyncTest {
    private fun stroke(id: String, y: Float, page: String = "week", x: Float = 300f) = Stroke(
        id = id, size = 3f, pen = Pen.BALLPEN, top = y - 5f, bottom = y + 5f, left = x, right = x + 100f,
        points = listOf(StrokePoint(x, y - 5f), StrokePoint(x + 100f, y + 5f)), pageId = page
    )

    @Test fun `only strokes centred in the region are taken`() {
        val all = listOf(stroke("a", 100f), stroke("b", 500f), stroke("c", 900f))
        assertEquals(listOf("b"), PlannerSync.inRegion(all, 400f, 600f).map { it.id })
        assertTrue(PlannerSync.inRegion(all, 1000f, 1200f).isEmpty())
    }

    @Test fun `copies move vertically and keep the page of the target`() {
        val moved = PlannerSync.copies(listOf(stroke("b", 500f)), -300f, "day")
        assertEquals(1, moved.size)
        assertEquals("day", moved[0].pageId)
        assertEquals(195f, moved[0].top, 0.01f)
        assertEquals(205f, moved[0].bottom, 0.01f)
        assertEquals(200f, moved[0].points[0].y + 5f, 0.01f)
        assertEquals(300f, moved[0].left, 0.01f)          // no horizontal move
    }

    @Test fun `copy ids are stable per source and target`() {
        val s = stroke("b", 500f)
        val first = PlannerSync.copies(listOf(s), 0f, "day")[0].id
        assertEquals(first, PlannerSync.copies(listOf(s), 0f, "day")[0].id)
        assertNotEquals(first, PlannerSync.copies(listOf(s), 0f, "other")[0].id)
        assertNotEquals(s.id, first)
        assertEquals(PlannerSync.copyId("b", "day"), first)
    }

    @Test fun `day page shares the week step and rows start under the tab bar`() {
        assertEquals(PlannerLayout.week(2480f).step, PlannerLayout.dayStep(2480f), 0.001f)
        assertTrue(PlannerLayout.dayLines(2480f) > PlannerLayout.week(2480f).lines)
    }

    // ------------------------------------------------------------------------------------ syncing week and day
    private val h = 2480f
    private val layout = PlannerLayout.week(h)
    private val monday: LocalDate = LocalDate.of(2026, 11, 2)
    private val wednesday: LocalDate = monday.plusDays(2)
    private val row = layout.rowTop(2)                   // Wednesday's row on the week page
    private val dayTop = PlannerLayout.ROWS_TOP

    private fun onWeek(id: String, dy: Float = 20f, x: Float = 300f) = stroke(id, row + dy, "week", x)
    private fun onDay(id: String, dy: Float = 20f, x: Float = 300f) = stroke(id, dayTop + dy, "day", x)

    private class FakeStore(vararg strokes: Stroke) : PlannerSyncStore {
        val all = LinkedHashMap<String, Stroke>().apply { strokes.forEach { put(it.id, it) } }
        val kv = HashMap<String, String>()
        var applied = 0
        override suspend fun strokes(pageId: String) = all.values.filter { it.pageId == pageId }
        override suspend fun mirrored(key: String) = kv[key]
        override suspend fun apply(delete: List<String>, insert: List<Stroke>, update: List<Stroke>, key: String, mirrored: String) {
            applied++
            delete.forEach { all.remove(it) }
            update.forEach { require(all.containsKey(it.id)) { "update of a missing stroke" }; all[it.id] = it }
            insert.forEach { require(!all.containsKey(it.id)) { "duplicate id ${it.id}" }; all[it.id] = it }
            kv[key] = mirrored
        }
        fun ids(page: String) = all.values.filter { it.pageId == page }.map { it.id }.toSet()
        fun add(s: Stroke) { all[s.id] = s }
        fun erase(id: String) { all.remove(id) }
    }

    /** Leaving the week page in the app: the week page is open, so only the day page is changed. */
    private fun leaveWeek(store: FakeStore, frozen: Set<String> = setOf("week")) = runBlocking {
        PlannerSync.weekToDays(store, "week", monday, h, mapOf(wednesday to "day"), frozen)
    }

    private fun leaveDay(store: FakeStore, frozen: Set<String> = setOf("day")) = runBlocking {
        PlannerSync.dayToWeek(store, "day", wednesday, "week", h, frozen)
    }

    private fun copyOnDay(id: String) = PlannerSync.copyId(id, "day")
    private fun copyOnWeek(id: String) = PlannerSync.copyId(id, "week")

    @Test fun `day ink written before the feature survives the first week to day sync`() {
        val store = FakeStore(onWeek("w1"), onDay("d1"), onDay("d-far", dy = 2000f))
        assertEquals(setOf("day"), leaveWeek(store))
        assertEquals(setOf("d1", "d-far", copyOnDay("w1")), store.ids("day"))
        assertEquals(setOf("w1"), store.ids("week"))           // the open week page is left alone
        // Leaving the day page brings its own ink to the week row; nothing is lost on either page.
        assertEquals(setOf("week"), leaveDay(store))
        assertEquals(setOf("w1", copyOnWeek("d1")), store.ids("week"))
        assertEquals(setOf("d1", "d-far", copyOnDay("w1")), store.ids("day"))
    }

    @Test fun `a copy is placed where the original sits in its row`() {
        val store = FakeStore(onWeek("w1", dy = 33f, x = 410f))
        leaveWeek(store)
        val copy = store.all.getValue(copyOnDay("w1"))
        assertEquals(dayTop + 33f - 5f, copy.top, 0.01f)
        assertEquals(410f, copy.left, 0.01f)
    }

    @Test fun `new day ink after a process kill is not overwritten by the older week row`() {
        val store = FakeStore(onWeek("w1"), onDay("d1"))
        leaveWeek(store, frozen = emptySet())
        // Written on the day page, then the app was killed: no sync on leaving it. Later the week page is left.
        store.add(onDay("d2", dy = 60f))
        leaveWeek(store)
        assertTrue("d2" in store.ids("day"))
        assertTrue(copyOnDay("w1") in store.ids("day"))
        // The next sync that may change the week page gives it the new line as well.
        leaveDay(store)
        assertTrue(copyOnWeek("d2") in store.ids("week"))
    }

    @Test fun `copies follow their source when it changes`() {
        val store = FakeStore(onWeek("w1"), onWeek("w2", dy = 60f))
        leaveWeek(store)
        assertEquals(setOf(copyOnDay("w1"), copyOnDay("w2")), store.ids("day"))

        store.erase("w1")                                       // erased on the week page
        store.add(onWeek("w3", dy = 90f))                       // a new line
        store.add(store.all.getValue("w2").let { it.copy(top = it.top + 10f, bottom = it.bottom + 10f) })   // moved down
        leaveWeek(store)
        assertEquals(setOf(copyOnDay("w2"), copyOnDay("w3")), store.ids("day"))
        assertEquals(dayTop + 60f + 10f - 5f, store.all.getValue(copyOnDay("w2")).top, 0.01f)
    }

    @Test fun `native strokes of the target that were never mirrored are never deleted`() {
        val dayInk = listOf(onDay("d1"), onDay("d2", dy = 70f), onDay("d-below", dy = 1500f))
        val store = FakeStore(onWeek("w1"), *dayInk.toTypedArray())
        repeat(3) {
            leaveWeek(store); leaveDay(store)
            store.erase("w1"); store.add(onWeek("w1-$it", dy = 40f + it))   // the week row keeps changing
            leaveWeek(store); leaveDay(store)
        }
        assertTrue(store.ids("day").containsAll(dayInk.map { it.id }))
        assertTrue(store.ids("week").containsAll(listOf(copyOnWeek("d1"), copyOnWeek("d2"))))
    }

    @Test fun `syncing again changes nothing`() {
        val store = FakeStore(onWeek("w1"), onWeek("w2", dy = 60f), onDay("d1", dy = 90f))
        leaveWeek(store, frozen = emptySet())
        val after = store.all.toMap()
        val writes = store.applied
        assertEquals(emptySet<String>(), leaveWeek(store, frozen = emptySet()))
        assertEquals(emptySet<String>(), leaveDay(store, frozen = emptySet()))
        assertEquals(writes, store.applied)
        assertEquals(after, store.all.toMap())
        assertEquals(setOf("w1", "w2", copyOnWeek("d1")), store.ids("week"))
        assertEquals(setOf("d1", copyOnDay("w1"), copyOnDay("w2")), store.ids("day"))
    }

    @Test fun `erasing works in both directions once a stroke was mirrored`() {
        val store = FakeStore(onWeek("w1"), onDay("d1", dy = 60f))
        leaveWeek(store, frozen = emptySet())
        // The copy of the week line is erased on the day page: leaving it removes the week line.
        store.erase(copyOnDay("w1"))
        leaveDay(store)
        assertEquals(setOf(copyOnWeek("d1")), store.ids("week"))
        // The copy of the day line is erased on the week page: leaving it removes the day line.
        store.erase(copyOnWeek("d1"))
        leaveWeek(store)
        assertEquals(emptySet<String>(), store.ids("day"))
    }

    @Test fun `day ink is copied to the week without leaving the day page`() {
        val store = FakeStore(onDay("d1"))
        leaveDay(store)
        assertEquals(setOf("d1"), store.ids("day"))
        assertEquals(setOf(copyOnWeek("d1")), store.ids("week"))
        assertEquals(row + 20f - 5f, store.all.getValue(copyOnWeek("d1")).top, 0.01f)
    }

    @Test fun `a stroke moved on the page that holds its copy moves the original`() {
        val store = FakeStore(onWeek("w1"))
        leaveWeek(store)
        store.add(store.all.getValue(copyOnDay("w1")).let { it.copy(left = it.left + 50f, right = it.right + 50f) })
        leaveDay(store)
        assertEquals(350f, store.all.getValue("w1").left, 0.01f)
        assertEquals(setOf("w1"), store.ids("week"))
    }

    @Test fun `the open page is never changed and its part waits`() {
        val store = FakeStore(onWeek("w1"), onDay("d1", dy = 60f))
        leaveWeek(store, frozen = setOf("week", "day"))
        assertEquals(setOf("w1"), store.ids("week"))
        assertEquals(setOf("d1"), store.ids("day"))
        // Once neither page is open, each gets the other's ink.
        leaveWeek(store, frozen = emptySet())
        assertEquals(setOf("w1", copyOnWeek("d1")), store.ids("week"))
        assertEquals(setOf("d1", copyOnDay("w1")), store.ids("day"))
    }

    @Test fun `a deferred erase is kept for the next sync`() {
        val store = FakeStore(onWeek("w1"))
        leaveWeek(store, frozen = emptySet())
        store.erase(copyOnDay("w1"))
        leaveDay(store, frozen = setOf("week", "day"))         // both open: nothing can change yet
        assertEquals(setOf("w1"), store.ids("week"))
        leaveDay(store)
        assertEquals(emptySet<String>(), store.ids("week"))
    }

    @Test fun `copies made by the first version are recognised without a record`() {
        // The old sync wrote the same copy ids; there is no record of what was mirrored.
        val old = PlannerSync.copies(listOf(onWeek("w1")), dayTop - row, "day")
        val store = FakeStore(onWeek("w1"), *old.toTypedArray(), onDay("d1", dy = 60f))
        leaveWeek(store, frozen = emptySet())
        assertEquals(setOf("w1", copyOnWeek("d1")), store.ids("week"))
        assertEquals(setOf(copyOnDay("w1"), "d1"), store.ids("day"))
    }

    @Test fun `a copy moved out of the row is left alone instead of copied twice`() {
        val store = FakeStore(onWeek("w1"))
        leaveWeek(store, frozen = emptySet())
        store.kv.clear()                                        // no record, e.g. data from the first version
        store.add(store.all.getValue(copyOnDay("w1")).let { it.copy(top = it.top + 1500f, bottom = it.bottom + 1500f) })
        leaveWeek(store, frozen = emptySet())                    // must not insert a second stroke with the same id
        assertEquals(setOf("w1"), store.ids("week"))
        assertEquals(setOf(copyOnDay("w1")), store.ids("day"))
    }

    @Test fun `a damaged record reads as nothing mirrored, which only adds`() {
        assertEquals(emptyMap<String, PlannerSync.Mark>(), PlannerSync.decode("{ not json"))
        assertEquals(emptyMap<String, PlannerSync.Mark>(), PlannerSync.decode(null))
        val m = mapOf("a" to PlannerSync.Mark(1f, 2f, 3f, 4f, 5))
        assertEquals(m, PlannerSync.decode(PlannerSync.encode(m)))
        val store = FakeStore(onWeek("w1"), onDay("d1", dy = 60f))
        store.kv[PlannerSync.key("week", "day")] = "garbage"
        leaveWeek(store, frozen = emptySet())
        assertEquals(setOf("w1", copyOnWeek("d1")), store.ids("week"))
        assertEquals(setOf("d1", copyOnDay("w1")), store.ids("day"))
    }

    @Test fun `ink on other rows of the week is not taken`() {
        val store = FakeStore(stroke("tue", layout.rowTop(1) + 20f, "week"), stroke("thu", layout.rowTop(3) + 20f, "week"))
        assertEquals(emptySet<String>(), leaveWeek(store))
        assertEquals(emptySet<String>(), store.ids("day"))
    }
}
