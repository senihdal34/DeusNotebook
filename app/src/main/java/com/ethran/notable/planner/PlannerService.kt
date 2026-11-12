package com.ethran.notable.planner

import com.ethran.notable.data.AppRepository
import com.ethran.notable.data.db.Notebook
import com.ethran.notable.data.db.Page
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

/** True once the planner has been opened automatically in this app process (start-up only). */
object PlannerLaunch {
    @Volatile var done = false
}

/**
 * Keeps the planner notebook up to date: one handwritten page per week. They are ordinary pages, so the
 * editor's pen, eraser and page swipe work on them; the week tool in the editor moves to the previous or next week.
 */
@Singleton
class PlannerService @Inject constructor(private val appRepository: AppRepository) {
    data class Target(val bookId: String, val pageId: String)

    /** Opens the page of this week (and makes sure the next weeks exist). */
    suspend fun openCurrentWeek(today: LocalDate = LocalDate.now()): Target {
        val weeks = PlannerWeeks.weeksToEnsure(today)
        return ensureWeeks(weeks).getValue(weeks.first())
    }

    /** The page of the week starting on [monday], created when missing. */
    suspend fun ensureWeek(monday: LocalDate): Target = ensureWeeks(listOf(PlannerWeeks.mondayOf(monday))).getValue(PlannerWeeks.mondayOf(monday))

    /** The long to-do page of one day, created when missing (the week page exists too). */
    suspend fun ensureDay(day: LocalDate): Target = ensureWeeks(listOf(PlannerWeeks.mondayOf(day)), listOf(day)).getValue(day)

    /** The page of the month [date] is in, created when missing (with the next months, so swiping stays inside the notebook). */
    suspend fun ensureMonth(date: LocalDate): Target {
        val first = YearMonth.from(date)
        return ensureLevel(PlannerLevel.Month, (0..MONTHS_AHEAD).map { first.plusMonths(it.toLong()) }.map {
            Want(PlannerWeeks.monthBackground(it), PlannerNames.monthTitle(it), it.atDay(1))
        })
    }

    /** The page of the quarter [date] is in, created when missing (with the next quarters). */
    suspend fun ensureQuarter(date: LocalDate): Target {
        val first = PlannerWeeks.YearQuarter.of(date)
        return ensureLevel(PlannerLevel.Quarter, (0..QUARTERS_AHEAD).map { first.plusQuarters(it) }.map {
            Want(PlannerWeeks.quarterBackground(it.year, it.quarter), PlannerNames.quarterTitle(it.year, it.quarter), it.start)
        })
    }

    /** The page of the year [date] is in, created when missing (with the next years). */
    suspend fun ensureYear(date: LocalDate): Target =
        ensureLevel(PlannerLevel.Year, (0..YEARS_AHEAD).map { date.year + it }.map {
            Want(PlannerWeeks.yearBackground(it), PlannerNames.yearTitle(it), LocalDate.of(it, 1, 1))
        })

    private data class Want(val background: String, val title: String, val date: LocalDate)

    /**
     * Finds or creates the notebook of one level and its pages for [wants] (the first one is returned), in date order.
     * Same lock and the same create-on-demand rule as the week pages; only pages that are missing are created.
     */
    private suspend fun ensureLevel(level: PlannerLevel, wants: List<Want>): Target = withContext(NonCancellable) {
        mutex.withLock {
            val books = appRepository.bookRepository
            val pages = appRepository.pageRepository

            var book = findBook(PlannerNames.allLevelNotebookTitles(level))
            if (book == null) {
                book = Notebook(title = PlannerNames.levelNotebookTitle(level), defaultBackground = "blank")
                books.createEmpty(book)
            }
            var existing = book.pageIds.chunked(400).flatMap { pages.getByIds(it) }
            if (relocalize(book, existing, PlannerNames.levelNotebookTitle(level), PlannerNames.allLevelNotebookTitles(level))) {
                book = findBook(PlannerNames.allLevelNotebookTitles(level)) ?: book
                existing = book.pageIds.chunked(400).flatMap { pages.getByIds(it) }
            }

            val byBackground = existing.associateBy { it.background }.toMutableMap()
            val ids = book.pageIds.toMutableList()
            var changed = false
            var first: Page? = null
            for (want in wants) {
                val page = byBackground[want.background] ?: Page(
                    notebookId = book.id, background = want.background, backgroundType = "native",
                    createdAt = Date.from(want.date.atStartOfDay(ZoneId.systemDefault()).toInstant()), title = want.title
                ).also { pages.create(it); ids.add(it.id); byBackground[want.background] = it; changed = true }
                if (first == null) first = page
            }
            if (changed) {
                val background = byBackground.values.associate { it.id to it.background }
                val sorted = ids.sortedBy { id -> background[id]?.let(PlannerWeeks::sortKey) ?: Long.MAX_VALUE }
                books.update(book.copy(pageIds = sorted))
            }
            Target(book.id, requireNotNull(first).id)
        }
    }

    /** Returns targets keyed by Monday for the weeks and by date for the extra [days]. */
    private suspend fun ensureWeeks(mondays: List<LocalDate>, days: List<LocalDate> = emptyList()): Map<LocalDate, Target> = withContext(NonCancellable) {
        mutex.withLock {
            val books = appRepository.bookRepository
            val pages = appRepository.pageRepository

            var book = findBook()
            if (book == null) {
                book = Notebook(title = PlannerNames.notebookTitle(), defaultBackground = "blank")
                books.createEmpty(book)
            }
            val existing = book.pageIds.chunked(400).flatMap { pages.getByIds(it) }.toMutableList()
            val ids = book.pageIds.toMutableList()
            var changed = false

            // To-do pages of the first version: remove the ones nothing was written on.
            for (p in existing.toList()) {
                if (PlannerWeeks.parseTodo(p.background) == null) continue
                if (pages.getWithStrokeById(p.id).strokes.isEmpty() && pages.getWithImageById(p.id).images.isEmpty()) {
                    pages.delete(p.id); ids.remove(p.id); existing.remove(p); changed = true
                }
            }

            // The app language may have changed since the pages were made: rename the planner's own names (titles only).
            if (relocalize(book, existing)) book = findBook() ?: book

            val byBackground = existing.associateBy { it.background }.toMutableMap()
            val result = mutableMapOf<LocalDate, Target>()
            for (monday in mondays) {
                val bg = PlannerWeeks.weekBackground(monday)
                val page = byBackground[bg] ?: Page(
                    notebookId = book.id, background = bg, backgroundType = "native",
                    createdAt = Date.from(monday.atStartOfDay(ZoneId.systemDefault()).toInstant()),
                    title = PlannerNames.weekTitle(monday)
                ).also { pages.create(it); ids.add(it.id); byBackground[bg] = it; changed = true }
                result[monday] = Target(book.id, page.id)
            }
            for (day in days) {
                val bg = PlannerWeeks.dayBackground(day)
                val page = byBackground[bg] ?: Page(
                    notebookId = book.id, background = bg, backgroundType = "native",
                    createdAt = Date.from(day.atStartOfDay(ZoneId.systemDefault()).toInstant()),
                    title = PlannerNames.dayTitle(day)
                ).also { pages.create(it); ids.add(it.id); byBackground[bg] = it; changed = true }
                result[day] = Target(book.id, page.id)
            }
            if (changed) {
                val background = byBackground.values.associate { it.id to it.background }
                val sorted = ids.sortedBy { id -> background[id]?.let(PlannerWeeks::sortKey) ?: Long.MAX_VALUE }
                books.update(book.copy(pageIds = sorted))
            }
            result
        }
    }

    /** The planner notebook, whichever language its name was given in. */
    private suspend fun findBook(titles: List<String> = PlannerNames.allNotebookTitles()): Notebook? {
        val books = appRepository.bookRepository
        for (title in titles) books.findByTitle(title)?.let { return it }
        return null
    }

    /**
     * Gives the planner notebook and its pages the names of the current app language. Only names the app made are
     * changed: pages and strokes, and names typed by the user, are not touched. Returns true when anything changed.
     */
    private suspend fun relocalize(
        book: Notebook, pagesOfBook: List<Page>, title: String = PlannerNames.notebookTitle(),
        allTitles: List<String> = PlannerNames.allNotebookTitles()
    ): Boolean {
        var changed = false
        if (book.title != title && book.title in allTitles) {
            appRepository.bookRepository.update(book.copy(title = title))
            changed = true
        }
        for (page in pagesOfBook) {
            val newTitle = PlannerNames.renamed(page.background, page.title) ?: continue
            appRepository.pageRepository.updateTitle(page.id, newTitle)
            changed = true
        }
        return changed
    }

    /** Renames the planner after an app language change. Does nothing when there is no planner notebook yet. */
    suspend fun relocalize(): Unit = withContext(NonCancellable) {
        mutex.withLock {
            findBook()?.let { book ->
                relocalize(book, book.pageIds.chunked(400).flatMap { appRepository.pageRepository.getByIds(it) })
            }
            for (level in PlannerLevel.entries) {
                val all = PlannerNames.allLevelNotebookTitles(level)
                val book = findBook(all) ?: continue
                relocalize(book, book.pageIds.chunked(400).flatMap { appRepository.pageRepository.getByIds(it) }, PlannerNames.levelNotebookTitle(level), all)
            }
        }
    }

    private companion object {
        const val MONTHS_AHEAD = 3
        const val QUARTERS_AHEAD = 2
        const val YEARS_AHEAD = 2

        // One lock for every instance: the editor's week tool and Home both create pages.
        val mutex = Mutex()
    }
}
