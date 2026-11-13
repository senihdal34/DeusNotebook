package com.ethran.notable.editor.ui

/**
 * The thin scroll bar along the bottom edge (pure maths). It is measured against the viewport that is actually
 * drawn (the page area beside the tool rail), never against the window, and it shows only when the page really is
 * scrolled sideways: a bar computed from a width that includes the rail's gutter would be drawn on every page.
 */
object HorizontalIndicator {
    /** Start and size as fractions of the bar's length, or null when there is nothing to show. */
    fun fractions(viewportPx: Int, scrollX: Float): Pair<Float, Float>? {
        if (viewportPx <= 0 || scrollX <= 0f) return null
        val virtual = scrollX + viewportPx
        return scrollX / virtual to viewportPx / virtual
    }
}
