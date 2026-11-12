package com.ethran.notable.editor.rail

import com.ethran.notable.editor.utils.NamedSettings
import com.ethran.notable.editor.utils.Pen
import com.ethran.notable.editor.utils.PenSetting

/*
 * Pure logic of the editor tool rail (no Android views, no Compose): which buttons exist, how saved pens map
 * to the simplified pen set, when the rail folds away while writing, and how big its buttons are.
 * Everything here is covered by JVM unit tests.
 */

/** A button on the rail. */
enum class RailItem { Back, Pen, Eraser, Highlighter, Lasso, Line, Undo, Redo, PageCounter, PrevPage, NextPage, NewPage, Favorite, More }

/** An entry of the "more" menu. */
enum class MoreItem { InsertImage, Paste, PageBackground, ResetView, Export, ClearPage, WikiLink, Tag, Library, BugReport, HideToolbar }

/** What decides which buttons are shown. */
data class RailContext(
    val isInboxPage: Boolean = false,
    /** Planner pages (background starts with `planner-`) have their own navigation, so no page counter, no "new page", no star. */
    val isPlannerPage: Boolean = false,
    /** The page belongs to a notebook (single pages have no "n of N"). */
    val inBook: Boolean = true,
    val clipboardHasContent: Boolean = false,
    /** Zoomed in or scrolled sideways. */
    val viewMoved: Boolean = false,
)

object RailLayout {
    /** Groups from the top of the rail; a thin rule separates the groups. */
    fun topGroups(ctx: RailContext): List<List<RailItem>> = buildList {
        add(listOf(RailItem.Back, RailItem.Pen, RailItem.Eraser, RailItem.Highlighter, RailItem.Lasso, RailItem.Line))
        add(listOf(RailItem.Undo, RailItem.Redo))
        if (ctx.inBook && !ctx.isPlannerPage) add(listOf(RailItem.PageCounter, RailItem.PrevPage, RailItem.NextPage))
    }

    /** Buttons pinned to the bottom of the rail. */
    fun bottom(ctx: RailContext): List<RailItem> = buildList {
        if (ctx.inBook && !ctx.isPlannerPage) add(RailItem.NewPage)
        if (!ctx.isPlannerPage) add(RailItem.Favorite)
        add(RailItem.More)
    }

    fun all(ctx: RailContext): List<RailItem> = topGroups(ctx).flatten() + bottom(ctx)

    fun moreItems(ctx: RailContext): List<MoreItem> = buildList {
        add(MoreItem.InsertImage)
        if (ctx.clipboardHasContent) add(MoreItem.Paste)
        if (!ctx.isPlannerPage) add(MoreItem.PageBackground)
        if (ctx.viewMoved) add(MoreItem.ResetView)
        add(MoreItem.Export)
        add(MoreItem.ClearPage)
        add(MoreItem.WikiLink)
        add(MoreItem.Tag)
        add(MoreItem.Library)
        add(MoreItem.BugReport)
        add(MoreItem.HideToolbar)
    }
}

/** Result of mapping a saved editor state to the pens the interface still offers. */
data class MigratedPen(val pen: Pen, val penSettings: NamedSettings, val lineDashed: Boolean)

/**
 * The pen popover offers Ballpoint and Fountain (the highlighter is Marker). Saved state may still name a pen that is
 * no longer offered; it maps to a pen that is, so nothing crashes. Colour is a per-pen setting now, so the red, blue
 * and green ballpoints become the ballpoint with that colour. The enum values themselves stay: old strokes keep
 * their pen and colour and are drawn exactly as before.
 */
object PenMigration {
    private val offered = setOf(Pen.BALLPEN, Pen.FOUNTAIN, Pen.MARKER)

    fun migrate(pen: Pen, saved: NamedSettings): MigratedPen {
        val settings = (Pen.DEFAULT_SETTINGS + saved).toMutableMap()
        fun colourSetting(old: Pen): PenSetting = (saved[old.penName] ?: Pen.DEFAULT_SETTINGS.getValue(old.penName)).copy()
        return when (pen) {
            in offered -> MigratedPen(pen, settings, lineDashed = false)
            Pen.REDBALLPEN, Pen.BLUEBALLPEN, Pen.GREENBALLPEN -> {
                settings[Pen.BALLPEN.penName] = colourSetting(pen)
                MigratedPen(Pen.BALLPEN, settings, lineDashed = false)
            }
            Pen.DASHED -> {
                saved[Pen.DASHED.penName]?.let { settings[Pen.BALLPEN.penName] = it.copy() }
                MigratedPen(Pen.BALLPEN, settings, lineDashed = true)
            }
            // Pencil and Brush are gone from the interface.
            else -> MigratedPen(Pen.FOUNTAIN, settings, lineDashed = false)
        }
    }
}

/**
 * When the rail folds into its handle. The rail is open or folded. With auto-hide on it folds after the pen has been
 * writing for [hideAfterMs]; a gap of more than [idleGapMs] between pen events ends a writing run. Opening it again
 * (handle tap) starts the count anew. With auto-hide off it never folds by itself. It never folds while a popover is open.
 *
 * Time is passed in, so this class has no clock and no threads.
 */
class RailVisibility(
    private var autoHide: Boolean = true,
    startFolded: Boolean = false,
    private val hideAfterMs: Long = HIDE_AFTER_MS,
    private val idleGapMs: Long = IDLE_GAP_MS,
) {
    var folded: Boolean = startFolded
        private set

    private var runStart = NONE
    private var lastPen = NONE

    /** The pen touched the page (call for every raw drawing event, or at least often enough). */
    fun onPenWriting(nowMs: Long) {
        if (runStart == NONE || lastPen == NONE || nowMs - lastPen > idleGapMs) runStart = nowMs
        lastPen = nowMs
    }

    /** Call regularly (twice a second is plenty). Returns true when the rail folded on this call. */
    fun tick(nowMs: Long, popoverOpen: Boolean = false): Boolean {
        if (!autoHide || folded || popoverOpen || runStart == NONE) return false
        val stillWriting = nowMs - lastPen <= idleGapMs
        if (stillWriting && nowMs - runStart >= hideAfterMs) {
            folded = true
            return true
        }
        return false
    }

    /** Handle or edge tapped, or the toolbar shown by another route. */
    fun unfold() {
        folded = false
        runStart = NONE
    }

    /** The user hid the toolbar, or the page wants it folded (planner pages). */
    fun fold() {
        folded = true
        runStart = NONE
    }

    fun setAutoHide(enabled: Boolean) {
        autoHide = enabled
        if (!enabled) runStart = NONE
    }

    companion object {
        const val HIDE_AFTER_MS = 5_000L
        const val IDLE_GAP_MS = 1_500L
        private const val NONE = Long.MIN_VALUE
    }
}

/** Sizes. The rail sits in a gutter whose width never changes, so the page area keeps its size. */
object RailMetrics {
    /** Width of the gutter beside the page area (what the old sidebar used when it was open). */
    const val GUTTER_DP = 56

    /** Smallest circle on an ordinary screen (about 9 mm on a 300 ppi tablet at density 2). */
    const val MIN_CIRCLE_DP = 48f
    /** Only if the screen is too short for all buttons at [MIN_CIRCLE_DP] do they shrink to this, then the rail scrolls. */
    const val FLOOR_CIRCLE_DP = 40f
    const val GAP_DP = 2f
    const val RULE_DP = 12f
    const val PADDING_DP = 6f

    /** The mockup has 88 px circles on a 1860 px wide screen. */
    fun preferredCircleDp(screenWidthDp: Float): Float =
        (screenWidthDp * 88f / 1860f).coerceAtLeast(MIN_CIRCLE_DP).coerceAtMost(GUTTER_DP - 4f)

    fun contentHeightDp(slots: Int, rules: Int, circle: Float): Float =
        slots * (circle + GAP_DP) + rules * RULE_DP + 2 * PADDING_DP

    /** Circle size for a screen: the preferred size, shrunk (never below [FLOOR_CIRCLE_DP]) if all slots would not fit. */
    fun circleDp(screenWidthDp: Float, availableHeightDp: Float, slots: Int, rules: Int): Float {
        val preferred = preferredCircleDp(screenWidthDp)
        if (contentHeightDp(slots, rules, preferred) <= availableHeightDp) return preferred
        val fit = (availableHeightDp - rules * RULE_DP - 2 * PADDING_DP) / slots - GAP_DP
        return fit.coerceIn(FLOOR_CIRCLE_DP, preferred)
    }
}

/** Page counter and neighbours for the buttons "n of N", ‹ and ›. */
data class PagePosition(val index: Int, val total: Int) {
    val hasPrev get() = index > 0
    val hasNext get() = index in 0 until total - 1
    val number get() = index + 1
    val known get() = index >= 0 && total > 0
}
