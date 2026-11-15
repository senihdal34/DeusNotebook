package com.ethran.notable.ui.home

import com.ethran.notable.data.db.Page
import java.text.Normalizer
import java.util.Locale

/**
 * Pure logic of the Home "All notes" list: when the "More notes" pill shows, how a long list is cut into
 * windows of [ALL_NOTES_PAGE_SIZE], and how the search box filters. No Android types, so JVM tests cover it.
 */

/** Notes added to the list by each "Show more" tap. A button, not an infinite scroll: e-ink has no smooth scrolling. */
const val ALL_NOTES_PAGE_SIZE = 20

/** Note cards the normal Home grid shows: the lead card plus two (landscape) or three (portrait) more. */
fun homeCardSlots(landscape: Boolean): Int = if (landscape) 3 else 4

/** Columns of the All notes grid. */
fun allNotesColumns(landscape: Boolean): Int = if (landscape) 2 else 3

/** Notes the Home grid does not show. [total] is every note of the current topic, [shownCards] the cards on screen. */
fun hiddenNotes(total: Int, shownCards: Int): Int = (total - shownCards).coerceAtLeast(0)

/** The "More notes" pill is only offered when at least one note is not already on the Home grid. */
fun showMoreNotesPill(total: Int, shownCards: Int): Boolean = hiddenNotes(total, shownCards) > 0

/** Notes still to be revealed by "Show more". */
fun remainingNotes(loaded: Int, total: Int): Int = (total - loaded).coerceAtLeast(0)

/** Groups [items] into rows of [columns] cards; the last row may be short. */
fun <T> chunkRows(items: List<T>, columns: Int): List<List<T>> = items.chunked(columns.coerceAtLeast(1))

/** Newest first, with the id as tie-break: the same order as the database query. */
fun sortNewestFirst(pages: List<Page>): List<Page> =
    pages.sortedWith(compareByDescending<Page> { it.createdAt }.thenByDescending { it.id })

// ---------- search ----------

/**
 * Text prepared for matching: Turkish dotted/dotless i and accents are folded (ı İ ç ş ğ ö ü -> i i c s g o u),
 * case is dropped and runs of spaces collapse, so "toplanti" finds "Toplantı".
 */
fun foldForSearch(text: String): String {
    val noDots = text.replace('İ', 'i').replace('ı', 'i')
    val decomposed = Normalizer.normalize(noDots.lowercase(Locale.ROOT), Normalizer.Form.NFD)
    return decomposed.filter { Character.getType(it) != Character.NON_SPACING_MARK.toInt() }
        .replace(Regex("\\s+"), " ").trim()
}

/** A leading '#' is allowed in a query ("#work" finds the tag "work"). */
private fun queryTokens(query: String): List<String> =
    foldForSearch(query).split(' ').map { it.removePrefix("#") }.filter { it.isNotEmpty() }

/** Every word of [query] has to appear in the title or in one of the tags. An empty query matches everything. */
fun matchesQuery(title: String, tags: List<String>, query: String): Boolean {
    val tokens = queryTokens(query)
    if (tokens.isEmpty()) return true
    val haystack = (listOf(title) + tags).map(::foldForSearch)
    return tokens.all { token -> haystack.any { token in it } }
}

/**
 * Notes matching [query] (title or tag text), newest first. [titleOf] gives the text shown on the card
 * (cleaned of broken characters), [tagsOf] the tags of a note.
 */
fun filterNotes(
    pages: List<Page>, query: String, titleOf: (Page) -> String, tagsOf: (Page) -> List<String>
): List<Page> {
    val sorted = sortNewestFirst(pages)
    if (queryTokens(query).isEmpty()) return sorted
    return sorted.filter { matchesQuery(titleOf(it), tagsOf(it), query) }
}

// ---------- paging ----------

/** What the list shows now: the notes loaded so far and how many exist in all. */
data class AllNotesWindow(
    val items: List<Page> = emptyList(), val total: Int = 0, val ready: Boolean = false
) {
    val hasMore: Boolean get() = items.size < total
    val remaining: Int get() = remainingNotes(items.size, total)
}

/**
 * Loads the list window by window. [count] and [fetch] are one source: the paged database query for the plain list,
 * or a list already in memory (a topic, search results). [fetch] has the semantics of SQL `LIMIT limit OFFSET offset`.
 */
class AllNotesPager(
    private val count: suspend () -> Int,
    private val fetch: suspend (limit: Int, offset: Int) -> List<Page>,
    private val pageSize: Int = ALL_NOTES_PAGE_SIZE
) {
    /** The first window. */
    suspend fun first(): AllNotesWindow = window(fetch(pageSize, 0), count())

    /** The next [pageSize] notes appended; a note that appears twice (list changed meanwhile) is kept once. */
    suspend fun more(current: AllNotesWindow): AllNotesWindow {
        if (!current.hasMore) return current
        val next = fetch(pageSize, current.items.size)
        return window((current.items + next).distinctBy { it.id }, count())
    }

    /** Reads the same window again (after a note was deleted, tagged or opened): as many notes as before, from the top. */
    suspend fun reload(current: AllNotesWindow): AllNotesWindow =
        window(fetch(maxOf(current.items.size, pageSize), 0).distinctBy { it.id }, count())

    private fun window(items: List<Page>, total: Int) = AllNotesWindow(items, maxOf(total, items.size), ready = true)
}

/** A pager over a list already in memory (a topic's notes, search results), newest first. */
fun memoryPager(pages: List<Page>, pageSize: Int = ALL_NOTES_PAGE_SIZE): AllNotesPager {
    val sorted = sortNewestFirst(pages)
    return AllNotesPager(
        count = { sorted.size },
        fetch = { limit, offset -> sorted.drop(offset).take(limit) },
        pageSize = pageSize
    )
}
