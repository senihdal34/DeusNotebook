package com.ethran.notable.planner

import androidx.compose.ui.geometry.Offset
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Size of the one fixed planner page (the portrait page), in page pixels. */
data class PlannerPageSize(val width: Float, val height: Float)

/**
 * Where the fixed planner page sits inside the editor's page area ("the box"), in screen pixels.
 *
 * Every planner page (week, day, month, quarter, year) is laid out on ONE virtual page, the portrait page, and handwriting
 * is stored in that page's coordinates. In portrait the box is that page: scale 1, offset 0, nothing changes. In any other
 * shape (landscape, split screen) the same page is shown scaled to fit the box and centred, so ink, rows, tap zones and the
 * overlays all stay together. The part of the box left over is a quiet margin.
 *
 * Mapping: `screen = page * scale + offset`, `page = (screen - offset) / scale`.
 *
 * Pure (no Android classes) so the maths is tested on the JVM. The editor feeds it into the page's existing zoom and scroll
 * (`zoom = scale`, `scroll = -offset / scale`), so the pen, eraser and selection paths need no change of their own.
 */
data class PlannerViewport(
    val boxW: Float, val boxH: Float,
    val pageW: Float, val pageH: Float,
    val scale: Float, val offsetX: Float, val offsetY: Float
) {
    /** True when the box is the page itself (portrait): nothing is scaled or moved. */
    val isIdentity: Boolean get() = scale == 1f && offsetX == 0f && offsetY == 0f

    /** The sheet (the page as it appears on screen) in box pixels. */
    val sheet: PageRect get() = PageRect(offsetX, offsetY, offsetX + pageW * scale, offsetY + pageH * scale)

    fun toPageX(screenX: Float): Float = (screenX - offsetX) / scale
    fun toPageY(screenY: Float): Float = (screenY - offsetY) / scale
    fun toScreenX(pageX: Float): Float = pageX * scale + offsetX
    fun toScreenY(pageY: Float): Float = pageY * scale + offsetY

    fun toPage(screen: Offset): Offset = Offset(toPageX(screen.x), toPageY(screen.y))
    fun toScreen(page: Offset): Offset = Offset(toScreenX(page.x), toScreenY(page.y))

    /** True when the screen point lies on the sheet (not in the margin). */
    fun sheetContains(screenX: Float, screenY: Float): Boolean = sheet.contains(screenX, screenY)

    /** The scroll that puts the sheet at its place when the page is drawn with `zoom = scale` (the page's own scroll is `screen / zoom + scroll`). */
    val scroll: Offset get() = Offset(-offsetX / scale, -offsetY / scale)

    companion object {
        /** A box that is within this many pixels of the page counts as the page (rounding between dp and px). */
        const val SNAP_PX = 2f

        /** The viewport of a box that is the page. */
        fun identity(boxW: Float, boxH: Float) = PlannerViewport(boxW, boxH, boxW, boxH, 1f, 0f, 0f)

        /**
         * The viewport of a [boxW] x [boxH] page area for the fixed page [pageW] x [pageH]. A box that already is the page
         * (within [SNAP_PX]) gives the identity with the box as the page, so portrait stays exactly as it is. Otherwise the page
         * is scaled to fit the box and centred. Sizes that are not positive and finite give the identity (never NaN).
         */
        fun of(boxW: Float, boxH: Float, pageW: Float, pageH: Float): PlannerViewport {
            val ok = boxW.isFinite() && boxH.isFinite() && pageW.isFinite() && pageH.isFinite() &&
                boxW > 0f && boxH > 0f && pageW > 0f && pageH > 0f
            if (!ok) return identity(max(boxW.takeIf { it.isFinite() } ?: 0f, 0f), max(boxH.takeIf { it.isFinite() } ?: 0f, 0f))
            if (abs(boxW - pageW) <= SNAP_PX && abs(boxH - pageH) <= SNAP_PX) return identity(boxW, boxH)
            val scale = min(boxW / pageW, boxH / pageH)
            return PlannerViewport(boxW, boxH, pageW, pageH, scale, (boxW - pageW * scale) / 2f, (boxH - pageH * scale) / 2f)
        }

        /** [of] with the page given as a [PlannerPageSize]. */
        fun of(boxW: Float, boxH: Float, page: PlannerPageSize) = of(boxW, boxH, page.width, page.height)

        /**
         * The portrait page of a screen [screenW] x [screenH] (either way round) whose editor has a tool rail gutter of
         * [gutterPx] on one side: the short side minus the gutter wide, the long side tall. On a 1860 x 2480 screen with a
         * 105 px gutter that is 1755 x 2480, the box of the portrait editor.
         */
        fun portraitPage(screenW: Int, screenH: Int, gutterPx: Float): PlannerPageSize =
            PlannerPageSize(min(screenW, screenH) - gutterPx, max(screenW, screenH).toFloat())
    }
}
