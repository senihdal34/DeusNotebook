package com.ethran.notable.planner

import android.content.Context
import androidx.compose.ui.unit.dp
import com.ethran.notable.SCREEN_HEIGHT
import com.ethran.notable.SCREEN_WIDTH
import com.ethran.notable.editor.rail.RailMetrics
import com.ethran.notable.ui.convertDpToPixel

/**
 * The fixed planner page of this device: the portrait page, whatever the screen is turned to. Everything that depends on
 * the page's height (the layout of the rows, the ink migration, the week/day sync, the exports) reads it from here and never
 * from the current size of the editor, which is a different shape in landscape.
 */
object PlannerPage {
    /** Width of the tool rail's gutter in pixels (constant in every orientation). */
    fun gutterPx(context: Context): Float = convertDpToPixel(RailMetrics.GUTTER_DP.dp, context)

    fun size(context: Context): PlannerPageSize =
        PlannerViewport.portraitPage(SCREEN_WIDTH, SCREEN_HEIGHT, gutterPx(context))

    /** The viewport of a page area [boxW] x [boxH] pixels. */
    fun viewport(context: Context, boxW: Int, boxH: Int): PlannerViewport =
        PlannerViewport.of(boxW.toFloat(), boxH.toFloat(), size(context))
}
