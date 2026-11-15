package com.ethran.notable.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ethran.notable.data.db.AnnotationRepository
import com.ethran.notable.data.db.Page
import com.ethran.notable.data.db.PageRepository
import com.ethran.notable.data.db.PageTagRepository
import com.ethran.notable.io.ExportedExcerpts
import com.ethran.notable.io.SyncState
import com.ethran.notable.io.TextSanitizer
import com.ethran.notable.data.db.TagPriority
import com.ethran.notable.data.db.TagPriorityDao
import com.ethran.notable.data.datastore.GlobalAppSettings
import com.ethran.notable.io.ThumbnailGenerator
import com.ethran.notable.io.VaultTagScanner
import com.ethran.notable.planner.PlannerService
import com.ethran.notable.ui.home.AllNotesPager
import com.ethran.notable.ui.home.AllNotesWindow
import com.ethran.notable.ui.home.filterNotes
import com.ethran.notable.ui.home.memoryPager
import java.io.File
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

enum class HomeSection { Notes, Pinned, Reading }

/** Remembers the open Home tab while the app process lives (Home is rebuilt when coming back from a note). */
object HomeSectionMemory { var last: HomeSection? = null }

data class TopicGroup(
    val tag: String,
    val pages: List<Page>
)

data class HomeUiState(
    val pinnedPages: List<Page> = emptyList(),
    val recentPages: List<Page> = emptyList(),
    val topicGroups: List<TopicGroup> = emptyList(),
    val isLoading: Boolean = true,
    /** Bumped whenever a missing thumbnail has been created, so cards reload their image. */
    val thumbRevision: Int = 0,
    /** Page id -> tags attached from Home (stored apart from the #tag boxes drawn in a note). */
    val pageTags: Map<String, List<String>> = emptyMap(),
    /** Page id -> first lines of its exported note (only pages that have been exported). */
    val excerpts: Map<String, String> = emptyMap(),
    /** Every normal note (favourites included, planner pages not): the count behind "More notes". */
    val totalNotes: Int = 0
)

/** The "All notes" list on Home: closed, or open with the notes loaded so far and the search text. */
data class AllNotesUiState(
    val open: Boolean = false,
    val query: String = "",
    val window: AllNotesWindow = AllNotesWindow(),
    /** A "Show more" load is running. */
    val busy: Boolean = false
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val pageRepository: PageRepository,
    private val annotationRepository: AnnotationRepository,
    private val tagPriorityDao: TagPriorityDao,
    private val thumbnailGenerator: ThumbnailGenerator,
    private val plannerService: PlannerService,
    private val pageTagRepository: PageTagRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _section = MutableStateFlow(
        HomeSectionMemory.last ?: HomeSection.Notes
    )
    val section: StateFlow<HomeSection> = _section.asStateFlow()
    fun selectSection(s: HomeSection) {
        HomeSectionMemory.last = s; _section.value = s
        if (s != HomeSection.Notes) closeAllNotes()
    }

    /** Creates the planner pages for this and the coming weeks if needed, then hands back the current week. */
    fun openPlanner(onReady: (bookId: String, pageId: String) -> Unit) {
        viewModelScope.launch {
            val target = try {
                withContext(Dispatchers.IO) { plannerService.openCurrentWeek() }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) { null }
            if (target != null) onReady(target.bookId, target.pageId)
        }
    }

    /** Topic the Home list is filtered by; null shows the latest notes. */
    private val _selectedTopic = MutableStateFlow<String?>(null)
    val selectedTopic: StateFlow<String?> = _selectedTopic.asStateFlow()

    fun selectTopic(tag: String?) {
        _selectedTopic.value = tag
        reloadAllNotes(keepWindow = false)
    }

    // ---------- All notes list ----------

    private val _allNotes = MutableStateFlow(AllNotesUiState())
    val allNotes: StateFlow<AllNotesUiState> = _allNotes.asStateFlow()
    private var allJob: kotlinx.coroutines.Job? = null

    /** Every normal note, read once when a search starts and filtered in memory; dropped whenever Home reloads. */
    @Volatile private var searchPool: List<Page>? = null

    fun openAllNotes() {
        _allNotes.value = AllNotesUiState(open = true)
        reloadAllNotes(keepWindow = false)
    }

    fun closeAllNotes() {
        allJob?.cancel()
        _allNotes.value = AllNotesUiState()
    }

    fun setNotesSearch(query: String) {
        _allNotes.update { it.copy(query = query) }
        reloadAllNotes(keepWindow = false, debounceMs = 250)
    }

    fun showMoreNotes() {
        val current = _allNotes.value
        if (!current.open || !current.window.hasMore || current.busy) return
        _allNotes.update { it.copy(busy = true) }
        allJob?.cancel()
        allJob = viewModelScope.launch {
            try {
                val (next, extra) = withContext(Dispatchers.IO) {
                    val w = pagerForAllNotes(current.query).more(current.window)
                    w to excerptsFor(w.items)
                }
                if (extra.isNotEmpty()) _uiState.update { it.copy(excerpts = it.excerpts + extra) }
                _allNotes.update { it.copy(window = next, busy = false) }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _allNotes.update { it.copy(busy = false) }
            }
        }
    }

    /** Where the list reads from: the paged database query, or a list in memory for a topic and for a search. */
    private suspend fun pagerForAllNotes(query: String): AllNotesPager {
        val state = _uiState.value
        val topic = _selectedTopic.value
        val tagsByPage = HashMap<String, MutableList<String>>()
        state.pageTags.forEach { (id, tags) -> tagsByPage.getOrPut(id) { ArrayList() }.addAll(tags) }
        state.topicGroups.forEach { g -> g.pages.forEach { tagsByPage.getOrPut(it.id) { ArrayList() }.add(g.tag) } }
        val titleOf = { p: Page -> TextSanitizer.cleanLine(p.title) }
        val tagsOf = { p: Page -> tagsByPage[p.id].orEmpty() }
        if (topic != null) {
            val pages = state.topicGroups.firstOrNull { it.tag == topic }?.pages.orEmpty()
            return memoryPager(filterNotes(pages, query, titleOf, tagsOf))
        }
        if (query.isBlank()) {
            return AllNotesPager(
                count = { pageRepository.countNotes() },
                fetch = { limit, offset -> pageRepository.getNotesPage(limit, offset) }
            )
        }
        val pool = searchPool ?: pageRepository.getAllNotesList().also { searchPool = it }
        return memoryPager(filterNotes(pool, query, titleOf, tagsOf))
    }

    private fun reloadAllNotes(keepWindow: Boolean, debounceMs: Long = 0) {
        allJob?.cancel()
        if (!_allNotes.value.open) return
        allJob = viewModelScope.launch {
            try {
                if (debounceMs > 0) delay(debounceMs)
                val current = _allNotes.value
                val (next, extra) = withContext(Dispatchers.IO) {
                    val pager = pagerForAllNotes(current.query)
                    val w = if (keepWindow && current.window.ready) pager.reload(current.window) else pager.first()
                    w to excerptsFor(w.items)
                }
                if (extra.isNotEmpty()) _uiState.update { it.copy(excerpts = it.excerpts + extra) }
                _allNotes.update { it.copy(window = next, busy = false) }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _allNotes.update { it.copy(busy = false) }
            }
        }
    }

    /**
     * Excerpts of All notes cards that the Home load did not cover (it reads only the pinned, recent and topic notes).
     * Same source as Home: the exported Markdown when there is one, else the text kept by a recognition on Back.
     */
    private fun excerptsFor(pages: List<Page>): Map<String, String> {
        val known = _uiState.value.excerpts
        val missing = pages.filter { it.id !in known }
        if (missing.isEmpty()) return emptyMap()
        return try {
            ExportedExcerpts.load(ExportedExcerpts.inboxDir(GlobalAppSettings.current.obsidianInboxPath), missing)
        } catch (e: Exception) { emptyMap() }
    }

    private val thumbRequested = HashSet<String>()

    /** Thumbnails of the cards that are on screen: made once, off the main thread, never for cards that were not shown. */
    fun ensureThumbnails(pageIds: List<String>) {
        val todo = synchronized(thumbRequested) { pageIds.filter { thumbRequested.add(it) } }
        if (todo.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            // One recomposition for the whole row, not one per thumbnail (each is a screen refresh on e-ink).
            var made = false
            for (id in todo) if (thumbnailGenerator.ensure(id)) made = true
            if (made) _uiState.update { it.copy(thumbRevision = it.thumbRevision + 1) }
        }
    }

    private val _inputTag = MutableStateFlow("")
    val inputTag: StateFlow<String> = _inputTag.asStateFlow()

    init {
        loadData()
        // A note left by Back (or Save & Exit) is recognised in the background: once its title and excerpt are stored,
        // reload Home (and the All notes list in place) so they show without leaving and re-entering Home. Collected
        // here, not in a composable, so it also runs while the editor is on top of Home.
        viewModelScope.launch { SyncState.finished.collect { loadData() } }
    }

    fun setInputTag(value: String) {
        _inputTag.value = value
    }

    /** The load in flight: a new one replaces it, so a slow older load can never publish after a newer one. */
    private var loadJob: kotlinx.coroutines.Job? = null

    private fun loadData() {
        synchronized(this) {
            loadJob?.cancel()
            loadJob = launchLoad()
        }
    }

    private fun launchLoad(): kotlinx.coroutines.Job {
        return viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    // Single-shot reads
                    val pinned = try { pageRepository.getPinnedPagesList() } catch (e: Exception) { emptyList() }
                    val recent = try { pageRepository.getRecentPagesList(10) } catch (e: Exception) { emptyList() }
                    val totalNotes = try { pageRepository.countNotes() } catch (e: Exception) { 0 }

                    // Combine annotation tags + manually added tags (TagPriority)
                    val annotTags = try { annotationRepository.getDistinctTags() } catch (e: Exception) { emptyList() }
                    val manualOrder = try { tagPriorityDao.getAllSorted() } catch (e: Exception) { emptyList() }
                    val manualTags = manualOrder.map { it.tagName }
                    val pageTags = try { pageTagRepository.all() } catch (e: Exception) { emptyMap() }
                    val vaultTags = try { VaultTagScanner.cachedTags } catch (e: Exception) { emptyList() }
                    val hidden = try { pageTagRepository.hidden() } catch (e: Exception) { emptySet() }
                    val allTags = (annotTags + manualTags + pageTags.values.flatten() + vaultTags)
                        .map { it.trim() }.filter { it.isNotEmpty() && it !in hidden }.distinct()
                    val sortedTags = manualOrder.associate { it.tagName to it.sortOrder }

                    // Two batched reads (chunked inside the repositories) instead of two queries per tag; grouped in memory.
                    val tagPages = try { annotationRepository.getTagPagePairs(allTags) } catch (e: Exception) { emptyList() }
                    val topicPageIds = pageIdsForTags(allTags, tagPages, pageTags)
                    val topicPages = try { pageRepository.getByIds(topicPageIds) } catch (e: Exception) { emptyList() }
                    val groups = buildTopicGroups(allTags, tagPages, pageTags, topicPages, sortedTags)

                    val allPages = (pinned + recent + groups.flatMap { it.pages }).distinctBy { it.id }
                    val excerpts = try {
                        ExportedExcerpts.load(ExportedExcerpts.inboxDir(GlobalAppSettings.current.obsidianInboxPath), allPages)
                    } catch (e: Exception) { emptyMap() }
                    if (_selectedTopic.value != null && groups.none { it.tag == _selectedTopic.value }) _selectedTopic.value = null
                    _uiState.update {
                        it.copy(
                            pinnedPages = pinned, recentPages = recent, topicGroups = groups,
                            isLoading = false, pageTags = pageTags, excerpts = excerpts, totalNotes = totalNotes
                        )
                    }
                    searchPool = null
                    reloadAllNotes(keepWindow = true)
                    generateMissingThumbnails(allPages.map { it.id })
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    private var thumbJob: kotlinx.coroutines.Job? = null

    private fun generateMissingThumbnails(pageIds: List<String>) {
        thumbJob?.cancel()
        thumbJob = viewModelScope.launch(Dispatchers.IO) {
            for (id in pageIds) {
                if (thumbnailGenerator.ensure(id)) {
                    _uiState.update { it.copy(thumbRevision = it.thumbRevision + 1) }
                }
            }
        }
    }

    fun addTag(tagName: String) {
        val cleaned = TextSanitizer.cleanLine(tagName)
        if (cleaned.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                pageTagRepository.unhide(cleaned)
                tagPriorityDao.upsert(TagPriority(tagName = cleaned, sortOrder = 0))
                _inputTag.value = ""
                loadData()
            } catch (e: Exception) { /* ignore */ }
        }
    }

    /** Deletes a topic: off every note, out of the topic list. The notes stay. */
    fun removeTag(tagName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                tagPriorityDao.delete(tagName)
                pageTagRepository.deleteTopic(tagName)
                loadData()
            } catch (e: Exception) { /* ignore */ }
        }
    }

    fun togglePin(pageId: String, currentPinned: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            pageRepository.setPinned(pageId, !currentPinned)
            val pinned = pageRepository.getPinnedPagesList()
            val recent = pageRepository.getRecentPagesList(10)
            _uiState.update { it.copy(pinnedPages = pinned, recentPages = recent) }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            loadData()
        }
    }

    // ---------- note actions: tags and delete ----------

    /** Adds the tag to the note, or removes it when the note already has it. */
    fun toggleTag(pageId: String, tag: String) {
        val clean = PageTagRepository.normalize(tag)
        if (clean.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            try { pageTagRepository.unhide(clean) } catch (e: Exception) { return@launch }
            val current = try { pageTagRepository.get(pageId) } catch (e: Exception) { return@launch }
            val next = if (clean in current) current - clean else current + clean
            try { pageTagRepository.set(pageId, next) } catch (e: Exception) { return@launch }
            loadData()
        }
    }

    /** Deletes the note with its strokes, tags and cached thumbnail. The exported Markdown file is not touched. */
    fun deletePage(pageId: String, thumbDir: File) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                pageRepository.delete(pageId)
                pageTagRepository.clear(pageId)
                File(thumbDir, pageId).delete()
            } catch (e: Exception) { return@launch }
            loadData()
        }
    }
}