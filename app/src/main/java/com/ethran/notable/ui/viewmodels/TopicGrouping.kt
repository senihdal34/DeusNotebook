package com.ethran.notable.ui.viewmodels

import com.ethran.notable.data.db.Page
import com.ethran.notable.data.db.TagPagePair

/**
 * Pure grouping used by [HomeViewModel]: builds the topic groups in memory from batched
 * query results, so a refresh needs two queries instead of two per tag.
 *
 * - [tags]: topics to build, in order (already trimmed, de-hidden, distinct).
 * - [tagPages]: (tag, page id) rows from the annotation `#tag` boxes.
 * - [pageTags]: page id -> tags attached from Home.
 * - [pages]: every page that exists for the union of page ids, in the order the database
 *   returned them. Each group keeps this order; ids with no page row are dropped.
 * - [manualOrder]: tag -> sort order; tags without one go last, ties keep [tags] order.
 */
internal fun buildTopicGroups(
    tags: List<String>,
    tagPages: List<TagPagePair>,
    pageTags: Map<String, List<String>>,
    pages: List<Page>,
    manualOrder: Map<String, Int>
): List<TopicGroup> {
    val idsByTag = HashMap<String, MutableSet<String>>()
    for ((tag, pageId) in tagPages) idsByTag.getOrPut(tag) { HashSet() }.add(pageId)
    for ((pageId, ts) in pageTags) for (tag in ts) idsByTag.getOrPut(tag) { HashSet() }.add(pageId)
    return tags.map { tag ->
        val ids = idsByTag[tag].orEmpty()
        TopicGroup(tag = tag, pages = if (ids.isEmpty()) emptyList() else pages.filter { it.id in ids })
    }.sortedBy { manualOrder[it.tag] ?: Int.MAX_VALUE }
}

/** Every page id any of [tags] points to, from either source, without duplicates. */
internal fun pageIdsForTags(
    tags: Collection<String>,
    tagPages: List<TagPagePair>,
    pageTags: Map<String, List<String>>
): List<String> {
    val wanted = tags.toHashSet()
    val ids = LinkedHashSet<String>()
    for ((tag, pageId) in tagPages) if (tag in wanted) ids.add(pageId)
    for ((pageId, ts) in pageTags) if (ts.any { it in wanted }) ids.add(pageId)
    return ids.toList()
}
