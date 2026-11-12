package com.ethran.notable.planner

import android.util.Log
import androidx.compose.ui.geometry.Offset
import androidx.room.withTransaction
import com.ethran.notable.data.PageDataManager
import com.ethran.notable.data.db.AppDatabase
import com.ethran.notable.data.db.Kv
import com.ethran.notable.data.db.Stroke
import com.ethran.notable.editor.utils.offsetStroke
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.io.File
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/**
 * Keeps the first lines of a day page equal to that day's row on the week page. Both use the same line step and the
 * same horizontal layout, so a copy only moves the ink up or down.
 *
 * Words used here:
 * - a **native** stroke was written on the page it is on; it has its own random id.
 * - a **copy** (derived stroke) mirrors a native stroke of the other page; its id is [copyId] of that native's id (its
 *   **origin**) and its own page, so it can always be told apart and syncing twice makes the same ids.
 *
 * A sync of a week row and a day page is a merge of the two regions, not "the page left wins":
 * - a native stroke on one side with no copy on the other gets a copy there;
 * - a stroke and its copy that differ (moved or resized on one side) are made equal to the side that changed since the
 *   last sync (the page being left when both changed or nothing is recorded);
 * - the one rule that removes ink: a stroke that was mirrored at the last sync and whose counterpart is gone from the
 *   other page's region since (erased there, or moved out of the row) is removed too, so erasing works on either page.
 *   Strokes that were never mirrored are never removed, so the first sync of a pair only adds.
 *
 * What was mirrored is kept per pair in the key-value table ([KEY_PREFIX]); no schema change.
 */
object PlannerSync {
    const val KEY_PREFIX = "plannersync:"
    /** A copy whose centre lands this close outside the row still counts as in it (float error from moving it). */
    private const val EDGE = 1f
    private const val SAME = 0.5f
    private const val TAG = "PlannerSync"

    /** Strokes whose vertical centre lies in [top, bottom). */
    fun inRegion(strokes: List<Stroke>, top: Float, bottom: Float): List<Stroke> =
        strokes.filter { val c = (it.top + it.bottom) / 2f; c >= top && c < bottom }

    fun copyId(originId: String, targetPageId: String): String =
        UUID.nameUUIDFromBytes("$originId>$targetPageId".toByteArray()).toString()

    /** Copies of [strokes] on [targetPageId], moved by [dy]; ids are derived, so syncing twice gives the same ids. */
    fun copies(strokes: List<Stroke>, dy: Float, targetPageId: String): List<Stroke> = strokes.map { s ->
        offsetStroke(s, Offset(0f, dy)).copy(id = copyId(s.id, targetPageId), pageId = targetPageId)
    }

    /** Where a stroke sat in its region when it was last mirrored: its box relative to the region top, and its size. */
    @Serializable
    data class Mark(val t: Float, val b: Float, val l: Float, val r: Float, val n: Int) {
        fun same(o: Mark) = n == o.n && abs(t - o.t) < SAME && abs(b - o.b) < SAME && abs(l - o.l) < SAME && abs(r - o.r) < SAME
    }

    fun mark(s: Stroke, regionTop: Float) = Mark(s.top - regionTop, s.bottom - regionTop, s.left, s.right, s.points.size)

    /** One page of a pair: all its strokes and the top of its mirrored region. */
    class Side(val pageId: String, val strokes: List<Stroke>, val top: Float)

    /** What a sync changes, and what is mirrored afterwards (origin id -> mark). */
    data class Plan(
        val delete: List<String>, val insert: List<Stroke>, val update: List<Stroke>,
        val mirrored: Map<String, Mark>, val changedPages: Set<String>
    ) {
        val noStrokeChanges get() = delete.isEmpty() && insert.isEmpty() && update.isEmpty()
    }

    /** Origin id -> stroke, for the strokes in [side]'s region. */
    private fun origins(side: Side, other: Side, height: Float, known: Set<String>): Map<String, Stroke> {
        val byCopyId = HashMap<String, String>()
        for (s in other.strokes) byCopyId[copyId(s.id, side.pageId)] = s.id
        for (o in known) byCopyId[copyId(o, side.pageId)] = o
        val strict = inRegion(side.strokes, side.top, side.top + height).mapTo(HashSet()) { it.id }
        val out = LinkedHashMap<String, Stroke>()
        for (s in inRegion(side.strokes, side.top - EDGE, side.top + height + EDGE)) {
            val origin = byCopyId[s.id]
            if (origin != null || s.id in strict) out.putIfAbsent(origin ?: s.id, s)
        }
        return out
    }

    /**
     * The merge of the regions of [a] and [b] (both [height] tall), given what was mirrored at the last sync ([before]).
     * [left] is the page being left (it wins a tie). Pages in [frozen] are not changed: their part waits for a later sync,
     * and [Plan.mirrored] keeps the old record for it so nothing is lost or mistaken for an erase.
     */
    fun plan(a: Side, b: Side, height: Float, before: Map<String, Mark>, left: String, frozen: Set<String> = emptySet()): Plan {
        val ea = origins(a, b, height, before.keys)
        val eb = origins(b, a, height, before.keys)
        val delete = ArrayList<String>()
        val insert = ArrayList<Stroke>()
        val update = ArrayList<Stroke>()
        val mirrored = LinkedHashMap<String, Mark>()
        val changed = LinkedHashSet<String>()
        val idsOn = mapOf(a.pageId to a.strokes.mapTo(HashSet()) { it.id }, b.pageId to b.strokes.mapTo(HashSet()) { it.id })
        fun other(side: Side) = if (side === a) b else a

        for (o in LinkedHashSet(ea.keys + eb.keys)) {
            val sa = ea[o]
            val sb = eb[o]
            if (sa != null && sb != null) {
                val ma = mark(sa, a.top)
                val mb = mark(sb, b.top)
                val old = before[o]
                if (ma.same(mb)) { mirrored[o] = old?.takeIf { it.same(ma) } ?: ma; continue }
                val aWins = when {
                    old != null && old.same(mb) -> true
                    old != null && old.same(ma) -> false
                    else -> a.pageId == left
                }
                val (from, fromSide, to, toSide) = if (aWins) Quad(sa, a, sb, b) else Quad(sb, b, sa, a)
                if (toSide.pageId in frozen) { old?.let { mirrored[o] = it }; continue }
                update += offsetStroke(from, Offset(0f, toSide.top - fromSide.top)).copy(id = to.id, pageId = toSide.pageId)
                mirrored[o] = mark(from, fromSide.top)
                changed += toSide.pageId
                continue
            }
            val side = if (sa != null) a else b
            val s = sa ?: sb ?: continue
            val target = other(side)
            when {
                o in before -> {
                    // Mirrored at the last sync and gone from the other region since: erased (or moved away) there.
                    if (side.pageId in frozen) before[o]?.let { mirrored[o] = it }
                    else { delete += s.id; changed += side.pageId }
                }
                s.id == o -> {
                    val copy = copies(listOf(s), target.top - side.top, target.pageId).first()
                    // A copy already elsewhere on that page was moved out of the row there: leave both alone.
                    if (target.pageId !in frozen && copy.id !in idsOn.getValue(target.pageId)) {
                        insert += copy
                        mirrored[o] = mark(s, side.top)
                        changed += target.pageId
                    }
                }
                // A copy whose original is gone, with no record of it being mirrored: left as it is.
            }
        }
        return Plan(delete, insert, update, mirrored, changed)
    }

    private data class Quad(val from: Stroke, val fromSide: Side, val to: Stroke, val toSide: Side)

    private val json = Json { ignoreUnknownKeys = true }
    private val marksSerializer = MapSerializer(String.serializer(), Mark.serializer())

    fun encode(m: Map<String, Mark>): String = json.encodeToString(marksSerializer, m)

    /** A missing or damaged record reads as "nothing mirrored yet", which only ever adds ink. */
    fun decode(s: String?): Map<String, Mark> = s?.let { runCatching { json.decodeFromString(marksSerializer, it) }.getOrNull() }.orEmpty()

    fun key(weekPageId: String, dayPageId: String) = "$KEY_PREFIX$weekPageId:$dayPageId"

    /**
     * Syncs one week row with its day page. Returns the pages whose strokes changed. Call it under [run]'s lock: two
     * syncs of the same pair at once would each read the other's half-done state.
     */
    suspend fun syncPair(
        store: PlannerSyncStore, weekPageId: String, rowTop: Float, dayPageId: String, height: Float,
        leftPageId: String, frozen: Set<String>
    ): Set<String> {
        val key = key(weekPageId, dayPageId)
        val before = decode(store.mirrored(key))
        val week = Side(weekPageId, store.strokes(weekPageId), rowTop)
        val day = Side(dayPageId, store.strokes(dayPageId), PlannerLayout.ROWS_TOP)
        val p = plan(week, day, height, before, leftPageId, frozen)
        if (p.noStrokeChanges && p.mirrored == before) return emptySet()
        store.apply(p.delete, p.insert, p.update, key, encode(p.mirrored))
        return p.changedPages
    }

    /** Leaving the week page: its seven rows are merged with the day pages that already exist. */
    suspend fun weekToDays(
        store: PlannerSyncStore, weekPageId: String, monday: LocalDate, pageHeight: Float,
        dayPages: Map<LocalDate, String>, frozen: Set<String>
    ): Set<String> {
        val layout = PlannerLayout.week(pageHeight)
        val changed = HashSet<String>()
        for (i in 0..6) {
            val dayPage = dayPages[monday.plusDays(i.toLong())] ?: continue
            changed += syncPair(store, weekPageId, layout.rowTop(i), dayPage, layout.rowH, weekPageId, frozen)
        }
        return changed
    }

    /** Leaving a day page: its first lines are merged with the day's row on the week page. */
    suspend fun dayToWeek(
        store: PlannerSyncStore, dayPageId: String, day: LocalDate, weekPageId: String, pageHeight: Float, frozen: Set<String>
    ): Set<String> {
        val layout = PlannerLayout.week(pageHeight)
        return syncPair(store, weekPageId, layout.rowTop(day.dayOfWeek.value - 1), dayPageId, layout.rowH, dayPageId, frozen)
    }

    // ------------------------------------------------------------------------------------------- running a sync
    /**
     * The app's planner syncs. Its scope outlives the editor's composition, so a sync started when a page is left is
     * never cut off halfway; previews and memory cache of the pages a sync changed are dropped so they are drawn from
     * the strokes next time.
     */
    private val runner by lazy {
        PlannerSyncRunner(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
            currentPage = PageDataManager::currentPageId,
            forget = { filesDir, id ->
                File(filesDir, "pages/previews/full/$id").delete()
                File(filesDir, "pages/previews/thumbs/$id").delete()
                PageDataManager.removePage(id)
            },
            log = { Log.w(TAG, it) }
        )
    }

    fun run(filesDir: File, openPages: () -> Set<String>, sync: suspend (frozen: Set<String>) -> Set<String>): Job =
        runner.run(filesDir, openPages, sync)
}

/**
 * Runs planner syncs one at a time (one lock for all of them: two syncs of a pair at once would each read the other's
 * half-done state). A sync gets the pages it must not change: the page open in the editor holds its strokes in memory,
 * and changing them underneath it would leave the screen and the database apart.
 */
class PlannerSyncRunner(
    private val scope: CoroutineScope,
    private val currentPage: () -> String,
    private val forget: (filesDir: File, pageId: String) -> Unit,
    private val log: (String) -> Unit,
    /** Strokes just drawn are saved a moment after the pen lifts. */
    private val settleMs: Long = 300,
) {
    private val lock = Mutex()

    fun run(filesDir: File, openPages: () -> Set<String>, sync: suspend (frozen: Set<String>) -> Set<String>): Job = scope.launch {
        delay(settleMs)
        lock.withLock {
            try {
                for (id in sync(openPages() + currentPage())) {
                    // Opened while the sync ran: its cache may be older than the strokes, but the open page must not be dropped.
                    if (id == currentPage()) log("a synced page was opened during the sync") else forget(filesDir, id)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log("sync failed: ${e.javaClass.simpleName}: ${e.message}")
            }
        }
    }
}

/**
 * Makes sure leaving a page syncs it once: the week tool syncs before it switches and [markSynced]s the page, and the
 * editor's cleanup for that page then [takeNeedsSync] false and skips its own sync.
 */
class ExitSyncGuard {
    private val synced = java.util.concurrent.atomic.AtomicReference<String?>(null)
    fun markSynced(pageId: String?) = synced.set(pageId)
    /** True unless [pageId]'s exit was already synced; either way the mark is used up. */
    fun takeNeedsSync(pageId: String): Boolean = !synced.compareAndSet(pageId, null)
}

/** Reads and writes what a planner sync needs; a fake in tests. */
interface PlannerSyncStore {
    suspend fun strokes(pageId: String): List<Stroke>
    suspend fun mirrored(key: String): String?
    /** Applies the stroke changes and the new record together: all of it or none. */
    suspend fun apply(delete: List<String>, insert: List<Stroke>, update: List<Stroke>, key: String, mirrored: String)
}

@Singleton
class RoomPlannerSyncStore @Inject constructor(private val db: AppDatabase) : PlannerSyncStore {
    override suspend fun strokes(pageId: String): List<Stroke> = db.pageDao().getPageWithStrokesById(pageId).strokes
    override suspend fun mirrored(key: String): String? = db.kvDao().get(key)?.value
    override suspend fun apply(delete: List<String>, insert: List<Stroke>, update: List<Stroke>, key: String, mirrored: String) {
        db.withTransaction {
            if (delete.isNotEmpty()) db.strokeDao().deleteAll(delete)
            if (update.isNotEmpty()) db.strokeDao().update(update)
            if (insert.isNotEmpty()) db.strokeDao().create(insert)
            db.kvDao().set(Kv(key, mirrored))
        }
    }
}
