package com.ethran.notable.planner

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger

class PlannerSyncRunnerTest {
    private val dir = File("unused")
    private val forgotten = Collections.synchronizedList(ArrayList<String>())
    private val logged = Collections.synchronizedList(ArrayList<String>())
    @Volatile private var current = "open"

    private fun runner() = PlannerSyncRunner(
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
        currentPage = { current },
        forget = { _, id -> forgotten += id },
        log = { logged += it },
        settleMs = 0
    )

    @Test fun `syncs never overlap`() = runBlocking {
        val r = runner()
        val active = AtomicInteger()
        val most = AtomicInteger()
        val jobs = (1..30).map {
            r.run(dir, { emptySet() }) {
                most.accumulateAndGet(active.incrementAndGet(), ::maxOf)
                delay(2)
                active.decrementAndGet()
                emptySet()
            }
        }
        jobs.joinAll()
        assertEquals(1, most.get())
    }

    @Test fun `the open pages are frozen and the current page is never dropped`() = runBlocking {
        val r = runner()
        var frozen: Set<String> = emptySet()
        r.run(dir, { setOf("editor-page") }) { f -> frozen = f; setOf("day", "open") }.join()
        assertEquals(setOf("editor-page", "open"), frozen)
        assertEquals(listOf("day"), forgotten.toList())
        assertEquals(1, logged.size)
    }

    @Test fun `a failed sync is logged and the next one still runs`() = runBlocking {
        val r = runner()
        r.run(dir, { emptySet() }) { error("boom") }.join()
        r.run(dir, { emptySet() }) { setOf("week") }.join()
        assertTrue(logged.single().contains("boom"))
        assertEquals(listOf("week"), forgotten.toList())
    }

    @Test fun `a sync outlives the scope that started it`() = runBlocking {
        val r = runner()
        val done = AtomicInteger()
        var job: Job? = null
        val composition = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        composition.launch { job = r.run(dir, { emptySet() }) { delay(50); done.incrementAndGet(); emptySet() } }.join()
        composition.cancel()
        job?.join()
        assertEquals(1, done.get())
    }

    @Test fun `leaving a page syncs it once`() {
        val g = ExitSyncGuard()
        assertTrue(g.takeNeedsSync("week"))                    // a swipe or leaving the editor: the cleanup syncs
        g.markSynced("week")                                    // the week tool synced before switching
        assertFalse(g.takeNeedsSync("week"))                    // so the cleanup skips it
        assertTrue(g.takeNeedsSync("week"))                     // and the mark is used up
        g.markSynced("week")
        assertTrue(g.takeNeedsSync("day"))                      // another page is not skipped
    }

    private fun CoroutineScope.cancel() = coroutineContext[Job]?.cancel()
}
