package com.ethran.notable.planner

import androidx.lifecycle.LiveData
import com.ethran.notable.data.db.Kv
import com.ethran.notable.data.db.KvDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap

class PlannerDoneTest {
    /** A key-value table that yields between read and write, so unguarded read-modify-writes interleave. */
    private class SlowKv : KvDao {
        val map = ConcurrentHashMap<String, String>()
        override suspend fun get(key: String): Kv? { yield(); return map[key]?.let { Kv(key, it) } }
        override fun getLive(key: String): LiveData<Kv?> = throw UnsupportedOperationException()
        override suspend fun set(kv: Kv) { yield(); map[kv.key] = kv.value }
        override suspend fun delete(key: String) { yield(); map.remove(key) }
        override suspend fun getWithPrefix(prefix: String): List<Kv> = map.filterKeys { it.startsWith(prefix) }.map { Kv(it.key, it.value) }
    }

    private val day: LocalDate = LocalDate.of(2026, 11, 4)

    @Before @After fun clear() { PlannerDone.days = emptyMap() }

    @Test fun `parallel ticks on one day are all kept`() = runBlocking(Dispatchers.Default) {
        val kv = SlowKv()
        val repo = PlannerDoneRepository(kv)
        (1..60).map { line -> async { repo.toggle(day, line) } }.awaitAll()
        assertEquals((1..60).toSet(), PlannerDone.lines(day))
        assertEquals((1..60).joinToString(","), kv.map["plannerdone:$day"])
        // Read back by a fresh load, the stored list is the same.
        PlannerDone.days = emptyMap()
        repo.load(listOf(day))
        assertEquals((1..60).toSet(), PlannerDone.lines(day))
    }

    @Test fun `a load running beside ticks does not undo them`() = runBlocking(Dispatchers.Default) {
        val kv = SlowKv()
        val repo = PlannerDoneRepository(kv)
        val jobs = (1..40).map { line -> async { if (line % 4 == 0) repo.load(listOf(day)) else repo.toggle(day, line) } }
        jobs.awaitAll()
        assertEquals((1..40).filter { it % 4 != 0 }.toSet(), PlannerDone.lines(day))
    }

    @Test fun `ticking twice clears the line and the stored key`() = runBlocking {
        val kv = SlowKv()
        val repo = PlannerDoneRepository(kv)
        assertEquals(setOf(3), repo.toggle(day, 3))
        assertEquals(emptySet<Int>(), repo.toggle(day, 3))
        assertEquals(null, kv.map["plannerdone:$day"])
    }
}
