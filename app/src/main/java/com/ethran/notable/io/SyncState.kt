package com.ethran.notable.io

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import com.ethran.notable.data.AppRepository
import com.ethran.notable.ui.SnackConf
import com.ethran.notable.ui.SnackState
import io.shipbook.shipbooksdk.ShipBook
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import com.ethran.notable.R
import com.ethran.notable.i18n.AppStrings

private val log = ShipBook.getLogger("SyncState")

/**
 * Background work of a note page that outlives the editor screen: recognition (title, excerpt) and, for Save & Exit,
 * the export to the vault. Runs on its own scope, one run per page at a time (a request that arrives while a run is
 * in progress is queued and runs once more afterwards), never blocks the UI, and a failure keeps the ink untouched.
 */
object SyncState {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Pages left with ink or images still in memory (their database writes may be in flight): never deleted as empty. */
    private val hasContent = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private val queue = SyncQueue()

    /** Pages whose recognition or export is running (the Home card shows its syncing mark for these). */
    val syncingPageIds = mutableStateListOf<String>()

    private val _finished = MutableSharedFlow<String>(extraBufferCapacity = 16)

    /**
     * Emits a page id when its run ends after recognising or exporting (done or failed): the Home list reloads to show
     * the new title. Not emitted for a page that had nothing to do (no ink, or deleted).
     */
    val finished: SharedFlow<String> = _finished.asSharedFlow()

    /** Save & Exit: recognition and the full export with the chosen [tags]. */
    fun launchSync(
        appRepository: AppRepository,
        pageId: String,
        tags: List<String>,
        context: Context,
        exportEngine: ExportEngine
    ) = start(appRepository, pageId, tags, context, exportEngine, export = true)

    /**
     * Any other way out of a note page (rail back arrow, system back, back gesture, navigation away): recognition only
     * (title and excerpt), when the page has ink. Nothing is exported to the vault. Planner and notebook pages are ignored.
     */
    fun launchOnLeave(
        appRepository: AppRepository,
        pageId: String,
        tags: List<String>,
        context: Context,
        exportEngine: ExportEngine,
        isNotePage: Boolean,
        /** The open page still holds ink or images in memory: the database may not have them yet, so never delete it as empty. */
        contentInMemory: Boolean = false
    ) {
        if (!isNotePage) return
        if (contentInMemory) hasContent.add(pageId)
        start(appRepository, pageId, tags, context, exportEngine, export = false)
    }

    private fun start(
        appRepository: AppRepository,
        pageId: String,
        tags: List<String>,
        context: Context,
        exportEngine: ExportEngine,
        export: Boolean
    ) {
        val appContext = context.applicationContext
        if (RecognisedTextStore.dir == null) RecognisedTextStore.dir = java.io.File(appContext.filesDir, "recognised")
        if (!queue.request(pageId, tags, export)) {
            log.i("Page $pageId is already being processed, queued one more run")
            return
        }
        syncingPageIds.add(pageId)

        scope.launch {
            var runTags = tags
            var runExport = export
            var changed = false
            while (true) {
                try {
                    if (run(appRepository, pageId, runTags, runExport, appContext, exportEngine)) changed = true
                } catch (e: Exception) {
                    // A failed recognition may still have stored the provisional (date) title.
                    changed = true
                    log.e("Background sync failed for page $pageId: ${e.message}", e)
                    SnackState.globalSnackFlow.tryEmit(
                        SnackConf(
                            text = AppStrings.get(R.string.snack_sync_failed, e.message ?: ""),
                            duration = 4000
                        )
                    )
                }
                val next = queue.finish(pageId) ?: break
                runTags = next.first
                runExport = next.second
            }
            syncingPageIds.remove(pageId)
            // Only when a title or excerpt may have changed: an inkless leave must not reload (and repaint) Home.
            if (changed) _finished.tryEmit(pageId)
        }
    }

    private suspend fun run(
        appRepository: AppRepository,
        pageId: String,
        tags: List<String>,
        export: Boolean,
        context: Context,
        exportEngine: ExportEngine
    ): Boolean {
        val page = appRepository.pageRepository.getById(pageId)
        if (page == null) {
            log.i("Page $pageId is gone (deleted), nothing to do")
            return false
        }
        // Strokes are written to the database a moment after they are drawn: a page that is left empty-looking is checked
        // again after a short wait before it is deleted (see Discard below).
        val strokeCount = appRepository.pageRepository.getWithStrokeById(pageId).strokes.size
        val hasTags = tags.isNotEmpty() || appRepository.pageTags.get(pageId).isNotEmpty()
        val imageCount = appRepository.pageRepository.getWithImageById(pageId).images.size
        val route = if (export) LeaveRoute.SaveAndExit else LeaveRoute.Other
        /** True when recognition or export ran, so the title or excerpt may have changed. */
        return when (NoteLeave.actionOnLeave(isNotePage = true, strokeCount = strokeCount, hasTags = hasTags, route = route, currentTitle = page.title, imageCount = imageCount, pinned = page.pinned == 1)) {
            LeaveAction.None -> { log.i("Nothing to do for page $pageId (no ink)"); false }
            LeaveAction.Discard -> {
                val inMemory = hasContent.remove(pageId)
                kotlinx.coroutines.delay(DISCARD_RECHECK_MS)
                val stillEmpty = !inMemory &&
                    appRepository.pageRepository.getWithStrokeById(pageId).strokes.isEmpty() &&
                    appRepository.pageRepository.getWithImageById(pageId).images.isEmpty()
                if (stillEmpty) {
                    appRepository.pageRepository.delete(pageId)
                    log.i("Empty note $pageId deleted on leave")
                    true
                } else {
                    log.i("Note $pageId looked empty but has content, kept")
                    false
                }
            }
            LeaveAction.Recognise -> {
                InboxSyncEngine.recognisePage(appRepository, pageId, context)
                log.i("Recognition complete for page $pageId")
                true
            }
            LeaveAction.Export -> {
                InboxSyncEngine.syncInboxPage(appRepository, pageId, tags, context, exportEngine)
                log.i("Background sync complete for page $pageId")
                true
            }
        }
    }

    private const val DISCARD_RECHECK_MS = 1500L
}
