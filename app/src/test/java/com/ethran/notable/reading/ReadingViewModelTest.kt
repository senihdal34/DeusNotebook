package com.ethran.notable.reading

import com.ethran.notable.reading.badges.BadgeStore
import com.ethran.notable.reading.badges.InMemoryBadgeStore
import com.ethran.notable.reading.chapters.ChapterResult
import com.ethran.notable.reading.chapters.NoChapters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class FakePrefs(var saved: Int = ReadingGoal.DEFAULT, var name: String = "") : ReadingPrefs {
    override fun readerName(): String = name
    val store = InMemoryBadgeStore()
    val goalWrites = mutableListOf<Int>()
    override fun goal(): Int = ReadingGoal.clamp(saved)
    override fun saveGoal(goal: Int) { saved = goal; goalWrites += goal }
    override fun badgeStore(): BadgeStore = store
}

private class FakeLibrary(
    var books: List<NrBook> = emptyList(),
    var events: List<NrEvent> = emptyList()
) : ReadingLibrary {
    var booksCalls = 0
    var syncedWith: List<NrBook>? = null
    /** Runs while the refresh is reading, i.e. between the start of the refresh and the data being built. */
    var duringRead: () -> Unit = {}

    override fun books(): List<NrBook> { booksCalls++; duringRead(); return books }
    override fun events(): List<NrEvent> = events
    override fun highlights(): List<NrHighlight> = emptyList()
    override fun syncCovers(books: List<NrBook>) { syncedWith = books }
    override fun coverDir(): File? = null
    override fun chapters(file: File): ChapterResult = ChapterResult.None(NoChapters.Unreadable)
}

@OptIn(ExperimentalCoroutinesApi::class)
class ReadingViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private var now = 1_000L

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    /** Runs what a refresh started (a standard dispatcher does not start a job until told to, as a real one starts it on another thread). */
    private fun ReadingViewModel.refreshNow() { refresh(); dispatcher.scheduler.advanceUntilIdle() }

    private fun vm(prefs: ReadingPrefs, library: ReadingLibrary) = ReadingViewModel(prefs, library, dispatcher) { now }

    private fun book(name: String) = NrBook(
        path = "/sdcard/Books/$name.epub", title = name, authors = listOf("A"), series = null,
        progressPage = 1, progressTotal = 10, lastAccess = 1L, addedAt = 1L, finished = false, hash = null
    )

    private fun event() = NrEvent(
        type = 1, time = 1L, docId = "d", md5 = null, durationMs = 1_000L, page = 1, progressPct = 1.0, sid = "s", path = null
    )

    @Test fun startsWithTheSavedGoalAndClampsIt() {
        assertEquals(30, vm(FakePrefs(30), FakeLibrary()).ui.value.data.goalBooks)
        assertEquals(ReadingGoal.MAX, vm(FakePrefs(10_000), FakeLibrary()).ui.value.data.goalBooks)
    }

    @Test fun theReaderNameFromThePrefsReachesTheBoard() {
        val library = FakeLibrary(books = listOf(book("a")))
        val vm = vm(FakePrefs(name = "Ada"), library)
        vm.refreshNow()
        assertEquals("Ada", vm.ui.value.data.name)
    }

    @Test fun setGoalIsClampedSavedAndShownWithoutARefresh() {
        val prefs = FakePrefs()
        val library = FakeLibrary()
        val vm = vm(prefs, library)
        vm.setGoal(0)
        assertEquals(ReadingGoal.MIN, vm.ui.value.data.goalBooks)
        vm.setGoal(25)
        assertEquals(25, vm.ui.value.data.goalBooks)
        assertEquals(listOf(ReadingGoal.MIN, 25), prefs.goalWrites)
        assertEquals(0, library.booksCalls)
    }

    @Test fun goalChangedDuringARunningRefreshIsKept() {
        val prefs = FakePrefs()
        val library = FakeLibrary(books = listOf(book("a")), events = listOf(event()))
        val vm = vm(prefs, library)
        library.duringRead = { vm.setGoal(40) }     // the user presses + while NeoReader is being read
        vm.refreshNow()
        assertTrue(vm.ui.value.loaded)
        assertEquals(40, vm.ui.value.data.goalBooks)
        assertEquals(40, prefs.saved)
    }

    @Test fun refreshWithAnEmptyLibraryKeepsThePreviousData() {
        val library = FakeLibrary(books = listOf(book("a"), book("b")), events = listOf(event()))
        val vm = vm(FakePrefs(), library)
        vm.refreshNow()
        val before = vm.ui.value
        assertTrue(before.loaded)
        assertTrue(before.sourceAvailable)
        assertEquals(1_000L, before.data.now)

        library.books = emptyList()
        now = 2_000L
        vm.refreshNow()
        assertEquals(2, library.booksCalls)
        assertEquals(before, vm.ui.value)           // not replaced, not flagged unavailable
    }

    @Test fun firstRefreshWithAnEmptyLibraryFlagsTheSourceUnavailable() {
        val library = FakeLibrary()
        val vm = vm(FakePrefs(), library)
        assertTrue(vm.ui.value.sourceAvailable)
        assertFalse(vm.ui.value.loaded)
        vm.refreshNow()
        assertTrue(vm.ui.value.loaded)
        assertFalse(vm.ui.value.sourceAvailable)
        assertNull(vm.ui.value.badges)
    }

    @Test fun sourceIsAvailableAgainOnceBooksComeBack() {
        val library = FakeLibrary()
        val vm = vm(FakePrefs(), library)
        vm.refreshNow()
        assertFalse(vm.ui.value.sourceAvailable)
        library.books = listOf(book("a"))
        vm.refreshNow()
        assertTrue(vm.ui.value.sourceAvailable)
    }

    @Test fun aRefreshCalledWhileOneRunsIsDropped() {
        val library = FakeLibrary(books = listOf(book("a")))
        val vm = vm(FakePrefs(), library)
        library.duringRead = { vm.refresh() }       // Home resumes while the tab already asked for a refresh
        vm.refreshNow()
        assertEquals(1, library.booksCalls)
    }

    @Test fun coversAreSyncedFromTheBooksJustRead() {
        val library = FakeLibrary(books = listOf(book("a")))
        vm(FakePrefs(), library).refreshNow()
        assertEquals(library.books, library.syncedWith)
    }

    @Test fun badgesNeedBothTheLibraryAndTheReadingLog() {
        val prefs = FakePrefs()
        val library = FakeLibrary(books = listOf(book("a")))
        val vm = vm(prefs, library)
        vm.refreshNow()
        assertNull(vm.ui.value.badges)
        assertEquals(0, prefs.store.saves)          // the store is not filled from half the data

        library.events = listOf(event())
        vm.refreshNow()
        assertNotNull(vm.ui.value.badges)
        assertEquals(1, prefs.store.saves)
    }
}
