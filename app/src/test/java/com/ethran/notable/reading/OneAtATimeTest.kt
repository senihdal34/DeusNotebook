package com.ethran.notable.reading

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class OneAtATimeTest {
    @Test fun `a call while one runs is dropped, and the next one after it runs`() = runBlocking {
        val guard = OneAtATime()
        val runs = AtomicInteger()
        val gate = CompletableDeferred<Unit>()
        val first = guard.launch(this, Dispatchers.Default) { runs.incrementAndGet(); gate.await() }
        assertNotNull(first)
        assertNull(guard.launch(this, Dispatchers.Default) { runs.incrementAndGet() })   // the resume refresh
        gate.complete(Unit)
        first?.join()
        guard.launch(this, Dispatchers.Default) { runs.incrementAndGet() }?.join()
        assertEquals(2, runs.get())
    }

    @Test fun `many calls at the same moment start one job`() = runBlocking {
        val guard = OneAtATime()
        val runs = AtomicInteger()
        val gate = CompletableDeferred<Unit>()
        val started = (1..50).map { async(Dispatchers.Default) { guard.launch(this@runBlocking, Dispatchers.IO) { runs.incrementAndGet(); gate.await() } } }.awaitAll()
        assertEquals(1, started.count { it != null })
        gate.complete(Unit)
        started.filterNotNull().forEach { it.join() }
        assertEquals(1, runs.get())
    }
}
