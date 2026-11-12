package com.ethran.notable.ui.viewmodels

import com.ethran.notable.data.db.Page
import com.ethran.notable.data.db.SQLITE_VARIABLE_CHUNK
import com.ethran.notable.data.db.TagPagePair
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TopicGroupingTest {

    private fun page(id: String) = Page(id = id)
    private fun ids(g: TopicGroup) = g.pages.map { it.id }

    @Test
    fun `groups annotation and page tags, keeping database page order`() {
        val pages = listOf(page("a"), page("b"), page("c"))
        val groups = buildTopicGroups(
            tags = listOf("x", "y"),
            tagPages = listOf(TagPagePair("x", "c"), TagPagePair("x", "a"), TagPagePair("y", "b")),
            pageTags = mapOf("b" to listOf("x")),
            pages = pages,
            manualOrder = emptyMap()
        )
        assertEquals(listOf("x", "y"), groups.map { it.tag })
        assertEquals(listOf("a", "b", "c"), ids(groups[0]))
        assertEquals(listOf("b"), ids(groups[1]))
    }

    @Test
    fun `page with the tag from both sources appears once`() {
        val groups = buildTopicGroups(
            listOf("x"), listOf(TagPagePair("x", "a")), mapOf("a" to listOf("x")),
            listOf(page("a")), emptyMap()
        )
        assertEquals(listOf("a"), ids(groups.single()))
    }

    @Test
    fun `ids without a page row are dropped and an unused tag gets an empty group`() {
        val groups = buildTopicGroups(
            listOf("x", "unused"), listOf(TagPagePair("x", "ghost"), TagPagePair("x", "a")), emptyMap(),
            listOf(page("a")), emptyMap()
        )
        assertEquals(listOf("a"), ids(groups[0]))
        assertEquals("unused", groups[1].tag)
        assertTrue(groups[1].pages.isEmpty())
    }

    @Test
    fun `manual order wins, unordered tags go last in original order`() {
        val groups = buildTopicGroups(
            listOf("a", "b", "c", "d"), emptyList(), emptyMap(), emptyList(),
            manualOrder = mapOf("c" to 0, "b" to 1)
        )
        assertEquals(listOf("c", "b", "a", "d"), groups.map { it.tag })
    }

    @Test
    fun `hidden tags are not built and their pages are not requested`() {
        val tagPages = listOf(TagPagePair("hidden", "h1"), TagPagePair("x", "a"))
        val pageTags = mapOf("h2" to listOf("hidden"), "a" to listOf("x"))
        val wanted = listOf("x")
        assertEquals(listOf("a"), pageIdsForTags(wanted, tagPages, pageTags))
        val groups = buildTopicGroups(wanted, tagPages, pageTags, listOf(page("a")), emptyMap())
        assertEquals(listOf("x"), groups.map { it.tag })
    }

    @Test
    fun `pageIdsForTags returns the union once`() {
        val ids = pageIdsForTags(
            listOf("x", "y"),
            listOf(TagPagePair("x", "a"), TagPagePair("y", "a"), TagPagePair("y", "b")),
            mapOf("c" to listOf("x"), "a" to listOf("y"))
        )
        assertEquals(listOf("a", "b", "c"), ids)
    }

    @Test
    fun `no tags gives no groups and no ids`() {
        assertTrue(buildTopicGroups(emptyList(), emptyList(), emptyMap(), emptyList(), emptyMap()).isEmpty())
        assertTrue(pageIdsForTags(emptyList(), emptyList(), emptyMap()).isEmpty())
    }

    @Test
    fun `chunk size stays below SQLite's 999 variable limit`() {
        assertTrue(SQLITE_VARIABLE_CHUNK in 1..998)
        val chunks = (1..2500).map { "t$it" }.chunked(SQLITE_VARIABLE_CHUNK)
        assertTrue(chunks.all { it.size <= SQLITE_VARIABLE_CHUNK })
        assertEquals(2500, chunks.sumOf { it.size })
        assertEquals(3, chunks.size)
    }
}
