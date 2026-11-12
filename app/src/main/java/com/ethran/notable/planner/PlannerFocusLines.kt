package com.ethran.notable.planner

import com.ethran.notable.data.db.KvProxy
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.builtins.serializer

/**
 * How many ruled "focus" lines the month, quarter and year pages show. One number per page type (not per page),
 * 1..5, stored in the key-value table as `planner-focus-lines:<month|quarter|year>`. The page drawing reads the
 * in-memory [counts] snapshot, so it never waits for the database.
 */
object PlannerFocusLines {
    const val MIN = 1
    const val MAX = 5
    const val DEFAULT = 2
    private const val KEY_PREFIX = "planner-focus-lines:"

    @Volatile var counts: Map<PlannerLevel, Int> = emptyMap()

    fun lines(level: PlannerLevel): Int = counts[level] ?: DEFAULT

    fun clamp(n: Int) = n.coerceIn(MIN, MAX)

    fun key(level: PlannerLevel) = KEY_PREFIX + level.focusKind

    /** A stored value as a line count; anything unreadable is the default, a number out of range is clamped. */
    fun parse(stored: String?): Int = stored?.trim()?.trim('"')?.toIntOrNull()?.let(::clamp) ?: DEFAULT
}

/** Reads and writes the stored text; a fake in tests. */
interface FocusStore {
    suspend fun read(key: String): String?
    suspend fun write(key: String, value: String)
}

class KvProxyFocusStore(private val kv: KvProxy) : FocusStore {
    override suspend fun read(key: String): String? = kv.get(key, Int.serializer())?.toString()
    override suspend fun write(key: String, value: String) = kv.setKv(key, value.toInt(), Int.serializer())
}

/** Loads the focus line counts into [PlannerFocusLines.counts] and adds a line; no schema change (uses the `Kv` table). */
class PlannerFocusRepository(private val store: FocusStore) {
    /** Reads all three counts. Returns true when the snapshot changed, so the page needs a redraw. */
    suspend fun load(): Boolean = lock.withLock {
        val before = PlannerFocusLines.counts
        val loaded = PlannerLevel.entries.associateWith { PlannerFocusLines.parse(runCatching { store.read(PlannerFocusLines.key(it)) }.getOrNull()) }
        PlannerFocusLines.counts = loaded
        PlannerLevel.entries.any { (before[it] ?: PlannerFocusLines.DEFAULT) != loaded.getValue(it) }
    }

    /** One more focus line for [level], at most [PlannerFocusLines.MAX]. Returns the new count. */
    suspend fun add(level: PlannerLevel): Int = lock.withLock {
        val now = PlannerFocusLines.parse(runCatching { store.read(PlannerFocusLines.key(level)) }.getOrNull())
        val next = PlannerFocusLines.clamp(now + 1)
        if (next != now) store.write(PlannerFocusLines.key(level), next.toString())
        PlannerFocusLines.counts = PlannerFocusLines.counts + (level to next)
        next
    }

    private companion object {
        val lock = Mutex()
    }
}
