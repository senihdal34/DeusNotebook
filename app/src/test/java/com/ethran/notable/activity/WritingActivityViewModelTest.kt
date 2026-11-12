package com.ethran.notable.activity

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class WritingActivityViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private var today = LocalDate.of(2026, 11, 12)

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private fun vm(source: FakeSource = FakeSource(), prefs: FakePrefs = FakePrefs()) =
        WritingActivityViewModel(WritingActivityRepository(source, FakeStore(), dispatcher), prefs, dispatcher) { today }

    private fun idle() = dispatcher.scheduler.advanceUntilIdle()

    @Test fun `starts with an empty grid that is not yet loaded`() {
        val v = vm()
        assertFalse(v.ui.value.loaded)
        assertTrue(v.ui.value.view.isEmpty)
        assertEquals(ActivityPeriod.DEFAULT, v.ui.value.period)
    }

    @Test fun `refresh loads the counts and the year chips`() {
        val v = vm(FakeSource(mapOf("2026-11-11" to 9, "2025-06-01" to 8, "2025-06-02" to 8)))
        v.refresh(); idle()
        val ui = v.ui.value
        assertTrue(ui.loaded)
        assertEquals(listOf(2026, 2025), ui.years)
        assertEquals(1, ui.view.daysWithInk)
        assertEquals(1, ui.view.currentStreak)   // today has no ink yet, yesterday does
    }

    @Test fun `no strokes at all is loaded and empty`() {
        val v = vm()
        v.refresh(); idle()
        assertTrue(v.ui.value.loaded && v.ui.value.view.isEmpty)
        assertEquals(emptyList(), v.ui.value.years)
    }

    @Test fun `choosing a period is remembered and changes the view`() {
        val prefs = FakePrefs()
        val v = vm(FakeSource(mapOf("2026-02-01" to 9, "2025-06-01" to 9)), prefs)
        v.refresh(); idle()
        v.selectPeriod(ActivityPeriod.Year(2025)); idle()
        assertEquals(ActivityPeriod.Year(2025), v.ui.value.period)
        assertEquals(ActivityPeriod.Year(2025), prefs.saved)
        assertEquals(1, v.ui.value.view.daysWithInk)
        assertEquals(null, v.ui.value.view.currentStreak)
        v.selectPeriod(ActivityPeriod.Months(3)); idle()
        assertEquals(0, v.ui.value.view.daysWithInk)
    }

    @Test fun `the saved period is used on the next start`() {
        val v = vm(FakeSource(mapOf("2026-02-01" to 9)), FakePrefs(ActivityPeriod.Months(6)))
        assertEquals(ActivityPeriod.Months(6), v.ui.value.period)
    }

    @Test fun `a saved year without data falls back to 12 months`() {
        val v = vm(FakeSource(mapOf("2026-02-01" to 9)), FakePrefs(ActivityPeriod.Year(2019)))
        v.refresh(); idle()
        assertEquals(ActivityPeriod.DEFAULT, v.ui.value.period)
    }

    @Test fun `a new day moves today on the next refresh`() {
        val source = FakeSource(mapOf("2026-11-12" to 9))
        val v = vm(source)
        v.refresh(); idle()
        assertEquals(1, v.ui.value.view.currentStreak)
        today = today.plusDays(2)
        v.refresh(); idle()
        assertEquals(0, v.ui.value.view.currentStreak)
    }

    @Test fun `a failing refresh keeps the last state`() {
        val source = FakeSource(mapOf("2026-11-12" to 9))
        val v = vm(source)
        v.refresh(); idle()
        source.failing = true
        v.refresh(); idle()
        assertEquals(1, v.ui.value.view.daysWithInk)
        assertTrue(v.ui.value.loaded)
    }

    @Test fun `resume without new strokes reuses the memory copy`() {
        val source = FakeSource(mapOf("2026-11-12" to 9))
        val v = vm(source)
        v.refresh(); idle(); v.refresh(); idle(); v.refresh(); idle()
        assertEquals(1, source.countCalls)
    }
}
