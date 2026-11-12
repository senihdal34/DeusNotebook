package com.ethran.notable.planner

import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlannerFocusLinesTest {
    private class FakeStore(val data: MutableMap<String, String> = mutableMapOf()) : FocusStore {
        override suspend fun read(key: String) = data[key]
        override suspend fun write(key: String, value: String) { data[key] = value }
    }

    @AfterTest fun reset() { PlannerFocusLines.counts = emptyMap() }

    @Test fun `keys are per page type`() {
        assertEquals("planner-focus-lines:month", PlannerFocusLines.key(PlannerLevel.Month))
        assertEquals("planner-focus-lines:quarter", PlannerFocusLines.key(PlannerLevel.Quarter))
        assertEquals("planner-focus-lines:year", PlannerFocusLines.key(PlannerLevel.Year))
    }

    @Test fun `stored values are read safely`() {
        assertEquals(2, PlannerFocusLines.parse(null))
        assertEquals(2, PlannerFocusLines.parse("nonsense"))
        assertEquals(2, PlannerFocusLines.parse(""))
        assertEquals(4, PlannerFocusLines.parse("4"))
        assertEquals(4, PlannerFocusLines.parse(" 4 "))
        assertEquals(5, PlannerFocusLines.parse("99"))
        assertEquals(1, PlannerFocusLines.parse("0"))
        assertEquals(1, PlannerFocusLines.parse("-3"))
    }

    @Test fun `default is two lines until something is loaded`() {
        assertEquals(2, PlannerFocusLines.lines(PlannerLevel.Month))
    }

    @Test fun `adding a line stores it and stops at five`() = runBlocking {
        val store = FakeStore()
        val repo = PlannerFocusRepository(store)
        assertEquals(3, repo.add(PlannerLevel.Month))
        assertEquals("3", store.data["planner-focus-lines:month"])
        assertEquals(4, repo.add(PlannerLevel.Month))
        assertEquals(5, repo.add(PlannerLevel.Month))
        assertEquals(5, repo.add(PlannerLevel.Month))
        assertEquals("5", store.data["planner-focus-lines:month"])
        assertEquals(5, PlannerFocusLines.lines(PlannerLevel.Month))
        assertEquals(2, PlannerFocusLines.lines(PlannerLevel.Year))
        assertEquals(null, store.data["planner-focus-lines:year"])
    }

    @Test fun `load reports a change only when the numbers differ`() = runBlocking {
        val store = FakeStore(mutableMapOf("planner-focus-lines:quarter" to "4", "planner-focus-lines:year" to "garbage"))
        val repo = PlannerFocusRepository(store)
        assertTrue(repo.load())
        assertEquals(4, PlannerFocusLines.lines(PlannerLevel.Quarter))
        assertEquals(2, PlannerFocusLines.lines(PlannerLevel.Year))
        assertEquals(2, PlannerFocusLines.lines(PlannerLevel.Month))
        assertFalse(repo.load())
    }

    @Test fun `a failing store does not break loading`() = runBlocking {
        val broken = object : FocusStore {
            override suspend fun read(key: String): String? = throw IllegalStateException("no database")
            override suspend fun write(key: String, value: String) {}
        }
        assertFalse(PlannerFocusRepository(broken).load())
        assertEquals(2, PlannerFocusLines.lines(PlannerLevel.Month))
    }
}
