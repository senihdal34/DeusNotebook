package com.ethran.notable.editor

/**
 * What the editor's bitmap shows before the first full redraw, and whether that is enough.
 *
 * A page opens while its row is still being read, so the first bitmap is drawn without knowing the background. A new, empty
 * note then has nothing else to wait for: its strokes load in a few milliseconds, often before the surface exists, and the
 * redraw the loader asks for can arrive before anybody listens or before there is a surface to show it on. The ruled lines
 * must therefore not depend on that redraw: the bitmap is painted again as soon as the row is known.
 */
internal enum class InitialBackground {
    /** The page row is not read yet: a white sheet stands in, and the real background has to be painted once it is. */
    PLACEHOLDER,

    /** A native template (lines, dots, squares, inbox): drawn straight into the bitmap. */
    NATIVE,

    /** Pictures and PDFs are drawn by the full redraw; planner sheets of another shape are scaled by it too. */
    PLAIN_WHITE,
}

internal object FirstPaint {
    fun initialBackground(pageKnown: Boolean, isNativeBackground: Boolean, plannerSheetNeedsScaling: Boolean): InitialBackground = when {
        !pageKnown -> InitialBackground.PLACEHOLDER
        plannerSheetNeedsScaling -> InitialBackground.PLAIN_WHITE
        isNativeBackground -> InitialBackground.NATIVE
        else -> InitialBackground.PLAIN_WHITE
    }

    /** The bitmap has to be painted again once the page row is known if it only got the stand-in. */
    fun needsRepaintWhenPageKnown(drawn: InitialBackground): Boolean = drawn == InitialBackground.PLACEHOLDER
}
