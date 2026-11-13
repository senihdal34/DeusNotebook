package com.ethran.notable.planner

import com.ethran.notable.data.db.Stroke

/**
 * In-memory store for the planner sync and layout migration tests. In the default mode every page counts as already
 * migrated (the sync tests are about merging); [strict] makes the markers real, so a page only counts once it was
 * marked or migrated.
 */
internal open class FakePlannerStore(vararg strokes: Stroke) : PlannerSyncStore {
    val all = LinkedHashMap<String, Stroke>().apply { strokes.forEach { put(it.id, it) } }
    val kv = HashMap<String, String>()
    var applied = 0
    var strict = false
    val marked = HashSet<String>()
    /** Sizes of the stroke chunks written by each migration commit. */
    val commits = ArrayList<List<Int>>()
    var backups = 0
    var backupFails = false

    override suspend fun strokes(pageId: String) = all.values.filter { it.pageId == pageId }
    override suspend fun mirrored(key: String) = kv[key]
    override suspend fun apply(delete: List<String>, insert: List<Stroke>, update: List<Stroke>, key: String, mirrored: String) {
        applied++
        delete.forEach { all.remove(it) }
        update.forEach { require(all.containsKey(it.id)) { "update of a missing stroke" }; all[it.id] = it }
        insert.forEach { require(!all.containsKey(it.id)) { "duplicate id ${it.id}" }; all[it.id] = it }
        kv[key] = mirrored
    }

    override suspend fun layoutMarked(pageId: String) = !strict || pageId in marked
    override suspend fun markLayout(pageId: String) { marked += pageId }
    override suspend fun commitMigration(pageId: String, updated: List<List<Stroke>>) {
        if (pageId in marked) return
        updated.flatten().forEach { require(all.containsKey(it.id)) { "update of a missing stroke" }; all[it.id] = it }
        kv.keys.removeAll { it.startsWith(PlannerSync.KEY_PREFIX) && pageId in it }
        marked += pageId
        commits += updated.map { it.size }
    }
    override suspend fun backupBeforeFirstMigration() {
        backups++
        if (backupFails) error("disk full")
    }

    fun ids(page: String) = all.values.filter { it.pageId == page }.map { it.id }.toSet()
    fun add(s: Stroke) { all[s.id] = s }
    fun erase(id: String) { all.remove(id) }
}
