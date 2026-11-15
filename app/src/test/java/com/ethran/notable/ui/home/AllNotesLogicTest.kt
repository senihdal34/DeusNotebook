package com.ethran.notable.ui.home

import com.ethran.notable.data.db.Page
import java.util.Date
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AllNotesLogicTest {
    private fun page(n: Int, title: String = "Note $n", created: Long = n * 1000L) =
        Page(id = "p%03d".format(n), title = title, createdAt = Date(created))

    private fun pages(count: Int) = (1..count).map { page(it) }

    // ---- visibility of the More pill ----

    @Test fun pillHiddenWhenEveryNoteIsOnTheGrid() {
        assertFalse(showMoreNotesPill(total = 4, shownCards = 4))
        assertFalse(showMoreNotesPill(total = 0, shownCards = 0))
        assertFalse(showMoreNotesPill(total = 2, shownCards = 2))
    }

    @Test fun pillShownWhenNotesAreHiddenAndCountsThem() {
        assertTrue(showMoreNotesPill(total = 5, shownCards = 4))
        assertEquals(33, hiddenNotes(total = 37, shownCards = 4))
    }

    @Test fun hiddenCountNeverNegative() = assertEquals(0, hiddenNotes(total = 1, shownCards = 3))

    @Test fun gridSlotsMatchTheHomeLayout() {
        assertEquals(4, homeCardSlots(landscape = false))
        assertEquals(3, homeCardSlots(landscape = true))
        assertEquals(3, allNotesColumns(false))
        assertEquals(2, allNotesColumns(true))
    }

    // ---- rows ----

    @Test fun rowsAreFilledLeftToRightLastOneMayBeShort() {
        val rows = chunkRows((1..7).toList(), 3)
        assertEquals(listOf(listOf(1, 2, 3), listOf(4, 5, 6), listOf(7)), rows)
        assertTrue(chunkRows(emptyList<Int>(), 3).isEmpty())
    }

    // ---- paging ----

    /** A pager over a fake "table" with SQL LIMIT/OFFSET semantics. */
    private fun dbPager(table: () -> List<Page>, size: Int = 20) = AllNotesPager(
        count = { table().size },
        fetch = { limit, offset -> sortNewestFirst(table()).drop(offset).take(limit) },
        pageSize = size
    )

    @Test fun firstWindowHoldsTwentyNewestOfFortyFive() = runBlocking {
        val w = dbPager({ pages(45) }).first()
        assertEquals(20, w.items.size)
        assertEquals("p045", w.items.first().id)
        assertEquals("p026", w.items.last().id)
        assertEquals(45, w.total)
        assertTrue(w.hasMore)
        assertEquals(25, w.remaining)
    }

    @Test fun showMoreAppendsUntilTheEndWithoutDuplicates() = runBlocking {
        val pager = dbPager({ pages(45) })
        var w = pager.first()
        w = pager.more(w)
        assertEquals(40, w.items.size)
        w = pager.more(w)
        assertEquals(45, w.items.size)
        assertFalse(w.hasMore)
        assertEquals(45, w.items.map { it.id }.distinct().size)
        assertEquals("p001", w.items.last().id)
        assertEquals(w, pager.more(w))
    }

    @Test fun exactlyOnePageHasNoShowMore() = runBlocking {
        val w = dbPager({ pages(20) }).first()
        assertEquals(20, w.items.size)
        assertFalse(w.hasMore)
    }

    @Test fun emptyTableIsReadyAndEmpty() = runBlocking {
        val w = dbPager({ emptyList() }).first()
        assertTrue(w.ready)
        assertTrue(w.items.isEmpty())
        assertFalse(w.hasMore)
    }

    @Test fun reloadKeepsTheWindowSizeAndSeesAChange() = runBlocking {
        var table = pages(45)
        val pager = dbPager({ table })
        var w = pager.more(pager.first())
        table = table.filter { it.id != "p040" }
        w = pager.reload(w)
        assertEquals(40, w.items.size)
        assertEquals(44, w.total)
        assertFalse(w.items.any { it.id == "p040" })
    }

    @Test fun noteAddedMeanwhileDoesNotDuplicateOnShowMore() = runBlocking {
        var table = pages(30)
        val pager = dbPager({ table })
        var w = pager.first()
        table = table + page(31)
        w = pager.more(w)
        assertEquals(w.items.size, w.items.map { it.id }.distinct().size)
    }

    @Test fun equalCreationTimesStillPageStably() = runBlocking {
        val table = (1..45).map { page(it, created = 5000) }
        val pager = dbPager({ table })
        val w = pager.more(pager.more(pager.first()))
        assertEquals(45, w.items.map { it.id }.distinct().size)
    }

    @Test fun memoryPagerSortsNewestFirstAndPages() = runBlocking {
        val pager = memoryPager(pages(25).shuffled(java.util.Random(1)))
        val w = pager.first()
        assertEquals("p025", w.items.first().id)
        assertEquals(20, w.items.size)
        assertEquals(25, pager.more(w).items.size)
    }

    // ---- search ----

    @Test fun foldIgnoresCaseAccentsAndTurkishI() {
        assertEquals("toplanti notlari", foldForSearch("  TOPLANTI   Notları "))
        assertEquals("istanbul", foldForSearch("İstanbul"))
        assertEquals("cagri", foldForSearch("Çağrı"))
    }

    @Test fun everyWordMustMatchTitleOrTag() {
        assertTrue(matchesQuery("Toplantı notları", listOf("iş"), "toplanti is"))
        assertFalse(matchesQuery("Toplantı notları", listOf("iş"), "toplanti ev"))
        assertTrue(matchesQuery("x", listOf("work"), "#work"))
    }

    @Test fun blankQueryMatchesEverything() {
        assertTrue(matchesQuery("a", emptyList(), "   "))
        assertTrue(matchesQuery("a", emptyList(), "#"))
    }

    @Test fun filterKeepsNewestFirstAndOnlyMatches() {
        val all = listOf(page(1, "Alpha"), page(2, "Beta"), page(3, "alpha beta"))
        val tags = mapOf("p002" to listOf("alpha"))
        val out = filterNotes(all, "alpha", { it.title }, { tags[it.id].orEmpty() })
        assertEquals(listOf("p003", "p002", "p001"), out.map { it.id })
        assertEquals(listOf("p003", "p001"), filterNotes(all, "alpha", { it.title }, { emptyList() }).map { it.id })
        assertEquals(3, filterNotes(all, "", { it.title }, { emptyList() }).size)
    }
}
