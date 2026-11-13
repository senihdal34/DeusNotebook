package com.ethran.notable.planner

import android.util.Log
import androidx.compose.ui.geometry.Offset
import com.ethran.notable.data.PageDataManager
import com.ethran.notable.data.db.Stroke
import com.ethran.notable.editor.utils.offsetStroke
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import kotlin.math.floor

/**
 * One-time move of handwriting on week and day pages from the layout before the zoom strip to the current one.
 *
 * The zoom strip pushed [PlannerLayout.ROWS_TOP] down by [PlannerLayout.ZOOM_H] and so made the seven week rows (and the
 * line step, which is a row's height divided by its line count) a little smaller. Ink written earlier then sat above
 * its check boxes. Every page that holds such ink is migrated once, the first time it is opened (or, for the page on the
 * other side of a week/day pair, the first time a sync needs it), and carries the marker [KEY_PREFIX]`<pageId>` = [LAYOUT]
 * afterwards. Pages created with the current layout get the marker when they are created, so they are never touched.
 *
 * Only strokes are moved, and only by a translation: a stroke keeps its size and shape, it is shifted so that its
 * vertical centre sits where the same spot of the old row (week) or of the old line grid (day) is now.
 */
object PlannerInkMigration {
    const val KEY_PREFIX = "planner-layout:"
    const val LAYOUT = "v2"

    /** The layout before the zoom strip: the tab bar was the only thing under the header. */
    const val NAV_H_OLD = 153f
    const val ROWS_TOP_OLD = PlannerLayout.HEADER_H + NAV_H_OLD

    fun markerKey(pageId: String) = "$KEY_PREFIX$pageId"

    enum class Kind { Week, Day }

    /** The week rows of the old layout for a page of [pageHeight] px. */
    data class OldWeek(val rowH: Float, val lines: Int) {
        val step get() = rowH / lines
        fun rowTop(i: Int) = ROWS_TOP_OLD + i * rowH
        val bottom get() = rowTop(7)
    }

    fun oldWeek(pageHeight: Float): OldWeek {
        val rowH = (pageHeight - ROWS_TOP_OLD - PlannerLayout.BOTTOM_MARGIN) / 7f
        return OldWeek(rowH, PlannerLayout.lineCount(rowH))
    }

    /**
     * The new vertical position of a point at [y] (a stroke's centre), or [y] itself when it did not move: for a week page the
     * row it lies in decides and the spot keeps its fraction of the row; for a day page the distance from the top of the
     * lines is scaled by the ratio of the new to the old line step. Header ink and ink under the last row stay where they are.
     */
    fun newY(kind: Kind, y: Float, pageHeight: Float): Float {
        val old = oldWeek(pageHeight)
        val new = PlannerLayout.week(pageHeight)
        if (!(old.rowH > 0f) || !(new.rowH > 0f)) return y
        return when (kind) {
            Kind.Week -> {
                if (y < ROWS_TOP_OLD || y >= old.bottom) return y
                val i = floor((y - ROWS_TOP_OLD) / old.rowH).toInt().coerceIn(0, 6)
                new.rowTop(i) + (y - old.rowTop(i)) * new.rowH / old.rowH
            }
            Kind.Day -> {
                if (y < ROWS_TOP_OLD) y
                else PlannerLayout.ROWS_TOP + (y - ROWS_TOP_OLD) * new.step / old.step
            }
        }
    }

    /** The strokes of a page that have to move, already moved (same ids); strokes that stay are not returned. */
    fun migrate(strokes: List<Stroke>, kind: Kind, pageHeight: Float): List<Stroke> = strokes.mapNotNull { s ->
        val cy = (s.top + s.bottom) / 2f
        val dy = newY(kind, cy, pageHeight) - cy
        if (dy == 0f) null else offsetStroke(s, Offset(0f, dy))
    }

    /** The kind of planner page a page with this [background] is, or null when the layout change did not touch it. */
    fun kindOf(background: String): Kind? = when {
        PlannerWeeks.parseWeek(background) != null -> Kind.Week
        PlannerWeeks.parseDay(background) != null -> Kind.Day
        else -> null
    }

    /**
     * Called when a page is about to be loaded into the editor, before its strokes are read (so nothing is drawn or
     * saved at the old positions). Migrates a week or day page that has no marker yet and drops what the app holds for
     * it: strokes in memory and the saved previews. Never throws: a failure leaves the page as it was, unmarked, and the
     * migration is tried again at the next opening.
     */
    suspend fun onOpen(store: PlannerInkStore, filesDir: File, pageId: String, background: String, pageHeight: Float) {
        val kind = kindOf(background) ?: return
        try {
            if (!ensure(store, pageId, kind, pageHeight)) return
            File(filesDir, "pages/previews/full/$pageId").delete()
            File(filesDir, "pages/previews/thumbs/$pageId").delete()
            PageDataManager.dropLoaded(pageId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("PlannerInkMigration", "layout migration of $pageId failed: ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    private const val CHUNK = 400
    private val lock = Mutex()

    /**
     * Migrates [pageId] once. Returns true when strokes were changed (the caller must then drop what it holds in memory
     * for the page). One lock for the whole app: two migrations of a page at once (the editor opening it while a sync
     * needs it) would each read the old positions.
     */
    suspend fun ensure(store: PlannerInkStore, pageId: String, kind: Kind, pageHeight: Float): Boolean = lock.withLock {
        if (store.layoutMarked(pageId)) return@withLock false
        val moved = migrate(store.strokes(pageId), kind, pageHeight)
        // A copy of the database taken before the very first change of real ink; never blocks the migration.
        if (moved.isNotEmpty()) runCatching { store.backupBeforeFirstMigration() }
        store.commitMigration(pageId, moved.chunked(CHUNK))
        moved.isNotEmpty()
    }
}

/** What the migration reads and writes; a fake in tests. */
interface PlannerInkStore {
    suspend fun strokes(pageId: String): List<Stroke>

    suspend fun layoutMarked(pageId: String): Boolean

    /** Marks a page that is created with the current layout, so it is never migrated. */
    suspend fun markLayout(pageId: String)

    /**
     * In one transaction: updates [updated] (grouped in chunks), removes every sync record that names the page (the old
     * records describe the old positions; the next sync of the pair starts over and only merges) and writes the marker.
     * Does nothing when the marker is already there.
     */
    suspend fun commitMigration(pageId: String, updated: List<List<Stroke>>)

    /** Copies the database once to a backup file; does nothing when the copy exists. May throw. */
    suspend fun backupBeforeFirstMigration()
}
