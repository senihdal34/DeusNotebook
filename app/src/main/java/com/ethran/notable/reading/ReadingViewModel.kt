package com.ethran.notable.reading

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import com.ethran.notable.reading.chapters.ChapterRepository
import com.ethran.notable.reading.chapters.ChapterState
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.ethran.notable.reading.badges.BadgeBoard
import com.ethran.notable.reading.badges.BadgeEngine
import com.ethran.notable.reading.badges.BadgeFacts
import java.io.File
import javax.inject.Inject

data class ReadingUi(
    val data: ReadingData = ReadingData.Empty,
    val loaded: Boolean = false,
    /** False when NeoReader's library could not be read (not installed, or its data changed). */
    val sourceAvailable: Boolean = true,
    /** The badges for this reading (null until there is a reading log to work them out from). */
    val badges: BadgeBoard? = null,
    /** The chapters of the book on top of the board (the Next stop box), read from its file after the board is up. */
    val chapters: ChapterState = ChapterState.Idle
)

/** Holds no Android context: the device-facing work lives in [ReadingPrefs] and [ReadingLibrary], which tests replace with fakes. */
@HiltViewModel
class ReadingViewModel internal constructor(
    private val prefs: ReadingPrefs,
    private val library: ReadingLibrary,
    private val io: CoroutineDispatcher,
    private val clock: () -> Long
) : ViewModel() {
    @Inject constructor(prefs: ReadingPrefs, library: ReadingLibrary) : this(prefs, library, Dispatchers.IO, System::currentTimeMillis)

    /** Written on the main thread (setGoal), read on the IO thread of a running refresh. */
    @Volatile private var goal = prefs.goal()

    private val _ui = MutableStateFlow(ReadingUi(ReadingData.Empty.copy(goalBooks = goal)))
    val ui: StateFlow<ReadingUi> = _ui.asStateFlow()

    private var chaptersJob: Job? = null

    private val refreshes = OneAtATime()

    /**
     * Reads NeoReader's data off the main thread; the last data stays on screen while it loads or if reading fails.
     * A call while a refresh runs is dropped: opening the tab and resuming Home both ask for one at the same moment.
     */
    fun refresh() {
        refreshes.launch(viewModelScope, io) {
            val books = library.books()
            val events = library.events()
            val highlights = library.highlights()
            Log.i(TAG, "refresh: books=${books.size} events=${events.size} highlights=${highlights.size}")
            library.syncCovers(books)
            val coverDir = library.coverDir()
            if (books.isEmpty() && _ui.value.loaded) return@launch   // keep what we had
            val data = ReadingStats.build(books, events, highlights, clock(), goalBooks = goal, coverDir = coverDir, name = prefs.readerName())
            // Badges need the reading log as well as the library: without it the store would be filled from half the data.
            val badges = if (books.isNotEmpty() && events.isNotEmpty()) {
                runCatching { BadgeEngine.evaluate(BadgeFacts.from(data, ReadingInsights.today(data)), prefs.badgeStore(), data.now) }
                    .onFailure { Log.w(TAG, "badges failed: ${it.javaClass.simpleName}: ${it.message}") }.getOrNull()
            } else null
            // The goal is read again here: a change made while the data was being built must not be overwritten by the old one.
            _ui.update { ReadingUi(data.copy(goalBooks = goal), loaded = true, sourceAvailable = books.isNotEmpty(), badges = badges ?: it.badges, chapters = it.chapters) }
            loadChapters(data.continueReading.firstOrNull())
        }
    }

    /**
     * Reads the chapters of the book on top (the board's Next stop box) from its file, off the main thread, once per book:
     * the result is cached on disk by path, size and modification time. The board shows its quiet "looking up" text meanwhile.
     */
    private fun loadChapters(book: RBook?) {
        if (book == null) { chaptersJob?.cancel(); _ui.update { it.copy(chapters = ChapterState.Idle) }; return }
        if (_ui.value.chapters.bookIdOrNull == book.id) return          // already loaded, loading, or known to have none
        chaptersJob?.cancel()
        _ui.update { it.copy(chapters = ChapterState.Loading(book.id)) }
        chaptersJob = viewModelScope.launch(io) {
            val result = library.chapters(File(book.path))
            Log.i(TAG, "chapters: " + when (result) {
                is com.ethran.notable.reading.chapters.ChapterResult.Found -> "${result.map.entries.size} entries (${result.map.source})"
                is com.ethran.notable.reading.chapters.ChapterResult.None -> "none, ${result.reason.short}"
            })
            _ui.update { if (it.chapters.bookIdOrNull == book.id) it.copy(chapters = ChapterRepository.stateOf(book.id, result)) else it }
        }
    }

    /** Changes the yearly goal: saved right away, and the board shows it without reading everything again. */
    fun setGoal(books: Int) {
        goal = ReadingGoal.clamp(books)
        prefs.saveGoal(goal)
        _ui.update { it.copy(data = it.data.copy(goalBooks = goal)) }
    }

    private companion object {
        const val TAG = "ReadingViewModel"
    }
}
