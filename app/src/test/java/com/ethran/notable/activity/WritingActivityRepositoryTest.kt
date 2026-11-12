package com.ethran.notable.activity

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal class FakeSource(var days: Map<String, Int> = emptyMap()) : ActivitySource {
    var signatureCalls = 0
    var countCalls = 0
    var lastSince: Long? = null
    var failing = false
    override suspend fun signature(): StrokeSignature {
        signatureCalls++
        if (failing) error("database closed")
        return StrokeSignature(days.values.sum().toLong(), days.size)
    }
    override suspend fun dayCounts(sinceMs: Long): Map<String, Int> { countCalls++; lastSince = sinceMs; return days }
}

internal class FakeStore(var json: String? = null) : ActivityStore {
    var writes = 0
    override suspend fun read(): String? = json
    override suspend fun write(json: String) { writes++; this.json = json }
}

internal class FakePrefs(var saved: ActivityPeriod? = null) : ActivityPrefs {
    override fun period(): ActivityPeriod? = saved
    override fun savePeriod(period: ActivityPeriod) { saved = period }
}

@OptIn(ExperimentalCoroutinesApi::class)
class WritingActivityRepositoryTest {
    private fun d(s: String) = LocalDate.parse(s)
    private fun TestScope.repo(source: FakeSource, store: FakeStore) = WritingActivityRepository(source, store, StandardTestDispatcher(testScheduler))

    @Test fun `no strokes gives no days and writes no snapshot`() = runTest {
        val store = FakeStore()
        assertEquals(emptyMap(), repo(FakeSource(), store).dayCounts())
        assertEquals(0, store.writes)
    }

    @Test fun `counts are saved under the versioned key format`() = runTest {
        val store = FakeStore()
        val source = FakeSource(mapOf("2026-11-12" to 7))
        val r = repo(source, store)
        assertEquals(mapOf(d("2026-11-12") to 7), r.dayCounts())
        assertEquals(0L, source.lastSince)
        assertEquals(mapOf("2026-11-12" to 7), WritingActivityRepository.decode(store.json))
        assertEquals("writing-activity:v1", WritingActivityRepository.KEY)
    }

    @Test fun `an unchanged table is not counted again`() = runTest {
        val source = FakeSource(mapOf("2026-11-12" to 7))
        val r = repo(source, FakeStore())
        r.dayCounts(); r.dayCounts(); r.dayCounts()
        assertEquals(1, source.countCalls)
        assertEquals(3, source.signatureCalls)
    }

    @Test fun `a changed table is counted again`() = runTest {
        val source = FakeSource(mapOf("2026-11-12" to 7))
        val r = repo(source, FakeStore())
        r.dayCounts()
        source.days = mapOf("2026-11-12" to 9)
        assertEquals(mapOf(d("2026-11-12") to 9), r.dayCounts())
        assertEquals(2, source.countCalls)
    }

    @Test fun `deleting notes never shrinks the history`() = runTest {
        val store = FakeStore()
        val source = FakeSource(mapOf("2026-11-10" to 40, "2026-11-11" to 25))
        val r = repo(source, store)
        r.dayCounts()
        source.days = mapOf("2026-11-11" to 5)       // the note of the 10th was deleted, the 11th was mostly erased
        val after = r.dayCounts()
        assertEquals(40, after[d("2026-11-10")])
        assertEquals(25, after[d("2026-11-11")])
    }

    @Test fun `history survives an app restart through the snapshot`() = runTest {
        val store = FakeStore()
        repo(FakeSource(mapOf("2026-11-10" to 40)), store).dayCounts()
        val restarted = repo(FakeSource(emptyMap()), store)       // strokes are gone, the snapshot remains
        assertEquals(mapOf(d("2026-11-10") to 40), restarted.dayCounts())
    }

    @Test fun `a damaged snapshot is ignored`() = runTest {
        val store = FakeStore("{not json")
        val r = repo(FakeSource(mapOf("2026-11-10" to 6)), store)
        assertEquals(mapOf(d("2026-11-10") to 6), r.dayCounts())
        assertEquals(mapOf("2026-11-10" to 6), WritingActivityRepository.decode(store.json))
    }

    @Test fun `thousands of strokes from an import do not change other days`() = runTest {
        val source = FakeSource(mapOf("2026-11-01" to 12, "2026-11-02" to 4000, "2026-11-03" to 9))
        val counts = repo(source, FakeStore()).dayCounts()
        assertEquals(12, counts[d("2026-11-01")])
        assertEquals(9, counts[d("2026-11-03")])
        assertTrue(WritingActivityStats.isWritten(counts.getValue(d("2026-11-03"))))
    }

    @Test fun `a failing database surfaces the error and keeps the last answer`() = runTest {
        val source = FakeSource(mapOf("2026-11-12" to 7))
        val r = repo(source, FakeStore())
        r.dayCounts()
        source.failing = true
        val failed = runCatching { r.dayCounts() }
        assertTrue(failed.isFailure)
        source.failing = false
        assertEquals(mapOf(d("2026-11-12") to 7), r.dayCounts())
    }

    @Test fun `decode handles empty and null`() {
        assertEquals(emptyMap(), WritingActivityRepository.decode(null))
        assertEquals(emptyMap(), WritingActivityRepository.decode(""))
        assertEquals(mapOf("a" to 1), WritingActivityRepository.decode(WritingActivityRepository.encode(mapOf("a" to 1))))
    }
}
