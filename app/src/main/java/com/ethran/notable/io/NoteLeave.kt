package com.ethran.notable.io

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** How the user leaves a note page. Whatever the route, the page is recognised; only Save & Exit exports to the vault. */
enum class LeaveRoute {
    /** The rail's back arrow. */
    RailBack,

    /** The explicit Save & Exit button of the capture bar (with its tags). */
    SaveAndExit,

    /** Anything else: system back, back gesture, a menu entry that navigates away. Seen as the editor leaving the screen. */
    Other,
}

/** What leaving a page triggers. */
enum class LeaveAction {
    /** Leave without doing anything. */
    None,

    /** Recognise the handwriting and set the title and excerpt. Nothing is written to the vault. */
    Recognise,

    /** Recognition plus the full export: Markdown, page image and stroke file in the vault inbox. */
    Export,
}

/**
 * Pure decisions for leaving a note page (no Android, covered by JVM tests).
 *
 * A note page is a page outside any notebook that is not a planner page (quick notes: background "inbox", and whatever
 * background new notes get later). Planner pages and notebook pages never go through this.
 */
object NoteLeave {
    fun isNotePage(bookId: String?, isPlannerPage: Boolean, isInboxPage: Boolean): Boolean =
        !isPlannerPage && (isInboxPage || bookId == null)

    /**
     * Save & Exit exports when there is ink or a tag (the rule it always had). Every other route only recognises, and
     * only when the page has ink: an empty page is simply left, as before.
     */
    fun actionOnLeave(isNotePage: Boolean, strokeCount: Int, hasTags: Boolean, route: LeaveRoute): LeaveAction = when {
        !isNotePage -> LeaveAction.None
        route == LeaveRoute.SaveAndExit -> if (strokeCount > 0 || hasTags) LeaveAction.Export else LeaveAction.None
        strokeCount > 0 -> LeaveAction.Recognise
        else -> LeaveAction.None
    }

    /** `shouldSyncOnLeave`: does leaving run any recognition at all. */
    fun shouldSyncOnLeave(isNotePage: Boolean, strokeCount: Int, hasTags: Boolean, route: LeaveRoute): Boolean =
        actionOnLeave(isNotePage, strokeCount, hasTags, route) != LeaveAction.None

    /** Language-neutral stand-in title (local date and time of creation), used until, or if, recognition gives one. */
    fun provisionalTitle(createdAt: Date): String = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(createdAt)

    /**
     * The title to store, or null to leave the stored one. A recognised line always wins over what is stored. Without
     * one, a blank title becomes the date-based one, and a title that is already there (recognised earlier) is kept.
     */
    fun titleToSave(currentTitle: String, recognisedTitle: String?, createdAt: Date): String? = when {
        !recognisedTitle.isNullOrBlank() -> recognisedTitle.takeIf { it != currentTitle }
        currentTitle.isBlank() -> provisionalTitle(createdAt)
        else -> null
    }
}

/**
 * Bookkeeping of syncs in flight: one per page at a time. A request that arrives while one runs is not lost: the
 * page is run once more when the first finishes (the user may have written more in between), with the latest tags.
 */
class SyncQueue {
    private class Pending(val tags: List<String>, val export: Boolean)

    private val running = HashSet<String>()
    private val pending = HashMap<String, Pending>()

    /** True when the caller may start now; false when it was queued behind the running one. */
    @Synchronized
    fun request(pageId: String, tags: List<String>, export: Boolean): Boolean {
        if (running.add(pageId)) return true
        val before = pending[pageId]
        // An export request is never downgraded to a plain recognition.
        pending[pageId] = Pending(if (export || before?.export != true) tags else before.tags, export || before?.export == true)
        return false
    }

    /** The run for [pageId] ended. Returns the queued follow-up (tags, export) to run next, or null when done. */
    @Synchronized
    fun finish(pageId: String): Pair<List<String>, Boolean>? {
        val next = pending.remove(pageId)
        if (next == null) running.remove(pageId)
        return next?.let { it.tags to it.export }
    }

    @Synchronized
    fun isRunning(pageId: String) = pageId in running
}
