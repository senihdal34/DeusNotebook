package com.ethran.notable.io

import java.io.File
import java.util.Calendar
import java.util.Date
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteLeaveTest {
    private fun act(note: Boolean = true, strokes: Int, tags: Boolean = false, route: LeaveRoute) =
        NoteLeave.actionOnLeave(note, strokes, tags, route)

    @Test fun everyRouteWithInkRecognisesButOnlySaveAndExitExports() {
        assertEquals(LeaveAction.Recognise, act(strokes = 3, route = LeaveRoute.RailBack))
        assertEquals(LeaveAction.Recognise, act(strokes = 1, route = LeaveRoute.Other))
        assertEquals(LeaveAction.Export, act(strokes = 3, route = LeaveRoute.SaveAndExit))
    }

    @Test fun aPageWithoutInkIsSimplyLeft() {
        assertEquals(LeaveAction.None, act(strokes = 0, route = LeaveRoute.RailBack))
        assertEquals(LeaveAction.None, act(strokes = 0, route = LeaveRoute.Other))
        // tags alone do not make Back recognise anything
        assertEquals(LeaveAction.None, act(strokes = 0, tags = true, route = LeaveRoute.RailBack))
    }

    @Test fun saveAndExitKeepsItsOldRuleInkOrTags() {
        assertEquals(LeaveAction.Export, act(strokes = 0, tags = true, route = LeaveRoute.SaveAndExit))
        assertEquals(LeaveAction.None, act(strokes = 0, route = LeaveRoute.SaveAndExit))
    }

    @Test fun plannerAndNotebookPagesNeverSync() {
        for (route in LeaveRoute.entries) assertEquals(LeaveAction.None, act(note = false, strokes = 50, tags = true, route = route))
    }

    @Test fun shouldSyncOnLeaveMirrorsTheAction() {
        assertTrue(NoteLeave.shouldSyncOnLeave(true, 2, false, LeaveRoute.RailBack))
        assertFalse(NoteLeave.shouldSyncOnLeave(true, 0, false, LeaveRoute.RailBack))
        assertFalse(NoteLeave.shouldSyncOnLeave(false, 2, false, LeaveRoute.Other))
    }

    @Test fun whatIsANotePage() {
        assertTrue(NoteLeave.isNotePage(bookId = null, isPlannerPage = false, isInboxPage = false))
        assertTrue(NoteLeave.isNotePage(bookId = null, isPlannerPage = false, isInboxPage = true))
        assertFalse(NoteLeave.isNotePage(bookId = "b", isPlannerPage = false, isInboxPage = false))
        assertFalse(NoteLeave.isNotePage(bookId = null, isPlannerPage = true, isInboxPage = false))
        assertFalse(NoteLeave.isNotePage(bookId = "b", isPlannerPage = true, isInboxPage = true))
    }

    private val created = Calendar.getInstance().apply { clear(); set(2026, Calendar.NOVEMBER, 15, 9, 5) }.time

    @Test fun provisionalTitleIsTheCreationDateAndTime() =
        assertEquals("2026-11-15 09:05", NoteLeave.provisionalTitle(created))

    @Test fun aRecognisedLineAlwaysWins() {
        assertEquals("Toplantı notları", NoteLeave.titleToSave("", "Toplantı notları", created))
        assertEquals("Toplantı notları", NoteLeave.titleToSave("2026-11-15 09:05", "Toplantı notları", created))
        assertEquals("Yeni", NoteLeave.titleToSave("Eski", "Yeni", created))
        assertNull(NoteLeave.titleToSave("Aynı", "Aynı", created))
    }

    @Test fun failedRecognitionKeepsOrSetsTheProvisionalTitle() {
        assertEquals("2026-11-15 09:05", NoteLeave.titleToSave("", null, created))
        assertEquals("2026-11-15 09:05", NoteLeave.titleToSave("  ", "  ", created))
        // a title from an earlier recognition is not replaced by a failed one
        assertNull(NoteLeave.titleToSave("Toplantı", null, created))
        assertNull(NoteLeave.titleToSave("Toplantı", "", created))
    }

    @Test fun titleLineIsTheCleanedFirstLine() {
        assertEquals("Merhaba dünya", InboxSyncEngine.titleLineOf("Merhaba dünya\nikinci satır"))
        assertNull(InboxSyncEngine.titleLineOf(""))
        assertNull(InboxSyncEngine.titleLineOf("   \nabc"))
        assertEquals(50, InboxSyncEngine.titleLineOf("a".repeat(80))?.length)
    }
}

class SyncQueueTest {
    @Test fun oneRunPerPageAtATime() {
        val q = SyncQueue()
        assertTrue(q.request("p", emptyList(), export = false))
        assertTrue(q.isRunning("p"))
        assertFalse(q.request("p", emptyList(), export = false))
        // another page is independent
        assertTrue(q.request("q", emptyList(), export = false))
    }

    @Test fun aRequestDuringARunIsQueuedAndRunsOnceAfterwards() {
        val q = SyncQueue()
        q.request("p", listOf("a"), export = false)
        q.request("p", listOf("b"), export = false)
        q.request("p", listOf("c"), export = false)
        assertEquals(listOf("c") to false, q.finish("p"))
        assertTrue(q.isRunning("p"))
        assertNull(q.finish("p"))
        assertFalse(q.isRunning("p"))
        assertTrue(q.request("p", emptyList(), export = false))
    }

    @Test fun anExportRequestIsNeverDowngraded() {
        val q = SyncQueue()
        q.request("p", emptyList(), export = false)
        q.request("p", listOf("x"), export = true)
        q.request("p", listOf("y"), export = false)
        assertEquals(listOf("x") to true, q.finish("p"))
    }

    @Test fun finishWithNothingQueuedFreesThePage() {
        val q = SyncQueue()
        q.request("p", emptyList(), export = true)
        assertNull(q.finish("p"))
        assertFalse(q.isRunning("p"))
    }
}

class RecognisedTextStoreTest {
    @Test fun savedTextComesBackAndOtherPagesAreEmpty() {
        val dir = kotlin.io.path.createTempDirectory("rec").toFile()
        try {
            RecognisedTextStore.dir = dir
            RecognisedTextStore.save("page-1", "Başlık\nilk satır")
            assertEquals("Başlık\nilk satır", RecognisedTextStore.read("page-1"))
            assertNull(RecognisedTextStore.read("page-2"))
            // a path-like id cannot leave the folder
            RecognisedTextStore.save("../evil", "x")
            assertTrue(dir.listFiles()!!.all { it.parentFile == dir })
            assertFalse(File(dir.parentFile, "evil.txt").exists())
        } finally {
            RecognisedTextStore.dir = null
            dir.deleteRecursively()
        }
    }

    @Test fun withoutAFolderNothingHappens() {
        RecognisedTextStore.dir = null
        RecognisedTextStore.save("p", "x")
        assertNull(RecognisedTextStore.read("p"))
    }

    @Test fun anExcerptComesFromTheRecognisedTextWhenThereIsNoExport() {
        val dir = kotlin.io.path.createTempDirectory("vault").toFile()
        try {
            val page = com.ethran.notable.data.db.Page(notebookId = null, background = "inbox", createdAt = Date())
            val map = ExportedExcerpts.load(dir, listOf(page), recognised = { if (it == page.id) "Başlık\nbir iki üç" else null })
            assertEquals("bir iki üç", map[page.id])
            // no recognised text, no export: no excerpt
            assertTrue(ExportedExcerpts.load(dir, listOf(page), recognised = { null }).isEmpty())
        } finally {
            dir.deleteRecursively()
        }
    }
}
